package com.mdobak.vacuums.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap

enum class Connection { Connecting, Online, Offline }

/** Live connection to one robot. */
class RobotSession(val host: String, private val scope: CoroutineScope) {
    val api = ValetudoApi(host)

    private val _info = MutableStateFlow(RobotInfo())
    val info: StateFlow<RobotInfo> = _info
    private val _caps = MutableStateFlow<Set<String>>(emptySet())
    val caps: StateFlow<Set<String>> = _caps
    private val _state = MutableStateFlow(RobotState())
    val state: StateFlow<RobotState> = _state
    private val _map = MutableStateFlow<ValetudoMap?>(null)
    val map: StateFlow<ValetudoMap?> = _map
    private val _events = MutableStateFlow<List<ValetudoEvent>>(emptyList())
    val events: StateFlow<List<ValetudoEvent>> = _events
    private val _stats = MutableStateFlow<Map<String, Double>>(emptyMap())
    val stats: StateFlow<Map<String, Double>> = _stats
    private val _connection = MutableStateFlow(Connection.Connecting)
    val connection: StateFlow<Connection> = _connection
    private val _pendingMapChange = MutableStateFlow(false)
    val pendingMapChange: StateFlow<Boolean> = _pendingMapChange
    private val _cameraEnabled = MutableStateFlow(false)
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val propsCache = ConcurrentHashMap<String, JsonElement>()

    /** The last data of the screens, so that a screen can show it again immediately. */
    val uiCache = ConcurrentHashMap<String, Any>()
    private val jobs = mutableListOf<Job>()
    private var loaded = false

    fun has(cap: String) = cap in _caps.value

    fun resume() {
        if (jobs.isNotEmpty()) return
        jobs += scope.launch { initialLoad() }
        jobs += scope.launch { streamLoop("robot/state/attributes/sse") { _state.value = RobotState.parse(AppJson.parseToJsonElement(it)) } }
        jobs += scope.launch {
            streamLoop("robot/state/map/sse") { txt ->
                val m = withContext(Dispatchers.Default) { ValetudoMap.parse(AppJson.parseToJsonElement(txt)) }
                _map.value = m
            }
        }
        jobs += scope.launch {
            while (isActive) {
                refreshEvents()
                if (has(Cap.CurrentStats)) refreshStats()
                if (has(Cap.PendingMapChange)) runCatching { _pendingMapChange.value = api.get("robot/capabilities/${Cap.PendingMapChange}")["pending"].bool == true }
                delay(10_000)
            }
        }
    }

    fun pause() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun initialLoad() {
        _connection.value = Connection.Connecting
        while (scope.isActive && !tryLoad()) {
            _connection.value = Connection.Offline
            delay(4000)
        }
    }

    private suspend fun tryLoad(): Boolean {
        try {
            val robot = api.get("robot")
            val caps = api.get("robot/capabilities").strList.toSet()
            _caps.value = caps
            val custom = runCatching { api.get("valetudo/config/customizations")["friendlyName"].str ?: "" }.getOrDefault("")
            val valetudo = runCatching { api.get("valetudo") }.getOrNull()
            val version = runCatching { api.get("valetudo/version")["release"].str ?: "" }.getOrDefault("")
            _info.value = RobotInfo(
                manufacturer = robot["manufacturer"].str ?: "",
                model = robot["modelName"].str ?: "",
                friendlyName = custom,
                systemId = valetudo["systemId"].str ?: "",
                version = version,
            )
            _state.value = RobotState.parse(api.get("robot/state/attributes"))
            _connection.value = Connection.Online
            refreshMap()
            if (Cap.Duststreaming in caps) refreshCamera()
            loaded = true
            return true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return false
        }
    }

    private suspend fun streamLoop(path: String, onData: suspend (String) -> Unit) {
        while (scope.isActive) {
            try {
                api.sse(path).collect { data ->
                    _connection.value = Connection.Online
                    runCatching { onData(data) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            delay(3000)
            // Get a fresh copy in case events were missed.
            runCatching {
                _state.value = RobotState.parse(api.get("robot/state/attributes"))
                _connection.value = Connection.Online
            }.onFailure { _connection.value = Connection.Offline }
        }
    }

    suspend fun refreshMap() {
        runCatching {
            val txt = api.getText("robot/state/map")
            _map.value = withContext(Dispatchers.Default) { ValetudoMap.parse(AppJson.parseToJsonElement(txt)) }
        }
    }

    suspend fun refreshEvents() {
        runCatching { _events.value = ValetudoEvent.parse(api.get("events")) }
    }

    private suspend fun refreshStats() {
        runCatching {
            _stats.value = api.get("robot/capabilities/${Cap.CurrentStats}").arr
                ?.mapNotNull { (it["type"].str ?: return@mapNotNull null) to (it["value"].dbl ?: 0.0) }?.toMap() ?: emptyMap()
        }
    }

    suspend fun refreshCamera() {
        runCatching { _cameraEnabled.value = api.get("valetudo/config/duststreaming")["enabled"].bool == true }
    }

    fun setFriendlyName(name: String) { _info.value = _info.value.copy(friendlyName = name) }
    fun setPendingMapChange(v: Boolean) { _pendingMapChange.value = v }
    fun setCameraEnabled(v: Boolean) { _cameraEnabled.value = v }

    suspend fun props(cap: String): JsonElement =
        propsCache[cap] ?: api.get("robot/capabilities/$cap/properties").also { propsCache[cap] = it }

    suspend fun capGet(cap: String, sub: String = ""): JsonElement =
        api.get("robot/capabilities/$cap" + if (sub.isNotEmpty()) "/$sub" else "")

    suspend fun capPut(cap: String, body: JsonElement?, sub: String = ""): JsonElement =
        api.put("robot/capabilities/$cap" + if (sub.isNotEmpty()) "/$sub" else "", body)

    suspend fun setEnabled(cap: String, enabled: Boolean) {
        capPut(cap, jsonObj("action" to if (enabled) "enable" else "disable"))
    }

    suspend fun presets(cap: String): List<String> =
        propsCache["$cap/presets"]?.strList ?: api.get("robot/capabilities/$cap/presets").also { propsCache["$cap/presets"] = it }.strList

    suspend fun setPreset(cap: String, name: String) = capPut(cap, jsonObj("name" to name), "preset")

    suspend fun basic(action: String) = capPut(Cap.BasicControl, jsonObj("action" to action))

    suspend fun segments(): List<Segment> = capGet(Cap.Segmentation).arr?.mapNotNull {
        Segment(it["id"].str ?: return@mapNotNull null, it["name"].str ?: "", it["material"].str)
    } ?: emptyList()

    /** Runs [block]; reports success or failure as a short message. */
    fun action(success: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                success?.let { _messages.tryEmit(it) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.tryEmit(errorText(e))
            }
        }
    }

    fun message(text: String) { _messages.tryEmit(text) }

    companion object {
        fun errorText(e: Throwable): String = when (e) {
            is ApiException -> "The robot rejected the command: ${e.message?.take(160)}"
            is java.net.SocketTimeoutException, is java.net.ConnectException -> "The robot does not respond."
            else -> e.message ?: "Unknown error"
        }
    }
}
