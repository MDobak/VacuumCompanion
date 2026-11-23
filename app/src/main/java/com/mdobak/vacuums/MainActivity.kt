package com.mdobak.vacuums

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mdobak.vacuums.data.Discovery
import com.mdobak.vacuums.data.RobotSession
import com.mdobak.vacuums.data.RobotStore
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.ui.LocalSession
import com.mdobak.vacuums.ui.control.CameraScreen
import com.mdobak.vacuums.ui.control.ManualControlScreen
import com.mdobak.vacuums.ui.devices.DevicesScreen
import com.mdobak.vacuums.ui.home.HomeScreen
import com.mdobak.vacuums.ui.map.MapEditorScreen
import com.mdobak.vacuums.ui.map.MapScreen
import com.mdobak.vacuums.ui.schedule.ScheduleEditScreen
import com.mdobak.vacuums.ui.schedule.SchedulesScreen
import com.mdobak.vacuums.ui.settings.*
import com.mdobak.vacuums.ui.theme.Appearance
import com.mdobak.vacuums.ui.theme.VacuumCompanionTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VacuumCompanionApp : Application()

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = RobotStore(app)
    val discovery = Discovery(app)
    private val prefs = app.getSharedPreferences("app", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow<RobotSession?>(null)
    val session: StateFlow<RobotSession?> = _session

    private val _appearance = MutableStateFlow(runCatching { Appearance.valueOf(prefs.getString("appearance", "System")!!) }.getOrDefault(Appearance.System))
    val appearance: StateFlow<Appearance> = _appearance

    fun setAppearance(a: Appearance) {
        _appearance.value = a
        prefs.edit().putString("appearance", a.name).apply()
    }

    fun open(host: String): RobotSession {
        _session.value?.let { if (it.host == host) return it; it.pause() }
        val s = RobotSession(host, viewModelScope)
        _session.value = s
        store.lastHost = host
        s.resume()
        return s
    }

    fun close() {
        _session.value?.pause()
        _session.value = null
        store.lastHost = null
    }

    override fun onCleared() {
        _session.value?.pause()
        discovery.stop()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val vm: AppViewModel = viewModel()
            val appearance by vm.appearance.collectAsState()
            VacuumCompanionTheme(appearance) { AppRoot(vm) }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val session by vm.session.collectAsState()

    // Pause live updates while the app is in the background.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> session?.resume()
                Lifecycle.Event.ON_STOP -> session?.pause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val start = remember {
        val last = vm.store.lastHost
        if (last != null && vm.store.robots.value.any { it.host == last }) { vm.open(last); "home" } else "devices"
    }

    CompositionLocalProvider(LocalSession provides session, LocalNav provides nav) {
        NavHost(
            navController = nav,
            startDestination = start,
            enterTransition = { slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } },
            exitTransition = { slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } },
            popEnterTransition = { slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } },
            popExitTransition = { slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } },
        ) {
            composable("devices") {
                DevicesScreen(vm) { host ->
                    vm.open(host)
                    nav.navigate("home") { popUpTo("devices") { inclusive = true } }
                }
            }
            robotRoute("home") { HomeScreen(onSwitchRobot = { vm.close(); nav.navigate("devices") { popUpTo(0) } }) }
            robotRoute("map/{mode}") { MapScreen(it.arguments?.getString("mode") ?: "view") }
            robotRoute("editor/{tab}") { MapEditorScreen(it.arguments?.getString("tab") ?: "rooms") }
            robotRoute("manual") { ManualControlScreen() }
            robotRoute("camera") { CameraScreen() }
            robotRoute("schedules") { SchedulesScreen() }
            robotRoute("schedule/{id}") { ScheduleEditScreen(it.arguments?.getString("id") ?: "new") }
            robotRoute("settings") { SettingsScreen(vm) }
            robotRoute("settings/map") { MapSettingsScreen() }
            robotRoute("settings/consumables") { ConsumablesScreen() }
            robotRoute("settings/dnd") { DndScreen() }
            robotRoute("settings/voice") { VoicePackScreen() }
            robotRoute("settings/wifi") { WifiScreen() }
            robotRoute("settings/advanced") { AdvancedScreen(vm) }
            robotRoute("settings/quirks") { QuirksScreen() }
            robotRoute("settings/network") { NetworkScreen() }
            robotRoute("settings/updates") { UpdatesScreen() }
            robotRoute("settings/system") { SystemInfoScreen() }
            robotRoute("settings/log") { LogScreen() }
        }
    }
}

/** A route that needs a robot session. Without one, it shows nothing. */
private fun androidx.navigation.NavGraphBuilder.robotRoute(
    route: String,
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(route) { entry ->
        if (LocalSession.current != null) content(entry)
    }
}

fun NavHostController.back() {
    if (!popBackStack()) navigate("home")
}
