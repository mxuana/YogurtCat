package com.example.change2proimage

import kotlin.math.roundToInt

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

    fun resolve(relay: RelayConfig, model: String, resolution: String, ratio: String): SizePlan {
        val target = targetDimensions(resolution, ratio)
        if (relay.sizeMode == "none" || target.first == null || target.second == null) {
            return SizePlan(target.first, target.second, null, null, adapted = false)
        }

        // OpenAI-compatible GPT image endpoints commonly expose only these size presets.
        // Keep the user's target selection, but adapt the actual request instead of sending an invalid size.
        if (relay.sizeMode == "size" && model.contains("gpt-image", ignoreCase = true)) {
            val request = when (ratio) {
                "3:4", "9:16" -> 1024 to 1536
                "4:3", "16:9" -> 1536 to 1024
                else -> 1024 to 1024
            }
            return SizePlan(target.first, target.second, request.first, request.second,
                adapted = target.first != request.first || target.second != request.second)
        }

        return SizePlan(target.first, target.second, target.first, target.second, adapted = false)
    }

    private fun targetDimensions(resolution: String, ratio: String): Pair<Int?, Int?> {
        if (ratio == "自动") return null to null
        val longSide = when (resolution) {
            "2K" -> 2048
            "4K" -> 4096
            else -> 1024
        }
        return when (ratio) {
            "1:1" -> longSide to longSide
            "3:4" -> align64(longSide * 3.0 / 4.0) to longSide
            "4:3" -> longSide to align64(longSide * 3.0 / 4.0)
            "16:9" -> longSide to align64(longSide * 9.0 / 16.0)
            "9:16" -> align64(longSide * 9.0 / 16.0) to longSide
            else -> null to null
        }
    }

    private fun align64(value: Double): Int = ((value / 64.0).roundToInt().coerceAtLeast(1)) * 64
}
