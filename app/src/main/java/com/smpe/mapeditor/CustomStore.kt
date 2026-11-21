package com.smpe.mapeditor

import android.content.Context
import java.io.File

/** Menyimpan model impor (DFF/TXD) di penyimpanan aplikasi supaya tidak hilang saat game dibuka ulang. */
object CustomStore {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "custom").apply { mkdirs() }

    fun save(ctx: Context, name: String, id: Int, dff: ByteArray, txd: ByteArray?) {
        val d = dir(ctx)
        File(d, "$name.dff").writeBytes(dff)
        val t = File(d, "$name.txd")
        if (txd != null) t.writeBytes(txd) else t.delete()
        val list = File(d, "list.txt")
        val kept = (if (list.exists()) list.readLines() else emptyList()).filter { !it.startsWith("$name ") }
        list.writeText((kept + "$name $id").joinToString("\n") + "\n")
    }

    /** Pulihkan semua model impor ke GameData yang baru dibuka. Mengembalikan jumlah yang berhasil. */
    fun restore(ctx: Context, g: GameData): Int {
        val d = dir(ctx)
        val list = File(d, "list.txt")
        if (!list.exists()) return 0
        var n = 0
        for (line in list.readLines()) {
            val parts = line.trim().split(" ")
            if (parts.size < 2) continue
            val name = parts[0]
            val id = parts[1].toIntOrNull() ?: continue
            val dffFile = File(d, "$name.dff")
            if (!dffFile.exists()) continue
            val txdFile = File(d, "$name.txd")
            if (g.importModel(name, dffFile.readBytes(), if (txdFile.exists()) txdFile.readBytes() else null)) {
                g.customIds[name] = id
                n++
            }
        }
        return n
    }
}
