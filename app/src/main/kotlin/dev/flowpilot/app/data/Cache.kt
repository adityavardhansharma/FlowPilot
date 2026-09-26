package dev.flowpilot.app.data

import android.content.Context
import dev.flowpilot.core.api.OpenCodeJson
import dev.flowpilot.core.api.Project
import dev.flowpilot.core.api.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import java.io.File

@Serializable
data class HomeSnapshot(val sessions: List<Session> = emptyList(), val projects: List<Project> = emptyList(), val cursor: String? = null)

/**
 * A small JSON file cache, one folder per server, so Home and recently opened chats paint instantly.
 * Writes are atomic (temp file then rename) so a crash never leaves a half file behind.
 */
class Cache(context: Context) {
    private val root = File(context.filesDir, "cache")
    private val messages = ListSerializer(JsonObject.serializer())

    private fun dir(server: String) = File(root, server.replace(Regex("[^A-Za-z0-9_.-]"), "_")).apply { mkdirs() }

    suspend fun readHome(server: String): HomeSnapshot? = read(File(dir(server), "home.json")) {
        OpenCodeJson.decodeFromString(HomeSnapshot.serializer(), it)
    }

    suspend fun writeHome(server: String, snapshot: HomeSnapshot) =
        write(File(dir(server), "home.json"), OpenCodeJson.encodeToString(HomeSnapshot.serializer(), snapshot))

    suspend fun readMessages(server: String, session: String): List<JsonObject>? = read(File(dir(server), "chat-$session.json")) {
        OpenCodeJson.decodeFromString(messages, it)
    }

    /** Keeps the newest page of raw messages (newest first, as the server returns them). */
    suspend fun writeMessages(server: String, session: String, newestFirst: List<JsonObject>) =
        write(File(dir(server), "chat-$session.json"), OpenCodeJson.encodeToString(messages, newestFirst.take(60)))

    suspend fun clear(server: String) = withContext(Dispatchers.IO) { dir(server).deleteRecursively(); Unit }

    private suspend fun <T> read(file: File, parse: (String) -> T): T? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else runCatching { parse(file.readText()) }.getOrNull()
    }

    private suspend fun write(file: File, text: String) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
        Unit
    }
}
