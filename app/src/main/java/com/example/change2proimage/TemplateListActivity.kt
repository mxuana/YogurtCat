package com.example.change2proimage

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide

class TemplateListActivity : BaseActivity() {
    private lateinit var store: WorkspaceStore
    private lateinit var grid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WorkspaceStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(18))
            setBackgroundColor(Color.parseColor("#FFF8FC"))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(Button(this).apply { text = "‹"; textSize = 25f; backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT); setTextColor(Color.parseColor("#2A2233")); setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(TextView(this).apply { text = "模板"; textSize = 25f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#2A2233")); setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        top.addView(Button(this).apply { text = "＋ 新建"; backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8F57D8")); setTextColor(Color.WHITE); setOnClickListener { startActivity(Intent(this@TemplateListActivity, TemplateEditActivity::class.java)) } }, LinearLayout.LayoutParams(dp(104), dp(46)))
        root.addView(top)

        val sticker = TextView(this).apply {
            text = "表情包模板  ·  图片模板与批量生成   ›"
            textSize = 14f; setTextColor(Color.parseColor("#65458A")); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(12), 0); background = softBg()
            setOnClickListener { startActivity(Intent(this@TemplateListActivity, StickerActivity::class.java)) }
        }
        root.addView(sticker, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10); bottomMargin = dp(12) })

        grid = GridLayout(this).apply { columnCount = 2; alignmentMode = GridLayout.ALIGN_BOUNDS; useDefaultMargins = false }
        root.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); if (::grid.isInitialized) render() }

    private fun render() {
        grid.removeAllViews()
        val items = store.getTemplates().filterNot { it.name == "透明表情包" }
        items.forEachIndexed { index, t ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = cardBg(); setPadding(dp(6), dp(6), dp(6), dp(10))
                setOnClickListener { open(t) }
                setOnLongClickListener { confirmDelete(t); true }
            }
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#F3EAF8")) }
                setOnClickListener { open(t) }
            }
            t.coverUri.takeIf { it.isNotBlank() }?.let { Glide.with(this).load(Uri.parse(it)).into(preview) } ?: t.fixedReferenceUris.firstOrNull()?.let { Glide.with(this).load(Uri.parse(it)).into(preview) }
                ?: preview.setImageResource(android.R.drawable.ic_menu_gallery)
            card.addView(preview, LinearLayout.LayoutParams(-1, dp(220)))
            card.addView(TextView(this).apply {
                text = t.name; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#2A2233")); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(6), dp(9), dp(6), 0)
            })
            card.addView(TextView(this).apply {
                text = t.prompt.replace("\n", " "); textSize = 12f; setTextColor(Color.parseColor("#817787")); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(6), dp(3), dp(6), 0)
            })
            val lp = GridLayout.LayoutParams().apply {
                width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(index % 2, 1f)
                setMargins(if (index % 2 == 0) 0 else dp(5), dp(5), if (index % 2 == 0) dp(5) else 0, dp(5))
            }
            grid.addView(card, lp)
        }
        if (items.isEmpty()) grid.addView(TextView(this).apply { text = "还没有模板\n点击右上角「新建」创建"; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#756D7C")); setPadding(0, dp(60), 0, 0) }, GridLayout.LayoutParams().apply { width = -1; columnSpec = GridLayout.spec(0, 2) })
    }

    private fun open(t: GenerationTemplate) = startActivity(Intent(this, TemplateDetailActivity::class.java).putExtra("template_id", t.id))
    private fun confirmDelete(t: GenerationTemplate) { AlertDialog.Builder(this).setTitle("删除模板？").setMessage("将删除「${t.name}」。").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.deleteTemplate(t.id); render() }.show() }
    private fun cardBg() = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.parseColor("#E8DCEF")) }
    private fun softBg() = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#F5EFFA")); setStroke(dp(1), Color.parseColor("#E8DCEF")) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
