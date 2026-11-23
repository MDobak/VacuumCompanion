package com.mdobak.vacuums.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mdobak.vacuums.AppViewModel
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.Labels
import com.mdobak.vacuums.ui.LocalSession
import com.mdobak.vacuums.ui.control.CameraView
import com.mdobak.vacuums.ui.home.StatusOrb
import com.mdobak.vacuums.ui.map.CleanSelection
import com.mdobak.vacuums.ui.map.MapCanvas
import com.mdobak.vacuums.ui.map.cleanSelection
import com.mdobak.vacuums.ui.map.startSelection
import com.mdobak.vacuums.ui.components.rememberLoad
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import com.mdobak.vacuums.ui.map.MapLayerOptions
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Theme
import kotlinx.coroutines.delay

private enum class TvScreen { Devices, Home, Camera }

@Composable
fun TvRoot(vm: AppViewModel) {
    val session by vm.session.collectAsState()
    var screen by remember {
        val last = vm.store.lastHost
        mutableStateOf(
            if (last != null && vm.store.robots.value.any { it.host == last }) { vm.open(last); TvScreen.Home } else TvScreen.Devices
        )
    }

    // Stop the live updates while the app is in the background.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> session?.resume()
                Lifecycle.Event.ON_STOP -> session?.pause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    CompositionLocalProvider(LocalSession provides session) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                screen == TvScreen.Devices || session == null -> TvDevices(vm) { host ->
                    vm.open(host)
                    screen = TvScreen.Home
                }
                screen == TvScreen.Camera -> {
                    BackHandler { screen = TvScreen.Home }
                    TvCamera()
                }
                else -> TvHome(
                    onCamera = { screen = TvScreen.Camera },
                    onRobots = { vm.close(); screen = TvScreen.Devices },
                )
            }
        }
    }
}

/** A large button for the remote control. The focused tile is larger and has a bright border. */
@Composable
private fun TvTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    sub: String? = null,
    overline: String? = null,
    compact: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "scale")
    val cs = MaterialTheme.colorScheme
    val bg = when {
        focused -> cs.onSurface
        primary -> cs.primaryContainer
        selected -> cs.secondaryContainer
        else -> cs.surfaceVariant
    }
    val fg = when {
        focused -> cs.surface
        primary -> cs.onPrimaryContainer
        selected -> cs.onSecondaryContainer
        else -> cs.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier.scale(scale).onFocusChanged { focused = it.hasFocus },
        shape = RoundedCornerShape(18.dp),
        color = bg,
        contentColor = fg,
        border = if (focused) BorderStroke(3.dp, cs.primary) else null,
    ) {
        Row(Modifier.padding(horizontal = if (compact) 16.dp else 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(if (compact) 24.dp else 30.dp))
            Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
            Column {
                if (overline != null) Text(overline, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.7f), maxLines = 1)
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TvDevices(vm: AppViewModel, onOpen: (String) -> Unit) {
    val saved by vm.store.robots.collectAsState()
    val found by vm.discovery.found.collectAsState()
    val scanning by vm.discovery.scanning.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.discovery.start(scope) }
    DisposableEffect(Unit) { onDispose { vm.discovery.stop() } }

    data class Item(val host: String, val name: String, val model: String, val found: FoundRobot?)
    val items = saved.map { r -> Item(r.host, r.name, r.model, null) } +
        found.filter { f -> saved.none { it.host == f.host } }
            .map { f -> Item(f.host, f.name, listOf(f.manufacturer, f.model).filter { it.isNotBlank() }.joinToString(" "), f) }

    val first = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && !focusedOnce) { delay(50); runCatching { first.requestFocus() }; focusedOnce = true }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
        Text("Select a robot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scanning) {
                CircularProgressIndicator(Modifier.padding(end = 10.dp).size(16.dp), strokeWidth = 2.dp)
                Text("The app searches the network for Valetudo robots.", color = Theme.colors.textSecondary)
            } else {
                Text(if (items.isEmpty()) "The app did not find a robot on this network." else "Search completed.", color = Theme.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(28.dp))
        LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(items, key = { _, it -> it.host }) { i, it ->
                TvTile(
                    label = it.name.ifBlank { it.model.ifBlank { "Robot" } },
                    icon = Icons.Outlined.CleaningServices,
                    sub = if (it.name.isNotBlank() && it.model.isNotBlank()) "${it.model} · ${it.host}" else it.host,
                    modifier = Modifier.fillMaxWidth().height(84.dp).let { m -> if (i == 0) m.focusRequester(first) else m },
                ) {
                    it.found?.let { f -> vm.store.upsert(SavedRobot(f.host, f.name, it.model)) }
                    onOpen(it.host)
                }
            }
            item {
                TvTile("Search again", Icons.Outlined.Refresh, Modifier.fillMaxWidth().height(84.dp).let { m -> if (items.isEmpty()) m.focusRequester(first) else m }) {
                    if (!scanning) vm.discovery.start(scope)
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(100); if (!focusedOnce) runCatching { first.requestFocus() } }
}

@Composable
private fun TvHome(onCamera: () -> Unit, onRobots: () -> Unit) {
    val s = session
    val st by s.state.collectAsState()
    val caps by s.caps.collectAsState()
    val map by s.map.collectAsState()
    val c = Theme.colors
    val sel = s.cleanSelection

    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s) { s.messages.collect { message = it } }
    LaunchedEffect(message) { if (message != null) { delay(3500); message = null } }

    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(540.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvStatusHeader(st, caps)
            Spacer(Modifier.weight(1f))
            TvPresets(caps)
            Spacer(Modifier.height(4.dp))
            TvControls(st, caps, onCamera, onRobots)
        }
        // View-only map with the selected rooms.
        Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(24.dp), color = c.mapBackground) {
            val m = map
            if (m == null) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (caps.isNotEmpty()) "The map is not available." else "Loading…", color = c.textSecondary)
                }
            } else {
                val rooms = if (sel.kind == "rooms") sel.rooms.toList() else emptyList()
                // Obstacle markers hide the rooms at TV distance.
                MapCanvas(m, Modifier.fillMaxSize(), selected = rooms, showOrder = rooms.size > 1 && sel.customOrder,
                    interactive = false, fitPadding = 32f, options = MapLayerOptions(showObstacles = false))
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Text(message ?: "", Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}

/** The status ring, the name, the status and the three values of the phone panel. */
@Composable
private fun TvStatusHeader(st: RobotState, caps: Set<String>) {
    val s = session
    val info by s.info.collectAsState()
    val conn by s.connection.collectAsState()
    val stats by s.stats.collectAsState()
    val c = Theme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusOrb(st, Modifier.size(150.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(info.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (conn) {
                    Connection.Offline -> "Offline. The app tries to connect again."
                    Connection.Connecting -> "Connecting…"
                    Connection.Online -> Labels.status(st) + (st.error?.takeIf { st.isError && it.isNotBlank() }?.let { " · $it" } ?: "")
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (st.isError || conn == Connection.Offline) MaterialTheme.colorScheme.error else c.textSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TvStat("${st.battery ?: "–"}%", if (st.batteryFlag == "charging") "Charging" else "Battery")
                TvStat(Labels.of(st.dockStatus ?: if (st.isDocked) "idle" else null), "Dock")
                if (Cap.CurrentStats in caps) {
                    TvStat(stats["area"]?.let { Labels.area(it) } ?: "–", stats["time"]?.let { Labels.duration(it) } ?: "Area")
                } else {
                    TvStat(Labels.of(st.operationMode), "Mode")
                }
            }
        }
    }
}

@Composable
private fun TvStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary, maxLines = 1)
    }
}

private class Preset(val key: String, val title: String, val value: String, val icon: ImageVector, val open: () -> Unit)

@Composable
private fun TvPresets(caps: Set<String>) {
    val s = session
    val st by s.state.collectAsState()
    val sel = s.cleanSelection
    val map by s.map.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }

    val hasArea = Cap.Segmentation in caps
    val route = if (Cap.CleanRoute in caps) rememberLoad(Cap.CleanRoute) { s.capGet(Cap.CleanRoute)["route"].str } else null
    val passCap = when {
        sel.kind == "zones" -> Cap.ZoneCleaning
        Cap.Segmentation in caps -> Cap.Segmentation
        else -> null
    }
    val passProps = rememberLoad(passCap) { passCap?.let { s.props(it) } }
    val maxPasses = passProps.value["iterationCount"]["max"].int ?: 1
    val passes = sel.iterations.coerceIn(1, maxPasses)
    val names = map?.segments.orEmpty().associate { it.segmentId to it.name }

    val tiles = buildList {
        if (hasArea) {
            val area = when {
                sel.kind == "rooms" && sel.rooms.isNotEmpty() ->
                    if (sel.rooms.size <= 2) sel.rooms.joinToString(", ") { names[it] ?: "Room $it" } else "${sel.rooms.size} rooms"
                sel.kind == "zones" && sel.zones.shapes.isNotEmpty() -> if (sel.zones.shapes.size == 1) "1 zone" else "${sel.zones.shapes.size} zones"
                else -> "Everything"
            }
            add(Preset("area", "Area", area, Icons.Outlined.SelectAll) { dialog = "area" })
            add(Preset("passes", "Passes", "${passes}×", Icons.Outlined.Repeat) { dialog = "passes" })
        }
        if (Cap.OperationMode in caps) add(Preset(Cap.OperationMode, "Mode", Labels.of(st.operationMode), modeIcon(st.operationMode)) { dialog = Cap.OperationMode })
        if (Cap.FanSpeed in caps) add(Preset(Cap.FanSpeed, "Suction", Labels.of(st.fanSpeed), Icons.Outlined.Air) { dialog = Cap.FanSpeed })
        if (Cap.WaterUsage in caps) add(Preset(Cap.WaterUsage, "Water", Labels.of(st.waterGrade), Icons.Outlined.WaterDrop) { dialog = Cap.WaterUsage })
        if (route != null) add(Preset("route", "Route", Labels.of(route.value), Icons.Outlined.Route) { dialog = "route" })
    }
    if (tiles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    key(p.key) { TvTile(p.value, p.icon, Modifier.weight(1f).height(60.dp), overline = p.title, compact = true, onClick = p.open) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        "area" -> TvAreaDialog(onDismiss = { dialog = null })
        "passes" -> TvOptionDialog(
            "Passes", (1..maxPasses).map { it.toString() }, passes.toString(),
            label = { if (it == "1") "1 pass" else "$it passes" },
            description = when {
                maxPasses <= 1 -> "This robot cleans this area one time only."
                sel.kind == "all" -> "The robot cleans the full home this number of times. With more than one pass, the robot cleans all rooms as a room cleaning."
                else -> "The robot cleans the selected area this number of times."
            },
            onDismiss = { dialog = null },
        ) { v -> dialog = null; sel.iterations = v.toInt() }
        "route" -> {
            val props = rememberLoad(Cap.CleanRoute) { s.props(Cap.CleanRoute) }
            val mopOnly = props.value["mopOnly"].strList
            TvOptionDialog(
                "Cleaning route", props.value["supportedRoutes"].strList, route?.value,
                label = { Labels.of(it) + if (it in mopOnly) " (mop only)" else "" },
                description = "Defines how densely the robot covers the floor.",
                onDismiss = { dialog = null },
            ) { v ->
                dialog = null
                s.action { s.capPut(Cap.CleanRoute, jsonObj("route" to v)); route?.value = v }
            }
        }
        else -> {
            val title = when (d) { Cap.OperationMode -> "Cleaning mode"; Cap.FanSpeed -> "Suction power"; else -> "Water flow" }
            val current = when (d) { Cap.OperationMode -> st.operationMode; Cap.FanSpeed -> st.fanSpeed; else -> st.waterGrade }
            val options = rememberLoad(d) { s.presets(d) }
            TvOptionDialog(title, options.value ?: emptyList(), current, onDismiss = { dialog = null }) { v ->
                dialog = null
                s.action { s.setPreset(d, v) }
            }
        }
    }
}

private fun modeIcon(mode: String?): ImageVector = when (mode) {
    "mop" -> Icons.Outlined.WaterDrop
    "vacuum" -> Icons.Outlined.Air
    else -> Icons.Outlined.CleaningServices
}

@Composable
private fun TvDialog(title: String, description: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(460.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (description != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = Theme.colors.textSecondary)
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}

@Composable
private fun TvOptionDialog(
    title: String,
    options: List<String>,
    selected: String?,
    label: (String) -> String = { Labels.of(it) },
    description: String? = null,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(options.isNotEmpty()) { if (options.isNotEmpty()) { delay(50); runCatching { focus.requestFocus() } } }
    TvDialog(title, description, onDismiss) {
        if (options.isEmpty()) Text("Loading…", color = Theme.colors.textSecondary)
        val target = options.indexOf(selected).coerceAtLeast(0)
        options.forEachIndexed { i, o ->
            TvTile(label(o), if (o == selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                Modifier.fillMaxWidth().height(56.dp).let { if (i == target) it.focusRequester(focus) else it },
                compact = true, selected = o == selected) { onSelect(o) }
        }
    }
}

/** Selects everything or some rooms. Rooms are cleaned in the order of selection. */
@Composable
private fun TvAreaDialog(onDismiss: () -> Unit) {
    val s = session
    val sel = s.cleanSelection
    val map by s.map.collectAsState()
    val rooms = map?.segments.orEmpty().filter { it.segmentId != null }.sortedBy { it.name ?: "" }
    val draft = remember { mutableStateListOf<String>().apply { if (sel.kind == "rooms") addAll(sel.rooms) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { focus.requestFocus() } }

    fun done() {
        val d = CleanSelection().also { it.copyFrom(sel); it.rooms.clear(); it.rooms.addAll(draft) }
        sel.commit(d, if (draft.isEmpty()) "all" else "rooms")
        onDismiss()
    }

    TvDialog("Area to clean", "Select rooms in the sequence of cleaning. Select nothing to clean everything.", onDismiss = { done() }) {
        TvTile("Everything", if (draft.isEmpty()) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
            Modifier.fillMaxWidth().height(56.dp).focusRequester(focus), compact = true, selected = draft.isEmpty()) { draft.clear() }
        rooms.forEach { r ->
            val id = r.segmentId!!
            val n = draft.indexOf(id)
            TvTile(r.name ?: "Room $id", if (n >= 0) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                Modifier.fillMaxWidth().height(56.dp), compact = true, selected = n >= 0,
                sub = if (n >= 0 && draft.size > 1) "Number ${n + 1}" else null) {
                if (n >= 0) draft.remove(id) else draft.add(id)
            }
        }
        Spacer(Modifier.height(4.dp))
        TvTile("Done", Icons.Outlined.Check, Modifier.fillMaxWidth().height(56.dp), primary = true, compact = true) { done() }
    }
}

private class Action(val key: String, val label: String, val icon: ImageVector, val run: () -> Unit)

/** The control bar: the main action as a pill, then round icon buttons. */
@Composable
private fun TvControls(st: RobotState, caps: Set<String>, onCamera: () -> Unit, onRobots: () -> Unit) {
    val s = session
    val running = st.status in setOf("cleaning", "moving", "returning", "manual_control")
    val basic = Cap.BasicControl in caps
    // A stable key keeps the focus when Start changes to Pause.
    val main = when {
        !basic -> null
        running && st.status != "returning" -> Action("main", "Pause", Icons.Outlined.Pause) { s.action { s.basic("pause") } }
        // A new start uses the selected area and passes.
        else -> Action("main", if (st.isPaused) "Resume" else "Start", Icons.Outlined.PlayArrow) {
            s.action { if (st.isPaused) s.basic("start") else s.startSelection() }
        }
    }
    val control = buildList {
        if (basic) {
            add(Action("stop", "Stop", Icons.Outlined.Stop) { s.action { s.basic("stop") } })
            add(Action("dock", "Dock", Icons.Outlined.Home) { s.action { s.basic("home") } })
        }
    }
    val more = buildList {
        if (Cap.Locate in caps) add(Action("locate", "Locate", Icons.AutoMirrored.Outlined.VolumeUp) {
            s.action("The robot plays a sound.") { s.capPut(Cap.Locate, jsonObj("action" to "locate")) }
        })
        if (Cap.Duststreaming in caps) add(Action("camera", "Camera", Icons.Outlined.Videocam, onCamera))
        add(Action("robots", "Robots", Icons.Outlined.SwapHoriz, onRobots))
    }
    val first = remember { FocusRequester() }
    // Focus the main action, so that the remote control works immediately.
    LaunchedEffect(basic) { delay(150); runCatching { first.requestFocus() } }
    // Wait for the capabilities, so that the focus does not move to another button.
    if (caps.isEmpty()) return

    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cs.surfaceVariant.copy(alpha = 0.4f)) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (main != null) key(main.key) { TvPillAction(main, Modifier.weight(1f).focusRequester(first)) }
            else Spacer(Modifier.weight(1f))
            control.forEach { a -> key(a.key) { TvIconAction(a) } }
            if (control.isNotEmpty() && more.isNotEmpty()) {
                Box(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp).width(1.dp).height(36.dp).background(cs.outlineVariant))
            }
            more.forEachIndexed { i, a ->
                key(a.key) { TvIconAction(a, if (main == null && i == 0) Modifier.focusRequester(first) else Modifier) }
            }
        }
    }
}

/** The main action. It has the accent color until it gets the focus. */
@Composable
private fun TvPillAction(a: Action, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "scale")
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = a.run,
        modifier = modifier.height(56.dp).scale(scale).onFocusChanged { focused = it.hasFocus },
        shape = CircleShape,
        color = if (focused) cs.onSurface else cs.primary,
        contentColor = if (focused) cs.surface else cs.onPrimary,
        border = if (focused) BorderStroke(3.dp, cs.primary) else null,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(a.icon, null, Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text(a.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun TvIconAction(a: Action, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "scale")
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = a.run,
            modifier = modifier.size(56.dp).scale(scale).onFocusChanged { focused = it.hasFocus },
            shape = CircleShape,
            color = if (focused) cs.onSurface else cs.surfaceVariant,
            contentColor = if (focused) cs.surface else cs.onSurface,
            border = if (focused) BorderStroke(3.dp, cs.primary) else null,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(a.icon, a.label, Modifier.size(26.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(a.label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (focused) cs.onSurface else Theme.colors.textSecondary)
    }
}

@Composable
private fun TvCamera() {
    val s = session
    val enabled by s.cameraEnabled.collectAsState()
    LaunchedEffect(Unit) { s.refreshCamera() }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (enabled) {
            CameraView(Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.VideocamOff, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text("The camera stream is off.\nTurn it on in the phone app.", color = Color.White, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        var hint by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(4000); hint = false }
        AnimatedVisibility(hint, Modifier.align(Alignment.TopEnd).padding(24.dp), enter = fadeIn(), exit = fadeOut()) {
            Text("Press Back to close the camera", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        }
    }
}
