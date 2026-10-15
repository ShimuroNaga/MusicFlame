package com.music.musicflame.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R

/**
 * Términos de la IA local. Se muestra solo la primera vez que el usuario entra a la pantalla de IA
 * ([onAccept] != null → botones Aceptar / Ahora no) y se puede releer desde Ajustes > IA
 * ([onAccept] == null → solo "Cerrar").
 */
@Composable
fun AiTermsDialog(onAccept: (() -> Unit)?, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    @Composable fun Block(title: Int, body: Int) {
        Text(stringResource(title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_terms_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Block(R.string.ai_terms_what_t, R.string.ai_terms_what_b)
                Block(R.string.ai_terms_privacy_t, R.string.ai_terms_privacy_b)
                Block(R.string.ai_terms_limits_t, R.string.ai_terms_limits_b)
                Block(R.string.ai_terms_device_t, R.string.ai_terms_device_b)
                Block(R.string.ai_terms_license_t, R.string.ai_terms_license_b)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { runCatching { uri.openUri("https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct") } }) { Text(stringResource(R.string.ai_terms_link_model), fontSize = 12.sp) }
                    TextButton(onClick = { runCatching { uri.openUri("https://www.apache.org/licenses/LICENSE-2.0") } }) { Text(stringResource(R.string.ai_terms_link_license), fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            if (onAccept != null) Button(onClick = onAccept) { Text(stringResource(R.string.ai_terms_accept), fontWeight = FontWeight.Bold) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_terms_close)) }
        },
        dismissButton = {
            if (onAccept != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_terms_not_now)) }
        }
    )
}
