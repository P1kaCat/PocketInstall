package app.pocketinstall.server

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

class GithubLoginRequired : Exception("Connexion GitHub nécessaire pour accéder au dépôt privé")
object GithubBundle {
    const val URL = "https://github.com/P1kaCat/PocketInstall/releases/download/v3.2.0/PocketInstall-WinPE-x64.zip"
    const val SHA256 = "c2efce1840b197c08718b3ef76d0c43f2092f385a0f5e2f0db8aa7dd5ede2920"
    fun allowed(url: String): Boolean = runCatching {
        val uri=URI(url)
        uri.scheme=="https" && uri.userInfo==null && (uri.port==-1 || uri.port==443) && uri.host in setOf("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")
    }.getOrDefault(false)
    fun download(target: File, cookie: String, cancelled: () -> Boolean, connection: (HttpURLConnection)->Unit, progress: (Long,Long)->Unit) {
        var url=URL
        repeat(8) {
            require(allowed(url)) { "Adresse GitHub refusée" }
            val conn=java.net.URL(url).openConnection() as HttpURLConnection
            connection(conn); conn.instanceFollowRedirects=false;conn.connectTimeout=15000;conn.readTimeout=20000
            conn.setRequestProperty("User-Agent","PocketInstall/3.4.0")
            if(URI(url).host=="github.com" && cookie.isNotBlank())conn.setRequestProperty("Cookie",cookie)
            try {
                if(cancelled())throw InterruptedException()
                val status=conn.responseCode
                if(status in setOf(401,403,404))throw GithubLoginRequired()
                if(status in setOf(301,302,303,307,308)) {
                    url=URI(url).resolve(conn.getHeaderField("Location") ?: error("Redirection absente")).toString()
                    if(URI(url).host=="github.com" && URI(url).path.startsWith("/login"))throw GithubLoginRequired()
                } else {
                    require(status==200) { "GitHub : HTTP $status" }
                    val total=conn.contentLengthLong
                    require(total in 1..1073741824) { "Taille du ZIP absente ou invalide" }
                    require(target.parentFile!!.usableSpace>total+800L*1024*1024) { "Espace insuffisant sur le téléphone" }
                    conn.inputStream.use { input -> target.outputStream().use { output ->
                        val buffer=ByteArray(65536); var count=0L
                        while(true) {
                            if(cancelled())throw InterruptedException()
                            val n=input.read(buffer);if(n<0)break
                            count+=n;require(count<=total) { "ZIP trop volumineux" };output.write(buffer,0,n);progress(count,total)
                        }
                        require(count==total) { "ZIP incomplet" }
                    } }
                    target.inputStream().use { require(it.read()==0x50 && it.read()==0x4b) { "GitHub n’a pas fourni un ZIP" } }
                    require(LinuxInstaller.digest(target)==SHA256) { "L’empreinte du ZIP GitHub ne correspond pas à la release officielle" }
                    return
                }
            } finally { conn.disconnect() }
        }
        error("Trop de redirections GitHub")
    }
}
