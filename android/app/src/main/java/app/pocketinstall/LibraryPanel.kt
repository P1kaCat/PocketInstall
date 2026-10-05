package app.pocketinstall

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class LibraryEntry(val kind: String,val directory: File,val label: String,val bytes: Long,val current: Boolean)
private fun libraryEntries(context: Context): List<LibraryEntry> = listOf("windows","winpe","linux").flatMap { kind ->
    val id=context.getSharedPreferences(kind,Context.MODE_PRIVATE).getString(if(kind=="windows")"image" else "bundle",null)
    File(context.filesDir,kind).listFiles()?.filter {it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}"))}?.map { directory ->
        val label=when(kind) {
            "windows"->runCatching {WindowsStorage.info(directory).entries.joinToString(" / "){it.name}}.getOrDefault("Image Windows")
            "winpe"->"Windows PE · environnement PC"
            else->"Debian 13 · bureau et serveur"
        }
        LibraryEntry(kind,directory,label,directory.walkTopDown().filter {it.isFile}.sumOf {it.length()},directory.name==id)
    } ?: emptyList()
}

@Composable
fun LibraryPanel(busy: Boolean, onChanged: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var entries by remember {mutableStateOf<List<LibraryEntry>>(emptyList())}
    var loaded by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<LibraryEntry?>(null)}
    var message by remember {mutableStateOf("")}
    LaunchedEffect(Unit) {entries=withContext(Dispatchers.IO){libraryEntries(context)};loaded=true}
    selected?.let { entry ->
        AlertDialog(onDismissRequest={if(!deleting)selected=null},title={Text("Supprimer ces fichiers ?")},
            text={Text("${entry.label}\nLes fichiers stockés sur ce téléphone seront retirés. Tu pourras les télécharger de nouveau. Le système installé sur le PC reste intact.")},
            confirmButton={TextButton(enabled=!busy && !deleting,onClick={
                deleting=true
                scope.launch {
                    message=runCatching {withContext(Dispatchers.IO) {
                        check(ServerStore.state.value.status !in setOf(ServerStatus.RUNNING,ServerStatus.STARTING) && !ServerStore.state.value.importingWinPe && !WindowsDownloadStore.state.value.active && !LinuxDownloadStore.state.value.active && !WinPeDownloadStore.state.value.active) {"Arrête les transferts avant de supprimer"}
                        val parent=File(context.filesDir,entry.kind)
                        check(entry.directory.canonicalFile.parentFile==parent.canonicalFile)
                        val preferences=context.getSharedPreferences(entry.kind,Context.MODE_PRIVATE)
                        val key=if(entry.kind=="windows")"image" else "bundle"
                        if(preferences.getString(key,null)==entry.directory.name) {
                            val edit=preferences.edit().remove(key)
                            if(entry.kind=="windows")edit.putBoolean("enabled",false)
                            check(edit.commit())
                        }
                        check(entry.directory.deleteRecursively()) {"Certains fichiers n’ont pas pu être supprimés"}
                    };"Fichiers supprimés"}.getOrElse {"Suppression impossible : ${it.message}"}
                    entries=withContext(Dispatchers.IO){libraryEntries(context)}
                    deleting=false;selected=null;onChanged()
                }
            }){Text("Supprimer")}},dismissButton={TextButton(enabled=!deleting,onClick={selected=null}){Text("Annuler")}})
    }
    PocketSection("Bibliothèque","Cette liste contient les environnements et images réellement conservés sur ton téléphone, y compris les anciennes copies. Debian bureau et serveur partagent les mêmes fichiers. Arrête le serveur et les téléchargements avant de supprimer une image.") {
        if(!loaded || deleting)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        if(loaded && entries.isEmpty())PocketNote("Aucun environnement téléchargé")
        entries.forEach { entry ->
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(entry.label,style=MaterialTheme.typography.titleSmall)
                Text("${entry.bytes/1048576} Mio · ${if(entry.current)"Copie utilisée" else "Ancienne copie"}",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={selected=entry},enabled=!busy && !deleting){Text("Supprimer")}
                HorizontalDivider()
            }
        }
        if(busy)Text("Arrête le serveur ou attends la fin du téléchargement pour gérer les fichiers.",style=MaterialTheme.typography.bodySmall)
        if(message.isNotEmpty())PocketNote(message,error=message.startsWith("Suppression impossible"))
    }
}
