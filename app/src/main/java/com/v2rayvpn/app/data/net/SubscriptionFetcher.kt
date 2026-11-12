package com.v2rayvpn.app.data.net

import com.v2rayvpn.app.data.parse.ConfigParseException
import com.v2rayvpn.app.data.parse.SubscriptionParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches subscription payloads with hard limits: http/https only, short timeouts, a 2 MB cap and
 * no credential logging. The URL itself is a secret (it usually carries a token), so it is never
 * written to the log.
 */
class SubscriptionFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    data class Fetched(
        val body: String,
        val userInfo: SubscriptionParser.UserInfo?,
        val title: String?
    )

    fun validateUrl(url: String): String {
        val trimmed = url.trim()
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw ConfigParseException("Subscription URL is not a valid URL")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw ConfigParseException("Subscription URL must start with http:// or https://")
        }
        if (uri.host.isNullOrBlank()) throw ConfigParseException("Subscription URL has no host")
        return trimmed
    }

    @Throws(IOException::class, ConfigParseException::class)
    fun fetch(url: String): Fetched {
        val safeUrl = validateUrl(url)
        val request = Request.Builder()
            .url(safeUrl)
            .header("User-Agent", "V2RAY-VPN/1.0 (Android)")
            .header("Accept", "*/*")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Subscription server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Subscription response had no body")
            val declared = body.contentLength()
            if (declared > SubscriptionParser.MAX_BODY_BYTES) {
                throw ConfigParseException("Subscription body is larger than 2 MB")
            }
            val stream = body.byteStream()
            val buffer = ByteArray(16 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                if (total > SubscriptionParser.MAX_BODY_BYTES) {
                    throw ConfigParseException("Subscription body is larger than 2 MB")
                }
                out.write(buffer, 0, read)
            }
            val text = out.toString("UTF-8")
            val userInfo = SubscriptionParser.parseUserInfo(
                response.header("subscription-userinfo") ?: response.header("Subscription-Userinfo")
            )
            val title = response.header("profile-title") ?: response.header("Profile-Title")
            return Fetched(text, userInfo, title?.takeIf { it.isNotBlank() })
        }
    }
}
