package com.mdobak.vacuums.ui.map

import android.content.Context

/** Keeps the map layers that the user selected. */
object LayerPrefs {
    private const val FILE = "map_layers"

    fun load(context: Context): MapLayerOptions {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = MapLayerOptions()
        return MapLayerOptions(
            showPath = p.getBoolean("path", d.showPath),
            coverage = p.getBoolean("coverage", d.coverage),
            showObstacles = p.getBoolean("obstacles", d.showObstacles),
            showRestrictions = p.getBoolean("restrictions", d.showRestrictions),
            showAnnotations = p.getBoolean("annotations", d.showAnnotations),
            showLabels = p.getBoolean("labels", d.showLabels),
        )
    }

    fun save(context: Context, o: MapLayerOptions) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            putBoolean("path", o.showPath)
            putBoolean("coverage", o.coverage)
            putBoolean("obstacles", o.showObstacles)
            putBoolean("restrictions", o.showRestrictions)
            putBoolean("annotations", o.showAnnotations)
            putBoolean("labels", o.showLabels)
        }.apply()
    }
}
