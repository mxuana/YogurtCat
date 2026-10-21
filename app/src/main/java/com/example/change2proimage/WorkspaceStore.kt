package com.example.change2proimage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ImageSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新建生图",
    val prompt: String = "",
    val referenceUris: List<String> = emptyList(),
    val relayId: String = "",
    val model: String = "",
    val resolutionLevel: String = "1K",
    val aspectRatio: String = "1:1",
    val templateId: String = "",
    val templateName: String = "",
    val transparentBackground: Boolean = false,
    // Legacy fields are kept so older V3-V6 data can still be loaded safely.
    val sizeLabel: String = "自动",
    val customWidth: String = "",
    val customHeight: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class OcProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val referenceUris: List<String> = emptyList()
)

data class GenerationTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val coverUri: String = "",
    val fixedReferenceUris: List<String> = emptyList(),
    val resolutionLevel: String = "1K",
    val aspectRatio: String = "1:1",
    val transparentBackground: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class HistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val prompt: String,
    val referenceUris: List<String>,
    val relayId: String,
    val relayName: String,
    val model: String,
    val resolutionLevel: String = "1K",
    val aspectRatio: String = "1:1",
    val actualSize: String = "",
    val transparentBackground: Boolean = false,
    // Legacy fields retained for old history migration.
    val sizeLabel: String = "自动",
    val customWidth: String = "",
    val customHeight: String = "",
    val resultPath: String,
    val resultUrl: String = "",
    val revisedPrompt: String = "",
    val status: String = "SUCCESS",
    val stage: String = "",
    val httpCode: Int = 0,
    val errorDetail: String = "",
    val responseSnippet: String = "",
    val durationMs: Long = 0L
)

class WorkspaceStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("workspace_v3", Context.MODE_PRIVATE)

    fun getSessions(): MutableList<ImageSession> {
        val result = readArray("sessions") { sessionFromJson(it) }.toMutableList()
        if (result.isEmpty()) {
            val first = ImageSession()
            saveSessions(listOf(first))
            setCurrentSessionId(first.id)
            return mutableListOf(first)
        }
        return result.sortedByDescending { it.updatedAt }.toMutableList()
    }

    fun getSession(id: String): ImageSession? = getSessions().firstOrNull { it.id == id }

    fun createSession(): ImageSession {
        val session = ImageSession()
        val all = getSessions()
        all.add(0, session)
        saveSessions(all)
        setCurrentSessionId(session.id)
        return session
    }

    fun saveSession(session: ImageSession) {
        val all = getSessions()
        val index = all.indexOfFirst { it.id == session.id }
        if (index >= 0) all[index] = session else all.add(session)
        saveSessions(all)
    }

    fun deleteSession(id: String, deleteHistory: Boolean = false) {
        val all = getSessions().filterNot { it.id == id }.toMutableList()
        if (all.isEmpty()) all += ImageSession()
        saveSessions(all)
        if (deleteHistory) saveHistory(getHistory().filterNot { it.sessionId == id })
        if (getCurrentSessionId() == id) setCurrentSessionId(all.first().id)
    }

    fun getCurrentSessionId(): String? = prefs.getString("current_session_id", null)
    fun setCurrentSessionId(id: String) = prefs.edit().putString("current_session_id", id).apply()

    fun getOcs(): MutableList<OcProfile> = readArray("ocs") { ocFromJson(it) }.toMutableList()
    fun getOc(id: String): OcProfile? = getOcs().firstOrNull { it.id == id }

    fun saveOc(oc: OcProfile) {
        val all = getOcs()
        val index = all.indexOfFirst { it.id == oc.id }
        if (index >= 0) all[index] = oc else all.add(oc)
        saveOcs(all)
    }

    fun deleteOc(id: String) = saveOcs(getOcs().filterNot { it.id == id })

    fun getTemplates(): MutableList<GenerationTemplate> {
        ensureDefaultTemplates()
        return readArray("templates") { templateFromJson(it) }
            .sortedByDescending { it.updatedAt }
            .toMutableList()
    }

    fun getTemplate(id: String): GenerationTemplate? = getTemplates().firstOrNull { it.id == id }

    fun saveTemplate(template: GenerationTemplate) {
        ensureDefaultTemplates()
        val all = readArray("templates") { templateFromJson(it) }.toMutableList()
        val index = all.indexOfFirst { it.id == template.id }
        val saved = template.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) all[index] = saved else all.add(saved)
        saveTemplates(all)
    }

    fun deleteTemplate(id: String) {
        ensureDefaultTemplates()
        saveTemplates(readArray("templates") { templateFromJson(it) }.filterNot { it.id == id })
    }

    fun getHistory(sessionId: String? = null): List<HistoryRecord> {
        val all = readArray("history") { historyFromJson(it) }.sortedByDescending { it.createdAt }
        return if (sessionId == null) all else all.filter { it.sessionId == sessionId }
    }

    fun getHistoryItem(id: String): HistoryRecord? = getHistory().firstOrNull { it.id == id }

    fun addHistory(record: HistoryRecord) {
        val all = getHistory().toMutableList()
        all.add(0, record)
        saveHistory(all.distinctBy { it.id }.take(MAX_HISTORY))
    }

    private fun ensureDefaultTemplates() {
        if (prefs.getBoolean("templates_initialized", false)) return
        val current = readArray("templates") { templateFromJson(it) }.toMutableList()
        if (current.none { it.name == "透明表情包" }) {
            current += GenerationTemplate(
                name = "透明表情包",
                prompt = "把人物参考图中的角色绘制成统一风格的可爱 Q 版表情包。保持角色的发型、发色、眼睛、服装和标志性特征一致；表情夸张、动作简洁、轮廓清楚、适合作为聊天表情。画面只保留角色主体，不要场景、文字、水印、边框或装饰底板。输出透明背景 PNG，背景 Alpha 为透明，不要白底、纯色底或棋盘格。",
                resolutionLevel = "1K",
                aspectRatio = "1:1",
                transparentBackground = true
            )
        }
        saveTemplates(current)
        prefs.edit().putBoolean("templates_initialized", true).apply()
    }

    private fun saveSessions(items: List<ImageSession>) = writeArray("sessions", items.map { sessionToJson(it) })
    private fun saveOcs(items: List<OcProfile>) = writeArray("ocs", items.map { ocToJson(it) })
    private fun saveTemplates(items: List<GenerationTemplate>) = writeArray("templates", items.map { templateToJson(it) })
    private fun saveHistory(items: List<HistoryRecord>) {
        val previous = readArray("history") { historyFromJson(it) }
        writeArray("history", items.map { historyToJson(it) })
        cleanupRemovedHistoryFiles(previous, items)
    }

    private fun cleanupRemovedHistoryFiles(previous: List<HistoryRecord>, kept: List<HistoryRecord>) {
        val keptPaths = kept.map { it.resultPath }.filter { it.isNotBlank() }.toSet()
        previous.asSequence()
            .map { it.resultPath }
            .filter { it.isNotBlank() && it !in keptPaths }
            .distinct()
            .forEach(::deleteOwnedHistoryFile)
    }

    private fun deleteOwnedHistoryFile(path: String) {
        runCatching {
            val historyDir = File(appContext.filesDir, "history").canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile == historyDir && file.isFile) file.delete()
        }
    }

    private fun writeArray(key: String, objects: List<JSONObject>) {
        val arr = JSONArray()
        objects.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun <T> readArray(key: String, parser: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    runCatching { parser(arr.getJSONObject(i)) }.getOrNull()?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun strings(values: List<String>) = JSONArray().apply { values.forEach { put(it) } }
    private fun strings(arr: JSONArray?): List<String> = buildList {
        if (arr != null) for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
    }

    private fun sessionToJson(s: ImageSession) = JSONObject()
        .put("id", s.id).put("title", s.title).put("prompt", s.prompt)
        .put("referenceUris", strings(s.referenceUris)).put("relayId", s.relayId)
        .put("model", s.model).put("resolutionLevel", s.resolutionLevel).put("aspectRatio", s.aspectRatio)
        .put("templateId", s.templateId).put("templateName", s.templateName)
        .put("transparentBackground", s.transparentBackground)
        .put("sizeLabel", s.sizeLabel).put("customWidth", s.customWidth).put("customHeight", s.customHeight)
        .put("updatedAt", s.updatedAt)

    private fun sessionFromJson(o: JSONObject): ImageSession {
        val legacySize = o.optString("sizeLabel", "自动")
        return ImageSession(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = o.optString("title", "新建生图"),
            prompt = o.optString("prompt"),
            referenceUris = strings(o.optJSONArray("referenceUris")),
            relayId = o.optString("relayId"),
            model = o.optString("model"),
            resolutionLevel = o.optString("resolutionLevel", inferResolution(legacySize)),
            aspectRatio = o.optString("aspectRatio", inferRatio(legacySize)),
            templateId = o.optString("templateId"),
            templateName = o.optString("templateName"),
            transparentBackground = o.optBoolean("transparentBackground", false),
            sizeLabel = legacySize,
            customWidth = o.optString("customWidth"),
            customHeight = o.optString("customHeight"),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun ocToJson(o: OcProfile) = JSONObject()
        .put("id", o.id).put("name", o.name).put("prompt", o.prompt)
        .put("referenceUris", strings(o.referenceUris))

    private fun ocFromJson(o: JSONObject) = OcProfile(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name", "未命名 OC"),
        prompt = o.optString("prompt"),
        referenceUris = strings(o.optJSONArray("referenceUris"))
    )

    private fun templateToJson(t: GenerationTemplate) = JSONObject()
        .put("id", t.id).put("name", t.name).put("prompt", t.prompt).put("coverUri", t.coverUri)
        .put("fixedReferenceUris", strings(t.fixedReferenceUris))
        .put("resolutionLevel", t.resolutionLevel).put("aspectRatio", t.aspectRatio)
        .put("transparentBackground", t.transparentBackground).put("updatedAt", t.updatedAt)

    private fun templateFromJson(o: JSONObject) = GenerationTemplate(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name", "未命名模板"),
        prompt = o.optString("prompt"),
        coverUri = o.optString("coverUri"),
        fixedReferenceUris = strings(o.optJSONArray("fixedReferenceUris")),
        resolutionLevel = o.optString("resolutionLevel", "1K"),
        aspectRatio = o.optString("aspectRatio", "1:1"),
        transparentBackground = o.optBoolean("transparentBackground", false),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun historyToJson(h: HistoryRecord) = JSONObject()
        .put("id", h.id).put("sessionId", h.sessionId).put("createdAt", h.createdAt)
        .put("prompt", h.prompt).put("referenceUris", strings(h.referenceUris))
        .put("relayId", h.relayId).put("relayName", h.relayName).put("model", h.model)
        .put("resolutionLevel", h.resolutionLevel).put("aspectRatio", h.aspectRatio).put("actualSize", h.actualSize)
        .put("transparentBackground", h.transparentBackground)
        .put("sizeLabel", h.sizeLabel).put("customWidth", h.customWidth).put("customHeight", h.customHeight)
        .put("resultPath", h.resultPath).put("resultUrl", h.resultUrl).put("revisedPrompt", h.revisedPrompt)
        .put("status", h.status).put("stage", h.stage).put("httpCode", h.httpCode)
        .put("errorDetail", h.errorDetail).put("responseSnippet", h.responseSnippet).put("durationMs", h.durationMs)

    private fun historyFromJson(o: JSONObject): HistoryRecord {
        val legacySize = o.optString("sizeLabel", "自动")
        return HistoryRecord(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            sessionId = o.optString("sessionId"),
            createdAt = o.optLong("createdAt"),
            prompt = o.optString("prompt"),
            referenceUris = strings(o.optJSONArray("referenceUris")),
            relayId = o.optString("relayId"), relayName = o.optString("relayName"), model = o.optString("model"),
            resolutionLevel = o.optString("resolutionLevel", inferResolution(legacySize)),
            aspectRatio = o.optString("aspectRatio", inferRatio(legacySize)),
            actualSize = o.optString("actualSize"),
            transparentBackground = o.optBoolean("transparentBackground", false),
            sizeLabel = legacySize,
            customWidth = o.optString("customWidth"), customHeight = o.optString("customHeight"),
            resultPath = o.optString("resultPath"), resultUrl = o.optString("resultUrl"), revisedPrompt = o.optString("revisedPrompt"),
            status = o.optString("status", "SUCCESS"), stage = o.optString("stage"), httpCode = o.optInt("httpCode"),
            errorDetail = o.optString("errorDetail"), responseSnippet = o.optString("responseSnippet"), durationMs = o.optLong("durationMs")
        )
    }

    private fun inferResolution(size: String): String {
        val w = size.substringBefore('x').toIntOrNull() ?: return "1K"
        return when {
            w >= 3000 -> "4K"
            w >= 1800 -> "2K"
            else -> "1K"
        }
    }

    private fun inferRatio(size: String): String {
        val parts = size.split('x')
        val w = parts.getOrNull(0)?.toDoubleOrNull() ?: return "1:1"
        val h = parts.getOrNull(1)?.toDoubleOrNull() ?: return "1:1"
        val r = w / h
        return when {
            r > 1.6 -> "16:9"
            r > 1.2 -> "4:3"
            r < 0.62 -> "9:16"
            r < 0.85 -> "3:4"
            else -> "1:1"
        }
    }

    companion object { private const val MAX_HISTORY = 300 }
}
