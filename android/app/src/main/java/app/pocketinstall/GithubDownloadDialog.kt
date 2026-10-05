package app.pocketinstall

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URI

/** Authentication stays on GitHub's page; only its session cookie reaches the local downloader. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GithubDownloadDialog(close: () -> Unit, authenticated: (String)->Unit) {
    val context=LocalContext.current
    var delivered by remember {mutableStateOf(false)}
    val view=remember {
        WebView(context).apply {
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object: WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean {
                    if(!request.isForMainFrame)return false
                    val uri=runCatching {URI(request.url.toString())}.getOrNull()
                    return uri?.scheme!="https" || uri.host!="github.com"
                }
                override fun onPageFinished(view: WebView,url: String) {
                    if(!delivered && url=="https://github.com/P1kaCat/PocketInstall/releases/tag/v3.2.0") {
                        // A 404 page for an unauthorized account must not trigger a login loop.
                        view.evaluateJavascript("document.title") { title ->
                            if(!delivered && !title.contains("Page not found",ignoreCase=true)) {
                                delivered=true; authenticated(CookieManager.getInstance().getCookie("https://github.com").orEmpty())
                            }
                        }
                    }
                }
            }
            loadUrl("https://github.com/login?return_to=%2FP1kaCat%2FPocketInstall%2Freleases%2Ftag%2Fv3.2.0")
        }
    }
    DisposableEffect(view) { onDispose {delivered=true;view.stopLoading();view.destroy()} }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Connexion GitHub",style=MaterialTheme.typography.titleLarge)
                Text("Connecte le compte ayant accès à PocketInstall. Le téléchargement et l’import reprendront automatiquement.")
                AndroidView(factory={view},modifier=Modifier.fillMaxWidth().weight(1f))
                TextButton(onClick=close){Text("Annuler")}
            }
        }
    }
}
