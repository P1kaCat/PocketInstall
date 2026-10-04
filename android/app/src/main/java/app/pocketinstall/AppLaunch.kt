package app.pocketinstall

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun PocketLaunch(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Configuration changes and locale application must not replay the intro.
    var introComplete by rememberSaveable { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(AppLanguages.isConfirmed(context)) }
    when {
        !introComplete -> PocketIntro { introComplete = true }
        !confirmed -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                LanguagePicker(firstLaunch = true, onChosen = { confirmed = true },
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxHeight())
            }
        }
        else -> content()
    }
}

@Composable
private fun PocketIntro(onComplete: () -> Unit) {
    val scale = remember { Animatable(0.82f) }
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (ValueAnimator.areAnimatorsEnabled()) {
            coroutineScope {
                launch { scale.animateTo(1f, tween(550)) }
                launch { opacity.animateTo(1f, tween(400)) }
            }
        }
        onComplete()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.large, color = androidx.compose.ui.graphics.Color.White) {
                Image(painterResource(R.drawable.pocketinstall_logo), stringResource(R.string.logo_description),
                    Modifier.size(240.dp).graphicsLayer {
                        scaleX = scale.value; scaleY = scale.value; alpha = opacity.value
                    })
            }
        }
    }
}

@Composable
fun LanguagePicker(firstLaunch: Boolean, onChosen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedTag by rememberSaveable { mutableStateOf(AppLanguages.current(context).tag) }
    var failed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (firstLaunch) "Choisis ta langue / Choose your language" else stringResource(R.string.language_title),
            style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
            AppLanguage.entries.forEach { language ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                    selected = selectedTag == language.tag, role = Role.RadioButton,
                    onClick = { selectedTag = language.tag }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedTag == language.tag, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(language.displayName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (failed) Text(stringResource(R.string.language_save_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            val chosen = AppLanguage.entries.first { it.tag == selectedTag }
            if (AppLanguages.choose(context, chosen)) onChosen() else failed = true
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (firstLaunch) AppLanguage.entries.first { it.tag == selectedTag }.continueLabel
                else stringResource(R.string.language_apply))
        }
    }
}
