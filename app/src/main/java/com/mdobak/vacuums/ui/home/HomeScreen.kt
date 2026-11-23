package com.mdobak.vacuums.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.Labels
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.map.cleanSelection
import com.mdobak.vacuums.ui.map.startSelection
import com.mdobak.vacuums.ui.control.CameraView
import com.mdobak.vacuums.ui.map.MapCanvas
import com.mdobak.vacuums.ui.map.LayerPrefs
import androidx.compose.ui.platform.LocalContext
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSwitchRobot: () -> Unit) {
    val s = session
    val nav = LocalNav.current
    val info by s.info.collectAsState()
    val caps by s.caps.collectAsState()
    val conn by s.connection.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s) { s.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onSwitchRobot) { Icon(Icons.Outlined.Menu, "My robots") } },
                actions = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            if (conn == Connection.Offline) OfflineBanner()
            QuickActions(caps)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroCard(info)
                EventsList()
                PendingMapChange(caps)
                MapPreview()
                if (Cap.Duststreaming in caps) CameraCard()
                DockCard(caps)
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text("The robot is not reachable. The app tries to connect again.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuickActions(caps: Set<String>) {
    val s = session
    val nav = LocalNav.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (Cap.Segmentation in caps) Pill("Rooms", Icons.Outlined.GridView) { nav.navigate("map/select-rooms") }
        if (Cap.ZoneCleaning in caps) Pill("Zone", Icons.Outlined.CropFree) { nav.navigate("map/select-zones") }
        if (Cap.GoTo in caps) Pill("Go to", Icons.Outlined.NearMe) { nav.navigate("map/goto") }
        if (Cap.Locate in caps) Pill("Locate", Icons.AutoMirrored.Outlined.VolumeUp) {
            s.action("The robot plays a sound.") { s.capPut(Cap.Locate, jsonObj("action" to "locate")) }
        }
        if (Cap.ManualControlHiRes in caps || Cap.ManualControl in caps) Pill("Remote", Icons.Outlined.Gamepad) { nav.navigate("manual") }
        Pill("Schedules", Icons.Outlined.Schedule) { nav.navigate("schedules") }
    }
}

@Composable
private fun HeroCard(info: RobotInfo) {
    val s = session
    val nav = LocalNav.current
    val st by s.state.collectAsState()
    val stats by s.stats.collectAsState()
    val caps by s.caps.collectAsState()
    val c = Theme.colors
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(c.heroTop, c.heroBottom)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
    ) {
        Column {
            Spacer(Modifier.height(12.dp))
            StatusOrb(st, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
            Text(info.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 24.dp), textAlign = TextAlign.Center)
            Text(
                Labels.status(st), style = MaterialTheme.typography.bodyLarge,
                color = if (st.isError) MaterialTheme.colorScheme.error else c.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
            )
            if (st.isError && !st.error.isNullOrBlank()) {
                Text(st.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = c.divider)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Stat("${st.battery ?: "–"}%", if (st.batteryFlag == "charging") "Charging" else "Battery", Modifier.weight(1f))
                VerticalDivider(color = c.divider)
                Stat(Labels.of(st.dockStatus ?: if (st.isDocked) "idle" else null), "Dock", Modifier.weight(1f))
                VerticalDivider(color = c.divider)
                if (Cap.CurrentStats in caps) {
                    Stat(stats["area"]?.let { Labels.area(it) } ?: "–", stats["time"]?.let { Labels.duration(it) } ?: "Area", Modifier.weight(1f))
                } else {
                    Stat(Labels.of(st.operationMode), "Mode", Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = c.divider)
            PresetGrid(caps, Modifier.padding(12.dp), onArea = { nav.navigate("map/select") })
            HorizontalDivider(color = c.divider)
            BasicActionButtons(st, onStart = { s.startSelection() })
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 16.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary, maxLines = 1)
    }
}

/** The status visual: a ring with the battery level and a glow that follows the status. */
@Composable
internal fun StatusOrb(st: RobotState, modifier: Modifier) {
    val c = Theme.colors
    val accent = when {
        st.isError -> MaterialTheme.colorScheme.error
        st.isDocked || st.batteryFlag == "charging" -> c.success
        st.status == "idle" -> MaterialTheme.colorScheme.secondary
        else -> c.glow
    }
    val active = st.status in setOf("cleaning", "returning", "moving", "manual_control")
    val inf = rememberInfiniteTransition(label = "orb")
    val sweep by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep")
    val pulse by inf.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val battery = (st.battery ?: 0) / 100f
    val icon: ImageVector = when (st.status) {
        "cleaning" -> Icons.Outlined.Autorenew
        "returning" -> Icons.Outlined.Home
        "paused" -> Icons.Outlined.Pause
        "error" -> Icons.Outlined.ErrorOutline
        "docked" -> if (st.batteryFlag == "charging") Icons.Outlined.Bolt else Icons.Outlined.CheckCircleOutline
        "manual_control" -> Icons.Outlined.Gamepad
        "moving" -> Icons.Outlined.NearMe
        else -> Icons.Outlined.PowerSettingsNew
    }
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val ring = r * 0.72f
            val glowA = if (active) pulse else 0.7f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * glowA), accent.copy(alpha = 0f)), center, r), r)
            drawCircle(Color.Black.copy(alpha = if (c.isDark) 0.35f else 0.06f), ring)
            val stroke = 8.dp.toPx()
            val tl = Offset(center.x - ring, center.y - ring)
            drawArc(c.divider, 0f, 360f, false, tl, Size(ring * 2, ring * 2), style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(accent.copy(alpha = 0.6f), accent, accent.copy(alpha = 0.6f))),
                -90f, 360f * battery, false, tl, Size(ring * 2, ring * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (active) {
                rotate(sweep) {
                    drawArc(Color.White.copy(alpha = 0.55f), -90f, 30f, false,
                        Offset(center.x - ring - stroke * 1.6f, center.y - ring - stroke * 1.6f),
                        Size((ring + stroke * 1.6f) * 2, (ring + stroke * 1.6f) * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(30.dp).let { if (st.status == "cleaning") it.rotate(sweep) else it })
            Text("${st.battery ?: "–"}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 40.sp))
            Text("% battery", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun PresetGrid(caps: Set<String>, modifier: Modifier, onArea: () -> Unit) {
    val s = session
    val st by s.state.collectAsState()
    val route = if (Cap.CleanRoute in caps) rememberLoad(Cap.CleanRoute) { s.capGet(Cap.CleanRoute)["route"].str } else null
    val hasArea = Cap.Segmentation in caps || Cap.ZoneCleaning in caps
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        if (hasArea) {
            add { m -> AreaTile(m, onArea) }
            add { m -> PassesTile(m) }
        }
        if (Cap.OperationMode in caps) add { m -> PresetTile(Cap.OperationMode, "Mode", st.operationMode, modeIcon(st.operationMode), m) }
        if (Cap.FanSpeed in caps) add { m -> PresetTile(Cap.FanSpeed, "Suction", st.fanSpeed, Icons.Outlined.Air, m) }
        if (Cap.WaterUsage in caps) add { m -> PresetTile(Cap.WaterUsage, "Water", st.waterGrade, Icons.Outlined.WaterDrop, m) }
        if (route != null) add { m -> RouteTile(route, m) }
    }
    if (tiles.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { it(Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
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
private fun PresetTile(cap: String, title: String, value: String?, icon: ImageVector, modifier: Modifier) {
    val s = session
    var open by remember { mutableStateOf(false) }
    val options = rememberLoad(cap) { s.presets(cap) }
    Tile(title, Labels.of(value), icon, modifier) { open = true }
    if (open) {
        OptionSheet(title, options.value ?: emptyList(), value, onDismiss = { open = false }) {
            open = false
            s.action { s.setPreset(cap, it) }
        }
    }
}

@Composable
private fun RouteTile(route: LoadState<String?>, modifier: Modifier) {
    val s = session
    var open by remember { mutableStateOf(false) }
    val props = rememberLoad(Cap.CleanRoute) { s.props(Cap.CleanRoute) }
    Tile("Route", Labels.of(route.value), Icons.Outlined.Route, modifier) { open = true }
    if (open) {
        val mopOnly = props.value["mopOnly"].strList
        OptionSheet(
            "Cleaning route", props.value["supportedRoutes"].strList, route.value,
            label = { Labels.of(it) + if (it in mopOnly) " (mop only)" else "" },
            description = "Defines how densely the robot covers the floor.",
            onDismiss = { open = false },
        ) { v ->
            open = false
            s.action { s.capPut(Cap.CleanRoute, jsonObj("route" to v)); route.value = v }
        }
    }
}

@Composable
private fun AreaTile(modifier: Modifier, onClick: () -> Unit) {
    val s = session
    val sel = s.cleanSelection
    val map by s.map.collectAsState()
    val value = when {
        sel.kind == "rooms" && sel.rooms.isNotEmpty() -> {
            val names = map?.segments.orEmpty().associate { it.segmentId to it.name }
            if (sel.rooms.size <= 2) sel.rooms.joinToString(", ") { names[it] ?: "Room $it" } else "${sel.rooms.size} rooms"
        }
        sel.kind == "zones" && sel.zones.shapes.isNotEmpty() ->
            if (sel.zones.shapes.size == 1) "1 zone" else "${sel.zones.shapes.size} zones"
        else -> "Everything"
    }
    Tile("Area", value, Icons.Outlined.SelectAll, modifier, onClick = onClick)
}

@Composable
private fun PassesTile(modifier: Modifier) {
    val s = session
    val sel = s.cleanSelection
    val caps by s.caps.collectAsState()
    // A full cleaning with more than one pass is sent as a room cleaning of all rooms.
    val cap = when {
        sel.kind == "zones" -> Cap.ZoneCleaning
        Cap.Segmentation in caps -> Cap.Segmentation
        else -> null
    }
    val props = rememberLoad(cap) { cap?.let { s.props(it) } }
    val max = props.value["iterationCount"]["max"].int ?: 1
    var open by remember { mutableStateOf(false) }
    val passes = sel.iterations.coerceIn(1, max)
    Tile("Passes", "${passes}×", Icons.Outlined.Repeat, modifier) { open = true }
    if (open) {
        OptionSheet(
            "Passes", (1..max).map { it.toString() }, passes.toString(),
            label = { if (it == "1") "1 pass" else "$it passes" },
            description = when {
                max <= 1 -> "This robot cleans this area one time only."
                sel.kind == "all" -> "The robot cleans the full home this number of times. With more than one pass, the robot cleans all rooms as a room cleaning."
                else -> "The robot cleans the selected area this number of times."
            },
            onDismiss = { open = false },
        ) { v -> open = false; sel.iterations = v.toInt() }
    }
}

@Composable
private fun Tile(title: String, value: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Theme.colors
    Surface(onClick = onClick, color = Color.White.copy(alpha = if (c.isDark) 0.06f else 0.5f), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(c.glow.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = c.glow)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp), tint = c.textSecondary)
        }
    }
}

@Composable
private fun EventsList() {
    val s = session
    val events by s.events.collectAsState()
    val open = events.filter { !it.processed }
    if (open.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        open.take(4).forEach { e ->
            val (text, actions) = when (e.cls) {
                "ErrorStateValetudoEvent" -> "Error: ${e.message ?: "unknown"}" to listOf("ok" to "OK")
                "DustBinFullValetudoEvent" -> "The dust bin is full. Empty it." to listOf("ok" to "OK")
                "MopAttachmentReminderValetudoEvent" -> "Check the mop pads. Attach them for mopping or remove them for vacuuming only." to listOf("ok" to "OK")
                "ConsumableDepletedValetudoEvent" -> "Replace: ${Labels.consumable(e.type ?: "", e.subType)}." to listOf("ok" to "Later", "reset" to "Replaced")
                "PendingMapChangeValetudoEvent" -> "The robot found a new map. Use the new map?" to listOf("no" to "Keep old", "yes" to "Use new")
                "MissingResourceValetudoEvent" -> (e.message ?: "A resource is missing.") to listOf("ok" to "OK")
                else -> (e.message ?: Labels.of(e.cls.removeSuffix("ValetudoEvent"))) to listOf("ok" to "OK")
            }
            val isError = e.cls == "ErrorStateValetudoEvent"
            Surface(
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, Modifier.padding(top = 2.dp).size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    }
                    Row(Modifier.align(Alignment.End)) {
                        actions.forEach { (interaction, label) ->
                            TextButton(onClick = {
                                s.action {
                                    s.api.put("events/${e.id}/interact", jsonObj("interaction" to interaction))
                                    s.refreshEvents()
                                }
                            }) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingMapChange(caps: Set<String>) {
    val s = session
    val pending by s.pendingMapChange.collectAsState()
    if (Cap.PendingMapChange !in caps || !pending) return
    val events by s.events.collectAsState()
    if (events.any { !it.processed && it.cls == "PendingMapChangeValetudoEvent" }) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("The robot has a new map that is not accepted yet.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = { s.action { s.capPut(Cap.PendingMapChange, jsonObj("action" to "reject")); s.setPendingMapChange(false) } }) { Text("Keep old") }
                TextButton(onClick = { s.action { s.capPut(Cap.PendingMapChange, jsonObj("action" to "accept")); s.setPendingMapChange(false) } }) { Text("Use new") }
            }
        }
    }
}

@Composable
private fun MapPreview() {
    val s = session
    val nav = LocalNav.current
    val map by s.map.collectAsState()
    val c = Theme.colors
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = c.mapBackground,
        modifier = Modifier.fillMaxWidth().height(300.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
        onClick = { nav.navigate("map/view") },
    ) {
        Box {
            val m = map
            if (m == null || m.isEmpty) {
                Text(if (m == null) "Loading map…" else "No map yet", Modifier.align(Alignment.Center), color = c.textSecondary)
            } else {
                MapCanvas(m, Modifier.fillMaxSize(), interactive = false, options = LayerPrefs.load(LocalContext.current), fitPadding = 60f)
            }
            Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Map, null, Modifier.size(18.dp), tint = c.textSecondary)
                Spacer(Modifier.width(8.dp))
                Text("Map", style = MaterialTheme.typography.titleSmall)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f), shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Open", style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraCard() {
    val s = session
    val nav = LocalNav.current
    val enabled by s.cameraEnabled.collectAsState()
    var playing by remember { mutableStateOf(false) }
    val c = Theme.colors
    Surface(
        shape = RoundedCornerShape(28.dp), color = Color.Black,
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
    ) {
        Box {
            if (playing && enabled) {
                CameraView(Modifier.fillMaxSize())
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Videocam, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    if (enabled) {
                        FilledTonalButton(onClick = { playing = true }) { Text("Show live view") }
                    } else {
                        Text("The camera stream is off.", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (playing) { StatusDot(Color(0xFFFF4D4D)); Spacer(Modifier.width(6.dp)) }
                        Text(if (playing) "Live" else "Camera", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (playing) {
                Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = { nav.navigate("camera") }) { Icon(Icons.Outlined.Fullscreen, "Full screen", tint = Color.White) }
                    IconButton(onClick = { playing = false }) { Icon(Icons.Outlined.Close, "Stop", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun DockCard(caps: Set<String>) {
    val s = session
    val st by s.state.collectAsState()
    val hasActions = Cap.AutoEmptyTrigger in caps || Cap.MopDockClean in caps || Cap.MopDockDry in caps
    if (st.dockComponents.isEmpty() && !hasActions) return
    val c = Theme.colors
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Countertops, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Base station", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Labels.of(st.dockStatus), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            if (st.dockComponents.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                st.dockComponents.forEach { (k, v) ->
                    val ok = v == "ok"
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (ok) c.success else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(Labels.of(k), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(Labels.of(v), style = MaterialTheme.typography.bodyMedium, color = if (ok) c.textSecondary else MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (hasActions) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val washing = st.dockStatus == "cleaning"
                    val drying = st.dockStatus == "drying"
                    if (Cap.AutoEmptyTrigger in caps) DockButton("Empty bin", Icons.Outlined.DeleteSweep, Modifier.weight(1f)) {
                        s.action("Emptying the dust bin.") { s.capPut(Cap.AutoEmptyTrigger, jsonObj("action" to "trigger")) }
                    }
                    if (Cap.MopDockClean in caps) DockButton(if (washing) "Stop wash" else "Wash mops", Icons.Outlined.LocalLaundryService, Modifier.weight(1f)) {
                        s.action { s.capPut(Cap.MopDockClean, jsonObj("action" to if (washing) "stop" else "start")) }
                    }
                    if (Cap.MopDockDry in caps) DockButton(if (drying) "Stop dry" else "Dry mops", Icons.Outlined.Air, Modifier.weight(1f)) {
                        s.action { s.capPut(Cap.MopDockDry, jsonObj("action" to if (drying) "stop" else "start")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
