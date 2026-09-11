package com.music.musicflame.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.musicflame.audio.ProBiquadEqualizerAudioProcessor
import com.music.musicflame.data.LicenseRepository
import com.music.musicflame.data.ProStatusHolder

/**
 * EQ PRO — pantalla de las 10 bandas de pago.
 *
 * Separado del diálogo del EQ gratis de 5 bandas (que sigue viviendo dentro de
 * SettingsScreen.kt, sin tocarlo) para no arriesgar ese código ya probado. Este archivo es
 * autocontenido: lee/escribe las mismas SharedPreferences ("settings") que usa
 * MusicPlaybackService (keys "pro_eq_band_0".."pro_eq_band_9", "pro_eq_preamp",
 * "pro_eq_exclusive") y manda el mismo broadcast "com.music.musicflame.UPDATE_EQ" que ya
 * escucha el servicio — con un extra nuevo, "pro_eq_bypass", que el servicio interpreta como
 * el botón de comparación A/B.
 *
 * GATE POR LICENCIA: se lee de ProStatusHolder.isProUnlocked (el mismo holder reactivo que ya
 * usa el resto de la app), NO se llama a LicenseRepository directo aquí — así, si el usuario
 * activa su key en la sección de Licencia de Ajustes mientras este diálogo está en pantalla,
 * se desbloquea solo, sin tener que cerrar y reabrir.
 */
@Composable
fun ProEqualizerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val isProUnlocked = ProStatusHolder.isProUnlocked

    val bandCount = ProBiquadEqualizerAudioProcessor.BAND_COUNT
    val freqLabels = remember {
        ProBiquadEqualizerAudioProcessor.BAND_CENTER_FREQUENCIES_HZ.map { hz ->
            if (hz >= 1000f) "${(hz / 1000f).let { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it) }}k"
            else hz.toInt().toString()
        }
    }

    val tempBands = remember {
        List(bandCount) { i -> mutableFloatStateOf(sharedPrefs.getFloat("pro_eq_band_$i", 0f)) }
    }
    val tempPreamp = remember { mutableFloatStateOf(sharedPrefs.getFloat("pro_eq_preamp", 0f)) }
    val exclusiveMode = remember { mutableStateOf(sharedPrefs.getBoolean("pro_eq_exclusive", true)) }
    val bypassEnabled = remember { mutableStateOf(false) } // transitorio: no se guarda entre sesiones

    fun applyCurrentState() {
        val editor = sharedPrefs.edit()
        for (i in 0 until bandCount) editor.putFloat("pro_eq_band_$i", tempBands[i].floatValue)
        editor.putFloat("pro_eq_preamp", tempPreamp.floatValue)
        editor.putBoolean("pro_eq_exclusive", exclusiveMode.value)
        editor.apply()

        val intent = Intent("com.music.musicflame.UPDATE_EQ")
        intent.setPackage(context.packageName)
        intent.putExtra("pro_eq_bypass", bypassEnabled.value)
        context.sendBroadcast(intent)
    }

    fun applyPreset(gains: FloatArray) {
        for (i in 0 until minOf(bandCount, gains.size)) tempBands[i].floatValue = gains[i]
        applyCurrentState()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text("EQ PRO — 10 Bandas", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)

                    if (isProUnlocked) {
                        Button(onClick = {
                            applyCurrentState()
                            Toast.makeText(context, "EQ PRO aplicado 🔥", Toast.LENGTH_SHORT).show()
                        }) { Text("Guardar", fontWeight = FontWeight.Bold) }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }

                if (!isProUnlocked) {
                    LockedProEqualizerContent(context)
                } else {
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Spacer(Modifier.height(4.dp)) }

                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Modo PRO exclusivo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "Apaga el ecualizador de 5 bandas mientras el PRO está sonando, para que no se sumen los dos.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = exclusiveMode.value,
                                            onCheckedChange = { exclusiveMode.value = it; applyCurrentState() }
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Comparar (A/B)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                if (bypassEnabled.value) "Sonando SIN EQ Pro (audio original)" else "Sonando CON EQ Pro",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = bypassEnabled.value,
                                            onCheckedChange = { bypassEnabled.value = it; applyCurrentState() }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text("Presets PRO", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            val presets = remember {
                                listOf(
                                    "Flat" to FloatArray(bandCount) { 0f },
                                    "Super Bass" to FloatArray(bandCount) { i -> if (i in 0..2) 9f else 0f },
                                    "Vocal Clarity" to FloatArray(bandCount) { i -> if (i in 5..6) 6f else 0f },
                                    "Treble Boost" to FloatArray(bandCount) { i -> if (i in 8..9) 8f else 0f }
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(presets) { (name, gains) ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.clickable { applyPreset(gains) }
                                    ) {
                                        Text(
                                            text = name,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Pre-amp (ganancia maestra)", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("${tempPreamp.floatValue.toInt()} dB", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Slider(
                                        value = tempPreamp.floatValue,
                                        onValueChange = { tempPreamp.floatValue = it },
                                        valueRange = ProBiquadEqualizerAudioProcessor.MIN_PREAMP_GAIN_DB..ProBiquadEqualizerAudioProcessor.MAX_PREAMP_GAIN_DB
                                    )
                                }
                            }
                        }

                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Ecualizador 10 Bandas", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(16.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        for (i in 0 until bandCount) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(freqLabels[i], fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(6.dp))
                                                Box(
                                                    modifier = Modifier.height(150.dp).width(28.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    VerticalSlider(
                                                        value = tempBands[i].floatValue,
                                                        onValueChange = { tempBands[i].floatValue = it },
                                                        valueRange = ProBiquadEqualizerAudioProcessor.MIN_BAND_GAIN_DB..ProBiquadEqualizerAudioProcessor.MAX_BAND_GAIN_DB,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text("${tempBands[i].floatValue.toInt()}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedProEqualizerContent(context: Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(56.dp).width(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("EQ PRO bloqueado", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "El ecualizador de 10 bandas con motor propio, pre-amp, presets y normalización de volumen entre canciones es una función de pago.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LicenseRepository.CHECKOUT_URL)))
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir el link de compra.", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Comprar EQ PRO", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "¿Ya tienes tu license key? Actívala en Ajustes → Licencia.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
