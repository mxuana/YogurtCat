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
import com.bumptech.glide.Glide

class TemplateDetailActivity : BaseActivity() {
    private lateinit var store: WorkspaceStore
    private lateinit var template: GenerationTemplate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WorkspaceStore(this)
        val id = intent.getStringExtra("template_id")
        val found = id?.let(store::getTemplate)
        if (found == null) { Toast.makeText(this, "模板不存在", Toast.LENGTH_SHORT).show(); finish(); return }
        template = found

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#FFF8FC")) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        top.addView(Button(this).apply { text = "‹"; textSize = 26f; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48)))
        top.addView(TextView(this).apply { text = template.name; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#2A2233")); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(48), 1f))
        top.addView(Button(this).apply { text = "编辑"; setOnClickListener { startActivity(Intent(this@TemplateDetailActivity, TemplateEditActivity::class.java).putExtra("template_id", template.id)) } }, LinearLayout.LayoutParams(dp(70), dp(48)))
        root.addView(top)

        val scrollBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hero = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; adjustViewBounds = true; minimumHeight = dp(360); setBackgroundColor(Color.parseColor("#F4EDF7")) }
        val first = template.coverUri.takeIf { it.isNotBlank() } ?: template.fixedReferenceUris.firstOrNull()
        if (first != null) Glide.with(this).load(Uri.parse(first)).into(hero) else hero.setImageResource(android.R.drawable.ic_menu_gallery)
        scrollBody.addView(hero, LinearLayout.LayoutParams(-1, dp(430)))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(22)) }
        info.addView(TextView(this).apply { text = "创作信息"; textSize = 14f; setTextColor(Color.parseColor("#9A909F")) })
        info.addView(TextView(this).apply { text = template.prompt; textSize = 17f; setTextColor(Color.parseColor("#302837")); setPadding(0, dp(8), 0, dp(14)); setLineSpacing(0f, 1.15f) })
        info.addView(TextView(this).apply { text = "${template.resolutionLevel} · ${template.aspectRatio}" + if (template.transparentBackground) " · 透明背景" else ""; textSize = 13f; setTextColor(Color.parseColor("#817787")); setPadding(0, 0, 0, dp(12)) })
        if (template.fixedReferenceUris.isNotEmpty()) {
            val hsv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            template.fixedReferenceUris.forEachIndexed { index, value ->
                val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#F1EAF5")) } }
                Glide.with(this).load(Uri.parse(value)).into(image)
                wrap.addView(image, LinearLayout.LayoutParams(dp(76), dp(76)))
                wrap.addView(TextView(this).apply { text = "图${index + 1}"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#8A7F90")) }, LinearLayout.LayoutParams(dp(76), dp(24)))
                row.addView(wrap, LinearLayout.LayoutParams(dp(84), dp(104)))
            }
            hsv.addView(row); info.addView(hsv)
        }
        scrollBody.addView(info)
        root.addView(ScrollView(this).apply { addView(scrollBody) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply { setPadding(dp(20), dp(10), dp(20), dp(14)); gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.WHITE) }
        bottom.addView(TextView(this).apply { text = "参考图 ${template.fixedReferenceUris.size} 张"; textSize = 13f; setTextColor(Color.parseColor("#756B7B")) }, LinearLayout.LayoutParams(0, dp(54), 1f))
        bottom.addView(Button(this).apply {
            text = "✦  做同款"; textSize = 17f; setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8F57D8"))
            setOnClickListener {
                startActivity(Intent(this@TemplateDetailActivity, MainActivity::class.java).putExtra("apply_template_id", template.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }, LinearLayout.LayoutParams(dp(180), dp(58)))
        root.addView(bottom)
        setContentView(root)
    }

    override fun onResume() { super.onResume(); if (::template.isInitialized) store.getTemplate(template.id)?.let { if (it != template) recreate() } }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
