package com.music.musicflame.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R

/** Sección "Copia de seguridad" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
internal fun LazyListScope.settingsCopySecuritySection(
    style: SettingsStyle,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    item { SettingsSectionHeader(style, stringResource(R.string.section_backup)) }

    item {
        Text(
            text = stringResource(R.string.settings_guarda_tus_ajustes_de_apariencia_e),
            fontSize = 13.sp,
            color = style.mediumEmphasis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    item {
        SettingsRow(style, SettingsRowPos.First) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_exportar_configuracion)) },
                supportingContent = { Text(stringResource(R.string.settings_guardar_como_archivo_json)) },
                trailingContent = { Icon(Icons.Filled.Save, contentDescription = null, tint = style.trailingColor) },
                colors = style.listItemColors,
                modifier = Modifier.clickable { onExport() }
            )
            SettingsDivider(style)
        }
    }

    item {
        SettingsRow(style, SettingsRowPos.Last) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_importar_configuracion)) },
                supportingContent = { Text(stringResource(R.string.settings_elegir_un_archivo_json_exportado_a)) },
                trailingContent = { Icon(Icons.Filled.FileOpen, contentDescription = null, tint = style.trailingColor) },
                colors = style.listItemColors,
                modifier = Modifier.clickable { onImport() }
            )
        }
    }
}
