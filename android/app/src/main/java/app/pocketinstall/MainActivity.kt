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
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {
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
        enableEdgeToEdge()
        setContent {
            PocketTheme {
              PocketLaunch {
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
    onMode: (Boolean) -> Unit, onPxe: (Boolean) -> Unit, onChoose: (String) -> Unit, onRefresh: () -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onCopy: () -> Unit, onCopyRelay: () -> Unit, onSettings: () -> Unit, onWinPe: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val download by WindowsDownloadStore.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if(state.status==ServerStatus.RUNNING) 1 else 0) }
    var ready by rememberSaveable { mutableStateOf(false) }
    var diagnostic by rememberSaveable { mutableStateOf(false) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var licenseOpen by rememberSaveable { mutableStateOf(false) }
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    val active=state.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING)
    val busy=state.importingWinPe || download.active
    if (languageOpen) AlertDialog(onDismissRequest = { languageOpen = false },
        text = { LanguagePicker(firstLaunch = false, onChosen = { languageOpen = false }) },
        confirmButton = {}, dismissButton = {
            TextButton(onClick = { languageOpen = false }) { Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }
        })
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null && state.ip!="—") scope.launch {
            exportMessage=runCatching { withContext(Dispatchers.IO) {
                val config=WinPeHttp.freeboxConfig(InetAddress.getByName(state.ip) as Inet4Address)
                context.contentResolver.openOutputStream(uri)?.use {it.write(config)} ?: error("Destination inaccessible")
            }; "Configuration enregistrée" }.getOrElse {"Export impossible : ${it.message}"}
        }
    }
    if(licenseOpen) AlertDialog(onDismissRequest={licenseOpen=false},title={Text(context.getString(R.string.license_title))},
        text={Text(remember {context.assets.open("licenses/PocketInstall-Personal.txt").bufferedReader().use{it.readText()}},Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick={licenseOpen=false}){Text(context.getString(R.string.close))}})
    Scaffold(contentWindowInsets=WindowInsets.safeDrawing,bottomBar={
        NavigationBar {
            listOf(context.getString(R.string.prepare),context.getString(R.string.install),context.getString(R.string.help)).forEachIndexed { index,label ->
                NavigationBarItem(selected=tab==index,onClick={tab=index},enabled=!busy,
                    icon={Icon(painterResource(listOf(R.drawable.ic_prepare,R.drawable.ic_install,R.drawable.ic_help)[index]),contentDescription=null)},label={Text(label)})
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=680.dp).fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(color=Color.White,shape=MaterialTheme.shapes.small) {
                            androidx.compose.foundation.Image(painterResource(R.drawable.ic_pocketinstall), null, Modifier.size(56.dp))
                        }
                        Text("PocketInstall",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    }
                    Text(when(tab){0->context.getString(R.string.tagline_prepare);1->context.getString(R.string.tagline_install);else->context.getString(R.string.tagline_help)},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(tab==0) {
                    item {WinPePanel(state, {busyImport->ServerStore.mutable.update{it.copy(importingWinPe=busyImport)}}, {ready=it})}
                    item {WindowsPanel(state) {busyImport->ServerStore.mutable.update{it.copy(importingWinPe=busyImport)}}}
                }
                if(tab==1) {
                    item {PocketSection(context.getString(R.string.connection), context.getString(R.string.connection_help)) {
                        if(!active) {
                            if(networks.isEmpty()) PocketNote(context.getString(R.string.connect_wifi))
                            networks.forEach {candidate->
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    RadioButton(selected=candidate.id==chosen,onClick={onChoose(candidate.id)})
                                    Column(Modifier.weight(1f)) {Text(candidate.label.substringBefore(" ·"));Text(candidate.address.hostAddress.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                }
                            }
                            TextButton(onClick=onRefresh,enabled=!busy){Text(context.getString(R.string.refresh_networks))}
                        } else Text(context.getString(R.string.server_address, state.ip),style=MaterialTheme.typography.bodyMedium)
                        if(active) OutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth()){Text(context.getString(R.string.stop_server))}
                        else Button(onClick=onWinPe,enabled=ready && !busy && !selectedUsb && networks.any{it.id==chosen},modifier=Modifier.fillMaxWidth()){Text(context.getString(R.string.start_server))}
                        if(!active && !ready) Text(context.getString(R.string.winpe_required),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }}
                    item {InstallationProgress(state)}
                }
                if(tab==2) {
                    item { TextButton(onClick = { languageOpen = true }, enabled = !busy) {
                        Text(androidx.compose.ui.res.stringResource(R.string.language_title) + " · " + AppLanguages.current(context).displayName)
                    } }
                    item {PocketSection(context.getString(R.string.freebox), context.getString(R.string.freebox_help)) {
                        Text(context.getString(R.string.once),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick={setup=!setup},modifier=Modifier.fillMaxWidth()){Text(if(setup) context.getString(R.string.close_guide) else context.getString(R.string.configure_freebox))}
                        if(setup) {
                            Text(context.getString(R.string.freebox_step1))
                            Button(onClick={export.launch("pocketinstall.ipxe")},enabled=state.status==ServerStatus.RUNNING){Text(context.getString(R.string.save_ipxe))}
                            Text(context.getString(R.string.freebox_step2))
                            Text(context.getString(R.string.freebox_step3))
                            if(state.loaderUrl.isNotEmpty()) TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Chargeur",state.loaderUrl))}){Text(context.getString(R.string.copy_loader))}
                            Text(context.getString(R.string.freebox_update),style=MaterialTheme.typography.bodySmall)
                            if(exportMessage.isNotEmpty()) PocketNote(exportMessage)
                        }
                    }}
                    item {PocketSection(context.getString(R.string.diagnostic), context.getString(R.string.diagnostic_help)) {
                        OutlinedButton(onClick={diagnostic=!diagnostic},modifier=Modifier.fillMaxWidth()){Text(if(diagnostic) context.getString(R.string.hide_details) else context.getString(R.string.show_details))}
                        if(diagnostic) {
                            Text(state.message)
                            Text("IP ${state.ip} · ${state.requests} requêtes · ${state.clientsSeen} clients",fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                            if(state.url.isNotEmpty()){Text(state.url,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall);TextButton(onClick=onCopy){Text(context.getString(R.string.copy_url))}}
                            if(state.tftpMessage.isNotEmpty()) Text(state.tftpMessage,style=MaterialTheme.typography.bodySmall)
                            if(state.installMessage.isNotEmpty()) Text(state.installMessage)
                            if(!active) {
                                PocketCheck(context.getString(R.string.usb_test),selectedUsb,true,onMode)
                                PocketCheck(context.getString(R.string.pxe_test),selectedPxe,!selectedUsb,onPxe)
                                if(selectedUsb) TextButton(onClick=onSettings){Text(context.getString(R.string.android_network))}
                                OutlinedButton(onClick=onStart,enabled=!busy && networks.any{it.id==chosen}){Text(context.getString(R.string.start_efi))}
                            }
                            TextButton(onClick=onCopyRelay,enabled=state.url.isNotEmpty()){Text(context.getString(R.string.copy_relay))}
                            TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Diagnostic iPXE","dhcp\nchain ${state.url}"))},enabled=state.url.isNotEmpty()){Text(context.getString(R.string.copy_ipxe))}
                        }
                    }}
                    if(diagnostic) {
                        items(state.events.takeLast(30).reversed()) {event->
                            Text("${event.method} ${event.resource} · ${event.status}\n${event.peer} · ${event.sentBytes}/${event.expectedBytes} octets",fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                        }
                        items(state.tftpEvents.takeLast(10).reversed()) {event->Text("TFTP ${event.resource} · ${event.result}",fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
                    }
                    item {TextButton(onClick={licenseOpen=true}){Text(context.getString(R.string.license_version, BuildConfig.VERSION_NAME))}}
                }
            }
        }
    }
}

@Composable
private fun InstallationProgress(state: ServerSnapshot) {
    val context = LocalContext.current
    val active=state.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING)
    val phase=state.winPeProgress.stage
    val steps=listOf(context.getString(R.string.server_started),context.getString(R.string.pc_connected),context.getString(R.string.loading_winpe),context.getString(R.string.winpe_started),context.getString(R.string.installing_windows),context.getString(R.string.first_boot))
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
    PocketSection(context.getString(R.string.progress), context.getString(R.string.progress_help)) {
        steps.forEachIndexed {index,label->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(color=if(index<=current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,shape=androidx.compose.foundation.shape.CircleShape,modifier=Modifier.size(32.dp)) {
                    Box(contentAlignment=Alignment.Center){Text(if(index<current) "✓" else "${index+1}",style=MaterialTheme.typography.labelLarge)}
                }
                Text(label,style=MaterialTheme.typography.bodyMedium,color=if(index<=current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val status=when {
            state.status==ServerStatus.ERROR->context.getString(R.string.server_failed)
            state.status==ServerStatus.STARTING->context.getString(R.string.checking_server)
            !active->context.getString(R.string.start_wait)
            install.isNotEmpty()->install.substringBefore('\n')
            else->context.getString(when(phase) { WinPeStage.WAITING -> R.string.waiting_pc; WinPeStage.DETECTED -> R.string.pc_detected; WinPeStage.IPXE -> R.string.ipxe_connected; WinPeStage.LOADING -> R.string.loading_winpe; WinPeStage.SENT -> R.string.winpe_sent; WinPeStage.STARTED -> R.string.winpe_confirmed })
        }
        PocketNote(status,error=error)
        if(state.winPeProgress.wimTotal>0 && phase<WinPeStage.STARTED) {
            val progress=(state.winPeProgress.wimBytes.toFloat()/state.winPeProgress.wimTotal).coerceIn(0f,1f)
            LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
            Text(context.getString(R.string.sending_winpe, (progress*100).toInt()),style=MaterialTheme.typography.labelLarge)
        }
        if(state.pcHardware.isNotEmpty()) Text(state.pcHardware.substringBefore(" · TPM"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.winPeProgress.error.isNotEmpty()) PocketNote(state.winPeProgress.error,error=true)
    }
}
