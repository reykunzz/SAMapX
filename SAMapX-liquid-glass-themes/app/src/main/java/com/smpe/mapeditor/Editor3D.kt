@file:OptIn(ExperimentalMaterial3Api::class)

package com.smpe.mapeditor

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import android.opengl.GLSurfaceView
import android.net.Uri
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlEditor(vm: EditorVM, game: GameData) {
    val st = remember(game, vm.fileName) { sceneStats(vm.entries) }
    val gl = remember(game, vm.fileName) {
        GlScene(game).also { s -> s.tx = st[0]; s.ty = st[1]; s.tz = st[2]; s.dist = 150f; vm.gl = s }
    }
    var panMode by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(true) }
    var panelExpanded by remember { mutableStateOf(true) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerWidth = this.maxWidth
        val containerHeight = this.maxHeight
        val panelWidth = if (containerWidth < 420.dp) (containerWidth - 24.dp).coerceAtLeast(220.dp) else 320.dp
        Viewport(vm, gl, st, panMode, { panMode = it }, showPanel, { showPanel = !showPanel },
            Modifier.fillMaxSize())
        val selObj = vm.entries.getOrNull(vm.sel) as? Obj
        if (selObj != null && (!showPanel || containerWidth >= 700.dp)) {
            SelCard(vm, gl, selObj, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 46.dp))
            // Kanan bawah: gizmo di atas, lalu tombol kamera
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OrbitGizmo(gl, Modifier)
                CameraPad(vm, gl, Modifier)
            }
        }
        if (showPanel) {
            Column(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 8.dp, top = 58.dp, bottom = 8.dp)
                    .width(panelWidth)
                    .heightIn(max = containerHeight - 76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
                            )
                        ), RoundedCornerShape(18.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(I18n.t("INSPECTOR"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    TextButton(onClick = { panelExpanded = !panelExpanded }) {
                        Text(if (panelExpanded) I18n.t("Minimize") else I18n.t("Buka"))
                    }
                    TextButton(onClick = { showPanel = false }) { Text(I18n.t("Tutup")) }
                }
                if (panelExpanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Panel(vm, gl, st, Modifier.fillMaxWidth().heightIn(max = containerHeight - 120.dp))
                }
            }
        }
    }
}

@Composable
fun Viewport(
    vm: EditorVM, gl: GlScene, st: FloatArray,
    panMode: Boolean, setPan: (Boolean) -> Unit,
    panelVisible: Boolean, togglePanel: () -> Unit, modifier: Modifier
) {
    val density = LocalDensity.current.density
    val pm = rememberUpdatedState(panMode)
    val modeKey = vm.topDown to vm.selSet.toList()
    // Mode Objek: kamera selalu berpusat di objek yang dipilih.
    LaunchedEffect(vm.sel, vm.camMode) {
        if (vm.camMode == 1) {
            val o = vm.entries.getOrNull(vm.sel) as? Obj
            if (o != null) { gl.tx = o.x; gl.ty = o.y; gl.tz = o.z; gl.requestRender() }
        }
    }
    val exScene = remember(vm.extra) { vm.extra.mapIndexed { i, o -> SceneObj(-(i + 2), o) } }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(800); tick++ } }
    Box(modifier.clipToBounds()) {
        AndroidView(
            factory = { c ->
                GLSurfaceView(c).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    setRenderer(gl)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    gl.view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { v ->
                val sc = vm.entries.mapIndexedNotNull { i, e -> if (e is Obj) SceneObj(i, e) else null }
                sc.forEachIndexed { k, so -> so.pos = k }
                for (so in sc) { val l = so.o.lod; if (l != null && l >= 0 && l < sc.size) sc[l].isLod = true }
                for (so in exScene) { val l = so.o.lod; if (l != null && l >= 0 && l < sc.size) sc[l].isLod = true }
                gl.scene = sc
                gl.extra = exScene
                gl.sel = vm.sel
                gl.selStream = vm.selStream
                gl.topDown = vm.topDown
                gl.multiSel = vm.selSet.toSet()
                gl.editShow = true
                gl.gridOn = vm.gridOn
                v.requestRender()
            }
        )
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                // Scroll roda mouse: zoom in/out
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == PointerEventType.Scroll) {
                            val dy = ev.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                            if (dy != 0f) {
                                gl.dist = (gl.dist * Math.pow(1.12, dy.toDouble()).toFloat()).coerceAtLeast(0.5f)
                                gl.requestRender()
                            }
                            ev.changes.forEach { it.consume() }
                        }
                    }
                }
            }.pointerInput(Unit) {
                fun camMove(pan: Offset) {
                    if (!pm.value) {
                        gl.yaw -= pan.x * 0.004f
                        if (!gl.topDown) gl.pitch = (gl.pitch + pan.y * 0.004f).coerceIn(0.05f, 1.5f)
                    } else {
                        val k = gl.dist / (minOf(size.width, size.height) * 1.1f)
                        val c = cos(gl.yaw); val s = sin(gl.yaw)
                        gl.tx += -pan.x * k * c - pan.y * k * s
                        gl.ty += -pan.x * k * s + pan.y * k * c
                    }
                    gl.requestRender()
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val axis = if (vm.sel >= 0 && (vm.entries.getOrNull(vm.sel) as? Obj)?.locked != true) gl.hitGizmo(down.position.x, down.position.y, 44f * density) else -1
                    var moved = false
                    var started = false
                    var startVal = 0f
                    var acc = 0f
                    var last = down.position
                    val grp = if (axis < 0 && !pm.value) vm.groupIdx().filter { (vm.entries.getOrNull(it) as? Obj)?.locked != true } else emptyList()
                    val freeHit = grp.isNotEmpty() && gl.pick(down.position.x, down.position.y, 24f * density) in grp
                    val gx = FloatArray(grp.size); val gy = FloatArray(grp.size)
                    var fdx = 0f; var fdy = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            moved = true
                            val a = pressed[0]; val b = pressed[1]
                            val span = (a.position - b.position).getDistance()
                            val prev = (a.previousPosition - b.previousPosition).getDistance()
                            if (prev > 1f && span > 1f) gl.dist = (gl.dist * prev / span).coerceAtLeast(0.5f)
                            camMove((a.position + b.position) / 2f - (a.previousPosition + b.previousPosition) / 2f)
                            a.consume(); b.consume()
                        } else {
                            val c = pressed[0]
                            if (!moved && (c.position - down.position).getDistance() > 8f * density) moved = true
                            if (axis >= 0) {
                                if (moved) {
                                    val o = vm.entries.getOrNull(vm.sel) as? Obj
                                    if (o != null) {
                                        if (!started) { started = true; vm.beginEdit("Geser " + (if (axis == 0) "X" else if (axis == 1) "Y" else "Z")); startVal = if (axis == 0) o.x else if (axis == 1) o.y else o.z; acc = 0f }
                                        acc += gl.axisDelta(axis, last.x, last.y, c.position.x, c.position.y)
                                        var v = startVal + acc
                                        if (vm.snap > 0f) v = Math.round(v / vm.snap) * vm.snap
                                        vm.setAxis(vm.sel, axis, v)
                                    }
                                    c.consume()
                                }
                            } else if (freeHit) {
                                val dragHold = false
                                val dragDist = Math.hypot((c.position.x - down.position.x).toDouble(), (c.position.y - down.position.y).toDouble())
                                // Mode Jelajah: objek yang sudah dipilih langsung bisa digeser. Mode Kamera/Objek: harus ditahan dulu.
                                if (moved && (!dragHold || c.uptimeMillis - down.uptimeMillis > 500L) && dragDist > (if (dragHold) 32.0 else 12.0) * density) {
                                    if (!started) {
                                        started = true; vm.beginEdit("Geser objek"); fdx = 0f; fdy = 0f
                                        for ((j, idx) in grp.withIndex()) {
                                            val o = vm.entries.getOrNull(idx) as? Obj
                                            if (o != null) { gx[j] = o.x; gy[j] = o.y }
                                        }
                                    }
                                    val pd = c.position - c.previousPosition
                                    val k = gl.dist / (minOf(size.width, size.height) * 1.1f)
                                    val cs = cos(gl.yaw); val sn = sin(gl.yaw)
                                    fdx += pd.x * k * cs + pd.y * k * sn
                                    fdy += pd.x * k * sn - pd.y * k * cs
                                    for ((j, idx) in grp.withIndex()) {
                                        var vx = gx[j] + fdx; var vy = gy[j] + fdy
                                        if (vm.snap > 0f) {
                                            vx = Math.round(vx / vm.snap) * vm.snap
                                            vy = Math.round(vy / vm.snap) * vm.snap
                                        }
                                        vm.setAxis(idx, 0, vx); vm.setAxis(idx, 1, vy)
                                    }
                                    gl.requestRender()
                                    c.consume()
                                }
                            } else if (moved) {
                                camMove(c.position - c.previousPosition)
                                c.consume()
                            }
                            last = c.position
                        }
                    }
                    val held = android.os.SystemClock.uptimeMillis() - down.uptimeMillis
                    // Mode Jelajah: cukup sentuh. Mode Kamera/Objek: harus ditahan agar tidak salah pilih.
                    if (!moved && (vm.camMode == 0 || held >= 400L)) {
                        val i = gl.pick(down.position.x, down.position.y, 40f * density)
                        if (i >= 0) { vm.tapSelect(i); vm.selStream = -1 }
                        else if (i <= -2) { vm.selStream = -(i + 2); vm.selSet.clear(); vm.sel = -1 }
                        else if (!vm.multi) { vm.clearSel(); vm.selStream = -1 }
                        gl.requestRender()
                    }
                }
            }
        )
        ViewToolbar(
            panMode = panMode, setPan = setPan,
            canUndo = vm.canUndo, canRedo = vm.canRedo,
            onFocus = {
                val o = vm.entries.getOrNull(vm.sel) as? Obj
                if (o != null) { gl.tx = o.x; gl.ty = o.y; gl.tz = o.z; gl.dist = minOf(gl.dist, 60f); gl.requestRender() }
            },
            onAll = { gl.tx = st[0]; gl.ty = st[1]; gl.tz = st[2]; gl.dist = st[3] * 1.2f; gl.requestRender() },
            onUndo = { vm.undo(); gl.requestRender() },
            onRedo = { vm.redo(); gl.requestRender() },
            panelVisible = panelVisible, onTogglePanel = togglePanel,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 8.dp, top = 6.dp),
            snap = vm.snap,
            onSnap = {
                val order = listOf(0f, 0.5f, 1f, 5f)
                vm.snap = order[(order.indexOf(vm.snap) + 1) % order.size]
            },
            topDown = vm.topDown,
            onTopDown = {
                vm.topDown = !vm.topDown
                gl.topDown = vm.topDown
                gl.pitch = if (vm.topDown) 1.5f else 0.7f
                gl.requestRender()
            },
            multi = vm.multi,
            onMulti = { vm.multi = !vm.multi },
            undoLabel = vm.undoLabel,
            gridOn = vm.gridOn,
            onGrid = { vm.gridOn = !vm.gridOn; gl.gridOn = vm.gridOn; gl.requestRender() }
        )
        val sel = vm.entries.getOrNull(vm.sel) as? Obj
        val g = vm.game
        val hud = remember(tick, vm.sel, vm.count) {
            val ss = vm.extra.getOrNull(vm.selStream)
            val head = if (sel != null) sel.model + "  (ID " + sel.id + ")" else if (ss != null) "stream: " + ss.model + "  (ID " + ss.id + ")" else vm.count.toString() + " objek"
            if (g != null) head + "  |  model " + g.okCount.get() + " ok, " + g.failCount.get() + " gagal  |  tekstur " +
                g.texdbs.sumOf { it.ok.get() } + " ok, " + g.texdbs.sumOf { it.fail.get() } + " gagal" +
                (if (g.failCount.get() > 0) "  [" + g.reasons.entries.joinToString { it.key + " " + it.value.get() } + "]" else "") else head
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), shape = RoundedCornerShape(10.dp), color = Color(0xE6121714)) {
            Text(hud, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, color = Color.White)
        }
    }
}

fun rotZ(o: Obj, deg: Float): Obj {
    val h = Math.toRadians(-deg.toDouble()).toFloat() / 2f
    val pz = sin(h); val pw = cos(h)
    return o.copy(
        rx = o.rx * pw + o.ry * pz,
        ry = -o.rx * pz + o.ry * pw,
        rz = o.rw * pz + o.rz * pw,
        rw = o.rw * pw - o.rz * pz
    )
}

@Composable
fun Nudge(label: String, mod: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = mod, contentPadding = PaddingValues(0.dp)) { Text(label, fontSize = 12.sp) }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier.border(1.dp, cs.outline.copy(alpha = 0.58f), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.78f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun Panel(vm: EditorVM, gl: GlScene, st: FloatArray, modifier: Modifier) {
    val i = vm.sel
    val o = vm.entries.getOrNull(i) as? Obj
    var step by remember { mutableFloatStateOf(1f) }
    var viewDist by remember { mutableFloatStateOf(gl.viewDist) }
    var hideLod by remember { mutableStateOf(gl.hideLod) }
    var flipV by remember { mutableStateOf(gl.flipV) }
    val scope = rememberCoroutineScope()
    fun loadStream(all: Boolean) {
        val g = vm.game ?: return
        vm.streamNote = "Memuat stream..."
        val base = if (all) null else vm.fileName.lowercase().removeSuffix(".ipl")
        val known = HashMap<Int, String>()
        for (e in vm.entries) if (e is Obj) known[e.id] = e.model
        scope.launch(Dispatchers.IO) {
            val r = g.streamObjects(base, known)
            vm.extra = r.first; vm.streamNote = r.second
            gl.requestRender()
        }
    }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.sel, vm.selStream, vm.selSet.size) { if (vm.sel >= 0 || vm.selStream >= 0 || vm.selSet.size > 1) tab = 0 }
    Column(modifier) {
    PanelTabs(tab) { tab = it }
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (tab == 0) {
        SectionCard(I18n.t("OBJEK TERPILIH")) {
            if (o == null) {
                Text(I18n.t("Ketuk objek di tampilan 3D untuk memilihnya."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(modelColor(o.model), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(o.model, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("ID ${o.id}", style = MaterialTheme.typography.labelMedium)
                }
                AxisRow(o.x, o.y, o.z)
                Text(I18n.t("Seret panah merah (X), hijau (Y), biru (Z) di layar 3D untuk menggeser objek."), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val sobj = vm.extra.getOrNull(vm.selStream)
        if (sobj != null) {
            SectionCard(I18n.t("OBJEK STREAM (DILIHAT SAJA)")) {
                Text(sobj.model + "  (ID " + sobj.id + ")", fontWeight = FontWeight.Bold)
                AxisRow(sobj.x, sobj.y, sobj.z)
                Button(onClick = { vm.adoptStream(); gl.requestRender() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(I18n.t("Salin ke IPL agar bisa diedit"))
                }
                Text(I18n.t("Objek aslinya tetap ada di stream, jadi hasilnya objek tambahan."), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (vm.selSet.size > 1) GroupCard(vm, gl, step)
        if (o != null) {
            SectionCard(I18n.t("GERAKKAN")) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in listOf(0.1f, 1f, 5f, 20f)) FilterChip(selected = step == s, onClick = { step = s }, label = { Text(s.toString()) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(I18n.t("Snap seret:"), style = MaterialTheme.typography.labelMedium)
                    for (s in listOf(0f, 0.5f, 1f, 5f)) FilterChip(selected = vm.snap == s, onClick = { vm.snap = s }, label = { Text(if (s == 0f) I18n.t("Off") else s.toString()) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Nudge("X-", Modifier.weight(1f)) { vm.update(i, o.copy(x = o.x - step)) }
                    Nudge("X+", Modifier.weight(1f)) { vm.update(i, o.copy(x = o.x + step)) }
                    Nudge("Y-", Modifier.weight(1f)) { vm.update(i, o.copy(y = o.y - step)) }
                    Nudge("Y+", Modifier.weight(1f)) { vm.update(i, o.copy(y = o.y + step)) }
                    Nudge("Z-", Modifier.weight(1f)) { vm.update(i, o.copy(z = o.z - step)) }
                    Nudge("Z+", Modifier.weight(1f)) { vm.update(i, o.copy(z = o.z + step)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Nudge("-90", Modifier.weight(1f)) { vm.update(i, rotZ(o, -90f)) }
                    Nudge("-15", Modifier.weight(1f)) { vm.update(i, rotZ(o, -15f)) }
                    Nudge("+15", Modifier.weight(1f)) { vm.update(i, rotZ(o, 15f)) }
                    Nudge("+90", Modifier.weight(1f)) { vm.update(i, rotZ(o, 90f)) }
                }
            }
            SectionCard(I18n.t("EDIT NILAI")) {
                val key = listOf(i, o.x, o.y, o.z, o.model, o.id)
                var fm by remember(key) { mutableStateOf(o.model) }
                var fid by remember(key) { mutableStateOf(o.id.toString()) }
                var fx by remember(key) { mutableStateOf(String.format(Locale.US, "%.3f", o.x)) }
                var fy by remember(key) { mutableStateOf(String.format(Locale.US, "%.3f", o.y)) }
                var fz by remember(key) { mutableStateOf(String.format(Locale.US, "%.3f", o.z)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(I18n.t("Model"), fm, Modifier.weight(2f), num = false) { fm = it }
                    Field(I18n.t("ID"), fid, Modifier.weight(1f)) { fid = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("X", fx, Modifier.weight(1f)) { fx = it }
                    Field("Y", fy, Modifier.weight(1f)) { fy = it }
                    Field("Z", fz, Modifier.weight(1f)) { fz = it }
                }
                Button(onClick = {
                    vm.update(i, o.copy(
                        model = fm.trim().ifEmpty { o.model }, id = fid.toIntOrNull() ?: o.id,
                        x = fx.toFloatOrNull() ?: o.x, y = fy.toFloatOrNull() ?: o.y, z = fz.toFloatOrNull() ?: o.z))
                }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text(I18n.t("Terapkan")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Nudge(I18n.t("Ke tengah"), Modifier.weight(1f)) { vm.update(i, o.copy(x = gl.tx, y = gl.ty, z = gl.tz)) }
                Nudge(I18n.t("Salin"), Modifier.weight(1f)) { vm.duplicate(i) }
                Nudge(if (o.locked) I18n.t("Buka kunci") else I18n.t("Kunci"), Modifier.weight(1f)) { vm.setLocked(i, !o.locked); gl.requestRender() }
                Nudge(I18n.t("Hapus"), Modifier.weight(1f)) { vm.remove(i) }
            }
        }
        }
        if (tab == 1) {
        SectionCard(I18n.t("IPL STREAM (DETAIL PETA)")) {
            Text(vm.streamNote.ifEmpty { I18n.t("IPL teks hanya memuat sebagian objek (banyak LOD). Detail bangunan ada di file stream biner dalam gta3.img. Hanya untuk dilihat, tidak ikut disimpan.") },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Nudge(I18n.t("Stream ini"), Modifier.weight(1f)) { loadStream(false) }
                Nudge(I18n.t("Semua (berat)"), Modifier.weight(1f)) { loadStream(true) }
                Nudge(I18n.t("Kosongkan"), Modifier.weight(1f)) { vm.extra = emptyList(); vm.streamNote = ""; gl.requestRender() }
            }
        }
        }
        if (tab == 2) {
        SectionCard(I18n.t("ADEGAN")) {
            Button(onClick = { vm.picker = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text(I18n.t("Taruh objek")) }
            ReplaceModelCard(vm)
            Text("Jarak pandang: ${viewDist.toInt()} m", style = MaterialTheme.typography.labelMedium)
            Slider(value = viewDist, onValueChange = { viewDist = it; gl.viewDist = it; gl.requestRender() }, valueRange = 150f..2500f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(I18n.t("Balik tekstur vertikal"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = flipV, onCheckedChange = { flipV = it; gl.flipV = it; gl.requestRender() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(I18n.t("Sembunyikan model LOD"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = hideLod, onCheckedChange = { hideLod = it; gl.hideLod = it; gl.requestRender() })
            }
        }
        Text(vm.gameStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}

@Composable
fun PanelTabs(tab: Int, onTab: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(cs.surface.copy(alpha = 0.82f), RoundedCornerShape(13.dp))
            .border(1.dp, cs.outline.copy(alpha = 0.48f), RoundedCornerShape(13.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf("Objek", "Stream", "Adegan").forEachIndexed { i, label ->
            val on = tab == i
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(
                        if (on) Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.96f), cs.primary.copy(alpha = 0.76f)))
                        else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent))
                    )
                    .then(if (on) Modifier.border(1.dp, cs.secondary.copy(alpha = 0.72f), RoundedCornerShape(9.dp)) else Modifier)
                    .clickable { onTab(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AxisRow(x: Float, y: Float, z: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AxisBox("X", x, Modifier.weight(1f)); AxisBox("Y", y, Modifier.weight(1f)); AxisBox("Z", z, Modifier.weight(1f))
    }
}

@Composable
fun AxisBox(label: String, v: Float, modifier: Modifier) {
    Column(modifier
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(String.format(Locale.US, "%.2f", v), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

/** Kartu kecil objek terpilih di bawah viewport: Salin, Hapus, Tutup. */
@Composable
fun SelCard(vm: EditorVM, gl: GlScene, o: Obj, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = cs.surface.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.72f)), tonalElevation = 2.dp, shadowElevation = 4.dp) {
        Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(o.model, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.widthIn(max = 120.dp))
            Spacer(Modifier.width(6.dp))
            Text("${o.id}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = cs.primary)
            Spacer(Modifier.width(2.dp))
            MiniBtn(UiIcons.Copy, cs.onSurface) { vm.duplicate(vm.sel); gl.requestRender() }
            MiniBtn(Icons.Default.Delete, cs.error) { vm.remove(vm.sel); gl.requestRender() }
            MiniBtn(Icons.Default.Close, cs.onSurfaceVariant) { vm.clearSel(); gl.requestRender() }
        }
    }
}

@Composable
fun MiniBtn(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ModelPicker(vm: EditorVM) {
    val g = vm.game
    var q by remember { mutableStateOf("") }
    val key = q.trim().lowercase()
    val res = if (g == null) emptyList() else g.allModels()
        .filter { it.second > 0 && (key.isEmpty() || it.first.contains(key) || it.second.toString() == key) }
        .sortedBy { it.first }
        .take(400)
    AlertDialog(
        onDismissRequest = { vm.picker = false },
        confirmButton = { TextButton(onClick = { vm.picker = false }) { Text(I18n.t("Batal")) } },
        title = { Text(I18n.t("Taruh objek")) },
        text = {
            Column {
                OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(I18n.t("Cari nama model atau ID")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(res) { d ->
                        Column(
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                val s = vm.gl
                                vm.sel = vm.add(d.first, d.second, s?.tx ?: 0f, s?.ty ?: 0f, s?.tz ?: 10f)
                                vm.picker = false
                            }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ModelThumb(g, d.first, 84.dp)
                            Text(d.first, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DiagDialog(vm: EditorVM, text: String) {
    val clip = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { vm.diag = null },
        confirmButton = { TextButton(onClick = { clip.setText(AnnotatedString(text)) }) { Text(I18n.t("Salin")) } },
        dismissButton = { TextButton(onClick = { vm.diag = null }) { Text(I18n.t("Tutup")) } },
        title = { Text(I18n.t("Diagnosa file game")) },
        text = {
            SelectionContainer {
                Text(text, fontSize = 10.sp, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
            }
        }
    )
}

@Composable
fun BoxScope.GamePrompt(vm: EditorVM, onPick: () -> Unit) {
    Card(
        Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(I18n.t("Tampilkan model GTA asli"), fontWeight = FontWeight.Bold)
            Text(I18n.t("Pilih folder game (berisi gta3.img, Maps, dan texdb). Mendukung GTA SA mobile (tekstur dari texdb) maupun PC. Di Android 11+ folder Android/data tidak bisa dipilih, salin dulu ke Download/GTASA."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.gameStatus.isNotEmpty()) Text(vm.gameStatus, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick, shape = RoundedCornerShape(14.dp)) { Text(I18n.t("Pilih folder GTA SA")) }
                OutlinedButton(onClick = { vm.diag = GameData.lastReport.ifEmpty { I18n.t("Belum ada laporan. Pilih folder GTA SA dulu.") } }, shape = RoundedCornerShape(14.dp)) { Text(I18n.t("Diagnosa")) }
            }
        }
    }
}

fun fileNameOf(ctx: Context, u: Uri): String {
    try {
        ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i) ?: "file"
        }
    } catch (_: Exception) { }
    return u.lastPathSegment ?: "file"
}

@Composable
fun ImportModelCard(vm: EditorVM, gl: GlScene) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dffUri by remember { mutableStateOf<Uri?>(null) }
    var txdUri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val pickDff = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            dffUri = u
            if (name.isBlank()) name = fileNameOf(ctx, u).substringBeforeLast('.').lowercase()
                .filter { it.isLetterOrDigit() || it == '_' }.take(24)
        }
    }
    val pickTxd = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) txdUri = u
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(I18n.t("Impor model baru (DFF + TXD)"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { pickDff.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(dffUri?.let { "DFF: " + fileNameOf(ctx, it) } ?: I18n.t("Pilih file .dff"))
        }
        OutlinedButton(onClick = { pickTxd.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(txdUri?.let { "TXD: " + fileNameOf(ctx, it) } ?: I18n.t("Pilih file .txd (opsional)"))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) },
            label = { Text(I18n.t("Nama model")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val du = dffUri
                val g = vm.game
                when {
                    du == null -> msg = "Pilih file DFF dulu."
                    name.isEmpty() -> msg = "Isi nama model."
                    g == null -> msg = I18n.t("Pilih folder GTA SA dulu.")
                    g.nameTaken(name) -> msg = "Nama '$name' sudah dipakai game atau model lain. Pilih nama lain."
                    else -> {
                        val n = name; val tu = txdUri
                        val used = vm.entries.filterIsInstance<Obj>().map { it.id }.toHashSet()
                        val id = g.nextFreeId(used)
                        msg = "Mengimpor..."
                        scope.launch(Dispatchers.IO) {
                            val dff = try { ctx.contentResolver.openInputStream(du)?.use { it.readBytes() } } catch (e: Exception) { null }
                            val txd = tu?.let { try { ctx.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } } catch (e: Exception) { null } }
                            val ok = dff != null && g.importModel(n, dff, txd)
                            if (ok) { g.customIds[n] = id; CustomStore.save(ctx, n, id, dff!!, txd) }
                            withContext(Dispatchers.Main) {
                                if (ok) {
                                    vm.sel = vm.add(n, id, gl.tx, gl.ty, gl.tz)
                                    gl.requestRender()
                                    msg = "Model $n diimpor dan disimpan (ID $id). Belum masuk gta3.img, jadi jangan dimainkan di game dulu."
                                } else {
                                    msg = "Gagal membaca DFF. Pastikan file format RenderWare (.dff)."
                                }
                            }
                        }
                    }
                }
            },
            enabled = dffUri != null,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
        ) { Text(I18n.t("Impor & tempatkan")) }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GroupCard(vm: EditorVM, gl: GlScene, step: Float) {
    SectionCard("${vm.selSet.size} OBJEK DIPILIH") {
        Text(I18n.t("Ketuk objek untuk menambah atau mengeluarkan dari pilihan. Seret salah satu objek yang dipilih untuk menggeser semuanya."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Nudge("X -", Modifier.weight(1f)) { vm.shiftGroup(0, -step); gl.requestRender() }
            Nudge("X +", Modifier.weight(1f)) { vm.shiftGroup(0, step); gl.requestRender() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Nudge("Y -", Modifier.weight(1f)) { vm.shiftGroup(1, -step); gl.requestRender() }
            Nudge("Y +", Modifier.weight(1f)) { vm.shiftGroup(1, step); gl.requestRender() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Nudge("Z -", Modifier.weight(1f)) { vm.shiftGroup(2, -step); gl.requestRender() }
            Nudge("Z +", Modifier.weight(1f)) { vm.shiftGroup(2, step); gl.requestRender() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Nudge("-90", Modifier.weight(1f)) { vm.rotateGroup(-90f); gl.requestRender() }
            Nudge("+90", Modifier.weight(1f)) { vm.rotateGroup(90f); gl.requestRender() }
        }
        if (vm.selSet.size == 2) {
            val a = vm.entries.getOrNull(vm.selSet[0]) as? Obj
            val b = vm.entries.getOrNull(vm.selSet[1]) as? Obj
            if (a != null && b != null) {
                val dxy = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                val d3 = Math.sqrt(dxy * dxy + (a.z - b.z).toDouble() * (a.z - b.z))
                Text(String.format(Locale.US, "Penggaris: %.2f m (datar %.2f m)", d3, dxy),
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Nudge(I18n.t("Kunci semua"), Modifier.weight(1f)) { vm.setLockedGroup(true); gl.requestRender() }
            Nudge(I18n.t("Buka semua"), Modifier.weight(1f)) { vm.setLockedGroup(false); gl.requestRender() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Nudge(I18n.t("Batal pilih"), Modifier.weight(1f)) { vm.clearSel(); gl.requestRender() }
            Nudge(I18n.t("Hapus semua"), Modifier.weight(1f)) { vm.removeGroup(); gl.requestRender() }
        }
    }
}

@Composable
fun ReplaceModelCard(vm: EditorVM) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(I18n.t("Ganti model"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(I18n.t("Dari"), from, Modifier.weight(1f)) { from = it }
            Field(I18n.t("Menjadi"), to, Modifier.weight(1f)) { to = it }
        }
        Button(
            onClick = {
                val n = vm.replaceModel(from, to)
                msg = if (n > 0) "$n objek diganti." else "Tidak ada objek yang cocok (atau terkunci)."
            },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
        ) { Text(I18n.t("Ganti semua")) }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ExportCard(vm: EditorVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(I18n.t("Export paket mod"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(I18n.t("Membuat IDE, IPL, dan DFF/TXD model impor dalam satu folder. Lalu disalin ke folder game dengan Termux."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                val g = vm.game
                if (g == null) { msg = I18n.t("Pilih folder GTA SA dulu."); return@Button }
                val snap = vm.entries.toList()
                msg = "Mengekspor..."
                scope.launch(Dispatchers.IO) {
                    val r = try { ModExport.export(ctx, g, snap) } catch (e: Exception) { "Gagal: ${e.message}" }
                    withContext(Dispatchers.Main) { msg = r }
                }
            },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
        ) { Text(I18n.t("Export paket mod")) }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Panel kamera: pilihan mode (Jelajah, Kamera, Objek), tombol arah, joystick analog untuk putar kamera. */
@Composable
fun CameraPad(vm: EditorVM, gl: GlScene, modifier: Modifier) {
    var speedLevel by remember { mutableIntStateOf(1) }
    val speeds = listOf(0.03f, 0.08f, 0.2f)
    val cs0 = MaterialTheme.colorScheme
    fun move(dRight: Float, dFwd: Float, dUp: Float) {
        val step = maxOf(0.5f, gl.dist * speeds[speedLevel])
        val cs = cos(gl.yaw); val sn = sin(gl.yaw)
        // kanan = (cs, sn), depan = (-sn, cs)
        val dx = (cs * dRight - sn * dFwd) * step
        val dy = (sn * dRight + cs * dFwd) * step
        val dz = dUp * step
        if (vm.camMode == 1 && vm.sel >= 0) {
            vm.shiftSel(dx, dy, dz)
            val o = vm.entries.getOrNull(vm.sel) as? Obj
            if (o != null) { gl.tx = o.x; gl.ty = o.y; gl.tz = o.z }
        } else {
            gl.tx += dx; gl.ty += dy; gl.tz += dz
        }
        gl.requestRender()
    }
    fun orbit(dYaw: Float, dPitch: Float) {
        gl.yaw += dYaw
        if (!gl.topDown) gl.pitch = (gl.pitch + dPitch).coerceIn(0.05f, 1.5f)
        gl.requestRender()
    }
    fun setMode(newMode: Int) {
        if (vm.camMode == 0 && newMode != 0) {
            vm.savedCam = floatArrayOf(gl.tx, gl.ty, gl.tz, gl.dist, gl.yaw, gl.pitch)
        }
        vm.camMode = newMode
        gl.requestRender()
    }
    fun backToExplore() {
        vm.savedCam?.let { c -> gl.tx = c[0]; gl.ty = c[1]; gl.tz = c[2]; gl.dist = c[3]; gl.yaw = c[4]; gl.pitch = c[5] }
        vm.savedCam = null
        vm.camMode = 0
        gl.requestRender()
    }
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Pilihan mode
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(2 to I18n.t("Kamera"), 1 to I18n.t("Objek")).forEach { (mode, label) ->
                val on = vm.camMode == mode
                Surface(
                    modifier = Modifier.clickable { setMode(mode) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (on) cs0.primary else cs0.surface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, cs0.outline)
                ) {
                    Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) cs0.onPrimary else cs0.onSurface)
                }
            }
        }
        // Tombol arah: pad gaya keyboard (maju di atas, kiri-mundur-kanan di bawah), lalu naik-turun berdempetan
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyPad(onUp = { move(0f, 1f, 0f) }, onDown = { move(0f, -1f, 0f) },
                onLeft = { move(-1f, 0f, 0f) }, onRight = { move(1f, 0f, 0f) })
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                CamBtn("\u2191") { move(0f, 0f, 1f) }
                CamBtn("\u2193") { move(0f, 0f, -1f) }
            }
        }
        // Rotasi objek (mode Objek), kembali, dan kecepatan
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.camMode == 1) {
                CamBtn("\u21BA") { vm.rotateSel(-15f) }
                CamBtn("\u21BB") { vm.rotateSel(15f) }
            }
            if (vm.camMode != 0) CamBtn("\u2190", repeat = false) { backToExplore() }
            CamBtn("${speedLevel + 1}x", repeat = false) { speedLevel = (speedLevel + 1) % speeds.size }
        }
    }
}

/** Joystick analog: tahan dan geser untuk memutar kamera terus-menerus. Nilai -1..1 per frame. */
@Composable
fun Joystick(onDelta: (Float, Float) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val radiusPx = with(LocalDensity.current) { 40.dp.toPx() }
    var knob by remember { mutableStateOf(Offset.Zero) }
    val curOnDelta by rememberUpdatedState(onDelta)
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            val k = knob
            if (k != Offset.Zero) curOnDelta(k.x / radiusPx, k.y / radiusPx)
        }
    }
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(cs.surface.copy(alpha = 0.9f))
            .border(1.dp, cs.outline, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { knob = Offset.Zero },
                    onDragEnd = { knob = Offset.Zero },
                    onDragCancel = { knob = Offset.Zero },
                    onDrag = { change, drag ->
                        change.consume()
                        val nk = knob + drag
                        val len = nk.getDistance()
                        knob = if (len > radiusPx) nk * (radiusPx / len) else nk
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
            .size(34.dp).clip(CircleShape).background(cs.primary))
    }
}

@Composable
fun CamBtn(symbol: String, repeat: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val curClick by rememberUpdatedState(onClick)
    Surface(
        modifier = Modifier.size(36.dp).pointerInput(repeat) {
            detectTapGestures(onPress = {
                curClick()
                val job = if (repeat) scope.launch {
                    delay(300)
                    while (true) { curClick(); delay(60) }
                } else null
                tryAwaitRelease()
                job?.cancel()
            })
        },
        shape = RoundedCornerShape(10.dp), color = cs.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, cs.outline)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(symbol, fontSize = 15.sp, color = cs.onSurface)
        }
    }
}

/** Pratinjau texture model dari DFF (texture diambil dari texdb). Dimuat sekali lalu disimpan di cache. */
@Composable
fun ModelThumb(g: GameData?, name: String, thumb: Dp = 44.dp) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, g, name) {
        value = withContext(Dispatchers.IO) {
            g?.loadModel(name)?.parts?.firstOrNull { it.tex != null }?.tex?.let { texToBitmap(it) }
        }
    }
    val b = bmp
    Box(Modifier.size(thumb).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (b != null) Image(b, null, Modifier.fillMaxSize())
        else Icon(Icons.Default.List, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

fun texToBitmap(t: TexData): androidx.compose.ui.graphics.ImageBitmap? {
    val n = t.w * t.h
    if (t.w <= 0 || t.h <= 0 || t.rgba.size < n * 4) return null
    val px = IntArray(n)
    for (i in 0 until n) {
        val o = i * 4
        val r = t.rgba[o].toInt() and 0xff
        val gg = t.rgba[o + 1].toInt() and 0xff
        val b = t.rgba[o + 2].toInt() and 0xff
        val a = t.rgba[o + 3].toInt() and 0xff
        px[i] = (a shl 24) or (r shl 16) or (gg shl 8) or b
    }
    return android.graphics.Bitmap.createBitmap(px, t.w, t.h, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/** Pad arah berbentuk D-pad: empat tombol bulat dengan segitiga dan titik di tengah. Tahan untuk berulang. */
@Composable
fun DPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(136.dp)) {
        DirBtn(0, Modifier.align(Alignment.TopCenter), onUp)
        DirBtn(3, Modifier.align(Alignment.BottomCenter), onDown)
        DirBtn(1, Modifier.align(Alignment.CenterStart), onLeft)
        DirBtn(2, Modifier.align(Alignment.CenterEnd), onRight)
        Box(Modifier.align(Alignment.Center).size(8.dp).clip(CircleShape).background(cs.onSurface))
    }
}

/** dir: 0 = atas, 1 = kiri, 2 = kanan, 3 = bawah. */
@Composable
fun DirBtn(dir: Int, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val curClick by rememberUpdatedState(onClick)
    Box(
        modifier.size(44.dp).clip(CircleShape).background(cs.surface.copy(alpha = 0.9f))
            .border(2.dp, cs.onSurface, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    curClick()
                    val job = scope.launch {
                        delay(300)
                        while (true) { curClick(); delay(60) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(18.dp)) {
            val path = Path().apply {
                when (dir) {
                    0 -> { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    3 -> { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }
                    1 -> { moveTo(0f, size.height / 2); lineTo(size.width, 0f); lineTo(size.width, size.height); close() }
                    else -> { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }
                }
            }
            drawPath(path, cs.onSurface)
        }
    }
}

/** Gizmo sumbu ala Blender: seret untuk orbit, ketuk bola X/Y/Z untuk melihat dari sumbu itu. */
@Composable
fun OrbitGizmo(gl: GlScene, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(100); tick++ } }
    val dotRadius = with(LocalDensity.current) { 7.dp.toPx() }
    val rPx = with(LocalDensity.current) { 30.dp.toPx() }
    fun ends(c: Offset): Array<Offset> {
        val cy = cos(gl.yaw); val sy = sin(gl.yaw); val sp = sin(gl.pitch); val cp = cos(gl.pitch)
        // proyeksi sumbu X, Y, Z ke layar
        return arrayOf(
            Offset(c.x + cy * rPx, c.y + sy * sp * rPx),
            Offset(c.x + sy * rPx, c.y - cy * sp * rPx),
            Offset(c.x, c.y - cp * rPx)
        )
    }
    fun setView(axis: Int) {
        when (axis) {
            0 -> { gl.yaw = 0f; gl.pitch = 0.05f }
            1 -> { gl.yaw = (Math.PI / 2).toFloat(); gl.pitch = 0.05f }
            else -> { gl.pitch = 1.5f }
        }
        gl.requestRender()
    }
    Box(modifier.size(76.dp)) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        gl.yaw += drag.x * 0.01f
                        if (!gl.topDown) gl.pitch = (gl.pitch + drag.y * 0.008f).coerceIn(0.05f, 1.5f)
                        gl.requestRender()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val e = ends(c)
                        for (i in e.indices) {
                            if ((p - e[i]).getDistance() < dotRadius * 2.5f) { setView(i); break }
                        }
                    }
                }
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(cs.surface.copy(alpha = 0.85f), rPx + 12f, c)
            val e = ends(c)
            val colors = listOf(Color(0xFFE06060), Color(0xFF6FBF73), Color(0xFF5A9BD5))
            for (i in 0..2) {
                drawLine(colors[i], c, e[i], strokeWidth = 3f)
                drawCircle(colors[i], dotRadius, e[i])
            }
            drawCircle(cs.onSurface, 4f, c)
        }
    }
}

/** Pad arah gaya keyboard: atas di tengah, kiri-bawah-kanan di baris bawah. */
@Composable
fun KeyPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyBtn(0, onUp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyBtn(1, onLeft)
            KeyBtn(3, onDown)
            KeyBtn(2, onRight)
        }
    }
}

/** Satu tombol keyboard: kotak gelap dengan bayangan dan panah putih. dir: 0 atas, 1 kiri, 2 kanan, 3 bawah. Tahan untuk berulang. */
@Composable
fun KeyBtn(dir: Int, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val curClick by rememberUpdatedState(onClick)
    Box(
        Modifier.size(38.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                curClick()
                val job = scope.launch {
                    delay(300)
                    while (true) { curClick(); delay(60) }
                }
                tryAwaitRelease()
                job.cancel()
            })
        }
    ) {
        Box(Modifier.offset(2.dp, 3.dp).size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0C0E10)))
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E353D))
                .border(1.dp, Color(0xFF474F59), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(18.dp)) {
                val dx = when (dir) { 1 -> -1f; 2 -> 1f; else -> 0f }
                val dy = when (dir) { 0 -> -1f; 3 -> 1f; else -> 0f }
                val cx = size.width / 2f; val cy = size.height / 2f
                val half = size.width * 0.42f
                val tip = Offset(cx + dx * half, cy + dy * half)
                val back = Offset(cx - dx * half, cy - dy * half)
                val white = Color(0xFFF2F4F7)
                drawLine(white, back, tip, strokeWidth = 3.5f, cap = StrokeCap.Round)
                val hl = size.width * 0.24f
                val px = -dy; val py = dx
                drawLine(white, tip, Offset(tip.x - dx * hl + px * hl, tip.y - dy * hl + py * hl), strokeWidth = 3.5f, cap = StrokeCap.Round)
                drawLine(white, tip, Offset(tip.x - dx * hl - px * hl, tip.y - dy * hl - py * hl), strokeWidth = 3.5f, cap = StrokeCap.Round)
            }
        }
    }
}
