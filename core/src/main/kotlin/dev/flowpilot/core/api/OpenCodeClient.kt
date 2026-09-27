package dev.flowpilot.core.api

import dev.flowpilot.core.sync.catching

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val start = System.nanoTime()
            try { chain.proceed(chain.request().newBuilder().header("Authorization", auth).build()) }
            finally { dev.flowpilot.core.sync.Diagnostics.record("http.headers_ms", (System.nanoTime() - start) / 1_000_000) }
        }
        .build()

    /**
     * A client for long-lived streams. The server sends a heartbeat comment every 15 seconds, so a read that
     * waits 45 seconds means the connection died silently (Wi-Fi switch, phone asleep) and must be reopened.
     */
    val streamingHttp: OkHttpClient = http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(STREAM_READ_TIMEOUT_S, TimeUnit.SECONDS).build()

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

    suspend fun deleteSession(id: String) { call(Request.Builder().url(url("api/session/$id")).delete().build()) }

    /** Messages newest first; pass [cursor] for older pages. */
    suspend fun messages(sessionID: String, cursor: String? = null, limit: Int = 40): Pair<List<JsonObject>, Cursor> {
        val raw = getRaw("api/session/$sessionID/message", mapOf("order" to "desc", "limit" to "$limit", "cursor" to cursor))
        return withContext(Dispatchers.Default) { parseMessages(raw) }
    }

    private fun parseMessages(raw: String): Pair<List<JsonObject>, Cursor> {
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
        call(Request.Builder().url(url("api/session/$sessionID/inbox/$inboxID")).delete().build())
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
        call(Request.Builder().url(url("api/session/$sessionID/form/$formID")).delete().build())
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
        call(req)
    }

    suspend fun startShell(command: String, cwd: String, timeoutMs: Int? = null): ShellInfo =
        post<ShellBody, Located<ShellInfo>>("api/shell", ShellBody(command, cwd, timeoutMs), directory = cwd).data

    suspend fun shell(id: String, directory: String): ShellInfo = get<Located<ShellInfo>>("api/shell/$id", directory = directory).data

    suspend fun shellOutput(id: String, directory: String, cursor: Long = 0): ShellOutput =
        get<Located<ShellOutput>>("api/shell/$id/output", mapOf("cursor" to "$cursor", "limit" to "65536"), directory).data

    // ---- plumbing ----
    // Every read of a response body, and every JSON parse, runs off the caller's thread. Callers are view models
    // on the main thread, and OkHttp reads the body from the socket lazily: on Android a large response read
    // there throws NetworkOnMainThreadException.

    internal suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap(), directory: String? = null): T {
        val s = serializer<T>()
        val raw = getRaw(path, query, directory)
        return decodeAsync(s, raw)
    }

    suspend fun getRaw(path: String, query: Map<String, String?> = emptyMap(), directory: String? = null): String =
        call(Request.Builder().url(url(path, query, directory)).header("Accept", "application/json").get().build())

    internal suspend inline fun <reified B, reified T> post(path: String, body: B, directory: String? = null): T {
        val s = serializer<T>()
        val req = Request.Builder().url(url(path, directory = directory)).post(json.encodeToString(serializer<B>(), body).toRequestBody(JSON)).build()
        return decodeAsync(s, call(req))
    }

    internal suspend inline fun <reified B> postUnit(path: String, body: B) {
        call(Request.Builder().url(url(path)).post(json.encodeToString(serializer<B>(), body).toRequestBody(JSON)).build())
    }

    internal suspend inline fun <reified B> patchUnit(path: String, body: B) {
        call(Request.Builder().url(url(path)).patch(json.encodeToString(serializer<B>(), body).toRequestBody(JSON)).build())
    }

    fun <T> decode(s: KSerializer<T>, raw: String): T = json.decodeFromString(s, raw)

    suspend fun <T> decodeAsync(s: KSerializer<T>, raw: String): T = withContext(Dispatchers.Default) { decode(s, raw) }

    /**
     * Sends [request] and returns the whole body as text, doing all network I/O on [Dispatchers.IO].
     * A GET that couldn't reach the server is tried once more, which rides out a Wi-Fi blip or a stale pooled connection.
     */
    suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        val attempts = if (request.method == "GET") 2 else 1
        var failure: ApiException.Unreachable? = null
        repeat(attempts) { i ->
            try {
                return@withContext readBody(request)
            } catch (e: ApiException.Unreachable) {
                failure = e
                if (i + 1 < attempts) delay(400)
            }
        }
        throw failure!!
    }

    private suspend fun readBody(request: Request): String = try {
        http.newCall(request).awaitBody()
    } catch (e: ApiException) {
        throw e
    } catch (e: IOException) {
        // The connection dropped while the body was streaming in.
        throw ApiException.Unreachable(e)
    }

    /** The raw response, for callers that stream it. Read its body off the main thread, or use [call]. */
    suspend fun send(request: Request): Response = withContext(Dispatchers.IO) { sendBlockingBody(request) }

    private suspend fun sendBlockingBody(request: Request): Response {
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw ApiException.Unreachable(e)
        }
        if (response.code == 401) { response.close(); throw ApiException.Unauthorized() }
        if (!response.isSuccessful) {
            response.use { throw ApiException.Http(it.code, it.body.string()) }
        }
        return response
    }

    companion object {
        const val STREAM_READ_TIMEOUT_S = 45L
        val JSON = "application/json".toMediaType()
        val OCTET = "application/octet-stream".toMediaType()

        /**
         * Redeems a pairing link printed by `opencode pair` (`http://host:port/auth/connect/CODE`)
         * and returns the endpoint with its 30-day token.
         */
        suspend fun redeemPairingLink(link: String, client: OkHttpClient = OkHttpClient()): ServerEndpoint = withContext(Dispatchers.IO) {
            val parsed = PairingLink.parse(link) ?: throw IllegalArgumentException("That code isn't an OpenCode pairing link.")
            val req = Request.Builder().url(parsed.redeemUrl).header("Accept", "application/json").get().build()
            val response = try { client.newCall(req).await() } catch (e: IOException) { throw ApiException.Unreachable(e) }
            response.use {
                if (it.code == 404 || it.code == 401 || it.code == 410) throw ApiException.Http(it.code, "This pairing code expired or was already used. Run opencode pair again.")
                if (!it.isSuccessful) throw ApiException.Http(it.code, it.body.string())
                val token = OpenCodeJson.decodeFromString(PairResponse.serializer(), it.body.string()).token
                ServerEndpoint(parsed.baseUrl, token)
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
            val url = catching { text.toHttpUrl() }.getOrNull() ?: return null
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
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { catching { cancel() } }
}

internal fun RequestBody.Companion.empty(): RequestBody = ByteArray(0).toRequestBody(null)

@Suppress("unused")
private fun JsonArray.objects() = map { it.jsonObject }

/** Cancellation owns the call until the entire body has been consumed, not only its headers. */
private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.use {
                    if (it.code == 401) throw ApiException.Unauthorized()
                    val text = it.body.string()
                    if (!it.isSuccessful) throw ApiException.Http(it.code, text)
                    text
                }
                cont.resume(body)
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    })
}
