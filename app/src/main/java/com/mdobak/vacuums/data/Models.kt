package com.mdobak.vacuums.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Capability names used by the app. */
object Cap {
    const val BasicControl = "BasicControlCapability"
    const val FanSpeed = "FanSpeedControlCapability"
    const val WaterUsage = "WaterUsageControlCapability"
    const val OperationMode = "OperationModeControlCapability"
    const val Locate = "LocateCapability"
    const val Segmentation = "MapSegmentationCapability"
    const val ZoneCleaning = "ZoneCleaningCapability"
    const val GoTo = "GoToLocationCapability"
    const val SegmentEdit = "MapSegmentEditCapability"
    const val SegmentRename = "MapSegmentRenameCapability"
    const val SegmentMaterial = "MapSegmentMaterialControlCapability"
    const val Restrictions = "CombinedVirtualRestrictionsCapability"
    const val Annotations = "MapAnnotationsCapability"
    const val MapReset = "MapResetCapability"
    const val MappingPass = "MappingPassCapability"
    const val PendingMapChange = "PendingMapChangeHandlingCapability"
    const val ManualControlHiRes = "HighResolutionManualControlCapability"
    const val ManualControl = "ManualControlCapability"
    const val PersistentMap = "PersistentMapControlCapability"
    const val Duststreaming = "DuststreamingCapability"
    const val Consumables = "ConsumableMonitoringCapability"
    const val CurrentStats = "CurrentStatisticsCapability"
    const val DoNotDisturb = "DoNotDisturbCapability"
    const val SpeakerVolume = "SpeakerVolumeControlCapability"
    const val SpeakerTest = "SpeakerTestCapability"
    const val VoicePack = "VoicePackManagementCapability"
    const val WifiConfig = "WifiConfigurationCapability"
    const val WifiScan = "WifiScanCapability"
    const val Quirks = "QuirksCapability"
    const val KeyLock = "KeyLockCapability"
    const val CarpetMode = "CarpetModeControlCapability"
    const val CarpetSensorMode = "CarpetSensorModeControlCapability"
    const val CleanRoute = "CleanRouteControlCapability"
    const val ObstacleAvoidance = "ObstacleAvoidanceControlCapability"
    const val PetObstacleAvoidance = "PetObstacleAvoidanceControlCapability"
    const val CollisionAvoidantNavigation = "CollisionAvoidantNavigationControlCapability"
    const val FloorDirectionNavigation = "FloorMaterialDirectionAwareNavigationControlCapability"
    const val ObstacleImages = "ObstacleImagesCapability"
    const val CameraLight = "CameraLightControlCapability"
    const val MopExtension = "MopExtensionControlCapability"
    const val MopTwist = "MopTwistControlCapability"
    const val MopFurnitureLegs = "MopExtensionFurnitureLegHandlingControlCapability"
    const val AutoEmptyInterval = "AutoEmptyDockAutoEmptyIntervalControlCapability"
    const val AutoEmptyDuration = "AutoEmptyDockAutoEmptyDurationControlCapability"
    const val AutoEmptyTrigger = "AutoEmptyDockManualTriggerCapability"
    const val MopDockClean = "MopDockCleanManualTriggerCapability"
    const val MopDockDry = "MopDockDryManualTriggerCapability"
    const val MopWashTemperature = "MopDockMopWashTemperatureControlCapability"
    const val MopAutoDrying = "MopDockMopAutoDryingControlCapability"
    const val MopDryingTime = "MopDockMopDryingTimeControlCapability"
}

data class RobotInfo(
    val manufacturer: String = "",
    val model: String = "",
    val friendlyName: String = "",
    val systemId: String = "",
    val version: String = "",
) {
    val displayName: String get() = friendlyName.ifBlank { listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Robot" } }
}

data class RobotState(
    val status: String? = null,
    val statusFlag: String? = null,
    val error: String? = null,
    val battery: Int? = null,
    val batteryFlag: String? = null,
    val fanSpeed: String? = null,
    val waterGrade: String? = null,
    val operationMode: String? = null,
    val dockStatus: String? = null,
    val dockComponents: Map<String, String> = emptyMap(),
    val attachments: Map<String, Boolean> = emptyMap(),
) {
    val isPaused get() = status == "paused"
    val isDocked get() = status == "docked"
    val isError get() = status == "error"

    companion object {
        fun parse(arr: JsonElement?): RobotState {
            var s = RobotState()
            val comps = mutableMapOf<String, String>()
            val att = mutableMapOf<String, Boolean>()
            arr.arr?.forEach { a ->
                when (a["__class"].str) {
                    "StatusStateAttribute" -> s = s.copy(
                        status = a["value"].str,
                        statusFlag = a["flag"].str,
                        error = a["error"]["message"].str ?: a["metaData"]["error_description"].str,
                    )
                    "BatteryStateAttribute" -> s = s.copy(battery = a["level"].int, batteryFlag = a["flag"].str)
                    "PresetSelectionStateAttribute" -> when (a["type"].str) {
                        "fan_speed" -> s = s.copy(fanSpeed = a["value"].str)
                        "water_grade" -> s = s.copy(waterGrade = a["value"].str)
                        "operation_mode" -> s = s.copy(operationMode = a["value"].str)
                    }
                    "DockStatusStateAttribute" -> s = s.copy(dockStatus = a["value"].str)
                    "DockComponentStateAttribute" -> a["type"].str?.let { t -> comps[t] = a["value"].str ?: "" }
                    "AttachmentStateAttribute" -> a["type"].str?.let { t -> att[t] = a["attached"].bool == true }
                }
            }
            return s.copy(dockComponents = comps, attachments = att)
        }
    }
}

data class ValetudoEvent(
    val id: String,
    val cls: String,
    val timestamp: String,
    val processed: Boolean,
    val message: String?,
    val type: String?,
    val subType: String?,
) {
    companion object {
        fun parse(e: JsonElement?): List<ValetudoEvent> {
            val values = when (e) {
                is JsonObject -> e.values.toList()
                else -> e.arr?.toList() ?: emptyList()
            }
            return values.mapNotNull { v ->
                ValetudoEvent(
                    id = v["id"].str ?: return@mapNotNull null,
                    cls = v["__class"].str ?: "",
                    timestamp = v["timestamp"].str ?: "",
                    processed = v["processed"].bool == true,
                    message = v["message"].str,
                    type = v["type"].str,
                    subType = v["subType"].str,
                )
            }.sortedByDescending { it.timestamp }
        }
    }
}

data class Segment(val id: String, val name: String, val material: String?)

data class Consumable(
    val type: String,
    val subType: String?,
    val remaining: Int,
    val unit: String,
    val maxValue: Int?,
)

data class Quirk(
    val id: String,
    val title: String,
    val description: String,
    val options: List<String>,
    val value: String,
) {
    val isTrigger get() = options.contains("trigger") && options.contains("select_to_trigger")
}

data class TimerPreAction(val type: String, val value: String)

data class ValetudoTimer(
    val id: String? = null,
    val enabled: Boolean = true,
    val label: String = "",
    val dow: List<Int> = listOf(1, 2, 3, 4, 5),
    val hour: Int = 9,
    val minute: Int = 0,
    val actionType: String = "full_cleanup",
    val segmentIds: List<String> = emptyList(),
    val iterations: Int = 1,
    val customOrder: Boolean = false,
    val preActions: List<TimerPreAction> = emptyList(),
) {
    fun toJson(): JsonObject {
        val params = mutableMapOf<String, Any?>()
        if (actionType == "segment_cleanup") {
            params["segment_ids"] = segmentIds
            params["iterations"] = iterations
            params["custom_order"] = customOrder
        }
        return jsonObj(
            "id" to id,
            "enabled" to enabled,
            "label" to label,
            "dow" to dow.sorted(),
            "hour" to hour,
            "minute" to minute,
            "action" to mapOf("type" to actionType, "params" to params),
            "pre_actions" to preActions.map { mapOf("type" to it.type, "params" to mapOf("value" to it.value)) },
        ).let { o -> if (id == null) JsonObject(o - "id") else o }
    }

    /** Shift hour/minute/dow by [offsetMinutes] (positive moves later). */
    fun shifted(offsetMinutes: Int): ValetudoTimer {
        var total = hour * 60 + minute + offsetMinutes
        var dayShift = 0
        while (total < 0) { total += 1440; dayShift -= 1 }
        while (total >= 1440) { total -= 1440; dayShift += 1 }
        return copy(hour = total / 60, minute = total % 60, dow = dow.map { ((it + dayShift) % 7 + 7) % 7 })
    }

    companion object {
        fun parse(e: JsonElement?): ValetudoTimer {
            val params = e["action"]["params"]
            return ValetudoTimer(
                id = e["id"].str,
                enabled = e["enabled"].bool ?: true,
                label = e["label"].str ?: "",
                dow = e["dow"].arr?.mapNotNull { it.int } ?: emptyList(),
                hour = e["hour"].int ?: 0,
                minute = e["minute"].int ?: 0,
                actionType = e["action"]["type"].str ?: "full_cleanup",
                segmentIds = params["segment_ids"].strList,
                iterations = params["iterations"].int ?: 1,
                customOrder = params["custom_order"].bool ?: false,
                preActions = e["pre_actions"].arr?.mapNotNull { p ->
                    val t = p["type"].str ?: return@mapNotNull null
                    TimerPreAction(t, p["params"]["value"].str ?: "")
                } ?: emptyList(),
            )
        }
    }
}

data class SavedRobot(val host: String, val name: String, val model: String)
