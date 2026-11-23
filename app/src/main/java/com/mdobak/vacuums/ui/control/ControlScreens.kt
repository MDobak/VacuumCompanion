package com.mdobak.vacuums.ui.control

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.LocalNav
import com.mdobak.vacuums.back
import com.mdobak.vacuums.ui.components.*
import com.mdobak.vacuums.ui.session
import com.mdobak.vacuums.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.math.min

/** The live MPEG-1 camera stream, played by JSMpeg in a WebView with the robot as origin. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CameraView(modifier: Modifier = Modifier) {
    val s = session
    val context = LocalContext.current
    val html = remember {
        val js = context.assets.open("jsmpeg.min.js").bufferedReader().use { it.readText() }
        CAMERA_HTML.replace("/*JSMPEG*/", js).replace("STREAM_URL", s.api.url("robot/capabilities/${Cap.Duststreaming}/stream"))
    }
    val origin = remember { s.api.baseUrl + "/" }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AColor.BLACK)
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                // Keeps the remote control keys with the app on Android TV.
                isFocusable = false
                isFocusableInTouchMode = false
                loadDataWithBaseURL(origin, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { wv ->
            wv.evaluateJavascript("window.stopStream && stopStream()", null)
            wv.loadUrl("about:blank")
            wv.destroy()
        },
    )
}

private const val CAMERA_HTML = """<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
canvas{width:100%;height:100%;object-fit:contain;display:block}
#m{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#bbb;font:14px sans-serif}</style>
<script>/*JSMPEG*/</script></head><body><canvas id="c"></canvas><div id="m">Connecting…</div>
<script>
class FetchSource {
  constructor(url, opts) { this.url = url; this.streaming = true; this.established = false; this.completed = false; this.progress = 0; this.destination = null; this.stopped = false; }
  connect(d) { this.destination = d; }
  start() {
    if (this.stopped) return;
    this.ctrl = new AbortController();
    fetch(this.url, { cache: 'no-store', signal: this.ctrl.signal }).then(r => {
      if (!r.ok || !r.body) throw new Error('HTTP ' + r.status);
      return this.pump(r.body.getReader());
    }).catch(e => this.retry());
  }
  pump(reader) {
    return reader.read().then(res => {
      if (res.done) return this.retry();
      if (!this.established) { this.established = true; this.progress = 1; document.getElementById('m').style.display = 'none'; }
      this.destination && this.destination.write(res.value.buffer.slice(res.value.byteOffset, res.value.byteOffset + res.value.byteLength));
      return this.pump(reader);
    });
  }
  retry() {
    if (this.stopped) return;
    this.established = false;
    document.getElementById('m').style.display = 'flex';
    document.getElementById('m').textContent = 'Connecting…';
    setTimeout(() => this.start(), 2000);
  }
  resume() {}
  destroy() { this.stopped = true; this.ctrl && this.ctrl.abort(); }
}
var player = new JSMpeg.Player('STREAM_URL', { source: FetchSource, canvas: document.getElementById('c'), autoplay: true, audio: false, videoBufferSize: 1024*1024, disableGl: false });
function stopStream() { try { player.destroy(); } catch (e) {} }
</script></body></html>"""

@Composable
fun CameraScreen() {
    val s = session
    val nav = LocalNav.current
    val caps by s.caps.collectAsState()
    val enabled by s.cameraEnabled.collectAsState()
    LaunchedEffect(Unit) { s.refreshCamera() }
    SubScreen("Camera", onBack = { nav.back() }, scrollable = false, actions = { CameraLightButton(caps) }) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            if (enabled) CameraView(Modifier.fillMaxSize()) else CameraOff()
        }
    }
}

@Composable
private fun CameraOff() {
    val s = session
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(Icons.Outlined.VideocamOff, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("The camera stream is off.", color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = {
            s.action {
                s.api.put("valetudo/config/duststreaming", jsonObj("enabled" to true))
                s.setCameraEnabled(true)
            }
        }) { Text("Turn on the stream") }
    }
}

@Composable
private fun CameraLightButton(caps: Set<String>) {
    val s = session
    if (Cap.CameraLight !in caps) return
    val light = rememberLoad(Unit) { s.capGet(Cap.CameraLight)["enabled"].bool == true }
    val on = light.value ?: return
    IconButton(onClick = { s.action { s.setEnabled(Cap.CameraLight, !on); light.value = !on } }) {
        Icon(if (on) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff, "Camera light")
    }
}

@Composable
fun ManualControlScreen() {
    val s = session
    val nav = LocalNav.current
    val caps by s.caps.collectAsState()
    val st by s.state.collectAsState()
    val camera by s.cameraEnabled.collectAsState()
    val c = Theme.colors
    val hiRes = Cap.ManualControlHiRes in caps
    val cap = if (hiRes) Cap.ManualControlHiRes else Cap.ManualControl
    val remote = rememberLoad(cap) { s.capGet(cap)["enabled"].bool == true }
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(remote.value) { if (remote.value == true) active = true }
    var vector by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    fun leave() {
        if (active) s.action { s.capPut(cap, jsonObj("action" to "disable")) }
        nav.back()
    }
    BackHandler { leave() }

    // Send the joystick vector at a fixed rate while the user drags.
    LaunchedEffect(active, dragging) {
        if (!active || !hiRes) return@LaunchedEffect
        if (!dragging) {
            runCatching { s.capPut(cap, jsonObj("action" to "move", "vector" to mapOf("velocity" to 0, "angle" to 0))) }
            return@LaunchedEffect
        }
        while (isActive) {
            val v = vector
            val velocity = (-v.y).coerceIn(-1f, 1f)
            val angle = (v.x * 120f).coerceIn(-180f, 180f)
            runCatching {
                s.capPut(cap, jsonObj("action" to "move", "vector" to mapOf("velocity" to velocity, "angle" to angle)))
            }
            delay(250)
        }
    }

    SubScreen("Remote control", onBack = { leave() }, scrollable = false, actions = { if (camera) CameraLightButton(caps) }) {
        val showCamera = Cap.Duststreaming in caps && camera
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val w = maxWidth
            val h = maxHeight
            val landscape = w > h
            val controls: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit = { mod, stick ->
                Column(mod.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            remote.value == null -> "Checking the robot…"
                            !active -> "Remote control moves the robot off the dock. Stay near the robot."
                            hiRes -> "Drag the control to drive. Up is forward."
                            else -> "Each button moves the robot one step."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    if (hiRes) Joystick(diameter = stick, enabled = active, onMove = { vector = it }, onActive = { dragging = it })
                    else DirectionPad(enabled = active)
                    Spacer(Modifier.weight(1f))
                    if (!active) {
                        Button(onClick = {
                            s.action { s.capPut(cap, jsonObj("action" to "enable")); active = true }
                        }, enabled = remote.value != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Outlined.Gamepad, null); Spacer(Modifier.width(8.dp)); Text("Start remote control")
                        }
                    } else {
                        OutlinedButton(onClick = {
                            s.action { s.capPut(cap, jsonObj("action" to "disable")); active = false }
                        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("End remote control") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Battery ${st.battery ?: "–"}%  ·  ${com.mdobak.vacuums.ui.Labels.status(st)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    val panelWidth = if (showCamera) minOf(w * 0.4f, 320.dp) else w
                    if (showCamera) Box(Modifier.fillMaxHeight().width(w - panelWidth).background(Color.Black), contentAlignment = Alignment.Center) {
                        CameraView(Modifier.fillMaxSize())
                    }
                    controls(Modifier.fillMaxHeight().width(panelWidth), minOf(240.dp, h - 170.dp).coerceAtLeast(120.dp))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    val camHeight = if (showCamera) h * 0.45f else 0.dp
                    if (showCamera) Box(Modifier.fillMaxWidth().height(camHeight).background(Color.Black), contentAlignment = Alignment.Center) {
                        CameraView(Modifier.fillMaxSize())
                    }
                    controls(Modifier.fillMaxWidth().weight(1f), minOf(240.dp, h - camHeight - 190.dp).coerceAtLeast(120.dp))
                }
            }
        }
    }
}

/** Step buttons for robots that only have the basic manual control. */
@Composable
private fun DirectionPad(enabled: Boolean) {
    val s = session
    val props = rememberLoad(Unit) { s.props(Cap.ManualControl)["supportedMovementCommands"].strList }
    val supported = props.value ?: emptyList()
    @Composable
    fun Step(command: String, icon: ImageVector, label: String) {
        FilledTonalIconButton(
            onClick = { s.action { s.capPut(Cap.ManualControl, jsonObj("action" to "move", "movementCommand" to command)) } },
            enabled = enabled && command in supported,
            modifier = Modifier.size(72.dp),
        ) { Icon(icon, label, Modifier.size(32.dp)) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Step("forward", Icons.Outlined.KeyboardArrowUp, "Forward")
        Row(horizontalArrangement = Arrangement.spacedBy(84.dp)) {
            Step("rotate_counterclockwise", Icons.AutoMirrored.Outlined.RotateLeft, "Turn left")
            Step("rotate_clockwise", Icons.AutoMirrored.Outlined.RotateRight, "Turn right")
        }
        Step("backward", Icons.Outlined.KeyboardArrowDown, "Backward")
    }
}

@Composable
private fun Joystick(diameter: androidx.compose.ui.unit.Dp = 240.dp, enabled: Boolean, onMove: (Offset) -> Unit, onActive: (Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val ring = MaterialTheme.colorScheme.outlineVariant
    var knob by remember { mutableStateOf(Offset.Zero) }
    val enabledState by rememberUpdatedState(enabled)
    Box(
        Modifier.size(diameter).clip(RoundedCornerShape(50)).background(track)
            .pointerInput(Unit) {
                val radius = min(size.width, size.height) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!enabledState) return@awaitEachGesture
                    onActive(true)
                    fun update(p: Offset) {
                        var d = p - center
                        val len = hypot(d.x, d.y)
                        val max = radius * 0.7f
                        if (len > max) d *= max / len
                        knob = d
                        onMove(Offset(d.x / max, d.y / max))
                    }
                    update(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        update(ch.position)
                    }
                    knob = Offset.Zero
                    onMove(Offset.Zero)
                    onActive(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(ring, r * 0.7f, style = Stroke(1.dp.toPx()))
            drawLine(ring, Offset(center.x, center.y - r * 0.85f), Offset(center.x, center.y + r * 0.85f), 1.dp.toPx())
            drawLine(ring, Offset(center.x - r * 0.85f, center.y), Offset(center.x + r * 0.85f, center.y), 1.dp.toPx())
            val k = center + knob
            drawCircle(primary.copy(alpha = if (enabled) 0.25f else 0.08f), r * 0.32f, k)
            drawCircle(if (enabled) primary else ring, r * 0.24f, k)
        }
        Icon(Icons.Outlined.KeyboardArrowUp, null, Modifier.align(Alignment.TopCenter).padding(top = 8.dp), tint = Theme.colors.textSecondary)
    }
}
