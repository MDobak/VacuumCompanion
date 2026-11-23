package com.mdobak.vacuums.ui.map

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.settings.hasMapSettings
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Theme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(initialMode: String) {
    val s = session
    val nav = LocalNav.current
    val map by s.map.collectAsState()
    val caps by s.caps.collectAsState()
    val colors = Theme.colors
    // The select modes choose the area for the Start button.
    val selectMode = initialMode.startsWith("select")
    val sel = s.cleanSelection
    // Done saves the draft; Back discards it.
    val draft = remember { CleanSelection().also { it.copyFrom(sel) } }

    val areaModes = buildList {
        add("all")
        if (Cap.Segmentation in caps) add("rooms")
        if (Cap.ZoneCleaning in caps) add("zones")
    }
    val canGoTo = Cap.GoTo in caps && !selectMode
    val firstMode = when {
        selectMode -> initialMode.removePrefix("select").removePrefix("-").takeIf { it in areaModes }
            ?: sel.kind.takeIf { it in areaModes } ?: "all"
        initialMode == "goto" && canGoTo -> "goto"
        else -> "view"
    }
    var mode by rememberSaveable { mutableStateOf(firstMode) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var options by remember { mutableStateOf(LayerPrefs.load(context)) }
    fun setLayers(o: MapLayerOptions) { options = o; LayerPrefs.save(context, o) }
    val selected = draft.rooms
    val zones = draft.zones
    var target by remember { mutableStateOf<Offset?>(null) }
    var obstacle by remember { mutableStateOf<MapEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    val viewport = rememberMapViewport()

    val segProps = rememberLoad(Cap.Segmentation in caps) { if (Cap.Segmentation in caps) s.props(Cap.Segmentation) else null }
    val zoneProps = rememberLoad(Cap.ZoneCleaning in caps) { if (Cap.ZoneCleaning in caps) s.props(Cap.ZoneCleaning) else null }
    val customOrderSupported = segProps.value["customOrderSupport"].bool == true
    val maxZones = zoneProps.value["zoneCount"]["max"].int ?: 1

    val currentMap by rememberUpdatedState(map)
    val currentOptions by rememberUpdatedState(options)
    val handler = remember(mode) {
        fun obstacleTap(cm: Offset, tol: Float): Boolean {
            // Hidden obstacles must not react to a tap.
            if (!currentOptions.showObstacles) return false
            val o = currentMap?.obstacleAt(cm, tol * 1.2f) ?: return false
            obstacle = o
            return true
        }
        fun roomAt(cm: Offset, tol: Float): String? {
            val m = currentMap ?: return null
            // A room name can be outside its room.
            val byLabel = if (currentOptions.showLabels) m.segments
                .minByOrNull { (Offset(it.midX * m.pixelSize, it.midY * m.pixelSize) - cm).getDistance() }
                ?.takeIf { (Offset(it.midX * m.pixelSize, it.midY * m.pixelSize) - cm).getDistance() < tol * 1.5f }
                else null
            return (byLabel ?: m.segmentAtCm(cm.x, cm.y) ?: m.segmentNearCm(cm.x, cm.y, tol * 0.6f))?.segmentId
        }
        when (mode) {
            "rooms" -> object : MapGestureHandler {
                override fun onTap(cm: Offset, toleranceCm: Float) {
                    if (obstacleTap(cm, toleranceCm)) return
                    val seg = roomAt(cm, toleranceCm) ?: return
                    if (seg in selected) selected.remove(seg) else selected.add(seg)
                }
            }
            // A room tap in "Everything" switches to room selection.
            "all" -> object : MapGestureHandler {
                override fun onTap(cm: Offset, toleranceCm: Float) {
                    if (obstacleTap(cm, toleranceCm) || "rooms" !in areaModes) return
                    val seg = roomAt(cm, toleranceCm) ?: return
                    mode = "rooms"
                    selected.clear()
                    selected.add(seg)
                }
            }
            "zones" -> zones.also { z -> z.onEmptyTap = { cm, tol -> obstacleTap(cm, tol) } }
            "goto" -> object : MapGestureHandler {
                override fun onTap(cm: Offset, toleranceCm: Float) {
                    if (!obstacleTap(cm, toleranceCm)) target = cm
                }
            }
            else -> object : MapGestureHandler {
                override fun onTap(cm: Offset, toleranceCm: Float) { obstacleTap(cm, toleranceCm) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectMode) "Area to clean" else "Map") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (canGoTo) IconToggleButton(
                        checked = mode == "goto",
                        onCheckedChange = { on -> mode = if (on) "goto" else "view"; target = null },
                        colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(if (mode == "goto") Icons.Filled.NearMe else Icons.Outlined.NearMe, "Send the robot to a point") }
                    if (!selectMode && hasMapSettings(caps)) IconButton(onClick = { nav.navigate("settings/map") }) { Icon(Icons.Outlined.EditLocationAlt, "Edit map") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Layers, "Map layers") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        LayerItem("Cleaning path", options.showPath) { setLayers(options.copy(showPath = it)) }
                        LayerItem("Coverage", options.coverage) { setLayers(options.copy(coverage = it)) }
                        LayerItem("Obstacles", options.showObstacles) { setLayers(options.copy(showObstacles = it)) }
                        LayerItem("Restrictions", options.showRestrictions) { setLayers(options.copy(showRestrictions = it)) }
                        LayerItem("Room names", options.showLabels) { setLayers(options.copy(showLabels = it)) }
                        LayerItem("Floor features", options.showAnnotations) { setLayers(options.copy(showAnnotations = it)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.mapBackground),
            )
        },
        containerColor = colors.mapBackground,
        bottomBar = {
            if (selectMode) Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 2.dp,
            ) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 12.dp)) {
                    if (selectMode) {
                        AreaPanel(mode = mode, modes = areaModes, onMode = { mode = it }, draft = draft) {
                            sel.commit(draft, mode); nav.popBackStack()
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val m = map
            if (m == null || m.isEmpty) {
                Text("No map yet. Start a cleaning or a mapping run.", Modifier.align(Alignment.Center), color = colors.textSecondary)
            } else {
                MapCanvas(
                    map = m,
                    modifier = Modifier.fillMaxSize(),
                    viewport = viewport,
                    selected = if (mode == "rooms") selected else emptyList(),
                    showOrder = mode == "rooms" && selected.size > 1 && draft.customOrder && customOrderSupported,
                    options = options.copy(showActiveZones = mode != "zones"),
                    handler = handler,
                ) { t ->
                    if (mode == "zones") zones.draw(this, t, colors)
                    if (mode == "goto") target?.let { drawPin(t.toScreen(it), colors.glow) }
                }
                if (mode == "goto") MapActionButton(
                    "Go here", Icons.Outlined.NearMe, enabled = target != null,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    onClick = {
                        val p = target ?: return@MapActionButton
                        s.action("The robot is on its way.") {
                            s.capPut(Cap.GoTo, jsonObj("action" to "goto", "coordinates" to mapOf("x" to p.x.roundToInt(), "y" to p.y.roundToInt())))
                        }
                        target = null
                    },
                )
                if (selectMode) AreaMapActions(
                    mode, m.segments.mapNotNull { it.segmentId }, draft, customOrderSupported, maxZones,
                    onAddZone = { zones.addNew(ShapeKind.Zone, MapTransform(m, viewport).visibleRectCm(viewport.size)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    obstacle?.let { o -> ObstacleDialog(o, Cap.ObstacleImages in caps) { obstacle = null } }
}

@Composable
private fun LayerItem(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
    )
}

/** The bottom panel of the area selector: the area type, a short summary and "Done". */
@Composable
private fun AreaPanel(
    mode: String,
    modes: List<String>,
    onMode: (String) -> Unit,
    draft: CleanSelection,
    onDone: () -> Unit,
) {
    val selected = draft.rooms
    val zoneCount = draft.zones.shapes.size
    if (modes.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            modes.forEach { m ->
                val (label, icon) = when (m) {
                    "rooms" -> "Rooms" to Icons.Outlined.GridView
                    "zones" -> "Zones" to Icons.Outlined.CropFree
                    else -> "Everything" to Icons.Outlined.Home
                }
                AreaChoice(label, icon, mode == m, Modifier.weight(1f)) { onMode(m) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    val valid = when (mode) {
        "rooms" -> selected.isNotEmpty()
        "zones" -> zoneCount > 0
        else -> true
    }
    Button(onClick = onDone, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
}

/** One area type: an icon above a label. The selected type has a colored frame. */
@Composable
private fun AreaChoice(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Theme.colors
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else c.divider),
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (selected) accent else c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                color = if (selected) MaterialTheme.colorScheme.onSurface else c.textSecondary)
        }
    }
}

@Composable
private fun MapActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    checked: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (checked) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The buttons in the lower right corner of the map. They change with the area type. */
@Composable
private fun AreaMapActions(mode: String, segIds: List<String>, draft: CleanSelection, customOrderSupported: Boolean, maxZones: Int, onAddZone: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (mode) {
            "rooms" -> {
                if (customOrderSupported) MapActionButton(
                    "In order", Icons.Outlined.FormatListNumbered, checked = draft.customOrder,
                    onClick = { draft.customOrder = !draft.customOrder },
                )
                val ids = segIds
                val all = ids.isNotEmpty() && draft.rooms.size == ids.size
                MapActionButton(if (all) "Clear" else "Select all", if (all) Icons.Outlined.Deselect else Icons.Outlined.SelectAll, onClick = {
                    draft.rooms.clear()
                    if (!all) draft.rooms.addAll(ids)
                }, enabled = ids.isNotEmpty())
            }
            "zones" -> {
                AnimatedVisibility(draft.zones.selectedId != null) {
                    MapActionButton("Delete zone", Icons.Outlined.Delete, onClick = { draft.zones.deleteSelected() })
                }
                MapActionButton("Add zone", Icons.Outlined.Add, onClick = onAddZone, enabled = draft.zones.shapes.size < maxZones)
            }
        }
    }
}

@Composable
fun ObstacleDialog(o: MapEntity, imagesSupported: Boolean, onDismiss: () -> Unit) {
    val s = session
    val id = o.meta["id"].str
    val label = o.meta["label"].str ?: "Obstacle"
    val img = rememberLoad(id) {
        if (!imagesSupported || id == null) null
        else runCatching { s.api.getBytes("robot/capabilities/${Cap.ObstacleImages}/img/$id") }.getOrNull()
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label.substringBefore(" (")) },
        text = {
            Column {
                val conf = Regex("\\((\\d+%)\\)").find(label)?.groupValues?.get(1)
                if (conf != null) Text("Detection confidence: $conf", style = MaterialTheme.typography.bodyMedium, color = Theme.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                when {
                    img.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    img.value != null -> Image(img.value!!, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
                    else -> Text(
                        if (imagesSupported) "No photo is available. Turn on \"Obstacle photos\" in the settings to save photos of obstacles."
                        else "This robot does not save obstacle photos.",
                        style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
