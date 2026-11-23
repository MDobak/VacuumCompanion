package com.mdobak.vacuums.ui.devices

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdobak.vacuums.AppViewModel
import com.mdobak.vacuums.data.Discovery
import com.mdobak.vacuums.data.FoundRobot
import com.mdobak.vacuums.data.SavedRobot
import com.mdobak.vacuums.ui.components.ConfirmDialog
import com.mdobak.vacuums.ui.components.SectionTitle
import com.mdobak.vacuums.ui.theme.Theme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val saved by vm.store.robots.collectAsState()
    val found by vm.discovery.found.collectAsState()
    val scanning by vm.discovery.scanning.collectAsState()
    val scope = rememberCoroutineScope()
    var addOpen by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf<SavedRobot?>(null) }
    val online = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) { vm.discovery.start(scope) }
    DisposableEffect(Unit) { onDispose { vm.discovery.stop() } }
    LaunchedEffect(saved) {
        saved.forEach { r -> launch { online[r.host] = Discovery.probe(r.host) != null } }
    }
    LaunchedEffect(found) { found.forEach { online[it.host] = true } }

    fun save(f: FoundRobot) {
        vm.store.upsert(SavedRobot(f.host, f.name, listOf(f.manufacturer, f.model).filter { it.isNotBlank() }.joinToString(" ")))
    }

    val newRobots = found.filter { f -> saved.none { it.host == f.host } }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Vacuums") },
                actions = {
                    IconButton(onClick = { vm.discovery.start(scope) }, enabled = !scanning) {
                        Icon(Icons.Outlined.Refresh, "Search again")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addOpen = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add by address") },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (saved.isNotEmpty()) {
                item { SectionTitle("My robots") }
                items(saved, key = { "s" + it.host }) { r ->
                    RobotCard(
                        name = r.name.ifBlank { r.model.ifBlank { "Robot" } },
                        detail = if (r.name.isNotBlank()) "${r.model} · ${r.host}" else r.host,
                        online = online[r.host],
                        onClick = { onOpen(r.host) },
                        onLongClick = { forget = r },
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Found on this network", Modifier.weight(1f))
                    if (scanning) CircularProgressIndicator(Modifier.padding(top = 16.dp, end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
            }
            if (newRobots.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            if (scanning) "Searching for robots on your Wi-Fi network…"
                            else "No other robots found. Make sure that the phone and the robot are on the same network, or add the robot by its IP address.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Theme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                        )
                    }
                }
            }
            items(newRobots, key = { "f" + it.host }) { f ->
                RobotCard(
                    name = f.name.ifBlank { listOf(f.manufacturer, f.model).filter { it.isNotBlank() }.joinToString(" ") },
                    detail = f.host,
                    online = true,
                    action = "Add",
                    onClick = { save(f); onOpen(f.host) },
                )
            }
        }
    }

    if (addOpen) {
        AddRobotDialog(onDismiss = { addOpen = false }) { f ->
            addOpen = false
            save(f)
            onOpen(f.host)
        }
    }
    forget?.let { r ->
        ConfirmDialog(
            title = "Remove robot?",
            text = "The app will forget \"${r.name.ifBlank { r.model }}\". The robot is not changed.",
            confirm = "Remove",
            destructive = true,
            onDismiss = { forget = null },
        ) { vm.store.remove(r.host) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RobotCard(
    name: String,
    detail: String,
    online: Boolean?,
    action: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = Theme.colors
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(c.heroTop, c.heroBottom)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(20.dp).animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Adjust, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (online != null) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) c.success else MaterialTheme.colorScheme.error))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (action != null) {
                FilledTonalButton(onClick = onClick) { Text(action) }
            } else {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textSecondary)
            }
        }
    }
}

@Composable
private fun AddRobotDialog(onDismiss: () -> Unit, onFound: (FoundRobot) -> Unit) {
    var host by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add robot") },
        text = {
            Column {
                Text("Type the IP address or the host name of the robot.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host, onValueChange = { host = it; error = null }, singleLine = true,
                    label = { Text("Address") }, placeholder = { Text("192.168.1.50") },
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = host.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val f = Discovery.probe(host.trim())
                    busy = false
                    if (f == null) error = "No robot responds at this address." else onFound(f)
                }
            }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
