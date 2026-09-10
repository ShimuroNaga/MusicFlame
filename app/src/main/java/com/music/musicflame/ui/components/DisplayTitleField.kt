package com.music.musicflame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Campo "Subtítulo de visualización" para EditSongDialog.
 * El usuario escribe libremente y puede insertar códigos § desde el
 * desplegable (icono de paleta). Lo que se guarde aquí va a
 * DisplayTitleRepository, nunca al tag real del mp3.
 *
 * [value] / [onValueChange] siguen el patrón estándar de TextField state.
 */
@Composable
fun DisplayTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("Subtítulo de visualización (solo en la app)") },
        placeholder = { Text("Ej: §bMi Canción§r") },
        supportingText = { Text("No modifica el archivo mp3, solo cómo se ve aquí") },
        trailingIcon = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = "Insertar código de formato")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    LazyColumn(modifier = Modifier.size(width = 220.dp, height = 280.dp)) {
                        items(FormatCodes.examples()) { example ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (example.previewColor != null) {
                                            // Antes este Box no tenía color de fondo, así que
                                            // el cuadrito de muestra quedaba invisible; ahora sí
                                            // pinta el color real de ese código §.
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(example.previewColor, CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text("${example.code}  —  ${example.label}")
                                    }
                                },
                                onClick = {
                                    onValueChange(value + example.code)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}
