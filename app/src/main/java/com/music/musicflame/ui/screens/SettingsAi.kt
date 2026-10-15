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
import com.music.musicflame.data.LocalLlm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.music.musicflame.ui.theme.parseCustomTextColor

/**
 * Sección "IA" de Ajustes: API key, nombre de la IA, color de las burbujas del chat
 * (Adaptativo / Personalizado / Arcoíris lento) y fondo del chat. El chat se entera solo (ver AiChatScreen).
 */
internal fun LazyListScope.settingsAiSection(style: SettingsStyle) {

    // ---------- Motor de IA + modelo local ----------
    item {
        val context = LocalContext.current
        val ai = remember { AiChatSettings(context) }
        val scope = rememberCoroutineScope()
        var ready by remember { mutableStateOf(LocalLlm.isReady(context)) }
        var progress by remember { mutableStateOf<Float?>(null) }   // null = sin descarga en curso
        var job by remember { mutableStateOf<Job?>(null) }
        var showTerms by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }

        if (showTerms) AiTermsDialog(onAccept = null, onDismiss = { showTerms = false })
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_ai_local_delete_title)) },
            text = { Text(stringResource(R.string.settings_ai_local_delete_body)) },
            confirmButton = { TextButton(onClick = { LocalLlm.deleteModel(context); ready = false; confirmDelete = false }) { Text(stringResource(R.string.settings_ai_key_remove)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )

        SettingsSectionHeader(style, stringResource(R.string.settings_ai_engine_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp)) {
                    Text(LocalLlm.MODEL_NAME, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = style.highEmphasis)
                    Text(stringResource(R.string.settings_ai_local_desc, LocalLlm.APPROX_SIZE_LABEL), fontSize = 12.sp, color = style.mediumEmphasis)
                    Spacer(Modifier.height(10.dp))
                    val p = progress
                    when {
                        p != null -> {
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text("${(p * 100).toInt()}%", fontSize = 12.sp, color = style.mediumEmphasis)
                            OutlinedButton(onClick = { job?.cancel(); progress = null }) { Text(stringResource(R.string.action_cancel)) }
                        }
                        ready -> {
                            Text(stringResource(R.string.settings_ai_local_ready, LocalLlm.sizeOnDiskMb(context).toString()), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.settings_ai_local_delete)) }
                        }
                        else -> {
                            Text(stringResource(R.string.settings_ai_local_wifi), fontSize = 12.sp, color = style.mediumEmphasis)
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = {
                                progress = 0f
                                job = scope.launch {
                                    try {
                                        LocalLlm.download(context) { progress = it }
                                        ready = LocalLlm.isReady(context)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.settings_ai_local_fail, e.message ?: ""), Toast.LENGTH_LONG).show()
                                    } finally { progress = null }
                                }
                            }) { Text(stringResource(R.string.settings_ai_local_download), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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

    // ---------- Fondo del chat ----------
    item {
        val context = LocalContext.current
        val ai = remember { AiChatSettings(context) }
        var bg by remember { mutableStateOf(ai.getBackgroundUri()) }
        var dim by remember { mutableFloatStateOf(ai.getBackgroundDim()) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                if (ai.importBackground(uri)) bg = ai.getBackgroundUri()
                else Toast.makeText(context, context.getString(R.string.settings_ai_bg_error), Toast.LENGTH_SHORT).show()
            }
        }
        SettingsSectionHeader(style, stringResource(R.string.settings_ai_bg_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (bg != null) {
                AsyncImage(
                    model = bg, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_ai_bg_dim), fontSize = 12.sp, color = style.mediumEmphasis)
                Slider(value = dim, onValueChange = { dim = it }, onValueChangeFinished = { ai.saveBackgroundDim(dim) }, valueRange = 0f..0.8f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch("image/*") }) {
                    Text(stringResource(if (bg == null) R.string.settings_ai_bg_pick else R.string.settings_ai_bg_change), fontWeight = FontWeight.Bold)
                }
                if (bg != null) OutlinedButton(onClick = { ai.removeBackground(); bg = null }) { Text(stringResource(R.string.settings_ai_bg_remove)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
