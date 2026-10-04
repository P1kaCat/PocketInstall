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
import app.pocketinstall.server.MicrosoftIso
import app.pocketinstall.server.WindowsVersion
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI

/** Use Microsoft's own browser session to generate a fresh, short-lived download link. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MicrosoftDownloadDialog(version: WindowsVersion, language: String, close: () -> Unit, resolved: (String,String) -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("Microsoft prépare le lien de téléchargement…") }
    var running by remember { mutableStateOf(true) }
    var delivered by remember { mutableStateOf(false) }
    val view = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            fun receive(url: String) {
                if(!delivered && MicrosoftIso.validUrl(url)) {
                    delivered = true; resolved(url,settings.userAgentString)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if(!request.isForMainFrame) return false
                    val url = request.url.toString()
                    if(MicrosoftIso.validUrl(url)) { receive(url); return true }
                    val uri = runCatching { URI(url) }.getOrNull()
                    return uri?.scheme != "https" || uri?.host !in setOf("www.microsoft.com","microsoft.com")
                }
            }
            setDownloadListener { url,_,_,_,_ -> receive(url) }
            loadUrl("https://www.microsoft.com/en-us/software-download/" + if(version == WindowsVersion.WINDOWS_11) "windows11" else "windows10ISO")
        }
    }
    DisposableEffect(view) { onDispose { running=false; delivered=true; view.stopLoading(); view.destroy() } }
    val windows = if(version == WindowsVersion.WINDOWS_11) "Windows 11" else "Windows 10"
    val script = remember(version,language) { """
        (function() {
          const s = window.__pocketInstallDownload || (window.__pocketInstallDownload = {});
          const lang = ${JSONObject.quote(language)};
          const windows = ${JSONObject.quote(windows)};
          const links = Array.from(document.querySelectorAll('a[href]'));
          const link = links.find(a => { try { const u = new URL(a.href); return /\.iso$/i.test(u.pathname) && !/arm64|arm_64|x86FRE/i.test(u.pathname) && /64|x64/i.test((a.textContent||'')+' '+u.pathname); } catch(e) { return false; } });
          if(link) return JSON.stringify({url:link.href});
          const languages = document.querySelector('#product-languages');
          if(languages && languages.options.length > 1) {
            const choice = Array.from(languages.options).find(o => o.textContent.trim() === lang || o.textContent.trim() === lang + ' (United States)');
            if(!choice) return JSON.stringify({message:'Cette langue n’est pas proposée. Choisis-la sur la page Microsoft.'});
            if(!s.language) { languages.value = choice.value; languages.dispatchEvent(new Event('change',{bubbles:true})); const b = document.querySelector('#submit-sku'); if(b) { s.language = true; b.click(); } }
            return JSON.stringify({message:'Microsoft génère le lien ISO…'});
          }
          const editions = document.querySelector('#product-edition');
          if(editions && editions.options.length > 1 && !s.edition) {
            const choice = Array.from(editions.options).find(o => o.textContent.includes(windows) && !/ARM|China/i.test(o.textContent));
            if(choice) { editions.value = choice.value; editions.dispatchEvent(new Event('change',{bubbles:true})); const b = document.querySelector('#submit-product-edition'); if(b) { s.edition = true; b.click(); } }
          }
          return JSON.stringify({message:'Microsoft prépare le téléchargement. Tu peux aussi utiliser les boutons de cette page.'});
        })();
    """.trimIndent() }
    LaunchedEffect(view) {
        repeat(120) {
            if(!running || delivered) return@LaunchedEffect
            view.evaluateJavascript(script) { raw ->
                runCatching {
                    val decoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching
                    val value = JSONObject(decoded)
                    val url = value.optString("url")
                    if(MicrosoftIso.validUrl(url) && !delivered) { delivered = true; resolved(url,view.settings.userAgentString) }
                    else value.optString("message").takeIf { it.isNotBlank() }?.let { message=it }
                }
            }
            delay(1000)
        }
        running = false
        message = "Microsoft n’a pas fourni de lien automatique. Utilise la page ci-dessous pour choisir l’ISO x64, ou annule et importe une ISO officielle."
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Téléchargement Microsoft",style=MaterialTheme.typography.titleLarge)
                Text(message)
                if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(factory={view},modifier=Modifier.fillMaxWidth().weight(1f))
                TextButton(onClick=close) { Text("Annuler") }
            }
        }
    }
}
