package app.pocketinstall

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference

data class DownloadSource(val label: String, val url: String)

@Composable
fun DownloadConfirmationDialog(title: String, sources: List<DownloadSource>, note: String,
    inspect: ((HttpURLConnection?) -> Unit) -> Map<String,Long>, close: () -> Unit,
    confirmed: (Map<String,Long>) -> Unit) {
    val context = LocalContext.current
    var sizes by remember { mutableStateOf<Map<String,Long>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val connection = remember { AtomicReference<HttpURLConnection?>(null) }
    DisposableEffect(Unit) { onDispose { connection.getAndSet(null)?.disconnect() } }
    LaunchedEffect(attempt) {
        sizes = null; error = null
        try {
            sizes = withContext(Dispatchers.IO) { inspect { connection.set(it) } }.also {
                require(it.isNotEmpty() && it.values.all { size -> size > 0 })
            }
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { error = e.message ?: context.getString(R.string.download_size_failed) }
    }
    AlertDialog(onDismissRequest=close, title={Text(title)}, text={
        Column(modifier=Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(sizes == null && error == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(context.getString(R.string.download_size_checking))
            }
            sizes?.let { files ->
                Text(context.getString(R.string.download_size_total, android.text.format.Formatter.formatFileSize(context, files.values.sum())), style=MaterialTheme.typography.titleMedium)
                files.forEach { (name, bytes) -> Text("$name · ${android.text.format.Formatter.formatFileSize(context,bytes)}",style=MaterialTheme.typography.bodySmall) }
            }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Text(note,style=MaterialTheme.typography.bodySmall)
            sources.forEach { source ->
                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(source.url)))}) {
                    Text(source.label)
                }
            }
            Text(context.getString(R.string.download_network_notice),style=MaterialTheme.typography.bodySmall)
        }
    }, confirmButton={
        if(error != null) TextButton(onClick={attempt++}) {Text(context.getString(R.string.download_size_retry))}
        else Button(onClick={sizes?.let(confirmed)},enabled=sizes != null) {Text(context.getString(R.string.download_confirm))}
    }, dismissButton={TextButton(onClick=close) {Text(context.getString(R.string.cancel))}})
}
