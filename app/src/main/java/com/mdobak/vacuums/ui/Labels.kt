package com.mdobak.vacuums.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.mdobak.vacuums.data.RobotState

/** User-facing names for Valetudo values. */
object Labels {
    private val known = mapOf(
        // Status
        "docked" to "Docked", "idle" to "Idle", "cleaning" to "Cleaning", "returning" to "Returning to dock",
        "paused" to "Paused", "error" to "Error", "manual_control" to "Manual control", "moving" to "Moving",
        // Status flags
        "zone" to "Zone", "segment" to "Rooms", "spot" to "Spot", "target" to "Go to", "resumable" to "Can resume",
        "mapping" to "Mapping",
        // Operation modes
        "vacuum_and_mop" to "Vacuum and mop", "vacuum" to "Vacuum", "mop" to "Mop", "vacuum_then_mop" to "Vacuum, then mop",
        // Presets
        "off" to "Off", "min" to "Minimum", "low" to "Low", "medium" to "Medium", "high" to "High", "max" to "Max",
        "turbo" to "Turbo",
        // Routes
        "quick" to "Quick", "normal" to "Standard", "intensive" to "Intensive", "deep" to "Deep",
        // Carpet sensor
        "avoid" to "Avoid carpets", "lift" to "Lift mops", "detach" to "Detach mops",
        // Auto empty
        "frequent" to "Frequent", "infrequent" to "Infrequent",
        // Temperature / drying
        "cold" to "Cold", "warm" to "Warm", "hot" to "Hot", "scalding" to "Very hot", "boiling" to "Boiling",
        "quiet" to "Quiet", "2h" to "2 hours", "3h" to "3 hours", "4h" to "4 hours", "5h" to "5 hours",
        // Materials
        "generic" to "Default", "tile" to "Tile", "wood" to "Wood", "wood_horizontal" to "Wood (horizontal)",
        "wood_vertical" to "Wood (vertical)", "carpet" to "Carpet", "carpet_low" to "Low-pile carpet",
        "carpet_high" to "High-pile carpet",
        // Dock
        "emptying" to "Emptying", "drying" to "Drying", "cleaning_mop" to "Washing mops", "pause" to "Paused",
        "water_tank_clean" to "Clean water tank", "water_tank_dirty" to "Dirty water tank", "detergent" to "Detergent",
        "dustbag" to "Dust bag", "ok" to "OK", "missing" to "Missing", "empty" to "Empty", "full" to "Full",
        "unknown" to "Unknown",
        // Timer actions
        "full_cleanup" to "Full cleaning", "segment_cleanup" to "Room cleaning",
        "fan_speed_control" to "Suction", "water_usage_control" to "Water flow", "operation_mode_control" to "Cleaning mode",
        // Quirk options
        "on" to "On", "every_segment" to "After each room", "each_cleanup" to "Each cleaning",
        "every_7_days" to "Every 7 days", "automatic" to "Automatic", "sequential" to "Sequential",
        "synchronized" to "Synchronized", "auto" to "Automatic", "short" to "Short", "long" to "Long",
        "every_cleanup" to "After each cleaning",
    )

    fun of(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        known[value]?.let { return it }
        Regex("^every_(\\d+)_m2$").find(value)?.let { return "Every ${it.groupValues[1]} m²" }
        Regex("^every_(\\d+)_min$").find(value)?.let { return "Every ${it.groupValues[1]} min" }
        Regex("^every_(\\d+)_cleanups$").find(value)?.let { return "Every ${it.groupValues[1]} cleanings" }
        return value.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun status(s: RobotState): String {
        val base = of(s.status)
        val flag = s.statusFlag
        return when {
            s.status == "cleaning" && flag == "segment" -> "Cleaning rooms"
            s.status == "cleaning" && flag == "zone" -> "Cleaning zones"
            s.status == "cleaning" && flag == "spot" -> "Spot cleaning"
            s.status == "cleaning" && flag == "mapping" -> "Mapping"
            s.status == "moving" && flag == "target" -> "Going to target"
            s.status == "moving" && flag == "mapping" -> "Mapping"
            s.status == "paused" && flag == "resumable" -> "Paused"
            else -> base
        }
    }

    fun consumable(type: String, subType: String?): String {
        val t = when (type) {
            "brush" -> "brush"; "filter" -> "Filter"; "cleaning" -> "Clean"; "mop" -> "Mop"
            "detergent" -> "Detergent"; "bin" -> "Dust bin"; "dustbag" -> "Dust bag"
            else -> of(type)
        }
        return when ("$type/$subType") {
            "brush/main" -> "Main brush"
            "brush/side_right" -> "Side brush"
            "brush/side_left" -> "Left side brush"
            "filter/main" -> "Filter"
            "cleaning/sensor" -> "Clean sensors"
            "cleaning/wheel" -> "Clean wheel"
            "cleaning/dock" -> "Clean dock"
            "mop/main" -> "Mop pads"
            "mop/dock_filter" -> "Dock water filter"
            "detergent/dock" -> "Dock detergent"
            "dustbag/dock" -> "Dock dust bag"
            else -> if (subType == null || subType == "none" || subType == "main") of(t) else "${of(t)} (${of(subType)})"
        }
    }

    fun materialIcon(m: String?): ImageVector = when {
        m == null -> Icons.Outlined.Texture
        m.startsWith("wood") -> Icons.Outlined.ViewWeek
        m == "tile" -> Icons.Outlined.GridOn
        m.startsWith("carpet") -> Icons.Outlined.Waves
        else -> Icons.Outlined.Texture
    }

    fun dayShort(dow: Int) = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[dow]
    fun dayLetter(dow: Int) = listOf("S", "M", "T", "W", "T", "F", "S")[dow]

    fun duration(seconds: Double): String {
        val m = (seconds / 60).toInt()
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    fun area(cm2: Double): String {
        val m2 = cm2 / 10000.0
        return if (m2 >= 10) "${m2.toInt()} m²" else String.format(java.util.Locale.US, "%.1f m²", m2)
    }
}
