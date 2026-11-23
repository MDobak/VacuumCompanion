package com.mdobak.vacuums.ui.settings

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.outlined.*
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.Labels
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.session

enum class QuirkSection { Robot, Cleaning, Carpets, Mop, Dock, DockService, Battery }

/** A quirk that the settings screen shows in a section. [ids] has one ID for each vendor that has the quirk. */
class QuirkSpec(
    val section: QuirkSection,
    val ids: List<String>,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val label: (String) -> String = { Labels.of(it) },
)

/** Quirk IDs from the Dreame, Midea, Roborock and Viomi quirk factories of Valetudo. */
object KnownQuirks {
    private fun q(section: QuirkSection, vararg ids: String, title: String, subtitle: String? = null, icon: ImageVector, label: (String) -> String = { Labels.of(it) }) =
        QuirkSpec(section, ids.toList(), title, subtitle, icon, label)

    val specs = listOf(
        q(QuirkSection.Robot, "57ffd1d3-306e-4451-b89c-934ec917fe7e", "977c5972-1f12-4ef1-9622-ce71fd085193",
            title = "Button lights", icon = Icons.Outlined.Lightbulb),
        q(QuirkSection.Robot, "1daf5179-0689-48a5-8f1b-0a23e11836dc", title = "Status light", icon = Icons.Outlined.EmojiObjects),

        q(QuirkSection.Cleaning, "15f0a896-c563-4560-b897-a586bd33a312", title = "Clean around people and pets later",
            subtitle = "The robot comes back to areas that were blocked.", icon = Icons.Outlined.Replay),
        q(QuirkSection.Cleaning, "75af01c4-5c24-4cb3-9619-41b46b6ce333", title = "Obstacle recognition",
            subtitle = "How much the robot tries to identify obstacles.", icon = Icons.Outlined.Visibility),
        q(QuirkSection.Cleaning, "d4688a29-a6e4-43c2-ab3a-08ddae40655c", title = "Stain cleaning",
            subtitle = "Finds stains and spills and cleans them carefully.", icon = Icons.Outlined.FormatColorReset),
        q(QuirkSection.Cleaning, "a8fcf6a3-80ed-4149-89ef-d81936b8e080", title = "Threshold crossing",
            subtitle = "How the wheels go over thresholds.", icon = Icons.Outlined.Stairs,
            label = { when (it) { "sequential" -> "One wheel after the other"; "synchronized" -> "Both wheels together"; else -> Labels.of(it) } }),
        q(QuirkSection.Cleaning, "2fa33876-f5ad-444d-9084-d51eb7be4670", title = "Threshold detection",
            subtitle = "Uses the camera to find thresholds.", icon = Icons.Outlined.Stairs),
        q(QuirkSection.Cleaning, "17d539ff-51d3-49be-8b9e-29aa1e9c8d39", title = "Threshold boost",
            subtitle = "More suction near thresholds.", icon = Icons.Outlined.Air),
        q(QuirkSection.Cleaning, "e560d60c-76de-4ccc-8c01-8ccbcece850e", title = "Extend the side brush",
            subtitle = "Reaches further into corners and below furniture.", icon = Icons.Outlined.OpenInFull),
        q(QuirkSection.Cleaning, "8f6a7013-794e-40d9-9bbe-8fdeed7c0b9d", title = "Edge extension frequency", icon = Icons.Outlined.EventRepeat),
        q(QuirkSection.Cleaning, "061b826c-417c-46a0-b6ad-807260cd4f70", title = "Vacuum only along the edges",
            subtitle = "Next cleaning only, in vacuum mode.", icon = Icons.Outlined.BorderOuter),
        q(QuirkSection.Cleaning, "017c40b7-dc24-48e8-a6e0-19d66528fa6d", title = "Turbo suction",
            subtitle = "Next cleaning only.", icon = Icons.Outlined.Bolt),
        q(QuirkSection.Cleaning, "afa83002-87db-43bb-b8ff-e4b38863a5d3", title = "Hair cutting",
            subtitle = "Cuts hair from the brush in the base station after cleaning.", icon = Icons.Outlined.ContentCut),
        q(QuirkSection.Cleaning, "224b6a0a-1a51-48d7-9d4d-61645399d368", title = "Strong hair cutting",
            subtitle = "After the next cleaning only.", icon = Icons.Outlined.ContentCut),

        q(QuirkSection.Carpets, "f8cb91ab-a47a-445f-b300-0aac0d4937c0", title = "Carpet detection sensitivity", icon = Icons.Outlined.Tune),
        q(QuirkSection.Carpets, "9450a668-88d7-4ff3-9455-a78b485fb33b", "d2ad3f99-c1b0-4195-9a98-4f13bdb0f1e8", title = "Deep carpet cleaning",
            subtitle = "Two slow passes in alternate directions.", icon = Icons.Outlined.Layers),
        q(QuirkSection.Carpets, "3d6cd658-c72a-48d9-ba54-38cf2d26e2f6", "4b100fec-08d5-4227-9edf-eb0198d6ea20", title = "Clean carpets first",
            icon = Icons.Outlined.LowPriority),
        q(QuirkSection.Carpets, "d23d7e7e-ef74-42a6-8a0a-4163742e437b", title = "Side brush on carpets", icon = Icons.Outlined.Cyclone),
        q(QuirkSection.Carpets, "f3ff1c65-9fe7-4312-b196-83ce91107fe8", title = "More distance from carpets",
            subtitle = "When the robot avoids carpets.", icon = Icons.Outlined.Straighten),

        q(QuirkSection.Mop, "a6709b18-57af-4e11-8b4c-8ae33147ab34", "12ec228f-6aae-4ba1-b85a-aa090ae4eb3e", "c50d98fb-7e29-4d09-a577-70c95ac33239",
            title = "Mop wash frequency", subtitle = "How often the robot goes back to wash the mops.", icon = Icons.Outlined.Autorenew),
        q(QuirkSection.Mop, "6afbb882-c4c4-4672-b008-887454e6e0d1", title = "Mop only",
            subtitle = "No vacuum when the mops are attached.", icon = Icons.Outlined.WaterDrop),
        q(QuirkSection.Mop, "66adac0f-0a16-4049-b6ac-080ef702bb39", title = "Wet the mops before cleaning", icon = Icons.Outlined.WaterDrop),
        q(QuirkSection.Mop, "4a52e16b-3c73-479d-b308-7f0bbdde0884", title = "Leave the mops in the base station",
            subtitle = "For vacuum-only cleaning.", icon = Icons.AutoMirrored.Outlined.Logout),

        q(QuirkSection.Dock, "2d4ce805-ebf7-4dcf-b919-c5fe4d4f2de3", "c0ccbdfe-c942-41ac-a770-ad1efdc98f8b", title = "Mop wash intensity",
            subtitle = "Higher levels use more water and take longer.", icon = Icons.Outlined.Opacity),
        q(QuirkSection.Dock, "b4ca6500-a461-49cb-966a-4726a33ad3df", title = "Mop wash duration", icon = Icons.Outlined.Timer),
        q(QuirkSection.Dock, "a2a03d42-c710-45e5-b53a-4bc62778589f", title = "Add detergent", icon = Icons.Outlined.Sanitizer,
            label = { when (it) { "on" -> "On"; "off" -> "Off"; else -> it } }),
        q(QuirkSection.Dock, "7f97b603-967f-44f0-9dfb-35bcdc21f433", title = "UV treatment of waste water", icon = Icons.Outlined.LightMode),
        q(QuirkSection.Dock, "8aa6f147-dbcc-44f6-a9f9-2a7f4fb59c7e", title = "Base station self-cleaning", icon = Icons.Outlined.CleaningServices),
        q(QuirkSection.Dock, "96a98473-d60c-4ed9-8ef4-b71f023085a0", title = "Auto-empty noise",
            subtitle = "Quiet mode empties less well.", icon = Icons.AutoMirrored.Outlined.VolumeDown),
        q(QuirkSection.Dock, "78d15359-c431-4602-99ed-17fd74a6d91d", title = "Dry the dust bag after mopping", icon = Icons.Outlined.Shower),
        q(QuirkSection.Dock, "9e194ec7-bc25-4187-8163-b796f213c3c3", title = "Dust bag drying", icon = Icons.Outlined.HeatPump),

        q(QuirkSection.DockService, "42c7db4b-2cad-4801-a526-44de8944a41f", title = "Clean the base station",
            subtitle = "The robot must be docked. Have a brush ready.", icon = Icons.Outlined.CleaningServices),
        q(QuirkSection.DockService, "ae753798-aa4f-4b35-a60c-91e7e5ae76f3", title = "Remove air from the water system",
            subtitle = "Use this if the mops do not stay wet.", icon = Icons.Outlined.Build),
        q(QuirkSection.DockService, "3e1b0851-3a5a-4943-bea6-dea3d7284bff", title = "Drain the robot water tank",
            subtitle = "Use this before transport or storage.", icon = Icons.Outlined.WaterDamage),
        q(QuirkSection.DockService, "86094736-d66e-40c3-807c-3f5ef33cbf09", title = "Test the water connection",
            subtitle = "For a permanent water connection.", icon = Icons.Outlined.Plumbing),

        q(QuirkSection.Battery, "afa79ff9-2078-4d39-b960-c61c2baa42b1", title = "Charging limit",
            subtitle = "A lower limit can make the battery last longer.", icon = Icons.Outlined.BatteryChargingFull),
        q(QuirkSection.Battery, "a09a213f-703d-4b3e-aafe-af55356f2c45", title = "Power saving (EU ErP)",
            subtitle = "Saves more power, but the robot can react more slowly.", icon = Icons.Outlined.EnergySavingsLeaf),
    )

    val placed = specs.flatMap { it.ids }.toSet()
}

/** Adds a row for each quirk of [section] that the robot has. */
fun RowList.quirkRows(section: QuirkSection, quirks: Map<String, Quirk>) {
    KnownQuirks.specs.filter { it.section == section }.forEach { spec ->
        val q = spec.ids.firstNotNullOfOrNull { quirks[it] } ?: return@forEach
        row { QuirkRow(q, spec.title, spec.subtitle, spec.icon, spec.label) }
    }
}

suspend fun loadQuirks(s: RobotSession): Map<String, Quirk> =
    s.capGet(Cap.Quirks).arr?.mapNotNull {
        Quirk(it["id"].str ?: return@mapNotNull null, it["title"].str ?: "", it["description"].str ?: "", it["options"].strList, it["value"].str ?: "")
    }?.associateBy { it.id } ?: emptyMap()

private fun isOnOff(q: Quirk) = q.options.toSet() == setOf("on", "off")

/** A switch, a choice or an action button for one quirk. */
@Composable
fun QuirkRow(q: Quirk, title: String = q.title, subtitle: String? = null, icon: ImageVector? = null, label: (String) -> String = { Labels.of(it) }) {
    val s = session
    var value by remember(q) { mutableStateOf(q.value) }
    var confirm by remember { mutableStateOf(false) }
    fun set(v: String) {
        val old = value
        value = v
        s.action { try { s.capPut(Cap.Quirks, jsonObj("id" to q.id, "value" to v)) } catch (e: Exception) { value = old; throw e } }
    }
    when {
        q.isTrigger -> {
            SettingRow(title, icon, subtitle = subtitle ?: q.description, chevron = false, onClick = { confirm = true }) {
                OutlinedButton(onClick = { confirm = true }) { Text("Start") }
            }
            if (confirm) ConfirmDialog(title, q.description, confirm = "Start", onDismiss = { confirm = false }) {
                s.action("$title: started.") { s.capPut(Cap.Quirks, jsonObj("id" to q.id, "value" to "trigger")) }
            }
        }
        isOnOff(q) -> SwitchRow(title, value == "on", icon, subtitle) { set(if (it) "on" else "off") }
        else -> ChoiceRow(title, value, q.options, icon, subtitle, label = label) { set(it) }
    }
}
