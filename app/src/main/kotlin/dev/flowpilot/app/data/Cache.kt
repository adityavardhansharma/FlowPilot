package dev.flowpilot.app.data

import android.content.Context
import androidx.room.*
import dev.flowpilot.core.api.*
import dev.flowpilot.core.chat.*
import dev.flowpilot.core.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
data class HomeSnapshot(val sessions: List<Session> = emptyList(), val projects: List<Project> = emptyList(), val cursor: String? = null)

@Entity(tableName = "snapshots", primaryKeys = ["server", "kind", "id"])
data class SnapshotRow(val server: String, val kind: String, val id: String, val json: String)

@Entity(tableName = "messages", primaryKeys = ["server", "session", "id"])
data class MessageRow(val server: String, val session: String, val id: String, val position: Int, val json: String)

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots WHERE server=:server AND kind=:kind AND id=:id")
    suspend fun read(server: String, kind: String, id: String): SnapshotRow?
    @Query("SELECT * FROM snapshots WHERE server=:server AND kind=:kind AND id=:id")
    fun observe(server: String, kind: String, id: String): Flow<SnapshotRow?>
    @Upsert suspend fun put(row: SnapshotRow)
    @Upsert suspend fun putMessages(rows: List<MessageRow>)
    @Query("SELECT * FROM messages WHERE server=:server AND session=:session ORDER BY position")
    suspend fun messages(server: String, session: String): List<MessageRow>
    @Query("DELETE FROM messages WHERE server=:server AND session=:session AND id IN (:ids)")
    suspend fun deleteMessages(server: String, session: String, ids: List<String>)
    @Query("DELETE FROM snapshots WHERE server=:server") suspend fun clearSnapshots(server: String)
    @Query("DELETE FROM messages WHERE server=:server") suspend fun clearMessages(server: String)
}

@Database(entities = [SnapshotRow::class, MessageRow::class], version = 1, exportSchema = true)
abstract class OfflineDatabase : RoomDatabase() { abstract fun snapshots(): SnapshotDao }

/** Transactional history and applied cursor storage. Legacy JSON snapshots migrate on first read. */
class Cache(context: Context, databaseName: String = "flowpilot-offline.db") : ChatStorage, HomeStorage {
    private val legacy = File(context.filesDir, "cache")
    private val db = Room.databaseBuilder(context.applicationContext, OfflineDatabase::class.java, databaseName).build()
    private val dao = db.snapshots()

    fun observeHome(server: String): Flow<HomeSnapshot?> = dao.observe(server, "home", "home").map { row ->
        row?.let { OpenCodeJson.decodeFromString(HomeSnapshot.serializer(), it.json) }
    }

    override suspend fun readHome(server: String): HomeSnapshot? = withContext(Dispatchers.IO) {
        dao.read(server, "home", "home")?.let { return@withContext OpenCodeJson.decodeFromString(HomeSnapshot.serializer(), it.json) }
        legacyFile(server, "home.json").takeIf { it.exists() }?.let { file ->
            catching { OpenCodeJson.decodeFromString(HomeSnapshot.serializer(), file.readText()) }.getOrNull()?.also {
                writeHome(server, it)
                file.delete()
            }
        }
    }

    override suspend fun writeHome(server: String, snapshot: HomeSnapshot) = withContext(Dispatchers.IO) {
        dao.put(SnapshotRow(server, "home", "home", OpenCodeJson.encodeToString(HomeSnapshot.serializer(), snapshot)))
    }

    override suspend fun readChat(server: String, session: String): SavedChat? = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.read(server, "chat", session)?.let { row ->
                val saved = OpenCodeJson.decodeFromString(SavedChat.serializer(), row.json)
                val entries = dao.messages(server, session).map { OpenCodeJson.decodeFromString(ChatEntry.serializer(), it.json) }
                return@withTransaction saved.copy(chat = saved.chat.copy(entries = entries))
            }
            val file = legacyFile(server, "chat-$session.json")
            if (!file.exists()) return@withTransaction null
            catching {
                val raw = OpenCodeJson.decodeFromString(ListSerializer(kotlinx.serialization.json.JsonObject.serializer()), file.readText())
                SavedChat(ChatState(session, entries = ChatReducer.entriesFrom(raw)))
            }.getOrNull()?.also { writeChat(server, it); file.delete() }
        }
    }

    override suspend fun writeChat(server: String, snapshot: SavedChat) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val id = snapshot.chat.sessionID
            // Rows allow future indexed search; upsert only changed messages instead of rewriting every row.
            val previous = dao.messages(server, id).associateBy { it.id }
            val rows = snapshot.chat.entries.mapIndexed { index, entry ->
                MessageRow(server, id, entry.id, index, OpenCodeJson.encodeToString(ChatEntry.serializer(), entry))
            }
            dao.putMessages(rows.filter { previous[it.id] != it })
            val retained = rows.mapTo(HashSet()) { it.id }
            (previous.keys - retained).toList().chunked(400).forEach { dao.deleteMessages(server, id, it) }
            dao.put(SnapshotRow(server, "chat", id, OpenCodeJson.encodeToString(SavedChat.serializer(), snapshot.copy(chat = snapshot.chat.copy(entries = emptyList())))))
        }
    }

    suspend fun readValue(server: String, kind: String, id: String): String? = dao.read(server, kind, id)?.json
    suspend fun writeValue(server: String, kind: String, id: String, json: String) = dao.put(SnapshotRow(server, kind, id, json))

    suspend fun clear(server: String) = withContext(Dispatchers.IO) {
        db.withTransaction { dao.clearSnapshots(server); dao.clearMessages(server) }
        legacyFile(server, "home.json").parentFile?.deleteRecursively()
        Unit
    }

    fun close() = db.close()

    private fun legacyFile(server: String, name: String) = File(File(legacy, server.replace(Regex("[^A-Za-z0-9_.-]"), "_")), name)
}
