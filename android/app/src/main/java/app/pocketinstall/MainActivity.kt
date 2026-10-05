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
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import app.pocketinstall.server.WinPeStage
import app.pocketinstall.server.WinPeHttp
import androidx.activity.compose.rememberLauncherForActivityResult
import java.net.InetAddress
import java.net.Inet4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
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
    private var pendingLinux: String? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Denied notifications do not prevent a foreground service; Android may
        // show it only in the active-apps/task manager surface.
        start(pendingNetwork, pendingUsb, pendingPxe)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketTheme {
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
                    { pendingNetwork = chosen; pendingUsb = usbMode; pendingPxe = pxeMode; pendingWinPe = false; pendingLinux = null; requestStart() },
                    { startService(Intent(this, PocketInstallService::class.java).setAction(PocketInstallService.ACTION_STOP)) },
                    { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PocketInstall Boot URL", state.url)) },
                    { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PocketInstall PXE relay",
                        "python3 scripts/prepare_pxe_relay.py --boot-url '${state.url}' --relay-ip IP_DU_RELAIS --interface INTERFACE_ETHERNET --target-mac MAC_DU_PC --output pxe-relay")) },
                    { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                        .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) } },
                    { choice -> pendingNetwork = chosen; pendingUsb = false; pendingPxe = false; pendingWinPe = choice == InstallerChoice.WINDOWS; pendingLinux = choice.linux?.name; requestStart() })
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
                .putExtra(PocketInstallService.EXTRA_USB, usb).putExtra(PocketInstallService.EXTRA_PXE, pxe).putExtra(PocketInstallService.EXTRA_WINPE, pendingWinPe).putExtra(PocketInstallService.EXTRA_LINUX, pendingLinux))
        } catch (e: Exception) {
            ServerStore.mutable.update { it.copy(status = ServerStatus.ERROR, message = "Démarrage refusé : ${e.javaClass.simpleName}.") }
        }
    }
}

@Composable
private fun PocketScreen(state: ServerSnapshot, networks: List<LanCandidate>, chosen: String, selectedUsb: Boolean, selectedPxe: Boolean,
    onMode: (Boolean) -> Unit, onPxe: (Boolean) -> Unit, onChoose: (String) -> Unit, onRefresh: () -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onCopy: () -> Unit, onCopyRelay: () -> Unit, onSettings: () -> Unit, onWinPe: (InstallerChoice) -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val download by WindowsDownloadStore.state.collectAsStateWithLifecycle()
    val winpeDownload by WinPeDownloadStore.state.collectAsStateWithLifecycle()
    var libraryRevision by remember {mutableStateOf(0)}
    val linuxDownload by LinuxDownloadStore.state.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf(runCatching { InstallerChoice.valueOf(context.getSharedPreferences("installer",Context.MODE_PRIVATE).getString("choice","WINDOWS")!!) }.getOrDefault(InstallerChoice.WINDOWS)) }
    var linuxReady by remember { mutableStateOf(false) }
    var linuxVerifying by remember { mutableStateOf(false) }
    LaunchedEffect(linuxDownload.prepared,libraryRevision) {
        linuxVerifying=true
        linuxReady=withContext(Dispatchers.IO) { runCatching {
            val directory=LinuxStorage.current(context) ?: return@runCatching false
            app.pocketinstall.server.LinuxInstaller.verify(directory)
            app.pocketinstall.server.LinuxProfile.entries.forEach { app.pocketinstall.server.LinuxHttp.preflight(directory,it) }
            true
        }.getOrDefault(false) }
        linuxVerifying=false
    }
    var tab by rememberSaveable { mutableStateOf(if(state.status==ServerStatus.RUNNING) 1 else 0) }
    var ready by rememberSaveable { mutableStateOf(false) }
    var diagnostic by rememberSaveable { mutableStateOf(false) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var licenseOpen by rememberSaveable { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    val active=state.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING)
    val busy=state.importingWinPe || download.active || linuxDownload.active || winpeDownload.active || linuxVerifying
    val installerReady=if(choice.linux!=null)linuxReady else ready
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null && state.ip!="—") scope.launch {
            exportMessage=runCatching { withContext(Dispatchers.IO) {
                val config=WinPeHttp.freeboxConfig(InetAddress.getByName(state.ip) as Inet4Address)
                context.contentResolver.openOutputStream(uri)?.use {it.write(config)} ?: error("Destination inaccessible")
            }; "Configuration enregistrée" }.getOrElse {"Export impossible : ${it.message}"}
        }
    }
    if(licenseOpen) AlertDialog(onDismissRequest={licenseOpen=false},title={Text("Licence PocketInstall")},
        text={Text(remember {context.assets.open("licenses/PocketInstall-Personal.txt").bufferedReader().use{it.readText()}},Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick={licenseOpen=false}){Text("Fermer")}})
    Scaffold(contentWindowInsets=WindowInsets.safeDrawing,bottomBar={
        NavigationBar {
            listOf("Préparer","Installer","Bibliothèque","Aide").forEachIndexed { index,label ->
                NavigationBarItem(selected=tab==index,onClick={tab=index},enabled=!busy,
                    icon={Icon(painterResource(listOf(R.drawable.ic_prepare,R.drawable.ic_install,R.drawable.ic_library,R.drawable.ic_help)[index]),contentDescription=null)},label={Text(label)})
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=680.dp).fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Text("PocketInstall",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text(when(tab){0->"Choisis ton prochain système.";1->"Connecte le PC. On s’occupe du reste.";2->"Tes systèmes, à portée de main.";else->"Un coup de main, au bon endroit."},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(tab==0) {
                    item {PocketSection("Système", "Windows conserve ses options habituelles. Linux bureau installe Debian avec Xfce. Linux serveur installe Debian sans bureau graphique, avec SSH. Ces deux choix Linux partagent les mêmes fichiers de démarrage.") {
                        PocketChoices { InstallerChoice.entries.forEach { next -> FilterChip(selected=choice==next,onClick={choice=next;context.getSharedPreferences("installer",Context.MODE_PRIVATE).edit().putString("choice",next.name).apply()},enabled=!active && !busy,label={Text(next.label)}) } }
                    }}
                    if(choice.linux!=null) item {LinuxPanel(checkNotNull(choice.linux),linuxReady,linuxVerifying,active)}
                    else {
                    item {WinPePanel(state, {busyImport->ServerStore.mutable.update{it.copy(importingWinPe=busyImport)}}, {ready=it})}
                    item {WindowsPanel(state) {busyImport->ServerStore.mutable.update{it.copy(importingWinPe=busyImport)}}}
                }
                    }
                if(tab==1) {
                    item {PocketSection("Connexion", "Téléphone sur le Wi-Fi de la box, PC branché en Ethernet. Sélectionne UEFI PXE IPv4 au démarrage du PC. Garde le téléphone connecté au même réseau et le serveur démarré jusqu’à la fin du transfert. La Freebox doit être configurée une fois dans Aide.") {
                        if(!active) {
                            if(networks.isEmpty()) PocketNote("Connecte le téléphone au Wi-Fi de ta box.")
                            networks.forEach {candidate->
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    RadioButton(selected=candidate.id==chosen,onClick={onChoose(candidate.id)})
                                    Column(Modifier.weight(1f)) {Text(candidate.label.substringBefore(" ·"));Text(candidate.address.hostAddress.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                }
                            }
                            TextButton(onClick=onRefresh,enabled=!busy){Text("Actualiser les réseaux")}
                        } else Text("Serveur sur ${state.ip}",style=MaterialTheme.typography.bodyMedium)
                        if(active) OutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth()){Text("Arrêter le serveur")}
                        else Button(onClick={onWinPe(choice)},enabled=installerReady && !busy && !selectedUsb && networks.any{it.id==chosen},modifier=Modifier.fillMaxWidth()){Text("Démarrer le serveur")}
                        if(!active && !installerReady) Text(if(choice.linux!=null) "Télécharge Debian dans Préparer pour démarrer." else "Vérifie ou importe le ZIP WinPE dans Préparer pour démarrer.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }}
                    item {if(state.linuxProfile!=null || (!active && choice.linux!=null))LinuxInstallationProgress(state) else InstallationProgress(state)}
                }
                if(tab==2) item {LibraryPanel(busy || active) {ready=false;linuxReady=false;libraryRevision++}}
                if(tab==3) {
                    item {PocketSection("Configuration Freebox", "Cette configuration reste manuelle. PocketInstall ne modifie aucun réglage de ta box. Réserve une IP au téléphone : sinon le fichier de configuration devra être exporté de nouveau.") {
                        Text("À faire une seule fois",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick={setup=!setup},modifier=Modifier.fillMaxWidth()){Text(if(setup) "Fermer le guide" else "Configurer ma Freebox")}
                        if(setup) {
                            Text("1. Démarre le serveur dans Installer. Réserve l’IP du téléphone dans les baux DHCP de la Freebox.")
                            Button(onClick={export.launch("pocketinstall.ipxe")},enabled=state.status==ServerStatus.RUNNING){Text("Enregistrer pocketinstall.ipxe")}
                            Text("2. Place ce fichier et snponly.efi dans le même dossier TFTP de la Freebox. Active le serveur TFTP sur ce dossier.")
                            Text("3. Dans DHCP : serveur TFTP = IP de la Freebox ; fichier de démarrage = snponly.efi. Applique les réglages.")
                            if(state.loaderUrl.isNotEmpty()) TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Chargeur",state.loaderUrl))}){Text("Copier l’URL de snponly.efi")}
                            Text("Pour l’attente silencieuse de cette version, remplace seulement pocketinstall.ipxe par le nouvel export. Les autres réglages restent identiques.",style=MaterialTheme.typography.bodySmall)
                            if(exportMessage.isNotEmpty()) PocketNote(exportMessage)
                        }
                    }}
                    item {PocketSection("Diagnostic", "Les adresses et journaux servent au dépannage. Une requête HTTP ne prouve pas le démarrage : WinPE et Windows doivent transmettre leur propre confirmation. Les commandes manuelles sont réservées aux tests.") {
                        OutlinedButton(onClick={diagnostic=!diagnostic},modifier=Modifier.fillMaxWidth()){Text(if(diagnostic) "Masquer les détails" else "Afficher les détails techniques")}
                        if(diagnostic) {
                            Text(state.message)
                            Text("IP ${state.ip} · ${state.requests} requêtes · ${state.clientsSeen} clients",fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                            if(state.url.isNotEmpty()){Text(state.url,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall);TextButton(onClick=onCopy){Text("Copier l’URL")}}
                            if(state.tftpMessage.isNotEmpty()) Text(state.tftpMessage,style=MaterialTheme.typography.bodySmall)
                            if(state.installMessage.isNotEmpty()) Text(state.installMessage)
                            if(!active) {
                                PocketCheck("Test réseau USB expérimental",selectedUsb,true,onMode)
                                PocketCheck("Test EFI en PXE",selectedPxe,!selectedUsb,onPxe)
                                if(selectedUsb) TextButton(onClick=onSettings){Text("Paramètres réseau Android")}
                                OutlinedButton(onClick=onStart,enabled=!busy && networks.any{it.id==chosen}){Text("Lancer le test EFI")}
                            }
                            TextButton(onClick=onCopyRelay,enabled=state.url.isNotEmpty()){Text("Copier la commande du relais Linux")}
                            TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Diagnostic iPXE","dhcp\nchain ${state.url}"))},enabled=state.url.isNotEmpty()){Text("Copier les commandes iPXE")}
                        }
                    }}
                    if(diagnostic) {
                        items(state.events.takeLast(30).reversed()) {event->
                            Text("${event.method} ${event.resource} · ${event.status}\n${event.peer} · ${event.sentBytes}/${event.expectedBytes} octets",fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                        }
                        items(state.tftpEvents.takeLast(10).reversed()) {event->Text("TFTP ${event.resource} · ${event.result}",fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
                    }
                    item {TextButton(onClick={licenseOpen=true}){Text("Licence · PocketInstall ${BuildConfig.VERSION_NAME}")}}
                }
            }
        }
    }
}

@Composable
private fun InstallationProgress(state: ServerSnapshot) {
    val active=state.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING)
    val phase=state.winPeProgress.stage
    val steps=listOf("Serveur démarré","PC connecté","Chargement WinPE","WinPE démarré","Installation Windows","Premier démarrage")
    val install=state.installMessage
    val current=when {
        !active || state.status==ServerStatus.STARTING->-1
        install.startsWith("Windows démarré")->5
        install.startsWith("Windows appliqué")->4
        install.startsWith("Installation de Windows") || install.startsWith("Configuration") || install.startsWith("Transfert") || install.startsWith("Vérification de l'image")->4
        phase==WinPeStage.STARTED->3
        phase>=WinPeStage.LOADING->2
        phase>=WinPeStage.DETECTED->1
        else->0
    }
    val error=state.status==ServerStatus.ERROR || install.startsWith("Installation interrompue") || state.winPeProgress.error.isNotEmpty()
    PocketSection("Progression", "La progression suit les signaux réels du PC. « WinPE envoyé » signifie que les fichiers ont été transférés, pas que WinPE a démarré. Le premier démarrage de Windows reste à vérifier à l’écran du PC si son signal ne parvient pas au téléphone.") {
        steps.forEachIndexed {index,label->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(color=if(index<=current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,shape=androidx.compose.foundation.shape.CircleShape,modifier=Modifier.size(32.dp)) {
                    Box(contentAlignment=Alignment.Center){Text(if(index<current) "✓" else "${index+1}",style=MaterialTheme.typography.labelLarge)}
                }
                Text(label,style=MaterialTheme.typography.bodyMedium,color=if(index<=current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val status=when {
            state.status==ServerStatus.ERROR->"Le serveur n’a pas pu démarrer. Consulte Aide > Diagnostic."
            state.status==ServerStatus.STARTING->"Vérification du serveur…"
            !active->"Démarre le serveur pour attendre le PC."
            install.isNotEmpty()->install.substringBefore('\n')
            else->phase.label
        }
        PocketNote(status,error=error)
        if(state.winPeProgress.wimTotal>0 && phase<WinPeStage.STARTED) {
            val progress=(state.winPeProgress.wimBytes.toFloat()/state.winPeProgress.wimTotal).coerceIn(0f,1f)
            LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
            Text("Envoi WinPE · ${(progress*100).toInt()} %",style=MaterialTheme.typography.labelLarge)
        }
        if(state.pcHardware.isNotEmpty()) Text(state.pcHardware.substringBefore(" · TPM"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.winPeProgress.error.isNotEmpty()) PocketNote(state.winPeProgress.error,error=true)
    }
}
