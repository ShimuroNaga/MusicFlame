package com.music.musicflame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Selector de tamaño de letra global: una barra deslizante (12sp a 22sp,
 * pasos de 1 en 1) más una vista previa en vivo que se redibuja al momento
 * con el tamaño y la fuente que tenga elegidos el usuario ([previewFontFamily],
 * ver AppFont) — así se ve exactamente cómo va a quedar antes de guardar.
 *
 * El valor guardado es el tamaño de referencia de bodyLarge en sp; el resto
 * de la escala de Material 3 (títulos, encabezados, etc.) se reescala en
 * proporción vía appTypographyFor() en Type.kt, así que la vista previa acá
 * solo necesita mostrar el tamaño "base" para dar una idea representativa.
 *
 * Uso: mostrar cuando el usuario toca "Tamaño de letra" en Ajustes >
 * Apariencia. [currentSizeSp] es el ya guardado; [onConfirm] se llama con el
 * nuevo tamaño solo si el usuario toca "Guardar". No es una función de pago
 * (afecta la usabilidad de toda la app, no solo estética).
 */
@Composable
fun AppFontSizeDialog(
    currentSizeSp: Float,
    previewFontFamily: FontFamily,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var tempSizeSp by remember { mutableFloatStateOf(currentSizeSp) }
    val minSp = 12f
    val maxSp = 22f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tamaño de letra", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Cambia el tamaño de todos los textos de la app (menús, títulos, letras). Gratis.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Vista previa en vivo: mismo tamaño y fuente que se aplicarían.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "MusicFlame — Aa 123",
                        fontFamily = previewFontFamily,
                        fontSize = tempSizeSp.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer12dp()

                Slider(
                    value = tempSizeSp,
                    onValueChange = { tempSizeSp = it },
                    valueRange = minSp..maxSp,
                    steps = (maxSp - minSp).toInt() - 1, // pasos de 1 en 1 sp
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pequeño", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${tempSizeSp.toInt()} sp",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Grande", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tempSizeSp) }) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun Spacer12dp() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
}
