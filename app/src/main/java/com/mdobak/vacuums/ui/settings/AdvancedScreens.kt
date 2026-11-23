package com.mdobak.vacuums.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdobak.vacuums.AppViewModel
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.Labels
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AdvancedScreen(vm: AppViewModel) {
    val s = session
    val nav = LocalNav.current
    val caps by s.caps.collectAsState()
    var restore by remember { mutableStateOf(false) }
    val otherQuirks = rememberLoad(Cap.Quirks in caps) { if (Cap.Quirks in caps) loadQuirks(s).keys - KnownQuirks.placed else emptySet() }
    var forget by remember { mutableStateOf(false) }
    SubScreen("Advanced", onBack = { nav.popBackStack() }) {
        InfoNote("These settings are for experienced users. Incorrect values can stop the robot from working correctly.", Modifier.padding(top = 8.dp))
        SectionTitle("Robot")
        SettingsCard {
            if (Cap.VoicePack in caps) {
                SettingRow("Voice packs", Icons.Outlined.RecordVoiceOver, onClick = { nav.navigate("settings/voice") })
                RowDivider()
            }
            if (Cap.Quirks in caps && (otherQuirks.value?.isNotEmpty() == true)) {
                SettingRow("Other quirks", Icons.Outlined.Science, subtitle = "Model-specific settings", onClick = { nav.navigate("settings/quirks") })
                RowDivider()
            }
            SettingRow("Network services", Icons.Outlined.Lan, subtitle = "Time server and network discovery", onClick = { nav.navigate("settings/network") })
            RowDivider()
            SettingRow("Software update", Icons.Outlined.SystemUpdate, onClick = { nav.navigate("settings/updates") })
            RowDivider()
            SettingRow("System information", Icons.Outlined.Info, onClick = { nav.navigate("settings/system") })
            RowDivider()
            SettingRow("Log", Icons.AutoMirrored.Outlined.Article, onClick = { nav.navigate("settings/log") })
        }
        SectionTitle("Reset")
        SettingsCard {
            SettingRow("Restore default configuration", Icons.Outlined.RestartAlt, titleColor = MaterialTheme.colorScheme.error,
                subtitle = "Resets the Valetudo settings. The map stays.", onClick = { restore = true })
            RowDivider()
            SettingRow("Remove this robot from the app", Icons.Outlined.LinkOff, titleColor = MaterialTheme.colorScheme.error, onClick = { forget = true })
        }
    }
    if (restore) ConfirmDialog("Restore default configuration?",
        "Valetudo resets its configuration to the defaults and restarts. The robot settings and the map do not change.",
        confirm = "Restore", destructive = true, onDismiss = { restore = false }) {
        s.action("The configuration is reset.") { s.api.put("valetudo/action", jsonObj("action" to "restoreDefaultConfiguration")) }
    }
    if (forget) ConfirmDialog("Remove this robot?", "The app forgets this robot. The robot does not change. You can add it again.",
        confirm = "Remove", destructive = true, onDismiss = { forget = false }) {
        val host = s.host
        vm.close()
        vm.store.remove(host)
        nav.navigate("devices") { popUpTo(0) }
    }
}

@Composable
fun QuirksScreen() {
    val s = session
    val nav = LocalNav.current
    val quirks = rememberLoad(Unit) {
        loadQuirks(s).values.filter { it.id !in KnownQuirks.placed }.sortedBy { it.isTrigger }
    }
    var trigger by remember { mutableStateOf<Quirk?>(null) }
    SubScreen("Other quirks", onBack = { nav.popBackStack() }) {
        InfoNote("Quirks are settings and actions that only some models have. Change them only if you know their function.", Modifier.padding(top = 8.dp))
        Loaded(quirks) { list ->
            val settings = list.filter { !it.isTrigger }
            val actions = list.filter { it.isTrigger }
            if (settings.isNotEmpty()) {
                SectionTitle("Settings")
                SettingsCard {
                    settings.forEachIndexed { i, q ->
                        if (i > 0) RowDivider()
                        var value by remember(q) { mutableStateOf(q.value) }
                        ChoiceRow(q.title, value, q.options, subtitle = q.description) { v ->
                            val old = value
                            value = v
                            s.action { try { s.capPut(Cap.Quirks, jsonObj("id" to q.id, "value" to v)) } catch (e: Exception) { value = old; throw e } }
                        }
                    }
                }
            }
            if (actions.isNotEmpty()) {
                SectionTitle("Service actions")
                SettingsCard {
                    actions.forEachIndexed { i, q ->
                        if (i > 0) RowDivider()
                        SettingRow(q.title, subtitle = q.description, chevron = false, onClick = { trigger = q }) {
                            OutlinedButton(onClick = { trigger = q }) { Text("Run") }
                        }
                    }
                }
            }
        }
    }
    trigger?.let { q ->
        ConfirmDialog(q.title, q.description, confirm = "Run", onDismiss = { trigger = null }) {
            s.action("${q.title}: started.") { s.capPut(Cap.Quirks, jsonObj("id" to q.id, "value" to "trigger")) }
        }
    }
}

@Composable
fun NetworkScreen() {
    val s = session
    val nav = LocalNav.current
    val ntp = rememberLoad("ntp") { s.api.get("ntpclient/config") }
    val ntpStatus = rememberLoad("ntps") { s.api.get("ntpclient/status") }
    val adv = rememberLoad("adv") { s.api.get("networkadvertisement/config")["enabled"].bool == true }
    val advProps = rememberLoad("advp") { s.api.get("networkadvertisement/properties") }
    var edit by remember { mutableStateOf<String?>(null) }

    SubScreen("Network services", onBack = { nav.popBackStack() }) {
        SectionTitle("Time server (NTP)")
        SettingsCard {
            Loaded(ntp) { cfg ->
                fun save(changed: Map<String, Any?>) {
                    val body = jsonObj(
                        "enabled" to (changed["enabled"] ?: cfg["enabled"].bool),
                        "server" to (changed["server"] ?: cfg["server"].str),
                        "port" to (changed["port"] ?: cfg["port"].int),
                        "interval" to (changed["interval"] ?: cfg["interval"].dbl?.toLong()),
                        "timeout" to (changed["timeout"] ?: cfg["timeout"].dbl?.toLong()),
                    )
                    s.action("Saved.") { s.api.put("ntpclient/config", body); ntp.reload(); ntpStatus.reload() }
                }
                SwitchRow("Synchronize the time", cfg["enabled"].bool, Icons.Outlined.Schedule) { save(mapOf("enabled" to it)) }
                RowDivider()
                SettingRow("Server", Icons.Outlined.Dns, value = cfg["server"].str, onClick = { edit = "server" })
                RowDivider()
                SettingRow("Port", Icons.Outlined.Numbers, value = cfg["port"].int?.toString(), onClick = { edit = "port" })
                RowDivider()
                SettingRow("Interval", Icons.Outlined.Update, value = cfg["interval"].dbl?.let { "${(it / 3_600_000).toInt()} h" }, onClick = { edit = "interval" })
                ntpStatus.value?.let { st ->
                    RowDivider()
                    val cls = st["state"]["__class"].str ?: ""
                    SettingRow("Status", Icons.Outlined.Info,
                        value = when { "Synced" in cls -> "Synchronized"; "Error" in cls -> "Error"; "Disabled" in cls -> "Off"; else -> "Unknown" })
                    st["robotTime"].str?.let { t -> RowDivider(); SettingRow("Robot time", Icons.Outlined.AccessTime, value = formatTime(t)) }
                }
                when (edit) {
                    "server" -> TextInputDialog("Time server", cfg["server"].str ?: "", "Host name", validate = { if (it.isBlank()) "Enter a host name." else null },
                        onDismiss = { edit = null }) { save(mapOf("server" to it)) }
                    "port" -> TextInputDialog("Port", cfg["port"].int?.toString() ?: "123", "Port",
                        validate = { if (it.toIntOrNull() !in 1..65535) "Enter a number from 1 to 65535." else null },
                        onDismiss = { edit = null }) { save(mapOf("port" to it.toInt())) }
                    "interval" -> TextInputDialog("Interval in hours", ((cfg["interval"].dbl ?: 28_800_000.0) / 3_600_000).toInt().toString(), "Hours",
                        validate = { if ((it.toIntOrNull() ?: 0) < 1) "Enter a number of 1 or more." else null },
                        onDismiss = { edit = null }) { save(mapOf("interval" to it.toLong() * 3_600_000)) }
                }
            }
        }
        SectionTitle("Network discovery")
        SettingsCard {
            SwitchRow("Announce the robot on the network", adv.value, Icons.Outlined.Podcasts,
                "Uses mDNS and SSDP. This app uses it to find the robot.") { v ->
                adv.value = v
                s.action { s.api.put("networkadvertisement/config", jsonObj("enabled" to v)) }
            }
            advProps.value?.get("zeroconfHostname")?.str?.let { h ->
                RowDivider()
                SettingRow("Host name", Icons.Outlined.Badge, value = h)
            }
        }
    }
}

private fun formatTime(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault(iso)

@Composable
fun UpdatesScreen() {
    val s = session
    val nav = LocalNav.current
    val state = rememberLoad(Unit) { s.api.get("updater/state") }
    val cfg = rememberLoad("cfg") { s.api.get("updater/config")["updateProvider"].str }
    var confirmApply by remember { mutableStateOf(false) }
    // Update the state while the updater is busy.
    LaunchedEffect(state.value) {
        if (state.value?.get("busy")?.bool == true) { kotlinx.coroutines.delay(2000); state.reload() }
    }
    SubScreen("Software update", onBack = { nav.popBackStack() }) {
        Loaded(state) { st ->
            val cls = st["__class"].str ?: ""
            val busy = st["busy"].bool == true
            val current = st["currentVersion"].str ?: ""
            val next = st["version"].str
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingRow("Installed version", Icons.Outlined.Verified, value = current)
                RowDivider()
                SettingRow("Status", Icons.Outlined.Info, subtitle = when {
                    "Idle" in cls -> "No check yet."
                    "NoUpdateRequired" in cls -> "The software is up to date."
                    "ApprovalPending" in cls -> "Version $next is available."
                    "Downloading" in cls -> "Downloading version $next…"
                    "ApplyPending" in cls -> "Version $next is ready to install."
                    "Error" in cls -> "Error: ${st["message"].str ?: "unknown"}"
                    else -> Labels.of(cls.removePrefix("ValetudoUpdater").removeSuffix("State"))
                })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            }
            st["changelog"].str?.takeIf { it.isNotBlank() }?.let { log ->
                SectionTitle("Changes in $next")
                SettingsCard { Text(log.take(4000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            when {
                "ApprovalPending" in cls -> Button(onClick = { s.action { s.api.put("updater", jsonObj("action" to "download")); state.reload() } },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Download") }
                "ApplyPending" in cls -> Button(onClick = { confirmApply = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Install and restart") }
                else -> FilledTonalButton(onClick = { s.action { s.api.put("updater", jsonObj("action" to "check")); state.reload() } },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Check for updates") }
            }
        }
        SectionTitle("Options")
        SettingsCard {
            ChoiceRow("Update channel", cfg.value, listOf("github", "github_nightly"), Icons.AutoMirrored.Outlined.AltRoute,
                label = { if (it == "github") "Stable" else "Nightly (test builds)" }) { v ->
                cfg.value = v
                s.action { s.api.put("updater/config", jsonObj("updateProvider" to v)) }
            }
        }
    }
    if (confirmApply) ConfirmDialog("Install the update?", "The robot restarts. Do not remove the robot from the dock until it is online again.",
        confirm = "Install", onDismiss = { confirmApply = false }) {
        s.action("The update is installing.") { s.api.put("updater", jsonObj("action" to "apply")) }
    }
}

@Composable
fun SystemInfoScreen() {
    val s = session
    val nav = LocalNav.current
    val info by s.info.collectAsState()
    val data = rememberLoad(Unit) {
        listOf(
            runCatching { s.api.get("robot") }.getOrNull(),
            runCatching { s.api.get("robot/properties") }.getOrNull(),
            runCatching { s.api.get("valetudo/version") }.getOrNull(),
            runCatching { s.api.get("system/host/info") }.getOrNull(),
            runCatching { s.api.get("valetudo") }.getOrNull(),
        )
    }
    SubScreen("System information", onBack = { nav.popBackStack() }, actions = {
        IconButton(onClick = { data.reload() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
    }) {
        Loaded(data) { (robot, props, ver, host, valetudo) ->
            SectionTitle("Robot")
            SettingsCard {
                InfoRow("Manufacturer", robot["manufacturer"].str)
                InfoRow("Model", robot["modelName"].str)
                InfoRow("Firmware", props["firmwareVersion"].str)
                InfoRow("Implementation", robot["implementation"].str)
                InfoRow("System ID", valetudo["systemId"].str)
                InfoRow("Address", s.host, last = true)
            }
            SectionTitle("Valetudo")
            SettingsCard {
                InfoRow("Version", ver["release"].str ?: info.version)
                InfoRow("Commit", ver["commit"].str?.take(12), last = true)
            }
            if (host != null) {
                val mem = host["mem"]
                val total = mem["total"].dbl ?: 0.0
                val free = mem["free"].dbl ?: 0.0
                SectionTitle("Host")
                SettingsCard {
                    InfoRow("Host name", host["hostname"].str)
                    InfoRow("Architecture", host["arch"].str)
                    InfoRow("Running for", host["uptime"].dbl?.let { uptime(it) })
                    InfoRow("Load (1, 5, 15 min)", host["load"].let { l -> listOf("1", "5", "15").mapNotNull { k -> l[k].dbl?.let { "%.2f".format(it) } }.joinToString("  ") })
                    InfoRow("Memory used", if (total > 0) "${((total - free) / 1_048_576).toInt()} of ${(total / 1_048_576).toInt()} MB" else null)
                    InfoRow("Valetudo memory", mem["valetudo_current"].dbl?.let { "${(it / 1_048_576).toInt()} MB" }, last = true)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String?, last: Boolean = false) {
    SettingRow(title, value = value ?: "–")
    if (!last) RowDivider()
}

private fun uptime(sec: Double): String {
    val t = sec.toLong()
    val d = t / 86400; val h = (t % 86400) / 3600; val m = (t % 3600) / 60
    return listOfNotNull(if (d > 0) "$d d" else null, if (h > 0 || d > 0) "$h h" else null, "$m min").joinToString(" ")
}

@Composable
fun LogScreen() {
    val s = session
    val nav = LocalNav.current
    val level = rememberLoad("level") { s.api.get("valetudo/log/level") }
    val log = rememberLoad("log") { s.api.getText("valetudo/log/content").lines().takeLast(400).joinToString("\n") }
    SubScreen("Log", onBack = { nav.popBackStack() }, actions = {
        IconButton(onClick = { log.reload() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
    }) {
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            val lv = level.value
            ChoiceRow("Log level", lv?.get("current")?.str, lv?.get("presets")?.strList ?: emptyList(), Icons.Outlined.FilterList) { v ->
                s.action { s.api.put("valetudo/log/level", jsonObj("level" to v)); level.reload() }
            }
        }
        SectionTitle("Last entries")
        SettingsCard {
            Loaded(log) { text ->
                SelectionContainer {
                    Text(
                        text.ifBlank { "The log is empty." },
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
        }
    }
}
