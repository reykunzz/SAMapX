package com.smpe.mapeditor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Item di dalam folder game yang dibaca lewat SAF (folder yang dipilih di awal). */
data class DocItem(val docId: String, val name: String, val isDir: Boolean)

fun listChildren(ctx: Context, tree: Uri, parentId: String): List<DocItem> {
    val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
    val cols = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    val out = ArrayList<DocItem>()
    ctx.contentResolver.query(childUri, cols, null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val id = c.getString(0) ?: continue
            val name = c.getString(1) ?: continue
            out.add(DocItem(id, name, c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
        }
    }
    return out.sortedWith(compareBy<DocItem>({ !it.isDir }, { it.name.lowercase() }))
}

/**
 * Menyisipkan satu baris ke blok objs pertama file IDE. Mengembalikan "ok" atau pesan error.
 * File lama dicadangkan dulu ke penyimpanan aplikasi.
 */
fun addToIde(ctx: Context, docUri: Uri, ideName: String, newLine: String, id: Int): String {
    val bytes = ctx.contentResolver.openInputStream(docUri)?.use { it.readBytes() }
        ?: return "Gagal membaca file IDE."
    val text = String(bytes, Charsets.ISO_8859_1)
    val lines = text.split(Regex("\r?\n")).toMutableList()
    if (lines.any { it.substringBefore(',').trim() == id.toString() })
        return "ID $id sudah ada di $ideName."
    val dir = File(ctx.filesDir, "ide_backup").apply { mkdirs() }
    File(dir, "${System.currentTimeMillis()}_$ideName").writeBytes(bytes)
    val eol = if (text.contains("\r\n")) "\r\n" else "\n"
    var inObjs = false
    var at = -1
    for (i in lines.indices) {
        val t = lines[i].trim().lowercase()
        if (t == "objs") inObjs = true
        else if (inObjs && t == "end") { at = i; break }
    }
    if (at >= 0) lines.add(at, newLine)
    else { lines.add("objs"); lines.add(newLine); lines.add("end") }
    val out = lines.joinToString(eol).toByteArray(Charsets.ISO_8859_1)
    ctx.contentResolver.openOutputStream(docUri, "wt")?.use { it.write(out) }
        ?: return "Gagal menulis file IDE."
    return "ok"
}

@Composable
fun MapBrowser(vm: EditorVM, tree: Uri, onOpen: (Uri) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val rootId = remember(tree) { DocumentsContract.getTreeDocumentId(tree) }
    var path by remember { mutableStateOf(listOf(Pair(rootId, ""))) }
    var items by remember { mutableStateOf(emptyList<DocItem>()) }
    var ideSel by remember { mutableStateOf<DocItem?>(null) }
    var msg by remember { mutableStateOf("") }
    val cur = path.last().first
    LaunchedEffect(cur) { items = withContext(Dispatchers.IO) { listChildren(ctx, tree, cur) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("files" + path.drop(1).joinToString("") { "/" + it.second }, Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, maxLines = 1)
            TextButton(onClick = onClose) { Text("Tutup") }
        }
        if (path.size > 1) TextButton(onClick = { path = path.dropLast(1); ideSel = null; msg = "" }) { Text("Naik") }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            for (item in items) {
                val show = item.isDir || item.name.endsWith(".ide", true) || item.name.endsWith(".ipl", true)
                if (show) {
                    Text(if (item.isDir) "[DIR] " + item.name else item.name,
                        Modifier.fillMaxWidth().clickable {
                            if (item.isDir) { path = path + Pair(item.docId, item.name); ideSel = null; msg = "" }
                            else if (item.name.endsWith(".ipl", true)) onOpen(DocumentsContract.buildDocumentUriUsingTree(tree, item.docId))
                            else { ideSel = item; msg = "" }
                        }.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        ideSel?.let { ide -> AddIdeCard(vm, tree, ide, onMsg = { msg = it }) }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AddIdeCard(vm: EditorVM, tree: Uri, ide: DocItem, onMsg: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember(ide.docId) { mutableStateOf("") }
    var model by remember(ide.docId) { mutableStateOf("") }
    var idText by remember(ide.docId) { mutableStateOf("") }
    var txd by remember(ide.docId) { mutableStateOf("") }
    val g = vm.game
    // Model dari gta3.img yang belum punya ID di IDE mana pun (ID 0).
    val candidates = g?.defList?.filter { it.id == 0 && it.lc.contains(search.trim().lowercase().removeSuffix(".dff")) }
        ?.map { it.lc }?.take(20) ?: emptyList()

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        Text("Tambah model ke ${ide.name}", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari model dari gta3.img") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth().height(160.dp).verticalScroll(rememberScrollState())) {
            for (c in candidates) {
                Text(c, Modifier.fillMaxWidth().clickable { model = c; if (txd.isEmpty()) txd = c }.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (candidates.isEmpty()) Text(
            "Tidak ada model yang bisa ditambahkan. Model yang sudah punya ID di IDE game tidak ditampilkan. " +
            "Model baru harus di-add dulu ke gta3.img dengan nama DFF yang belum dipakai game.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Terpilih: " + model.ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = idText, onValueChange = { idText = it.filter { ch -> ch.isDigit() } },
            label = { Text("ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = txd, onValueChange = { txd = it }, label = { Text("Nama TXD (kosong = nama model)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { idText = (g?.nextFreeId() ?: 0).toString() }, modifier = Modifier.weight(1f)) { Text("ID otomatis") }
            Button(onClick = {
                val gg = vm.game
                val id = idText.toIntOrNull() ?: 0
                val m = model.trim().lowercase()
                val t = txd.trim().lowercase().ifEmpty { m }
                when {
                    gg == null -> onMsg("Folder GTA SA belum dipilih.")
                    m.isEmpty() -> onMsg("Pilih model dulu.")
                    id <= 0 -> onMsg("Isi ID dengan angka.")
                    gg.idTaken(id) -> onMsg("ID $id sudah dipakai game.")
                    else -> {
                        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, ide.docId)
                        scope.launch(Dispatchers.IO) {
                            val r = addToIde(ctx, doc, ide.name, "$id, $m, $t, 1, 300", id)
                            withContext(Dispatchers.Main) {
                                if (r == "ok") {
                                    gg.defList.removeAll { d -> d.lc == m && d.id == 0 }
                                    gg.defList.add(ObjDef(id, m, t))
                                    onMsg("Ditambahkan ke ${ide.name}: $id, $m (TXD $t). Sekarang bisa dipilih di Tambah objek.")
                                    model = ""; idText = ""
                                } else onMsg(r)
                            }
                        }
                    }
                }
            }, modifier = Modifier.weight(1f)) { Text("Tambah ke IDE") }
        }
    }
}
