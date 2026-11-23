package com.mdobak.vacuums.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import com.mdobak.vacuums.data.RobotSession

val LocalSession = compositionLocalOf<RobotSession?> { null }
val LocalNav = staticCompositionLocalOf<NavHostController> { error("No navigation") }

/** The current session. Only call in robot routes. */
val session: RobotSession @Composable get() = LocalSession.current!!
