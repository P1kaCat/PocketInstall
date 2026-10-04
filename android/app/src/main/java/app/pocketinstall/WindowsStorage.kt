package app.pocketinstall

import android.content.Context
import app.pocketinstall.server.*
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

object WindowsStorage {
    private fun prefs(context: Context) = context.getSharedPreferences("windows",Context.MODE_PRIVATE)
    fun selection(context: Context): WindowsSelection {
        val p = prefs(context)
        fun <T : Enum<T>> choice(key: String, values: Array<T>, fallback: T) = values.firstOrNull { it.name == p.getString(key,null) } ?: fallback
        return WindowsSelection(choice("version",WindowsVersion.entries.toTypedArray(),WindowsVersion.WINDOWS_11),
            choice("edition",WindowsEdition.entries.toTypedArray(),WindowsEdition.HOME),
            choice("debloat",DebloatProfile.entries.toTypedArray(),DebloatProfile.NONE),
            p.getBoolean("clipchamp",false),p.getBoolean("solitaire",false),p.getBoolean("news",false),p.getBoolean("weather",false),
            choice("storageLayout",StorageLayout.entries.toTypedArray(),StorageLayout.SPLIT),
            p.getInt("systemGiB",128).takeIf { it in setOf(96,128,160,256,512) } ?: 128,p.getBoolean("hideSystemDrive",true))
    }
    fun save(context: Context, selection: WindowsSelection, enabled: Boolean) {
        check(prefs(context).edit().putString("version",selection.version.name).putString("edition",selection.edition.name)
            .putString("debloat",selection.debloat.name).putBoolean("clipchamp",selection.removeClipchamp)
            .putBoolean("solitaire",selection.removeSolitaire).putBoolean("news",selection.removeNews)
            .putBoolean("weather",selection.removeWeather).putString("storageLayout",selection.storageLayout.name)
            .putInt("systemGiB",selection.systemGiB).putBoolean("hideSystemDrive",selection.hideSystemDrive).putBoolean("enabled",enabled).commit())
    }
    fun enabled(context: Context) = prefs(context).getBoolean("enabled",false)
    fun current(context: Context): File? {
        val id = prefs(context).getString("image",null) ?: return null
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(context.filesDir,"windows/$id").takeIf { it.isDirectory }
    }
    fun info(directory: File, verifyHash: Boolean = false): WindowsImageInfo {
        val file = File(directory,"image.wim")
        val manifest = JSONObject(File(directory,"image.json").readText())
        val sha = manifest.getString("sha256"); val bytes = manifest.getLong("bytes")
        require(sha.matches(Regex("[a-f0-9]{64}")) && file.length() == bytes)
        if(verifyHash) require(WindowsImage.hash(file) == sha) { "L'image Windows a changé." }
        return WindowsImageInfo(bytes,sha,WindowsImage.inspect(file))
    }
    fun importImage(context: Context, input: InputStream, iso: Boolean, expectedHash: String, progress: (Long) -> Unit): WindowsImageInfo {
        require(expectedHash.isEmpty() || expectedHash.matches(Regex("[a-fA-F0-9]{64}"))) { "SHA-256 : 64 caractères hexadécimaux attendus." }
        val previous = current(context)
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir,"windows/$id"); check(directory.mkdirs())
        try {
            val source = File(directory,if(iso) "source.iso" else "image.wim")
            source.outputStream().use { output ->
                val buffer = ByteArray(1048576); var count = 0L
                while(true) {
                    if(Thread.currentThread().isInterrupted) error("Import interrompu.")
                    val n = input.read(buffer); if(n < 0) break
                    count += n; require(count <= WindowsImage.MAX_BYTES) { "Fichier trop volumineux (16 Gio maximum)." }
                    require(directory.usableSpace > n + 64L * 1024 * 1024) { "Espace insuffisant sur le téléphone." }
                    output.write(buffer,0,n); progress(count)
                }
            }
            if(expectedHash.isNotEmpty()) require(WindowsImage.hash(source).equals(expectedHash,true)) { "Le fichier ne correspond pas au SHA-256 attendu." }
            val file = File(directory,"image.wim")
            if(iso) {
                require(directory.usableSpace > source.length() + 64L * 1024 * 1024) { "L'extraction nécessite de l'espace libre supplémentaire sur le téléphone." }
                WindowsIso.extract(source,file); check(source.delete())
            }
            val result = WindowsImageInfo(file.length(),WindowsImage.hash(file),WindowsImage.inspect(file))
            File(directory,"image.json").writeText(JSONObject().put("bytes",result.bytes).put("sha256",result.sha256)
                .put("sourceHashChecked",expectedHash.isNotEmpty()).toString())
            check(prefs(context).edit().putString("image",id).commit())
            previous?.deleteRecursively()
            return result
        } catch(e: Exception) { directory.deleteRecursively(); throw e }
    }
    fun prepareDownloadedIso(context: Context, source: File, selection: WindowsSelection, cancelled: () -> Boolean): WindowsImageInfo {
        val previous = current(context)
        require(source.length() in 1048576..WindowsImage.MAX_BYTES) { "ISO incomplète." }
        require(source.parentFile!!.usableSpace > source.length() + 67108864) { "Espace insuffisant pour préparer Windows." }
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir,"windows/$id"); check(directory.mkdirs())
        try {
            val staged = File(directory,"source.iso")
            check(source.renameTo(staged)) { "Impossible de préparer l’ISO téléchargée." }
            check(!cancelled()) { "Préparation annulée." }
            val file = File(directory,"image.wim")
            WindowsIso.extract(staged,file)
            check(!cancelled()) { "Préparation annulée." }
            val result = WindowsImageInfo(file.length(),WindowsImage.hash(file),WindowsImage.inspect(file))
            result.selected(selection) // Validate requested version/edition before replacing the current image.
            check(!cancelled()) { "Préparation annulée." }
            File(directory,"image.json").writeText(JSONObject().put("bytes",result.bytes).put("sha256",result.sha256)
                .put("source","Microsoft HTTPS").put("sourceHashChecked",false).toString())
            check(staged.delete())
            check(prefs(context).edit().putString("image",id).putBoolean("enabled",false).commit())
            previous?.deleteRecursively()
            return result
        } catch(e: Exception) { directory.deleteRecursively(); throw e }
    }
    fun plan(selection: WindowsSelection, info: WindowsImageInfo): ByteArray {
        val image = info.selected(selection)
        return JSONObject().put("schema",2).put("enabled",true).put("version",selection.version.name)
            .put("editionId",selection.edition.editionId).put("index",image.index).put("bytes",info.bytes).put("sha256",info.sha256)
            .put("debloat",selection.debloat.name).put("removeClipchamp",selection.removeClipchamp).put("removeSolitaire",selection.removeSolitaire)
            .put("removeNews",selection.removeNews).put("removeWeather",selection.removeWeather)
            .put("storageLayout",selection.storageLayout.name).put("systemGiB",selection.systemGiB)
            .put("hideSystemDrive",selection.storageLayout == StorageLayout.SPLIT && selection.hideSystemDrive).toString().toByteArray(Charsets.UTF_8)
    }
}
