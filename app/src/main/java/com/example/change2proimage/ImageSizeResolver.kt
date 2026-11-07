package com.example.change2proimage

import kotlin.math.floor
import kotlin.math.sqrt

data class SizePlan(
    val targetWidth: Int?,
    val targetHeight: Int?,
    val requestWidth: Int?,
    val requestHeight: Int?,
    val adapted: Boolean
) {
    val targetLabel: String get() = if (targetWidth != null && targetHeight != null) "${targetWidth}×${targetHeight}" else "自动"
    val requestLabel: String get() = if (requestWidth != null && requestHeight != null) "${requestWidth}×${requestHeight}" else "自动"
}

object ImageSizeResolver {
    val resolutions = listOf("1K", "2K", "4K")
    val ratios = listOf("自动", "1:1", "3:4", "4:3", "16:9", "9:16")

    private const val MAX_EDGE = 3840
    private const val MAX_PIXELS = 8_294_400L

    fun resolve(relay: RelayConfig, model: String, resolution: String, ratio: String): SizePlan {
        val target = targetDimensions(resolution, ratio)
        if (relay.sizeMode == "none" || target.first == null || target.second == null) {
            return SizePlan(target.first, target.second, null, null, adapted = false)
        }
        // The modern GPT Image family accepts custom WIDTHxHEIGHT sizes.
        // Older GPT Image models retain the standard size presets.
        val modern = Regex("^gpt-image-(2(?:\\.|-|$)|[3-9](?:\\.|-|$))", RegexOption.IGNORE_CASE)
            .containsMatchIn(model)
        val request = if (relay.sizeMode == "size" && model.contains("gpt-image", true) && !modern) {
            when (ratio) {
                "3:4", "9:16" -> 1024 to 1536
                "4:3", "16:9" -> 1536 to 1024
                else -> 1024 to 1024
            }
        } else if (modern) {
            constrain(target.first, target.second)
        } else target
        return SizePlan(target.first, target.second, request.first, request.second,
            adapted = target != request)
    }

    private fun targetDimensions(resolution: String, ratio: String): Pair<Int?, Int?> {
        if (ratio == "自动") return null to null
        val longSide = when (resolution) {
            "2K" -> 2048
            "4K" -> 3840
            else -> 1024
        }
        val fraction = when (ratio) {
            "1:1" -> 1.0
            "3:4", "4:3" -> 3.0 / 4.0
            "16:9", "9:16" -> 9.0 / 16.0
            else -> return null to null
        }
        val shortSide = align16(longSide * fraction)
        return if (ratio == "3:4" || ratio == "9:16") shortSide to longSide else longSide to shortSide
    }

    private fun constrain(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height),
            sqrt(MAX_PIXELS.toDouble() / (width.toLong() * height.toLong())))
        var w = align16(width * scale)
        var h = align16(height * scale)
        // Rounding down guarantees compliance with both edge and area limits.
        while (w.toLong() * h > MAX_PIXELS) {
            if (w >= h) w -= 16 else h -= 16
        }
        return w to h
    }

    private fun align16(value: Double): Int = (floor(value / 16.0).toInt().coerceAtLeast(1)) * 16
}
