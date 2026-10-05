package app.pocketinstall

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketinstall.server.LinuxProfile
import app.pocketinstall.server.LinuxStage

enum class InstallerChoice(val label: String, val linux: LinuxProfile?) {
    WINDOWS("Windows",null), DESKTOP("Linux bureau",LinuxProfile.DESKTOP), SERVER("Linux serveur",LinuxProfile.SERVER)
}

@Composable
fun LinuxPanel(profile: LinuxProfile, ready: Boolean, verifying: Boolean, active: Boolean) {
    val context=LocalContext.current
    val download by LinuxDownloadStore.state.collectAsStateWithLifecycle()
    PocketSection(profile.label,"Les fichiers de démarrage sont téléchargés depuis Debian et vérifiés par SHA-256. Le PC télécharge les paquets sur Internet pendant l’installation. Tu choisis ton compte et ton disque sur le PC ; aucun disque n’est effacé automatiquement. La configuration Freebox actuelle reste valable. Debian serveur utilise la ligne de commande et SSH, sans bureau graphique.") {
        Text(if(profile==LinuxProfile.DESKTOP) "Un bureau léger avec Xfce." else "Sans interface graphique · SSH inclus.",style=MaterialTheme.typography.bodyMedium)
        if(download.active) {
            if(download.total>0) LinearProgressIndicator(progress={(download.bytes.toFloat()/download.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            Text(download.message,style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={LinuxDownloadService.cancel(context)}){Text("Annuler")}
        } else {
            PocketNote(when {verifying->"Vérification des fichiers…";ready->"Environnement Debian prêt";else->"Télécharge le démarrage Debian · environ 55 Mio"})
            Button(onClick={LinuxDownloadService.start(context)},enabled=!active && !verifying,modifier=Modifier.fillMaxWidth()) {Text(if(ready) "Actualiser Debian" else "Télécharger Debian")}
            if(download.message.isNotEmpty()) Text(download.message,style=MaterialTheme.typography.bodySmall)
            if(ready) Text("Passe dans Installer pour démarrer le serveur.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LinuxInstallationProgress(state: ServerSnapshot) {
    val stage=state.linuxProgress.stage
    val active=state.status==ServerStatus.RUNNING
    val steps=listOf("Serveur démarré","iPXE connecté","Chargement Debian","Installateur sur le PC","Installation terminée")
    val current=if(!active)-1 else when(stage) {LinuxStage.WAITING->0;LinuxStage.IPXE->1;LinuxStage.LOADING,LinuxStage.SENT->2;LinuxStage.STARTED->3;LinuxStage.INSTALLED->4}
    PocketSection("Progression Debian","Le transfert des fichiers ne confirme pas le démarrage. Les deux derniers états nécessitent un signal de l’installateur Debian. Le choix du disque et du compte se fait sur le PC. Le premier démarrage du système installé reste à confirmer à son écran. Si la session du téléphone expire, Debian peut continuer sans envoyer ces signaux.") {
        steps.forEachIndexed {index,label->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(if(index<current) "✓" else "${index+1}",color=MaterialTheme.colorScheme.primary);Text(label,color=if(index<=current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        PocketNote(if(active)stage.label else if(state.status==ServerStatus.STARTING)"Vérification du serveur…" else state.message,error=state.status==ServerStatus.ERROR)
        if(active && state.linuxProgress.total>0 && stage<LinuxStage.STARTED) LinearProgressIndicator(progress={(state.linuxProgress.bytes.toFloat()/state.linuxProgress.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
        if(state.linuxProgress.error.isNotEmpty())PocketNote(state.linuxProgress.error,error=true)
    }
}
