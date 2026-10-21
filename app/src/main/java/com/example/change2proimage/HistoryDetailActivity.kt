package com.example.change2proimage

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryDetailActivity : BaseActivity() {
    private lateinit var store: WorkspaceStore
    private var record: HistoryRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)
        store = WorkspaceStore(this)
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        val id = intent.getStringExtra("history_id").orEmpty()
        val item = store.getHistoryItem(id)
        if (item == null) {
            Toast.makeText(this, "历史记录不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        record = item
        bind(item)
        findViewById<Button>(R.id.reuse).setOnClickListener {
            setResult(Activity.RESULT_OK, Intent().putExtra("history_id", item.id))
            finish()
        }
        findViewById<Button>(R.id.saveHistoryImage).setOnClickListener { saveImage(item) }
        findViewById<Button>(R.id.copyDiagnostics).setOnClickListener { copyDiagnostics(item) }
    }

    private fun bind(item: HistoryRecord) {
        val image = findViewById<ImageView>(R.id.historyImage)
        val save = findViewById<Button>(R.id.saveHistoryImage)
        val file = item.resultPath.takeIf { it.isNotBlank() }?.let(::File)
        if (item.status == "SUCCESS" && file?.exists() == true) {
            image.visibility = View.VISIBLE
            Glide.with(this).load(file).into(image)
            save.visibility = View.VISIBLE
        } else {
            image.visibility = View.GONE
            save.visibility = View.GONE
        }

        findViewById<TextView>(R.id.historyPrompt).text = item.prompt
        findViewById<TextView>(R.id.historyParams).text = buildString {
            append("状态：${if (item.status == "FAILED") "生成失败" else "生成成功"}\n")
            append("中转站：${item.relayName}\n模型：${item.model}\n")
            append("选择：${item.resolutionLevel} · ${item.aspectRatio}\n")
            if (item.actualSize.isNotBlank()) append("实际请求尺寸：${item.actualSize}\n")
            append("参考图：${item.referenceUris.size} 张\n")
            append("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.createdAt))}\n")
            if (item.durationMs > 0) append("耗时：${formatDuration(item.durationMs)}\n")
            if (item.status == "FAILED") {
                append("\n失败阶段：${item.stage.ifBlank { "未知" }}\n")
                if (item.httpCode > 0) append("HTTP：${item.httpCode}\n")
                append("错误：${item.errorDetail.ifBlank { "未记录详细错误" }}")
                if (item.responseSnippet.isNotBlank()) append("\n\n服务器返回：\n${item.responseSnippet}")
            } else if (item.revisedPrompt.isNotBlank()) {
                append("\n优化提示词：${item.revisedPrompt}")
            }
        }

        val refs = findViewById<LinearLayout>(R.id.historyRefs)
        refs.removeAllViews()
        item.referenceUris.forEach { s ->
            val ref = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(92), dp(92)).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this).load(Uri.parse(s)).into(ref)
            refs.addView(ref)
        }
        if (item.referenceUris.isEmpty()) refs.addView(TextView(this).apply { text = "无参考图" })
    }

    private fun copyDiagnostics(item: HistoryRecord) {
        val text = buildString {
            append("酸奶喵生图诊断\n")
            append("记录ID：${item.id}\n")
            append("状态：${item.status}\n")
            append("阶段：${item.stage}\n")
            append("中转站：${item.relayName}\n")
            append("模型：${item.model}\n")
            append("选择：${item.resolutionLevel} · ${item.aspectRatio}\n")
            append("实际尺寸：${item.actualSize.ifBlank { "自动" }}\n")
            append("参考图：${item.referenceUris.size} 张\n")
            append("HTTP：${if (item.httpCode > 0) item.httpCode else "无"}\n")
            append("错误：${item.errorDetail.ifBlank { "无" }}\n")
            if (item.responseSnippet.isNotBlank()) append("服务器返回：${item.responseSnippet}")
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("酸奶喵诊断", text))
        Toast.makeText(this, "诊断信息已复制（不包含 API Key）", Toast.LENGTH_SHORT).show()
    }

    private fun saveImage(item: HistoryRecord) {
        val file = File(item.resultPath)
        if (!file.exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val format = ImageFormatUtil.fromFile(file)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "酸奶喵_${System.currentTimeMillis()}.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/酸奶喵")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        Toast.makeText(this, if (uri != null) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val minutes = total / 60
        val seconds = total % 60
        return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
