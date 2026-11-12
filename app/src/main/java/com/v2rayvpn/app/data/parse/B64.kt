package com.v2rayvpn.app.data.parse

import java.util.Base64

/** Tolerant base64 decoder used for vmess links and subscription payloads. */
object B64 {

    fun decodeToString(input: String): String {
        val cleaned = input
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .replace('-', '+')
            .replace('_', '/')
        if (cleaned.isEmpty()) return ""
        val padded = when (cleaned.length % 4) {
            0 -> cleaned
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned.dropLast(1)
        }
        val bytes = try {
            Base64.getDecoder().decode(padded)
        } catch (e: IllegalArgumentException) {
            throw ConfigParseException("Invalid base64 payload")
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun encode(input: String): String =
        Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
}
