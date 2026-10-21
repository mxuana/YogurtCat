package com.example.change2proimage

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

class TemplateEditActivity : BaseActivity() {
    private lateinit var store: WorkspaceStore
    private lateinit var nameEdit: EditText
    private lateinit var promptEdit: EditText
    private lateinit var refsInfo: TextView
    private lateinit var resolution: Spinner
    private lateinit var ratio: Spinner
    private lateinit var transparent: Switch
    private val refs = mutableListOf<Uri>()
    private var templateId: String? = null
    private var coverUri: Uri? = null

    private val coverPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; coverUri = uri; updateRefsInfo() }
    }

    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { uri ->
            if (refs.none { it.toString() == uri.toString() } && refs.size < 10) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                refs += uri
            }
        }
        updateRefsInfo()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WorkspaceStore(this)
        templateId = intent.getStringExtra("template_id")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(28))
            setBackgroundColor(Color.parseColor("#FFF8FC"))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(Button(this).apply { text = "返回"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(96), dp(52)))
        top.addView(TextView(this).apply {
            text = if (templateId == null) "新建模板" else "编辑模板"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        content.addView(top)

        content.addView(label("模板名称"))
        nameEdit = EditText(this).apply { hint = "例如：透明表情包"; setSingleLine(true) }
        content.addView(nameEdit, full(dp(58)))

        content.addView(label("固定提示词"))
        promptEdit = EditText(this).apply {
            hint = "保存模板固定使用的整段提示词"
            minLines = 6
            gravity = Gravity.TOP
        }
        content.addView(promptEdit, full(dp(180)))

        content.addView(label("模板展示图"))
        content.addView(Button(this).apply { text = "选择作品封面 / 效果图"; setOnClickListener { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }, full(dp(52)))

        content.addView(label("固定参考图（可选）"))
        refsInfo = TextView(this).apply { textSize = 14f; setTextColor(Color.parseColor("#6E6677")); setPadding(0, dp(6), 0, dp(8)) }
        content.addView(refsInfo)
        val refRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        refRow.addView(Button(this).apply {
            text = "+ 添加固定参考图"
            setOnClickListener { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        refRow.addView(Button(this).apply { text = "清空"; setOnClickListener { refs.clear(); updateRefsInfo() } }, LinearLayout.LayoutParams(dp(100), dp(54)))
        content.addView(refRow)

        content.addView(label("输出参数"))
        val sizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resolution = Spinner(this).also { it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ImageSizeResolver.resolutions) }
        ratio = Spinner(this).also { it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ImageSizeResolver.ratios) }
        sizeRow.addView(resolution, LinearLayout.LayoutParams(0, dp(58), 1f))
        sizeRow.addView(ratio, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(10) })
        content.addView(sizeRow)

        transparent = Switch(this).apply {
            text = "透明背景"
            textSize = 16f
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(transparent)
        content.addView(TextView(this).apply {
            text = "开启后会请求模型输出透明背景，并在提示词中加入 Alpha 透明要求。是否真正支持取决于当前中转站和模型。"
            textSize = 12f
            setTextColor(Color.parseColor("#7B7383"))
            setPadding(0, 0, 0, dp(16))
        })

        content.addView(Button(this).apply { text = "保存模板"; setOnClickListener { save() } }, full(dp(58)))
        setContentView(ScrollView(this).apply { addView(content) })

        templateId?.let { id -> store.getTemplate(id)?.let { load(it) } }
        updateRefsInfo()
    }

    private fun load(t: GenerationTemplate) {
        nameEdit.setText(t.name)
        promptEdit.setText(t.prompt)
        coverUri = t.coverUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        refs.clear(); t.fixedReferenceUris.mapTo(refs) { Uri.parse(it) }
        resolution.setSelection(ImageSizeResolver.resolutions.indexOf(t.resolutionLevel).coerceAtLeast(0))
        ratio.setSelection(ImageSizeResolver.ratios.indexOf(t.aspectRatio).coerceAtLeast(0))
        transparent.isChecked = t.transparentBackground
    }

    private fun save() {
        val name = nameEdit.text.toString().trim()
        val prompt = promptEdit.text.toString().trim()
        if (name.isBlank()) { nameEdit.error = "请填写模板名称"; return }
        if (prompt.isBlank()) { promptEdit.error = "请填写固定提示词"; return }
        val old = templateId?.let(store::getTemplate)
        val template = GenerationTemplate(
            id = old?.id ?: java.util.UUID.randomUUID().toString(),
            name = name,
            prompt = prompt,
            coverUri = coverUri?.toString().orEmpty(),
            fixedReferenceUris = refs.map { it.toString() },
            resolutionLevel = resolution.selectedItem?.toString() ?: "1K",
            aspectRatio = ratio.selectedItem?.toString() ?: "1:1",
            transparentBackground = transparent.isChecked,
            updatedAt = System.currentTimeMillis()
        )
        store.saveTemplate(template)
        Toast.makeText(this, "模板已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateRefsInfo() { refsInfo.text = (if (coverUri == null) "未设置展示图 · " else "已设置展示图 · ") + if (refs.isEmpty()) "没有固定参考图；使用模板时只需上传人物参考图" else "已保存 ${refs.size} 张固定参考图" }
    private fun label(text: String) = TextView(this).apply { this.text = text; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(18), 0, dp(6)) }
    private fun full(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
