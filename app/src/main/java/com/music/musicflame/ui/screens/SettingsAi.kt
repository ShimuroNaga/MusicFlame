package com.music.musicflame.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.music.musicflame.R
import com.music.musicflame.data.AiChatSettings
import com.music.musicflame.data.AiModel
import com.music.musicflame.data.LocalLlm
import com.music.musicflame.data.OnlineAiStore
import com.music.musicflame.data.OnlineErrorKind
import com.music.musicflame.data.OnlineLlm
import com.music.musicflame.data.OnlineModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.music.musicflame.ui.theme.parseCustomTextColor

/**
 * Sección "IA" de Ajustes: API key, nombre de la IA, color de las burbujas del chat
 * (Adaptativo / Personalizado / Arcoíris lento) del chat. El chat se entera solo (ver AiChatScreen).
 */
internal fun LazyListScope.settingsAiSection(style: SettingsStyle) {

    // ---------- ¿Qué IA usar? Offline (modelo descargado) u Online (Groq con la key del usuario) ----------
    item {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val ai = remember { AiChatSettings(context) }
        val store = remember { OnlineAiStore(context) }
        var online by remember { mutableStateOf(ai.isOnlineMode()) }
        var model by remember { mutableStateOf(ai.getOnlineModel()) }
        var hasKey by remember { mutableStateOf(false) }
        var keyInput by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var showConsent by remember { mutableStateOf(false) }
        var showOnlineTerms by remember { mutableStateOf(false) }
        var showKeyHelp by remember { mutableStateOf(false) }

        // La key vive cifrada: consultarla abre el Keystore, así que se hace en IO y no al componer.
        LaunchedEffect(Unit) { hasKey = withContext(Dispatchers.IO) { store.hasApiKey() } }

        if (showConsent) AiOnlineTermsDialog(
            onAccept = { ai.saveOnlineTermsAccepted(true); ai.saveOnlineMode(true); online = true; showConsent = false },
            onDismiss = { showConsent = false }
        )
        if (showOnlineTerms) AiOnlineTermsDialog(onAccept = null, onDismiss = { showOnlineTerms = false })
        if (showKeyHelp) AiOnlineKeyHelpDialog(onDismiss = { showKeyHelp = false })

        SettingsSectionHeader(style, stringResource(R.string.settings_ai_mode_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.settings_ai_mode_hint), fontSize = 12.sp, color = style.mediumEmphasis)
            Spacer(Modifier.height(6.dp))
            AiModeRow(
                selected = !online, style = style,
                title = stringResource(R.string.settings_ai_mode_offline),
                subtitle = stringResource(R.string.settings_ai_mode_offline_sub)
            ) { online = false; ai.saveOnlineMode(false) }
            AiModeRow(
                selected = online, style = style,
                title = stringResource(R.string.settings_ai_mode_online),
                subtitle = stringResource(R.string.settings_ai_mode_online_sub)
            ) {
                // Antes de mandar nada a internet, el usuario acepta el aviso de privacidad (una sola vez).
                if (ai.isOnlineTermsAccepted()) { online = true; ai.saveOnlineMode(true) } else showConsent = true
            }

            if (online) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_ai_online_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = style.highEmphasis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(if (hasKey) R.string.settings_ai_key_saved else R.string.settings_ai_key_missing),
                            fontSize = 13.sp,
                            color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it.filter { c -> !c.isWhitespace() } },   // pegar con salto de línea/espacios no la rompe
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.settings_ai_key_label)) },
                            supportingText = { Text(stringResource(R.string.settings_ai_key_hint)) }
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = keyInput.isNotBlank() && !busy,
                                onClick = {
                                    val k = keyInput
                                    busy = true
                                    scope.launch {
                                        val saved = withContext(Dispatchers.IO) { store.saveApiKey(k) }
                                        if (!saved) {
                                            Toast.makeText(context, context.getString(R.string.settings_ai_key_save_fail), Toast.LENGTH_LONG).show()
                                            busy = false
                                            return@launch
                                        }
                                        hasKey = true; keyInput = ""
                                        // Comprueba la key al instante (no gasta tokens). Si Groq dice que no sirve, no se queda guardada.
                                        val problem = OnlineLlm.verifyKey(k)
                                        if (problem == OnlineErrorKind.INVALID_KEY) {
                                            withContext(Dispatchers.IO) { store.clearApiKey() }
                                            hasKey = false
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(when (problem) {
                                                null -> R.string.settings_ai_key_ok
                                                OnlineErrorKind.INVALID_KEY -> R.string.settings_ai_key_invalid
                                                else -> R.string.settings_ai_key_saved_no_check
                                            }),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        busy = false
                                    }
                                }
                            ) { Text(stringResource(R.string.settings_ai_key_save), fontWeight = FontWeight.Bold) }
                            if (hasKey) OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { store.clearApiKey() }
                                        hasKey = false
                                    }
                                }
                            ) { Text(stringResource(R.string.settings_ai_key_remove)) }
                        }
                        TextButton(onClick = { showKeyHelp = true }) { Text(stringResource(R.string.settings_ai_key_howto)) }

                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.settings_ai_online_model_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = style.highEmphasis)
                        OnlineModel.values().forEach { m ->
                            AiModeRow(
                                selected = model == m, style = style, title = m.displayName,
                                subtitle = stringResource(
                                    if (m == OnlineModel.GPT_OSS_120B) R.string.settings_ai_online_model_120b else R.string.settings_ai_online_model_20b
                                )
                            ) { model = m; ai.saveOnlineModel(m) }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.settings_ai_online_usage_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = style.highEmphasis)
                        Spacer(Modifier.height(6.dp))
                        AiUsageBar(model = model)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.settings_ai_online_fallback), fontSize = 12.sp, color = style.mediumEmphasis)
                        TextButton(onClick = { showOnlineTerms = true }) { Text(stringResource(R.string.settings_ai_online_terms_button)) }
                    }
                }
            }
        }
    }

    // ---------- Motor de IA + modelo local (solo UNO descargado a la vez) ----------
    item {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var installed by remember { mutableStateOf(LocalLlm.installed(context)) }
        var downloading by remember { mutableStateOf<AiModel?>(null) }   // null = sin descarga en curso
        var progress by remember { mutableStateOf(0f) }
        var job by remember { mutableStateOf<Job?>(null) }
        var showTerms by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }

        if (showTerms) AiTermsDialog(onAccept = null, onDismiss = { showTerms = false })
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_ai_local_delete_title)) },
            text = { Text(stringResource(R.string.settings_ai_local_delete_body)) },
            confirmButton = { TextButton(onClick = { LocalLlm.deleteModel(context); installed = null; confirmDelete = false }) { Text(stringResource(R.string.settings_ai_key_remove)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )

        SettingsSectionHeader(style, stringResource(R.string.settings_ai_engine_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.settings_ai_models_hint), fontSize = 12.sp, color = style.mediumEmphasis)
            Spacer(Modifier.height(8.dp))
            AiModel.values().forEach { m ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(m.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = style.highEmphasis)
                        Text(stringResource(R.string.settings_ai_model_meta, m.sizeLabel, m.licenseName), fontSize = 12.sp, color = style.mediumEmphasis)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(m.descRes), fontSize = 12.sp, color = style.mediumEmphasis)
                        Spacer(Modifier.height(10.dp))
                        when {
                            downloading == m -> {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(4.dp))
                                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = style.mediumEmphasis)
                                OutlinedButton(onClick = { job?.cancel(); downloading = null }) { Text(stringResource(R.string.action_cancel)) }
                            }
                            installed == m -> {
                                Text(stringResource(R.string.settings_ai_local_ready, LocalLlm.sizeOnDiskMb(context).toString()), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.settings_ai_local_delete)) }
                            }
                            installed != null || downloading != null -> {
                                // Ya hay otro modelo (o una descarga en curso): este queda bloqueado hasta borrar el actual.
                                Text(stringResource(R.string.settings_ai_model_locked), fontSize = 12.sp, color = style.mediumEmphasis)
                            }
                            else -> {
                                Text(stringResource(R.string.settings_ai_local_wifi), fontSize = 12.sp, color = style.mediumEmphasis)
                                Spacer(Modifier.height(6.dp))
                                Button(onClick = {
                                    progress = 0f
                                    downloading = m
                                    job = scope.launch {
                                        try {
                                            LocalLlm.download(context, m) { progress = it }
                                            installed = LocalLlm.installed(context)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.settings_ai_local_fail, e.message ?: ""), Toast.LENGTH_LONG).show()
                                        } finally { downloading = null }
                                    }
                                }) { Text(stringResource(R.string.settings_ai_local_download), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = { showTerms = true }) { Text(stringResource(R.string.settings_ai_terms_button)) }
        }
    }

    // ---------- Nombre ----------
    item {
        val context = LocalContext.current
        val ai = remember { AiChatSettings(context) }
        var name by remember { mutableStateOf(ai.getName()) }
        SettingsSectionHeader(style, stringResource(R.string.settings_ai_name_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24); ai.saveName(name) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_ai_name_label)) }
            )
        }
    }

    // ---------- Color de burbujas ----------
    item {
        val context = LocalContext.current
        val ai = remember { AiChatSettings(context) }
        var mode by remember { mutableStateOf(ai.getBubbleMode()) }
        var hex by remember { mutableStateOf(ai.getBubbleHex()) }
        SettingsSectionHeader(style, stringResource(R.string.settings_ai_bubbles_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            listOf(
                AiChatSettings.MODE_ADAPTIVE to R.string.settings_ai_bubbles_adaptive,
                AiChatSettings.MODE_CUSTOM to R.string.settings_ai_bubbles_custom,
                AiChatSettings.MODE_RAINBOW to R.string.settings_ai_bubbles_rainbow
            ).forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { mode = value; ai.saveBubbleMode(value) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mode == value, onClick = { mode = value; ai.saveBubbleMode(value) })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label), fontSize = 14.sp, color = style.highEmphasis)
                }
            }
            if (mode == AiChatSettings.MODE_CUSTOM) {
                Spacer(Modifier.height(6.dp))
                val presets = listOf(
                    "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                    "#009688", "#4CAF50", "#8BC34A", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E"
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        val selected = hex.equals(p, ignoreCase = true)
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(parseCustomTextColor(p))
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, RoundedCornerShape(8.dp))
                                .clickable { hex = p; ai.saveBubbleHex(p) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = hex, onValueChange = { hex = it; ai.saveBubbleHex(it) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_hex_rrggbb_o_rgba_r_g_b_a)) }
                )
            }
            if (mode == AiChatSettings.MODE_RAINBOW) {
                Text(stringResource(R.string.settings_ai_bubbles_rainbow_desc), fontSize = 11.sp, color = style.mediumEmphasis, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** Fila con radio + título + descripción corta (modo de IA y modelo online). */
@Composable
private fun AiModeRow(selected: Boolean, style: SettingsStyle, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.padding(top = 2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = style.highEmphasis)
            Text(subtitle, fontSize = 12.sp, color = style.mediumEmphasis)
        }
    }
}
