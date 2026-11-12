package com.v2rayvpn.app.data.net

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Optional public-IP lookup.
 *
 * This is the only outbound request the app makes that is not a subscription fetch, and it is
 * opt-in. The UI states which external service is contacted before it is enabled.
 */
object PublicIpLookup {

    const val ENDPOINT_HOST = "api.ipify.org"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://$ENDPOINT_HOST")
                .header("User-Agent", "V2RAY-VPN/1.0 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string()?.trim() ?: return@withContext null
                if (body.length in 7..45 && body.matches(Regex("^[0-9a-fA-F:.]+$"))) body else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
