package com.labteto.dshmobile.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.labteto.dshmobile.core.wire.WireJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Persists remembered hosts and app settings. */
@Singleton
class HostsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val HOSTS = stringPreferencesKey("hosts_json")
        val AUTO_LAST = booleanPreferencesKey("auto_last")
        val AUTO_LAN = booleanPreferencesKey("auto_lan")
        val AUTO_LOOPBACK = booleanPreferencesKey("auto_loopback")
        val BACKGROUND = booleanPreferencesKey("background")
        val NOTIFY_TURN = booleanPreferencesKey("notify_turn")
        val NOTIFY_GOAL = booleanPreferencesKey("notify_goal")
        val NOTIFY_ACTION = booleanPreferencesKey("notify_action")
        val THEME = stringPreferencesKey("theme")
        val LOCALE = stringPreferencesKey("locale")
        val PORTS = stringPreferencesKey("ports_json")
    }

    private val hostsSerializer = ListSerializer(HostConfig.serializer())

    val hosts: Flow<List<HostConfig>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.HOSTS] ?: return@map emptyList()
        runCatching {
            WireJson.decodeFromString(hostsSerializer, raw).sortedByDescending { it.lastConnectedAt }
        }.getOrDefault(emptyList())
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val ports = prefs[Keys.PORTS]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(3080)
        AppSettings(
            autoConnectLast = prefs[Keys.AUTO_LAST] ?: true,
            autoConnectLan = prefs[Keys.AUTO_LAN] ?: false,
            autoConnectLoopback = prefs[Keys.AUTO_LOOPBACK] ?: true,
            keepConnectedInBackground = prefs[Keys.BACKGROUND] ?: false,
            notifyTurnComplete = prefs[Keys.NOTIFY_TURN] ?: true,
            notifyGoal = prefs[Keys.NOTIFY_GOAL] ?: true,
            notifyNeedsAction = prefs[Keys.NOTIFY_ACTION] ?: true,
            themePreference = prefs[Keys.THEME] ?: "system",
            localeOverride = prefs[Keys.LOCALE],
            knownPorts = ports,
        )
    }

    suspend fun settingsOnce(): AppSettings = settings.first()

    suspend fun upsertHost(config: HostConfig) {
        val current = hosts.first().toMutableList()
        current.removeAll { it.host == config.host && it.port == config.port }
        current.add(0, config)
        persist(current)
    }

    suspend fun touchHost(host: String, port: Int) {
        val current = hosts.first().map {
            if (it.host == host && it.port == port) it.copy(lastConnectedAt = System.currentTimeMillis()) else it
        }
        persist(current)
    }

    suspend fun rememberHost(name: String, host: String, port: Int, isLoopback: Boolean): HostConfig {
        val config = HostConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            host = host,
            port = port,
            isLoopback = isLoopback,
            lastConnectedAt = System.currentTimeMillis(),
        )
        upsertHost(config)
        return config
    }

    suspend fun removeHost(id: String) = persist(hosts.first().filterNot { it.id == id })

    suspend fun addKnownPort(port: Int) {
        val s = settingsOnce()
        val ports = (s.knownPorts + port).distinct().take(8)
        dataStore.edit { it[Keys.PORTS] = ports.joinToString(",") }
    }

    suspend fun setSetting(transform: (AppSettings) -> AppSettings) {
        val next = transform(settingsOnce())
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_LAST] = next.autoConnectLast
            prefs[Keys.AUTO_LAN] = next.autoConnectLan
            prefs[Keys.AUTO_LOOPBACK] = next.autoConnectLoopback
            prefs[Keys.BACKGROUND] = next.keepConnectedInBackground
            prefs[Keys.NOTIFY_TURN] = next.notifyTurnComplete
            prefs[Keys.NOTIFY_GOAL] = next.notifyGoal
            prefs[Keys.NOTIFY_ACTION] = next.notifyNeedsAction
            prefs[Keys.THEME] = next.themePreference
            next.localeOverride?.let { prefs[Keys.LOCALE] = it } ?: prefs.remove(Keys.LOCALE)
        }
    }

    private suspend fun persist(list: List<HostConfig>) {
        dataStore.edit { it[Keys.HOSTS] = WireJson.encodeToString(hostsSerializer, list) }
    }
}
