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
    val download by WindowsDownloadStore.state.collectAsState()
    var resolver by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("French") }
    var manual by remember { mutableStateOf(false) }
    val locked = busy || resolver || download.active || state.importingWinPe || state.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING)
    fun update(value: WindowsSelection = selection, install: Boolean = enabled) {
        val valid = install && image?.entries?.count { it.matches(value) } == 1
        WindowsStorage.save(context,value,valid); selection = value; enabled = valid
    }
    LaunchedEffect(download.prepared) {
        try {
            image = withContext(Dispatchers.IO) { WindowsStorage.current(context)?.let { WindowsStorage.info(it) } }
            enabled = WindowsStorage.enabled(context)
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
    Card { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Installer Windows",style=MaterialTheme.typography.titleLarge)
        Text("Windows")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { WindowsVersion.entries.forEach { version ->
            FilterChip(selected=selection.version == version,onClick={ update(selection.copy(version=version)) },enabled=!locked,label={Text(version.label)})
        } }
        if(selection.version == WindowsVersion.WINDOWS_10) Text("Windows 10 : support standard terminé. Vérifie ta couverture de mises à jour avant de le choisir.")
        Text("Édition · selon ta licence")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { WindowsEdition.entries.forEach { edition ->
            FilterChip(selected=selection.edition == edition,onClick={update(selection.copy(edition=edition))},enabled=!locked,label={Text(edition.label)})
        } }
        Text("Organisation du disque",style=MaterialTheme.typography.titleMedium)
        StorageLayout.entries.forEach { layout ->
            FilterChip(selected=selection.storageLayout == layout,onClick={update(selection.copy(storageLayout=layout))},enabled=!locked,label={Text(layout.label)})
        }
        if(selection.storageLayout == StorageLayout.SPLIT) {
            Text("C: pour Windows, les logiciels et les fichiers temporaires · D: pour tes fichiers et jeux.")
            Text("Espace réservé à Windows : ${selection.systemGiB} Gio")
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(96,128,160).forEach { size ->
                    FilterChip(selected=selection.systemGiB == size,onClick={update(selection.copy(systemGiB=size))},enabled=!locked,label={Text("$size Gio")})
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(256,512).forEach { size ->
                    FilterChip(selected=selection.systemGiB == size,onClick={update(selection.copy(systemGiB=size))},enabled=!locked,label={Text("$size Gio")})
                }
            }
            WindowsCheck("Masquer C: dans l’Explorateur",selection.hideSystemDrive,!locked) {update(selection.copy(hideSystemDrive=it))}
            Text("D: reçoit le reste du disque (au moins 16 Gio). C: reste accessible en saisissant son chemin. Les dossiers personnels restent sur C: ; enregistre tes fichiers sur D: pour utiliser cet espace.")
        }
        Text("Les petites partitions de démarrage et de récupération restent masquées. Le choix et l’effacement du disque sont confirmés sur le PC.")
        Text("Débloat")
        DebloatProfile.entries.forEach { profile ->
            FilterChip(selected=selection.debloat == profile,onClick={update(selection.copy(debloat=profile))},enabled=!locked,label={Text(profile.label)})
        }
        if(selection.debloat == DebloatProfile.CUSTOM) {
            WindowsCheck("Retirer Clipchamp",selection.removeClipchamp,!locked) {update(selection.copy(removeClipchamp=it))}
            WindowsCheck("Retirer Solitaire",selection.removeSolitaire,!locked) {update(selection.copy(removeSolitaire=it))}
            WindowsCheck("Retirer Actualités",selection.removeNews,!locked) {update(selection.copy(removeNews=it))}
            WindowsCheck("Retirer Météo",selection.removeWeather,!locked) {update(selection.copy(removeWeather=it))}
        }
        if(selection.debloat == DebloatProfile.LIGHT) Text("Retire Clipchamp, Solitaire, Actualités et Météo s'ils sont présents. Réinstallation possible via Microsoft Store.")
        if(selection.debloat == DebloatProfile.AUTO) Text("Le PC détecté détermine le profil : léger avec moins de 8 Go de RAM ou 2 cœurs maximum, sinon aucun. L'édition reste ton choix. La compatibilité Windows 11 du processeur reste à vérifier.")
        if(selection.debloat != DebloatProfile.NONE) Text("Windows Update, Defender, Microsoft Store et les pilotes sont conservés. Les changements sont journalisés sur le PC.")
        Text(message)
        if(busy) LinearProgressIndicator()
        Text("Langue de Windows")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=language == "French",onClick={language="French"},enabled=!locked,label={Text("Français")})
            FilterChip(selected=language == "English",onClick={language="English"},enabled=!locked,label={Text("English US")})
        }
        Button(onClick={resolver=true},enabled=!locked) { Text("Télécharger et préparer Windows") }
        Text("ISO x64 officielle Microsoft · Home et Pro dans la même image. Prévois environ 15 à 20 Go libres. L’installation reste à confirmer sur le PC.")
        if(download.message.isNotBlank()) Text(download.message)
        if(download.active) {
            if(download.total > 0) LinearProgressIndicator(progress={ (download.bytes.toFloat()/download.total).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick={WindowsDownloadService.cancel(context)}) {Text("Annuler le téléchargement")}
        }
        TextButton(onClick={manual=!manual},enabled=!locked) {Text(if(manual) "Masquer l’import manuel" else "Import manuel / secours")}
        if(manual) {
        OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(if(selection.version == WindowsVersion.WINDOWS_11) "https://www.microsoft.com/fr-fr/software-download/windows11" else "https://www.microsoft.com/fr-fr/software-download/windows10ISO")))},enabled=!locked) {Text("Ouvrir le site Microsoft")}
        Text("Le ZIP WinPE démarre le PC ; l'ISO Windows contient le système à installer. Import ISO9660/UDF standard ou sources/install.wim/install.esd extrait sur le téléphone.")
        OutlinedTextField(value=hash,onValueChange={hash=it.take(64)},enabled=!locked,label={Text("SHA-256 officiel du fichier (facultatif)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        WindowsCheck("Ce fichier provient d'un téléchargement officiel Microsoft",trusted,!locked) {trusted=it}
        Button(onClick={picker.launch(arrayOf("*/*"))},enabled=!locked && trusted && (hash.isBlank() || hash.trim().matches(Regex("[a-fA-F0-9]{64}")))) {Text("Importer l'image Windows")}
        }
        val match = image?.entries?.filter { it.matches(selection) }.orEmpty()
        if(image != null) {
            Text("Éditions disponibles : " + image!!.entries.filter { it.architecture == 9 && it.editionId in setOf("Core","Professional") }.joinToString { it.name })
            Text(if(match.size == 1) "${match.single().name} · index ${match.single().index} détecté" else "La sélection n'est pas disponible dans cette image. Importe l'image correspondante ou change d'édition.")
        }
        WindowsCheck("Préparer l'installation au prochain démarrage PXE",enabled,!locked && match.size == 1) {update(install=it)}
        Text("Installation neuve : le disque sera choisi et l'effacement confirmé sur le PC. Le transfert commence après cet effacement. Garde le serveur ouvert jusqu'à la fin du transfert.")
        if(state.pcHardware.isNotEmpty()) Text("PC détecté : ${state.pcHardware}")
        if(state.installMessage.isNotEmpty()) Text(state.installMessage)
    } }
}
@Composable
private fun WindowsCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row { Checkbox(checked=checked,onCheckedChange=onChange,enabled=enabled); Text(label,modifier=Modifier.padding(top=12.dp)) }
}
