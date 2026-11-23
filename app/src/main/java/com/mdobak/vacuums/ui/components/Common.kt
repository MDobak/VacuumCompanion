package com.mdobak.vacuums.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdobak.vacuums.data.RobotSession
import com.mdobak.vacuums.ui.LocalSession
import com.mdobak.vacuums.ui.theme.Theme
import kotlinx.coroutines.CancellationException

class LoadState<T>(private val onChange: ((T?) -> Unit)? = null) {
    private var _value by mutableStateOf<T?>(null)

    /** The data. A change also updates the session cache. */
    var value: T?
        get() = _value
        set(v) { _value = v; onChange?.invoke(v) }
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    internal var trigger by mutableIntStateOf(0)
    fun reload() { trigger++ }
    internal fun restore(v: T) { _value = v; loading = false }
}

/** Loads data for a composable. Cached data from the session shows first, then fresh data replaces it. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, loader: suspend () -> T): LoadState<T> {
    val session = LocalSession.current
    val cacheKey = "${currentCompositeKeyHashCode}/${keys.joinToString("/")}"
    val state = remember(*keys) {
        LoadState<T> { v -> if (v == null) session?.uiCache?.remove(cacheKey) else session?.uiCache?.put(cacheKey, v) }.also { st ->
            @Suppress("UNCHECKED_CAST")
            (session?.uiCache?.get(cacheKey) as T?)?.let { st.restore(it) }
        }
    }
    LaunchedEffect(state, state.trigger) {
        state.loading = true
        try {
            state.value = loader()
            state.error = null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.error = RobotSession.errorText(e)
        }
        state.loading = false
    }
    return state
}

/** Shows a spinner, an error with retry, or the content. */
@Composable
fun <T> Loaded(state: LoadState<T>, content: @Composable (T) -> Unit) {
    val v = state.value
    when {
        v != null -> content(v)
        state.error != null -> ErrorBox(state.error!!) { state.reload() }
        else -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun ErrorBox(text: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val session = LocalSession.current
    val snackbar = remember { SnackbarHostState() }
    if (session != null) {
        LaunchedEffect(session) { session.messages.collect { snackbar.showSnackbar(it) } }
    }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        floatingActionButton = floating,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    ) { pad ->
        val mod = Modifier.padding(pad).fillMaxSize()
        if (scrollable) {
            Column(mod.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), content = content)
        } else {
            Column(mod, content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Theme.colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 60.dp, end = 16.dp), color = Theme.colors.divider)
}

@Composable
fun RowIcon(icon: ImageVector?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (icon == null) {
        Spacer(Modifier.width(4.dp))
        return
    }
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SettingRow(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    chevron: Boolean = true,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) titleColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Theme.colors.textSecondary, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (onClick != null && chevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Theme.colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean?,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(title = title, icon = icon, subtitle = subtitle, enabled = enabled && checked != null, chevron = false,
        onClick = { if (checked != null) onChange(!checked) }) {
        if (checked == null) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
fun ChoiceRow(
    title: String,
    value: String?,
    options: List<String>,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    label: (String) -> String = { com.mdobak.vacuums.ui.Labels.of(it) },
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title = title, icon = icon, subtitle = subtitle, value = value?.let(label) ?: "…", enabled = enabled && options.isNotEmpty(), onClick = { open = true })
    if (open) {
        OptionSheet(title = title, options = options, selected = value, label = label, onDismiss = { open = false }) {
            open = false
            onSelect(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(
    title: String,
    options: List<String>,
    selected: String?,
    label: (String) -> String = { com.mdobak.vacuums.ui.Labels.of(it) },
    description: String? = null,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = Theme.colors.textSecondary, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            }
            options.forEach { o ->
                val sel = o == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = sel, role = Role.RadioButton) { onSelect(o) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label(o), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    if (sel) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String = "OK",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirm: String = "Save",
    validate: (String) -> String? = { null },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val err = validate(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
                isError = err != null, supportingText = err?.let { { Text(it) } })
        },
        confirmButton = { TextButton(enabled = err == null, onClick = { onDismiss(); onConfirm(text.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Theme.colors.textSecondary, modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp))
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, selected: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
