package com.mdobak.vacuums.ui.map

import androidx.compose.runtime.*
import com.mdobak.vacuums.data.*

/** The area for the next cleaning. [kind] is "all", "rooms" or "zones". */
class CleanSelection {
    var kind by mutableStateOf("all")
    val rooms = mutableStateListOf<String>()
    val zones = ShapeEditor()
    var iterations by mutableIntStateOf(1)
    var customOrder by mutableStateOf(true)

    fun copyFrom(o: CleanSelection) {
        kind = o.kind
        rooms.clear(); rooms.addAll(o.rooms)
        zones.clear()
        o.zones.shapes.forEach { zones.addRect(it.kind, it.rect, select = false, markDirty = false) }
        iterations = o.iterations
        customOrder = o.customOrder
    }

    /** Copies [draft] and clears the data of the other kinds. */
    fun commit(draft: CleanSelection, kind: String) {
        copyFrom(draft)
        this.kind = kind
        if (kind != "rooms") rooms.clear()
        if (kind != "zones") zones.clear()
    }
}

/** The selection of this robot session. */
val RobotSession.cleanSelection: CleanSelection
    get() = uiCache.getOrPut("cleanSelection") { CleanSelection() } as CleanSelection

/** Starts a cleaning of the selected area. A full cleaning uses the basic start command. */
suspend fun RobotSession.startSelection() {
    val sel = cleanSelection
    when {
        sel.kind == "rooms" && sel.rooms.isNotEmpty() -> {
            val props = props(Cap.Segmentation)
            val max = props["iterationCount"]["max"].int ?: 1
            val ids = sel.rooms.toList()
            capPut(Cap.Segmentation, jsonObj(
                "action" to "start_segment_action",
                "segment_ids" to ids,
                "iterations" to sel.iterations.coerceIn(1, max),
                "customOrder" to (props["customOrderSupport"].bool == true && sel.customOrder && ids.size > 1),
            ))
        }
        sel.kind == "zones" && sel.zones.shapes.isNotEmpty() -> {
            val max = props(Cap.ZoneCleaning)["iterationCount"]["max"].int ?: 1
            capPut(Cap.ZoneCleaning, jsonObj("action" to "clean", "zones" to sel.zones.zonesJson(), "iterations" to sel.iterations.coerceIn(1, max)))
        }
        // The basic start command has no passes. For more than one pass, clean all rooms as a room cleaning.
        sel.iterations > 1 && Cap.Segmentation in caps.value -> {
            val props = props(Cap.Segmentation)
            val max = props["iterationCount"]["max"].int ?: 1
            val ids = map.value?.segments.orEmpty().mapNotNull { it.segmentId }
            if (ids.isEmpty() || max <= 1) basic("start")
            else capPut(Cap.Segmentation, jsonObj(
                "action" to "start_segment_action",
                "segment_ids" to ids,
                "iterations" to sel.iterations.coerceIn(1, max),
                "customOrder" to false,
            ))
        }
        else -> basic("start")
    }
}
