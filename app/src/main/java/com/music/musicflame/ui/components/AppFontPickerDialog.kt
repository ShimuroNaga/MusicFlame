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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.ui.theme.AppFont

/**
 * Selector de tipo de letra para toda la app: una lista con una tarjeta por
 * fuente, cada una mostrando su propio nombre escrito CON esa fuente (para
 * previsualizarla de verdad, no solo el nombre en la fuente del sistema).
 *
 * Uso: mostrar cuando el usuario toca "Tipo de letra" en Ajustes >
 * Apariencia. [currentFont] es la ya guardada; [onConfirm] se llama con la
 * nueva fuente elegida solo si el usuario toca "Guardar". Mismo patrón que
 * EqualizerStylePickerDialog (grid -> lista, porque el nombre de la fuente
 * necesita más ancho para leerse bien previsualizado).
 */
@Composable
fun AppFontPickerDialog(
    currentFont: AppFont,
    isUnlocked: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (AppFont) -> Unit,
    onLockedFontClick: () -> Unit = {}
) {
    var tempFont by remember { mutableStateOf(currentFont) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tipo de letra", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (!isUnlocked) {
                    Text(
                        "Roboto, Lato, Open Sans, Inter, Asap Sharp y Nunito son gratis. Las demás son de pago (Ajustes > Pagos).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(AppFont.entries.toList()) { font ->
                        val locked = !isUnlocked && !font.isFree
                        AppFontCard(
                            font = font,
                            isSelected = tempFont == font,
                            locked = locked,
                            onClick = { if (locked) onLockedFontClick() else tempFont = font }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tempFont) }) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun AppFontCard(
    font: AppFont,
    isSelected: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val contentAlpha = if (locked) 0.5f else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            enabled = !locked,
            modifier = Modifier.width(36.dp)
        )
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            // El nombre se dibuja CON la fuente real que representa: la
            // preview en sí misma.
            Text(
                font.displayName,
                fontFamily = font.fontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "MusicFlame",
                fontFamily = font.fontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (locked) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(56.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Bloqueado",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text("$5 MXN", fontSize = 9.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
