package com.mdobak.vacuums.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import java.net.Inet4Address
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class FoundRobot(
    val host: String,
    val name: String,
    val model: String,
    val manufacturer: String,
)

/** Saved robots, kept in SharedPreferences as JSON. */
class RobotStore(context: Context) {
    private val prefs = context.getSharedPreferences("robots", Context.MODE_PRIVATE)
    private val _robots = MutableStateFlow(load())
    val robots: StateFlow<List<SavedRobot>> = _robots

    var lastHost: String?
        get() = prefs.getString("last", null)
        set(v) { prefs.edit().putString("last", v).apply() }

    private fun load(): List<SavedRobot> = runCatching {
        AppJson.parseToJsonElement(prefs.getString("list", "[]")!!).arr!!.mapNotNull {
            SavedRobot(it["host"].str ?: return@mapNotNull null, it["name"].str ?: "", it["model"].str ?: "")
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<SavedRobot>) {
        _robots.value = list
        val arr = JsonArray(list.map { JsonObject(mapOf("host" to JsonPrimitive(it.host), "name" to JsonPrimitive(it.name), "model" to JsonPrimitive(it.model))) })
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun upsert(r: SavedRobot) = save(_robots.value.filterNot { it.host == r.host } + r)
    fun remove(host: String) {
        save(_robots.value.filterNot { it.host == host })
        if (lastHost == host) lastHost = null
    }
}

/** Finds Valetudo robots with mDNS and a scan of the local /24 subnet. */
class Discovery(private val context: Context) {
    private val _found = MutableStateFlow<List<FoundRobot>>(emptyList())
    val found: StateFlow<List<FoundRobot>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var job: Job? = null
    private var nsdListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start(scope: CoroutineScope) {
        stop()
        _found.value = emptyList()
        _scanning.value = true
        startNsd(scope)
        job = scope.launch {
            runCatching { scanSubnet() }
            _scanning.value = false
        }
    }

    fun stop() {
        job?.cancel(); job = null
        _scanning.value = false
        val nsd = context.getSystemService(NsdManager::class.java)
        nsdListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        nsdListener = null
        multicastLock?.let { runCatching { it.release() } }
        multicastLock = null
    }

    private fun add(r: FoundRobot) {
        _found.update { list -> if (list.any { it.host == r.host }) list else (list + r).sortedBy { it.host } }
    }

    private fun startNsd(scope: CoroutineScope) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        runCatching {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            multicastLock = wifi?.createMulticastLock("vacuums-discovery")?.apply { setReferenceCounted(false); acquire() }
        }
        val queue = ConcurrentLinkedQueue<NsdServiceInfo>()
        val resolving = AtomicBoolean(false)

        fun next() {
            if (!resolving.compareAndSet(false, true)) return
            val info = queue.poll() ?: run { resolving.set(false); return }
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving.set(false); next()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val addr = if (Build.VERSION.SDK_INT >= 34) serviceInfo.hostAddresses.firstOrNull { it is Inet4Address } ?: serviceInfo.hostAddresses.firstOrNull()
                    else @Suppress("DEPRECATION") serviceInfo.host
                    if (addr != null) {
                        val port = serviceInfo.port
                        val host = (addr.hostAddress ?: "") + if (port != 80 && port > 0) ":$port" else ""
                        scope.launch { probe(host)?.let { add(it) } }
                    }
                    resolving.set(false); next()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) { queue.add(serviceInfo); next() }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }
        nsdListener = listener
        runCatching { nsd.discoverServices("_valetudo._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun localIpv4(): Pair<String, Int>? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        return (la.address.hostAddress ?: return null) to la.prefixLength
    }

    private suspend fun scanSubnet() = withContext(Dispatchers.IO) {
        val (ip, _) = localIpv4() ?: return@withContext
        val prefix = ip.substringBeforeLast('.')
        val own = ip.substringAfterLast('.').toIntOrNull()
        val sem = Semaphore(48)
        (1..254).filter { it != own }.map { n ->
            async { sem.withPermit { probe("$prefix.$n")?.let { add(it) } } }
        }.awaitAll()
    }

    companion object {
        /** Returns robot data if [host] runs Valetudo, else null. */
        suspend fun probe(host: String): FoundRobot? = withContext(Dispatchers.IO) {
            val base = ValetudoApi.normalizeBaseUrl(host)
            runCatching {
                val req = Request.Builder().url("$base/api/v2/robot").build()
                ValetudoApi.probeClient.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val j = AppJson.parseToJsonElement(r.body.string())
                    val model = j["modelName"].str ?: return@use null
                    val manu = j["manufacturer"].str ?: ""
                    var name = ""
                    runCatching {
                        val cr = Request.Builder().url("$base/api/v2/valetudo/config/customizations").build()
                        ValetudoApi.probeClient.newCall(cr).execute().use { c ->
                            if (c.isSuccessful) name = AppJson.parseToJsonElement(c.body.string())["friendlyName"].str ?: ""
                        }
                    }
                    FoundRobot(host.removePrefix("http://").trimEnd('/'), name, model, manu)
                }
            }.getOrNull()
        }
    }
}
