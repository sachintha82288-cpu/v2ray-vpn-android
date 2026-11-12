package com.v2rayvpn.app.core.util

/**
 * Removes credentials from anything that may be shown, copied, exported or logged.
 *
 * This is intentionally aggressive: it is better to over-redact a diagnostic line than to leak
 * a UUID, password or subscription token into a log file the user may share.
 */
object Redactor {

    private val uuid = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    )
    private val vmessUri = Regex("vmess://[A-Za-z0-9+/=_-]+")
    private val shareUri = Regex("(vless|trojan|ss|socks|http)://[^\\s\"'<>]+")
    private val sensitivePair = Regex(
        "(?i)(uuid|password|passwd|pass|pbk|publickey|shortid|sid|id|token|auth|apikey|api_key|secret|flow|key)\\s*[=:]\\s*\"?([^\\s,\"'}&]+)\"?"
    )
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+")

    fun redact(input: String): String {
        var out = input
        // Key/value pairs first: a later marker must not look like a secret itself.
        out = sensitivePair.replace(out) { m -> "${m.groupValues[1]}=<redacted>" }
        out = bearer.replace(out, "Bearer <redacted>")
        out = vmessUri.replace(out, "vmess://<redacted>")
        out = shareUri.replace(out, "$1://<redacted>")
        out = uuid.replace(out, "[uuid]")
        return out
    }
}
