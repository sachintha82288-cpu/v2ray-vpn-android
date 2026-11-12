package com.v2rayvpn.app.data.parse

import com.v2rayvpn.app.domain.model.Profile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses subscription payloads.
 *
 * Supported response formats (verified against what this parser actually handles):
 *  1. base64 of newline separated share links (the common V2Ray/Xray subscription format);
 *  2. plain newline separated share links;
 *  3. a JSON array of single-outbound Xray client configurations;
 *  4. one JSON object (single Xray configuration).
 *
 * Anything else is reported as unsupported instead of being half-parsed.
 */
object SubscriptionParser {

    const val MAX_BODY_BYTES = 2 * 1024 * 1024

    data class Result(val profiles: List<Profile>, val skipped: Int, val format: String)

    fun parse(body: String, subscriptionId: String?, subscriptionTitle: String?): Result {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) throw ConfigParseException("Subscription returned an empty body")
        if (trimmed.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) {
            throw ConfigParseException("Subscription body is larger than 2 MB")
        }

        val profiles = ArrayList<Profile>()
        var skipped = 0
        val format: String

        when {
            trimmed.startsWith("[") -> {
                format = "JSON array"
                val array = try {
                    JSONArray(trimmed)
                } catch (e: Exception) {
                    throw ConfigParseException("Malformed JSON subscription")
                }
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    try {
                        profiles.add(XrayJsonImport.parse(obj.toString()))
                    } catch (e: ConfigParseException) {
                        skipped++
                    }
                }
            }

            trimmed.startsWith("{") -> {
                format = "Xray JSON"
                profiles.add(XrayJsonImport.parse(trimmed))
            }

            else -> {
                val decoded = decodeIfBase64(trimmed)
                format = if (decoded != trimmed) "base64 link list" else "plain link list"
                decoded.lineSequence().forEach { line ->
                    val entry = line.trim()
                    if (entry.isEmpty() || entry.startsWith("#") || entry.startsWith("//")) return@forEach
                    try {
                        profiles.add(UriParser.parse(entry))
                    } catch (e: ConfigParseException) {
                        skipped++
                    }
                }
            }
        }

        if (profiles.isEmpty()) {
            throw ConfigParseException("No usable configuration found in the subscription response")
        }

        val deduplicated = LinkedHashMap<String, Profile>()
        profiles.forEach { profile ->
            val copy = profile.copy(
                subscriptionId = subscriptionId,
                subscriptionTitle = subscriptionTitle,
                sourceUri = null
            )
            deduplicated.putIfAbsent(copy.fingerprintKey, copy)
        }
        return Result(deduplicated.values.toList(), skipped, format)
    }

    private fun decodeIfBase64(text: String): String {
        val compact = text.replace("\n", "").replace("\r", "").replace(" ", "")
        val looksBase64 = compact.matches(Regex("^[A-Za-z0-9+/=_-]+$")) && compact.length > 16
        if (!looksBase64) return text
        return try {
            val decoded = B64.decodeToString(compact)
            if (decoded.contains("://") || decoded.contains("\n")) decoded else text
        } catch (e: ConfigParseException) {
            text
        }
    }

    /** Parses the `subscription-userinfo` header, only when the server actually sends it. */
    fun parseUserInfo(header: String?): UserInfo? {
        if (header.isNullOrBlank()) return null
        val parts = header.split(';').mapNotNull { part ->
            val key = part.substringBefore('=', "").trim().lowercase()
            val value = part.substringAfter('=', "").trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }.toMap()
        if (parts.isEmpty()) return null
        fun long(key: String): Long? = parts[key]?.toLongOrNull()
        val upload = long("upload")
        val download = long("download")
        val total = long("total")
        val expire = long("expire")
        if (upload == null && download == null && total == null && expire == null) return null
        return UserInfo(upload, download, total, expire)
    }

    data class UserInfo(
        val uploadBytes: Long?,
        val downloadBytes: Long?,
        val totalBytes: Long?,
        val expiryEpochSeconds: Long?
    )
}
