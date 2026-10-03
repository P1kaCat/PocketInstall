package app.pocketinstall

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    private var pendingNetwork = -1L
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Denied notifications do not prevent a foreground service; Android may
        // show it only in the active-apps/task manager surface.
        start(pendingNetwork)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff67d9ee),
                background = Color(0xff08141f), surface = Color(0xff112537))) {
                val state by ServerStore.state.collectAsStateWithLifecycle()
                var networks by remember { mutableStateOf(LanNetwork.candidates(this)) }
                var chosen by remember { mutableLongStateOf(networks.firstOrNull()?.network?.networkHandle ?: -1L) }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) networks = LanNetwork.candidates(this@MainActivity)
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(state.status) {
                    if (state.status == ServerStatus.RUNNING) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                PocketScreen(state, networks, chosen, { chosen = it },
                    { networks = LanNetwork.candidates(this); chosen = networks.firstOrNull()?.network?.networkHandle ?: -1L },
                    { pendingNetwork = chosen; requestStart() },
                    { startService(Intent(this, PocketInstallService::class.java).setAction(PocketInstallService.ACTION_STOP)) },
                    { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PocketInstall Boot URL", state.url)) })
            }
        }
    }
    private fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else start(pendingNetwork)
    }
    private fun start(handle: Long) {
        try {
            ContextCompat.startForegroundService(this, Intent(this, PocketInstallService::class.java)
                .setAction(PocketInstallService.ACTION_START).putExtra(PocketInstallService.EXTRA_NETWORK, handle))
        } catch (e: Exception) {
            ServerStore.mutable.update { it.copy(status = ServerStatus.ERROR, message = "Démarrage refusé : ${e.javaClass.simpleName}.") }
        }
    }
}

@Composable
private fun PocketScreen(state: ServerSnapshot, networks: List<LanCandidate>, chosen: Long,
    onChoose: (Long) -> Unit, onRefresh: () -> Unit, onStart: () -> Unit, onStop: () -> Unit, onCopy: () -> Unit) {
    val active = state.status == ServerStatus.RUNNING || state.status == ServerStatus.STARTING
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("PocketInstall", style = MaterialTheme.typography.headlineLarge)
                Text("Ton téléphone, le point de départ du recovery.", color = MaterialTheme.colorScheme.primary)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Server: ${state.status}", style = MaterialTheme.typography.titleMedium)
                        Text(state.message)
                        if (state.status == ServerStatus.RUNNING) {
                            Text("IP : ${state.ip}", fontFamily = FontFamily.Monospace)
                            Text("Boot URL", style = MaterialTheme.typography.labelLarge)
                            Text(state.url, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            Button(onClick = onCopy) { Text("Copier l'URL") }
                            Text("LAN : ${state.subnet} · Arrêt automatique après 30 min")
                        }
                    }
                }
            }
            if (!active) {
                item {
                    Text("Réseau local", style = MaterialTheme.typography.titleMedium)
                    if (networks.isEmpty()) Text("Aucune IPv4 LAN privée. Connecte le téléphone au Wi-Fi du PC, puis actualise.")
                    networks.forEach { candidate ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(selected = candidate.network.networkHandle == chosen,
                                onClick = { onChoose(candidate.network.networkHandle) })
                            Text("${candidate.label}\n${candidate.address.hostAddress}/${candidate.prefix}", Modifier.padding(top = 6.dp))
                        }
                    }
                    OutlinedButton(onClick = onRefresh) { Text("Actualiser les réseaux") }
                }
            }
            item {
                if (active) Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Arrêter le serveur") }
                else Button(onClick = onStart, enabled = networks.any { it.network.networkHandle == chosen },
                    modifier = Modifier.fillMaxWidth()) { Text("Démarrer le test EFI") }
            }
            item {
                Text("IP clientes vues (5 min) : ${state.clientsSeen}\nRequêtes HTTP : ${state.requests}")
                Text("Un téléchargement ne prouve pas le boot. Le message de succès doit apparaître sur le PC.",
                    style = MaterialTheme.typography.bodySmall)
            }
            items(state.events.reversed(), key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${event.method} ${event.resource} · ${event.status}", fontFamily = FontFamily.Monospace)
                        Text("${event.peer} · ${if (event.phase == RequestPhase.STARTED) "Transfert demandé" else "Requête terminée"} · ${event.sentBytes}/${event.expectedBytes} octets",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("Premier test", style = MaterialTheme.typography.titleMedium)
                Text("1. Même LAN pour le PC et le téléphone.\n2. UEFI HTTP Boot, URL manuelle.\n3. POC non signé : politique Secure Boot adaptée.\n4. Saisir l'URL exacte.\n5. Lire le succès sur le PC, puis arrêt automatique.")
                Text("Wi-Fi dans Windows ≠ Wi-Fi dans l'UEFI. Ce PC n'est probablement pas compatible avec Wireless PocketInstall si son firmware n'a pas le réseau Wi-Fi préboot.",
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                Text("POC EFI uniquement · aucune installation Windows pour l'instant.", Modifier.padding(top = 10.dp))
            }
        }
    }
}
