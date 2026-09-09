package nz.myinspection.app.spike

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.util.UUID

/** Stores only a synthetic probe's grant, document and ID. Never opens inspection data. */
internal class SafProbe(context: Context) {
    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("platform-spike", Context.MODE_PRIVATE)
    private val access = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun writeAndVerify(tree: Uri, returnedFlags: Int): String {
        check(returnedFlags and access == access) { "Read/write grant required" }
        resolver.takePersistableUriPermission(tree, returnedFlags and access)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val id = UUID.randomUUID().toString()
        val payload = "MyInspection SAF probe $id\n"
        val doc = checkNotNull(DocumentsContract.createDocument(resolver, parent, "text/plain", "myinspection-spike-$id.txt"))
        checkNotNull(resolver.openOutputStream(doc, "w")).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        verifyBytes(doc, payload)
        // Store the single-line ID: this API 35 device adds XML indentation to trailing newlines.
        check(prefs.edit().putString("tree", tree.toString()).putString("doc", doc.toString())
            .putString("id", id).remove("payload").commit())
        return "SAF 写入并读回通过；请重启 app 后点只读验证。bytes=${payload.toByteArray(Charsets.UTF_8).size}"
    }

    fun verifyAfterRestart(): String {
        val tree = prefs.getString("tree", null) ?: return "尚无目录验证记录。"
        check(resolver.persistedUriPermissions.any { it.uri.toString() == tree && it.isReadPermission && it.isWritePermission })
        val payload = "MyInspection SAF probe ${checkNotNull(prefs.getString("id", null))}\n"
        verifyBytes(Uri.parse(checkNotNull(prefs.getString("doc", null))), payload)
        return "SAF 持久授权及只读验证通过；没有重写测试文件。bytes=${payload.toByteArray(Charsets.UTF_8).size}"
    }

    private fun verifyBytes(doc: Uri, payload: String) {
        val expected = payload.toByteArray(Charsets.UTF_8)
        // One extra byte detects an appended suffix without trusting provider length metadata.
        val bytes = ByteArray(expected.size + 1)
        var count = 0
        checkNotNull(resolver.openInputStream(doc)).use { input ->
            while (count < bytes.size) {
                val n = input.read(bytes, count, bytes.size - count)
                if (n < 0) break
                check(n > 0)
                count += n
            }
        }
        check(count == expected.size && bytes.copyOf(count).contentEquals(expected))
    }
}
