package app.pocketinstall

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pocketinstall.server.WinPeHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

@Composable
fun WinPePanel(state: ServerSnapshot, canStart: Boolean, onStart: () -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Vérification de l'environnement…") }
    val download by WindowsDownloadStore.state.collectAsState()
    val busy = importing || state.importingWinPe || download.active
    val active = state.status == ServerStatus.RUNNING || state.status == ServerStatus.STARTING
    LaunchedEffect(Unit) {
        importing = true; onBusy(true)
        try {
            val directory = withContext(Dispatchers.IO) {
                WinPeStorage.current(context)?.also { WinPeStorage.verify(it); WinPeHttp.preflight(it) }
            }
            ready = directory != null
            message = if (ready) "Environnement validé · fichiers et routes HTTP vérifiés." else "Importe PocketInstall-WinPE-x64.zip fourni dans la release."
        } catch (e: Exception) { ready = false; message = "Environnement indisponible : ${e.message}" }
        finally { importing = false; onBusy(false) }
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
            finally { importing = false; onBusy(false) }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && state.ip != "—") scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val config = WinPeHttp.freeboxConfig(InetAddress.getByName(state.ip) as Inet4Address)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(config) } ?: error("Destination inaccessible.")
                }
                message = "pocketinstall.ipxe enregistré. Dépose-le avec le nouveau snponly.efi dans le dossier TFTP Freebox."
            } catch (e: Exception) { message = "Export impossible : ${e.message}" }
        }
    }
    fun copy(value: String) { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PocketInstall", value)) }
    Card {
        Column(Modifier.padding(18.dp)) {
            Text("Windows PE · démarrage réseau")
            Text(message)
            if (busy) LinearProgressIndicator()
            if (!active) {
                Button(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = !busy) { Text("Importer l'environnement") }
                Button(onClick = onStart, enabled = ready && canStart && !busy) { Text("Démarrer · attendre le PC") }
            }
            if (active && state.winPeMode) {
                Text(if (state.status == ServerStatus.STARTING) "Vérification du serveur…" else state.winPeProgress.stage.label)
                if (state.winPeProgress.peer.isNotEmpty()) Text("PC : ${state.winPeProgress.peer}")
                val progress = state.winPeProgress
                if (progress.wimTotal > 0) {
                    LinearProgressIndicator(progress = { (progress.wimBytes.toFloat() / progress.wimTotal).coerceIn(0f, 1f) })
                    Text("Image : ${progress.wimBytes / 1048576} / ${progress.wimTotal / 1048576} Mio")
                }
                if (progress.error.isNotEmpty()) Text(progress.error)
                Text("Une fois la Freebox configurée, démarre le PC en Ethernet → UEFI PXE IPv4. Le chargement se fait automatiquement.")
                OutlinedButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Masquer la configuration" else "Configuration Freebox · une seule fois") }
                if (advanced && state.status == ServerStatus.RUNNING) {
                    Text("1. Réserve ${state.ip} pour ce téléphone dans les baux DHCP Freebox. Si l'IP change, exporte à nouveau la configuration.")
                    Button(onClick = { export.launch("pocketinstall.ipxe") }) { Text("Enregistrer la configuration Freebox") }
                    Text("2. Télécharge le nouveau snponly.efi depuis la release. Dépose snponly.efi et pocketinstall.ipxe dans le dossier racine TFTP de la Freebox. Remplace l'ancien chargeur même s'il porte le même nom.")
                    Text("3. Active TFTP sur ce dossier. Dans DHCP, Serveur TFTP = IP de la Freebox ; Fichier de démarrage = snponly.efi. Applique. La configuration de la box reste manuelle.")
                    Text("Chargeur de ce bundle : ${state.loaderUrl}")
                    Button(onClick = { copy(state.loaderUrl) }) { Text("Copier l'URL du chargeur") }
                    Text("Debug : dhcp\nchain ${state.url}")
                    Button(onClick = { copy("dhcp\nchain ${state.url}") }) { Text("Copier les commandes de diagnostic") }
                }
                Text("Le transfert seul ne prouve pas le boot. Le succès doit apparaître dans WinPE sur le PC. Si l’installation Windows est activée, le disque et son effacement seront confirmés sur le PC.")
            }
        }
    }
}

