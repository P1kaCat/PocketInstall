package app.pocketinstall.server

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Only official HTTPS media hosts; never accept a link supplied by an unrelated page. */
object MicrosoftIso {
    private val hosts = setOf("software.download.prss.microsoft.com", "software-static.download.prss.microsoft.com", "software-download.microsoft.com", "download.microsoft.com")
    fun validUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host?.lowercase() in hosts && uri.userInfo == null &&
            uri.port in setOf(-1,443) && uri.path.endsWith(".iso",true) && uri.fragment == null
    }.getOrDefault(false)

    fun download(url: String, destination: File, userAgent: String, cancelled: () -> Boolean,
                 connection: (HttpURLConnection?) -> Unit, progress: (Long,Long) -> Unit,
                 approvedBytes: Long? = null,
                 open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
        var address = url
        var redirects = 0
        while(true) {
            require(validUrl(address)) { "Lien ISO Microsoft HTTPS invalide." }
            if(cancelled()) throw InterruptedException("Téléchargement annulé.")
            val http = open(URL(address))
            connection(http)
            try {
                http.instanceFollowRedirects = false
                http.connectTimeout = 20000; http.readTimeout = 30000
                http.setRequestProperty("User-Agent",userAgent)
                http.setRequestProperty("Accept-Encoding","identity")
                val status = http.responseCode
                if(status in setOf(301,302,303,307,308)) {
                    check(++redirects <= 5) { "Trop de redirections Microsoft." }
                    address = URI(address).resolve(http.getHeaderField("Location") ?: error("Redirection sans adresse.")).toString()
                    continue
                }
                check(status == 200) { if(status == 403 || status == 401) "Lien Microsoft expiré ou refusé. Relance le téléchargement pour obtenir un nouveau lien." else "Microsoft répond HTTP $status." }
                val total = http.contentLengthLong
                DownloadMetadata.checkApproved(total, approvedBytes)
                require(total in 1048576..WindowsImage.MAX_BYTES) { "La réponse ne contient pas une ISO de taille valide." }
                require(destination.parentFile!!.usableSpace > total * 2 + 67108864) { "Espace insuffisant : prévois deux fois la taille de l'ISO pour télécharger puis préparer Windows." }
                http.inputStream.use { input -> destination.outputStream().use { output ->
                    val buffer = ByteArray(1048576); var bytes = 0L
                    while(true) {
                        if(cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException("Téléchargement annulé.")
                        val n = input.read(buffer); if(n < 0) break
                        bytes += n
                        require(bytes <= total) { "La taille du téléchargement a changé." }
                        require(destination.parentFile!!.usableSpace > n + 67108864) { "Espace libre insuffisant." }
                        output.write(buffer,0,n); progress(bytes,total)
                    }
                    check(bytes == total) { "Téléchargement incomplet. Relance le téléchargement." }
                } }
                return
            } finally { http.disconnect(); connection(null) }
        }
    }
}
