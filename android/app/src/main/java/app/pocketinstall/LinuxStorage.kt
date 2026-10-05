package app.pocketinstall

import android.content.Context
import app.pocketinstall.server.*
import java.io.File
import java.util.UUID

object LinuxStorage {
    fun current(context: Context): File? {
        val id=context.getSharedPreferences("linux",Context.MODE_PRIVATE).getString("bundle",null) ?: return null
        if(!id.matches(Regex("[a-f0-9-]{36}"))) return null
        return File(context.filesDir,"linux/$id").takeIf { it.isDirectory }
    }
    fun prepare(context: Context, cancelled: () -> Boolean, connection: (java.net.HttpURLConnection) -> Unit,
                progress: (String,Long,Long) -> Unit) {
        val root=File(context.filesDir,"linux").apply { mkdirs() }
        require(root.usableSpace>300L*1024*1024) { "Libère au moins 300 Mio sur le téléphone" }
        val directory=File(root,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            LinuxInstaller.download(directory,cancelled,connection,progress)
            LinuxProfile.entries.forEach { LinuxHttp.preflight(directory,it) }
            if(cancelled()) throw InterruptedException()
            check(context.getSharedPreferences("linux",Context.MODE_PRIVATE).edit().putString("bundle",directory.name).commit())
            root.listFiles()?.filter { it.isDirectory && it!=directory }?.forEach { it.deleteRecursively() }
        } catch(e: Exception) { directory.deleteRecursively(); throw e }
    }
}
