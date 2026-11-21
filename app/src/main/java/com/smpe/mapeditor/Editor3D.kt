@file:OptIn(ExperimentalMaterial3Api::class)

package com.smpe.mapeditor

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
            SelCard(vm, gl, selObj, Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 46.dp))
        }
        if (showPanel) {
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 58.dp, bottom = 8.dp)
                    .width(panelWidth)
                    .heightIn(max = containerHeight - 76.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("INSPECTOR", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    TextButton(onClick = { panelExpanded = !panelExpanded }) {
                        Text(if (panelExpanded) "Minimize" else "Buka")
                    }
                    TextButton(onClick = { showPanel = false }) { Text("Tutup") }
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
                        gl.yaw -= pan.x * 0.008f
                        if (!gl.topDown) gl.pitch = (gl.pitch + pan.y * 0.008f).coerceIn(0.05f, 1.5f)
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
                                if (moved) {
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
                    if (!moved) {
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
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
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
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
    PanelTabs(tab) { tab = it }
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (tab == 0) {
        SectionCard("OBJEK TERPILIH") {
            if (o == null) {
                Text("Ketuk objek di tampilan 3D untuk memilihnya.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(modelColor(o.model), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(o.model, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("ID ${o.id}", style = MaterialTheme.typography.labelMedium)
                }
                AxisRow(o.x, o.y, o.z)
                Text("Seret panah merah (X), hijau (Y), biru (Z) di layar 3D untuk menggeser objek.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val sobj = vm.extra.getOrNull(vm.selStream)
        if (sobj != null) {
            SectionCard("OBJEK STREAM (DILIHAT SAJA)") {
                Text(sobj.model + "  (ID " + sobj.id + ")", fontWeight = FontWeight.Bold)
                AxisRow(sobj.x, sobj.y, sobj.z)
                Button(onClick = { vm.adoptStream(); gl.requestRender() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Salin ke IPL agar bisa diedit")
                }
                Text("Objek aslinya tetap ada di stream, jadi hasilnya objek tambahan.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (vm.selSet.size > 1) GroupCard(vm, gl, step)
        if (o != null) {
            SectionCard("GERAKKAN") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in listOf(0.1f, 1f, 5f, 20f)) FilterChip(selected = step == s, onClick = { step = s }, label = { Text(s.toString()) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Snap seret:", style = MaterialTheme.typography.labelMedium)
                    for (s in listOf(0f, 0.5f, 1f, 5f)) FilterChip(selected = vm.snap == s, onClick = { vm.snap = s }, label = { Text(if (s == 0f) "Off" else s.toString()) })
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
            SectionCard("EDIT NILAI") {
                val key = listOf(i, o.x, o.y, o.z, o.model, o.id)
                var fm by remember(key) { mutableStateOf(o.model) }
                var fid by remember(key) { mutableStateOf(o.id.toString()) }
                var fx by remember(key) { mutableStateOf(String.format(Locale.US, "%.3f", o.x)) }
                var fy by remember(key) { mutableStateOf(String.format(Locale.US, "%.3f", o.y)) }
                var fz by remember(key) { mutableStateOf(String.format(Locale.US, "%.3f", o.z)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Model", fm, Modifier.weight(2f), num = false) { fm = it }
                    Field("ID", fid, Modifier.weight(1f)) { fid = it }
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
                }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text("Terapkan") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Nudge("Ke tengah", Modifier.weight(1f)) { vm.update(i, o.copy(x = gl.tx, y = gl.ty, z = gl.tz)) }
                Nudge("Salin", Modifier.weight(1f)) { vm.duplicate(i) }
                Nudge(if (o.locked) "Buka kunci" else "Kunci", Modifier.weight(1f)) { vm.setLocked(i, !o.locked); gl.requestRender() }
                Nudge("Hapus", Modifier.weight(1f)) { vm.remove(i) }
            }
        }
        }
        if (tab == 1) {
        SectionCard("IPL STREAM (DETAIL PETA)") {
            Text(vm.streamNote.ifEmpty { "IPL teks hanya memuat sebagian objek (banyak LOD). Detail bangunan ada di file stream biner dalam gta3.img. Hanya untuk dilihat, tidak ikut disimpan." },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Nudge("Stream ini", Modifier.weight(1f)) { loadStream(false) }
                Nudge("Semua (berat)", Modifier.weight(1f)) { loadStream(true) }
                Nudge("Kosongkan", Modifier.weight(1f)) { vm.extra = emptyList(); vm.streamNote = ""; gl.requestRender() }
            }
        }
        }
        if (tab == 2) {
        SectionCard("ADEGAN") {
            Button(onClick = { vm.picker = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text("Tambah objek") }
            ImportModelCard(vm, gl)
            ReplaceModelCard(vm)
            ExportCard(vm)
            Text("Jarak pandang: ${viewDist.toInt()} m", style = MaterialTheme.typography.labelMedium)
            Slider(value = viewDist, onValueChange = { viewDist = it; gl.viewDist = it; gl.requestRender() }, valueRange = 150f..2500f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Balik tekstur vertikal", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = flipV, onCheckedChange = { flipV = it; gl.flipV = it; gl.requestRender() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sembunyikan model LOD", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
            .background(cs.surfaceVariant, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf("Objek", "Stream", "Adegan").forEachIndexed { i, label ->
            val on = tab == i
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (on) cs.primary else Color.Transparent)
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
    Column(modifier.background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(String.format(Locale.US, "%.2f", v), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

/** Kartu kecil objek terpilih di bawah viewport: Salin, Hapus, Tutup. */
@Composable
fun SelCard(vm: EditorVM, gl: GlScene, o: Obj, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = cs.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, cs.primary)) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(max = 240.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.model, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Text("${o.id}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = cs.primary)
                }
                Text(String.format(Locale.US, "%.1f · %.1f · %.1f", o.x, o.y, o.z), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            TbBtn(UiIcons.Copy, "Salin") { vm.duplicate(vm.sel); gl.requestRender() }
            TbBtn(Icons.Default.Delete, "Hapus", tint = cs.error) { vm.remove(vm.sel); gl.requestRender() }
            TbBtn(Icons.Default.Close, "Tutup") { vm.clearSel(); gl.requestRender() }
        }
    }
}

@Composable
fun ModelPicker(vm: EditorVM) {
    val g = vm.game
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.picker = false },
        confirmButton = { TextButton(onClick = { vm.picker = false }) { Text("Batal") } },
        title = { Text("Tambah objek") },
        text = {
            Column {
                OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Cari nama model atau ID") }, modifier = Modifier.fillMaxWidth())
                val key = q.trim().lowercase()
                val res = if (g == null || key.isEmpty()) emptyList() else g.allModels().filter { it.first.contains(key) || it.second.toString() == key }.take(150)
                if (key.isEmpty()) Text(
                    if (g != null && !g.idsKnown) "File .ide tidak ditemukan: daftar dari nama model, ID = 0. Ubah ID lewat Edit nilai." else "Ketik nama model, misalnya lamppost.",
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(res) { d ->
                        Text("${d.first}   (${d.second})", Modifier.fillMaxWidth().clickable {
                            val s = vm.gl
                            vm.sel = vm.add(d.first, d.second, s?.tx ?: 0f, s?.ty ?: 0f, s?.tz ?: 10f)
                            vm.picker = false
                        }.padding(vertical = 12.dp))
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
        confirmButton = { TextButton(onClick = { clip.setText(AnnotatedString(text)) }) { Text("Salin") } },
        dismissButton = { TextButton(onClick = { vm.diag = null }) { Text("Tutup") } },
        title = { Text("Diagnosa file game") },
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
            Text("Tampilkan model GTA asli", fontWeight = FontWeight.Bold)
            Text("Pilih folder game (berisi gta3.img, Maps, dan texdb). Mendukung GTA SA mobile (tekstur dari texdb) maupun PC. Di Android 11+ folder Android/data tidak bisa dipilih, salin dulu ke Download/GTASA.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.gameStatus.isNotEmpty()) Text(vm.gameStatus, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick, shape = RoundedCornerShape(14.dp)) { Text("Pilih folder GTA SA") }
                OutlinedButton(onClick = { vm.diag = GameData.lastReport.ifEmpty { "Belum ada laporan. Pilih folder GTA SA dulu." } }, shape = RoundedCornerShape(14.dp)) { Text("Diagnosa") }
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
        Text("Impor model baru (DFF + TXD)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { pickDff.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(dffUri?.let { "DFF: " + fileNameOf(ctx, it) } ?: "Pilih file .dff")
        }
        OutlinedButton(onClick = { pickTxd.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(txdUri?.let { "TXD: " + fileNameOf(ctx, it) } ?: "Pilih file .txd (opsional)")
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) },
            label = { Text("Nama model") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val du = dffUri
                val g = vm.game
                when {
                    du == null -> msg = "Pilih file DFF dulu."
                    name.isEmpty() -> msg = "Isi nama model."
                    g == null -> msg = "Pilih folder GTA SA dulu."
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
        ) { Text("Impor & tempatkan") }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GroupCard(vm: EditorVM, gl: GlScene, step: Float) {
    SectionCard("${vm.selSet.size} OBJEK DIPILIH") {
        Text("Ketuk objek untuk menambah atau mengeluarkan dari pilihan. Seret salah satu objek yang dipilih untuk menggeser semuanya.",
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
            Nudge("Kunci semua", Modifier.weight(1f)) { vm.setLockedGroup(true); gl.requestRender() }
            Nudge("Buka semua", Modifier.weight(1f)) { vm.setLockedGroup(false); gl.requestRender() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Nudge("Batal pilih", Modifier.weight(1f)) { vm.clearSel(); gl.requestRender() }
            Nudge("Hapus semua", Modifier.weight(1f)) { vm.removeGroup(); gl.requestRender() }
        }
    }
}

@Composable
fun ReplaceModelCard(vm: EditorVM) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ganti model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Dari", from, Modifier.weight(1f)) { from = it }
            Field("Menjadi", to, Modifier.weight(1f)) { to = it }
        }
        Button(
            onClick = {
                val n = vm.replaceModel(from, to)
                msg = if (n > 0) "$n objek diganti." else "Tidak ada objek yang cocok (atau terkunci)."
            },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
        ) { Text("Ganti semua") }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ExportCard(vm: EditorVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Export paket mod", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Membuat IDE, IPL, dan DFF/TXD model impor dalam satu folder. Lalu disalin ke folder game dengan Termux.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                val g = vm.game
                if (g == null) { msg = "Pilih folder GTA SA dulu."; return@Button }
                val snap = vm.entries.toList()
                msg = "Mengekspor..."
                scope.launch(Dispatchers.IO) {
                    val r = try { ModExport.export(ctx, g, snap) } catch (e: Exception) { "Gagal: ${e.message}" }
                    withContext(Dispatchers.Main) { msg = r }
                }
            },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
        ) { Text("Export paket mod") }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
