package com.example.change2proimage

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide

class StickerTaskActivity : BaseActivity() {
    private lateinit var ss: StickerStore
    private lateinit var gs: GenerationTaskStore
    private lateinit var list: LinearLayout
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { render(); h.postDelayed(this, 1200) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); ss = StickerStore(this); gs = GenerationTaskStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(18)); setBackgroundColor(Color.parseColor("#FFF8FC")) }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@StickerTaskActivity).apply { text = "‹"; textSize = 34f; gravity = Gravity.CENTER; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(44), dp(48)))
            addView(TextView(this@StickerTaskActivity).apply { text = "任务详情"; textSize = 24f; setTypeface(typeface, Typeface.BOLD) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        })
        root.addView(TextView(this).apply { text = "查看整套进度、生成结果与失败项。成功图片会直接显示在这里。"; textSize = 13f; setTextColor(Color.parseColor("#786F84")); setPadding(dp(4), 0, dp(4), dp(10)) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }
    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { h.removeCallbacks(tick); super.onPause() }

    private fun render() {
        list.removeAllViews(); val batches = ss.getBatches()
        if (batches.isEmpty()) { list.addView(TextView(this).apply { text = "还没有表情包任务"; gravity = Gravity.CENTER; setPadding(0, dp(60), 0, 0); setTextColor(Color.parseColor("#786F84")) }); return }
        batches.forEach { b ->
            val tasks = b.generationTaskIds.mapNotNull(gs::get); val ok = tasks.count { it.status == GenerationTask.STATUS_SUCCESS }; val fail = tasks.count { it.status == GenerationTask.STATUS_FAILED }
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = cardBg() }
            card.addView(TextView(this).apply { text = "${b.ocName} · 表情包"; textSize = 19f; setTypeface(typeface, Typeface.BOLD) })
            card.addView(TextView(this).apply { text = "$ok / ${tasks.size} · ${if (ok == tasks.size && tasks.isNotEmpty()) "已完成" else if (fail > 0) "$fail 张失败" else "生成中"}\n${b.relayName} · ${b.model}"; textSize = 13f; setPadding(0, dp(5), 0, dp(8)); setTextColor(Color.parseColor("#786F84")) })
            card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = tasks.size.coerceAtLeast(1); progress = ok; progressTintList = ColorStateList.valueOf(Color.parseColor("#D96AA7")) }, LinearLayout.LayoutParams(-1, dp(6)))
            val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
            tasks.chunked(2).forEach { pair ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { task -> row.addView(taskTile(task), LinearLayout.LayoutParams(0, dp(176), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
                if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)); results.addView(row)
            }
            card.addView(results)
            if (fail > 0) card.addView(Button(this).apply { text = "重试失败项"; backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6A54C9")); setTextColor(Color.WHITE); setOnClickListener { retry(b, tasks.filter { it.status == GenerationTask.STATUS_FAILED }.map { it.id }) } })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun taskTile(task: GenerationTask): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(7), dp(7), dp(7), dp(7)); background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#FBF8FF")) }
        val image = ImageView(this@StickerTaskActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#F0EBF5")) } }
        if (task.status == GenerationTask.STATUS_SUCCESS && task.resultPath.isNotBlank()) Glide.with(this@StickerTaskActivity).load(java.io.File(task.resultPath)).into(image) else image.setImageResource(android.R.drawable.ic_menu_gallery)
        addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(TextView(this@StickerTaskActivity).apply { text = when (task.status) { GenerationTask.STATUS_SUCCESS -> "✓ 已完成"; GenerationTask.STATUS_FAILED -> "生成失败 · 点下方重试"; GenerationTask.STATUS_GENERATING -> "生成中…"; GenerationTask.STATUS_DOWNLOADING -> "下载结果…"; else -> task.stage.ifBlank { "等待生成" } }; textSize = 12f; maxLines = 1; setTextColor(if (task.status == GenerationTask.STATUS_FAILED) Color.parseColor("#C7485A") else Color.parseColor("#665F70")); setPadding(dp(2), dp(5), 0, 0) })
    }

    private fun retry(b: StickerQueueBatch, ids: List<String>) { ids.forEach { id -> gs.update(id) { it.copy(status = GenerationTask.STATUS_QUEUED, stage = "等待重试", completedAt = 0, errorMessage = "", httpCode = 0) } }; ContextCompat.startForegroundService(this, Intent(this, StickerQueueService::class.java).putExtra("batch_id", b.id)); Toast.makeText(this, "失败项已重新加入队列", Toast.LENGTH_SHORT).show() }
    private fun cardBg() = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.parseColor("#E8DCEF")) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
