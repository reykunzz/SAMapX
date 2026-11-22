package com.smpe.mapeditor

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Mengecek rilis terbaru di GitHub. Rilis dibuat otomatis oleh workflow saat tag v* didorong. */
object UpdateChecker {
    // Ganti jika akun atau nama repo GitHub berbeda.
    const val OWNER = "reykunzz"
    const val REPO = "SAMapX"

    data class Release(val version: String, val pageUrl: String, val apkUrl: String?)

    fun currentVersion(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** Mengembalikan rilis terbaru, atau null jika gagal (misalnya tidak ada koneksi). */
    fun fetchLatest(): Release? {
        val conn = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "SanEditor")
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                }
            }
            return Release(json.optString("tag_name").removePrefix("v"), json.optString("html_url"), apk)
        } finally {
            conn.disconnect()
        }
    }

    /** true jika versi a lebih baru daripada b (format angka dipisah titik, misalnya 1.8.4). */
    fun isNewer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }
}
