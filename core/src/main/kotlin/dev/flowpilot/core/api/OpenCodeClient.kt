package dev.flowpilot.core.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val OpenCodeJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/** A server the phone is paired with. The secret is a pairing token or the server password. */
data class ServerEndpoint(val baseUrl: String, val secret: String) {
    val url: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()
}

sealed class ApiException(message: String) : IOException(message) {
    class Unauthorized : ApiException("Your pairing expired. Scan a new code to reconnect.")
    class Http(val code: Int, val body: String) : ApiException("The server answered $code${if (body.isNotBlank()) ": ${body.take(200)}" else ""}")
    class Unreachable(cause: Throwable) : ApiException("Can't reach your computer. Check that OpenCode is running, then retry.") {
        init { initCause(cause) }
    }
}

/**
 * Typed client for the OpenCode v2 API. Every call is main-safe: OkHttp runs it on its own threads.
 */
class OpenCodeClient(
    val endpoint: ServerEndpoint,
    baseClient: OkHttpClient = OkHttpClient(),
) {
    private val json = OpenCodeJson
    private val auth = Credentials.basic("opencode", endpoint.secret)

    val http: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", auth).build()) }
        .build()

    /** A client for long-lived streams: no read timeout. */
    val streamingHttp: OkHttpClient = http.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    fun url(path: String, query: Map<String, String?> = emptyMap(), directory: String? = null): HttpUrl {
        val b = endpoint.url.newBuilder().addPathSegments(path.trimStart('/'))
        if (directory != null) b.addQueryParameter("location[directory]", directory)
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    // ---- server ----
    suspend fun info(): ServerInfo = get("api/info")

    // ---- projects ----
    suspend fun projects(): List<Project> = get<List<Project>>("api/project").sortedByDescending { it.time.active }

    // ---- sessions ----
    suspend fun sessions(cursor: String? = null, limit: Int = 50, project: String? = null, search: String? = null): Page<Session> =
        get("api/session", mapOf("order" to "desc", "limit" to "$limit", "parentID" to "null", "cursor" to cursor, "project" to project, "search" to search))

    suspend fun session(id: String): Session = get<DataEnvelope<Session>>("api/session/$id").data

    suspend fun activeSessions(): Set<String> = get<DataEnvelope<JsonObject>>("api/session/active").data.keys

    suspend fun createSession(body: CreateSessionBody): Session = post<CreateSessionBody, DataEnvelope<Session>>("api/session", body).data

    suspend fun rename(id: String, title: String) { patchUnit("api/session/$id", RenameBody(title)) }

    suspend fun deleteSession(id: String) { send(Request.Builder().url(url("api/session/$id")).delete().build()).close() }

    /** Messages newest first; pass [cursor] for older pages. */
    suspend fun messages(sessionID: String, cursor: String? = null, limit: Int = 40): Pair<List<JsonObject>, Cursor> {
        val raw = getRaw("api/session/$sessionID/message", mapOf("order" to "desc", "limit" to "$limit", "cursor" to cursor))
        val obj = json.parseToJsonElement(raw).jsonObject
        val data = obj["data"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val cur = obj["cursor"]?.let { json.decodeFromJsonElement(Cursor.serializer(), it) } ?: Cursor()
        return data to cur
    }

    suspend fun prompt(sessionID: String, body: PromptBody): InboxItem = post<PromptBody, DataEnvelope<InboxItem>>("api/session/$sessionID/prompt", body).data

    /** Marks the chat read up to [idle], which clears its unread dot everywhere. */
    suspend fun markViewed(sessionID: String, idle: Long) { postUnit("api/session/$sessionID/view", ViewBody(idle)) }

    suspend fun interrupt(sessionID: String) { postUnit("api/session/$sessionID/interrupt", JsonObject(emptyMap())) }

    suspend fun inbox(sessionID: String): List<InboxItem> = get<DataEnvelope<List<InboxItem>>>("api/session/$sessionID/inbox").data

    suspend fun cancelInbox(sessionID: String, inboxID: String) {
        send(Request.Builder().url(url("api/session/$sessionID/inbox/$inboxID")).delete().build()).close()
    }

    suspend fun setModel(sessionID: String, model: ModelRef) { postUnit("api/session/$sessionID/model", ModelBody(model)) }

    suspend fun setAgent(sessionID: String, agent: String) { postUnit("api/session/$sessionID/agent", AgentBody(agent)) }

    // ---- human in the loop ----
    suspend fun permissions(sessionID: String): List<PermissionRequest> = get<DataEnvelope<List<PermissionRequest>>>("api/session/$sessionID/permission").data

    suspend fun pendingPermissions(directory: String): List<PermissionRequest> = get<LocatedList<PermissionRequest>>("api/permission/request", directory = directory).data

    suspend fun replyPermission(sessionID: String, requestID: String, decision: Decision) {
        postUnit("api/session/$sessionID/permission/$requestID/reply", PermissionReplyBody(decision.wire))
    }

    suspend fun pendingForms(directory: String): List<Form> = get<LocatedList<Form>>("api/form", directory = directory).data

    suspend fun forms(sessionID: String): List<Form> = get<DataEnvelope<List<Form>>>("api/session/$sessionID/form").data

    suspend fun replyForm(sessionID: String, formID: String, answer: JsonObject) {
        postUnit("api/session/$sessionID/form/$formID/reply", FormReplyBody(answer))
    }

    suspend fun cancelForm(sessionID: String, formID: String) {
        send(Request.Builder().url(url("api/session/$sessionID/form/$formID")).delete().build()).close()
    }

    // ---- catalog ----
    suspend fun models(directory: String? = null): List<Model> = get<LocatedList<Model>>("api/model", directory = directory).data

    suspend fun defaultModel(directory: String? = null): Model? = get<Located<Model?>>("api/model/default", directory = directory).data

    suspend fun agents(directory: String? = null): List<Agent> = get<LocatedList<Agent>>("api/agent", directory = directory).data

    // ---- filesystem and shell ----
    suspend fun listDir(directory: String, path: String? = null): List<FsEntry> =
        get<LocatedList<FsEntry>>("api/fs/list", mapOf("path" to path), directory = directory).data

    /** Writes [content] to [path] under [directory], creating parent folders. */
    /** Writes raw bytes to [path] relative to [directory]. [directory] must exist; missing folders in [path] are created. */
    suspend fun writeFile(directory: String, path: String, content: String) {
        val req = Request.Builder()
            .url(url("api/experimental/fs/write", mapOf("path" to path), directory))
            .post(content.toByteArray().toRequestBody(OCTET))
            .build()
        send(req).close()
    }

    suspend fun startShell(command: String, cwd: String, timeoutMs: Int? = null): ShellInfo =
        post<ShellBody, Located<ShellInfo>>("api/shell", ShellBody(command, cwd, timeoutMs), directory = cwd).data

    suspend fun shell(id: String, directory: String): ShellInfo = get<Located<ShellInfo>>("api/shell/$id", directory = directory).data

    suspend fun shellOutput(id: String, directory: String, cursor: Long = 0): ShellOutput =
        get<Located<ShellOutput>>("api/shell/$id/output", mapOf("cursor" to "$cursor", "limit" to "65536"), directory).data

    // ---- plumbing ----
    internal suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap(), directory: String? = null): T =
        decode(serializer(), getRaw(path, query, directory))

    suspend fun getRaw(path: String, query: Map<String, String?> = emptyMap(), directory: String? = null): String =
        send(Request.Builder().url(url(path, query, directory)).header("Accept", "application/json").get().build()).use { it.body.string() }

    internal suspend inline fun <reified B, reified T> post(path: String, body: B, directory: String? = null): T {
        val req = Request.Builder().url(url(path, directory = directory)).post(json.encodeToString(serializer<B>(), body).toRequestBody(JSON)).build()
        return decode(serializer(), send(req).use { it.body.string() })
    }

    internal suspend inline fun <reified B> postUnit(path: String, body: B) {
        send(Request.Builder().url(url(path)).post(json.encodeToString(serializer<B>(), body).toRequestBody(JSON)).build()).close()
    }

    internal suspend inline fun <reified B> patchUnit(path: String, body: B) {
        send(Request.Builder().url(url(path)).patch(json.encodeToString(serializer<B>(), body).toRequestBody(JSON)).build()).close()
    }

    fun <T> decode(s: KSerializer<T>, raw: String): T = json.decodeFromString(s, raw)

    suspend fun send(request: Request): Response {
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw ApiException.Unreachable(e)
        }
        if (response.code == 401) { response.close(); throw ApiException.Unauthorized() }
        if (!response.isSuccessful) {
            val body = response.body.string()
            response.close()
            throw ApiException.Http(response.code, body)
        }
        return response
    }

    companion object {
        val JSON = "application/json".toMediaType()
        val OCTET = "application/octet-stream".toMediaType()

        /**
         * Redeems a pairing link printed by `opencode pair` (`http://host:port/auth/connect/CODE`)
         * and returns the endpoint with its 30-day token.
         */
        suspend fun redeemPairingLink(link: String, client: OkHttpClient = OkHttpClient()): ServerEndpoint {
            val parsed = PairingLink.parse(link) ?: throw IllegalArgumentException("That code isn't an OpenCode pairing link.")
            val req = Request.Builder().url(parsed.redeemUrl).header("Accept", "application/json").get().build()
            val response = try { client.newCall(req).await() } catch (e: IOException) { throw ApiException.Unreachable(e) }
            response.use {
                if (it.code == 404 || it.code == 401 || it.code == 410) throw ApiException.Http(it.code, "This pairing code expired or was already used. Run opencode pair again.")
                if (!it.isSuccessful) throw ApiException.Http(it.code, it.body.string())
                val token = OpenCodeJson.decodeFromString(PairResponse.serializer(), it.body.string()).token
                return ServerEndpoint(parsed.baseUrl, token)
            }
        }
    }
}

/** A parsed `opencode pair` link. */
data class PairingLink(val baseUrl: String, val code: String) {
    val redeemUrl: String get() = "${baseUrl.trimEnd('/')}/auth/connect/$code"

    companion object {
        fun parse(raw: String): PairingLink? {
            val text = raw.trim()
            val url = runCatching { text.toHttpUrl() }.getOrNull() ?: return null
            val segs = url.pathSegments.filter { it.isNotEmpty() }
            val i = segs.indexOf("connect")
            if (i < 1 || segs[i - 1] != "auth" || i + 1 >= segs.size) return null
            val prefix = segs.subList(0, i - 1).joinToString("/")
            val base = url.newBuilder().encodedPath("/" + if (prefix.isEmpty()) "" else "$prefix/").query(null).fragment(null).build().toString().trimEnd('/')
            return PairingLink(base, segs[i + 1])
        }
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

internal fun RequestBody.Companion.empty(): RequestBody = ByteArray(0).toRequestBody(null)

@Suppress("unused")
private fun JsonArray.objects() = map { it.jsonObject }
