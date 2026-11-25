package com.smpe.mapeditor

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import android.content.Intent
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.foundation.text.selection.SelectionContainer
import java.io.File
import android.content.Context
import androidx.compose.material.icons.filled.MoreVert
import kotlinx.coroutines.Dispatchers
import java.util.Locale

// ---------- File terakhir ----------
data class Recent(val uri: String, val name: String, val t: Long)

fun loadRecents(prefs: android.content.SharedPreferences): List<Recent> =
    (prefs.getString("recent", "") ?: "").lines().mapNotNull { l ->
        val p = l.split("\t")
        if (p.size >= 3 && p[0].isNotBlank()) Recent(p[0], p[1], p[2].toLongOrNull() ?: 0L) else null
    }

// ---------- Model ----------
sealed interface Entry
data class Raw(val t: String) : Entry
data class Obj(
    val id: Int, val model: String, val interior: Int,
    val x: Float, val y: Float, val z: Float,
    val rx: Float, val ry: Float, val rz: Float, val rw: Float,
    val lod: Int?, val src: String? = null,
    val locked: Boolean = false
) : Entry {
    private fun f(v: Float) = String.format(Locale.US, "%.6f", v)
    fun line(): String = src ?: buildString {
        append("$id, $model, $interior, ${f(x)}, ${f(y)}, ${f(z)}, ${f(rx)}, ${f(ry)}, ${f(rz)}, ${f(rw)}")
        if (lod != null) append(", $lod")
    }
}

fun parseObj(l: String): Obj? = try {
    val p = l.split(",").map { it.trim() }
    if (p.size < 10) null else Obj(
        p[0].toInt(), p[1], p[2].toInt(),
        p[3].toFloat(), p[4].toFloat(), p[5].toFloat(),
        p[6].toFloat(), p[7].toFloat(), p[8].toFloat(), p[9].toFloat(),
        p.getOrNull(10)?.toIntOrNull(), l
    )
} catch (e: Exception) { null }

fun parseIpl(text: String): List<Entry> {
    var inInst = false
    return text.lines().map { l ->
        val t = l.trim()
        when {
            t.equals("inst", true) -> { inInst = true; Raw(l) }
            t.equals("end", true) -> { inInst = false; Raw(l) }
            inInst && t.isNotEmpty() && !t.startsWith("#") -> parseObj(l) ?: Raw(l)
            else -> Raw(l)
        }
    }
}

class EditorVM : ViewModel() {
    val entries = mutableStateListOf<Entry>()
    var fileName by mutableStateOf("Belum ada file")
    var uri by mutableStateOf<Uri?>(null)
    var sel by mutableIntStateOf(-1)
    var dirty by mutableStateOf(false)
    var game by mutableStateOf<GameData?>(null)
    var gameStatus by mutableStateOf("")
    var picker by mutableStateOf(false)
    var gl: GlScene? = null
    var diag by mutableStateOf<String?>(null)
    var selStream by mutableIntStateOf(-1)

    /** Pilihan banyak objek (mode Pilih banyak). Berisi indeks entries. */
    val selSet = mutableStateListOf<Int>()
    var multi by mutableStateOf(false)
    var topDown by mutableStateOf(false)
    var gridOn by mutableStateOf(true)
    /** 0 = Jelajah, 1 = Objek (kamera berpusat di objek, arah menggeser objek), 2 = Kamera (kamera bebas). */
    var camMode by mutableIntStateOf(0)
    /** Tombol Edit di kartu objek: jika aktif, objek yang dipilih bisa digeser dengan jari. */
    var dragEdit by mutableStateOf(false)
    /** Posisi kamera sebelum masuk mode Objek/Kamera: tx, ty, tz, dist, yaw, pitch. */
    var savedCam by mutableStateOf<FloatArray?>(null)
    /** Naik setiap ada perubahan; dipakai untuk autosave draf. */
    var rev by mutableIntStateOf(0)
    /** Label aksi terakhir yang bisa dibatalkan, mis. "Geser X". */
    var undoLabel by mutableStateOf("")
    private val undoLabels = ArrayList<String>()
    var snap by mutableFloatStateOf(0f)
    var extra by mutableStateOf<List<Obj>>(emptyList())
    var streamNote by mutableStateOf("")
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    private val undoS = ArrayList<List<Entry>>()
    private val redoS = ArrayList<List<Entry>>()
    private fun snapshot(label: String = "Ubah") {
        undoS.add(entries.toList()); if (undoS.size > 80) undoS.removeAt(0)
        undoLabels.add(label); if (undoLabels.size > 80) undoLabels.removeAt(0)
        undoLabel = label; rev++
        redoS.clear(); canUndo = true; canRedo = false
    }
    fun undo() {
        if (undoS.isEmpty()) return
        redoS.add(entries.toList())
        val s = undoS.removeAt(undoS.size - 1)
        undoLabels.removeLastOrNull()
        selSet.clear(); entries.clear(); entries.addAll(s); sel = -1; dirty = true
        canUndo = undoS.isNotEmpty(); canRedo = true
        undoLabel = undoLabels.lastOrNull() ?: ""
    }
    fun redo() {
        if (redoS.isEmpty()) return
        undoS.add(entries.toList())
        val s = redoS.removeAt(redoS.size - 1)
        selSet.clear(); entries.clear(); entries.addAll(s); sel = -1; dirty = true
        canRedo = redoS.isNotEmpty(); canUndo = true
    }
    val count: Int get() = entries.count { it is Obj }

    /** IPL kosong baru: satu blok inst/end, siap diisi objek. */
    fun newFile() {
        selSet.clear(); entries.clear(); entries.add(Raw("inst")); entries.add(Raw("end"))
        fileName = "baru.ipl"; uri = null; sel = -1; dirty = true
        undoS.clear(); redoS.clear(); canUndo = false; canRedo = false
        extra = emptyList(); streamNote = ""; selStream = -1
    }

    fun load(text: String, name: String, u: Uri?): Boolean {
        if (text.startsWith("bnry")) return false
        selSet.clear(); entries.clear(); entries.addAll(parseIpl(text))
        fileName = name; uri = u; sel = -1; dirty = false
        undoS.clear(); redoS.clear(); canUndo = false; canRedo = false
        extra = emptyList(); streamNote = ""; selStream = -1
        return true
    }
    fun text(): String = entries.joinToString("\n") { if (it is Obj) it.line() else (it as Raw).t }

    /** File IDE pendamping: satu baris per ID model unik yang dipakai di IPL ini. */
    fun ideText(): String {
        val seen = LinkedHashMap<Int, String>()
        for (e in entries) if (e is Obj && !seen.containsKey(e.id)) seen[e.id] = e.model.lowercase()
        // Model impor ikut masuk IDE meski belum dipakai di IPL.
        game?.customIds?.forEach { (name, id) -> if (!seen.containsKey(id)) seen[id] = name }
        return buildString {
            append("objs\n")
            for ((id, model) in seen) append("$id, $model, $model, 1, 300\n")
            append("end\n")
        }
    }
    fun update(i: Int, o: Obj) {
        snapshot("Ubah nilai")
        val old = entries[i] as? Obj
        // Objek terkunci: posisi dan rotasi tidak ikut berubah.
        entries[i] = if (old != null && old.locked)
            o.copy(x = old.x, y = old.y, z = old.z, rx = old.rx, ry = old.ry, rz = old.rz, rw = old.rw, src = null, locked = true)
        else o.copy(src = null)
        dirty = true
    }

    fun setLocked(i: Int, v: Boolean) {
        val o = entries.getOrNull(i) as? Obj ?: return
        snapshot(if (v) "Kunci objek" else "Buka kunci")
        entries[i] = o.copy(locked = v, src = null); dirty = true
    }

    fun setLockedGroup(v: Boolean) {
        val g = groupIdx(); if (g.isEmpty()) return
        snapshot(if (v) "Kunci grup" else "Buka kunci grup")
        for (i in g) { val o = entries.getOrNull(i) as? Obj ?: continue; entries[i] = o.copy(locked = v, src = null) }
        dirty = true
    }

    /** Ganti semua objek bernama [from] menjadi [to]. Objek terkunci dilewati. Mengembalikan jumlah yang diganti. */
    fun replaceModel(from: String, to: String): Int {
        val f = from.trim().lowercase(); val t = to.trim()
        if (f.isEmpty() || t.isEmpty()) return 0
        val idx = entries.indices.filter { val o = entries[it] as? Obj; o != null && !o.locked && o.model.lowercase() == f }
        if (idx.isEmpty()) return 0
        snapshot("Ganti model")
        for (i in idx) { val o = entries[i] as Obj; entries[i] = o.copy(model = t, src = null) }
        dirty = true
        return idx.size
    }

    /** Pulihkan draf yang belum disimpan. Hasilnya belum punya lokasi file, jadi Simpan akan menanyakan lokasi. */
    fun loadDraft(text: String, name: String) {
        selSet.clear(); entries.clear(); entries.addAll(parseIpl(text))
        fileName = name; uri = null; sel = -1; dirty = true
        undoS.clear(); redoS.clear(); undoLabels.clear()
        canUndo = false; canRedo = false; undoLabel = ""
    }
    fun remove(i: Int) { snapshot("Hapus objek"); entries.removeAt(i); sel = -1; selSet.clear(); dirty = true }

    fun clearSel() { selSet.clear(); sel = -1; dragEdit = false }

    /** Ketuk objek: mode biasa memilih satu objek, mode Pilih banyak menambah/mengeluarkan dari pilihan. */
    fun tapSelect(i: Int) {
        if (!multi) { selSet.clear(); sel = i; dragEdit = false; return }
        if (selSet.contains(i)) selSet.remove(i) else selSet.add(i)
        sel = if (selSet.size == 1) selSet[0] else -1
    }

    /** Daftar objek yang sedang dipilih (banyak, atau satu dari sel). */
    fun groupIdx(): List<Int> = when {
        selSet.isNotEmpty() -> selSet.toList()
        sel >= 0 -> listOf(sel)
        else -> emptyList()
    }

    fun shiftGroup(axis: Int, delta: Float) {
        val g = groupIdx(); if (g.isEmpty()) return
        beginEdit("Geser grup")
        for (i in g) {
            val o = entries.getOrNull(i) as? Obj ?: continue
            val v = if (axis == 0) o.x else if (axis == 1) o.y else o.z
            setAxis(i, axis, v + delta)
        }
    }

    fun rotateGroup(deg: Float) {
        val g = groupIdx(); if (g.isEmpty()) return
        beginEdit("Putar grup")
        for (i in g) {
            val o = entries.getOrNull(i) as? Obj ?: continue
            if (o.locked) continue
            entries[i] = rotZ(o, deg).copy(src = null)
        }
        dirty = true
    }

    /** Geser objek yang dipilih sejauh (dx, dy, dz) dalam koordinat dunia. */
    fun shiftSel(dx: Float, dy: Float, dz: Float) {
        val i = sel
        val o = entries.getOrNull(i) as? Obj ?: return
        if (o.locked) return
        snapshot("Geser")
        entries[i] = o.copy(x = o.x + dx, y = o.y + dy, z = o.z + dz, src = null)
        dirty = true
        rev++
    }

    /** Putar objek yang dipilih saat ini sebesar deg derajat (sumbu Z). */
    fun rotateSel(deg: Float) {
        val i = sel
        val o = entries.getOrNull(i) as? Obj ?: return
        if (o.locked) return
        snapshot("Putar")
        entries[i] = rotZ(o, deg).copy(src = null)
        dirty = true
        rev++
    }

    fun removeGroup() {
        val g = groupIdx(); if (g.isEmpty()) return
        beginEdit("Hapus grup")
        for (i in g.sortedDescending()) entries.removeAt(i)
        selSet.clear(); sel = -1; dirty = true
    }
    fun beginEdit(label: String = "Ubah") { snapshot(label) }
    fun setAxis(i: Int, axis: Int, v: Float) {
        val o = entries.getOrNull(i) as? Obj ?: return
        if (o.locked) return
        rev++
        entries[i] = when (axis) { 0 -> o.copy(x = v, src = null); 1 -> o.copy(y = v, src = null); else -> o.copy(z = v, src = null) }
        dirty = true
    }
    /** Copy the selected (view-only) stream object into the editable IPL. */
    fun adoptStream() {
        val o = extra.getOrNull(selStream) ?: return
        val n = add(o.model, o.id, o.x, o.y, o.z)
        val e = entries[n] as Obj
        entries[n] = e.copy(interior = o.interior, rx = o.rx, ry = o.ry, rz = o.rz, rw = o.rw, lod = -1, src = null)
        selStream = -1; sel = n
    }
    fun duplicate(i: Int) {
        val o = entries.getOrNull(i) as? Obj ?: return
        snapshot(); entries.add(i + 1, o.copy(x = o.x + 3f, src = null)); dirty = true; sel = i + 1
    }
    fun add(model: String = "new_object", id: Int = 1337, x: Float = 0f, y: Float = 0f, z: Float = 10f): Int {
        val o = Obj(id, model, 0, x, y, z, 0f, 0f, 0f, 1f, -1)
        snapshot()
        val last = entries.indexOfLast { it is Obj }
        dirty = true
        return if (last >= 0) { entries.add(last + 1, o); last + 1 }
        else {
            val end = entries.indexOfLast { it is Raw && it.t.trim().equals("end", true) }
            if (end >= 0) { entries.add(end, o); end }
            else { entries.add(Raw("inst")); entries.add(o); entries.add(Raw("end")); entries.size - 2 }
        }
    }
}

// ---------- Helpers ----------
fun modelColor(model: String): Color =
    Color.hsv(((model.lowercase().hashCode() and 0x7fffffff) % 360).toFloat(), 0.62f, 0.95f)

fun shade(c: Color, k: Float) =
    Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), c.alpha)

/** returns [centerX, centerY, centerZ, suggestedDistance] */
fun sceneStats(entries: List<Entry>): FloatArray {
    val o = entries.filterIsInstance<Obj>()
    if (o.isEmpty()) return floatArrayOf(0f, 0f, 0f, 300f)
    val ext = max(o.maxOf { it.x } - o.minOf { it.x }, o.maxOf { it.y } - o.minOf { it.y })
    return floatArrayOf(o.map { it.x }.average().toFloat(), o.map { it.y }.average().toFloat(),
        o.map { it.z }.average().toFloat(), (ext * 0.9f).coerceIn(80f, 6000f))
}

/** Orbit camera: z is up, yaw 0 looks north. */
class Cam(tx: Float, ty: Float, tz: Float, yaw: Float, pitch: Float, dist: Float, val w: Float, val h: Float) {
    private val cp = cos(pitch); private val sp = sin(pitch)
    private val cyw = cos(yaw); private val syw = sin(yaw)
    val camX = tx + dist * cp * syw
    val camY = ty - dist * cp * cyw
    val camZ = tz + dist * sp
    private val fx = -cp * syw; private val fy = cp * cyw; private val fz = -sp
    private val rx = cyw; private val ry = syw
    private val ux = ry * fz; private val uy = -rx * fz; private val uz = rx * fy - ry * fx
    val focal = minOf(w, h) * 1.1f
    fun zc(x: Float, y: Float, z: Float) = (x - camX) * fx + (y - camY) * fy + (z - camZ) * fz
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val vx = x - camX; val vy = y - camY; val vz = z - camZ
        val d = vx * fx + vy * fy + vz * fz
        if (d < 1f) return false
        out[0] = w / 2f + (vx * rx + vy * ry) / d * focal
        out[1] = h / 2f - (vx * ux + vy * uy + vz * uz) / d * focal
        out[2] = d
        return true
    }
}

fun DrawScope.line3(cam: Cam, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, c: Color) {
    val a = cam.zc(x1, y1, z1); val b = cam.zc(x2, y2, z2)
    if (a < 1f && b < 1f) return
    var ax = x1; var ay = y1; var az = z1; var bx = x2; var by = y2; var bz = z2
    if (a < 1f) { val t = (1f - a) / (b - a); ax = x1 + (x2 - x1) * t; ay = y1 + (y2 - y1) * t; az = z1 + (z2 - z1) * t }
    else if (b < 1f) { val t = (1f - b) / (a - b); bx = x2 + (x1 - x2) * t; by = y2 + (y1 - y2) * t; bz = z2 + (z1 - z2) * t }
    val p = FloatArray(3); val q = FloatArray(3)
    if (cam.project(ax, ay, az, p) && cam.project(bx, by, bz, q))
        drawLine(c, Offset(p[0], p[1]), Offset(q[0], q[1]), 1f)
}

fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float) {
    // drawText throws when the text origin is outside the canvas, so skip those
    if (x.isNaN() || y.isNaN() || x < 0f || y < 0f || x > size.width - 30f || y > size.height - 20f) return
    drawText(tm, text, Offset(x, y), TextStyle(color = Color.White, fontSize = 10.sp, background = Color(0xE6121714)))
}

// ---------- Theme ----------
// Deep Emerald: latar hijau-hitam, aksen zamrud gelap, teks putih kehijauan lembut.
private val Scheme = darkColorScheme(
    primary = Color(0xFF4D9BFF), onPrimary = Color(0xFF06121F),
    primaryContainer = Color(0xFF173A66), onPrimaryContainer = Color(0xFFE6F0FF),
    secondary = Color(0xFF8FB4E8), onSecondary = Color(0xFF06121F),
    secondaryContainer = Color(0xFF1A3350), onSecondaryContainer = Color(0xFFE6F0FF),
    tertiary = Color(0xFF7FD3F2),
    background = Color(0xFF08111E), onBackground = Color(0xFFE6F0FF),
    surface = Color(0xFF0E1A2D), onSurface = Color(0xFFE6F0FF),
    surfaceVariant = Color(0xB815243C), onSurfaceVariant = Color(0xFF8FA6C4),
    surfaceContainerLowest = Color(0xFF0A1424), surfaceContainerLow = Color(0xFF0E1A2D),
    surfaceContainer = Color(0xFF122139), surfaceContainerHigh = Color(0xFF15243C),
    surfaceContainerHighest = Color(0xFF1B2D49),
    outline = Color(0xFF2C4A72), outlineVariant = Color(0xFF2C4A72), error = Color(0xFFFF7A8A)
)

val RussoOne = FontFamily(Font(R.font.russo_one, FontWeight.Normal))

val DmSans = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_semibold, FontWeight.SemiBold),
    Font(R.font.dmsans_bold, FontWeight.Bold),
    Font(R.font.dmsans_extrabold, FontWeight.ExtraBold),
    Font(R.font.dmsans_extrabold, FontWeight.Black)
)

private fun appTypography(): Typography {
    val b = Typography()
    fun TextStyle.f() = copy(fontFamily = DmSans)
    return Typography(
        displayLarge = b.displayLarge.f(), displayMedium = b.displayMedium.f(), displaySmall = b.displaySmall.f(),
        headlineLarge = b.headlineLarge.f(), headlineMedium = b.headlineMedium.f(), headlineSmall = b.headlineSmall.f(),
        titleLarge = b.titleLarge.f(), titleMedium = b.titleMedium.f(), titleSmall = b.titleSmall.f(),
        bodyLarge = b.bodyLarge.f(), bodyMedium = b.bodyMedium.f(), bodySmall = b.bodySmall.f(),
        labelLarge = b.labelLarge.f(), labelMedium = b.labelMedium.f(), labelSmall = b.labelSmall.f()
    )
}

@Composable
fun SmpeTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, typography = appTypography(), content = content)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashFile = File(filesDir, "crash.txt")
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crashFile.writeText(e.stackTraceToString().take(3500)) } catch (_: Exception) {}
            old?.uncaughtException(t, e)
        }
        setContent {
            SmpeTheme {
                var crash by remember { mutableStateOf(if (crashFile.exists()) crashFile.readText() else null) }
                App()
                crash?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { crashFile.delete(); crash = null },
                        confirmButton = { TextButton(onClick = { crashFile.delete(); crash = null }) { Text("Tutup") } },
                        title = { Text("Laporan crash terakhir") },
                        text = {
                            SelectionContainer {
                                Text(msg, fontSize = 10.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
                            }
                        }
                    )
                }
            }
        }
    }
}

fun nameOf(ctx: android.content.Context, u: Uri): String {
    ctx.contentResolver.query(u, null, null, null, null)?.use {
        if (it.moveToFirst()) {
            val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0) return it.getString(i)
        }
    }
    return "file.ipl"
}

// ---------- App ----------
@Composable
fun App(vm: EditorVM = viewModel()) {
    val ctx = LocalContext.current
    remember { I18n.load(ctx) }
    var updateRel by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    val homeScope = rememberCoroutineScope()
    fun manualCheck() {
        homeScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { UpdateChecker.fetchLatest() }.getOrNull() }
            when {
                r == null -> Toast.makeText(ctx, I18n.t("Gagal cek update. Periksa koneksi."), Toast.LENGTH_SHORT).show()
                UpdateChecker.isNewer(r.version, UpdateChecker.currentVersion(ctx)) -> updateRel = r
                else -> Toast.makeText(ctx, I18n.t("Anda sudah memakai versi terbaru."), Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { runCatching { UpdateChecker.fetchLatest() }.getOrNull() }
        val skip = ctx.getSharedPreferences("smpe", Context.MODE_PRIVATE).getString("skip_update", "")
        if (r != null && UpdateChecker.isNewer(r.version, UpdateChecker.currentVersion(ctx)) && r.version != skip) updateRel = r
    }
    val snack = remember { SnackbarHostState() }
    // Menu awal: potret. Setelah file IPL dibuka (editor 3D): landscape.
    val activity = ctx as? android.app.Activity
    val editorOpen = vm.entries.isNotEmpty()

    // Draf: disimpan otomatis 1,5 detik setelah perubahan terakhir, dibuang saat file disimpan.
    val draftFile = remember { File(ctx.filesDir, "draft.ipl") }
    val draftMeta = remember { File(ctx.filesDir, "draft_name.txt") }
    var hasDraft by remember { mutableStateOf(draftFile.exists()) }
    LaunchedEffect(vm.rev) {
        if (vm.entries.isNotEmpty() && vm.dirty && vm.rev > 0) {
            delay(1500)
            val txt = vm.text(); val nm = vm.fileName
            withContext(Dispatchers.IO) {
                try { draftFile.writeText(txt); draftMeta.writeText(nm) } catch (_: Exception) { }
            }
            hasDraft = true
        }
    }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var browsing by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = vm.entries.isNotEmpty() && vm.dirty) { showExitConfirm = true }
    val prefs = remember { ctx.getSharedPreferences("smpe", Context.MODE_PRIVATE) }
    fun say(m: String) { scope.launch { snack.showSnackbar(m) } }

    val recents = remember { mutableStateListOf<Recent>().also { it.addAll(loadRecents(prefs)) } }
    fun saveRecents() {
        prefs.edit().putString("recent", recents.joinToString("\n") { it.uri + "\t" + it.name.replace('\t', ' ') + "\t" + it.t }).apply()
    }
    fun pushRecent(u: Uri, name: String) {
        recents.removeAll { it.uri == u.toString() }
        recents.add(0, Recent(u.toString(), name, System.currentTimeMillis()))
        while (recents.size > 5) recents.removeAt(recents.size - 1)
        saveRecents()
    }
    fun openUri(u: Uri) {
        try {
            try { ctx.contentResolver.takePersistableUriPermission(u, 3) } catch (_: Exception) {}
            val txt = ctx.contentResolver.openInputStream(u)!!.bufferedReader().readText()
            if (vm.load(txt, nameOf(ctx, u), u)) {
                tab = 1
                pushRecent(u, vm.fileName)
                say("${vm.count} objek dimuat")
            } else say("IPL biner belum didukung. Gunakan IPL teks.")
        } catch (e: Exception) {
            recents.removeAll { it.uri == u.toString() }; saveRecents()
            say("Gagal membuka file: ${e.message}")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) openUri(u)
    }
    fun write(u: Uri) {
        try {
            ctx.contentResolver.openOutputStream(u, "wt")!!.use { it.write(vm.text().toByteArray()) }
            vm.uri = u; vm.dirty = false; say("Tersimpan")
            draftFile.delete(); draftMeta.delete(); hasDraft = false
        } catch (e: Exception) { say("Gagal menyimpan: ${e.message}. Coba Simpan sebagai.") }
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u: Uri? ->
        if (u != null) write(u)
    }
    fun writeIde(u: Uri) {
        try {
            ctx.contentResolver.openOutputStream(u, "wt")!!.use { it.write(vm.ideText().toByteArray()) }
            say("File IDE tersimpan")
        } catch (e: Exception) { say("Gagal menyimpan IDE: ${e.message}") }
    }
    val saveIde = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u: Uri? ->
        if (u != null) writeIde(u)
    }

    fun loadGame(u: Uri) {
        vm.gameStatus = "Memuat data GTA SA..."
        say("Memuat data GTA SA...")
        scope.launch(Dispatchers.IO) {
            try {
                val g = GameData.open(ctx, u)
                CustomStore.restore(ctx, g)
                vm.game = g
                vm.gameStatus = g.summary()
                say("Data GTA SA siap: " + g.summary())
            } catch (e: Exception) {
                vm.gameStatus = "Gagal: ${e.message}"
                say("Gagal memuat data GTA: ${e.message}")
            }
        }
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            try { ctx.contentResolver.takePersistableUriPermission(u, 1) } catch (_: Exception) {}
            prefs.edit().putString("tree", u.toString()).apply()
            loadGame(u)
        }
    }
    LaunchedEffect(Unit) {
        val saved = prefs.getString("tree", null)
        if (saved != null && vm.game == null) loadGame(Uri.parse(saved))
    }

    val headerFn: @Composable (Boolean) -> Unit = { ov ->
            AppHeader(
                hasFile = vm.entries.isNotEmpty(), tab = tab, onTab = { tab = it },
                dirty = vm.dirty,
                onOpen = { open.launch(arrayOf("*/*")) },
                onSave = { val u = vm.uri; if (u != null) write(u) else saveAs.launch(vm.fileName) },
                menu = { close ->
                    if (vm.entries.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Simpan sebagai") }, onClick = { close(); saveAs.launch(vm.fileName) })
                        DropdownMenuItem(text = { Text("Simpan IDE") }, onClick = { close(); saveIde.launch(vm.fileName.substringBeforeLast('.') + ".ide") })
                        DropdownMenuItem(text = { Text("Urungkan") }, enabled = vm.canUndo, onClick = { close(); vm.undo() })
                        DropdownMenuItem(text = { Text("Ulangi") }, enabled = vm.canRedo, onClick = { close(); vm.redo() })
                    }
                    DropdownMenuItem(text = { Text("Diagnosa file game") }, onClick = {
                        close()
                        scope.launch(Dispatchers.IO) {
                            vm.diag = (vm.game?.diagnose() ?: "Build: " + BUILD_TAG + "\n" + GameData.lastReport.ifEmpty { "Folder GTA SA belum dipilih." }) + "\nGL: " + (vm.gl?.glLog?.ifEmpty { "ok" } ?: "belum dibuat")
                        }
                    })
                    DropdownMenuItem(text = { Text("Pilih folder GTA SA") }, onClick = { close(); pickTree.launch(null) })
                },
                overlay = ov
            )
    }
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.map_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x77030B18), Color(0x99071327), Color(0x88030B18))
                )
            )
        )
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (!(tab == 1 && vm.game != null && vm.entries.isNotEmpty())) headerFn(false)
        },
        floatingActionButton = {
            if (vm.entries.isNotEmpty() && !(tab == 1 && vm.game != null)) ExtendedFloatingActionButton(
                onClick = { vm.sel = vm.add() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Objek baru") }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                vm.entries.isEmpty() -> Empty(
                    onOpen = { open.launch(arrayOf("*/*")) },
                    onPickFolder = { pickTree.launch(null) },
                    gameStatus = vm.gameStatus,
                    recents = recents,
                    onRecent = { r -> openUri(Uri.parse(r.uri)) },
                    onClearRecents = { recents.clear(); saveRecents() },
                    onNew = { vm.newFile(); tab = 0 },
                    onBrowse = { browsing = true },
                    onCheckUpdate = { manualCheck() }
                )
                tab == 0 -> ListView(vm)
                tab == 1 -> {
                    val g = vm.game
                    if (g != null) Box(Modifier.fillMaxSize()) {
                        GlEditor(vm, g)
                        Box(Modifier.align(Alignment.TopEnd)) { headerFn(true) }
                    }
                    else Box(Modifier.fillMaxSize()) { View3D(vm); GamePrompt(vm) { pickTree.launch(null) } }
                }
                else -> { /* tab 0 and 1 are handled above */ }
            }
        }
        if (vm.sel in vm.entries.indices && vm.entries[vm.sel] is Obj && !(tab == 1 && vm.game != null)) EditSheet(vm)
        if (hasDraft && vm.entries.isEmpty()) AlertDialog(
            onDismissRequest = { },
            title = { Text("Draf belum disimpan") },
            text = { Text("Ada perubahan dari sesi sebelumnya yang belum disimpan. Pulihkan?") },
            confirmButton = {
                TextButton(onClick = {
                    val t = try { draftFile.readText() } catch (_: Exception) { "" }
                    val n = try { draftMeta.readText() } catch (_: Exception) { "baru.ipl" }
                    if (t.isNotEmpty()) vm.loadDraft(t, n)
                    hasDraft = false
                }) { Text("Pulihkan") }
            },
            dismissButton = {
                TextButton(onClick = { draftFile.delete(); draftMeta.delete(); hasDraft = false }) { Text("Buang") }
            }
        )
        if (browsing) Dialog(onDismissRequest = { browsing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val t = prefs.getString("tree", null)
                if (t == null) Column(Modifier.padding(16.dp)) {
                    Text("Pilih folder GTA SA dulu.")
                    TextButton(onClick = { browsing = false }) { Text("Tutup") }
                }
                else MapBrowser(vm, Uri.parse(t), onOpen = { u -> openUri(u); browsing = false }, onClose = { browsing = false }, onNew = { vm.newFile(); tab = 0; browsing = false })
            }
        }
        if (showExitConfirm) AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Ada perubahan belum disimpan") },
            text = { Text("Keluar sekarang akan membuang perubahan yang belum disimpan. Gunakan Simpan di menu atas dulu jika ingin menyimpannya.") },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; (ctx as? android.app.Activity)?.finish() }) { Text("Keluar") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Batal") } }
        )
        updateRel?.let { UpdateDialog(ctx, it) { updateRel = null } }
        if (vm.picker) ModelPicker(vm)
        vm.diag?.let { DiagDialog(vm, it) }
    }
    }
}

@Composable
fun Empty(
    onOpen: () -> Unit,
    onPickFolder: () -> Unit,
    gameStatus: String,
    recents: List<Recent>,
    onRecent: (Recent) -> Unit,
    onClearRecents: () -> Unit,
    onNew: () -> Unit,
    onBrowse: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var showGuide by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    HomeSidebar(
        onGuide = { showGuide = true },
        onChangelog = { showChangelog = true },
        onCheckUpdate = onCheckUpdate
    )
    Box(
        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 720.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth().border(1.dp, Color(0x7758BFFF), RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xB30B2745))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("SAN EDITOR", fontFamily = RussoOne, fontWeight = FontWeight.Normal, fontSize = 26.sp,
                            color = Color(0xFFE6EBF2), letterSpacing = 1.sp,
                            style = TextStyle(shadow = Shadow(color = Color(0xFF0B0E13), offset = Offset(0f, 4f), blurRadius = 8f)))
                        Text(I18n.t("Editor map GTA San Andreas"), style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A96A6))
                    }
                }
            }
            if (showGuide) GuideDialog { showGuide = false }
            if (showChangelog) ChangelogDialog { showChangelog = false }

            val folderReady = gameStatus.isNotBlank() && !gameStatus.startsWith("Gagal")
            Card(Modifier.fillMaxWidth().border(1.dp, Color(0x664CA9E8), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xB30B2540))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(I18n.t("Langkah"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    StepRow(folderReady, I18n.t("1. Pilih folder game"), I18n.t("Pilih folder files di kartu Folder GTA San Andreas."))
                    StepRow(false, I18n.t("2. Pilih map"), I18n.t("Tekan Pilih map, lalu pilih file .ipl yang mau diedit."))
                    StepRow(false, I18n.t("3. Edit dan simpan"), I18n.t("Pindahkan atau tambah objek, lalu tekan Simpan."))
                }
            }

            if (gameStatus.startsWith("Gagal")) Card(Modifier.fillMaxWidth().border(1.dp, Color(0x664CA9E8), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cs.errorContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(I18n.t("Folder belum benar"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onErrorContainer)
                    Text(I18n.t("Pilih folder files yang berisi data, models, dan texdb."), style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer)
                    OutlinedButton(onClick = onPickFolder) { Text(I18n.t("Pilih ulang")) }
                }
            }

            Card(Modifier.fillMaxWidth().border(1.dp, Color(0x664CA9E8), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xB30B2540))) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, tint = cs.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(I18n.t("Folder GTA San Andreas"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                gameStatus.isBlank() -> I18n.t("Belum dipilih. Tekan Pilih.")
                                gameStatus.startsWith("Gagal") -> I18n.t("Folder belum benar. Pilih folder files.")
                                else -> "Folder game terdeteksi \u2713"
                            },
                            color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    TextButton(onClick = onPickFolder) { Text(if (gameStatus.isBlank()) "Pilih" else I18n.t("Ganti")) }
                }
            }

            Card(Modifier.fillMaxWidth().border(1.dp, Color(0x664CA9E8), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xB30B2540))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(I18n.t("Pilih map"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("Pilih file map (.ipl) dari folder game. Dari sana Anda juga bisa mendaftarkan model dan membuat IPL baru."),
                        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onBrowse,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)) {
                        Text(I18n.t("Pilih map"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onPickFolder) { Text(I18n.t("Ganti folder")) }
                        TextButton(onClick = onNew) { Text(I18n.t("Buat IPL baru")) }
                    }
                }
            }

            if (recents.isEmpty()) Card(Modifier.fillMaxWidth().border(1.dp, Color(0x664CA9E8), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xB30B2540))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.List, null, tint = cs.onSurfaceVariant)
                    Text(I18n.t("Belum ada file dibuka"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("File yang Anda buka akan muncul di sini."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (recents.isNotEmpty()) Card(Modifier.fillMaxWidth().border(1.dp, Color(0x664CA9E8), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xB30B2540))) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(I18n.t("File terakhir"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onClearRecents) { Text(I18n.t("Hapus daftar"), style = MaterialTheme.typography.labelMedium) }
                    }
                    for (r in recents) {
                        Row(Modifier.fillMaxWidth().clickable { onRecent(r) }.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.List, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(r.name, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                android.text.format.DateUtils.getRelativeTimeSpanString(
                                    r.t, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(),
                                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }

            Text("Developed by ReyyXSA", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
    }
    }
    }
}

@Composable
fun AppHeader(
    hasFile: Boolean, tab: Int, onTab: (Int) -> Unit, dirty: Boolean,
    onOpen: () -> Unit, onSave: () -> Unit,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    overlay: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = Color.Transparent) {
        Row(
            Modifier.statusBarsPadding().height(44.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!overlay) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
            }
            if (hasFile) {
                HeaderTab("3D", tab == 1) { onTab(1) }
            } else {
                Text("San Editor", fontWeight = FontWeight.Black, color = cs.primary)
            }
            if (!overlay) Spacer(Modifier.weight(1f))
            val pad = PaddingValues(horizontal = 10.dp)
            if (!overlay) TextButton(onClick = onOpen, contentPadding = pad) { Text("Buka") }
            if (hasFile && !overlay) TextButton(onClick = onSave, contentPadding = pad) {
                Text("Simpan")
                if (dirty) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(6.dp).background(cs.tertiary, CircleShape))
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, "Menu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (hasFile) DropdownMenuItem(text = { Text(I18n.t("Daftar objek")) }, onClick = { menuOpen = false; onTab(0) })
                    if (overlay) {
                        DropdownMenuItem(text = { Text("Buka") }, onClick = { menuOpen = false; onOpen() })
                        if (hasFile) DropdownMenuItem(text = { Text(if (dirty) "Simpan \u2022" else "Simpan") }, onClick = { menuOpen = false; onSave() })
                    }
                    menu { menuOpen = false }
                }
            }
        }
    }
}

@Composable
private fun HeaderTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.primary
    Box(
        Modifier.fillMaxHeight().clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    val h = 2.dp.toPx()
                    drawRect(line, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) cs.primary else cs.onSurfaceVariant, maxLines = 1)
    }
}

// ---------- 3D ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun View3D(vm: EditorVM) {
    val st = remember(vm.fileName) { sceneStats(vm.entries) }
    var yaw by remember(vm.fileName) { mutableFloatStateOf(0.5f) }
    var pitch by remember(vm.fileName) { mutableFloatStateOf(0.8f) }
    var dist by remember(vm.fileName) { mutableFloatStateOf(st[3]) }
    var tx by remember(vm.fileName) { mutableFloatStateOf(st[0]) }
    var ty by remember(vm.fileName) { mutableFloatStateOf(st[1]) }
    var tz by remember(vm.fileName) { mutableFloatStateOf(st[2]) }
    var panMode by remember { mutableStateOf(false) }
    val tm = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outline

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().clipToBounds()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.type == PointerEventType.Scroll) {
                                val dy = ev.changes.sumOf { it.scrollDelta.y.toDouble() }
                                dist = (dist * 1.12.pow(dy).toFloat()).coerceIn(15f, 9000f)
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        dist = (dist / zoom).coerceIn(15f, 9000f)
                        if (!panMode) {
                            yaw -= pan.x * 0.008f
                            pitch = (pitch + pan.y * 0.008f).coerceIn(0.1f, 1.5f)
                        } else {
                            val k = dist / (minOf(size.width, size.height) * 1.1f)
                            val c = cos(yaw); val s = sin(yaw)
                            tx += -pan.x * k * c - pan.y * k * s
                            ty += -pan.x * k * s + pan.y * k * c
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val cam = Cam(tx, ty, tz, yaw, pitch, dist, size.width.toFloat(), size.height.toFloat())
                        val o = FloatArray(3)
                        var best = -1; var bd = (30f * density) * (30f * density)
                        vm.entries.forEachIndexed { i, e ->
                            if (e is Obj && cam.project(e.x, e.y, e.z, o)) {
                                val d = (Offset(o[0], o[1]) - tap).getDistanceSquared()
                                if (d < bd) { bd = d; best = i }
                            }
                        }
                        if (best >= 0) vm.sel = best
                    }
                }
        ) {
            val cam = Cam(tx, ty, tz, yaw, pitch, dist, size.width, size.height)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF14231C), Color(0xFF080B09))))

            // ground grid
            val step = 10f.pow(floor(log10(dist / 5f)))
            val x0 = floor(tx / step) * step
            val y0 = floor(ty / step) * step
            for (k in -8..8) {
                val gc = grid.copy(alpha = if (k % 5 == 0) 0.9f else 0.45f)
                line3(cam, x0 + k * step, y0 - 8 * step, 0f, x0 + k * step, y0 + 8 * step, 0f, gc)
                line3(cam, x0 - 8 * step, y0 + k * step, 0f, x0 + 8 * step, y0 + k * step, 0f, gc)
            }

            // visible objects, far to near
            val dep = FloatArray(vm.entries.size)
            val vis = ArrayList<Int>()
            val maxD = dist * 6f + 800f
            vm.entries.forEachIndexed { i, e ->
                if (e is Obj) {
                    val d = cam.zc(e.x, e.y, e.z)
                    if (d in 1f..maxD) { dep[i] = d; vis.add(i) }
                }
            }
            vis.sortByDescending { dep[it] }

            val half = max(3f, dist * 0.006f)
            val o = FloatArray(3); val gp = FloatArray(3); val pts = FloatArray(16)
            val path = Path()
            fun face(a: Int, b: Int, c: Int, d: Int, col: Color, k: Float) {
                path.reset()
                path.moveTo(pts[a * 2], pts[a * 2 + 1]); path.lineTo(pts[b * 2], pts[b * 2 + 1])
                path.lineTo(pts[c * 2], pts[c * 2 + 1]); path.lineTo(pts[d * 2], pts[d * 2 + 1])
                path.close()
                drawPath(path, shade(col, k))
                drawPath(path, Color(0x66000000), style = Stroke(1f))
            }

            for (i in vis) {
                val e = vm.entries[i] as Obj
                val col = modelColor(e.model)
                if (!cam.project(e.x, e.y, e.z, o)) continue
                if (cam.project(e.x, e.y, 0f, gp))
                    drawLine(col.copy(alpha = 0.35f), Offset(gp[0], gp[1]), Offset(o[0], o[1]), 1f)
                val px = half * 2f / o[2] * cam.focal
                var cube = px >= 4f
                if (cube) {
                    for (b in 0..7) {
                        val ok = cam.project(
                            e.x + if (b and 1 != 0) half else -half,
                            e.y + if (b and 2 != 0) half else -half,
                            e.z + if (b and 4 != 0) half else -half, gp)
                        if (!ok) { cube = false; break }
                        pts[b * 2] = gp[0]; pts[b * 2 + 1] = gp[1]
                    }
                }
                if (cube) {
                    val dx = cam.camX - e.x; val dy = cam.camY - e.y; val dz = cam.camZ - e.z
                    if (dy < -half) face(0, 1, 5, 4, col, 0.8f)
                    if (dy > half) face(2, 3, 7, 6, col, 0.8f)
                    if (dx < -half) face(0, 2, 6, 4, col, 0.65f)
                    if (dx > half) face(1, 3, 7, 5, col, 0.65f)
                    if (dz < -half) face(0, 1, 3, 2, col, 0.5f)
                    if (dz > half) face(4, 5, 7, 6, col, 1.1f)
                } else {
                    drawCircle(col, 2.5f * density, Offset(o[0], o[1]))
                }
                if (i == vm.sel) drawCircle(Color.White, max(px, 10f * density), Offset(o[0], o[1]), style = Stroke(2f * density))
            }

            // labels: nearest 25 + selected
            var shown = 0
            val thr = max(250f, dist * 0.9f)
            for (idx in vis.indices.reversed()) {
                val i = vis[idx]
                val want = i == vm.sel || (shown < 25 && dep[i] <= thr)
                if (!want) continue
                val e = vm.entries[i] as Obj
                if (cam.project(e.x, e.y, e.z, o)) {
                    shown++
                    label(tm, e.model, o[0] + 8f, o[1] - 34f)
                }
            }
        }

        ViewToolbar(
            panMode = panMode, setPan = { panMode = it },
            canUndo = vm.canUndo, canRedo = vm.canRedo,
            onFocus = {
                val o = vm.entries.getOrNull(vm.sel)
                if (o is Obj) { tx = o.x; ty = o.y; tz = o.z; dist = minOf(dist, 120f) }
            },
            onAll = { tx = st[0]; ty = st[1]; tz = st[2]; dist = st[3]; yaw = 0.5f; pitch = 0.8f },
            onUndo = { vm.undo() }, onRedo = { vm.redo() },
            panelVisible = null, onTogglePanel = {},
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        )
    }
}

// ---------- List ----------
@Composable
fun ListView(vm: EditorVM) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 semua, 1 luar, 2 interior, 3 LOD
    fun isLod(o: Obj) = o.model.startsWith("lod", ignoreCase = true)
    val all = vm.entries.withIndex().filter { it.value is Obj }
    val nOut = all.count { (it.value as Obj).interior == 0 }
    val nIn = all.size - nOut
    val nLod = all.count { isLod(it.value as Obj) }
    val rows = all.filter { (_, e) ->
        e as Obj
        val okF = when (filter) { 1 -> e.interior == 0; 2 -> e.interior != 0; 3 -> isLod(e); else -> true }
        okF && (q.isBlank() || e.model.contains(q.trim(), true) || e.id.toString() == q.trim())
    }
    Column {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Cari model atau ID  ·  ${vm.fileName}", maxLines = 1) },
            singleLine = true, shape = RoundedCornerShape(16.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val labels = listOf("Semua ${all.size}", "Luar $nOut", "Interior $nIn", "LOD $nLod")
            labels.forEachIndexed { i, l ->
                FilterChip(
                    selected = filter == i, onClick = { filter = i }, label = { Text(l) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(rows, key = { it.index }) { row ->
                val i = row.index
                val e = row.value as Obj
                Card(
                    onClick = { vm.sel = i }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(modelColor(e.model), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("${e.id}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(e.model, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(String.format(Locale.US, "%.1f, %.1f, %.1f", e.x, e.y, e.z),
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Edit ----------
@Composable
fun Field(label: String, v: String, mod: Modifier = Modifier, num: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = v, onValueChange = onChange, modifier = mod, label = { Text(label) },
        singleLine = true, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (num) KeyboardType.Decimal else KeyboardType.Text)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(vm: EditorVM) {
    val i = vm.sel
    val o = vm.entries[i] as Obj
    var model by remember(i) { mutableStateOf(o.model) }
    var id by remember(i) { mutableStateOf(o.id.toString()) }
    var x by remember(i) { mutableStateOf(o.x.toString()) }
    var y by remember(i) { mutableStateOf(o.y.toString()) }
    var z by remember(i) { mutableStateOf(o.z.toString()) }
    ModalBottomSheet(onDismissRequest = { vm.sel = -1 }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit objek", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Model", model, Modifier.weight(2f), num = false) { model = it }
                Field("ID", id, Modifier.weight(1f)) { id = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("X", x, Modifier.weight(1f)) { x = it }
                Field("Y", y, Modifier.weight(1f)) { y = it }
                Field("Z", z, Modifier.weight(1f)) { z = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        vm.update(i, o.copy(
                            model = model.trim().ifEmpty { o.model }, id = id.toIntOrNull() ?: o.id,
                            x = x.toFloatOrNull() ?: o.x, y = y.toFloatOrNull() ?: o.y, z = z.toFloatOrNull() ?: o.z))
                        vm.sel = -1
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                ) { Text("Terapkan") }
                OutlinedButton(
                    onClick = { vm.remove(i) }, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Hapus") }
            }
        }
    }
}

@Composable
fun StepRow(done: Boolean, title: String, desc: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(if (done) "\u2713" else "\u25CB", color = if (done) cs.primary else cs.onSurfaceVariant,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

private val guideSteps = listOf(
        "1. Pilih folder game" to "Di halaman awal, tekan Ganti pada kartu Folder GTA San Andreas, lalu pilih folder files yang berisi data, models, dan texdb. Jangan pilih texdb saja.",
        "2. Pilih map" to "Tekan Pilih map. Buka data lalu maps, lalu pilih folder wilayah (misalnya LA). Tekan file .ipl untuk mengedit objek, atau file .ide untuk menambah model.",
        "3. Tambah model ke IDE" to "Di file .ide, cari model di kolom Cari model dari gta3.img. Hanya model yang ada di gta3.img dan belum punya ID yang ditampilkan. Isi ID (atau tekan ID otomatis, hasilnya di bawah 20000), isi nama TXD, lalu tekan Tambah ke IDE. File IDE lama dicadangkan otomatis.",
        "4. Taruh objek di IPL" to "Buka file .ipl, lalu tekan Tambah objek dan pilih model yang sudah punya ID. Atur posisi, lalu Simpan.",
        "5. Tampilan 3D" to "Buka tab 3D. Geser satu jari untuk memutar kamera, cubit untuk zoom. Ketuk objek untuk memilihnya, lalu seret panah X, Y, atau Z untuk menggeser. Toolbar berisi Putar, Geser, Fokus, Grid, Tampilan atas, Pilih banyak, dan Inspector.",
        "6. Inspector" to "Tab Objek berisi tombol geser, rotasi, Kunci, dan Hapus. Tab Adegan berisi Tambah objek, Ganti model, dan Tanah.",
        "7. Simpan" to "Tombol Simpan menimpa file asli. Titik kuning berarti ada perubahan yang belum disimpan. Menu titik tiga berisi Simpan sebagai, Urungkan, Ulangi, dan Diagnosa file game.",
        "8. Aturan penting" to "Pakai ID di bawah 20000. ID 20000 ke atas membuat game crash. Pastikan nama model sama persis di IDE, IPL, dan gta3.img. Backup gta3.img sebelum mengubahnya."
)

private val changelogItems = listOf(
    "Editor IPL dengan tampilan 3D dan inspector.",
    "Tambah model dari gta3.img ke IDE, lalu taruh objek di IPL.",
    "Pilih banyak objek, grid, kunci objek, dan tampilan atas.",
    "Halaman awal baru dengan bahasa Indonesia dan English.",
    "Perbaikan: ID otomatis di bawah 20000, gta3.img di texdb terbaca.",
    "Kamera: tombol arah dan geser objek lebih aman.",
    "Kartu objek lebih kecil dan tombol kamera berbentuk panah.",
    "Pop-up update menampilkan catatan rilis.",
    "Rotasi lebih halus, geser objek perlu ditahan sebentar, dan tombol kamera bisa diatur kecepatannya.",
    "Mode Objek: kamera berpusat di objek yang dipilih.",
    "Pratinjau texture model saat menambah objek.",
    "Tampilan 3D layar penuh: header transparan, toolbar dan Inspector di kiri atas.",
    "Perbaikan: toolbar atas di tab 3D bisa ditekan.",
    "Mode Jelajah, Kamera, dan Objek dipisah. Pilih objek dengan tahan. Joystick untuk putar kamera.",
    "Tambah objek: pilih model dari gambar yang bisa digulir, tanpa mengetik ID.",
    "Tombol panah kiri untuk kembali ke mode Jelajah setelah mengedit objek.",
    "Di mode Jelajah, tap objek hanya memilihnya. Tekan Edit (pensil) untuk menggesernya.",
    "Tombol arah memakai pad bergaya D-pad. Bahasa mengikuti sistem: Indonesia atau English.",
    "Tampilan 3D diperbarui: pad keyboard, gizmo sumbu, joystick dihapus.",
    "Tab Objek dipindah ke menu ⋮ di samping tab 3D.",
    "Tampilan 3D dirapikan: panel kanan lebih kecil, gizmo di kiri bawah, kartu objek di tengah bawah.",
    "Toolbar atas dirapikan: lima ikon utama, sisanya di menu ≡.",
    "Gizmo di atas tombol kamera di kanan bawah. Kartu objek di kiri bawah."
)

@Composable
fun GuideDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(I18n.t("Panduan penggunaan")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((title, body) in guideSteps) Column {
                    Text(I18n.t(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(I18n.t(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(I18n.t("Tutup")) } }
    )
}

@Composable
fun ChangelogDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(I18n.t("Changelog")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (item in changelogItems) Text("• " + I18n.t(item), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(I18n.t("Tutup")) } }
    )
}

@Composable
fun SupportSection() {
    val ctx = LocalContext.current
    var showCritic by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var manualRel by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = {
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { UpdateChecker.fetchLatest() }.getOrNull() }
                when {
                    r == null -> Toast.makeText(ctx, I18n.t("Gagal cek update. Periksa koneksi."), Toast.LENGTH_SHORT).show()
                    UpdateChecker.isNewer(r.version, UpdateChecker.currentVersion(ctx)) -> manualRel = r
                    else -> Toast.makeText(ctx, I18n.t("Anda sudah memakai versi terbaru."), Toast.LENGTH_SHORT).show()
                }
            }
        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(I18n.t("Cek update")) }
        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://saweria.co/ReyyXSA"))) },
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(I18n.t("Dukung")) }
        OutlinedButton(onClick = { showCritic = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(I18n.t("Kritik & saran")) }
        OutlinedButton(onClick = { showLang = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(I18n.t("Bahasa")) }
    }
    if (showCritic) CriticDialog(ctx) { showCritic = false }
    manualRel?.let { UpdateDialog(ctx, it) { manualRel = null } }
    if (showLang) LanguageDialog(ctx) { showLang = false }
}

@Composable
fun CriticDialog(ctx: Context, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(I18n.t("Kritik & saran")) },
        text = { Text(I18n.t("Kirim kritik atau saran lewat email atau Facebook.")) },
        confirmButton = {
            TextButton(onClick = {
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:reysaputra567@gmail.com?subject=" + Uri.encode("Kritik & saran San Editor")))
                ctx.startActivity(mail); onClose()
            }) { Text(I18n.t("Email")) }
        },
        dismissButton = {
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/1DXWyJB8i5/"))); onClose()
            }) { Text("Facebook") }
        }
    )
}

@Composable
fun LanguageDialog(ctx: Context, onClose: () -> Unit) {
    val options = listOf("id" to "Bahasa Indonesia", "en" to "English")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(I18n.t("Bahasa")) },
        text = {
            Column {
                for ((code, label) in options) {
                    TextButton(onClick = { I18n.set(ctx, code); onClose() }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, fontWeight = if (I18n.lang == code) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(I18n.t("Tutup")) } }
    )
}

@Composable
fun UpdateDialog(ctx: Context, rel: UpdateChecker.Release, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(I18n.t("Ada update baru") + " v" + rel.version) },
        text = {
            val notes = rel.notes
                .replace(Regex("(?m)^#+\\s*"), "").replace("**", "").replace("__", "").replace("`", "")
                .replace(Regex("(?m)^\\s*[-*]\\s+"), "\u2022 ").trim()
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(if (notes.isBlank()) I18n.t("Versi terbaru tersedia di GitHub.") else notes, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.apkUrl ?: rel.pageUrl)))
                onClose()
            }) { Text(I18n.t("Unduh")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    ctx.getSharedPreferences("smpe", Context.MODE_PRIVATE).edit().putString("skip_update", rel.version).apply()
                    onClose()
                }) { Text(I18n.t("Jangan tampilkan untuk versi ini")) }
                TextButton(onClick = onClose) { Text(I18n.t("Nanti")) }
            }
        }
    )
}

/** Sidebar kiri ala aplikasi desktop: menu utama dengan gaya kaca biru. */
@Composable
fun HomeSidebar(onGuide: () -> Unit, onChangelog: () -> Unit, onCheckUpdate: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.width(190.dp).fillMaxHeight()
            .clip(shape).background(
                Brush.linearGradient(listOf(Color(0xD51C5B88), Color(0xC20A2745), Color(0xD20B4C79)))
            )
            .border(1.dp, Color(0xAA55BFFF), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("San Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black,
            color = cs.onSurface, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 10.dp))
        SideItem(I18n.t("Beranda"), selected = true) { }
        SideItem(I18n.t("Panduan")) { onGuide() }
        SideItem(I18n.t("Changelog")) { onChangelog() }
        SideItem(I18n.t("Cek update")) { onCheckUpdate() }
        SideItem("Donate") {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://saweria.co/ReyyXSA")))
        }
        SideItem(I18n.t("Kritik & saran")) {
            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:reysaputra567@gmail.com?subject=" + Uri.encode("Kritik & saran San Editor"))))
        }
        SideItem(I18n.t("Bahasa")) {
            I18n.set(ctx, if (I18n.lang == "id") "en" else "id")
        }
    }
}

@Composable
fun SideItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(Color(0xDD1689E8), Color(0xBB1255A0)))
                else Brush.linearGradient(listOf(Color(0x663B83B5), Color(0x44305C85)))
            )
            .border(1.dp, if (selected) Color(0xFF75D1FF) else Color(0x554CA9E8), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) cs.onSurface else cs.onSurfaceVariant)
    }
}
