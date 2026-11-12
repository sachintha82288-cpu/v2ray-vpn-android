package com.v2rayvpn.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2rayvpn.app.core.AppContainer
import com.v2rayvpn.app.core.log.LogBus
import com.v2rayvpn.app.core.util.QrCode
import com.v2rayvpn.app.data.net.PublicIpLookup
import com.v2rayvpn.app.data.parse.ConfigParseException
import com.v2rayvpn.app.data.parse.ShareUriBuilder
import com.v2rayvpn.app.data.parse.SubscriptionParser
import com.v2rayvpn.app.data.parse.UriParser
import com.v2rayvpn.app.data.parse.XrayJsonImport
import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.Subscription
import com.v2rayvpn.app.vpn.LatencyTester
import com.v2rayvpn.app.vpn.VpnController
import com.v2rayvpn.app.vpn.VpnStateHolder
import com.v2rayvpn.app.vpn.XrayEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class InstalledApp(val packageName: String, val label: String)

/**
 * Single ViewModel for the whole app.
 *
 * The screen set is small and shares one repository and one connection state, so a single
 * ViewModel avoids duplicated plumbing between twelve screens. Logic stays in the repository,
 * parsers and VPN service; this class only orchestrates.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get()
    private val repository = container.repository
    private val fetcher = container.subscriptionFetcher

    val profiles: StateFlow<List<Profile>> = repository.profiles
    val subscriptions: StateFlow<List<Subscription>> = repository.subscriptions
    val settings: StateFlow<AppSettings> = repository.settings
    val connectionState = VpnStateHolder.state
    val traffic = VpnStateHolder.traffic
    val activeProfileId = VpnStateHolder.profileId
    val publicIp = VpnStateHolder.publicIp
    val logs = LogBus.entries

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    private val _xrayVersion = MutableStateFlow("")
    val xrayVersion: StateFlow<String> = _xrayVersion.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadOnce()
            XrayEngine.ensureEnv(getApplication())
            _xrayVersion.value = withContext(Dispatchers.IO) {
                com.v2rayvpn.app.vpn.XrayEngine(
                    object : com.v2rayvpn.app.vpn.XrayEngine.Listener {
                        override fun onCoreStatus(code: Int, message: String) = Unit
                        override fun onCoreStarted() = Unit
                        override fun onCoreStopped() = Unit
                    }
                ).version()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(30 * 60 * 1000L)
                refreshDueSubscriptions()
            }
        }
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is com.v2rayvpn.app.domain.model.ConnectionState.Connected &&
                    settings.value.publicIpLookupEnabled
                ) {
                    VpnStateHolder.setPublicIp(PublicIpLookup.fetch())
                }
            }
        }
    }

    fun postMessage(text: String?) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------------ import

    fun importText(raw: String, nameHint: String? = null) {
        val text = raw.trim()
        if (text.isEmpty()) {
            postMessage("Nothing to import")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = withContext(Dispatchers.Default) { parseAny(text, nameHint) }
                val outcome = repository.importProfiles(result.first)
                val first = result.first.firstOrNull()
                if (first != null && repository.settings.value.selectedProfileId == null) {
                    repository.selectProfile(first.id)
                }
                postMessage("Imported: ${outcome.summary}${result.second?.let { " ($it)" } ?: ""}")
            } catch (e: ConfigParseException) {
                postMessage(e.message ?: "Could not parse the configuration")
            } catch (e: Exception) {
                postMessage("Import failed: ${e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun parseAny(text: String, nameHint: String?): Pair<List<Profile>, String?> {
        if (XrayJsonImport.looksLikeJson(text)) {
            return listOf(XrayJsonImport.parse(text, nameHint)) to "Xray JSON"
        }
        if (text.startsWith("[")) {
            val parsed = SubscriptionParser.parse(text, null, null)
            return parsed.profiles to parsed.format
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.size == 1) {
            return listOf(UriParser.parse(lines.first())) to "share link"
        }
        val profiles = ArrayList<Profile>()
        var failed = 0
        lines.forEach { line ->
            try {
                profiles.add(UriParser.parse(line))
            } catch (e: ConfigParseException) {
                failed++
            }
        }
        if (profiles.isEmpty()) throw ConfigParseException("No valid configuration link found")
        return profiles to (if (failed > 0) "$failed line(s) skipped" else null)
    }

    fun importQr(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val decoded = withContext(Dispatchers.IO) {
                    QrCode.decode(getApplication(), uri)
                }
                if (decoded.isNullOrBlank()) {
                    postMessage("No QR code found in that image")
                } else {
                    importText(decoded)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > XrayJsonImport.MAX_JSON_LENGTH * 4) {
                            throw ConfigParseException("File is too large to import")
                        }
                        String(bytes, Charsets.UTF_8)
                    }
                }
                if (text.isNullOrBlank()) postMessage("The selected file is empty") else importText(text)
            } catch (e: ConfigParseException) {
                postMessage(e.message)
            } catch (e: Exception) {
                postMessage("Could not read the file")
            } finally {
                _busy.value = false
            }
        }
    }

    // ------------------------------------------------------------------ profiles

    fun saveProfile(profile: Profile) {
        viewModelScope.launch {
            repository.upsertProfile(profile)
            postMessage("Saved ${profile.name}")
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            repository.deleteProfile(profile.id)
            postMessage("Deleted ${profile.name}")
        }
    }

    fun renameProfile(profile: Profile, name: String) {
        viewModelScope.launch { repository.renameProfile(profile.id, name) }
    }

    fun duplicateProfile(profile: Profile) {
        viewModelScope.launch {
            repository.duplicateProfile(profile.id)
            postMessage("Duplicated ${profile.name}")
        }
    }

    fun toggleFavorite(profile: Profile) {
        viewModelScope.launch { repository.setFavorite(profile.id, !profile.favorite) }
    }

    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            repository.selectProfile(profile.id)
            postMessage("Selected ${profile.name}")
        }
    }

    fun shareUri(profile: Profile): String = ShareUriBuilder.build(profile)

    fun testLatency(profile: Profile) {
        if (_testing.value.contains(profile.id)) return
        viewModelScope.launch {
            _testing.value = _testing.value + profile.id
            try {
                val result = LatencyTester.measure(getApplication(), profile, repository.settings.value)
                repository.recordLatency(profile.id, result, LatencyTester.TEST_TYPE)
            } finally {
                _testing.value = _testing.value - profile.id
            }
        }
    }

    fun testAll() {
        viewModelScope.launch {
            repository.profiles.value.forEach { profile ->
                if (_testing.value.contains(profile.id)) return@forEach
                _testing.value = _testing.value + profile.id
                val result = LatencyTester.measure(getApplication(), profile, repository.settings.value)
                repository.recordLatency(profile.id, result, LatencyTester.TEST_TYPE)
                _testing.value = _testing.value - profile.id
            }
            postMessage("Latency test finished")
        }
    }

    // ------------------------------------------------------------------ subscriptions

    fun addSubscription(name: String, url: String, autoRefreshHours: Int) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val safeUrl = withContext(Dispatchers.IO) { fetcher.validateUrl(url) }
                val subscription = Subscription(
                    name = name.ifBlank { "Subscription" },
                    url = safeUrl,
                    autoRefreshHours = autoRefreshHours
                )
                repository.addSubscription(subscription)
                refreshSubscription(subscription.id)
            } catch (e: ConfigParseException) {
                postMessage(e.message)
            } catch (e: Exception) {
                postMessage("Could not add the subscription: ${e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun refreshSubscription(id: String) {
        viewModelScope.launch {
            val subscription = repository.subscriptions.value.firstOrNull { it.id == id } ?: return@launch
            _busy.value = true
            try {
                val fetched = withContext(Dispatchers.IO) { fetcher.fetch(subscription.url) }
                val parsed = withContext(Dispatchers.Default) {
                    SubscriptionParser.parse(fetched.body, subscription.id, subscription.name)
                }
                repository.replaceSubscriptionProfiles(subscription.id, parsed.profiles)
                repository.updateSubscription(
                    subscription.copy(
                        lastUpdated = System.currentTimeMillis(),
                        lastError = null,
                        profileCount = parsed.profiles.size,
                        uploadBytes = fetched.userInfo?.uploadBytes ?: subscription.uploadBytes,
                        downloadBytes = fetched.userInfo?.downloadBytes ?: subscription.downloadBytes,
                        totalBytes = fetched.userInfo?.totalBytes ?: subscription.totalBytes,
                        expiryEpochSeconds = fetched.userInfo?.expiryEpochSeconds ?: subscription.expiryEpochSeconds
                    )
                )
                postMessage("${subscription.name}: ${parsed.profiles.size} configurations")
            } catch (e: Exception) {
                // Never destroy previously working data on a failed refresh.
                repository.updateSubscription(
                    subscription.copy(lastError = e.message ?: e.javaClass.simpleName)
                )
                postMessage("Refresh failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun refreshAllSubscriptions() {
        viewModelScope.launch {
            repository.subscriptions.value.filter { it.enabled }.forEach { refreshSubscription(it.id) }
        }
    }

    private fun refreshDueSubscriptions() {
        val globalHours = repository.settings.value.subscriptionAutoRefreshHours
        val now = System.currentTimeMillis()
        repository.subscriptions.value.forEach { subscription ->
            val hours = if (subscription.autoRefreshHours > 0) subscription.autoRefreshHours else globalHours
            if (hours <= 0) return@forEach
            val last = subscription.lastUpdated ?: 0L
            if (now - last >= hours * 3600_000L) refreshSubscription(subscription.id)
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            repository.deleteSubscription(subscription.id)
            postMessage("Removed ${subscription.name} and its configurations")
        }
    }

    // ------------------------------------------------------------------ connection

    fun connect(profileId: String?) {
        VpnController.start(getApplication(), profileId)
    }

    fun disconnect() {
        VpnController.stop(getApplication())
    }

    fun prepareVpnIntent(): Intent? = VpnController.prepareIntent(getApplication())

    // ------------------------------------------------------------------ settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun completeOnboarding() {
        updateSettings { it.copy(onboardingDone = true) }
    }

    fun clearLogs() = LogBus.clear()

    fun logText(): String = LogBus.exportText()

    fun clearAllData() {
        viewModelScope.launch {
            VpnController.stop(getApplication())
            repository.clearAllData()
            postMessage("All local data deleted")
        }
    }

    fun loadInstalledApps(): List<InstalledApp> {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(packageName: String): String {
        val pm = getApplication<Application>().packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun refreshPublicIp() {
        viewModelScope.launch {
            VpnStateHolder.setPublicIp(PublicIpLookup.fetch())
        }
    }

    fun exportProfilesJson(): String {
        val array = org.json.JSONArray()
        repository.profiles.value.forEach { profile ->
            array.put(
                JSONObject()
                    .put("name", profile.name)
                    .put("uri", ShareUriBuilder.build(profile))
            )
        }
        return JSONObject().put("exported", System.currentTimeMillis()).put("profiles", array).toString(2)
    }
}
