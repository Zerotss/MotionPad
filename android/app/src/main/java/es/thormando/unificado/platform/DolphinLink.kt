package es.thormando.unificado.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Docs
import android.provider.DocumentsContract.Document as Doc
import es.thormando.unificado.domain.DolphinConfig

/** Uses the Android folder grant for the official Dolphin user-data provider. */
class DolphinLink(private val ctx: Context, private val tree: Uri) {
    companion object {
        const val PACKAGE = "org.dolphinemu.dolphinemu"
        const val AUTHORITY = "$PACKAGE.user"
    }
    private val resolver = ctx.contentResolver
    private data class Change(val name: String, val original: ByteArray?, val updated: ByteArray, var uri: Uri?)

    private fun child(parent: Uri, name: String): Uri? {
        val children = Docs.buildChildDocumentsUriUsingTree(tree, Docs.getDocumentId(parent))
        val cursor = requireNotNull(resolver.query(children, arrayOf(Doc.COLUMN_DOCUMENT_ID, Doc.COLUMN_DISPLAY_NAME), null, null, null))
        cursor.use {
            while (it.moveToNext()) if (it.getString(1) == name)
                return Docs.buildDocumentUriUsingTree(tree, it.getString(0))
        }
        return null
    }

    private fun read(uri: Uri) = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
    private fun write(uri: Uri, bytes: ByteArray) {
        requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
        check(read(uri).contentEquals(bytes)) { "Dolphin did not save the complete file." }
    }
    private fun create(folder: Uri, name: String) =
        requireNotNull(Docs.createDocument(resolver, folder, "application/octet-stream", name))

    fun configFolder(): Uri {
        val id = Docs.getTreeDocumentId(tree)
        val folder = Docs.buildDocumentUriUsingTree(tree, id)
        val message = "Open Dolphin Emulator, select its Config folder, then tap Use this folder."
        resolver.query(folder, arrayOf(Doc.COLUMN_DISPLAY_NAME, Doc.COLUMN_MIME_TYPE), null, null, null)?.use {
            require(it.moveToFirst() && it.getString(0) == "Config" && it.getString(1) == Doc.MIME_TYPE_DIR) { message }
        } ?: error(message)
        if (tree.authority == AUTHORITY) {
            require(id.trimEnd('/') == "root/Config") { message }
        } else {
            require(child(folder, "Dolphin.ini") != null || child(folder, "WiimoteNew.ini") != null) { message }
        }
        return folder
    }

    fun install(folder: Uri, connectedMask: Int) {
        val profiles = (1..2).map { ctx.assets.open("ThorCentro-J$it.ini").bufferedReader().use { r -> r.readText() } }
        fun change(name: String, content: (ByteArray?) -> ByteArray): Change {
            val uri = child(folder, name)
            val original = uri?.let(::read)
            return Change(name, original, content(original), uri)
        }
        val changes = listOf(
            change("DSUClient.ini") { DolphinConfig.SERVER_FILE.toByteArray() },
            change("WiimoteNew.ini") { original ->
                val text = original?.let { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(it)).toString() }.orEmpty()
                DolphinConfig.controllers(text, profiles, connectedMask).toByteArray()
            })
            .filterNot { it.original?.contentEquals(it.updated) == true }
        val stamp = System.currentTimeMillis()
        for (c in changes) c.original?.let { write(create(folder, "${c.name}.motionpad-$stamp.bak"), it) }
        val started = mutableListOf<Change>()
        try {
            for (c in changes) {
                check(child(folder, c.name) == c.uri && (c.uri == null || read(c.uri!!).contentEquals(c.original))) {
                    "Dolphin settings changed. Close Dolphin and try again."
                }
                val target = c.uri ?: create(folder, c.name)
                c.uri = target
                started.add(c)
                check(child(folder, c.name) == target) { "The file provider renamed the settings file." }
                write(target, c.updated)
            }
        } catch (e: Exception) {
            val restored = started.asReversed().map { c -> runCatching {
                if (c.original == null) check(Docs.deleteDocument(resolver, c.uri!!)) else write(c.uri!!, c.original)
            }.isSuccess }.all { it }
            if (!restored) error("Setup was interrupted. Restore the .motionpad-$stamp.bak files in Dolphin's Config folder.")
            throw e
        }
    }
}
