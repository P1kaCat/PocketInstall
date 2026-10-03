package app.pocketinstall

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WinPePanel(state: ServerSnapshot, canStart: Boolean, onStart: () -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(WinPeStorage.current(context) != null) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Importe PocketInstall-WinPE-x64.zip fourni dans la release (maximum 2 Gio).") }
    val busy = importing || state.importingWinPe
    val active = state.status == ServerStatus.RUNNING || state.status == ServerStatus.STARTING
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !active && !busy) scope.launch {
            importing = true; onBusy(true); message = "Import et vérification SHA-256…"
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { WinPeStorage.importBundle(context, it) }
                        ?: error("Fichier inaccessible.")
                }
                ready = true; message = "Bundle WinPE x64 importé. Aucune installation automatique."
            } catch (e: Exception) { message = "Import refusé : ${e.message ?: e.javaClass.simpleName}" }
            finally { importing = false; onBusy(false) }
        }
    }
    Card {
        Column(Modifier.padding(18.dp)) {
            Text("Environnement Windows PE")
            Text(message)
            if (!active) {
                Button(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = !busy) { Text("Importer le bundle WinPE") }
                Button(onClick = onStart, enabled = ready && canStart && !busy) { Text("Démarrer WinPE · Freebox") }
            }
            if (active && state.winPeMode && state.url.isNotEmpty()) {
                Text("1. Télécharger le chargeur ci-dessous et remplacer le fichier de démarrage Freebox par snponly.efi. Serveur TFTP : IP de la Freebox (192.168.0.254 chez toi).")
                Text(state.loaderUrl)
                Button(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("iPXE loader", state.loaderUrl)) }) { Text("Copier l'URL du chargeur") }
                Text("2. PC en Ethernet, UEFI PXE IPv4. Ce chargeur non signé exige une politique Secure Boot compatible. À l'invite iPXE (Ctrl+B si nécessaire), saisir ces deux commandes :")
                Text("dhcp\nchain ${state.url}")
                Button(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("iPXE chain", "chain ${state.url}")) }) { Text("Copier la commande chain") }
                Text("Cette URL change à chaque session. WinPE peut monter les disques. Le script ouvre une console ; il ne lance ni formatage ni installation.")
            }
        }
    }
}
