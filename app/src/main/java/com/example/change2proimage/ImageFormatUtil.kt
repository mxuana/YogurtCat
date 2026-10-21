package com.example.change2proimage

import java.io.File

object ImageFormatUtil {
    data class Info(val extension: String, val mime: String)

    fun detect(bytes: ByteArray, contentType: String? = null): Info {
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return Info("png", "image/png")
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return Info("jpg", "image/jpeg")
        if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return Info("webp", "image/webp")
        val declared = contentType?.substringBefore(';')?.trim().orEmpty()
        throw IllegalArgumentException("不支持或无法识别的图片格式${if (declared.isBlank()) "" else "：$declared"}")
    }

    fun fromFile(file: File): Info = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> Info("jpg", "image/jpeg")
        "webp" -> Info("webp", "image/webp")
        else -> Info("png", "image/png")
    }
}
