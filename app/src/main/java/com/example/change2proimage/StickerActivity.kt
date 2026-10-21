package com.example.change2proimage

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide

class StickerActivity : BaseActivity() {
    private lateinit var workspace: WorkspaceStore
    private lateinit var stickers: StickerStore
    private lateinit var relayStore: RelayStore
    private lateinit var taskStore: GenerationTaskStore
    private lateinit var grid: LinearLayout
    private lateinit var ocText: TextView
    private lateinit var relaySpinner: Spinner
    private lateinit var modelSpinner: Spinner
    private lateinit var taskSummary: TextView
    private var oc: OcProfile? = null
    private val directReferenceUris = mutableListOf<String>()
    private val pickDirectReferences = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MainActivity.MAX_REFERENCE_IMAGES)) { uris ->
        directReferenceUris.clear(); directReferenceUris.addAll(uris.map { it.toString() }); oc = null
        if (::ocText.isInitialized) ocText.text = if (directReferenceUris.isEmpty()) "尚未选择人物" else "直接参考图 · ${directReferenceUris.size} 张"
    }
    private var relays = emptyList<RelayConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspace = WorkspaceStore(this)
        stickers = StickerStore(this)
        relayStore = RelayStore(this)
        taskStore = GenerationTaskStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(18))
            setBackgroundColor(Color.parseColor("#FFF8FC"))
        }
        root.addView(buildHeader())
        root.addView(TextView(this).apply {
            text = "模板图是图一；可选择 OC，也可直接添加人物参考图作为图二及后续参考图。"
            setTextColor(Color.parseColor("#786F84")); textSize = 14f
            setPadding(dp(4), dp(6), dp(4), dp(12))
        })
        root.addView(buildControlCard())
        root.addView(buildActionRow())
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadRelays()
    }

    override fun onResume() { super.onResume(); render(); renderTaskSummary() }

    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@StickerActivity).apply {
            text = "‹"; textSize = 34f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#211B2E")); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        addView(TextView(this@StickerActivity).apply {
            text = "表情包工作台"; textSize = 24f; setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(TextView(this@StickerActivity).apply {
            text = "任务详情"; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6A54C9"))
            setOnClickListener { startActivity(Intent(this@StickerActivity, StickerTaskActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(76), dp(48)))
    }

    private fun buildControlCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = cardBg()
        addView(label("人物 / OC"))
        val ocRow = LinearLayout(this@StickerActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ocText = TextView(this@StickerActivity).apply { text = "尚未选择人物"; textSize = 16f }
        ocRow.addView(ocText, LinearLayout.LayoutParams(0, dp(48), 1f))
        ocRow.addView(secondaryButton("OC") { chooseOc() }, LinearLayout.LayoutParams(dp(70), dp(44)))
        ocRow.addView(secondaryButton("参考图") { pickDirectReferences.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, LinearLayout.LayoutParams(dp(88), dp(44)).apply { marginStart = dp(6) })
        addView(ocRow)
        addView(label("中转站与模型"))
        relaySpinner = Spinner(this@StickerActivity)
        modelSpinner = Spinner(this@StickerActivity)
        addView(relaySpinner, LinearLayout.LayoutParams(-1, dp(48)))
        addView(modelSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        taskSummary = TextView(this@StickerActivity).apply {
            textSize = 14f; setTextColor(Color.parseColor("#786F84")); setPadding(0, dp(10), 0, 0)
        }
        addView(taskSummary)
    }

    private fun buildActionRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8))
        addView(secondaryButton("＋ 添加模板") {
            startActivity(Intent(this@StickerActivity, StickerItemEditActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        addView(primaryButton("生成整套") { generateItems(stickers.getItems()) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(10) })
    }

    private fun loadRelays() {
        relays = relayStore.getAll()
        relaySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, relays.map { it.name })
        relaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val relay = relays.getOrNull(position) ?: return
                val models = (relay.cachedModels + relay.defaultModel).filter { it.isNotBlank() }.distinct()
                modelSpinner.adapter = ArrayAdapter(this@StickerActivity, android.R.layout.simple_spinner_dropdown_item, models)
            }
        }
    }

    private fun render() {
        grid.removeAllViews()
        val items = stickers.getItems()
        if (items.isEmpty()) {
            grid.addView(TextView(this).apply {
                text = "还没有表情模板\n添加一张现有表情图作为模板后，就能在这里批量替换人物。"
                gravity = Gravity.CENTER; setPadding(0, dp(52), 0, 0); setTextColor(Color.parseColor("#786F84"))
            }); return
        }
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { item -> row.addView(itemCard(item), LinearLayout.LayoutParams(0, dp(248), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }) }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(row)
        }
    }

    private fun itemCard(item: StickerTemplateItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(9), dp(9), dp(9), dp(9)); background = cardBg()
        val image = ImageView(this@StickerActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        Glide.with(this@StickerActivity).load(item.imageUri).into(image)
        addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(TextView(this@StickerActivity).apply { text = item.name; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setPadding(dp(2), dp(7), 0, 0) })
        addView(TextView(this@StickerActivity).apply {
            text = "${item.resolutionLevel} · ${item.aspectRatio}${if (item.transparentBackground) " · 透明" else ""}"
            textSize = 12f; setTextColor(Color.parseColor("#786F84")); setPadding(dp(2), dp(2), 0, dp(5))
        })
        val actions = LinearLayout(this@StickerActivity).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(secondaryButton("编辑") { startActivity(Intent(this@StickerActivity, StickerItemEditActivity::class.java).putExtra("item_id", item.id)) }, LinearLayout.LayoutParams(0, dp(40), 1f))
        actions.addView(primaryButton("生成") { generateItems(listOf(item)) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        addView(actions)
    }

    private fun chooseOc() {
        val all = workspace.getOcs()
        if (all.isEmpty()) { Toast.makeText(this, "请先创建 OC", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("选择人物 / OC").setItems(all.map { it.name }.toTypedArray()) { _, i ->
            oc = all[i]; directReferenceUris.clear(); ocText.text = "${all[i].name} · ${all[i].referenceUris.size} 张人物参考图"
        }.show()
    }

    private fun generateItems(items: List<StickerTemplateItem>) {
        val personRefs = oc?.referenceUris ?: directReferenceUris
        val personName = oc?.name ?: if (directReferenceUris.isNotEmpty()) "直接参考图" else ""
        val personId = oc?.id ?: "direct_${System.currentTimeMillis()}"
        if (personRefs.isEmpty()) { Toast.makeText(this, "请选择 OC 或添加人物参考图", Toast.LENGTH_SHORT).show(); return }
        if (items.isEmpty()) { Toast.makeText(this, "还没有表情模板", Toast.LENGTH_SHORT).show(); return }
        val relay = relays.getOrNull(relaySpinner.selectedItemPosition) ?: run { Toast.makeText(this, "请先配置中转站", Toast.LENGTH_SHORT).show(); return }
        val model = modelSpinner.selectedItem?.toString().orEmpty()
        if (model.isBlank()) { Toast.makeText(this, "当前中转站没有可用模型，请先在主生图页读取模型", Toast.LENGTH_LONG).show(); return }
        enqueue(personId, personName, personRefs, items, relay, model)
    }

    private fun enqueue(personId: String, personName: String, personRefs: List<String>, items: List<StickerTemplateItem>, relay: RelayConfig, model: String) {
        val ids = items.map { item ->
            val task = GenerationTask(
                sessionId = "sticker_$personId", prompt = item.prompt,
                referenceUris = (listOf(item.imageUri) + personRefs).distinct().take(MainActivity.MAX_REFERENCE_IMAGES),
                relayId = relay.id, relayName = relay.name, model = model,
                resolutionLevel = item.resolutionLevel, aspectRatio = item.aspectRatio,
                transparentBackground = item.transparentBackground
            )
            taskStore.upsert(task); task.id
        }
        val batch = StickerQueueBatch(ocId = personId, ocName = personName, relayId = relay.id, relayName = relay.name, model = model, itemIds = items.map { it.id }, generationTaskIds = ids)
        stickers.saveBatch(batch)
        ContextCompat.startForegroundService(this, Intent(this, StickerQueueService::class.java).putExtra("batch_id", batch.id))
        renderTaskSummary()
        Toast.makeText(this, "已加入 ${items.size} 张表情生成任务", Toast.LENGTH_SHORT).show()
    }

    private fun renderTaskSummary() {
        if (!::taskSummary.isInitialized) return
        val batch = stickers.getBatches().firstOrNull()
        if (batch == null) { taskSummary.text = "当前没有表情包任务"; return }
        val tasks = batch.generationTaskIds.mapNotNull(taskStore::get)
        val success = tasks.count { it.status == GenerationTask.STATUS_SUCCESS }
        val failed = tasks.count { it.status == GenerationTask.STATUS_FAILED }
        val active = tasks.count { it.isRunning() }
        taskSummary.text = "最近任务：${batch.ocName} · $success/${tasks.size} 完成${if (active > 0) " · $active 进行中" else ""}${if (failed > 0) " · $failed 失败" else ""}"
    }

    private fun label(text: String) = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#786F84")); setPadding(0, dp(7), 0, dp(3)) }
    private fun primaryButton(text: String, action: () -> Unit) = Button(this).apply { this.text = text; backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6A54C9")); setTextColor(Color.WHITE); setOnClickListener { action() } }
    private fun secondaryButton(text: String, action: () -> Unit) = Button(this).apply { this.text = text; backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0EBFF")); setTextColor(Color.parseColor("#5F46B8")); setOnClickListener { action() } }
    private fun cardBg() = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.parseColor("#EADFEB")) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
