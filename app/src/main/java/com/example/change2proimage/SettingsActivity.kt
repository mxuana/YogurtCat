package com.example.change2proimage

import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SettingsActivity : BaseActivity() {
    private lateinit var store: RelayStore
    private lateinit var container: LinearLayout
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = RelayStore(this); container = findViewById(R.id.relaysContainer)
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        findViewById<Button>(R.id.addRelay).setOnClickListener { showRelayDialog(null) }
        findViewById<Button>(R.id.openUiEditor).setOnClickListener { startActivity(Intent(this, UiEditorActivity::class.java)) }
        findViewById<Button>(R.id.openOc).setOnClickListener { startActivity(Intent(this, OcListActivity::class.java)) }
        findViewById<Button>(R.id.openTemplates).setOnClickListener { startActivity(Intent(this, TemplateListActivity::class.java)) }
        findViewById<TextView>(R.id.versionInfo).text = "当前版本：${BuildConfig.VERSION_NAME}"
        findViewById<Button>(R.id.checkUpdate).setOnClickListener { UpdateManager(this).checkForUpdate(silent = false, force = true) }
        setupCollapsibleSections()
        renderRelays()
    }

    override fun onResume() { super.onResume(); if (::container.isInitialized) renderRelays() }

    private fun setupCollapsibleSections() {
        val prefs = getSharedPreferences("settings_sections", MODE_PRIVATE)
        fun bind(toggleId: Int, sectionId: Int, key: String, defaultOpen: Boolean, title: String) {
            val toggle = findViewById<TextView>(toggleId)
            val section = findViewById<View>(sectionId)
            fun apply(open: Boolean) {
                section.visibility = if (open) View.VISIBLE else View.GONE
                toggle.text = title + if (open) "                                      ⌄" else "                                      ›"
            }
            var open = prefs.getBoolean(key, defaultOpen)
            apply(open)
            toggle.setOnClickListener { open = !open; prefs.edit().putBoolean(key, open).apply(); apply(open) }
        }
        bind(R.id.toggleWorkspace, R.id.workspaceSection, "workspace", true, "工作区")
        bind(R.id.toggleRelays, R.id.relaySection, "relays", true, "中转站")
        bind(R.id.toggleApp, R.id.appSection, "app", false, "应用")
    }

    private fun renderRelays() {
        container.removeAllViews()
        store.getAll().forEach { relay ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.parseColor("#E8DCEF")); setColor(Color.WHITE) }
                elevation = dp(2).toFloat()
            }
            val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            titleRow.addView(TextView(this).apply { text = relay.name; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
            titleRow.addView(TextView(this).apply {
                text = "●  " + when (relay.modelCheckStatus) { "ok" -> "模型可用"; "error" -> "读取失败"; "checking" -> "检测中"; else -> "未检测" }
                textSize = 12f
                setTextColor(when (relay.modelCheckStatus) { "ok" -> 0xFF2E9B63.toInt(); "error" -> 0xFFD24B4B.toInt(); "checking" -> 0xFFD49A28.toInt(); else -> 0xFF8A8490.toInt() })
                setOnClickListener { checkRelayModels(relay) }
            })
            card.addView(titleRow)
            card.addView(TextView(this).apply {
                text = buildString {
                    append(relay.baseUrl.ifBlank { "未设置 API 主机" })
                    append("\nAPI Key：${if (relay.apiKey.isBlank()) "未设置" else "已设置"}")
                    append("\n默认模型：${relay.defaultModel.ifBlank { relay.cachedModels.firstOrNull() ?: "自动读取" }}")
                    if (relay.cachedModels.isNotEmpty()) append("\n已缓存模型：${relay.cachedModels.size} 个")
                    append("\n模型检测：${relay.modelCheckMessage}")
                    append("\n流式响应：${if (relay.streamEnabled) "开启" else "关闭"}")
                }
                textSize = 13f; setPadding(0, dp(6), 0, dp(6))
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.END }
            fun action(textValue: String, destructive: Boolean = false, click: () -> Unit) = Button(this).apply {
                text = textValue; textSize = 13f; isAllCaps = false; minWidth = 0; minimumWidth = 0
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F6F1FA"))
                setTextColor(Color.parseColor(if (destructive) "#B64A63" else "#5F4A72"))
                setOnClickListener { click() }
            }
            row.addView(action("检测") { checkRelayModels(relay) }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(action("编辑") { showRelayDialog(relay) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
            row.addView(action("删除", true) { confirmDelete(relay) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
            card.addView(row)
            container.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
    }

    private fun showRelayDialog(existing: RelayConfig?) {
        val view = layoutInflater.inflate(R.layout.dialog_relay, null)
        val name = view.findViewById<EditText>(R.id.relayName)
        val baseUrl = view.findViewById<EditText>(R.id.baseUrl)
        val apiKey = view.findViewById<EditText>(R.id.apiKey)
        val defaultModel = view.findViewById<EditText>(R.id.defaultModel)
        val modelsPath = view.findViewById<EditText>(R.id.modelsPath)
        val generationsPath = view.findViewById<EditText>(R.id.generationsPath)
        val editsPath = view.findViewById<EditText>(R.id.editsPath)
        val imageField = view.findViewById<EditText>(R.id.imageField)
        val sizeMode = view.findViewById<Spinner>(R.id.sizeMode)
        val streamEnabled = view.findViewById<Switch>(R.id.streamEnabled)
        val advanced = view.findViewById<LinearLayout>(R.id.advancedContainer)
        val toggleAdvanced = view.findViewById<Button>(R.id.toggleAdvanced)
        val test = view.findViewById<Button>(R.id.testRelay)
        val testResult = view.findViewById<TextView>(R.id.testResult)

        val modeLabels = listOf("size 字符串（1024x1024）", "width + height", "不发送尺寸参数")
        val modeValues = listOf("size", "width_height", "none")
        sizeMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        apiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        if (existing != null) {
            name.setText(existing.name); baseUrl.setText(existing.baseUrl); apiKey.setText(existing.apiKey)
            defaultModel.setText(existing.defaultModel); modelsPath.setText(existing.modelsPath)
            generationsPath.setText(existing.generationsPath); editsPath.setText(existing.editsPath)
            imageField.setText(existing.imageField); streamEnabled.isChecked = existing.streamEnabled; sizeMode.setSelection(modeValues.indexOf(existing.sizeMode).coerceAtLeast(0))
        } else {
            modelsPath.setText("/v1/models"); generationsPath.setText("/v1/images/generations")
            editsPath.setText("/v1/images/edits"); imageField.setText("image")
        }

        toggleAdvanced.setOnClickListener {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            toggleAdvanced.text = if (advanced.visibility == View.VISIBLE) "收起高级设置" else "高级设置（通常不用管）"
        }

        var fetchedModels = existing?.cachedModels ?: emptyList()
        test.setOnClickListener {
            val base = baseUrl.text.toString().trim().trimEnd('/')
            if (!base.startsWith("https://") && !base.startsWith("http://")) { baseUrl.error = "请输入完整 API 主机"; return@setOnClickListener }
            val path = normalizePath(modelsPath.text.toString(), "/v1/models")
            test.isEnabled = false; testResult.text = "正在检查…"
            val b = Request.Builder().url(joinUrl(base, path)).get()
            val key = apiKey.text.toString().trim(); if (key.isNotBlank()) b.header("Authorization", "Bearer $key")
            client.newCall(b.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = runOnUiThread { test.isEnabled = true; testResult.text = "连接失败：${e.message}" }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val text = it.body?.string().orEmpty()
                        if (!it.isSuccessful) { runOnUiThread { test.isEnabled = true; testResult.text = "HTTP ${it.code}: ${text.take(300)}" }; return }
                        val models = runCatching {
                            val arr = JSONObject(text).optJSONArray("data")
                            buildList { if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("id")?.takeIf { id -> id.isNotBlank() }?.let { id -> add(id) } }
                        }.getOrDefault(emptyList())
                        fetchedModels = models
                        runOnUiThread {
                            test.isEnabled = true
                            testResult.text = if (models.isEmpty()) "连接成功，但模型列表为空" else "连接成功，读取到 ${models.size} 个模型：\n${models.take(8).joinToString("\n")}" + if (models.size > 8) "\n…" else ""
                            if (defaultModel.text.toString().trim().isBlank() && models.isNotEmpty()) defaultModel.setText(models.first())
                        }
                    }
                }
            })
        }

        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "新增中转站" else "编辑中转站")
            .setView(view).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val relayName = name.text.toString().trim(); val relayBase = baseUrl.text.toString().trim().trimEnd('/')
                if (relayName.isBlank()) { name.error = "请填写名称"; return@setOnClickListener }
                if (!relayBase.startsWith("https://") && !relayBase.startsWith("http://")) { baseUrl.error = "请输入完整 http:// 或 https:// 地址"; return@setOnClickListener }
                store.upsert(RelayConfig(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(), name = relayName, baseUrl = relayBase,
                    apiKey = apiKey.text.toString().trim(), modelsPath = normalizePath(modelsPath.text.toString(), "/v1/models"),
                    generationsPath = normalizePath(generationsPath.text.toString(), "/v1/images/generations"),
                    editsPath = normalizePath(editsPath.text.toString(), "/v1/images/edits"),
                    defaultModel = defaultModel.text.toString().trim(), imageField = imageField.text.toString().trim().ifBlank { "image" },
                    sizeMode = modeValues[sizeMode.selectedItemPosition], cachedModels = if (fetchedModels.isNotEmpty()) fetchedModels else existing?.cachedModels ?: emptyList(),
                    streamEnabled = streamEnabled.isChecked, modelCheckStatus = existing?.modelCheckStatus ?: "unknown",
                    modelCheckMessage = existing?.modelCheckMessage ?: "尚未检测", modelCheckedAt = existing?.modelCheckedAt ?: 0L
                ))
                renderRelays(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun checkRelayModels(relay: RelayConfig) {
        store.updateModelCheck(relay.id, "checking", "正在读取模型…")
        renderRelays()
        val request = Request.Builder().url(joinUrl(relay.baseUrl, relay.modelsPath)).get().apply {
            if (relay.apiKey.isNotBlank()) header("Authorization", "Bearer ${relay.apiKey}")
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = runOnUiThread {
                store.updateModelCheck(relay.id, "error", "连接失败：${e.message.orEmpty().take(80)}"); renderRelays()
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val body = r.body?.string().orEmpty()
                    if (!r.isSuccessful) { runOnUiThread { store.updateModelCheck(relay.id, "error", "HTTP ${r.code}"); renderRelays() }; return }
                    val models = runCatching {
                        val a = JSONObject(body).optJSONArray("data")
                        buildList { if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { id -> add(id) } }
                    }.getOrDefault(emptyList())
                    runOnUiThread {
                        if (models.isEmpty()) store.updateModelCheck(relay.id, "error", "连接成功，但没有读取到模型")
                        else store.updateModelCheck(relay.id, "ok", "读取到 ${models.size} 个模型", models)
                        renderRelays()
                    }
                }
            }
        })
    }

    private fun confirmDelete(relay: RelayConfig) {
        AlertDialog.Builder(this).setTitle("删除 ${relay.name}？").setMessage("只删除本机保存的中转站配置。")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.delete(relay.id); renderRelays() }.show()
    }

    private fun normalizePath(value: String, fallback: String): String {
        val v = value.trim().ifBlank { fallback }; return if (v.startsWith('/')) v else "/$v"
    }
    private fun joinUrl(base: String, path: String) = base.trimEnd('/') + "/" + path.trimStart('/')
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
