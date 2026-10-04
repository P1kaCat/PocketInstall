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
    var message by remember { mutableStateOf("Importe une image d'installation Windows officielle.") }
    var hash by remember { mutableStateOf("") }
    var trusted by remember { mutableStateOf(false) }
    val download by WindowsDownloadStore.state.collectAsStateWithLifecycle()
    var resolver by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("French") }
    var manual by remember { mutableStateOf(false) }
    val locked = busy || resolver || download.active || state.importingWinPe || state.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING)
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
        WindowsDownloadService.start(context,url,agent)
    }
    val match = image?.entries?.filter { it.matches(selection) }.orEmpty()
    val sizing = image?.let { runCatching { WindowsDiskSize.selectedGiB(selection,it) } }
    val valid = match.size == 1 && sizing?.isSuccess == true
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PocketSection("01  Windows", "Choisis la version et l’édition correspondant à ta licence. L’image Microsoft contient Home et Pro. Windows 10 a atteint la fin de son support standard : vérifie ta couverture de mises à jour.") {
            PocketChoices { WindowsVersion.entries.forEach { version ->
                FilterChip(selected=selection.version==version,onClick={update(selection.copy(version=version))},enabled=!locked,label={Text(version.label)})
            } }
            PocketChoices { WindowsEdition.entries.forEach { edition ->
                FilterChip(selected=selection.edition==edition,onClick={update(selection.copy(edition=edition))},enabled=!locked,label={Text(edition.label)})
            } }
            if(selection.version==WindowsVersion.WINDOWS_10) Text("Support standard terminé",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            PocketChoices {
                FilterChip(selected=language=="French",onClick={language="French"},enabled=!locked,label={Text("Français")})
                FilterChip(selected=language=="English",onClick={language="English"},enabled=!locked,label={Text("English US")})
            }
            Button(onClick={resolver=true},enabled=!locked,modifier=Modifier.fillMaxWidth()) {Text(if(image==null) "Télécharger Windows" else "Télécharger une autre image")}
            if(download.active) {
                if(download.total>0) LinearProgressIndicator(progress={(download.bytes.toFloat()/download.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(download.message)
                OutlinedButton(onClick={WindowsDownloadService.cancel(context)}) {Text("Annuler le téléchargement")}
            } else if(download.message.isNotBlank() && download.prepared == 0L) PocketNote(download.message)
            if(!busy && (message.startsWith("Import refusé") || message.startsWith("Image indisponible"))) PocketNote(message,error=true)
            if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(message) }
            else if(image!=null) PocketNote(if(match.size==1) "${match.single().name} · image disponible" else "Cette édition manque dans l’image importée.",error=match.size!=1)
            else Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={manual=!manual},enabled=!locked) {Text(if(manual) "Fermer l’import manuel" else "J’ai déjà une image Windows")}
            if(manual) {
                HelpButton("Importer une image", "Importe une ISO Microsoft officielle, ou sources/install.wim ou install.esd extrait de cette ISO. Le ZIP WinPE contient l’environnement de démarrage, pas Windows. Prévois 15 à 20 Go libres sur le téléphone pour le téléchargement et la préparation. Un SHA-256 officiel peut être fourni pour vérifier le fichier.")
                OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(if(selection.version==WindowsVersion.WINDOWS_11) "https://www.microsoft.com/fr-fr/software-download/windows11" else "https://www.microsoft.com/fr-fr/software-download/windows10ISO")))},enabled=!locked) {Text("Site Microsoft")}
                OutlinedTextField(value=hash,onValueChange={hash=it.take(64)},enabled=!locked,label={Text("SHA-256 officiel · facultatif")},singleLine=true,modifier=Modifier.fillMaxWidth())
                PocketCheck("Fichier téléchargé depuis Microsoft",trusted,!locked) {trusted=it}
                OutlinedButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!locked && trusted && (hash.isBlank() || hash.trim().matches(Regex("[a-fA-F0-9]{64}")))) {Text("Importer mon image")}
            }
        }
        PocketSection("02  Stockage", "Le disque est choisi sur le PC. Une installation neuve efface ses partitions uniquement après confirmation explicite. Les petites partitions EFI, MSR et récupération restent masquées. En mode séparé, les dossiers personnels restent sur C: : enregistre tes fichiers et jeux sur D:. Masquer C: retire son icône, sans bloquer son accès.") {
            PocketChoices { StorageLayout.entries.forEach { layout ->
                FilterChip(selected=selection.storageLayout==layout,onClick={update(selection.copy(storageLayout=layout))},enabled=!locked,label={Text(layout.label)})
            } }
            if(selection.storageLayout==StorageLayout.SPLIT) {
                PocketCheck("Taille Windows automatique",selection.autoSystemSize,!locked) {update(selection.copy(autoSystemSize=it))}
                if(selection.autoSystemSize) {
                    Text(sizing?.getOrNull()?.let {"Windows : $it Gio · Mes fichiers : le reste"} ?: "Taille calculée après l’import de Windows",style=MaterialTheme.typography.bodyMedium)
                    HelpButton("Taille automatique", "Le calcul utilise la taille de l’édition sélectionnée, 10 Gio pour les temporaires et 16 Gio de marge pour les mises à jour. Il couvre aussi le transfert WIM/ESD et respecte un plancher conservateur de 64 Gio pour Windows 11 et 32 Gio pour Windows 10. D: reçoit le reste, au moins 16 Gio. Ce calcul n’est pas un quota ni une garantie d’espace libre après installation de logiciels.")
                } else PocketChoices { listOf(48,64,80,96,128,160,256,512).forEach { size ->
                    FilterChip(selected=selection.systemGiB==size,onClick={update(selection.copy(systemGiB=size))},enabled=!locked,label={Text("$size Gio")})
                } }
                sizing?.exceptionOrNull()?.message?.let {PocketNote(it,error=true)}
                PocketCheck("Masquer C: dans l’Explorateur",selection.hideSystemDrive,!locked) {update(selection.copy(hideSystemDrive=it))}
            }
            Text("Effacement à confirmer sur le PC",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PocketSection("03  Personnalisation", "Léger retire Clipchamp, Solitaire, Actualités et Météo si présents. Auto choisit Léger avec moins de 8 Go de RAM ou au plus 2 cœurs, sinon Aucun. Windows Update, Defender, le Store et les pilotes restent disponibles. Le choix de l’édition n’est pas automatique.") {
            PocketChoices { DebloatProfile.entries.forEach { profile ->
                FilterChip(selected=selection.debloat==profile,onClick={update(selection.copy(debloat=profile))},enabled=!locked,label={Text(profile.label)})
            } }
            if(selection.debloat==DebloatProfile.CUSTOM) {
                PocketCheck("Retirer Clipchamp",selection.removeClipchamp,!locked) {update(selection.copy(removeClipchamp=it))}
                PocketCheck("Retirer Solitaire",selection.removeSolitaire,!locked) {update(selection.copy(removeSolitaire=it))}
                PocketCheck("Retirer Actualités",selection.removeNews,!locked) {update(selection.copy(removeNews=it))}
                PocketCheck("Retirer Météo",selection.removeWeather,!locked) {update(selection.copy(removeWeather=it))}
            }
            PocketCheck("Installer au prochain démarrage PXE",enabled,!locked && valid) {update(install=it)}
            if(enabled) PocketNote("Sélection prête. Ouvre l’onglet Installer.")
        }
    }
}
