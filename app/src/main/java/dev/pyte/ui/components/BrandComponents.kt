package dev.pyte.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pyte.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource

@Composable
fun ScreenHeading(title: String, subtitle: String, modifier: Modifier = Modifier, onSettings: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.pyte_wordmark), contentDescription = "PYTE",
                modifier = Modifier.width(108.dp).height(24.dp))
            Spacer(Modifier.weight(1f))
            if (onSettings != null) androidx.compose.material3.IconButton(onClick = onSettings,
                modifier = Modifier.semantics { contentDescription = "Einstellungen" }) {
                Icon(androidx.compose.material.icons.Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.on_device), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BrandButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    Button(onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.pressMotion(interaction).clip(shape).touchBloom(interaction).alpha(if (enabled) 1f else 0.45f).background(Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Color.Transparent, disabledContentColor = MaterialTheme.colorScheme.onPrimary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 17.dp)) {
        icon?.let { Icon(it, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LibraryWelcome(onImport: () -> Unit, modifier: Modifier = Modifier, onSearch: () -> Unit = {}) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.pyte_logo), contentDescription = "PYTE",
            modifier = Modifier.width(250.dp).aspectRatio(640f / 563f))
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.welcome_body),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        BrandButton(stringResource(R.string.search), onSearch, Modifier.fillMaxWidth(), icon = Icons.Default.Add)
        androidx.compose.material3.TextButton(onClick = onImport) { Text(stringResource(R.string.import_playlist)) }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.welcome_badge), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}
