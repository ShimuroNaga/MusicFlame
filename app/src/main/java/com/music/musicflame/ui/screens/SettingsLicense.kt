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

/** Sección "Licencias de terceros" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
internal fun LazyListScope.settingsLicenseSection(style: SettingsStyle) {
    item { SettingsSectionHeader(style, stringResource(R.string.section_third_party_licenses)) }
    item { ThirdPartyLicensesCard() }
}
