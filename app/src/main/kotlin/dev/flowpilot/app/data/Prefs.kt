package dev.flowpilot.app.data

import dev.flowpilot.core.sync.catching

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.flowpilot.core.api.ModelRef
import dev.flowpilot.core.api.OpenCodeJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException

// A corrupt or unreadable file starts over empty instead of crashing every launch.
private val Context.store: DataStore<Preferences> by preferencesDataStore(
    name = "flowpilot",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** A paired computer. The secret is sealed by [SecretBox]; only the ciphertext is stored. */
@Serializable
data class SavedServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val sealedSecret: String,
    val version: String? = null,
    val addedAt: Long = 0,
)

enum class ThemeMode { System, Light, Dark }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val showReasoning: Boolean = true,
    val defaultQueue: Boolean = false,
)

/** Everything FlowPilot remembers on the phone, in one DataStore. */
class Prefs(private val context: Context) {
    private val ds get() = context.store
    private val data: Flow<Preferences>
        get() = ds.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    private object K {
        val servers = stringPreferencesKey("servers")
        val current = stringPreferencesKey("current_server")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val reasoning = booleanPreferencesKey("show_reasoning")
        val queue = booleanPreferencesKey("default_queue")
        fun pinned(server: String) = stringSetPreferencesKey("pinned.$server")
        fun hidden(server: String) = stringSetPreferencesKey("hidden_models.$server")
        fun shown(server: String) = stringSetPreferencesKey("shown_models.$server")
        fun recent(server: String) = stringPreferencesKey("recent_models.$server")
        fun lastModel(server: String) = stringPreferencesKey("last_model.$server")
        fun lastAgent(server: String) = stringPreferencesKey("last_agent.$server")
        fun lastChat(server: String) = stringPreferencesKey("last_chat.$server")
        fun draft(server: String, session: String) = stringPreferencesKey("draft.$server.$session")
    }

    private val serverList = ListSerializer(SavedServer.serializer())
    private val refList = ListSerializer(ModelRef.serializer())

    val servers: Flow<List<SavedServer>> = data.map { p ->
        p[K.servers]?.let { catching { OpenCodeJson.decodeFromString(serverList, it) }.getOrNull() }.orEmpty()
    }.distinctUntilChanged()

    val currentServerId: Flow<String?> = data.map { it[K.current] }.distinctUntilChanged()

    val settings: Flow<Settings> = data.map { p ->
        Settings(
            themeMode = p[K.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.System,
            dynamicColor = p[K.dynamic] ?: false,
            showReasoning = p[K.reasoning] ?: true,
            defaultQueue = p[K.queue] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun saveServer(server: SavedServer, makeCurrent: Boolean = true) = ds.edit { p ->
        val list = p[K.servers]?.let { catching { OpenCodeJson.decodeFromString(serverList, it) }.getOrNull() }.orEmpty()
        val existing = list.firstOrNull { it.baseUrl == server.baseUrl }
        val saved = if (existing == null) server else server.copy(id = existing.id, addedAt = existing.addedAt)
        val next = list.filterNot { it.id == saved.id || it.baseUrl == saved.baseUrl } + saved
        p[K.servers] = OpenCodeJson.encodeToString(serverList, next)
        if (makeCurrent) p[K.current] = saved.id
    }

    suspend fun removeServer(id: String) = ds.edit { p ->
        val list = p[K.servers]?.let { catching { OpenCodeJson.decodeFromString(serverList, it) }.getOrNull() }.orEmpty()
        val next = list.filterNot { it.id == id }
        p[K.servers] = OpenCodeJson.encodeToString(serverList, next)
        p.asMap().keys.filter { it.name.startsWith("draft.$id.") || it.name in setOf("pinned.$id", "hidden_models.$id", "shown_models.$id", "recent_models.$id", "last_model.$id", "last_agent.$id", "last_chat.$id") }.forEach { p.remove(it) }
        if (p[K.current] == id) {
            val fallback = next.lastOrNull()?.id
            if (fallback != null) p[K.current] = fallback else p.remove(K.current)
        }
    }

    suspend fun selectServer(id: String) = ds.edit { it[K.current] = id }

    suspend fun updateSettings(f: (Settings) -> Settings) {
        val next = f(settings.first())
        ds.edit { p ->
            p[K.theme] = next.themeMode.name
            p[K.dynamic] = next.dynamicColor
            p[K.reasoning] = next.showReasoning
            p[K.queue] = next.defaultQueue
        }
    }

    fun pinned(server: String): Flow<Set<String>> = data.map { it[K.pinned(server)].orEmpty() }.distinctUntilChanged()

    suspend fun togglePin(server: String, session: String) = ds.edit { p ->
        val cur = p[K.pinned(server)].orEmpty()
        p[K.pinned(server)] = if (session in cur) cur - session else cur + session
    }

    /**
     * Model visibility. A model is visible when it was shown explicitly, or when it was never hidden and
     * [ModelVisibility.defaultVisible] says so. Keys are `provider/model`.
     */
    fun visibility(server: String): Flow<ModelVisibility> = data.map {
        ModelVisibility(hidden = it[K.hidden(server)].orEmpty(), shown = it[K.shown(server)].orEmpty())
    }.distinctUntilChanged()

    suspend fun setVisible(server: String, keys: Collection<String>, visible: Boolean) = ds.edit { p ->
        val hidden = p[K.hidden(server)].orEmpty()
        val shown = p[K.shown(server)].orEmpty()
        p[K.hidden(server)] = if (visible) hidden - keys.toSet() else hidden + keys
        p[K.shown(server)] = if (visible) shown + keys else shown - keys.toSet()
    }

    fun recentModels(server: String): Flow<List<ModelRef>> = data.map { p ->
        p[K.recent(server)]?.let { catching { OpenCodeJson.decodeFromString(refList, it) }.getOrNull() }.orEmpty()
    }.distinctUntilChanged()

    fun lastModel(server: String): Flow<ModelRef?> = data.map { p ->
        p[K.lastModel(server)]?.let { catching { OpenCodeJson.decodeFromString(ModelRef.serializer(), it) }.getOrNull() }
    }.distinctUntilChanged()

    suspend fun useModel(server: String, ref: ModelRef) = ds.edit { p ->
        val list = p[K.recent(server)]?.let { catching { OpenCodeJson.decodeFromString(refList, it) }.getOrNull() }.orEmpty()
        val next = (listOf(ref) + list.filterNot { it.id == ref.id && it.providerID == ref.providerID }).take(3)
        p[K.recent(server)] = OpenCodeJson.encodeToString(refList, next)
        p[K.lastModel(server)] = OpenCodeJson.encodeToString(ModelRef.serializer(), ref)
    }

    fun lastAgent(server: String): Flow<String?> = data.map { it[K.lastAgent(server)] }.distinctUntilChanged()
    suspend fun useAgent(server: String, agent: String) = ds.edit { it[K.lastAgent(server)] = agent }

    fun lastChat(server: String): Flow<String?> = data.map { it[K.lastChat(server)] }.distinctUntilChanged()
    suspend fun setLastChat(server: String, session: String?) = ds.edit { p ->
        if (session == null) p.remove(K.lastChat(server)) else p[K.lastChat(server)] = session
    }

    suspend fun draft(server: String, session: String): String {
        var result = ""
        ds.edit { p ->
            val key = K.draft(server, session)
            val legacy = stringPreferencesKey("draft.$session")
            if (p[key] == null && p[K.current] == server) p[legacy]?.let { p[key] = it; p.remove(legacy) }
            result = p[key].orEmpty()
        }
        return result
    }
    suspend fun saveDraft(server: String, session: String, text: String) = ds.edit { p ->
        if (text.isBlank()) p.remove(K.draft(server, session)) else p[K.draft(server, session)] = text
    }
}

data class ModelVisibility(val hidden: Set<String> = emptySet(), val shown: Set<String> = emptySet()) {
    fun isVisible(key: String, defaultVisible: Boolean): Boolean = when (key) {
        in shown -> true
        in hidden -> false
        else -> defaultVisible
    }
}
