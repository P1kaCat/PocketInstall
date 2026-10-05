package app.pocketinstall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary=Color(0xFF70D9E8), onPrimary=Color(0xFF00363E),
        primaryContainer=Color(0xFF17434D), onPrimaryContainer=Color(0xFFB6F1F8),
        secondary=Color(0xFFA7C9D1), secondaryContainer=Color(0xFF17434D), onSecondaryContainer=Color(0xFFB6F1F8),
        surfaceDim=Color(0xFF0C151B), surfaceBright=Color(0xFF2D3D47),
        surfaceContainerLowest=Color(0xFF081116), surfaceContainerLow=Color(0xFF101C23),
        surfaceContainer=Color(0xFF18262E), surfaceContainerHigh=Color(0xFF20313A), surfaceContainerHighest=Color(0xFF293D47),
        background=Color(0xFF0C151B),
        surface=Color(0xFF142129), surfaceVariant=Color(0xFF24343E),
        onBackground=Color(0xFFE6EDF2), onSurface=Color(0xFFE6EDF2),
        onSurfaceVariant=Color(0xFFB6C8D3), outline=Color(0xFF8195A1)
    ) else lightColorScheme(
        primary=Color(0xFF006677), onPrimary=Color.White,
        primaryContainer=Color(0xFFCEEFF3), onPrimaryContainer=Color(0xFF00363E),
        secondary=Color(0xFF405F68), secondaryContainer=Color(0xFFCEEFF3), onSecondaryContainer=Color(0xFF00363E),
        surfaceDim=Color(0xFFD9E3E9), surfaceBright=Color.White,
        surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFF0F5F7),
        surfaceContainer=Color(0xFFEAF1F4), surfaceContainerHigh=Color(0xFFE3ECEF), surfaceContainerHighest=Color(0xFFDCE7EC),
        background=Color(0xFFF3F7F9),
        surface=Color.White, surfaceVariant=Color(0xFFE2ECF1),
        onBackground=Color(0xFF15232C), onSurface=Color(0xFF15232C),
        onSurfaceVariant=Color(0xFF415661), outline=Color(0xFF647B87)
    )
    MaterialTheme(colorScheme=colors, shapes=Shapes(
        small=RoundedCornerShape(12.dp), medium=RoundedCornerShape(20.dp), large=RoundedCornerShape(24.dp)
    ), content=content)
}

@Composable
fun HelpButton(title: String, explanation: String) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick={open=true}, modifier=Modifier.heightIn(min=48.dp)
        .semantics { contentDescription="Aide : $title. Appuyer pour en savoir plus." },
        contentPadding=PaddingValues(horizontal=8.dp)) {
        Box(Modifier.size(24.dp).border(1.dp,MaterialTheme.colorScheme.primary,CircleShape),contentAlignment=Alignment.Center) {
            Text("?",style=MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(6.dp)); Text(context.getString(R.string.help),style=MaterialTheme.typography.labelLarge)
    }
    if(open) AlertDialog(onDismissRequest={open=false},title={Text(title)},
        text={Text(explanation,Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodyMedium)},
        confirmButton={TextButton(onClick={open=false}){Text(context.getString(R.string.understood))}})
}

@Composable
fun PocketSection(title: String, help: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f).semantics{heading()},style=MaterialTheme.typography.titleLarge)
                if(help!=null) HelpButton(title,help)
            }
            content()
        }
    }
}

@Composable
fun PocketNote(text: String, error: Boolean = false) {
    Surface(color=if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor=if(error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        shape=MaterialTheme.shapes.small,modifier=Modifier.fillMaxWidth()) {
        Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PocketCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,enabled=enabled,role=Role.Checkbox,onValueChange=onChange),
        verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked=checked,onCheckedChange=null,enabled=enabled)
        Spacer(Modifier.width(12.dp)); Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PocketChoices(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
}
