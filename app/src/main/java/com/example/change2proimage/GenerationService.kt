package com.example.change2proimage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.app.NotificationCompat
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GenerationService : Service() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .callTimeout(15, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private lateinit var taskStore: GenerationTaskStore
    private lateinit var relayStore: RelayStore
    private lateinit var workspace: WorkspaceStore

    override fun onCreate() {
        super.onCreate()
        taskStore = GenerationTaskStore(this)
        relayStore = RelayStore(this)
        workspace = WorkspaceStore(this)
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val task = taskStore.get(taskId)
        if (taskId.isBlank() || task == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(task, "准备生成…", ongoing = true))

        if (runningTaskIds.isNotEmpty() && taskId !in runningTaskIds) {
            failTask(task, "任务调度", "当前已有另一个生图任务正在运行")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!runningTaskIds.add(taskId)) {
            return START_NOT_STICKY
        }

        Thread {
            try {
                runTask(taskId)
            } finally {
                runningTaskIds.remove(taskId)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun runTask(taskId: String) {
        val task = taskStore.get(taskId) ?: return
        val relay = relayStore.getAll().firstOrNull { it.id == task.relayId }
        if (relay == null) {
            failTask(task, "准备请求", "中转站配置不存在，可能已被删除")
            return
        }
        if (relay.apiKey.isBlank()) {
            failTask(task, "准备请求", "${relay.name} 未配置 API Key")
            return
        }

        val plan = ImageSizeResolver.resolve(relay, task.model, task.resolutionLevel, task.aspectRatio)
        val requestedSize = plan.targetLabel
        val actualSize = plan.requestLabel
        updateTask(taskId, GenerationTask.STATUS_PREPARING, "读取参考图", requestedSize, actualSize)
        updateNotification(taskId, "正在准备参考图…")

        val hasReferences = task.referenceUris.isNotEmpty()
        val endpoint = joinUrl(relay.baseUrl, if (hasReferences) relay.editsPath else relay.generationsPath)

        val requestBody = try {
            if (hasReferences) buildMultipartBody(relay, task, plan) else buildJsonBody(relay, task, plan)
        } catch (e: Exception) {
            failTask(taskStore.get(taskId) ?: task, "读取参考图", e.message ?: e.javaClass.simpleName)
            return
        }

        updateTask(taskId, GenerationTask.STATUS_GENERATING, "等待模型生成", requestedSize, actualSize)
        updateNotification(taskId, "模型正在生成…")

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${relay.apiKey}")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val hint = if (task.transparentBackground && response.code in setOf(400, 422)) {
                        "HTTP ${response.code}；当前模型或中转站可能不支持透明背景参数"
                    } else {
                        "HTTP ${response.code}"
                    }
                    failTask(
                        taskStore.get(taskId) ?: task,
                        "等待模型生成",
                        hint,
                        response.code,
                        text.take(MAX_DIAGNOSTIC_CHARS)
                    )
                    return
                }
                handleGenerationJson(taskId, text)
            }
        } catch (e: Exception) {
            failTask(taskStore.get(taskId) ?: task, "等待模型生成", "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
    }

    private fun effectivePrompt(task: GenerationTask): String {
        if (!task.transparentBackground) return task.prompt
        return task.prompt + "\n\n输出要求：透明背景，保留 Alpha 通道；不要白色背景、纯色背景、场景背景或棋盘格背景。"
    }

    private fun buildJsonBody(relay: RelayConfig, task: GenerationTask, plan: SizePlan): RequestBody {
        val obj = JSONObject().put("model", task.model).put("prompt", effectivePrompt(task)).put("stream", relay.streamEnabled)
        if (task.transparentBackground) obj.put("background", "transparent")
        addJsonSize(obj, relay, plan)
        return obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }

    private fun buildMultipartBody(relay: RelayConfig, task: GenerationTask, plan: SizePlan): RequestBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", task.model)
            .addFormDataPart("prompt", effectivePrompt(task))
            .addFormDataPart("stream", relay.streamEnabled.toString())
        if (task.transparentBackground) builder.addFormDataPart("background", "transparent")
        addMultipartSize(builder, relay, plan)

        task.referenceUris.forEachIndexed { index, value ->
            val uri = Uri.parse(value)
            val mime = contentResolver.getType(uri) ?: "image/png"
            val name = queryFileName(uri) ?: "reference_${index + 1}.${if (mime.contains("jpeg")) "jpg" else "png"}"
            builder.addFormDataPart(relay.imageField.ifBlank { "image" }, name, uriRequestBody(uri, mime))
        }
        return builder.build()
    }

    private fun uriRequestBody(uri: Uri, mime: String): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType? = mime.toMediaTypeOrNull()

        override fun contentLength(): Long {
            return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
        }

        override fun writeTo(sink: BufferedSink) {
            val input = contentResolver.openInputStream(uri) ?: throw IOException("无法读取参考图：$uri")
            input.use { stream -> sink.writeAll(stream.source()) }
        }
    }

    private fun addJsonSize(obj: JSONObject, relay: RelayConfig, plan: SizePlan) {
        val w = plan.requestWidth ?: return
        val h = plan.requestHeight ?: return
        when (relay.sizeMode) {
            "width_height" -> { obj.put("width", w); obj.put("height", h) }
            "none" -> Unit
            else -> obj.put("size", "${w}x${h}")
        }
    }

    private fun addMultipartSize(builder: MultipartBody.Builder, relay: RelayConfig, plan: SizePlan) {
        val w = plan.requestWidth ?: return
        val h = plan.requestHeight ?: return
        when (relay.sizeMode) {
            "width_height" -> {
                builder.addFormDataPart("width", w.toString())
                builder.addFormDataPart("height", h.toString())
            }
            "none" -> Unit
            else -> builder.addFormDataPart("size", "${w}x${h}")
        }
    }

    private fun handleGenerationJson(taskId: String, text: String) {
        try {
            val item = JSONObject(text).getJSONArray("data").getJSONObject(0)
            val revised = item.optString("revised_prompt")
            val b64 = item.optString("b64_json")
            val url = item.optString("url")
            when {
                b64.isNotBlank() -> finishSuccess(taskId, Base64.decode(b64, Base64.DEFAULT), url, revised, null)
                url.isNotBlank() -> downloadResult(taskId, url, revised)
                else -> failTask(taskStore.get(taskId) ?: return, "解析接口结果", "接口成功返回，但没有 data[0].url 或 b64_json", response = text.take(MAX_DIAGNOSTIC_CHARS))
            }
        } catch (e: Exception) {
            failTask(taskStore.get(taskId) ?: return, "解析接口结果", "${e.javaClass.simpleName}: ${e.message.orEmpty()}", response = text.take(MAX_DIAGNOSTIC_CHARS))
        }
    }

    private fun downloadResult(taskId: String, url: String, revised: String) {
        updateTask(taskId, GenerationTask.STATUS_DOWNLOADING, "下载生成结果")
        updateNotification(taskId, "生成完成，正在下载图片…")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    failTask(taskStore.get(taskId) ?: return, "下载生成结果", "图片下载 HTTP ${response.code}", response.code)
                    return
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    failTask(taskStore.get(taskId) ?: return, "下载生成结果", "图片数据为空")
                    return
                }
                finishSuccess(taskId, bytes, url, revised, response.header("Content-Type"))
            }
        } catch (e: Exception) {
            failTask(taskStore.get(taskId) ?: return, "下载生成结果", "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
    }

    private fun finishSuccess(taskId: String, bytes: ByteArray, url: String, revised: String, contentType: String?) {
        val task = taskStore.get(taskId) ?: return
        val dir = File(filesDir, "history").apply { mkdirs() }
        val format = ImageFormatUtil.detect(bytes, contentType)
        val file = File(dir, "$taskId.${format.extension}")
        file.writeBytes(bytes)
        val now = System.currentTimeMillis()
        workspace.addHistory(
            HistoryRecord(
                id = taskId,
                sessionId = task.sessionId,
                createdAt = task.createdAt,
                prompt = task.prompt,
                referenceUris = task.referenceUris,
                relayId = task.relayId,
                relayName = task.relayName,
                model = task.model,
                resolutionLevel = task.resolutionLevel,
                aspectRatio = task.aspectRatio,
                actualSize = task.actualSize,
                transparentBackground = task.transparentBackground,
                sizeLabel = task.actualSize.ifBlank { "自动" },
                resultPath = file.absolutePath,
                resultUrl = url,
                revisedPrompt = revised,
                status = "SUCCESS",
                stage = "生成完成",
                durationMs = now - task.createdAt
            )
        )
        taskStore.update(taskId) {
            it.copy(
                status = GenerationTask.STATUS_SUCCESS,
                stage = "生成完成",
                completedAt = now,
                historyId = taskId,
                resultPath = file.absolutePath,
                revisedPrompt = revised
            )
        }
        showFinishedNotification(taskId, success = true, message = "图片生成完成，点击查看")
    }

    private fun failTask(task: GenerationTask, stage: String, message: String, httpCode: Int = 0, response: String = "") {
        if (task.isTerminal()) return
        val now = System.currentTimeMillis()
        workspace.addHistory(
            HistoryRecord(
                id = task.id,
                sessionId = task.sessionId,
                createdAt = task.createdAt,
                prompt = task.prompt,
                referenceUris = task.referenceUris,
                relayId = task.relayId,
                relayName = task.relayName,
                model = task.model,
                resolutionLevel = task.resolutionLevel,
                aspectRatio = task.aspectRatio,
                actualSize = task.actualSize,
                transparentBackground = task.transparentBackground,
                sizeLabel = task.actualSize.ifBlank { "自动" },
                resultPath = "",
                status = "FAILED",
                stage = stage,
                httpCode = httpCode,
                errorDetail = message,
                responseSnippet = response.take(MAX_DIAGNOSTIC_CHARS),
                durationMs = now - task.createdAt
            )
        )
        taskStore.update(task.id) {
            it.copy(
                status = GenerationTask.STATUS_FAILED,
                stage = stage,
                completedAt = now,
                httpCode = httpCode,
                errorMessage = message,
                responseSnippet = response.take(MAX_DIAGNOSTIC_CHARS),
                historyId = task.id
            )
        }
        showFinishedNotification(task.id, success = false, message = "生成失败：${message.take(80)}")
    }

    private fun updateTask(taskId: String, status: String, stage: String, requested: String? = null, actual: String? = null) {
        taskStore.update(taskId) {
            it.copy(
                status = status,
                stage = stage,
                requestedSize = requested ?: it.requestedSize,
                actualSize = actual ?: it.actualSize
            )
        }
    }

    private fun queryFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "酸奶喵生图任务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "后台生成图片时显示任务状态"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(task: GenerationTask, text: String, ongoing: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("酸奶喵 · ${task.model}")
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPendingIntent(task.id))
            .build()

    private fun updateNotification(taskId: String, text: String) {
        val task = taskStore.get(taskId) ?: return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(task, text, ongoing = true))
    }

    private fun showFinishedNotification(taskId: String, success: Boolean, message: String) {
        val task = taskStore.get(taskId) ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (success) R.drawable.ic_notification else R.drawable.ic_notification)
            .setContentTitle(if (success) "酸奶喵 · 生成完成" else "酸奶喵 · 生成失败")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent(taskId))
            .build()
        getSystemService(NotificationManager::class.java).notify(taskId.hashCode(), notification)
    }

    private fun mainPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun joinUrl(base: String, path: String) = base.trimEnd('/') + "/" + path.trimStart('/')

    companion object {
        const val EXTRA_TASK_ID = "generation_task_id"
        private const val CHANNEL_ID = "generation_tasks_v7"
        private const val NOTIFICATION_ID = 7101
        private const val MAX_DIAGNOSTIC_CHARS = 3000
        private val runningTaskIds = ConcurrentHashMap.newKeySet<String>()
    }
}
