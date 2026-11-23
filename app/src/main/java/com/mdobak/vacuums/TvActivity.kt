package com.mdobak.vacuums

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdobak.vacuums.ui.theme.Appearance
import com.mdobak.vacuums.ui.theme.VacuumCompanionTheme
import com.mdobak.vacuums.ui.tv.TvRoot

/** The simplified interface for Android TV: basic controls, a view-only map and the camera. */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            VacuumCompanionTheme(Appearance.Dark) { TvRoot(vm) }
        }
    }
}
