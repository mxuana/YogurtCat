package com.example.change2proimage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateManager(private val activity: Activity) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val prefs = activity.getSharedPreferences("update_state", Activity.MODE_PRIVATE)

    fun checkForUpdate(silent: Boolean = false, force: Boolean = false) {
        if (silent && !force) {
            val last = prefs.getLong("last_check", 0L)
            if (System.currentTimeMillis() - last < 12L * 60L * 60L * 1000L) return
        }
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()

        // Do not use api.github.com here. Public unauthenticated API calls are rate-limited
        // per IP and may return HTTP 403 on shared mobile/VPN addresses.
        val repo = BuildConfig.UPDATE_REPO
        val url = "https://github.com/$repo/releases/latest/download/latest.json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YogurtCat-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        if (!silent) Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!silent) activity.runOnUiThread {
                    Toast.makeText(activity, "检查更新失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        if (!silent) activity.runOnUiThread {
                            val hint = when (it.code) {
                                404 -> "发布仓库还没有 latest.json，请先发布一个新版本"
                                else -> "HTTP ${it.code}"
                            }
                            Toast.makeText(activity, "检查更新失败：$hint", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    val obj = runCatching { JSONObject(body) }.getOrNull()
                    if (obj == null) {
                        if (!silent) activity.runOnUiThread {
                            Toast.makeText(activity, "更新信息格式错误", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    val remoteCode = obj.optInt("versionCode", 0)
                    val versionName = obj.optString("versionName")
                    val tag = obj.optString("tag").ifBlank { if (versionName.isBlank()) "" else "v$versionName" }
                    val notes = obj.optString("notes")
                    val apkUrl = obj.optString("apkUrl")
                    val apkName = obj.optString("apkName").ifBlank {
                        if (versionName.isBlank()) "YogurtCat-update.apk" else "YogurtCat-$versionName.apk"
                    }
                    val sha256 = obj.optString("sha256").trim().lowercase()

                    if (apkUrl.isBlank()) {
                        if (!silent) activity.runOnUiThread {
                            Toast.makeText(activity, "更新信息里没有 APK 下载地址", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    if (!sha256.matches(Regex("[0-9a-f]{64}"))) {
                        if (!silent) activity.runOnUiThread {
                            Toast.makeText(activity, "更新信息缺少有效 SHA-256，已拒绝下载安装包", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    val newer = if (remoteCode > 0) {
                        remoteCode > BuildConfig.VERSION_CODE
                    } else {
                        isNewer(versionName.ifBlank { tag }, BuildConfig.VERSION_NAME)
                    }

                    if (!newer) {
                        if (!silent) activity.runOnUiThread {
                            Toast.makeText(activity, "已经是最新版本 ${BuildConfig.VERSION_NAME}", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val title = if (versionName.isBlank()) "发现新版本" else "酸奶喵 $versionName"
                    activity.runOnUiThread { showUpdateDialog(title, tag, notes, apkUrl, apkName, sha256) }
                }
            }
        })
    }

    private fun showUpdateDialog(title: String, tag: String, notes: String, apkUrl: String, apkName: String, sha256: String) {
        val message = buildString {
            append("当前版本：${BuildConfig.VERSION_NAME}\n")
            append("最新版本：$tag")
            if (notes.isNotBlank()) append("\n\n").append(notes.take(1800))
        }
        AlertDialog.Builder(activity)
            .setTitle(if (title.isBlank()) "发现新版本" else title)
            .setMessage(message)
            .setNegativeButton("稍后", null)
            .setPositiveButton("立即更新") { _, _ -> downloadApk(apkUrl, apkName, sha256) }
            .show()
    }

    private fun downloadApk(url: String, name: String, expectedSha256: String) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val label = TextView(activity).apply {
            text = "正在下载安装包…"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        box.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val dialog = AlertDialog.Builder(activity)
            .setTitle("下载更新")
            .setView(box)
            .setNegativeButton("取消") { _, _ -> }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        val request = Request.Builder().url(url).header("User-Agent", "YogurtCat-Android").build()
        val call = client.newCall(request)
        dialog.setOnDismissListener { if (!call.isCanceled()) call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity.runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                    if (!call.isCanceled()) Toast.makeText(activity, "下载失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        activity.runOnUiThread {
                            if (dialog.isShowing) dialog.dismiss()
                            Toast.makeText(activity, "下载失败：HTTP ${it.code}", Toast.LENGTH_LONG).show()
                        }
                        return
                    }
                    val body = it.body ?: return
                    val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
                    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val file = File(dir, safeName)
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            var done = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                if (call.isCanceled()) return
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                                    activity.runOnUiThread {
                                        progress.isIndeterminate = false
                                        progress.progress = pct
                                        label.text = "正在下载… $pct%"
                                    }
                                }
                            }
                        }
                    }
                    val actualSha256 = sha256(file)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        file.delete()
                        activity.runOnUiThread {
                            if (dialog.isShowing) dialog.dismiss()
                            Toast.makeText(activity, "更新包 SHA-256 校验失败，文件已删除", Toast.LENGTH_LONG).show()
                        }
                        return
                    }
                    activity.runOnUiThread {
                        if (dialog.isShowing) dialog.dismiss()
                        installApk(file)
                    }
                }
            }
        })
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "请先允许此应用安装未知应用，然后再次点击“检查更新”", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            activity.startActivity(intent)
            return
        }

        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = Regex("\\d+").findAll(remote).map { it.value.toIntOrNull() ?: 0 }.toList()
        val l = Regex("\\d+").findAll(local).map { it.value.toIntOrNull() ?: 0 }.toList()
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
