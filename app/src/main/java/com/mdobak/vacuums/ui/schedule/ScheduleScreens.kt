package com.mdobak.vacuums.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.Labels
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Theme
import java.util.TimeZone

/** Minutes to add to UTC to get the local time of the phone. */
private fun localOffsetMinutes(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000

private fun ValetudoTimer.toLocal() = shifted(localOffsetMinutes())
private fun ValetudoTimer.toUtc() = shifted(-localOffsetMinutes())

private fun daysText(dow: List<Int>): String = when {
    dow.size == 7 -> "Every day"
    dow.sorted() == listOf(1, 2, 3, 4, 5) -> "Weekdays"
    dow.sorted() == listOf(0, 6) -> "Weekends"
    dow.isEmpty() -> "No days"
    else -> (listOf(1, 2, 3, 4, 5, 6, 0).filter { it in dow }).joinToString(", ") { Labels.dayShort(it) }
}

private fun time(h: Int, m: Int) = "%02d:%02d".format(h, m)

@Composable
fun SchedulesScreen() {
    val s = session
    val nav = LocalNav.current
    val timers = rememberLoad(Unit) {
        s.api.get("timers").obj?.values?.map { ValetudoTimer.parse(it) }?.sortedBy { it.toLocal().let { t -> t.hour * 60 + t.minute } } ?: emptyList()
    }
    // Reload when the user comes back from the editor.
    val entry = nav.currentBackStackEntry
    LaunchedEffect(entry) { timers.reload() }
    var delete by remember { mutableStateOf<ValetudoTimer?>(null) }

    SubScreen(
        "Schedules", onBack = { nav.popBackStack() },
        floating = {
            ExtendedFloatingActionButton(onClick = { nav.navigate("schedule/new") }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New schedule") })
        },
    ) {
        Loaded(timers) { list ->
            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(48.dp), tint = Theme.colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    Text("No schedules", style = MaterialTheme.typography.titleMedium)
                    Text("Add a schedule to clean at a fixed time.", style = MaterialTheme.typography.bodyMedium, color = Theme.colors.textSecondary)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    list.forEach { t -> TimerCard(t, onDelete = { delete = t }, onChanged = { timers.reload() }) }
                }
                InfoNote("Times show in the time zone of this phone. The robot keeps the times in UTC.")
                Spacer(Modifier.height(72.dp))
            }
        }
    }
    delete?.let { t ->
        ConfirmDialog("Delete schedule?", "The schedule \"${t.label.ifBlank { time(t.toLocal().hour, t.toLocal().minute) }}\" will be deleted.",
            confirm = "Delete", destructive = true, onDismiss = { delete = null }) {
            s.action("Schedule deleted.") { s.api.delete("timers/${t.id}"); timers.reload() }
        }
    }
}

@Composable
private fun TimerCard(t: ValetudoTimer, onDelete: () -> Unit, onChanged: () -> Unit) {
    val s = session
    val nav = LocalNav.current
    val local = t.toLocal()
    var enabled by remember(t) { mutableStateOf(t.enabled) }
    var menu by remember { mutableStateOf(false) }
    val c = Theme.colors
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), onClick = { nav.navigate("schedule/${t.id}") }) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time(local.hour, local.minute), fontSize = 34.sp, fontWeight = FontWeight.Light,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else c.textSecondary)
                Text(listOfNotNull(t.label.ifBlank { null }, daysText(local.dow)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (t.actionType == "segment_cleanup") "Rooms: ${t.segmentIds.size}" + if (t.iterations > 1) ", ${t.iterations}×" else ""
                    else "Full clean",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
            Switch(checked = enabled, onCheckedChange = { v ->
                enabled = v
                s.action { s.api.put("timers/${t.id}", t.copy(enabled = v).toJson()) }
            })
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Run now") }, leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) }, onClick = {
                        menu = false
                        s.action("The schedule started.") { s.api.put("timers/${t.id}/action", jsonObj("action" to "execute_now")) }
                    })
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; nav.navigate("schedule/${t.id}") })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

private class EditorData(val timer: ValetudoTimer, val props: kotlinx.serialization.json.JsonElement, val segments: List<Segment>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(id: String) {
    val s = session
    val nav = LocalNav.current
    val isNew = id == "new"
    val data = rememberLoad(id) {
        val props = s.api.get("timers/properties")
        val segs = if (s.has(Cap.Segmentation)) s.segments() else emptyList()
        val t = if (isNew) ValetudoTimer() else ValetudoTimer.parse(s.api.get("timers")[id]).toLocal()
        EditorData(t, props, segs)
    }
    Loaded(data) { d -> ScheduleEditor(d, isNew) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditor(d: EditorData, isNew: Boolean) {
    val s = session
    val nav = LocalNav.current
    var t by remember { mutableStateOf(d.timer) }
    var timePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val actions = d.props["supportedActions"].strList
    val preActionTypes = d.props["supportedPreActions"].strList
    val c = Theme.colors

    fun save() {
        saving = true
        s.action(if (isNew) "Schedule added." else "Schedule saved.") {
            try {
                val body = t.toUtc().toJson()
                if (isNew) s.api.post("timers", body) else s.api.put("timers/${t.id}", body)
                nav.popBackStack()
            } finally { saving = false }
        }
    }

    val valid = t.dow.isNotEmpty() && (t.actionType != "segment_cleanup" || t.segmentIds.isNotEmpty())
    SubScreen(
        if (isNew) "New schedule" else "Edit schedule", onBack = { nav.popBackStack() },
        actions = { TextButton(onClick = { save() }, enabled = valid && !saving) { Text("Save") } },
    ) {
        Surface(onClick = { timePicker = true }, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time(t.hour, t.minute), fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text("Tap to change the time", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        SectionTitle("Repeat")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(1, 2, 3, 4, 5, 6, 0).forEach { day ->
                val sel = day in t.dow
                Surface(
                    shape = CircleShape, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(42.dp).clip(CircleShape).clickable {
                        t = t.copy(dow = if (sel) t.dow - day else t.dow + day)
                    },
                ) { Box(contentAlignment = Alignment.Center) { Text(Labels.dayLetter(day), style = MaterialTheme.typography.titleSmall) } }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Every day", selected = t.dow.size == 7) { t = t.copy(dow = (0..6).toList()) }
            Pill("Weekdays", selected = t.dow.sorted() == listOf(1, 2, 3, 4, 5)) { t = t.copy(dow = listOf(1, 2, 3, 4, 5)) }
            Pill("Weekends", selected = t.dow.sorted() == listOf(0, 6)) { t = t.copy(dow = listOf(0, 6)) }
        }

        SectionTitle("General")
        SettingsCard {
            var rename by remember { mutableStateOf(false) }
            SettingRow("Name", Icons.AutoMirrored.Outlined.Label, value = t.label.ifBlank { "None" }, onClick = { rename = true })
            RowDivider()
            SwitchRow("Enabled", t.enabled, Icons.Outlined.ToggleOn) { t = t.copy(enabled = it) }
            if (rename) {
                TextInputDialog("Schedule name", t.label, "Name", validate = { if (it.length > 24) "Use a maximum of 24 characters." else null },
                    onDismiss = { rename = false }) { t = t.copy(label = it) }
            }
        }

        SectionTitle("Action")
        SettingsCard {
            actions.forEachIndexed { i, a ->
                if (i > 0) RowDivider()
                SettingRow(
                    if (a == "segment_cleanup") "Clean selected rooms" else if (a == "full_cleanup") "Clean everything" else Labels.of(a),
                    if (a == "segment_cleanup") Icons.Outlined.GridView else Icons.Outlined.Home,
                    chevron = false, onClick = { t = t.copy(actionType = a) },
                ) { RadioButton(selected = t.actionType == a, onClick = { t = t.copy(actionType = a) }) }
            }
        }

        if (t.actionType == "segment_cleanup") {
            SectionTitle("Rooms")
            SettingsCard {
                d.segments.forEachIndexed { i, seg ->
                    if (i > 0) RowDivider()
                    val idx = t.segmentIds.indexOf(seg.id)
                    SettingRow(seg.name.ifBlank { "Room ${seg.id}" }, Labels.materialIcon(seg.material),
                        value = if (t.customOrder && idx >= 0) "#${idx + 1}" else null, chevron = false,
                        onClick = { t = t.copy(segmentIds = if (idx >= 0) t.segmentIds - seg.id else t.segmentIds + seg.id) },
                    ) { Checkbox(checked = idx >= 0, onCheckedChange = null) }
                }
                if (d.segments.isEmpty()) InfoNote("The map has no rooms.", Modifier.padding(8.dp))
            }
            SectionTitle("Options")
            SettingsCard {
                SettingRow("Passes", Icons.Outlined.Repeat, chevron = false) {
                    Stepper("", t.iterations, 1, 4) { t = t.copy(iterations = it) }
                }
                RowDivider()
                SwitchRow("Clean in the selected order", t.customOrder, Icons.Outlined.FormatListNumbered,
                    subtitle = "The order is the order in which you select the rooms.") { t = t.copy(customOrder = it) }
            }
        }

        if (preActionTypes.isNotEmpty()) {
            SectionTitle("Settings before start")
            SettingsCard {
                preActionTypes.forEachIndexed { i, type ->
                    if (i > 0) RowDivider()
                    val cap = when (type) {
                        "fan_speed_control" -> Cap.FanSpeed
                        "water_usage_control" -> Cap.WaterUsage
                        "operation_mode_control" -> Cap.OperationMode
                        else -> null
                    }
                    val title = when (type) {
                        "fan_speed_control" -> "Suction"
                        "water_usage_control" -> "Water"
                        "operation_mode_control" -> "Mode"
                        else -> Labels.of(type)
                    }
                    val presets = rememberLoad(cap) { cap?.let { s.presets(it) } ?: emptyList() }
                    val current = t.preActions.firstOrNull { it.type == type }?.value
                    ChoiceRow(title, current ?: "keep", listOf("keep") + (presets.value ?: emptyList()),
                        label = { if (it == "keep") "Do not change" else Labels.of(it) }) { v ->
                        val rest = t.preActions.filter { it.type != type }
                        t = t.copy(preActions = if (v == "keep") rest else rest + TimerPreAction(type, v))
                    }
                }
            }
        }
        InfoNote("Times use the time zone of this phone.")
    }

    if (timePicker) {
        val state = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePicker = false },
            confirmButton = { TextButton(onClick = { t = t.copy(hour = state.hour, minute = state.minute); timePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { timePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
