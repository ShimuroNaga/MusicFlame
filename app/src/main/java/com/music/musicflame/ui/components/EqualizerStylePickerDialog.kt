package com.music.musicflame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Selector de estilo de ecualizador gráfico: una tabla (grilla de 2 columnas)
 * con una tarjeta por estilo, cada una con su nombre, una línea de
 * descripción y una animación pequeña y en vivo de cómo se vería (dibujada
 * con datos falsos vía [rememberFakeEqualizerLevels], no requiere que haya
 * música sonando para poder previsualizar).
 *
 * Uso: mostrar cuando el usuario toca "Estilo de ecualizador gráfico" en
 * Ajustes > Apariencia. [currentStyle] es el ya guardado; [onConfirm] se
 * llama con el nuevo estilo elegido solo si el usuario toca "Guardar".
 */
@Composable
fun EqualizerStylePickerDialog(
    currentStyle: EqualizerStyle,
    onDismiss: () -> Unit,
    onConfirm: (EqualizerStyle) -> Unit
) {
    var tempStyle by remember { mutableStateOf(currentStyle) }
    val previewColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Estilo de ecualizador gráfico", fontWeight = FontWeight.Bold) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(EqualizerStyle.entries.toList()) { style ->
                    EqualizerStyleCard(
                        style = style,
                        isSelected = tempStyle == style,
                        previewColor = previewColor,
                        onClick = { tempStyle = style }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tempStyle) }) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun EqualizerStyleCard(
    style: EqualizerStyle,
    isSelected: Boolean,
    previewColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    // Cada tarjeta tiene su PROPIA fuente de niveles falsos (fase/semilla
    // propia por tarjeta gracias a `remember` por instancia), así todas las
    // vistas previas de la grilla se mueven al mismo tiempo pero no de forma
    // idéntica entre sí — se ve más vivo que si todas latieran exactamente igual.
    val previewLevels = rememberFakeEqualizerLevels(barCount = if (style == EqualizerStyle.PARTICLES) 14 else 20)

    val borderColor = if (isSelected) previewColor else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) previewColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
        ) {
            EqualizerCanvas(
                style = style,
                spectrum = previewLevels,
                color = previewColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.width(28.dp)
            )
            Text(style.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            style.description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 2.dp)
        )
    }
}
