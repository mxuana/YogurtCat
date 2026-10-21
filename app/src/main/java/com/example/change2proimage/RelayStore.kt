package com.example.change2proimage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RelayConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelsPath: String = "/v1/models",
    val generationsPath: String = "/v1/images/generations",
    val editsPath: String = "/v1/images/edits",
    val defaultModel: String = "",
    val imageField: String = "image",
    val sizeMode: String = "size",
    val cachedModels: List<String> = emptyList(),
    val streamEnabled: Boolean = false,
    val modelCheckStatus: String = "unknown",
    val modelCheckMessage: String = "尚未检测",
    val modelCheckedAt: Long = 0L
)

class RelayStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("relay_store_v2", Context.MODE_PRIVATE)

    fun getAll(): MutableList<RelayConfig> {
        val raw = prefs.getString("relays", null)
        if (raw.isNullOrBlank()) {
            val initial = mutableListOf(defaultRelay())
            saveAll(initial)
            return initial
        }

        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<RelayConfig>()
            for (i in 0 until arr.length()) {
                result += fromJson(arr.getJSONObject(i))
            }
            if (result.isEmpty()) mutableListOf(defaultRelay()) else result
        } catch (_: Exception) {
            mutableListOf(defaultRelay())
        }
    }

    fun saveAll(relays: List<RelayConfig>) {
        val arr = JSONArray()
        relays.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("relays", arr.toString()).apply()
    }

    fun upsert(relay: RelayConfig) {
        val all = getAll()
        val index = all.indexOfFirst { it.id == relay.id }
        if (index >= 0) all[index] = relay else all += relay
        saveAll(all)
    }


    fun updateModelCheck(id: String, status: String, message: String, models: List<String>? = null) {
        val all = getAll(); val index = all.indexOfFirst { it.id == id }; if (index < 0) return
        all[index] = all[index].copy(
            cachedModels = models?.distinct()?.filter { it.isNotBlank() } ?: all[index].cachedModels,
            modelCheckStatus = status, modelCheckMessage = message, modelCheckedAt = System.currentTimeMillis()
        )
        saveAll(all)
    }

    fun delete(id: String) {
        val all = getAll().filterNot { it.id == id }.toMutableList()
        if (all.isEmpty()) all += defaultRelay(apiKey = "", model = "gpt-image-2")
        saveAll(all)
    }

    fun updateCachedModels(id: String, models: List<String>) {
        val all = getAll()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return
        all[index] = all[index].copy(cachedModels = models.distinct(), modelCheckStatus = "ok", modelCheckMessage = "读取到 ${models.distinct().size} 个模型", modelCheckedAt = System.currentTimeMillis())
        saveAll(all)
    }

    private fun defaultRelay(apiKey: String? = null, model: String? = null): RelayConfig {
        val legacy = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val oldKey = apiKey ?: legacy.getString("key", "").orEmpty()
        val oldModel = model ?: legacy.getString("model", "gpt-image-2").orEmpty().ifBlank { "gpt-image-2" }
        return RelayConfig(
            name = "Change2Pro",
            baseUrl = "https://gateway.change2pro.com",
            apiKey = oldKey,
            modelsPath = "/v1/models",
            generationsPath = "/v1/images/generations",
            editsPath = "/v1/images/edits",
            defaultModel = oldModel,
            imageField = "image",
            sizeMode = "size",
            cachedModels = listOf("gpt-image-2", "gpt-image-2.5-flare", "gpt-image-2.5-sunburst")
        )
    }

    private fun toJson(r: RelayConfig): JSONObject = JSONObject()
        .put("id", r.id)
        .put("name", r.name)
        .put("baseUrl", r.baseUrl)
        .put("apiKey", r.apiKey)
        .put("modelsPath", r.modelsPath)
        .put("generationsPath", r.generationsPath)
        .put("editsPath", r.editsPath)
        .put("defaultModel", r.defaultModel)
        .put("imageField", r.imageField)
        .put("sizeMode", r.sizeMode)
        .put("cachedModels", JSONArray(r.cachedModels))
        .put("streamEnabled", r.streamEnabled)
        .put("modelCheckStatus", r.modelCheckStatus)
        .put("modelCheckMessage", r.modelCheckMessage)
        .put("modelCheckedAt", r.modelCheckedAt)

    private fun fromJson(o: JSONObject): RelayConfig {
        val modelsArray = o.optJSONArray("cachedModels") ?: JSONArray()
        val models = mutableListOf<String>()
        for (i in 0 until modelsArray.length()) {
            models += modelsArray.optString(i)
        }
        return RelayConfig(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", "未命名站点"),
            baseUrl = o.optString("baseUrl"),
            apiKey = o.optString("apiKey"),
            modelsPath = o.optString("modelsPath", "/v1/models"),
            generationsPath = o.optString("generationsPath", "/v1/images/generations"),
            editsPath = o.optString("editsPath", "/v1/images/edits"),
            defaultModel = o.optString("defaultModel"),
            imageField = o.optString("imageField", "image").ifBlank { "image" },
            sizeMode = o.optString("sizeMode", "size"),
            cachedModels = models.filter { it.isNotBlank() }.distinct(),
            streamEnabled = o.optBoolean("streamEnabled", false),
            modelCheckStatus = o.optString("modelCheckStatus", "unknown"),
            modelCheckMessage = o.optString("modelCheckMessage", "尚未检测"),
            modelCheckedAt = o.optLong("modelCheckedAt", 0L)
        )
    }
}
