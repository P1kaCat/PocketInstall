package app.pocketinstall

import android.provider.OpenableColumns
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketinstall.server.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WindowsPanel(state: ServerSnapshot, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(WindowsStorage.selection(context)) }
    var enabled by remember { mutableStateOf(WindowsStorage.enabled(context)) }
    var image by remember { mutableStateOf<WindowsImageInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(context.getString(R.string.image_prompt)) }
    var hash by remember { mutableStateOf("") }
    var trusted by remember { mutableStateOf(false) }
    val download by WindowsDownloadStore.state.collectAsStateWithLifecycle()
    var resolver by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<Pair<String,String>?>(null) }
    var language by remember { mutableStateOf("French") }
    var manual by remember { mutableStateOf(false) }
    val winpeDownload by WinPeDownloadStore.state.collectAsStateWithLifecycle()
    val locked = winpeDownload.active || busy || resolver || pendingDownload != null || download.active || state.importingWinPe || state.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING)
    fun update(value: WindowsSelection = selection, install: Boolean = enabled) {
        val valid = install && image?.entries?.count { it.matches(value) } == 1 &&
            runCatching { WindowsDiskSize.selectedGiB(value,checkNotNull(image)) }.isSuccess
        WindowsStorage.save(context,value,valid); selection = value; enabled = valid
    }
    LaunchedEffect(download.prepared) {
        try {
            image = withContext(Dispatchers.IO) { WindowsStorage.current(context)?.let { WindowsStorage.info(it) } }
            update(install=WindowsStorage.enabled(context))
            if(image != null) message = "Image importée · éditions détectées. Le fichier sera vérifié à nouveau avant le démarrage."
        } catch(e: Exception) { message = "Image indisponible : ${e.message}"; image = null }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null && !locked) scope.launch {
            busy = true; onBusy(true); message = "Copie et vérification de l'image…"
            try {
                image = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                        if(cursor.moveToFirst()) cursor.getString(0) else ""
                    }.orEmpty()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        WindowsStorage.importImage(context,input,name.endsWith(".iso",true),hash.trim()) { bytes ->
                            if(bytes % (16L * 1048576) == 0L) scope.launch { message = "Import : ${bytes / 1048576} Mio…" }
                        }
                    } ?: error("Fichier inaccessible.")
                }
                update(install=false)
                message = "Image importée et intégrité enregistrée. Choisis Windows puis l'édition."
            } catch(e: Exception) { message = "Import refusé : ${e.message}" }
            finally { busy = false; onBusy(false) }
        }
    }
    if(resolver) MicrosoftDownloadDialog(selection.version,language,close={resolver=false}) { url,agent ->
        resolver=false
        pendingDownload = url to agent
    }
    pendingDownload?.let { (url,agent) ->
        DownloadConfirmationDialog(selection.version.label + " · " + selection.edition.label,
            listOf(DownloadSource(context.getString(R.string.download_source_microsoft),"https://www.microsoft.com/software-download/" + if(selection.version==WindowsVersion.WINDOWS_11) "windows11" else "windows10ISO")),
            context.getString(R.string.download_windows_space),
            inspect={ connection -> mapOf("Windows x64 ISO" to DownloadMetadata.size(url,MicrosoftIso::validUrl,WindowsImage.MAX_BYTES,1048576,agent,connection)) },
            close={pendingDownload=null}, confirmed={sizes ->
                pendingDownload=null
                WindowsDownloadService.start(context,url,agent,sizes.getValue("Windows x64 ISO"))
            })
    }
    val match = image?.entries?.filter { it.matches(selection) }.orEmpty()
    val sizing = image?.let { runCatching { WindowsDiskSize.selectedGiB(selection,it) } }
    val valid = match.size == 1 && sizing?.isSuccess == true
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PocketSection(context.getString(R.string.windows), context.getString(R.string.windows_help)) {
            PocketChoices { WindowsVersion.entries.forEach { version ->
                FilterChip(selected=selection.version==version,onClick={update(selection.copy(version=version))},enabled=!locked,label={Text(version.label)})
            } }
            PocketChoices { WindowsEdition.entries.forEach { edition ->
                FilterChip(selected=selection.edition==edition,onClick={update(selection.copy(edition=edition))},enabled=!locked,label={Text(edition.label)})
            } }
            if(selection.version==WindowsVersion.WINDOWS_10) Text(context.getString(R.string.support_ended),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            PocketChoices {
                FilterChip(selected=language=="French",onClick={language="French"},enabled=!locked,label={Text("Français")})
                FilterChip(selected=language=="English",onClick={language="English"},enabled=!locked,label={Text("English US")})
            }
            Button(onClick={resolver=true},enabled=!locked,modifier=Modifier.fillMaxWidth()) {Text(if(image==null) context.getString(R.string.download_windows) else context.getString(R.string.download_another))}
            if(download.active) {
                if(download.total>0) LinearProgressIndicator(progress={(download.bytes.toFloat()/download.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(download.message)
                OutlinedButton(onClick={WindowsDownloadService.cancel(context)}) {Text(context.getString(R.string.cancel_download))}
            } else if(download.message.isNotBlank() && download.prepared == 0L) PocketNote(download.message)
            if(!busy && (message.startsWith("Import refusé") || message.startsWith("Image indisponible"))) PocketNote(message,error=true)
            if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(message) }
            else if(image!=null) PocketNote(if(match.size==1) context.getString(R.string.image_available, match.single().name) else context.getString(R.string.edition_missing),error=match.size!=1)
            else Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={manual=!manual},enabled=!locked) {Text(if(manual) context.getString(R.string.close_manual) else context.getString(R.string.already_image))}
            if(manual) {
                HelpButton(context.getString(R.string.import_image), context.getString(R.string.import_help))
                OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(if(selection.version==WindowsVersion.WINDOWS_11) "https://www.microsoft.com/fr-fr/software-download/windows11" else "https://www.microsoft.com/fr-fr/software-download/windows10ISO")))},enabled=!locked) {Text(context.getString(R.string.microsoft_site))}
                OutlinedTextField(value=hash,onValueChange={hash=it.take(64)},enabled=!locked,label={Text(context.getString(R.string.optional_hash))},singleLine=true,modifier=Modifier.fillMaxWidth())
                PocketCheck(context.getString(R.string.from_microsoft),trusted,!locked) {trusted=it}
                OutlinedButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!locked && trusted && (hash.isBlank() || hash.trim().matches(Regex("[a-fA-F0-9]{64}")))) {Text(context.getString(R.string.import_my_image))}
            }
        }
        PocketSection(context.getString(R.string.storage), context.getString(R.string.storage_help)) {
            PocketChoices { StorageLayout.entries.forEach { layout ->
                FilterChip(selected=selection.storageLayout==layout,onClick={update(selection.copy(storageLayout=layout))},enabled=!locked,label={Text(context.getString(if(layout==StorageLayout.SINGLE) R.string.layout_single else R.string.layout_split))})
            } }
            if(selection.storageLayout==StorageLayout.SPLIT) {
                PocketCheck(context.getString(R.string.auto_size),selection.autoSystemSize,!locked) {update(selection.copy(autoSystemSize=it))}
                if(selection.autoSystemSize) {
                    Text(sizing?.getOrNull()?.let {context.getString(R.string.split_sizes, it)} ?: context.getString(R.string.size_after_import),style=MaterialTheme.typography.bodyMedium)
                    HelpButton(context.getString(R.string.auto_size_title), context.getString(R.string.size_help))
                } else PocketChoices { listOf(48,64,80,96,128,160,256,512).forEach { size ->
                    FilterChip(selected=selection.systemGiB==size,onClick={update(selection.copy(systemGiB=size))},enabled=!locked,label={Text(context.getString(R.string.size_gib, size))})
                } }
                sizing?.exceptionOrNull()?.message?.let {PocketNote(it,error=true)}
                PocketCheck(context.getString(R.string.hide_c),selection.hideSystemDrive,!locked) {update(selection.copy(hideSystemDrive=it))}
            }
            Text(context.getString(R.string.confirm_erase),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PocketSection(context.getString(R.string.personalization), context.getString(R.string.personalization_help)) {
            PocketChoices { DebloatProfile.entries.forEach { profile ->
                FilterChip(selected=selection.debloat==profile,onClick={update(selection.copy(debloat=profile))},enabled=!locked,label={Text(context.getString(when(profile) { DebloatProfile.NONE -> R.string.profile_none; DebloatProfile.LIGHT -> R.string.profile_light; DebloatProfile.CUSTOM -> R.string.profile_custom; DebloatProfile.AUTO -> R.string.profile_auto }))})
            } }
            if(selection.debloat==DebloatProfile.CUSTOM) {
                PocketCheck(context.getString(R.string.remove_clipchamp),selection.removeClipchamp,!locked) {update(selection.copy(removeClipchamp=it))}
                PocketCheck(context.getString(R.string.remove_solitaire),selection.removeSolitaire,!locked) {update(selection.copy(removeSolitaire=it))}
                PocketCheck(context.getString(R.string.remove_news),selection.removeNews,!locked) {update(selection.copy(removeNews=it))}
                PocketCheck(context.getString(R.string.remove_weather),selection.removeWeather,!locked) {update(selection.copy(removeWeather=it))}
            }
            PocketCheck(context.getString(R.string.install_next),enabled,!locked && valid) {update(install=it)}
            if(enabled) PocketNote(context.getString(R.string.selection_ready))
        }
    }
}
