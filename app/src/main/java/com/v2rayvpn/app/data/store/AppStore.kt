package com.v2rayvpn.app.data.store

import com.v2rayvpn.app.core.crypto.SecretBox
import com.v2rayvpn.app.core.log.LogBus
import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.DnsMode
import com.v2rayvpn.app.domain.model.PerAppMode
import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.RoutingMode
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Subscription
import com.v2rayvpn.app.domain.model.Transport
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single encrypted document that holds profiles, subscriptions and settings.
 *
 * Why a document instead of Room + DataStore: every field here is either a credential or a
 * preference that must be protected at rest. One AES-GCM blob with a Keystore key gives a single,
 * auditable encryption boundary and needs no annotation processing, which keeps the build
 * reproducible on minimal toolchains. The trade-off (whole document rewritten on change) is
 * acceptable at the scale of a few hundred profiles.
 */
class AppStore(private val file: File) {

    private val mutex = Mutex()
    private var document: JSONObject = JSONObject()

    fun load() {
        if (!file.exists()) {
            document = JSONObject().put("version", 1)
            return
        }
        document = try {
            val plain = SecretBox.decrypt(file.readBytes())
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            // A keystore key can be lost (for example after a factory reset of the keystore).
            // Never crash on it: keep the old blob for the user to inspect and start empty.
            LogBus.e("Stored data could not be decrypted; starting with an empty store")
            runCatching { file.renameTo(File(file.parentFile, file.name + ".unreadable")) }
            JSONObject().put("version", 1)
        }
    }

    suspend fun read(): JSONObject = mutex.withLock { JSONObject(document.toString()) }

    suspend fun mutate(block: (JSONObject) -> Unit) {
        mutex.withLock {
            block(document)
            persist()
        }
    }

    private fun persist() {
        try {
            val plain = document.toString().toByteArray(Charsets.UTF_8)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(SecretBox.encrypt(plain))
            if (!tmp.renameTo(file)) {
                file.writeBytes(tmp.readBytes())
                tmp.delete()
            }
        } catch (e: Exception) {
            LogBus.e("Failed to persist local store: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_SETTINGS = "settings"
    }
}

// ------------------------------------------------------------------ mappers

internal fun Profile.toJson(): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("name", name)
    o.put("protocol", protocol.name)
    o.put("server", server)
    o.put("port", port)
    o.putOpt("uuid", uuid)
    o.putOpt("password", password)
    o.putOpt("username", username)
    o.put("alterId", alterId)
    o.put("vmessSecurity", vmessSecurity)
    o.put("vlessEncryption", vlessEncryption)
    o.putOpt("flow", flow)
    o.putOpt("ssMethod", ssMethod)
    o.put("transport", transport.name)
    o.put("security", security.name)
    o.putOpt("sni", sni)
    o.putOpt("fingerprint", fingerprint)
    o.put("alpn", JSONArray(alpn))
    o.put("allowInsecure", allowInsecure)
    o.putOpt("realityPublicKey", realityPublicKey)
    o.putOpt("realityShortId", realityShortId)
    o.putOpt("realitySpiderX", realitySpiderX)
    o.putOpt("wsPath", wsPath)
    o.putOpt("wsHost", wsHost)
    o.putOpt("grpcServiceName", grpcServiceName)
    o.put("grpcMultiMode", grpcMultiMode)
    o.putOpt("httpPath", httpPath)
    o.put("httpHost", JSONArray(httpHost))
    o.putOpt("xhttpPath", xhttpPath)
    o.putOpt("xhttpHost", xhttpHost)
    o.putOpt("sourceUri", sourceUri)
    o.putOpt("subscriptionId", subscriptionId)
    o.putOpt("subscriptionTitle", subscriptionTitle)
    o.put("favorite", favorite)
    o.putOpt("group", group)
    o.put("createdAt", createdAt)
    lastUsedAt?.let { o.put("lastUsedAt", it) }
    lastLatencyMs?.let { o.put("lastLatencyMs", it) }
    lastTestAt?.let { o.put("lastTestAt", it) }
    o.putOpt("lastTestType", lastTestType)
    return o
}

internal fun profileFromJson(o: JSONObject): Profile = Profile(
    id = o.optString("id"),
    name = o.optString("name"),
    protocol = runCatching { Protocol.valueOf(o.optString("protocol")) }.getOrDefault(Protocol.VMESS),
    server = o.optString("server"),
    port = o.optInt("port"),
    uuid = o.optString("uuid").takeIf { it.isNotBlank() },
    password = o.optString("password").takeIf { it.isNotBlank() },
    username = o.optString("username").takeIf { it.isNotBlank() },
    alterId = o.optInt("alterId", 0),
    vmessSecurity = o.optString("vmessSecurity", "auto"),
    vlessEncryption = o.optString("vlessEncryption", "none"),
    flow = o.optString("flow").takeIf { it.isNotBlank() },
    ssMethod = o.optString("ssMethod").takeIf { it.isNotBlank() },
    transport = runCatching { Transport.valueOf(o.optString("transport")) }.getOrDefault(Transport.TCP),
    security = runCatching { SecurityType.valueOf(o.optString("security")) }.getOrDefault(SecurityType.NONE),
    sni = o.optString("sni").takeIf { it.isNotBlank() },
    fingerprint = o.optString("fingerprint").takeIf { it.isNotBlank() },
    alpn = o.optJSONArray("alpn")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(),
    allowInsecure = o.optBoolean("allowInsecure", false),
    realityPublicKey = o.optString("realityPublicKey").takeIf { it.isNotBlank() },
    realityShortId = o.optString("realityShortId").takeIf { it.isNotBlank() },
    realitySpiderX = o.optString("realitySpiderX").takeIf { it.isNotBlank() },
    wsPath = o.optString("wsPath").takeIf { it.isNotBlank() },
    wsHost = o.optString("wsHost").takeIf { it.isNotBlank() },
    grpcServiceName = o.optString("grpcServiceName").takeIf { it.isNotBlank() },
    grpcMultiMode = o.optBoolean("grpcMultiMode", false),
    httpPath = o.optString("httpPath").takeIf { it.isNotBlank() },
    httpHost = o.optJSONArray("httpHost")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(),
    xhttpPath = o.optString("xhttpPath").takeIf { it.isNotBlank() },
    xhttpHost = o.optString("xhttpHost").takeIf { it.isNotBlank() },
    sourceUri = o.optString("sourceUri").takeIf { it.isNotBlank() },
    subscriptionId = o.optString("subscriptionId").takeIf { it.isNotBlank() },
    subscriptionTitle = o.optString("subscriptionTitle").takeIf { it.isNotBlank() },
    favorite = o.optBoolean("favorite", false),
    group = o.optString("group").takeIf { it.isNotBlank() },
    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    lastUsedAt = o.optLong("lastUsedAt", 0L).takeIf { it > 0 },
    lastLatencyMs = o.optLong("lastLatencyMs", -1L).takeIf { it >= 0 },
    lastTestAt = o.optLong("lastTestAt", 0L).takeIf { it > 0 },
    lastTestType = o.optString("lastTestType").takeIf { it.isNotBlank() }
)

internal fun Subscription.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("url", url)
    put("enabled", enabled)
    lastUpdated?.let { put("lastUpdated", it) }
    putOpt("lastError", lastError)
    put("profileCount", profileCount)
    uploadBytes?.let { put("uploadBytes", it) }
    downloadBytes?.let { put("downloadBytes", it) }
    totalBytes?.let { put("totalBytes", it) }
    expiryEpochSeconds?.let { put("expiryEpochSeconds", it) }
    put("autoRefreshHours", autoRefreshHours)
}

internal fun subscriptionFromJson(o: JSONObject): Subscription = Subscription(
    id = o.optString("id"),
    name = o.optString("name"),
    url = o.optString("url"),
    enabled = o.optBoolean("enabled", true),
    lastUpdated = o.optLong("lastUpdated", 0L).takeIf { it > 0 },
    lastError = o.optString("lastError").takeIf { it.isNotBlank() },
    profileCount = o.optInt("profileCount", 0),
    uploadBytes = o.optLong("uploadBytes", -1L).takeIf { it >= 0 },
    downloadBytes = o.optLong("downloadBytes", -1L).takeIf { it >= 0 },
    totalBytes = o.optLong("totalBytes", -1L).takeIf { it >= 0 },
    expiryEpochSeconds = o.optLong("expiryEpochSeconds", -1L).takeIf { it >= 0 },
    autoRefreshHours = o.optInt("autoRefreshHours", 0)
)

internal fun AppSettings.toJson(): JSONObject = JSONObject().apply {
    put("onboardingDone", onboardingDone)
    putOpt("selectedProfileId", selectedProfileId)
    put("routingMode", routingMode.name)
    put("blockAdsAndTrackers", blockAdsAndTrackers)
    put("ipv6Enabled", ipv6Enabled)
    put("dnsMode", dnsMode.name)
    put("remoteDns", remoteDns)
    put("localDns", localDns)
    put("mtu", mtu)
    put("perAppMode", perAppMode.name)
    put("perAppPackages", JSONArray(perAppPackages.toList()))
    put("reconnectOnNetworkChange", reconnectOnNetworkChange)
    put("logLevel", logLevel)
    put("reducedMotion", reducedMotion)
    put("publicIpLookupEnabled", publicIpLookupEnabled)
    put("subscriptionAutoRefreshHours", subscriptionAutoRefreshHours)
    put("healthCheckUrl", healthCheckUrl)
    put("latencyTestUrl", latencyTestUrl)
}

internal fun settingsFromJson(o: JSONObject): AppSettings = AppSettings(
    onboardingDone = o.optBoolean("onboardingDone", false),
    selectedProfileId = o.optString("selectedProfileId").takeIf { it.isNotBlank() },
    routingMode = runCatching { RoutingMode.valueOf(o.optString("routingMode")) }
        .getOrDefault(RoutingMode.BYPASS_LAN),
    blockAdsAndTrackers = o.optBoolean("blockAdsAndTrackers", false),
    ipv6Enabled = o.optBoolean("ipv6Enabled", true),
    dnsMode = runCatching { DnsMode.valueOf(o.optString("dnsMode")) }.getOrDefault(DnsMode.VPN),
    remoteDns = o.optString("remoteDns", "1.1.1.1"),
    localDns = o.optString("localDns", "1.1.1.1"),
    mtu = o.optInt("mtu", 1500).coerceIn(1280, 9000),
    perAppMode = runCatching { PerAppMode.valueOf(o.optString("perAppMode")) }
        .getOrDefault(PerAppMode.OFF),
    perAppPackages = o.optJSONArray("perAppPackages")?.let { arr ->
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    } ?: emptySet(),
    reconnectOnNetworkChange = o.optBoolean("reconnectOnNetworkChange", true),
    logLevel = o.optString("logLevel", "warning"),
    reducedMotion = o.optBoolean("reducedMotion", false),
    publicIpLookupEnabled = o.optBoolean("publicIpLookupEnabled", false),
    subscriptionAutoRefreshHours = o.optInt("subscriptionAutoRefreshHours", 0),
    healthCheckUrl = o.optString("healthCheckUrl", "https://www.google.com/generate_204"),
    latencyTestUrl = o.optString("latencyTestUrl", "https://www.google.com/generate_204")
)
