package com.smpe.mapeditor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Terjemahan sederhana. Kunci = teks bahasa Indonesia. Tambah bahasa baru dengan menambah peta di sini. */
object I18n {
    var lang by mutableStateOf("id")

    fun load(ctx: Context) {
        lang = ctx.getSharedPreferences("smpe", Context.MODE_PRIVATE).getString("lang", "id") ?: "id"
    }

    fun set(ctx: Context, code: String) {
        lang = code
        ctx.getSharedPreferences("smpe", Context.MODE_PRIVATE).edit().putString("lang", code).apply()
    }

    fun t(key: String): String = if (lang == "en") (en[key] ?: key) else key

    private val en: Map<String, String> = mapOf(
        "Pilih map" to "Select map",
        "Ganti folder" to "Change folder",
        "Buat IPL baru" to "New IPL",
        "Buka File IPL" to "Open IPL file",
        "Langkah" to "Steps",
        "1. Pilih folder game" to "1. Choose game folder",
        "2. Pilih map" to "2. Choose map",
        "3. Edit dan simpan" to "3. Edit and save",
        "Pilih folder files di kartu Folder GTA San Andreas." to "Choose the files folder in the GTA San Andreas Folder card.",
        "Tekan Pilih map, lalu pilih file .ipl yang mau diedit." to "Tap Select map, then choose the .ipl file to edit.",
        "Pindahkan atau tambah objek, lalu tekan Simpan." to "Move or add objects, then tap Save.",
        "Folder game terdeteksi ✓" to "Game folder detected ✓",
        "Belum dipilih. Tekan Pilih." to "Not selected. Tap Select.",
        "Folder belum benar. Pilih folder files." to "Folder is invalid. Choose the files folder.",
        "Folder belum benar" to "Folder is invalid",
        "Pilih ulang" to "Select again",
        "Pilih folder files yang berisi data, models, dan texdb." to "Choose the files folder that contains data, models, and texdb.",
        "Folder GTA San Andreas" to "GTA San Andreas Folder",
        "Editor map GTA San Andreas" to "GTA San Andreas map editor",
        "Buka" to "Open",
        "Ganti" to "Change",
        "Pilih file map (.ipl) dari folder game. Dari sana Anda juga bisa mendaftarkan model dan membuat IPL baru." to "Choose a map file (.ipl) from the game folder. From there you can also register models and create a new IPL.",
        "File terakhir" to "Recent files",
        "Hapus daftar" to "Clear list",
        "Belum ada file dibuka" to "No file opened yet",
        "File yang Anda buka akan muncul di sini." to "Files you open will appear here.",
        "Panduan" to "Guide",
        "Changelog" to "Changelog",
        "Panduan penggunaan" to "How to use",
        "Tutup" to "Close",
        "Dukung" to "Support",
        "Kritik & saran" to "Feedback",
        "Bahasa" to "Language",
        "Kirim kritik atau saran lewat email atau Facebook." to "Send feedback by email or Facebook.",
        "Email" to "Email"
    )
}
