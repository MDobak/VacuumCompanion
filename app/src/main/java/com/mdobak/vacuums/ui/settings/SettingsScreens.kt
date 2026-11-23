package com.mdobak.vacuums.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdobak.vacuums.AppViewModel
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.Labels
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Appearance
import com.mdobak.vacuums.ui.theme.Theme
import java.util.TimeZone

/** A switch for a capability with the {enabled} state and enable/disable actions. */
@Composable
fun CapSwitch(cap: String, title: String, icon: ImageVector? = null, subtitle: String? = null) {
    val s = session
    val caps by s.caps.collectAsState()
    if (cap !in caps) return
    val st = rememberLoad(cap) { s.capGet(cap)["enabled"].bool == true }
    SwitchRow(title, st.value, icon, subtitle) { v ->
        st.value = v
        s.action { try { s.setEnabled(cap, v) } catch (e: Exception) { st.value = !v; throw e } }
    }
}

/** A preset (mode, suction or water). The value comes from the live robot state. */
@Composable
fun PresetChoice(cap: String, title: String, icon: ImageVector, value: (RobotState) -> String?) {
    val s = session
    val st by s.state.collectAsState()
    val opts = rememberLoad(cap, "presets") { s.presets(cap) }
    ChoiceRow(title, value(st), opts.value ?: emptyList(), icon) { v -> s.action { s.setPreset(cap, v) } }
}

/** A choice for the capability value [key], with the options in properties[[propsKey]]. */
@Composable
fun CapChoice(cap: String, key: String, propsKey: String, title: String, icon: ImageVector? = null, subtitle: String? = null, label: (String) -> String = { Labels.of(it) }) {
    val s = session
    val caps by s.caps.collectAsState()
    if (cap !in caps) return
    val st = rememberLoad(cap) { s.capGet(cap)[key].str }
    val opts = rememberLoad(cap, "props") { s.props(cap)[propsKey].strList }
    ChoiceRow(title, st.value, opts.value ?: emptyList(), icon, subtitle, label = label) { v ->
        val old = st.value
        st.value = v
        s.action { try { s.capPut(cap, jsonObj(key to v)) } catch (e: Exception) { st.value = old; throw e } }
    }
}

/** Rows for one card. A row is added only when its condition is true. */
class RowList { val rows = mutableListOf<@Composable () -> Unit>(); fun row(r: @Composable () -> Unit) { rows += r } }

@Composable
private fun ColumnScope.Rows(build: RowList.() -> Unit) {
    val list = RowList().apply(build)
    list.rows.forEachIndexed { i, r -> if (i > 0) RowDivider(); r() }
}

/** A titled card. It shows only when it has one or more rows. */
@Composable
private fun Section(title: String, build: RowList.() -> Unit) {
    val list = RowList().apply(build)
    if (list.rows.isEmpty()) return
    SectionTitle(title)
    SettingsCard { list.rows.forEachIndexed { i, r -> if (i > 0) RowDivider(); r() } }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = session
    val nav = LocalNav.current
    val caps by s.caps.collectAsState()
    val info by s.info.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val camera by s.cameraEnabled.collectAsState()
    var rename by remember { mutableStateOf(false) }
    val quirkState = rememberLoad(Cap.Quirks in caps) { if (Cap.Quirks in caps) loadQuirks(s) else emptyMap() }
    val q = quirkState.value ?: emptyMap()

    SubScreen("Settings", onBack = { nav.popBackStack() }) {
        Section("Robot") {
            row { SettingRow("Name", Icons.Outlined.Badge, value = info.friendlyName.ifBlank { info.model }, onClick = { rename = true }) }
            if (Cap.KeyLock in caps) row { CapSwitch(Cap.KeyLock, "Child lock", Icons.Outlined.Lock, "Disables the buttons on the robot.") }
            if (Cap.Consumables in caps) row { SettingRow("Maintenance", Icons.Outlined.Build, subtitle = "Brushes, filter and sensors", onClick = { nav.navigate("settings/consumables") }) }
            quirkRows(QuirkSection.Robot, q)
        }

        Section("Cleaning") {
            if (Cap.OperationMode in caps) row { PresetChoice(Cap.OperationMode, "Cleaning mode", Icons.Outlined.CleaningServices) { it.operationMode } }
            if (Cap.FanSpeed in caps) row { PresetChoice(Cap.FanSpeed, "Suction power", Icons.Outlined.Air) { it.fanSpeed } }
            if (Cap.WaterUsage in caps) row { PresetChoice(Cap.WaterUsage, "Water flow", Icons.Outlined.WaterDrop) { it.waterGrade } }
            if (Cap.CleanRoute in caps) row { CapChoice(Cap.CleanRoute, "route", "supportedRoutes", "Cleaning route", Icons.Outlined.Route) }
            if (Cap.ObstacleAvoidance in caps) row { CapSwitch(Cap.ObstacleAvoidance, "Obstacle avoidance", Icons.Outlined.Sensors) }
            if (Cap.PetObstacleAvoidance in caps) row { CapSwitch(Cap.PetObstacleAvoidance, "Pet waste avoidance", Icons.Outlined.Pets) }
            if (Cap.CollisionAvoidantNavigation in caps) row { CapSwitch(Cap.CollisionAvoidantNavigation, "Gentle navigation", Icons.Outlined.Shield, "Fewer collisions with furniture.") }
            if (Cap.FloorDirectionNavigation in caps) row { CapSwitch(Cap.FloorDirectionNavigation, "Follow the floor direction", Icons.Outlined.ViewWeek, "Cleans along wood floor boards.") }
            if (Cap.ObstacleImages in caps) row { CapSwitch(Cap.ObstacleImages, "Obstacle photos", Icons.Outlined.PhotoCamera, "Keeps photos of obstacles on the map.") }
            quirkRows(QuirkSection.Cleaning, q)
        }

        Section("Carpets") {
            if (Cap.CarpetMode in caps) row { CapSwitch(Cap.CarpetMode, "Carpet boost", Icons.Outlined.Air, "Increases the suction on carpets.") }
            if (Cap.CarpetSensorMode in caps) row { CapChoice(Cap.CarpetSensorMode, "mode", "supportedModes", "Carpet handling", Icons.Outlined.Waves,
                label = { when (it) { "off" -> "Off"; "avoid" -> "Avoid carpets"; "lift" -> "Lift the mop"; "detach" -> "Detach the mop"; else -> Labels.of(it) } }) }
            quirkRows(QuirkSection.Carpets, q)
        }

        Section("Mop") {
            if (Cap.MopExtension in caps) row { CapSwitch(Cap.MopExtension, "Mop extension", Icons.Outlined.OpenInFull, "Extends the mop along edges.") }
            if (Cap.MopFurnitureLegs in caps) row { CapSwitch(Cap.MopFurnitureLegs, "Mop around furniture legs", Icons.Outlined.TableRestaurant) }
            if (Cap.MopTwist in caps) row { CapSwitch(Cap.MopTwist, "Mop twist", Icons.Outlined.Rotate90DegreesCw, "Turns the robot to mop near edges.") }
            quirkRows(QuirkSection.Mop, q)
        }

        Section("Base station") {
            if (Cap.AutoEmptyInterval in caps) row { CapChoice(Cap.AutoEmptyInterval, "interval", "supportedIntervals", "Auto-empty", Icons.Outlined.DeleteSweep) }
            if (Cap.AutoEmptyDuration in caps) row { CapChoice(Cap.AutoEmptyDuration, "duration", "supportedDurations", "Auto-empty duration", Icons.Outlined.HourglassBottom) }
            if (Cap.MopWashTemperature in caps) row { CapChoice(Cap.MopWashTemperature, "temperature", "supportedTemperatures", "Mop wash temperature", Icons.Outlined.Thermostat) }
            if (Cap.MopAutoDrying in caps) row { CapSwitch(Cap.MopAutoDrying, "Dry mops after cleaning", Icons.Outlined.Air) }
            if (Cap.MopDryingTime in caps) row { CapChoice(Cap.MopDryingTime, "duration", "supportedDurations", "Drying time", Icons.Outlined.Timer,
                label = { if (it.endsWith("h")) it.dropLast(1) + " hours" else Labels.of(it) }) }
            quirkRows(QuirkSection.Dock, q)
        }

        Section("Base station service") {
            quirkRows(QuirkSection.DockService, q)
        }

        Section("Battery") {
            quirkRows(QuirkSection.Battery, q)
        }

        Section("Camera") {
            if (Cap.Duststreaming in caps) row {
                SwitchRow("Live camera stream", camera, Icons.Outlined.Videocam, "Lets this app show the camera image.") { v ->
                    s.setCameraEnabled(v)
                    s.action { s.api.put("valetudo/config/duststreaming", jsonObj("enabled" to v)) }
                }
            }
            if (Cap.CameraLight in caps) row { CapSwitch(Cap.CameraLight, "Camera light", Icons.Outlined.FlashlightOn) }
            if (Cap.Duststreaming in caps) row { SettingRow("Show camera", Icons.Outlined.Fullscreen, onClick = { nav.navigate("camera") }) }
        }

        Section("Sound") {
            if (Cap.SpeakerVolume in caps) row { VolumeRow(test = Cap.SpeakerTest in caps) }
        }

        Section("Home") {
            if (hasMapSettings(caps)) row { SettingRow("Map", Icons.Outlined.Map, subtitle = "Rooms, no-go zones and map tools", onClick = { nav.navigate("settings/map") }) }
            row { SettingRow("Schedules", Icons.Outlined.Schedule, onClick = { nav.navigate("schedules") }) }
            if (Cap.DoNotDisturb in caps) row { SettingRow("Do not disturb", Icons.Outlined.DoNotDisturbOn, onClick = { nav.navigate("settings/dnd") }) }
            if (Cap.WifiConfig in caps) row { SettingRow("Wi-Fi", Icons.Outlined.Wifi, onClick = { nav.navigate("settings/wifi") }) }
        }

        Section("App") {
            row {
                ChoiceRow("Appearance", appearance.name, Appearance.entries.map { it.name }, Icons.Outlined.DarkMode,
                    label = { when (it) { "System" -> "Same as the phone"; else -> it } }) { vm.setAppearance(Appearance.valueOf(it)) }
            }
        }

        Section("More") {
            row { SettingRow("Advanced", Icons.Outlined.Tune, subtitle = "Voice packs, quirks, network, updates, system", onClick = { nav.navigate("settings/advanced") }) }
        }
        Spacer(Modifier.height(24.dp))
        Text("${info.manufacturer} ${info.model}  ·  Valetudo ${info.version}", style = MaterialTheme.typography.bodySmall,
            color = Theme.colors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
    }

    if (rename) {
        TextInputDialog("Robot name", info.friendlyName, "Name",
            validate = { if (!Regex("^[a-zA-Z0-9 -]{0,24}$").matches(it)) "Use letters, numbers, spaces and hyphens. Maximum 24 characters." else null },
            onDismiss = { rename = false }) { name ->
            s.action("Name saved.") {
                s.api.put("valetudo/config/customizations", jsonObj("friendlyName" to name))
                s.setFriendlyName(name)
            }
        }
    }
}

/** The volume slider, with a button that plays a test sound. */
@Composable
private fun VolumeRow(test: Boolean) {
    val s = session
    val vol = rememberLoad(Cap.SpeakerVolume) { s.capGet(Cap.SpeakerVolume)["volume"].int ?: 0 }
    val v = vol.value
    var value by remember(v) { mutableFloatStateOf((v ?: 0).toFloat()) }
    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(Icons.AutoMirrored.Outlined.VolumeUp)
        Slider(value, onValueChange = { value = it }, valueRange = 0f..100f, enabled = v != null,
            modifier = Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
            onValueChangeFinished = { s.action { s.capPut(Cap.SpeakerVolume, jsonObj("action" to "set_volume", "value" to value.toInt())) } })
        Text("${value.toInt()}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.titleSmall)
        if (test) IconButton(onClick = { s.action { s.capPut(Cap.SpeakerTest, jsonObj("action" to "play_test_sound")) } }) {
            Icon(Icons.Outlined.PlayCircleOutline, "Play a test sound")
        }
    }
}

private val mapSettingCaps = setOf(
    Cap.SegmentEdit, Cap.SegmentRename, Cap.SegmentMaterial, Cap.Restrictions, Cap.Annotations,
    Cap.PendingMapChange, Cap.MappingPass, Cap.MapReset, Cap.PersistentMap,
)

/** True if the map settings screen has one or more items for this robot. */
fun hasMapSettings(caps: Set<String>) = caps.any { it in mapSettingCaps }

@Composable
fun MapSettingsScreen() {
    val s = session
    val nav = LocalNav.current
    val caps by s.caps.collectAsState()
    val pending by s.pendingMapChange.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmMapping by remember { mutableStateOf(false) }

    SubScreen("Map", onBack = { nav.popBackStack() }) {
        if (Cap.SegmentEdit in caps || Cap.SegmentRename in caps || Cap.SegmentMaterial in caps || Cap.Restrictions in caps || Cap.Annotations in caps) {
            SectionTitle("Edit the map")
            SettingsCard {
                Rows {
                    if (Cap.SegmentEdit in caps || Cap.SegmentRename in caps || Cap.SegmentMaterial in caps)
                        row { SettingRow("Rooms", Icons.Outlined.GridView, subtitle = "Rename, split, join and floor type", onClick = { nav.navigate("editor/rooms") }) }
                    if (Cap.Restrictions in caps)
                        row { SettingRow("No-go zones and walls", Icons.Outlined.Block, subtitle = "No-go zones, no-mop zones and virtual walls", onClick = { nav.navigate("editor/restrictions") }) }
                    if (Cap.Annotations in caps)
                        row { SettingRow("Floor features", Icons.Outlined.Stairs, subtitle = "Thresholds, curtains and ramps", onClick = { nav.navigate("editor/annotations") }) }
                }
            }
        }
        if (Cap.PendingMapChange in caps && pending) {
            SectionTitle("New map")
            SettingsCard {
                InfoNote("The robot found a new map. Use the new map or keep the old map.", Modifier.padding(start = 8.dp, top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { s.action { s.capPut(Cap.PendingMapChange, jsonObj("action" to "reject")); s.setPendingMapChange(false) } }) { Text("Keep old") }
                    TextButton(onClick = { s.action { s.capPut(Cap.PendingMapChange, jsonObj("action" to "accept")); s.setPendingMapChange(false) } }) { Text("Use new") }
                }
            }
        }
        if (Cap.MappingPass in caps || Cap.MapReset in caps || Cap.PersistentMap in caps) {
            SectionTitle("Map tools")
            SettingsCard {
                Rows {
                    if (Cap.PersistentMap in caps) row { CapSwitch(Cap.PersistentMap, "Keep the map", Icons.Outlined.Save, "When off, the robot makes a new map for each cleaning.") }
                    if (Cap.MappingPass in caps) row { SettingRow("Make a new map", Icons.Outlined.Explore, subtitle = "The robot drives through the home without cleaning", onClick = { confirmMapping = true }) }
                    if (Cap.MapReset in caps) row { SettingRow("Delete the map", Icons.Outlined.DeleteForever, titleColor = MaterialTheme.colorScheme.error,
                        subtitle = "Rooms, zones and schedules for rooms become invalid", onClick = { confirmReset = true }) }
                }
            }
        }
        if (Cap.SegmentEdit in caps) InfoNote("To add a room, split a large room into two rooms. The robot does not let you draw new rooms.")
    }
    if (confirmMapping) ConfirmDialog("Make a new map?", "The robot starts a mapping run. It does not clean during this run.", confirm = "Start",
        onDismiss = { confirmMapping = false }) { s.action("The mapping run started.") { s.capPut(Cap.MappingPass, jsonObj("action" to "start_mapping")) } }
    if (confirmReset) ConfirmDialog("Delete the map?", "The robot deletes the map, the rooms and all restrictions. You cannot undo this.", confirm = "Delete", destructive = true,
        onDismiss = { confirmReset = false }) { s.action("The map is deleted.") { s.capPut(Cap.MapReset, jsonObj("action" to "reset")); s.refreshMap() } }
}

@Composable
fun ConsumablesScreen() {
    val s = session
    val nav = LocalNav.current
    val list = rememberLoad(Unit) {
        val props = s.props(Cap.Consumables)["availableConsumables"].arr ?: emptyList()
        val max = props.associate { "${it["type"].str}/${it["subType"].str}" to it["maxValue"].int }
        s.capGet(Cap.Consumables).arr?.map {
            val type = it["type"].str ?: ""
            val sub = it["subType"].str
            Consumable(type, sub, it["remaining"]["value"].int ?: 0, it["remaining"]["unit"].str ?: "percent", max["$type/$sub"])
        } ?: emptyList()
    }
    var reset by remember { mutableStateOf<Consumable?>(null) }
    SubScreen("Maintenance", onBack = { nav.popBackStack() }) {
        Loaded(list) { items ->
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                items.forEachIndexed { i, c ->
                    if (i > 0) RowDivider()
                    ConsumableRow(c) { reset = c }
                }
            }
            InfoNote("Reset an item after you replace or clean it.")
        }
    }
    reset?.let { c ->
        val name = Labels.consumable(c.type, c.subType)
        ConfirmDialog("Reset $name?", "Do this after you replace or clean the part. The counter starts again from 100%.", confirm = "Reset",
            onDismiss = { reset = null }) {
            s.action("$name is reset.") {
                s.capPut(Cap.Consumables, jsonObj("action" to "reset"), listOfNotNull(c.type, c.subType?.takeIf { it != "none" }).joinToString("/"))
                list.reload()
            }
        }
    }
}

@Composable
private fun ConsumableRow(c: Consumable, onReset: () -> Unit) {
    val pct = when {
        c.unit == "percent" -> c.remaining
        c.maxValue != null && c.maxValue > 0 -> c.remaining * 100 / c.maxValue
        else -> null
    }
    val left = when (c.unit) {
        "minutes" -> if (c.remaining >= 60) "${c.remaining / 60} h left" else "${c.remaining} min left"
        "percent" -> "${c.remaining}% left"
        else -> "${c.remaining} ${c.unit}"
    }
    val low = pct != null && pct <= 10
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Labels.consumable(c.type, c.subType), style = MaterialTheme.typography.bodyLarge)
                Text(left, style = MaterialTheme.typography.bodySmall, color = if (low) MaterialTheme.colorScheme.error else Theme.colors.textSecondary)
            }
            if (pct != null) Text("$pct%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
            OutlinedButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Reset") }
        }
        if (pct != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { pct.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

private fun shiftTime(h: Int, m: Int, offset: Int): Pair<Int, Int> {
    val t = ((h * 60 + m + offset) % 1440 + 1440) % 1440
    return t / 60 to t % 60
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DndScreen() {
    val s = session
    val nav = LocalNav.current
    val offset = remember { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000 }
    val cfg = rememberLoad(Unit) { s.capGet(Cap.DoNotDisturb) }
    var picker by remember { mutableStateOf<String?>(null) }
    SubScreen("Do not disturb", onBack = { nav.popBackStack() }) {
        Loaded(cfg) { c ->
            var enabled by remember { mutableStateOf(c["enabled"].bool == true) }
            var start by remember { mutableStateOf(shiftTime(c["start"]["hour"].int ?: 22, c["start"]["minute"].int ?: 0, offset)) }
            var end by remember { mutableStateOf(shiftTime(c["end"]["hour"].int ?: 8, c["end"]["minute"].int ?: 0, offset)) }
            fun save() {
                val su = shiftTime(start.first, start.second, -offset)
                val eu = shiftTime(end.first, end.second, -offset)
                s.action {
                    s.capPut(Cap.DoNotDisturb, jsonObj(
                        "enabled" to enabled,
                        "start" to mapOf("hour" to su.first, "minute" to su.second),
                        "end" to mapOf("hour" to eu.first, "minute" to eu.second),
                        "metaData" to emptyMap<String, Any>(),
                    ))
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SwitchRow("Do not disturb", enabled, Icons.Outlined.DoNotDisturbOn,
                    "In this period, the robot does not resume a paused cleaning and keeps quiet.") { enabled = it; save() }
                RowDivider()
                SettingRow("Start", Icons.Outlined.Bedtime, value = "%02d:%02d".format(start.first, start.second), enabled = enabled, onClick = { picker = "start" })
                RowDivider()
                SettingRow("End", Icons.Outlined.WbSunny, value = "%02d:%02d".format(end.first, end.second), enabled = enabled, onClick = { picker = "end" })
            }
            InfoNote("Times show in the time zone of this phone.")
            picker?.let { which ->
                val cur = if (which == "start") start else end
                val state = rememberTimePickerState(cur.first, cur.second, is24Hour = true)
                AlertDialog(
                    onDismissRequest = { picker = null },
                    confirmButton = {
                        TextButton(onClick = {
                            val v = state.hour to state.minute
                            if (which == "start") start = v else end = v
                            picker = null
                            save()
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
                    text = { TimePicker(state) },
                )
            }
        }
    }
}

@Composable
fun VoicePackScreen() {
    val s = session
    val nav = LocalNav.current
    var voiceDialog by remember { mutableStateOf(false) }
    val vp = rememberLoad(Unit) { s.capGet(Cap.VoicePack) }
    SubScreen("Voice packs", onBack = { nav.popBackStack() }, actions = {
        IconButton(onClick = { vp.reload() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
    }) {
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            val lang = vp.value?.get("currentLanguage")?.str
            val op = vp.value?.get("operationStatus")
            val status = op["type"].str
            SettingRow("Current voice", Icons.Outlined.RecordVoiceOver, value = lang?.uppercase() ?: "…")
            RowDivider()
            SettingRow("Status", Icons.Outlined.Info,
                value = if (status == null || status == "idle") "Idle" else "${Labels.of(status)}${op["progress"].int?.let { " $it%" } ?: ""}")
            RowDivider()
            SettingRow("Install a voice pack", Icons.Outlined.Download, onClick = { voiceDialog = true })
        }
        InfoNote("The robot downloads the voice pack from a web address. Use only voice packs made for this model.")
    }
    if (voiceDialog) VoicePackDialog(onDismiss = { voiceDialog = false })
}

@Composable
private fun VoicePackDialog(onDismiss: () -> Unit) {
    val s = session
    var url by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install a voice pack") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The robot downloads the voice pack from the address.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(url, { url = it }, label = { Text("Address (URL)") }, singleLine = true)
                OutlinedTextField(lang, { lang = it }, label = { Text("Language code, for example EN") }, singleLine = true)
                OutlinedTextField(hash, { hash = it }, label = { Text("MD5 hash (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = url.startsWith("http") && lang.isNotBlank(), onClick = {
                onDismiss()
                s.action("The download started.") {
                    s.capPut(Cap.VoicePack, jsonObj("action" to "download", "url" to url.trim(), "language" to lang.trim(), "hash" to hash.trim().ifBlank { null }))
                }
            }) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun WifiScreen() {
    val s = session
    val nav = LocalNav.current
    val caps by s.caps.collectAsState()
    val status = rememberLoad(Unit) { s.capGet(Cap.WifiConfig) }
    var scan by remember { mutableStateOf<List<Pair<String, Int?>>?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var configure by remember { mutableStateOf<String?>(null) }

    SubScreen("Wi-Fi", onBack = { nav.popBackStack() }) {
        SectionTitle("Connection")
        SettingsCard {
            Loaded(status) { st ->
                val d = st["details"]
                val signal = d["signal"].int
                Rows {
                    row { SettingRow("Status", Icons.Outlined.Wifi, value = Labels.of(st["state"].str)) }
                    row { SettingRow("Network", Icons.Outlined.Router, value = d["ssid"].str ?: "–") }
                    if (signal != null) row { SettingRow("Signal", Icons.Outlined.SignalWifi4Bar, value = "$signal dBm  ·  ${signalText(signal)}") }
                    d["frequency"].str?.let { f -> row { SettingRow("Band", Icons.Outlined.SettingsInputAntenna, value = f.replace("ghz", " GHz")) } }
                    d["upspeed"].dbl?.let { sp -> row { SettingRow("Link speed", Icons.Outlined.Speed, value = "$sp Mbit/s") } }
                    d["ips"].strList.takeIf { it.isNotEmpty() }?.let { ips -> row { SettingRow("IP address", Icons.Outlined.Lan, value = ips.first()) } }
                }
            }
        }
        if (Cap.WifiScan in caps) {
            SectionTitle("Networks near the robot")
            SettingsCard {
                when {
                    scanning -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    scan == null -> SettingRow("Scan for networks", Icons.Outlined.WifiFind, chevron = false, onClick = {
                        scanning = true
                        s.action {
                            try {
                                scan = s.capGet(Cap.WifiScan).arr?.mapNotNull { n ->
                                    val ssid = n["details"]["ssid"].str ?: return@mapNotNull null
                                    ssid to n["details"]["signal"].int
                                }?.distinctBy { it.first }?.sortedByDescending { it.second ?: -100 } ?: emptyList()
                            } finally { scanning = false }
                        }
                    })
                    scan!!.isEmpty() -> InfoNote("The robot found no networks.", Modifier.padding(8.dp))
                    else -> scan!!.forEachIndexed { i, (ssid, sig) ->
                        if (i > 0) RowDivider()
                        SettingRow(ssid, Icons.Outlined.Wifi, value = sig?.let { "$it dBm" }, onClick = { configure = ssid })
                    }
                }
            }
        }
        SectionTitle("Change network")
        SettingsCard {
            SettingRow("Connect to another network", Icons.Outlined.SwapHoriz, onClick = { configure = "" })
        }
        InfoNote("If the robot cannot connect to the new network, it opens its own Wi-Fi access point again. Then use the web interface of the robot to set it up.")
    }
    configure?.let { ssid0 ->
        var ssid by remember { mutableStateOf(ssid0) }
        var pass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { configure = null },
            title = { Text("Wi-Fi network") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(ssid, { ssid = it }, label = { Text("Network name (SSID)") }, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = { TextButton(enabled = ssid.isNotBlank() && pass.length >= 8, onClick = { confirm = true }) { Text("Connect") } },
            dismissButton = { TextButton(onClick = { configure = null }) { Text("Cancel") } },
        )
        if (confirm) ConfirmDialog("Change the Wi-Fi network?",
            "The robot disconnects from this network. This app loses the connection to the robot until the robot and the phone are on the same network.",
            confirm = "Connect", destructive = true, onDismiss = { confirm = false }) {
            configure = null
            s.action("The robot connects to the new network.") {
                s.capPut(Cap.WifiConfig, jsonObj("ssid" to ssid, "credentials" to mapOf("type" to "wpa2_psk", "typeSpecificSettings" to mapOf("password" to pass))))
            }
        }
    }
}

private fun signalText(dbm: Int) = when {
    dbm >= -55 -> "Excellent"
    dbm >= -65 -> "Good"
    dbm >= -75 -> "Fair"
    else -> "Weak"
}
