package com.music.musicflame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import com.music.musicflame.data.AppLanguage

/** Nombre legible de cada idioma. Español/English siempre se muestran en su propio idioma. */
@Composable
fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.SPANISH -> stringResource(R.string.language_spanish)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
}

/**
 * Selector de idioma de la app (Ajustes > Apariencia > Idioma). Mismo estilo
 * que AppFontPickerDialog: una tarjeta con RadioButton por opción.
 * [onConfirm] se llama con la opción elegida solo si el usuario toca "Guardar".
 */
@Composable
fun LanguagePickerDialog(
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (AppLanguage) -> Unit
) {
    var tempLanguage by remember { mutableStateOf(currentLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.language_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                AppLanguage.entries.forEach { language ->
                    LanguageCard(
                        label = appLanguageLabel(language),
                        isSelected = tempLanguage == language,
                        onClick = { tempLanguage = language }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tempLanguage) }) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun LanguageCard(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) accentColor.copy(alpha = 0.08f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        RadioButton(selected = isSelected, onClick = onClick, modifier = Modifier.width(36.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
