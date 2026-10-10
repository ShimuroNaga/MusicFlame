package com.music.musicflame.ui.components

import com.music.musicflame.R
import androidx.compose.ui.res.stringResource
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
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val codeExamples = remember(appContext.resources.configuration.locales.toLanguageTags()) { FormatCodes.examples(appContext) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(stringResource(R.string.dt_label)) },
        placeholder = { Text(stringResource(R.string.dt_placeholder)) },
        supportingText = { Text(stringResource(R.string.dt_support)) },
        trailingIcon = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.dt_insert_code))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    LazyColumn(modifier = Modifier.size(width = 220.dp, height = 280.dp)) {
                        items(codeExamples) { example ->
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
