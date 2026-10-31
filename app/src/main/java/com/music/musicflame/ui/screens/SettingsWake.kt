package com.music.musicflame.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import com.music.musicflame.data.AiChatSettings
import com.music.musicflame.voice.VoiceSettings
import com.music.musicflame.voice.wake.WakeEnrollDialog
import com.music.musicflame.voice.wake.WakeNameCheck
import com.music.musicflame.voice.wake.WakeSensitivity
import com.music.musicflame.voice.wake.WakeState
import com.music.musicflame.voice.wake.WakeTemplates
import com.music.musicflame.voice.wake.WakeTestDialog
import com.music.musicflame.voice.wake.WakeWord
import com.music.musicflame.voice.wake.WakeWordService

/**
 * Ajustes > IA > "Escuchar siempre": interruptor (apagado por defecto), avisos, registro guiado de "oye <nombre>",
 * botón "Probar nombre" y sensibilidad. Va justo debajo de la tarjeta "Asistente de voz".
 */
internal fun LazyListScope.settingsWakeSection(style: SettingsStyle) {
    item {
        val context = LocalContext.current
        val voice = remember { VoiceSettings(context) }
        var aiName by remember { mutableStateOf(AiChatSettings(context).getName()) }
        var enabled by remember { mutableStateOf(voice.isWakeEnabled()) }
        var sens by remember { mutableStateOf(voice.getWakeSensitivity()) }
        var hasTemplates by remember { mutableStateOf(WakeTemplates.hasTemplates(context)) }
        var ready by remember { mutableStateOf(WakeTemplates.isReadyFor(context, aiName)) }
        var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        var showEnroll by remember { mutableStateOf(false) }
        var showTest by remember { mutableStateOf(false) }
        var showOverlayAsk by remember { mutableStateOf(false) }
        var enableAfterEnroll by remember { mutableStateOf(false) }
        val state by WakeWord.state.collectAsState()
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

        // Al volver de los ajustes del sistema (permiso "sobre otras apps") o de cambiar el nombre de la IA.
        DisposableEffect(lifecycleOwner) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    aiName = AiChatSettings(context).getName()
                    enabled = voice.isWakeEnabled()
                    hasTemplates = WakeTemplates.hasTemplates(context)
                    ready = WakeTemplates.isReadyFor(context, aiName)
                    overlayOk = Settings.canDrawOverlays(context)
                    // Si cambiaste el nombre de la IA, las grabaciones ya no sirven: se apaga hasta volver a grabar.
                    if (enabled && !ready) { WakeWordService.stop(context); enabled = false }
                }
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }
        // El botón "Apagar" de la notificación también apaga el interruptor.
        LaunchedEffect(state) { enabled = voice.isWakeEnabled() }

        fun startNow() {
            voice.setWakeEnabled(true)
            enabled = true
            if (!WakeWordService.start(context)) {
                voice.setWakeEnabled(false); enabled = false
                Toast.makeText(context, context.getString(R.string.wake_reactivate_failed), Toast.LENGTH_LONG).show()
            }
        }
        fun afterReady() {
            if (!overlayOk) showOverlayAsk = true else startNow()
        }
        fun afterMic() {
            if (!ready) { enableAfterEnroll = true; showEnroll = true } else afterReady()
        }
        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) afterMic() else Toast.makeText(context, context.getString(R.string.wake_no_mic_permission), Toast.LENGTH_SHORT).show()
        }
        fun turnOn() {
            if (!voice.isEnabled()) {
                Toast.makeText(context, context.getString(R.string.wake_need_assistant), Toast.LENGTH_SHORT).show()
                return
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else afterMic()
        }

        if (showEnroll) WakeEnrollDialog(aiName = aiName, onFinished = { saved ->
            showEnroll = false
            hasTemplates = WakeTemplates.hasTemplates(context)
            ready = WakeTemplates.isReadyFor(context, aiName)
            if (saved && ready) {
                if (enabled) WakeWordService.start(context)          // ya activa: recarga con tu voz nueva
                else if (enableAfterEnroll) afterReady()
            }
            enableAfterEnroll = false
        })
        if (showTest) WakeTestDialog(aiName = aiName, onClose = { showTest = false })
        if (showOverlayAsk) AlertDialog(
            onDismissRequest = { showOverlayAsk = false; startNow() },
            title = { Text(stringResource(R.string.wake_overlay_title)) },
            text = { Text(stringResource(R.string.wake_overlay_body, aiName)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayAsk = false
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                    startNow()
                }) { Text(stringResource(R.string.wake_overlay_open)) }
            },
            dismissButton = { TextButton(onClick = { showOverlayAsk = false; startNow() }) { Text(stringResource(R.string.wake_overlay_skip)) } }
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.wake_card_title, aiName), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = style.highEmphasis)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.wake_card_desc, aiName), fontSize = 12.sp, color = style.mediumEmphasis)
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.wake_enable), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = style.highEmphasis)
                            Text(stringResource(R.string.wake_enable_sub), fontSize = 12.sp, color = style.mediumEmphasis)
                        }
                        Switch(checked = enabled, onCheckedChange = { on ->
                            if (on) turnOn() else { WakeWordService.stop(context); enabled = false }
                        })
                    }

                    // Estado en vivo: es el indicador de cuándo está escuchando.
                    Spacer(Modifier.height(6.dp))
                    val statusRes = when (state) {
                        WakeState.OFF -> if (enabled) R.string.wake_status_stopped else R.string.wake_status_off
                        WakeState.STARTING -> R.string.wake_status_starting
                        WakeState.LISTENING -> R.string.wake_status_listening
                        WakeState.PAUSED_ASSISTANT -> R.string.wake_status_assistant
                        WakeState.PAUSED_CALL -> R.string.wake_status_call
                        WakeState.PAUSED_LOUD_MUSIC -> R.string.wake_status_loud
                        WakeState.PAUSED_BUSY_MIC -> R.string.wake_status_mic_busy
                        WakeState.PAUSED_TEST -> R.string.wake_status_test
                    }
                    Text(
                        stringResource(statusRes, aiName), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (state == WakeState.LISTENING) MaterialTheme.colorScheme.primary
                        else if (enabled && state == WakeState.OFF) MaterialTheme.colorScheme.error else style.mediumEmphasis
                    )
                    if (enabled && state == WakeState.OFF) {
                        OutlinedButton(onClick = {
                            if (!WakeWordService.start(context)) Toast.makeText(context, context.getString(R.string.wake_reactivate_failed), Toast.LENGTH_LONG).show()
                        }) { Text(stringResource(R.string.wake_reactivate)) }
                    }

                    // Avisos junto al interruptor.
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.wake_warn_battery), fontSize = 12.sp, color = style.mediumEmphasis)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.wake_warn_notif), fontSize = 12.sp, color = style.mediumEmphasis)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.wake_privacy, aiName), fontSize = 12.sp, color = style.mediumEmphasis)
                    if (!overlayOk) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.wake_overlay_missing_status), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    // Aviso suave sobre el nombre: nunca bloquea nada.
                    val hint = WakeNameCheck.hint(aiName)
                    if (hint != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(
                                when (hint) {
                                    WakeNameCheck.Hint.SHORT -> R.string.wake_name_hint_short
                                    WakeNameCheck.Hint.ONE_SYLLABLE -> R.string.wake_name_hint_one_syl
                                    WakeNameCheck.Hint.LONG -> R.string.wake_name_hint_long
                                    WakeNameCheck.Hint.SYMBOLS -> R.string.wake_name_hint_symbols
                                }, aiName
                            ),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (hasTemplates && !ready) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.wake_name_changed, aiName), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }

                    // Grabación guiada y prueba.
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Button(onClick = {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else { enableAfterEnroll = false; showEnroll = true }
                        }) { Text(stringResource(if (ready) R.string.wake_rerecord_button else R.string.wake_record_button, aiName)) }
                        OutlinedButton(onClick = {
                            if (!ready) Toast.makeText(context, context.getString(R.string.wake_needs_recording, aiName), Toast.LENGTH_SHORT).show()
                            else showTest = true
                        }) { Text(stringResource(R.string.wake_test_button)) }
                    }
                    if (hasTemplates) {
                        TextButton(onClick = {
                            WakeWordService.stop(context)
                            WakeTemplates.clear(context)
                            enabled = false; ready = false; hasTemplates = false
                            Toast.makeText(context, context.getString(R.string.wake_deleted), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.wake_delete_button)) }
                    }

                    // Sensibilidad.
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.wake_sens_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = style.highEmphasis)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WakeSensitivity.values().forEach { s ->
                            FilterChip(
                                selected = sens == s,
                                onClick = { sens = s; voice.setWakeSensitivity(s) },
                                label = {
                                    Text(
                                        stringResource(when (s) {
                                            WakeSensitivity.LOW -> R.string.wake_sens_low
                                            WakeSensitivity.MEDIUM -> R.string.wake_sens_medium
                                            WakeSensitivity.HIGH -> R.string.wake_sens_high
                                        }), fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(when (sens) {
                            WakeSensitivity.LOW -> R.string.wake_sens_low_desc
                            WakeSensitivity.MEDIUM -> R.string.wake_sens_medium_desc
                            WakeSensitivity.HIGH -> R.string.wake_sens_high_desc
                        }), fontSize = 12.sp, color = style.mediumEmphasis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.wake_without_always), fontSize = 12.sp, color = style.mediumEmphasis)
                }
            }
        }
    }
}
