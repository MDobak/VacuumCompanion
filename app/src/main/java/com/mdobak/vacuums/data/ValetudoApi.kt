package com.mdobak.vacuums.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

class ApiException(val code: Int, message: String) : IOException(message)

/** Thin client for the Valetudo REST API (v2). */
class ValetudoApi(host: String) {
    val baseUrl: String = normalizeBaseUrl(host)
    private val api = "$baseUrl/api/v2"

    suspend fun get(path: String): JsonElement = parse(request("GET", path, null))

    suspend fun getText(path: String): String = request("GET", path, null)

    suspend fun getBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(path)).get().build()
        client.newCall(req).await().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, r.body.string().ifBlank { "HTTP ${r.code}" })
            r.body.bytes()
        }
    }

    suspend fun put(path: String, body: JsonElement? = null): JsonElement = parse(request("PUT", path, body))

    suspend fun post(path: String, body: JsonElement?): JsonElement = parse(request("POST", path, body))

    suspend fun delete(path: String): JsonElement = parse(request("DELETE", path, null))

    fun url(path: String): String = if (path.startsWith("http")) path else "$api/${path.trimStart('/')}"

    private fun parse(text: String): JsonElement =
        if (text.isBlank()) JsonNull else runCatching { AppJson.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }

    private suspend fun request(method: String, path: String, body: JsonElement?): String = withContext(Dispatchers.IO) {
        val rb = when {
            body != null -> AppJson.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_TYPE)
            method == "PUT" || method == "POST" -> "{}".toRequestBody(JSON_TYPE)
            else -> null
        }
        val req = Request.Builder().url(url(path)).method(method, rb).header("Accept", "application/json").build()
        client.newCall(req).await().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw ApiException(r.code, text.ifBlank { "HTTP ${r.code}" }.take(300))
            text
        }
    }

    /** Server-sent events. Emits the data of each event and fails when the connection drops. */
    fun sse(path: String): Flow<String> = callbackFlow {
        val req = Request.Builder().url(url(path)).header("Accept", "text/event-stream").build()
        val source = EventSources.createFactory(sseClient).newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                trySend(data)
            }

            override fun onClosed(eventSource: EventSource) {
                close(IOException("Stream closed"))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("Stream failed (${response?.code})"))
            }
        })
        awaitClose { source.cancel() }
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val sseClient: OkHttpClient = client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build()

        val probeClient: OkHttpClient = client.newBuilder()
            .connectTimeout(700, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()

        fun normalizeBaseUrl(host: String): String {
            var h = host.trim().trimEnd('/')
            if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
            return h
        }
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, r, _ -> r.close() }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

fun jsonObj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to v.toJson() })

fun jsonArr(items: List<Any?>): JsonArray = JsonArray(items.map { it.toJson() })

@Suppress("UNCHECKED_CAST")
fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject((this as Map<String, Any?>).mapValues { it.value.toJson() })
    is List<*> -> JsonArray(this.map { it.toJson() })
    else -> JsonPrimitive(toString())
}

val JsonElement?.obj: JsonObject? get() = this as? JsonObject
val JsonElement?.arr: JsonArray? get() = this as? JsonArray
val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
val JsonElement?.int: Int? get() = (this as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
val JsonElement?.dbl: Double? get() = (this as? JsonPrimitive)?.content?.toDoubleOrNull()
val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
val JsonElement?.strList: List<String> get() = arr?.mapNotNull { it.str } ?: emptyList()
