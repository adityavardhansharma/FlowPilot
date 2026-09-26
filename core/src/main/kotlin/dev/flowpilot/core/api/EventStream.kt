package dev.flowpilot.core.api

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import kotlin.math.min

/** One frame from `/api/event` or a session log. [data] is left raw so unknown event types never break parsing. */
data class ServerEvent(
    val id: String?,
    val type: String,
    val created: Long?,
    val data: JsonObject,
    val seq: Long?,
    val aggregateID: String?,
    val directory: String?,
) {
    val sessionID: String? get() = (data["sessionID"] as? JsonPrimitive)?.contentOrNullSafe() ?: aggregateID

    companion object {
        fun parse(raw: String): ServerEvent? = runCatching {
            val obj = OpenCodeJson.parseToJsonElement(raw).jsonObject
            val durable = obj["durable"] as? JsonObject
            ServerEvent(
                id = (obj["id"] as? JsonPrimitive)?.contentOrNullSafe(),
                type = obj["type"]!!.jsonPrimitive.content,
                created = (obj["created"] as? JsonPrimitive)?.longOrNull,
                data = (obj["data"] as? JsonObject) ?: JsonObject(emptyMap()),
                seq = (durable?.get("seq") as? JsonPrimitive)?.longOrNull,
                aggregateID = (durable?.get("aggregateID") as? JsonPrimitive)?.contentOrNullSafe(),
                directory = ((obj["location"] as? JsonObject)?.get("directory") as? JsonPrimitive)?.contentOrNullSafe(),
            )
        }.getOrNull()
    }
}

internal fun JsonPrimitive.contentOrNullSafe(): String? = if (this is kotlinx.serialization.json.JsonNull) null else content

sealed interface StreamSignal {
    /** The stream (re)opened. Anything missed while it was down must be refetched. */
    data object Connected : StreamSignal
    data class Event(val event: ServerEvent) : StreamSignal
    data class Disconnected(val error: Throwable?) : StreamSignal
}

/**
 * The global event stream as a cold Flow that reconnects forever with capped backoff.
 * It emits [StreamSignal.Connected] when `server.connected` arrives.
 */
fun OpenCodeClient.events(path: String = "api/event", query: Map<String, String?> = emptyMap()): Flow<StreamSignal> = flow {
    var attempt = 0
    while (true) {
        var sawData = false
        try {
            openSse(path, query).collect { signal ->
                if (signal is StreamSignal.Event) sawData = true
                emit(signal)
            }
            emit(StreamSignal.Disconnected(null))
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(StreamSignal.Disconnected(e))
        }
        if (sawData) attempt = 0
        delay(min(30_000L, 500L shl min(attempt, 6)))
        attempt++
    }
}

/** A session's durable log after [afterSeq], then live. Used to catch up an open chat. */
fun OpenCodeClient.sessionLog(sessionID: String, afterSeq: Long?, follow: Boolean = true): Flow<StreamSignal> =
    openSse("api/experimental/session/$sessionID/log", mapOf("after" to afterSeq?.toString(), "follow" to follow.toString()))
        .retryWhen { _, attempt -> attempt < 1 }

private fun OpenCodeClient.openSse(path: String, query: Map<String, String?>): Flow<StreamSignal> = callbackFlow {
    val request = Request.Builder().url(url(path, query)).header("Accept", "text/event-stream").build()
    val listener = object : EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            val event = ServerEvent.parse(data) ?: return
            if (event.type == "server.connected") trySendBlocking(StreamSignal.Connected)
            trySendBlocking(StreamSignal.Event(event))
        }

        override fun onClosed(eventSource: EventSource) { channel.close() }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            val error = when (response?.code) {
                401 -> ApiException.Unauthorized()
                null -> t ?: ApiException.Unreachable(IllegalStateException("stream closed"))
                else -> ApiException.Http(response.code, "")
            }
            channel.close(error)
        }
    }
    val source = EventSources.createFactory(streamingHttp).newEventSource(request, listener)
    awaitClose { source.cancel() }
}.buffer(capacity = 1024)
