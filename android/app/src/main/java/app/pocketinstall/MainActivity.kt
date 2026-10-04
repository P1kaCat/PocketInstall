package app.pocketinstall

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketinstall.server.RequestPhase
import kotlinx.coroutines.flow.update

class MainActivity : ComponentActivity() {
    private var pendingNetwork = ""
    private var pendingUsb = false
    private var pendingPxe = false
    private var pendingWinPe = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Denied notifications do not prevent a foreground service; Android may
        // show it only in the active-apps/task manager surface.
        start(pendingNetwork, pendingUsb, pendingPxe)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff67d9ee),
                background = Color(0xff08141f), surface = Color(0xff112537))) {
                val state by ServerStore.state.collectAsStateWithLifecycle()
                var usbMode by remember { mutableStateOf(state.usbMode) }
                var pxeMode by remember { mutableStateOf(state.pxeMode) }
                var networks by remember { mutableStateOf(LanNetwork.candidates(this, usbMode)) }
                var chosen by remember { mutableStateOf(networks.firstOrNull()?.id ?: "") }
                DisposableEffect(lifecycle, usbMode) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            networks = LanNetwork.candidates(this@MainActivity, usbMode)
                            if (networks.none { it.id == chosen }) chosen = networks.firstOrNull()?.id ?: ""
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(state.status) {
                    if (state.status == ServerStatus.RUNNING) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                PocketScreen(state, networks, chosen, usbMode, pxeMode,
                    { usbMode = it; if (it) pxeMode = false; networks = LanNetwork.candidates(this, it); chosen = networks.firstOrNull()?.id ?: "" },
                    { pxeMode = it },
                    { chosen = it },
                    { networks = LanNetwork.candidates(this, usbMode); chosen = networks.firstOrNull()?.id ?: "" },
                    { pendingNetwork = chosen; pendingUsb = usbMode; pendingPxe = pxeMode; pendingWinPe = false; requestStart() },
                    { startService(Intent(this, PocketInstallService::class.java).setAction(PocketInstallService.ACTION_STOP)) },
                    { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PocketInstall Boot URL", state.url)) },
                    { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PocketInstall PXE relay",
                        "python3 scripts/prepare_pxe_relay.py --boot-url '${state.url}' --relay-ip IP_DU_RELAIS --interface INTERFACE_ETHERNET --target-mac MAC_DU_PC --output pxe-relay")) },
                    { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                        .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) } },
                    { pendingNetwork = chosen; pendingUsb = false; pendingPxe = false; pendingWinPe = true; requestStart() })
            }
        }
    }
    private fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else start(pendingNetwork, pendingUsb, pendingPxe)
    }
    private fun start(candidate: String, usb: Boolean, pxe: Boolean) {
        try {
            ContextCompat.startForegroundService(this, Intent(this, PocketInstallService::class.java)
                .setAction(PocketInstallService.ACTION_START).putExtra(PocketInstallService.EXTRA_CANDIDATE, candidate)
                .putExtra(PocketInstallService.EXTRA_USB, usb).putExtra(PocketInstallService.EXTRA_PXE, pxe).putExtra(PocketInstallService.EXTRA_WINPE, pendingWinPe))
        } catch (e: Exception) {
            ServerStore.mutable.update { it.copy(status = ServerStatus.ERROR, message = "Démarrage refusé : ${e.javaClass.simpleName}.") }
        }
    }
}

@Composable
private fun PocketScreen(state: ServerSnapshot, networks: List<LanCandidate>, chosen: String, selectedUsb: Boolean, selectedPxe: Boolean,
    onMode: (Boolean) -> Unit, onPxe: (Boolean) -> Unit,
    onChoose: (String) -> Unit, onRefresh: () -> Unit, onStart: () -> Unit, onStop: () -> Unit, onCopy: () -> Unit,
    onCopyRelay: () -> Unit, onSettings: () -> Unit, onWinPe: () -> Unit) {
    var licenseOpen by remember { mutableStateOf(false) }
    var debugOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val licenseText = remember(context) {
        context.assets.open("licenses/PocketInstall-Personal.txt").bufferedReader().use { it.readText() }
    }
    if (licenseOpen) {
        AlertDialog(
            onDismissRequest = { licenseOpen = false },
            title = { Text("Licence PocketInstall") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(licenseText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { licenseOpen = false }) { Text("Fermer") } }
        )
    }
    val active = state.status == ServerStatus.RUNNING || state.status == ServerStatus.STARTING
    val usbMode = if (active) state.usbMode else selectedUsb
    val pxeMode = if (active) state.pxeMode else selectedPxe
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            run {
                Text("PocketInstall", style = MaterialTheme.typography.headlineLarge)
                Text("Ton téléphone, le point de départ du recovery.", color = MaterialTheme.colorScheme.primary)
            }
            run {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Serveur : ${when (state.status) { ServerStatus.STOPPED -> "arrêté"; ServerStatus.STARTING -> "vérification"; ServerStatus.RUNNING -> "démarré"; ServerStatus.ERROR -> "erreur" }}", style = MaterialTheme.typography.titleMedium)
                        Text(state.message)
                        if (state.status == ServerStatus.RUNNING && (!state.winPeMode || debugOpen)) {
                            Text("IP : ${state.ip}", fontFamily = FontFamily.Monospace)
                            Text(if (state.winPeMode) "Script de chargement WinPE" else if (pxeMode) "URL du fichier pour le relais HTTP" else "Boot URL", style = MaterialTheme.typography.labelLarge)
                            Text(state.url, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            Button(onClick = onCopy) { Text("Copier l'URL") }
                            Text("LAN : ${state.subnet} · Arrêt automatique après 30 min")
                        }
                    }
                }
            }
            if (state.status == ServerStatus.RUNNING && pxeMode) {
                run {
                    Card {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PXE · configuration réseau requise", style = MaterialTheme.typography.titleMedium)
                            Text("TFTP : ${state.ip}:${state.tftpPort}/UDP", fontFamily = FontFamily.Monospace)
                            Text(state.tftpMessage)
                            Text("Fichier de boot : ${state.bootFilename}", fontFamily = FontFamily.Monospace)
                            if (state.tftpPort == 69) {
                                Text("Sur un DHCP configurable : next-server / option 66 = ${state.ip} ; fichier / option 67 = ${state.bootFilename}. Réserve l'IP du téléphone et cible le PC UEFI x64.")
                            }
                            Text("Si la box ne propose pas ces réglages, il faut un relais PXE sur un autre appareil. L'appli ne configure pas la box et n'attribue aucune adresse IP.")
                            Text("Relais Linux : télécharger le dépôt, remplir IP_DU_RELAIS, INTERFACE_ETHERNET et MAC_DU_PC dans la commande copiée, puis suivre docs/PXE.md.")
                            OutlinedButton(onClick = onCopyRelay) { Text("Copier la commande du relais") }
                            Text("PC en Ethernet · UEFI PXE IPv4 · EFI non signé · aucun disque modifié.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (!active) {
                run {
                    Text("Connexion", style = MaterialTheme.typography.titleMedium)
                    Row {
                        RadioButton(selected = !usbMode, onClick = { onMode(false) })
                        Text("LAN · Wi-Fi / Ethernet", Modifier.padding(top = 12.dp))
                    }
                    Row {
                        RadioButton(selected = usbMode, onClick = { onMode(true) })
                        Text("Câble USB · expérimental", Modifier.padding(top = 12.dp))
                    }
                    if (debugOpen) {
                    if (usbMode) {
                        Text("Branche un câble USB de données puis active le partage de connexion USB dans les paramètres Android. Reviens ici et actualise.")
                        Text("Le PC doit reconnaître ce réseau USB dans son UEFI et proposer HTTP Boot dessus. MTP et la recharge ne suffisent pas. Ce mode ne transforme pas le téléphone en clé USB bootable.",
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onSettings) { Text("Ouvrir les paramètres réseau") }
                    }
                    if (!usbMode) {
                        Text("Démarrage", style = MaterialTheme.typography.titleMedium)
                        Row {
                            RadioButton(selected = !pxeMode, onClick = { onPxe(false) })
                            Text("HTTP Boot · URL dans l'UEFI", Modifier.padding(top = 12.dp))
                        }
                        Row {
                            RadioButton(selected = pxeMode, onClick = { onPxe(true) })
                            Text("PXE IPv4 · Ethernet · expérimental", Modifier.padding(top = 12.dp))
                        }
                        if (pxeMode) Text("Pour un PC sans HTTP Boot : TFTP charge directement l'EFI du test. Un DHCP configurable ou un relais externe reste nécessaire. Android peut bloquer le port UDP 69.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (usbMode) "Interface USB privée" else "Réseau local", style = MaterialTheme.typography.titleMedium)
                    }
                    if (networks.isEmpty()) Text(if (usbMode)
                        "Aucune interface USB compatible visible. Active le partage USB puis actualise ; certains téléphones ne l'exposent pas à l'application."
                        else "Aucune IPv4 LAN privée. Rejoins le même réseau local que le PC, puis actualise.")
                    networks.forEach { candidate ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(selected = candidate.id == chosen,
                                onClick = { onChoose(candidate.id) })
                            Text("${candidate.label}\n${candidate.address.hostAddress}/${candidate.prefix}", Modifier.padding(top = 6.dp))
                        }
                    }
                    OutlinedButton(onClick = onRefresh) { Text("Actualiser les réseaux") }
                }
            }
            WindowsPanel(state) { busy -> ServerStore.mutable.update { it.copy(importingWinPe = busy) } }
            run { WinPePanel(state, !selectedUsb && networks.any { it.id == chosen }, onWinPe) { busy -> ServerStore.mutable.update { it.copy(importingWinPe = busy) } } }
            run {
                if (active) Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Arrêter le serveur") }
                else if (debugOpen) Button(onClick = onStart, enabled = !state.importingWinPe && networks.any { it.id == chosen },
                    modifier = Modifier.fillMaxWidth()) { Text("Démarrer le test EFI") }
            }
            run { OutlinedButton(onClick = { debugOpen = !debugOpen }) { Text(if (debugOpen) "Masquer le diagnostic" else "Diagnostic avancé") } }
            if (debugOpen) {
            run {
                Text("IP clientes vues (5 min) : ${state.clientsSeen}\nRequêtes HTTP : ${state.requests}")
                Text("Un téléchargement ne prouve pas le boot. Le message de succès doit apparaître sur le PC.",
                    style = MaterialTheme.typography.bodySmall)
            }
            state.tftpEvents.reversed().forEach { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("TFTP ${event.resource} · ${event.result}", fontFamily = FontFamily.Monospace)
                        Text("${event.peer} · ${event.acknowledgedBytes}/${event.expectedBytes} octets acquittés",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.events.reversed().forEach { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${event.method} ${event.resource} · ${event.status}", fontFamily = FontFamily.Monospace)
                        Text("${event.peer} · ${if (event.phase == RequestPhase.STARTED) "Transfert demandé" else "Requête terminée"} · ${event.sentBytes}/${event.expectedBytes} octets",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            }
            run {
                Text(if (state.winPeMode) "Validation WinPE" else "Premier test", style = MaterialTheme.typography.titleMedium)
                Text(if (state.winPeMode) "Suivre la carte Windows PE ci-dessus. Le succès doit apparaître dans la console WinPE du PC. Les transferts HTTP seuls ne prouvent pas le démarrage." else if (pxeMode) "1. Téléphone sur le LAN et PC en Ethernet.\n2. Choisir UEFI PXE IPv4, pas Legacy PXE.\n3. Configurer DHCP/TFTP ou préparer le relais Linux.\n4. Adapter Secure Boot au test EFI non signé.\n5. Lire le succès sur le PC, puis arrêt automatique."
                    else if (usbMode) "1. Câble USB de données.\n2. Partage USB activé dans Android.\n3. Réseau USB reconnu par l’UEFI et HTTP Boot disponible.\n4. Saisir l’URL exacte ; POC non signé.\n5. Lire le succès sur le PC, puis arrêt automatique." else "1. Même LAN pour le PC et le téléphone.\n2. UEFI HTTP Boot, URL manuelle.\n3. POC non signé : politique Secure Boot adaptée.\n4. Saisir l'URL exacte.\n5. Lire le succès sur le PC, puis arrêt automatique.")
                if (!state.winPeMode && !usbMode && !pxeMode) Text("Wi-Fi dans Windows ≠ Wi-Fi dans l'UEFI. Ce PC n'est probablement pas compatible avec Wireless PocketInstall si son firmware n'a pas le réseau Wi-Fi préboot.",
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                if (usbMode) Text("USB dans Windows ≠ USB réseau dans l’UEFI. Si le firmware ne reconnaît pas le partage USB, ce PC n’est probablement pas compatible avec PocketInstall par câble USB.",
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                Text("Choisis Windows et son édition, puis confirme le disque sur le PC. Les tests EFI restent disponibles dans le diagnostic.", Modifier.padding(top = 10.dp))
                Text("Usage personnel et modifications privées autorisés. Redistribution soumise à accord écrit.",
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { licenseOpen = true }) { Text("Lire la licence") }
            }
        }
    }
}

