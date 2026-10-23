package com.music.musicflame.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.musicflame.R
import com.music.musicflame.audio.Spatial8DAudioProcessor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AUDIO 8D + BALANCE (gratis) — ajustes del efecto que procesa Spatial8DAudioProcessor.
 *
 * Autocontenido: lee y guarda en las mismas SharedPreferences ("settings") que lee
 * MusicPlaybackService.syncSpatial8dState() (keys audio8d_enabled / audio8d_period_s /
 * audio8d_depth / audio8d_balance) y avisa con el broadcast "com.music.musicflame.UPDATE_EQ"
 * que el servicio ya escucha, igual que ProEqualizerDialog.
 */
@Composable
fun Audio8DDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val enabled = remember { mutableStateOf(prefs.getBoolean("audio8d_enabled", false)) }
    val period = remember {
        mutableFloatStateOf(
            prefs.getFloat("audio8d_period_s", Spatial8DAudioProcessor.DEFAULT_PERIOD_SECONDS)
        )
    }
    val depth = remember {
        mutableFloatStateOf(prefs.getFloat("audio8d_depth", Spatial8DAudioProcessor.DEFAULT_DEPTH))
    }
    val balance = remember { mutableFloatStateOf(prefs.getFloat("audio8d_balance", 0f)) }

    val minP = Spatial8DAudioProcessor.MIN_PERIOD_SECONDS
    val maxP = Spatial8DAudioProcessor.MAX_PERIOD_SECONDS

    fun push() {
        prefs.edit()
            .putBoolean("audio8d_enabled", enabled.value)
            .putFloat("audio8d_period_s", period.floatValue)
            .putFloat("audio8d_depth", depth.floatValue)
            .putFloat("audio8d_balance", balance.floatValue)
            .apply()
        context.sendBroadcast(Intent("com.music.musicflame.UPDATE_EQ").setPackage(context.packageName))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio8d_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.audio8d_enable),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = enabled.value,
                        onCheckedChange = {
                            enabled.value = it
                            push()
                        }
                    )
                }
                Text(
                    stringResource(R.string.audio8d_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))
                // Velocidad: derecha = más rápido (periodo más corto), por eso el mapeo invertido.
                Text(stringResource(R.string.audio8d_speed, period.floatValue.roundToInt()))
                Slider(
                    value = minP + maxP - period.floatValue,
                    onValueChange = { period.floatValue = minP + maxP - it },
                    onValueChangeFinished = { push() },
                    valueRange = minP..maxP
                )

                Text(stringResource(R.string.audio8d_intensity, (depth.floatValue * 100f).roundToInt()))
                Slider(
                    value = depth.floatValue,
                    onValueChange = { depth.floatValue = it },
                    onValueChangeFinished = { push() },
                    valueRange = 0f..1f
                )

                val b = balance.floatValue
                val balanceLabel = when {
                    abs(b) < 0.01f -> stringResource(R.string.audio8d_balance_center)
                    b < 0f -> stringResource(R.string.audio8d_balance_left, (-b * 100f).roundToInt())
                    else -> stringResource(R.string.audio8d_balance_right, (b * 100f).roundToInt())
                }
                Text(stringResource(R.string.audio8d_balance, balanceLabel))
                Slider(
                    value = balance.floatValue,
                    onValueChange = { balance.floatValue = if (abs(it) < 0.04f) 0f else it },
                    onValueChangeFinished = { push() },
                    valueRange = -1f..1f
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.audio8d_headphones_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            TextButton(onClick = {
                period.floatValue = Spatial8DAudioProcessor.DEFAULT_PERIOD_SECONDS
                depth.floatValue = Spatial8DAudioProcessor.DEFAULT_DEPTH
                balance.floatValue = 0f
                push()
            }) { Text(stringResource(R.string.audio8d_reset)) }
        }
    )
}
