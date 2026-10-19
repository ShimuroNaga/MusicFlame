package com.music.musicflame.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import com.music.musicflame.data.OnlineAiStore
import com.music.musicflame.data.OnlineModel

private const val GROQ_KEYS_URL = "https://console.groq.com/keys"
private const val GROQ_PRIVACY_URL = "https://groq.com/privacy-policy"
private const val GROQ_TERMS_URL = "https://groq.com/terms-of-use"

/** 12300 -> "12.3K"; menos de mil se deja tal cual. */
private fun fmtTokens(n: Int): String {
    if (n < 1000) return n.toString()
    val d = (n % 1000) / 100
    return if (d == 0) "${n / 1000}K" else "${n / 1000}.${d}K"
}

/**
 * Barra de uso de la IA online: tokens gastados HOY / límite diario del modelo + peticiones restantes.
 * Se refresca sola cada vez que cambia el contador (sin recomponer nada más). Es una estimación local;
 * lo que Groq cuenta de verdad puede diferir (el límite es por cuenta de Groq, no por app).
 */
@Composable
fun AiUsageBar(model: OnlineModel, compact: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { OnlineAiStore(context) }
    var usage by remember(model) { mutableStateOf(store.usage(model)) }
    DisposableEffect(store, model) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> usage = store.usage(model) }
        store.usagePrefs.registerOnSharedPreferenceChangeListener(listener)
        usage = store.usage(model)   // por si cambió entre la composición y el registro
        onDispose { store.usagePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val scheme = MaterialTheme.colorScheme
    val barColor = if (usage.fraction >= 0.8f) scheme.error else scheme.primary
    val used = fmtTokens(usage.tokensUsed)
    val limit = fmtTokens(usage.tokenLimit)
    val remaining = usage.requestsRemaining ?: (usage.requestLimit - usage.requestsUsed).coerceAtLeast(0)
    Column(modifier) {
        LinearProgressIndicator(
            progress = { usage.fraction },
            modifier = Modifier.fillMaxWidth().height(if (compact) 4.dp else 6.dp),
            color = barColor, trackColor = scheme.surfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ai_online_usage_line, used, limit, remaining.toString()),
            fontSize = if (compact) 11.sp else 12.sp, color = scheme.onSurfaceVariant
        )
        if (!compact) Text(
            stringResource(R.string.ai_online_usage_reset, store.nextResetLocalLabel()),
            fontSize = 11.sp, color = scheme.onSurfaceVariant
        )
    }
}

/**
 * Aviso de privacidad del modo online. Con [onAccept] != null se muestra antes de activar el modo
 * (botones Aceptar / Ahora no); con null es solo lectura desde Ajustes (botón Cerrar).
 */
@Composable
fun AiOnlineTermsDialog(onAccept: (() -> Unit)?, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    @Composable fun Block(title: Int, body: Int) {
        Text(stringResource(title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_online_terms_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Block(R.string.ai_online_terms_what_t, R.string.ai_online_terms_what_b)
                Block(R.string.ai_online_terms_sent_t, R.string.ai_online_terms_sent_b)
                Block(R.string.ai_online_terms_key_t, R.string.ai_online_terms_key_b)
                Block(R.string.ai_online_terms_limits_t, R.string.ai_online_terms_limits_b)
                Block(R.string.ai_online_terms_license_t, R.string.ai_online_terms_license_b)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { runCatching { uri.openUri(GROQ_PRIVACY_URL) } }) { Text(stringResource(R.string.ai_online_terms_link_privacy), fontSize = 12.sp) }
                    TextButton(onClick = { runCatching { uri.openUri(GROQ_TERMS_URL) } }) { Text(stringResource(R.string.ai_online_terms_link_terms), fontSize = 12.sp) }
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

/** "Cómo obtener mi key": pasos para sacar una API key gratis de Groq, con botón que abre la página. */
@Composable
fun AiOnlineKeyHelpDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_online_help_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ai_online_help_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                listOf(
                    R.string.ai_online_help_step1, R.string.ai_online_help_step2,
                    R.string.ai_online_help_step3, R.string.ai_online_help_step4
                ).forEach {
                    Text(stringResource(it), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(R.string.ai_online_help_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { runCatching { uri.openUri(GROQ_KEYS_URL) } }) {
                Text(stringResource(R.string.ai_online_help_open), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_terms_close)) } }
    )
}
