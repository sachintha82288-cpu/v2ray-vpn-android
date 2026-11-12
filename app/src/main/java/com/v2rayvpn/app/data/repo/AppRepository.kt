package com.v2rayvpn.app.data.repo

import com.v2rayvpn.app.core.log.LogBus
import com.v2rayvpn.app.data.store.AppStore
import com.v2rayvpn.app.data.store.profileFromJson
import com.v2rayvpn.app.data.store.settingsFromJson
import com.v2rayvpn.app.data.store.subscriptionFromJson
import com.v2rayvpn.app.data.store.toJson
import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ImportOutcome(val added: Int, val duplicates: Int, val skipped: Int) {
    val summary: String
        get() = buildString {
            append("$added added")
            if (duplicates > 0) append(", $duplicates already present")
            if (skipped > 0) append(", $skipped skipped")
        }
}

/**
 * In-memory state with write-through to the encrypted [AppStore].
 *
 * Mutations are serialised by the store's mutex, so the exposed StateFlows always reflect what is
 * on disk once the call returns.
 */
class AppRepository(private val store: AppStore) {

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun loadOnce() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
        }
        load()
        loaded = true
    }

    suspend fun load() {
        val doc = store.read()
        _profiles.value = doc.optJSONArray(AppStore.KEY_PROFILES)
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::profileFromJson) } }
            ?: emptyList()
        _subscriptions.value = doc.optJSONArray(AppStore.KEY_SUBSCRIPTIONS)
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::subscriptionFromJson) } }
            ?: emptyList()
        _settings.value = doc.optJSONObject(AppStore.KEY_SETTINGS)?.let(::settingsFromJson) ?: AppSettings()
        LogBus.i("Local store loaded: ${_profiles.value.size} profiles, ${_subscriptions.value.size} subscriptions")
    }

    fun profileById(id: String?): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun selectedProfile(): Profile? = profileById(_settings.value.selectedProfileId)

    suspend fun importProfiles(incoming: List<Profile>, subscriptionId: String? = null): ImportOutcome {
        if (incoming.isEmpty()) return ImportOutcome(0, 0, 0)
        var added = 0
        var duplicates = 0
        store.mutate { doc ->
            val current = _profiles.value.toMutableList()
            val known = current.map { it.fingerprintKey }.toMutableSet()
            incoming.forEach { candidate ->
                if (known.contains(candidate.fingerprintKey)) {
                    duplicates++
                } else {
                    known.add(candidate.fingerprintKey)
                    current.add(candidate)
                    added++
                }
            }
            _profiles.value = current
            writeProfiles(doc, current)
        }
        LogBus.i("Import: $added added, $duplicates duplicates")
        return ImportOutcome(added, duplicates, 0)
    }

    suspend fun replaceSubscriptionProfiles(
        subscriptionId: String,
        profiles: List<Profile>
    ): ImportOutcome {
        var added = 0
        var duplicates = 0
        store.mutate { doc ->
            val existing = _profiles.value
            val kept = existing.filter { it.subscriptionId != subscriptionId }
            // Preserve user metadata (name, favourite, group, measurements) for profiles that still exist.
            val byFingerprint = kept.associateBy { it.fingerprintKey }
            val merged = kept.toMutableList()
            profiles.forEach { fresh ->
                val previous = byFingerprint[fresh.fingerprintKey]
                if (previous != null) {
                    duplicates++
                    return@forEach
                }
                merged.add(fresh)
                added++
            }
            _profiles.value = merged
            writeProfiles(doc, merged)
        }
        return ImportOutcome(added, duplicates, 0)
    }

    suspend fun upsertProfile(profile: Profile) {
        store.mutate { doc ->
            val current = _profiles.value.toMutableList()
            val index = current.indexOfFirst { it.id == profile.id }
            if (index >= 0) current[index] = profile else current.add(profile)
            _profiles.value = current
            writeProfiles(doc, current)
        }
    }

    suspend fun deleteProfile(id: String) {
        store.mutate { doc ->
            val current = _profiles.value.filterNot { it.id == id }
            _profiles.value = current
            writeProfiles(doc, current)
            if (_settings.value.selectedProfileId == id) {
                val updated = _settings.value.copy(selectedProfileId = current.firstOrNull()?.id)
                _settings.value = updated
                doc.put(AppStore.KEY_SETTINGS, updated.toJson())
            }
        }
    }

    suspend fun renameProfile(id: String, name: String) {
        val profile = profileById(id) ?: return
        upsertProfile(profile.copy(name = name.trim().ifBlank { profile.name }))
    }

    suspend fun duplicateProfile(id: String): Profile? {
        val profile = profileById(id) ?: return null
        val copy = profile.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${profile.name} (copy)",
            createdAt = System.currentTimeMillis(),
            lastLatencyMs = null,
            lastTestAt = null,
            lastTestType = null,
            lastUsedAt = null,
            favorite = false
        )
        upsertProfile(copy)
        return copy
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        profileById(id)?.let { upsertProfile(it.copy(favorite = favorite)) }
    }

    suspend fun recordLatency(id: String, latencyMs: Long?, testType: String) {
        profileById(id)?.let {
            upsertProfile(
                it.copy(
                    lastLatencyMs = latencyMs,
                    lastTestAt = System.currentTimeMillis(),
                    lastTestType = testType
                )
            )
        }
    }

    suspend fun selectProfile(id: String?) {
        updateSettings { it.copy(selectedProfileId = id) }
        id?.let { profileId -> profileById(profileId)?.let { upsertProfile(it.copy(lastUsedAt = System.currentTimeMillis())) } }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.mutate { doc ->
            val updated = transform(_settings.value)
            _settings.value = updated
            doc.put(AppStore.KEY_SETTINGS, updated.toJson())
        }
    }

    suspend fun addSubscription(subscription: Subscription) {
        store.mutate { doc ->
            val current = _subscriptions.value + subscription
            _subscriptions.value = current
            writeSubscriptions(doc, current)
        }
    }

    suspend fun updateSubscription(subscription: Subscription) {
        store.mutate { doc ->
            val current = _subscriptions.value.map { if (it.id == subscription.id) subscription else it }
            _subscriptions.value = current
            writeSubscriptions(doc, current)
        }
    }

    suspend fun deleteSubscription(id: String) {
        store.mutate { doc ->
            val current = _subscriptions.value.filterNot { it.id == id }
            _subscriptions.value = current
            writeSubscriptions(doc, current)
            val remainingProfiles = _profiles.value.filterNot { it.subscriptionId == id }
            _profiles.value = remainingProfiles
            writeProfiles(doc, remainingProfiles)
        }
    }

    suspend fun clearAllData() {
        store.mutate { doc ->
            _profiles.value = emptyList()
            _subscriptions.value = emptyList()
            _settings.value = AppSettings(onboardingDone = true)
            doc.put(AppStore.KEY_PROFILES, JSONArray())
            doc.put(AppStore.KEY_SUBSCRIPTIONS, JSONArray())
            doc.put(AppStore.KEY_SETTINGS, _settings.value.toJson())
        }
        LogBus.w("All local data deleted by the user")
    }

    private fun writeProfiles(doc: JSONObject, profiles: List<Profile>) {
        doc.put(AppStore.KEY_PROFILES, JSONArray().apply { profiles.forEach { put(it.toJson()) } })
    }

    private fun writeSubscriptions(doc: JSONObject, subscriptions: List<Subscription>) {
        doc.put(AppStore.KEY_SUBSCRIPTIONS, JSONArray().apply { subscriptions.forEach { put(it.toJson()) } })
    }
}
