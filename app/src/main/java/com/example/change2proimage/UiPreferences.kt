package com.example.change2proimage

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

data class UiConfig(
    val preset: String = "minimal",
    val pageMarginDp: Int = 12,
    val cardPaddingDp: Int = 10,
    val cardRadiusDp: Int = 18,
    val gapDp: Int = 8,
    val referenceSizeDp: Int = 76,
    val promptHeightDp: Int = 88,
    val generateSizeDp: Int = 58,
    val textScalePercent: Int = 100,
    val backgroundColor: String = "#FBF7FC",
    val cardColor: String = "#FFFFFF",
    val accentColor: String = "#7053D6",
    val showSubtitle: Boolean = true,
    val showModelStatus: Boolean = false,
    val showSizeHint: Boolean = false
)

class UiPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("ui_editor", Context.MODE_PRIVATE)

    fun load(): UiConfig = UiConfig(
        preset = prefs.getString("preset", "minimal") ?: "minimal",
        pageMarginDp = prefs.getInt("pageMarginDp", 12),
        cardPaddingDp = prefs.getInt("cardPaddingDp", 10),
        cardRadiusDp = prefs.getInt("cardRadiusDp", 18),
        gapDp = prefs.getInt("gapDp", 8),
        referenceSizeDp = prefs.getInt("referenceSizeDp", 76),
        promptHeightDp = prefs.getInt("promptHeightDp", 88),
        generateSizeDp = prefs.getInt("generateSizeDp", 58),
        textScalePercent = prefs.getInt("textScalePercent", 100),
        backgroundColor = prefs.getString("backgroundColor", "#FBF7FC") ?: "#FBF7FC",
        cardColor = prefs.getString("cardColor", "#FFFFFF") ?: "#FFFFFF",
        accentColor = prefs.getString("accentColor", "#7053D6") ?: "#7053D6",
        showSubtitle = prefs.getBoolean("showSubtitle", true),
        showModelStatus = prefs.getBoolean("showModelStatus", false),
        showSizeHint = prefs.getBoolean("showSizeHint", false)
    )

    fun save(c: UiConfig) {
        prefs.edit()
            .putString("preset", c.preset).putInt("pageMarginDp", c.pageMarginDp)
            .putInt("cardPaddingDp", c.cardPaddingDp).putInt("cardRadiusDp", c.cardRadiusDp)
            .putInt("gapDp", c.gapDp).putInt("referenceSizeDp", c.referenceSizeDp)
            .putInt("promptHeightDp", c.promptHeightDp).putInt("generateSizeDp", c.generateSizeDp)
            .putInt("textScalePercent", c.textScalePercent).putString("backgroundColor", c.backgroundColor)
            .putString("cardColor", c.cardColor).putString("accentColor", c.accentColor)
            .putBoolean("showSubtitle", c.showSubtitle).putBoolean("showModelStatus", c.showModelStatus)
            .putBoolean("showSizeHint", c.showSizeHint).apply()
    }

    fun preset(name: String): UiConfig = when (name) {
        "compact" -> UiConfig(preset="compact", pageMarginDp=14, cardPaddingDp=12, cardRadiusDp=20, gapDp=8, referenceSizeDp=82, promptHeightDp=96, generateSizeDp=60, showSubtitle=true, showModelStatus=true)
        "card" -> UiConfig(preset="card", pageMarginDp=18, cardPaddingDp=16, cardRadiusDp=26, gapDp=12, referenceSizeDp=92, promptHeightDp=112, generateSizeDp=68, showSubtitle=true, showModelStatus=true, showSizeHint=true)
        else -> UiConfig()
    }

    fun toJson(c: UiConfig): String = JSONObject().apply {
        put("preset",c.preset); put("pageMarginDp",c.pageMarginDp); put("cardPaddingDp",c.cardPaddingDp)
        put("cardRadiusDp",c.cardRadiusDp); put("gapDp",c.gapDp); put("referenceSizeDp",c.referenceSizeDp)
        put("promptHeightDp",c.promptHeightDp); put("generateSizeDp",c.generateSizeDp); put("textScalePercent",c.textScalePercent)
        put("backgroundColor",c.backgroundColor); put("cardColor",c.cardColor); put("accentColor",c.accentColor)
        put("showSubtitle",c.showSubtitle); put("showModelStatus",c.showModelStatus); put("showSizeHint",c.showSizeHint)
    }.toString(2)

    fun fromJson(text: String): UiConfig {
        val o = JSONObject(text); val d = load()
        fun color(key:String, fallback:String): String = o.optString(key, fallback).let { runCatching { Color.parseColor(it); it }.getOrDefault(fallback) }
        return UiConfig(
            o.optString("preset",d.preset), o.optInt("pageMarginDp",d.pageMarginDp).coerceIn(0,40),
            o.optInt("cardPaddingDp",d.cardPaddingDp).coerceIn(4,32), o.optInt("cardRadiusDp",d.cardRadiusDp).coerceIn(0,40),
            o.optInt("gapDp",d.gapDp).coerceIn(0,24), o.optInt("referenceSizeDp",d.referenceSizeDp).coerceIn(48,140),
            o.optInt("promptHeightDp",d.promptHeightDp).coerceIn(56,220), o.optInt("generateSizeDp",d.generateSizeDp).coerceIn(44,96),
            o.optInt("textScalePercent",d.textScalePercent).coerceIn(80,130), color("backgroundColor",d.backgroundColor),
            color("cardColor",d.cardColor), color("accentColor",d.accentColor), o.optBoolean("showSubtitle",d.showSubtitle),
            o.optBoolean("showModelStatus",d.showModelStatus), o.optBoolean("showSizeHint",d.showSizeHint)
        )
    }
}
