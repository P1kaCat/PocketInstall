package app.pocketinstall

import android.content.Context
import app.pocketinstall.server.WinPeBundle
import app.pocketinstall.server.WinPeEntry
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Immutable imported directories; the preference is switched only after verification. */
object WinPeStorage {
    private fun preferences(context: Context) = context.getSharedPreferences("winpe", Context.MODE_PRIVATE)
    fun current(context: Context): File? {
        val id = preferences(context).getString("bundle", null) ?: return null
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(context.filesDir, "winpe/$id").takeIf { it.isDirectory }
    }
    fun decode(bytes: ByteArray): List<WinPeEntry> {
        val json = JSONObject(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        require(json.getString("kind") == "pocketinstall-winpe-bundle-v1" &&
            json.getString("architecture") == "x64" && !json.getBoolean("installsWindows")) { "Bundle WinPE x64 attendu." }
        val entries = json.getJSONArray("resources")
        return (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            WinPeEntry(entry.getString("name"), entry.getLong("bytes"), entry.getString("sha256"))
        }
    }
    fun verify(directory: File) = WinPeBundle.verify(directory, decode(File(directory, "manifest.json").readBytes()))
    fun importBundle(context: Context, input: InputStream) {
        val parent = File(context.filesDir, "winpe").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val directory = File(parent, id)
        WinPeBundle.extract(input, directory, ::decode)
        if (!preferences(context).edit().putString("bundle", id).commit()) {
            directory.deleteRecursively()
            error("Impossible d'enregistrer le bundle.")
        }
    }
}
