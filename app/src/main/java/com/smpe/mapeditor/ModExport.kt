package com.smpe.mapeditor

import android.content.Context
import java.io.File

/**
 * Membuat paket mod yang strukturnya sama dengan folder files/ milik game:
 *   data/maps/custom/custom.ide, custom.ipl
 *   models/gta3/<nama>.dff
 *   texdb/txd/<nama>.txd (jika ada)
 * Folder hasilnya ada di penyimpanan aplikasi, lalu disalin ke folder game lewat Termux.
 */
object ModExport {
    fun export(ctx: Context, g: GameData, entries: List<Entry>): String {
        val base = ctx.getExternalFilesDir(null) ?: return "Penyimpanan eksternal tidak tersedia."
        val root = File(base, "SanEditor_mod")
        root.deleteRecursively()
        val ideDir = File(root, "data/maps/custom").apply { mkdirs() }
        val modelDir = File(root, "models/gta3").apply { mkdirs() }
        val txdDir = File(root, "texdb/txd").apply { mkdirs() }
        val store = File(ctx.filesDir, "custom")

        val defIds = g.defList.map { it.id }.toHashSet()
        val custom = g.customIds.toSortedMap()

        // Bentrok nama atau ID dengan game akan membuat game crash, jadi ekspor dibatalkan.
        val conflicts = ArrayList<String>()
        for ((name, id) in custom) {
            if (g.defList.any { it.lc == name }) conflicts += "$name: nama sudah ada di game"
            if (g.defList.any { it.id == id && it.lc != name }) conflicts += "$name: ID $id sudah dipakai game"
            if (g.imgIndex.keys.any { it.equals("$name.dff", ignoreCase = true) }) conflicts += "$name: ada di gta3.img"
        }
        if (conflicts.isNotEmpty())
            return "EKSPOR DIBATALKAN, ada bentrok:\n" + conflicts.joinToString("\n") +
                "\nImpor ulang model dengan nama yang belum dipakai."

        // IDE: satu baris per model impor yang filenya masih tersimpan.
        val ide = StringBuilder("objs\n")
        var models = 0
        for ((name, id) in custom) {
            val dff = File(store, "$name.dff")
            if (!dff.exists()) continue
            dff.copyTo(File(modelDir, "$name.dff"), overwrite = true)
            val txd = File(store, "$name.txd")
            if (txd.exists()) txd.copyTo(File(txdDir, "$name.txd"), overwrite = true)
            ide.append("$id, $name, $name, 1, 300\n")
            models++
        }
        ide.append("end\n")

        // IPL: hanya objek yang memakai ID model impor, supaya peta asli tidak ikut berubah.
        val customIdSet = custom.values.toHashSet()
        val ipl = StringBuilder("inst\n")
        var objs = 0
        for (e in entries) if (e is Obj && e.id in customIdSet) { ipl.append(e.line()).append('\n'); objs++ }
        ipl.append("end\n")

        File(ideDir, "custom.ide").writeText(ide.toString())
        File(ideDir, "custom.ipl").writeText(ipl.toString())

        // Objek dengan ID yang tidak ada di IDE mana pun. Ini penyebab umum game crash saat dimuat.
        val unknown = entries.filterIsInstance<Obj>()
            .filter { it.id !in defIds && it.id !in customIdSet }
            .map { "${it.id} (${it.model})" }.distinct()

        val src = root.absolutePath
        val gameFiles = "/storage/emulated/0/Android/data/com.rockstargames.gtasa/files"
        File(root, "BACA_DULU.txt").writeText(buildString {
            appendLine("LANGKAH PEMASANGAN")
            appendLine("1. Backup dulu: cp $gameFiles/data/gta.dat $gameFiles/data/gta.dat.bak")
            appendLine("2. Salin isi folder ini ke folder game:")
            appendLine("   cp -r $src/* $gameFiles/")
            appendLine("3. Buka $gameFiles/data/gta.dat, lalu tambahkan:")
            appendLine("   - di bawah baris IDE terakhir:  IDE DATA\\MAPS\\custom\\custom.IDE")
            appendLine("   - di bawah baris IPL terakhir:  IPL DATA\\MAPS\\custom\\custom.IPL")
            appendLine("4. Tes dengan satu objek dulu sebelum menambah banyak objek.")
            appendLine()
            appendLine("Model: $models, objek di IPL: $objs")
        })

        return buildString {
            append("Export selesai: $models model, $objs objek.\n")
            append("Folder: $src\n")
            if (unknown.isNotEmpty())
                append("PERINGATAN: objek dengan ID tidak dikenal: ${unknown.take(5).joinToString(", ")}. Objek ini bisa membuat game crash, jadi jangan diekspor sebelum diperbaiki.\n")
            append("Langkah pemasangan ada di BACA_DULU.txt.")
        }
    }
}
