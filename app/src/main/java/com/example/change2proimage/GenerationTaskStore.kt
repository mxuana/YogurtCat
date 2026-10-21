package com.example.change2proimage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GenerationTask(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val prompt: String,
    val referenceUris: List<String>,
    val relayId: String,
    val relayName: String,
    val model: String,
    val resolutionLevel: String,
    val aspectRatio: String,
    val transparentBackground: Boolean = false,
    val status: String = STATUS_QUEUED,
    val stage: String = "等待开始",
    val requestedSize: String = "",
    val actualSize: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val httpCode: Int = 0,
    val errorMessage: String = "",
    val responseSnippet: String = "",
    val historyId: String = "",
    val resultPath: String = "",
    val revisedPrompt: String = ""
) {
    fun isRunning(): Boolean = status in setOf(STATUS_QUEUED, STATUS_PREPARING, STATUS_GENERATING, STATUS_DOWNLOADING)
    fun isTerminal(): Boolean = status == STATUS_SUCCESS || status == STATUS_FAILED

    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_GENERATING = "GENERATING"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
    }
}

class GenerationTaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("generation_tasks_v7", Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): MutableList<GenerationTask> {
        val raw = prefs.getString("tasks", null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()?.let { add(it) }
                }
            }.sortedByDescending { it.updatedAt }.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    @Synchronized fun get(id: String): GenerationTask? = getAll().firstOrNull { it.id == id }
    @Synchronized fun getLatestForSession(sessionId: String): GenerationTask? = getAll().firstOrNull { it.sessionId == sessionId }

    @Synchronized
    fun upsert(task: GenerationTask) {
        val all = getAll()
        val index = all.indexOfFirst { it.id == task.id }
        if (index >= 0) all[index] = task else all.add(0, task)
        save(all.sortedByDescending { it.updatedAt }.take(80))
    }

    @Synchronized
    fun update(id: String, transform: (GenerationTask) -> GenerationTask): GenerationTask? {
        val all = getAll()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = transform(all[index]).copy(updatedAt = System.currentTimeMillis())
        all[index] = updated
        save(all.sortedByDescending { it.updatedAt }.take(80))
        return updated
    }


    @Synchronized
    fun recoverInterruptedTasksOnce(): Int {
        if (!recoveryClaimed.compareAndSet(false, true)) return 0
        val now = System.currentTimeMillis()
        var changed = 0
        val recovered = getAll().map { task ->
            if (task.status in setOf(
                    GenerationTask.STATUS_QUEUED,
                    GenerationTask.STATUS_PREPARING,
                    GenerationTask.STATUS_GENERATING,
                    GenerationTask.STATUS_DOWNLOADING
                )
            ) {
                changed++
                task.copy(
                    status = GenerationTask.STATUS_FAILED,
                    stage = "任务异常中断",
                    completedAt = now,
                    updatedAt = now,
                    errorMessage = "应用进程曾被系统终止，无法确认远端请求状态。为避免重复计费，本任务不会自动重试，请手动确认后再重试。"
                )
            } else task
        }
        if (changed > 0) save(recovered.sortedByDescending { it.updatedAt }.take(80))
        return changed
    }

    private fun save(items: List<GenerationTask>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("tasks", arr.toString()).commit()
    }

    private fun toJson(t: GenerationTask) = JSONObject()
        .put("id", t.id).put("sessionId", t.sessionId).put("prompt", t.prompt)
        .put("referenceUris", JSONArray(t.referenceUris)).put("relayId", t.relayId)
        .put("relayName", t.relayName).put("model", t.model)
        .put("resolutionLevel", t.resolutionLevel).put("aspectRatio", t.aspectRatio)
        .put("transparentBackground", t.transparentBackground)
        .put("status", t.status).put("stage", t.stage)
        .put("requestedSize", t.requestedSize).put("actualSize", t.actualSize)
        .put("createdAt", t.createdAt).put("updatedAt", t.updatedAt).put("completedAt", t.completedAt)
        .put("httpCode", t.httpCode).put("errorMessage", t.errorMessage).put("responseSnippet", t.responseSnippet)
        .put("historyId", t.historyId).put("resultPath", t.resultPath).put("revisedPrompt", t.revisedPrompt)

    private fun fromJson(o: JSONObject): GenerationTask {
        val refs = mutableListOf<String>()
        val arr = o.optJSONArray("referenceUris") ?: JSONArray()
        for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(refs::add)
        return GenerationTask(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            sessionId = o.optString("sessionId"), prompt = o.optString("prompt"), referenceUris = refs,
            relayId = o.optString("relayId"), relayName = o.optString("relayName"), model = o.optString("model"),
            resolutionLevel = o.optString("resolutionLevel", "1K"), aspectRatio = o.optString("aspectRatio", "1:1"),
            transparentBackground = o.optBoolean("transparentBackground", false),
            status = o.optString("status", GenerationTask.STATUS_QUEUED), stage = o.optString("stage", "等待开始"),
            requestedSize = o.optString("requestedSize"), actualSize = o.optString("actualSize"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()), updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            completedAt = o.optLong("completedAt"), httpCode = o.optInt("httpCode"), errorMessage = o.optString("errorMessage"),
            responseSnippet = o.optString("responseSnippet"), historyId = o.optString("historyId"),
            resultPath = o.optString("resultPath"), revisedPrompt = o.optString("revisedPrompt")
        )
    }

    companion object {
        private val recoveryClaimed = java.util.concurrent.atomic.AtomicBoolean(false)
    }

}
