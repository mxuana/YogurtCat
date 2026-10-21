package com.example.change2proimage

import android.Manifest
import android.content.ContentValues
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity() {
    private val modelClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var relayStore: RelayStore
    private lateinit var workspace: WorkspaceStore
    private lateinit var taskStore: GenerationTaskStore
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerSessionContainer: LinearLayout
    private lateinit var resultCard: View

    private lateinit var sessionTitle: TextView
    private lateinit var promptEdit: EditText
    private lateinit var referenceContainer: LinearLayout
    private lateinit var referenceHint: TextView
    private lateinit var resolutionSpinner: Spinner
    private lateinit var ratioSpinner: Spinner
    private lateinit var sizeHint: TextView
    private lateinit var relaySpinner: Spinner
    private lateinit var modelSpinner: Spinner
    private lateinit var modelStatus: TextView
    private lateinit var generateButton: Button
    private lateinit var saveButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var taskStatusCard: View
    private lateinit var taskStatusText: TextView
    private lateinit var resultImage: ImageView
    private lateinit var historyContainer: LinearLayout
    private lateinit var chatScroll: ScrollView

    private var relays = mutableListOf<RelayConfig>()
    private var currentRelay: RelayConfig? = null
    private var currentSession: ImageSession? = null
    private var suppressRelayCallback = false
    private var desiredModel: String? = null
    private val referenceUris = mutableListOf<Uri>()
    private var currentResultPath: String? = null
    private var lastObservedTaskUpdate = -1L

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshTaskUi()
            handler.postDelayed(this, TASK_POLL_MS)
        }
    }

    private val pickReferences = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_REFERENCE_IMAGES)
    ) { uris -> addReferenceUris(uris) }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val historyDetailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getStringExtra("history_id") ?: return@registerForActivityResult
            workspace.getHistoryItem(id)?.let { reuseHistory(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        relayStore = RelayStore(this)
        workspace = WorkspaceStore(this)
        taskStore = GenerationTaskStore(this)
        val recoveredTasks = taskStore.recoverInterruptedTasksOnce()
        bindViews()
        setupSizeSelectors()
        setupRelaySelector()
        bindActions()
        loadInitialSession()
        applyPendingOc(intent)
        applyPendingTemplate(intent)
        requestNotificationsIfNeeded()
        if (recoveredTasks > 0) Toast.makeText(this, "$recoveredTasks 个上次中断的任务已安全停止，请手动决定是否重试", Toast.LENGTH_LONG).show()
        UpdateManager(this).checkForUpdate(silent = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPendingOc(intent)
        applyPendingTemplate(intent)
        refreshTaskUi(force = true)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
        if (::workspace.isInitialized) saveCurrentSession()
    }

    override fun onResume() {
        super.onResume()
        if (::relayStore.isInitialized && currentSession != null) reloadRelays(preserveCurrentModel = true)
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        applyUiPreferences()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        drawerSessionContainer = findViewById(R.id.drawerSessionContainer)
        resultCard = findViewById(R.id.resultCard)
        sessionTitle = findViewById(R.id.sessionTitle)
        promptEdit = findViewById(R.id.prompt)
        referenceContainer = findViewById(R.id.referenceContainer)
        referenceHint = findViewById(R.id.referenceHint)
        resolutionSpinner = findViewById(R.id.resolution)
        ratioSpinner = findViewById(R.id.aspectRatio)
        sizeHint = findViewById(R.id.sizeHint)
        relaySpinner = findViewById(R.id.relay)
        modelSpinner = findViewById(R.id.model)
        modelStatus = findViewById(R.id.modelStatus)
        generateButton = findViewById(R.id.generate)
        saveButton = findViewById(R.id.save)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.status)
        taskStatusCard = findViewById(R.id.taskStatusCard)
        taskStatusText = findViewById(R.id.taskStatusText)
        resultImage = findViewById(R.id.image)
        historyContainer = findViewById(R.id.historyContainer)
        chatScroll = findViewById(R.id.chatScroll)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.sessions).setOnClickListener { showSessionChooser() }
        findViewById<Button>(R.id.settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.drawerClose).setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<Button>(R.id.drawerNewSession).setOnClickListener {
            createNewSession(); drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.drawerOc).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, OcListActivity::class.java))
        }
        findViewById<Button>(R.id.drawerTemplates).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, TemplateListActivity::class.java))
        }
        findViewById<Button>(R.id.drawerStickers).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, StickerActivity::class.java))
        }
        findViewById<Button>(R.id.drawerStickerTasks).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, StickerTaskActivity::class.java))
        }
        findViewById<Button>(R.id.drawerSettings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.addReferences).setOnClickListener {
            pickReferences.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.chooseOc).setOnClickListener { showOcChooser() }
        findViewById<Button>(R.id.moreOptions).setOnClickListener { showMoreOptions() }
        findViewById<Button>(R.id.clearReferences).setOnClickListener {
            referenceUris.clear(); renderReferences(); statusText.text = "已清空当前参考图"
        }
        generateButton.setOnClickListener { generate() }
        saveButton.setOnClickListener { saveCurrentImage() }
    }

    private fun setupSizeSelectors() {
        resolutionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ImageSizeResolver.resolutions)
        ratioSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ImageSizeResolver.ratios)
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateSizeHint()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        resolutionSpinner.onItemSelectedListener = listener
        ratioSpinner.onItemSelectedListener = listener
    }

    private fun setupRelaySelector() {
        relaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressRelayCallback || position !in relays.indices) return
                currentRelay = relays[position]
                populateModels(relays[position])
                fetchModels(relays[position])
                updateReferenceHint()
                updateSizeHint()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateSizeHint()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun loadInitialSession() {
        val sessions = workspace.getSessions()
        val id = workspace.getCurrentSessionId()
        val session = sessions.firstOrNull { it.id == id } ?: sessions.first()
        workspace.setCurrentSessionId(session.id)
        loadSession(session)
    }

    private fun loadSession(session: ImageSession) {
        currentSession = session
        lastObservedTaskUpdate = -1L
        sessionTitle.text = session.title.ifBlank { "新建生图" }
        promptEdit.setText(session.prompt)
        referenceUris.clear()
        session.referenceUris.mapNotNullTo(referenceUris) { runCatching { Uri.parse(it) }.getOrNull() }
        renderReferences()
        resolutionSpinner.setSelection(ImageSizeResolver.resolutions.indexOf(session.resolutionLevel).coerceAtLeast(0))
        ratioSpinner.setSelection(ImageSizeResolver.ratios.indexOf(session.aspectRatio).let { if (it >= 0) it else 1 })
        desiredModel = session.model.takeIf { it.isNotBlank() }
        reloadRelays(targetRelayId = session.relayId, preserveCurrentModel = false)
        renderHistory()
        displayLatestResult()
        statusText.text = ""
        updateTemplateHint()
        refreshTaskUi(force = true)
    }

    private fun saveCurrentSession() {
        val old = currentSession ?: return
        val prompt = promptEdit.text.toString()
        val title = makeTitle(prompt, old.title)
        val updated = old.copy(
            title = title,
            prompt = prompt,
            referenceUris = referenceUris.map { it.toString() },
            relayId = currentRelay?.id.orEmpty(),
            model = selectedModel(),
            resolutionLevel = selectedResolution(),
            aspectRatio = selectedRatio(),
            updatedAt = System.currentTimeMillis()
        )
        currentSession = updated
        sessionTitle.text = updated.title
        workspace.saveSession(updated)
        workspace.setCurrentSessionId(updated.id)
    }

    private fun makeTitle(prompt: String, existing: String): String {
        val clean = prompt.replace("\n", " ").trim()
        if (clean.isNotBlank()) return if (clean.length <= 18) clean else clean.take(18) + "…"
        return existing.ifBlank { "新建生图" }
    }

    private fun createNewSession() {
        saveCurrentSession()
        loadSession(workspace.createSession())
    }

    private fun showSessionChooser() {
        saveCurrentSession()
        renderSessionDrawer()
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun renderSessionDrawer() {
        drawerSessionContainer.removeAllViews()
        val currentId = currentSession?.id
        workspace.getSessions().forEach { session ->
            val selected = session.id == currentId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(12), 0)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.parseColor(if (selected) "#F1ECFF" else "#00FFFFFF"))
                }
                setOnClickListener {
                    saveCurrentSession()
                    workspace.setCurrentSessionId(session.id)
                    loadSession(session)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            row.addView(TextView(this).apply {
                text = if (selected) "●" else "○"
                textSize = 12f
                setTextColor(Color.parseColor(if (selected) "#6B52C7" else "#C1BACD"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.MATCH_PARENT)
            })
            row.addView(TextView(this).apply {
                text = session.title.ifBlank { "新建生图" }
                textSize = 15f
                setTextColor(Color.parseColor("#211B2E"))
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(8) }
            })
            row.addView(TextView(this).apply {
                text = "删除"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#B64A63"))
                setPadding(dp(8), 0, dp(4), 0)
                layoutParams = LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.MATCH_PARENT)
                setOnClickListener { confirmDeleteSession(session) }
            })
            row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(6) }
            drawerSessionContainer.addView(row)
        }
    }

    private fun confirmDeleteSession(session: ImageSession) {
        AlertDialog.Builder(this)
            .setTitle("删除会话？")
            .setMessage("将删除「${session.title.ifBlank { "新建生图" }}」以及它在本机保存的会话信息。历史图片记录也会一并删除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val deletingCurrent = session.id == currentSession?.id
                workspace.deleteSession(session.id, deleteHistory = true)
                if (deletingCurrent) loadInitialSession() else renderSessionDrawer()
            }
            .show()
    }

    private fun reloadRelays(targetRelayId: String? = null, preserveCurrentModel: Boolean = true) {
        val wantedRelay = targetRelayId?.takeIf { it.isNotBlank() } ?: currentRelay?.id
        if (preserveCurrentModel && desiredModel.isNullOrBlank()) desiredModel = selectedModel().takeIf { it.isNotBlank() }
        relays = relayStore.getAll()
        if (relays.isEmpty()) return
        val index = relays.indexOfFirst { it.id == wantedRelay }.let { if (it >= 0) it else 0 }
        suppressRelayCallback = true
        relaySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, relays.map { it.name })
        relaySpinner.setSelection(index)
        suppressRelayCallback = false
        currentRelay = relays[index]
        populateModels(relays[index])
        fetchModels(relays[index])
        updateReferenceHint()
        updateSizeHint()
    }

    private fun populateModels(relay: RelayConfig, fresh: List<String>? = null) {
        val preferred = desiredModel?.takeIf { it.isNotBlank() }
            ?: currentSession?.takeIf { it.relayId == relay.id }?.model?.takeIf { it.isNotBlank() }
            ?: relay.defaultModel
        val models = buildList {
            addAll(fresh.orEmpty()); addAll(relay.cachedModels)
            if (relay.defaultModel.isNotBlank()) add(relay.defaultModel)
        }.filter { it.isNotBlank() }.distinct()
        val visible = if (models.isEmpty()) listOf("请先在设置中配置模型") else models
        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, visible)
        val idx = visible.indexOf(preferred).let { if (it >= 0) it else 0 }
        modelSpinner.setSelection(idx)
        desiredModel = null
        updateSizeHint()
    }

    private fun fetchModels(relay: RelayConfig) {
        if (relay.baseUrl.isBlank() || relay.modelsPath.isBlank()) return
        modelStatus.text = "正在读取 ${relay.name} 的模型…"
        val builder = Request.Builder().url(joinUrl(relay.baseUrl, relay.modelsPath)).get()
        if (relay.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${relay.apiKey}")
        modelClient.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = runOnUiThread {
                if (currentRelay?.id == relay.id) modelStatus.text = "模型读取失败，使用缓存模型：${e.message}"
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        runOnUiThread { if (currentRelay?.id == relay.id) modelStatus.text = "模型接口 HTTP ${it.code}，使用缓存模型" }
                        return
                    }
                    val models = runCatching {
                        val data = JSONObject(text).optJSONArray("data")
                        buildList {
                            if (data != null) for (i in 0 until data.length()) {
                                data.optJSONObject(i)?.optString("id")?.takeIf { id -> id.isNotBlank() }?.let { id -> add(id) }
                            }
                        }
                    }.getOrDefault(emptyList())
                    if (models.isNotEmpty()) relayStore.updateCachedModels(relay.id, models)
                    runOnUiThread {
                        if (currentRelay?.id == relay.id) {
                            desiredModel = selectedModel().takeIf { model -> model.isNotBlank() && !model.startsWith("请先") }
                            populateModels(relay.copy(cachedModels = models.ifEmpty { relay.cachedModels }), models)
                            modelStatus.text = if (models.isEmpty()) "未读取到模型，使用缓存/默认模型" else "已读取 ${models.size} 个模型"
                        }
                    }
                }
            }
        })
    }

    private fun applyPendingOc(source: Intent) {
        val id = source.getStringExtra("apply_oc_id") ?: return
        source.removeExtra("apply_oc_id")
        workspace.getOc(id)?.let { applyOc(it) }
    }

    private fun applyPendingTemplate(source: Intent) {
        val id = source.getStringExtra("apply_template_id") ?: return
        source.removeExtra("apply_template_id")
        workspace.getTemplate(id)?.let { applyTemplate(it) }
    }

    private fun applyTemplate(template: GenerationTemplate) {
        promptEdit.setText(template.prompt)
        promptEdit.setSelection(promptEdit.text.length)
        referenceUris.clear()
        addReferenceUris(template.fixedReferenceUris.map { Uri.parse(it) }, announce = false)
        resolutionSpinner.setSelection(ImageSizeResolver.resolutions.indexOf(template.resolutionLevel).coerceAtLeast(0))
        ratioSpinner.setSelection(ImageSizeResolver.ratios.indexOf(template.aspectRatio).let { if (it >= 0) it else 0 })
        currentSession = currentSession?.copy(
            templateId = template.id,
            templateName = template.name,
            transparentBackground = template.transparentBackground
        )
        updateTemplateHint()
        statusText.text = if (template.transparentBackground) {
            "已应用模板「${template.name}」· 透明背景。现在只需添加人物参考图即可生成。"
        } else {
            "已应用模板「${template.name}」。现在可添加人物参考图后生成。"
        }
        saveCurrentSession()
    }

    private fun updateTemplateHint() {
        // 模板状态已经合并进紧凑创作界面，不再使用旧的 templateHint View。
        // 保留此方法，避免影响现有 applyTemplate()/会话恢复调用链。
    }

    private fun showOcChooser() {
        val ocs = workspace.getOcs()
        if (ocs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("还没有 OC")
                .setMessage("OC 只保存：名称、多张参考图、一整段人物提示词。")
                .setPositiveButton("新建 OC") { _, _ -> startActivity(Intent(this, OcEditActivity::class.java)) }
                .setNegativeButton("取消", null).show()
            return
        }
        val labels = ocs.map { it.name }.toMutableList().apply { add("⚙ 管理 OC") }
        AlertDialog.Builder(this)
            .setTitle("选择 OC")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == ocs.size) startActivity(Intent(this, OcListActivity::class.java)) else applyOc(ocs[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }


    private fun showMoreOptions() {
        val names = relays.map { it.name }.toMutableList().apply { add("中转站设置") }
        AlertDialog.Builder(this)
            .setTitle("更多 · 当前 ${currentRelay?.name ?: "未选择中转站"}")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == relays.size) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else if (which in relays.indices) {
                    currentRelay = relays[which]
                    suppressRelayCallback = true
                    relaySpinner.setSelection(which)
                    suppressRelayCallback = false
                    desiredModel = null
                    populateModels(relays[which])
                    fetchModels(relays[which])
                    saveCurrentSession()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyUiPreferences() {
        val c = UiPreferences(this).load()
        val main = findViewById<LinearLayout>(R.id.mainContent)
        val composer = findViewById<MaterialCardView>(R.id.composerCard)
        main.setPadding(dp(c.pageMarginDp), main.paddingTop, dp(c.pageMarginDp), main.paddingBottom)
        findViewById<View>(R.id.drawerLayout).setBackgroundColor(runCatching { Color.parseColor(c.backgroundColor) }.getOrDefault(Color.WHITE))
        composer.setCardBackgroundColor(runCatching { Color.parseColor(c.cardColor) }.getOrDefault(Color.WHITE))
        composer.radius = dp(c.cardRadiusDp).toFloat()
        val inner = composer.getChildAt(0)
        if (inner is View) inner.setPadding(dp(c.cardPaddingDp), dp(c.cardPaddingDp), dp(c.cardPaddingDp), dp(c.cardPaddingDp))
        promptEdit.minHeight = dp(c.promptHeightDp)
        promptEdit.textSize = 16f * c.textScalePercent / 100f
        generateButton.layoutParams = generateButton.layoutParams.apply { width = dp(c.generateSizeDp); height = dp(c.generateSizeDp) }
        (generateButton as? MaterialButton)?.cornerRadius = dp(c.generateSizeDp / 2)
        generateButton.backgroundTintList = ColorStateList.valueOf(runCatching { Color.parseColor(c.accentColor) }.getOrDefault(Color.parseColor("#7053D6")))
        sessionTitle.visibility = View.GONE
        modelStatus.visibility = if (c.showModelStatus) View.VISIBLE else View.GONE
        sizeHint.visibility = if (c.showSizeHint) View.VISIBLE else View.GONE
        renderReferences()
    }

    private fun applyOc(oc: OcProfile) {
        addReferenceUris(oc.referenceUris.map { Uri.parse(it) }, announce = false)
        val current = promptEdit.text.toString().trim()
        if (oc.prompt.isNotBlank() && !current.contains(oc.prompt)) {
            promptEdit.setText(if (current.isBlank()) oc.prompt else "${oc.prompt}\n\n$current")
            promptEdit.setSelection(promptEdit.text.length)
        }
        statusText.text = "已加入 OC「${oc.name}」的参考图和提示词，可继续修改"
    }

    private fun addReferenceUris(uris: List<Uri>, announce: Boolean = true) {
        var ignored = 0
        for (uri in uris) {
            if (referenceUris.any { it.toString() == uri.toString() }) continue
            if (referenceUris.size >= MAX_REFERENCE_IMAGES) { ignored++; continue }
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            referenceUris += uri
        }
        renderReferences()
        if (announce) statusText.text = if (ignored > 0) "最多 $MAX_REFERENCE_IMAGES 张参考图，已忽略 $ignored 张" else "已选择 ${referenceUris.size} 张参考图"
    }

    private fun renderReferences() {
        referenceContainer.removeAllViews()
        findViewById<View>(R.id.referenceScroll).visibility = if (referenceUris.isEmpty()) View.GONE else View.VISIBLE
        val ui = UiPreferences(this).load()
        referenceUris.forEach { uri ->
            val card = MaterialCardView(this).apply {
                radius = dp(14).toFloat()
                strokeWidth = dp(1)
                strokeColor = Color.parseColor("#ECDFF1")
                cardElevation = 0f
                layoutParams = LinearLayout.LayoutParams(dp(ui.referenceSizeDp), dp(ui.referenceSizeDp)).apply { marginEnd = dp(ui.gapDp) }
            }
            val frame = FrameLayout(this)
            val image = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            val remove = TextView(this).apply {
                text = "×"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#211B2E"))
                background = getDrawable(R.drawable.bg_thumb_delete)
                setOnClickListener {
                    referenceUris.remove(uri); renderReferences(); statusText.text = "已移除一张参考图"
                }
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(5); marginEnd = dp(5)
                }
            }
            Glide.with(this).load(uri).into(image)
            frame.addView(image); frame.addView(remove); card.addView(frame); referenceContainer.addView(card)
        }
        updateReferenceHint()
    }

    private fun updateReferenceHint() {
        referenceHint.text = if (referenceUris.isEmpty()) "未选择参考图" else "已选择 ${referenceUris.size} / $MAX_REFERENCE_IMAGES 张参考图"
    }

    private fun updateSizeHint() {
        val relay = currentRelay ?: return
        val model = selectedModel()
        if (model.isBlank() || model.startsWith("请先")) return
        val plan = ImageSizeResolver.resolve(relay, model, selectedResolution(), selectedRatio())
        sizeHint.text = when {
            plan.requestWidth == null -> "尺寸由模型自动决定"
            plan.adapted -> "目标 ${plan.targetLabel} · 当前模型接口适配为 ${plan.requestLabel}"
            else -> "实际请求尺寸：${plan.requestLabel}"
        }
    }

    private fun generate() {
        val relay = currentRelay ?: run { statusText.text = "请先配置中转站"; return }
        val session = currentSession ?: return
        val prompt = promptEdit.text.toString().trim()
        val model = selectedModel()
        if (prompt.isBlank()) { statusText.text = "请填写提示词"; return }
        if (relay.apiKey.isBlank()) { statusText.text = "请先在设置中填写 ${relay.name} 的 API Key"; return }
        if (model.isBlank() || model.startsWith("请先")) { statusText.text = "请选择可用模型"; return }

        val active = taskStore.getAll().firstOrNull { it.isRunning() }
        if (active != null) {
            statusText.text = if (active.sessionId == session.id) {
                "当前会话已有任务在后台生成，请等待完成"
            } else {
                "已有其他会话的生图任务正在后台运行，完成后再开始新任务"
            }
            return
        }

        saveCurrentSession()
        val task = GenerationTask(
            sessionId = session.id,
            prompt = prompt,
            referenceUris = referenceUris.map { it.toString() },
            relayId = relay.id,
            relayName = relay.name,
            model = model,
            resolutionLevel = selectedResolution(),
            aspectRatio = selectedRatio(),
            transparentBackground = currentSession?.transparentBackground == true
        )
        taskStore.upsert(task)
        lastObservedTaskUpdate = -1L
        ContextCompat.startForegroundService(
            this,
            Intent(this, GenerationService::class.java).putExtra(GenerationService.EXTRA_TASK_ID, task.id)
        )
        setBusy(true, "任务已交给后台生成，切屏不会中断")
        refreshTaskUi(force = true)
    }

    private fun refreshTaskUi(force: Boolean = false) {
        val sessionId = currentSession?.id ?: return
        val task = taskStore.getLatestForSession(sessionId)
        if (task == null) {
            setBusy(false, "")
            renderHistory()
            return
        }
        val stateChanged = force || task.updatedAt != lastObservedTaskUpdate
        if (stateChanged) lastObservedTaskUpdate = task.updatedAt
        setBusy(task.isRunning(), if (task.isRunning()) "生成中…" else "")
        if (task.status == GenerationTask.STATUS_SUCCESS && task.resultPath.isNotBlank()) {
            currentResultPath = task.resultPath
        }
        // 对话流就是任务状态的唯一展示位置；运行中每秒更新等待时间。
        if (stateChanged || task.isRunning()) renderHistory()
    }

    private fun displayLatestResult() {
        val latest = currentSession?.id?.let { id ->
            workspace.getHistory(id).firstOrNull { it.status == "SUCCESS" && it.resultPath.isNotBlank() }
        }
        currentResultPath = latest?.resultPath?.takeIf { File(it).exists() }
        renderHistory()
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        val sessionId = currentSession?.id ?: return
        val items = workspace.getHistory(sessionId).take(40).reversed()
        if (items.isEmpty()) {
            historyContainer.addView(TextView(this).apply {
                text = "上传参考图并输入提示词，开始第一张作品"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#8B8295"))
                setPadding(dp(20), dp(80), dp(20), dp(40))
                textSize = 14f
            })
        } else {
            items.forEach { addHistoryTurn(it) }
        }

        val latestTask = taskStore.getLatestForSession(sessionId)
        if (latestTask != null && latestTask.isRunning()) addRunningTurn(latestTask)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addHistoryTurn(record: HistoryRecord) {
        val turn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(24)
            }
        }
        addUserMessage(turn, record.referenceUris, record.prompt,
            "${record.aspectRatio} · ${record.resolutionLevel} · ${shortModel(record.model)}")

        if (record.status == "SUCCESS" && record.resultPath.isNotBlank() && File(record.resultPath).exists()) {
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.parseColor("#F2EDF5")) }
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)).apply {
                    topMargin = dp(12); marginEnd = dp(34)
                }
                setOnClickListener {
                    historyDetailLauncher.launch(Intent(this@MainActivity, HistoryDetailActivity::class.java).putExtra("history_id", record.id))
                }
            }
            Glide.with(this).load(File(record.resultPath)).into(image)
            turn.addView(image)
            turn.addView(LinearLayout(this).apply {
                gravity = Gravity.END
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(5), dp(34), 0)
                addView(TextView(this@MainActivity).apply {
                    text = if (record.durationMs > 0) "${record.durationMs / 1000}s" else "完成"
                    textSize = 12f; setTextColor(Color.parseColor("#8B8295")); setPadding(dp(8), dp(8), dp(12), dp(8))
                })
                addView(TextView(this@MainActivity).apply {
                    text = "保存"; textSize = 13f; setTextColor(Color.parseColor("#7053D6")); setPadding(dp(12), dp(8), dp(8), dp(8))
                    setOnClickListener { currentResultPath = record.resultPath; saveCurrentImage() }
                })
                addView(TextView(this@MainActivity).apply {
                    text = "复用"; textSize = 13f; setTextColor(Color.parseColor("#7053D6")); setPadding(dp(12), dp(8), dp(8), dp(8))
                    setOnClickListener { reuseHistory(record) }
                })
            })
        } else {
            turn.addView(TextView(this).apply {
                text = "生成失败 · ${record.stage.ifBlank { "请求失败" }}\n${record.errorDetail.ifBlank { "点击查看诊断" }}"
                setTextColor(Color.parseColor("#A33B54")); textSize = 13f
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedDrawable("#FFF1F4", 16)
                setOnClickListener { historyDetailLauncher.launch(Intent(this@MainActivity, HistoryDetailActivity::class.java).putExtra("history_id", record.id)) }
            })
        }
        historyContainer.addView(turn)
    }

    private fun addRunningTurn(task: GenerationTask) {
        val turn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
        }
        addUserMessage(turn, task.referenceUris, task.prompt,
            "${task.aspectRatio} · ${task.resolutionLevel} · ${shortModel(task.model)}")
        val elapsed = ((System.currentTimeMillis() - task.createdAt).coerceAtLeast(0L) / 1000L)
        turn.addView(TextView(this).apply {
            text = "酸奶喵正在生成…  ${elapsed}s\n${task.stage}"
            setTextColor(Color.parseColor("#6E6578")); textSize = 14f
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = roundedDrawable("#F5F0F7", 18)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12); marginEnd = dp(34) }
        })
        historyContainer.addView(turn)
    }

    private fun addUserMessage(parent: LinearLayout, refs: List<String>, prompt: String, meta: String) {
        if (refs.isNotEmpty()) {
            val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            refs.take(4).forEach { raw ->
                val image = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = roundedDrawable("#F2EDF5", 14); clipToOutline = true
                    layoutParams = LinearLayout.LayoutParams(dp(82), dp(82)).apply { marginEnd = dp(7) }
                }
                Glide.with(this).load(Uri.parse(raw)).into(image); strip.addView(image)
            }
            parent.addView(strip)
        }
        parent.addView(TextView(this).apply {
            text = meta; textSize = 11f; setTextColor(Color.parseColor("#8B8295"))
            setPadding(dp(2), if (refs.isEmpty()) 0 else dp(7), dp(2), dp(4))
        })
        parent.addView(TextView(this).apply {
            text = prompt; textSize = 15f; setTextColor(Color.parseColor("#2A2431"))
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = roundedDrawable("#F1EDF4", 18)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END; marginStart = dp(42)
            }
        })
    }

    private fun shortModel(model: String): String = when {
        model.contains("sunburst", true) -> "Sunburst"
        model.contains("flare", true) -> "Flare"
        model.length > 18 -> model.takeLast(18)
        else -> model
    }

    private fun roundedDrawable(color: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color)); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun reuseHistory(record: HistoryRecord) {
        promptEdit.setText(record.prompt)
        referenceUris.clear(); record.referenceUris.mapTo(referenceUris) { Uri.parse(it) }; renderReferences()
        resolutionSpinner.setSelection(ImageSizeResolver.resolutions.indexOf(record.resolutionLevel).coerceAtLeast(0))
        ratioSpinner.setSelection(ImageSizeResolver.ratios.indexOf(record.aspectRatio).let { if (it >= 0) it else 1 })
        desiredModel = record.model
        currentSession = currentSession?.copy(
            templateId = "",
            templateName = "",
            transparentBackground = record.transparentBackground
        )
        reloadRelays(targetRelayId = record.relayId, preserveCurrentModel = false)
        updateTemplateHint()
        statusText.text = if (record.transparentBackground) "已复用历史参数 · 透明背景" else "已一键复用历史参数，可直接修改后再次生成"
        saveCurrentSession()
    }

    private fun selectedModel(): String = modelSpinner.selectedItem?.toString().orEmpty()
    private fun selectedResolution(): String = resolutionSpinner.selectedItem?.toString() ?: "1K"
    private fun selectedRatio(): String = ratioSpinner.selectedItem?.toString() ?: "1:1"

    private fun setBusy(busy: Boolean, text: String) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        generateButton.isEnabled = !busy
        if (text.isNotBlank()) statusText.text = text
    }

    private fun saveCurrentImage() {
        val path = currentResultPath ?: run { statusText.text = "当前没有可保存的图片"; return }
        val file = File(path)
        if (!file.exists()) { statusText.text = "生成图片文件不存在"; return }
        val format = ImageFormatUtil.fromFile(file)
        val name = "酸奶喵_${System.currentTimeMillis()}.${format.extension}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/酸奶喵")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        statusText.text = if (uri != null) "已保存到相册：Pictures/酸奶喵" else "保存失败"
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun joinUrl(base: String, path: String) = base.trimEnd('/') + "/" + path.trimStart('/')
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val MAX_REFERENCE_IMAGES = 10
        private const val TASK_POLL_MS = 1000L
    }
}
