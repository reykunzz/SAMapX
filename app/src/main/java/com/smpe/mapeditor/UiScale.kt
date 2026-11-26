package com.smpe.mapeditor

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

/**
 * Memperbesar isi sebesar [UiScale.CONTROL_BOOST] bila UI sedang diperkecil oleh UiScale
 * (tidak pernah melebihi ukuran asli perangkat). Tidak membuat layout node baru,
 * jadi aman dipakai di dalam Box dengan Modifier.align.
 */
@Composable
fun ControlBoost(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val boosted = minOf(d.density * UiScale.CONTROL_BOOST, maxOf(UiScale.realDensity, d.density))
    if (boosted <= d.density + 0.001f) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(boosted, d.fontScale)) { content() }
    }
}

/**
 * Menyamakan tampilan di semua perangkat.
 *
 * Di HP, sisi pendek layar (landscape = tinggi) hanya sekitar 360-420dp, sedangkan di tablet/mode
 * desktop bisa 650dp ke atas. Karena itu UI di HP terlihat "ngezoom": hanya sedikit elemen yang muat.
 * Di sini aplikasi dibuat seolah-olah punya layar sebesar [REF_SHORT_DP], lalu seluruh UI
 * (Compose, dialog, dropdown, bottom sheet) diperkecil sebanding lewat densityDpi.
 *
 * Yang TIDAK terpengaruh: ukuran piksel GLSurfaceView (3D tetap tajam di resolusi asli).
 * Hit-test sentuhan di viewport 3D memakai [realDensity] supaya area sentuh tetap seukuran jari.
 */
object UiScale {
    /** Sisi pendek layar acuan dalam dp. Perbesar = UI makin kecil, perkecil = UI makin besar. */
    const val REF_SHORT_DP = 600f

    /** Pembesar tambahan untuk kontrol sentuh (gizmo + tombol kamera kanan bawah). 1f = tanpa tambahan. */
    const val CONTROL_BOOST = 1.25f

    /** Batas skala terkecil, supaya tombol di layar yang sangat kecil tidak terlalu mungil. */
    const val MIN_SCALE = 0.45f

    /** true = ukuran font sistem diabaikan, supaya tampilan sama di semua HP. */
    const val LOCK_FONT_SCALE = true

    /** Densitas asli perangkat (tanpa skala di atas). Dipakai untuk toleransi sentuhan. */
    val realDensity: Float get() = Resources.getSystem().displayMetrics.density

    /** Dipanggil dari Activity.attachBaseContext(). */
    fun wrap(base: Context): Context {
        val res = base.resources
        val dm = res.displayMetrics
        val shortDp = minOf(dm.widthPixels, dm.heightPixels) / dm.density
        val scale = (shortDp / REF_SHORT_DP).coerceIn(MIN_SCALE, 1f)

        val cfg = Configuration(res.configuration)
        if (scale < 1f) {
            cfg.densityDpi = (dm.densityDpi * scale).roundToInt().coerceAtLeast(60)
            // Samakan juga ukuran dp yang dilaporkan, supaya kode yang membaca Configuration konsisten.
            if (cfg.screenWidthDp > 0) cfg.screenWidthDp = (cfg.screenWidthDp / scale).roundToInt()
            if (cfg.screenHeightDp > 0) cfg.screenHeightDp = (cfg.screenHeightDp / scale).roundToInt()
            if (cfg.smallestScreenWidthDp > 0) cfg.smallestScreenWidthDp = (cfg.smallestScreenWidthDp / scale).roundToInt()
        }
        if (LOCK_FONT_SCALE) cfg.fontScale = 1f
        return base.createConfigurationContext(cfg)
    }
}
