package com.mdobak.vacuums.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.Labels
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapEditorScreen(initialTab: String) {
    val s = session
    val nav = LocalNav.current
    val map by s.map.collectAsState()
    val caps by s.caps.collectAsState()
    val colors = Theme.colors
    val scope = rememberCoroutineScope()

    val tabs = buildList {
        if (Cap.SegmentEdit in caps || Cap.SegmentRename in caps || Cap.SegmentMaterial in caps) add("rooms")
        if (Cap.Restrictions in caps) add("restrictions")
        if (Cap.Annotations in caps) add("annotations")
    }
    var tab by rememberSaveable { mutableStateOf(initialTab.takeIf { it in tabs } ?: tabs.firstOrNull() ?: "rooms") }
    val editor = remember { ShapeEditor() }
    val viewport = rememberMapViewport()
    var confirmLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var busy by remember { mutableStateOf(false) }

    val selected = remember { mutableStateListOf<String>() }
    var splitting by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var materialOpen by remember { mutableStateOf(false) }

    val restrictionProps = rememberLoad(Cap.Restrictions in caps) { if (Cap.Restrictions in caps) s.props(Cap.Restrictions) else null }
    val annotationProps = rememberLoad(Cap.Annotations in caps) { if (Cap.Annotations in caps) s.props(Cap.Annotations) else null }
    val materialProps = rememberLoad(Cap.SegmentMaterial in caps) { if (Cap.SegmentMaterial in caps) s.props(Cap.SegmentMaterial) else null }
    val supportsMopZones = restrictionProps.value["supportedRestrictedZoneTypes"].strList.contains("mop")
    val annotationTypes = annotationProps.value["supportedAnnotationTypes"].strList
    val materials = materialProps.value["supportedMaterials"].strList

    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, reloadKey) {
        editor.clear()
        splitting = false
        selected.clear()
        loadError = null
        when (tab) {
            "restrictions" -> runCatching { editor.loadRestrictions(s.capGet(Cap.Restrictions)) }
                .onFailure { loadError = RobotSession.errorText(it) }
            "annotations" -> map?.let { editor.loadAnnotations(it) }
        }
    }

    fun guarded(action: () -> Unit) {
        if (editor.dirty && tab != "rooms") confirmLeave = action else action()
    }

    BackHandler(enabled = editor.dirty && tab != "rooms") { guarded { nav.popBackStack() } }

    val currentMap by rememberUpdatedState(map)
    val roomHandler = remember {
        object : MapGestureHandler {
            override fun onTap(cm: Offset, toleranceCm: Float) {
                val seg = currentMap?.segmentAtCm(cm.x, cm.y)?.segmentId ?: return
                if (seg in selected) selected.remove(seg) else {
                    if (selected.size >= 2) selected.removeAt(0)
                    selected.add(seg)
                }
            }
        }
    }
    val handler: MapGestureHandler = if (tab == "rooms" && !splitting) roomHandler else editor

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Edit map") },
                    navigationIcon = { IconButton(onClick = { guarded { nav.popBackStack() } }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    actions = {
                        if (tab != "rooms") {
                            TextButton(enabled = editor.dirty && !busy, onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        if (tab == "restrictions") s.capPut(Cap.Restrictions, editor.restrictionsJson())
                                        else s.capPut(Cap.Annotations, editor.annotationsJson())
                                        editor.dirty = false
                                        s.message("Changes saved.")
                                        s.refreshMap()
                                    } catch (e: Exception) {
                                        s.message(RobotSession.errorText(e))
                                    }
                                    busy = false
                                }
                            }) { Text("Save") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.mapBackground),
                )
                if (tabs.size > 1) {
                    PrimaryTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0), containerColor = colors.mapBackground) {
                        tabs.forEach { t ->
                            Tab(selected = tab == t, onClick = { if (t != tab) guarded { tab = t } },
                                text = { Text(when (t) { "rooms" -> "Rooms"; "restrictions" -> "Restrictions"; else -> "Floor features" }) })
                        }
                    }
                }
            }
        },
        containerColor = colors.mapBackground,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                    when (tab) {
                        "rooms" -> RoomTools(
                            map = map, selected = selected, splitting = splitting, caps = caps, busy = busy,
                            onRename = { renameOpen = true },
                            onMaterial = { materialOpen = true },
                            onStartSplit = {
                                val seg = map?.segments?.firstOrNull { it.segmentId == selected.firstOrNull() } ?: return@RoomTools
                                val m = map!!
                                // Start with a horizontal line through the middle of the room.
                                val cx = seg.midX * m.pixelSize; val cy = seg.midY * m.pixelSize
                                val half = 120f
                                editor.clear()
                                editor.addLine(ShapeKind.SplitLine, Offset(cx - half, cy), Offset(cx + half, cy))
                                splitting = true
                            },
                            onCancelSplit = { editor.clear(); splitting = false },
                            onConfirmSplit = {
                                val line = editor.shapes.firstOrNull() ?: return@RoomTools
                                val id = selected.firstOrNull() ?: return@RoomTools
                                busy = true
                                scope.launch {
                                    try {
                                        s.capPut(Cap.SegmentEdit, jsonObj(
                                            "action" to "split_segment", "segment_id" to id,
                                            "pA" to mapOf("x" to line.a.x.roundToInt(), "y" to line.a.y.roundToInt()),
                                            "pB" to mapOf("x" to line.b.x.roundToInt(), "y" to line.b.y.roundToInt()),
                                        ))
                                        s.message("The room was split.")
                                        editor.clear(); splitting = false; selected.clear()
                                        s.refreshMap()
                                    } catch (e: Exception) { s.message(RobotSession.errorText(e)) }
                                    busy = false
                                }
                            },
                            onJoin = {
                                val (a, b) = selected.toList()
                                busy = true
                                scope.launch {
                                    try {
                                        s.capPut(Cap.SegmentEdit, jsonObj("action" to "join_segments", "segment_a_id" to a, "segment_b_id" to b))
                                        s.message("The rooms were joined.")
                                        selected.clear()
                                        s.refreshMap()
                                    } catch (e: Exception) { s.message(RobotSession.errorText(e)) }
                                    busy = false
                                }
                            },
                        )
                        else -> ShapeTools(
                            editor = editor,
                            kinds = if (tab == "restrictions") buildList {
                                add(ShapeKind.Wall); add(ShapeKind.NoGo); if (supportsMopZones) add(ShapeKind.NoMop)
                            } else buildList {
                                if ("threshold" in annotationTypes) add(ShapeKind.Threshold)
                                if ("curtain" in annotationTypes) add(ShapeKind.Curtain)
                                if ("ramp" in annotationTypes) add(ShapeKind.Ramp)
                            },
                            hint = if (tab == "restrictions") "The robot does not cross virtual walls and does not go into no-go zones. In no-mop zones, the robot only vacuums."
                            else "Mark door thresholds, curtains, and ramps so that the robot can plan its route over them.",
                            error = loadError,
                            onRetry = { reloadKey++ },
                            onAdd = { k ->
                                val m = map ?: return@ShapeTools
                                editor.addNew(k, MapTransform(m, viewport).visibleRectCm(viewport.size))
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val m = map
            if (m == null || m.isEmpty) {
                Text("No map yet.", Modifier.align(Alignment.Center), color = colors.textSecondary)
            } else {
                MapCanvas(
                    map = m,
                    modifier = Modifier.fillMaxSize(),
                    viewport = viewport,
                    selected = if (tab == "rooms") selected else emptyList(),
                    options = MapLayerOptions(
                        showPath = false,
                        showObstacles = false,
                        showRestrictions = tab != "restrictions",
                        showAnnotations = tab != "annotations",
                        showActiveZones = false,
                        showGoToTarget = false,
                    ),
                    handler = handler,
                ) { t -> editor.draw(this, t, colors) }
            }
        }
    }

    val firstSeg = map?.segments?.firstOrNull { it.segmentId == selected.firstOrNull() }
    if (renameOpen && firstSeg != null) {
        TextInputDialog(
            title = "Rename room", initial = firstSeg.name ?: "", label = "Name",
            validate = { if (it.isBlank()) "Type a name." else null },
            onDismiss = { renameOpen = false },
        ) { name ->
            s.action("Room renamed.") {
                s.capPut(Cap.SegmentRename, jsonObj("action" to "rename_segment", "segment_id" to firstSeg.segmentId, "name" to name))
                s.refreshMap()
            }
        }
    }
    if (materialOpen && firstSeg != null) {
        OptionSheet(
            title = "Floor type", options = materials, selected = firstSeg.material,
            description = "The robot adjusts its cleaning to the floor type. For wood floors, the direction tells the robot how to follow the boards.",
            onDismiss = { materialOpen = false },
        ) { mat ->
            materialOpen = false
            s.action("Floor type changed.") {
                s.capPut(Cap.SegmentMaterial, jsonObj("action" to "set_material", "segment_id" to firstSeg.segmentId, "material" to mat))
                s.refreshMap()
            }
        }
    }
    confirmLeave?.let { go ->
        ConfirmDialog(
            title = "Discard changes?", text = "You did not save your changes.", confirm = "Discard", destructive = true,
            onDismiss = { confirmLeave = null },
        ) { editor.dirty = false; go() }
    }
}

@Composable
private fun RoomTools(
    map: ValetudoMap?,
    selected: List<String>,
    splitting: Boolean,
    caps: Set<String>,
    busy: Boolean,
    onRename: () -> Unit,
    onMaterial: () -> Unit,
    onStartSplit: () -> Unit,
    onCancelSplit: () -> Unit,
    onConfirmSplit: () -> Unit,
    onJoin: () -> Unit,
) {
    val segs = selected.mapNotNull { id -> map?.segments?.firstOrNull { it.segmentId == id } }
    val sec = Theme.colors.textSecondary
    if (splitting) {
        Text("Move the ends of the line to where the new wall between the rooms must be. The line must cross the room.",
            style = MaterialTheme.typography.bodyMedium, color = sec)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancelSplit, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onConfirmSplit, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Split room") }
        }
        return
    }
    when (segs.size) {
        0 -> Text("Tap a room to rename it, change its floor type, or split it. Tap two rooms that touch to join them.",
            style = MaterialTheme.typography.bodyMedium, color = sec)
        1 -> {
            val seg = segs[0]
            Text(seg.name ?: "Room", style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(seg.area?.let { Labels.area(it.toDouble()) }, seg.material?.let { Labels.of(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = sec,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Cap.SegmentRename in caps) ToolButton("Rename", Icons.Outlined.Edit, Modifier.weight(1f), onClick = onRename)
                if (Cap.SegmentMaterial in caps) ToolButton("Floor", Labels.materialIcon(seg.material), Modifier.weight(1f), onClick = onMaterial)
                if (Cap.SegmentEdit in caps) ToolButton("Split", Icons.Outlined.ContentCut, Modifier.weight(1f), onClick = onStartSplit)
            }
        }
        else -> {
            Text("${segs[0].name} + ${segs[1].name}", style = MaterialTheme.typography.titleMedium)
            Text("The two rooms must touch.", style = MaterialTheme.typography.bodySmall, color = sec)
            Spacer(Modifier.height(12.dp))
            if (Cap.SegmentEdit in caps) {
                Button(onClick = onJoin, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.CallMerge, null); Spacer(Modifier.width(8.dp)); Text("Join rooms")
                }
            }
        }
    }
}

@Composable
private fun ToolButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text, maxLines = 1)
    }
}

@Composable
private fun ShapeTools(
    editor: ShapeEditor,
    kinds: List<ShapeKind>,
    hint: String,
    error: String?,
    onRetry: () -> Unit,
    onAdd: (ShapeKind) -> Unit,
) {
    if (error != null) {
        ErrorBox(error, onRetry)
        return
    }
    val sel = editor.selected
    if (sel != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sel.kind.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (sel.kind == ShapeKind.Ramp) {
                OutlinedButton(onClick = { editor.rotateSelectedRamp() }) { Icon(Icons.AutoMirrored.Outlined.RotateRight, null); Spacer(Modifier.width(6.dp)); Text("Direction") }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(onClick = { editor.deleteSelected() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete")
            }
        }
        Text("Drag to move. Drag the white points to change the size.", style = MaterialTheme.typography.bodySmall,
            color = Theme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    } else {
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.forEach { k ->
            val icon = when (k) {
                ShapeKind.Wall -> Icons.Outlined.HorizontalRule
                ShapeKind.NoGo -> Icons.Outlined.Block
                ShapeKind.NoMop -> Icons.Outlined.WaterDrop
                ShapeKind.Threshold -> Icons.Outlined.DoorFront
                ShapeKind.Curtain -> Icons.Outlined.Curtains
                ShapeKind.Ramp -> Icons.Outlined.Stairs
                else -> Icons.Outlined.Add
            }
            FilledTonalButton(onClick = { onAdd(k) }, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                Text(k.label.replace(" zone", "").replace("Virtual ", ""), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
