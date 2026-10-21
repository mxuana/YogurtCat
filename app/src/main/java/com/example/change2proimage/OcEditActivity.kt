package com.example.change2proimage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import java.util.UUID

class OcEditActivity : BaseActivity() {
    private lateinit var store: WorkspaceStore
    private lateinit var nameEdit: EditText
    private lateinit var promptEdit: EditText
    private lateinit var refsContainer: LinearLayout
    private val refs = mutableListOf<Uri>()
    private var ocId: String = ""

    private val picker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MainActivity.MAX_REFERENCE_IMAGES)
    ) { uris ->
        uris.forEach { uri ->
            if (refs.size >= MainActivity.MAX_REFERENCE_IMAGES || refs.any { it.toString() == uri.toString() }) return@forEach
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            refs += uri
        }
        renderRefs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oc_edit)
        store = WorkspaceStore(this)
        nameEdit = findViewById(R.id.ocName)
        promptEdit = findViewById(R.id.ocPrompt)
        refsContainer = findViewById(R.id.ocRefs)
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        findViewById<Button>(R.id.addOcRefs).setOnClickListener {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.clearOcRefs).setOnClickListener { refs.clear(); renderRefs() }
        findViewById<Button>(R.id.saveOc).setOnClickListener { save() }
        ocId = intent.getStringExtra("oc_id").orEmpty()
        if (ocId.isNotBlank()) store.getOc(ocId)?.let { load(it) }
    }

    private fun load(oc: OcProfile) {
        nameEdit.setText(oc.name)
        promptEdit.setText(oc.prompt)
        refs.clear()
        oc.referenceUris.mapTo(refs) { Uri.parse(it) }
        renderRefs()
    }

    private fun renderRefs() {
        refsContainer.removeAllViews()
        refs.toList().forEach { uri ->
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(92), dp(92)).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "OC 参考图，点击移除"
                setOnClickListener { refs.remove(uri); renderRefs() }
            }
            Glide.with(this).load(uri).into(image)
            refsContainer.addView(image)
        }
    }

    private fun save() {
        val name = nameEdit.text.toString().trim()
        val prompt = promptEdit.text.toString().trim()
        if (name.isBlank()) { nameEdit.error = "请填写 OC 名称"; return }
        if (prompt.isBlank()) { promptEdit.error = "请填写人物提示词"; return }
        store.saveOc(
            OcProfile(
                id = ocId.ifBlank { UUID.randomUUID().toString() },
                name = name,
                prompt = prompt,
                referenceUris = refs.map { it.toString() }
            )
        )
        Toast.makeText(this, "OC 已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
