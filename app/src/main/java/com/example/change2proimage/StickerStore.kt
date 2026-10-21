package com.example.change2proimage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StickerTemplateItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val imageUri: String,
    val prompt: String = "将图一表情包人物换成图二中的人物，画风、表情、动作、构图与图一保持一致；保留图二人物的发型、发色、眼睛、服装和标志性特征。",
    val resolutionLevel: String = "1K",
    val aspectRatio: String = "1:1",
    val transparentBackground: Boolean = true
)

data class StickerQueueBatch(
    val id: String = UUID.randomUUID().toString(),
    val ocId: String,
    val ocName: String,
    val relayId: String,
    val relayName: String,
    val model: String,
    val itemIds: List<String>,
    val generationTaskIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

class StickerStore(context: Context) {
    private val prefs = context.getSharedPreferences("sticker_templates_v1", Context.MODE_PRIVATE)

    fun getItems(): MutableList<StickerTemplateItem> = read("items") { o ->
        StickerTemplateItem(
            id=o.optString("id").ifBlank { UUID.randomUUID().toString() }, name=o.optString("name","未命名表情"),
            imageUri=o.optString("imageUri"), prompt=o.optString("prompt"),
            resolutionLevel=o.optString("resolutionLevel","1K"), aspectRatio=o.optString("aspectRatio","1:1"),
            transparentBackground=o.optBoolean("transparentBackground",true)
        )
    }.toMutableList()

    fun getItem(id:String)=getItems().firstOrNull{it.id==id}
    fun saveItem(item:StickerTemplateItem){ val a=getItems(); val i=a.indexOfFirst{it.id==item.id}; if(i>=0)a[i]=item else a.add(item); write("items",a.map(::itemJson)) }
    fun deleteItem(id:String)=write("items",getItems().filterNot{it.id==id}.map(::itemJson))

    fun getBatches(): MutableList<StickerQueueBatch> = read("batches") { o ->
        StickerQueueBatch(id=o.optString("id"), ocId=o.optString("ocId"), ocName=o.optString("ocName"), relayId=o.optString("relayId"), relayName=o.optString("relayName"), model=o.optString("model"), itemIds=strings(o.optJSONArray("itemIds")), generationTaskIds=strings(o.optJSONArray("generationTaskIds")), createdAt=o.optLong("createdAt"))
    }.sortedByDescending{it.createdAt}.toMutableList()
    fun getBatch(id:String)=getBatches().firstOrNull{it.id==id}
    fun saveBatch(b:StickerQueueBatch){ val a=getBatches(); val i=a.indexOfFirst{it.id==b.id}; if(i>=0)a[i]=b else a.add(0,b); write("batches",a.map(::batchJson)) }

    private fun itemJson(x:StickerTemplateItem)=JSONObject().put("id",x.id).put("name",x.name).put("imageUri",x.imageUri).put("prompt",x.prompt).put("resolutionLevel",x.resolutionLevel).put("aspectRatio",x.aspectRatio).put("transparentBackground",x.transparentBackground)
    private fun batchJson(x:StickerQueueBatch)=JSONObject().put("id",x.id).put("ocId",x.ocId).put("ocName",x.ocName).put("relayId",x.relayId).put("relayName",x.relayName).put("model",x.model).put("itemIds",JSONArray(x.itemIds)).put("generationTaskIds",JSONArray(x.generationTaskIds)).put("createdAt",x.createdAt)
    private fun write(k:String, objs:List<JSONObject>){ val a=JSONArray(); objs.forEach { a.put(it) }; prefs.edit().putString(k,a.toString()).apply() }
    private fun <T> read(k:String,p:(JSONObject)->T):List<T>{ val s=prefs.getString(k,null)?:return emptyList(); return runCatching{ val a=JSONArray(s); buildList{for(i in 0 until a.length()) add(p(a.getJSONObject(i)))} }.getOrDefault(emptyList()) }
    private fun strings(a:JSONArray?):List<String> = buildList{ if(a!=null) for(i in 0 until a.length()) a.optString(i).takeIf{it.isNotBlank()}?.let(::add) }
}
