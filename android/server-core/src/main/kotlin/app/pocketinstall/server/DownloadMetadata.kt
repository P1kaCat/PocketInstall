package app.pocketinstall.server

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** HEAD only: discover the exact payload size without consuming an asset body. */
object DownloadMetadata {
    fun size(url: String, allowed: (String) -> Boolean, maximum: Long, minimum: Long = 1,
             userAgent: String = "PocketInstall/3.4.1", connection: (HttpURLConnection?) -> Unit = {},
             open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): Long {
        var address = url
        repeat(8) {
            require(allowed(address)) { "Source de téléchargement refusée." }
            val http = open(URL(address))
            connection(http)
            try {
                http.requestMethod = "HEAD"
                http.instanceFollowRedirects = false
                http.connectTimeout = 15000; http.readTimeout = 15000
                http.setRequestProperty("User-Agent", userAgent)
                http.setRequestProperty("Accept-Encoding", "identity")
                val status = http.responseCode
                if(status in setOf(301,302,303,307,308)) {
                    address = URI(address).resolve(http.getHeaderField("Location") ?: error("Redirection absente.")).toString()
                } else {
                    check(status == 200) { "La source répond HTTP $status. Aucun fichier n’a été téléchargé." }
                    return http.contentLengthLong.also {
                        require(it in minimum..maximum) { "Taille inconnue ou invalide. Le téléchargement reste bloqué." }
                    }
                }
            } finally { http.disconnect(); connection(null) }
        }
        error("Trop de redirections.")
    }

    fun checkApproved(actual: Long, approved: Long?) {
        if(approved != null) require(approved > 0 && actual == approved) {
            "La taille a changé. Confirme à nouveau le téléchargement."
        }
    }
}
