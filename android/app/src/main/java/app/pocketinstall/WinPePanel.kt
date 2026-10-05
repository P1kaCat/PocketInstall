package app.pocketinstall

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketinstall.server.WinPeHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WinPePanel(state: ServerSnapshot, onBusy: (Boolean) -> Unit, onReady: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Vérification de l'environnement…") }
    val download by WindowsDownloadStore.state.collectAsStateWithLifecycle()
    val winpeDownload by WinPeDownloadStore.state.collectAsStateWithLifecycle()
    val busy = winpeDownload.active || importing || state.importingWinPe || download.active
    val active = state.status == ServerStatus.RUNNING || state.status == ServerStatus.STARTING
    LaunchedEffect(winpeDownload.prepared) {
        importing = true; onBusy(true)
        try {
            val directory = withContext(Dispatchers.IO) {
                WinPeStorage.current(context)?.also { WinPeStorage.verify(it); WinPeHttp.preflight(it) }
            }
            ready = directory != null
            message = if (ready) "Environnement validé · fichiers et routes HTTP vérifiés." else "Télécharge WinPE automatiquement, ou importe ton ZIP."
        } catch (e: Exception) { ready = false; message = "Environnement indisponible : ${e.message}" }
        finally { importing = false; onBusy(false); onReady(ready) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !active && !busy) scope.launch {
            importing = true; onBusy(true); ready = false; message = "Import, vérification des fichiers et test des routes HTTP…"
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { WinPeStorage.importBundle(context, it) }
                        ?: error("Fichier inaccessible.")
                }
                ready = true; message = "Environnement validé. Appuie sur Démarrer pour attendre le PC."
            } catch (e: Exception) {
                ready = withContext(Dispatchers.IO) {
                    runCatching {
                        WinPeStorage.current(context)?.also { WinPeStorage.verify(it); WinPeHttp.preflight(it) } != null
                    }.getOrDefault(false)
                }
                message = "Import refusé : ${e.message ?: e.javaClass.simpleName}" +
                    if (ready) "\nL'environnement précédent reste disponible." else ""
            }
            finally { importing = false; onBusy(false); onReady(ready) }
        }
    }
    if(winpeDownload.loginRequired) GithubDownloadDialog(close={WinPeDownloadService.dismissLogin()}) { cookie -> WinPeDownloadService.start(context,cookie) }
    PocketSection("Environnement PC", "WinPE démarre le PC et prépare l’installation. Le bouton Télécharger récupère le ZIP officiel sur GitHub puis l’importe automatiquement. Si le dépôt est privé, connecte ton compte GitHub ayant accès. Les fichiers, leur intégrité et les routes HTTP sont vérifiés avant de rendre l’environnement disponible. L’ISO Windows est préparée séparément, dans la section Windows.") {
        if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Vérification en cours…") }
        else Text(if(ready) "WinPE disponible" else "WinPE à importer",style=androidx.compose.material3.MaterialTheme.typography.titleMedium)
        if(message.contains("refusé") || message.contains("indisponible") || message.startsWith("Export impossible")) PocketNote(message,error=true)
        if(winpeDownload.active) {
            Text(winpeDownload.message,style=androidx.compose.material3.MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={WinPeDownloadService.cancel(context)}){Text("Annuler le téléchargement")}
        } else if(winpeDownload.message.isNotEmpty()) Text(winpeDownload.message,style=androidx.compose.material3.MaterialTheme.typography.bodySmall)
        if(!active) Button(onClick={WinPeDownloadService.start(context)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(ready) "Actualiser WinPE depuis GitHub" else "Télécharger WinPE depuis GitHub")}
        if(!active) OutlinedButton(onClick={picker.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed"))},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
            Text(if(ready) "Remplacer le ZIP WinPE" else "Importer le ZIP WinPE")
        }
    }
}
