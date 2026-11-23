package com.mdobak.vacuums.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BatteryUnknown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mdobak.vacuums.data.Cap
import com.mdobak.vacuums.data.RobotState
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Theme

fun batteryIcon(level: Int?, charging: Boolean): ImageVector = when {
    charging -> Icons.Outlined.BatteryChargingFull
    level == null -> Icons.AutoMirrored.Outlined.BatteryUnknown
    level >= 90 -> Icons.Outlined.BatteryFull
    level >= 70 -> Icons.Outlined.Battery6Bar
    level >= 50 -> Icons.Outlined.Battery4Bar
    level >= 30 -> Icons.Outlined.Battery3Bar
    level >= 15 -> Icons.Outlined.Battery2Bar
    else -> Icons.Outlined.Battery1Bar
}

@Composable
fun BasicActionButtons(st: RobotState, onStart: (suspend () -> Unit)? = null) {
    val s = session
    val caps by s.caps.collectAsState()
    if (Cap.BasicControl !in caps) return
    val running = st.status in setOf("cleaning", "moving", "returning", "manual_control")
    val items = buildList {
        if (running && st.status != "returning") add(Triple("Pause", Icons.Outlined.Pause, "pause"))
        else add(Triple(if (st.isPaused) "Resume" else "Start", Icons.Outlined.PlayArrow, "start"))
        add(Triple("Stop", Icons.Outlined.Stop, "stop"))
        add(Triple("Dock", Icons.Outlined.Home, "home"))
    }
    // A new start can use the selected area. A resume always uses the basic command.
    fun run(action: String) = s.action {
        if (action == "start" && !st.isPaused && onStart != null) onStart() else s.basic(action)
    }
    Row(Modifier.fillMaxWidth()) {
        items.forEach { (label, icon, action) ->
            TextButton(onClick = { run(action) }, modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Theme.colors.textSecondary)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) { Icon(Icons.Outlined.Remove, "Less") }
        Text("$value×", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) { Icon(Icons.Outlined.Add, "More") }
    }
}
