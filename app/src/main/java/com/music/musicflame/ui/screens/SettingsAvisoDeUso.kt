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

/** Sección "Aviso de Uso" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
internal fun LazyListScope.settingsAvisoDeUsoSection() {
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_aviso_de_uso_y_redistribucion),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_musicflame_es_un_proyecto_personal),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(10.dp))

                val avisoReglas = listOf(
                    stringResource(R.string.settings_contacto_previo_obligatorio_cualqu),
                    stringResource(R.string.settings_reparto_de_ingresos_si_dicha_redis),
                    stringResource(R.string.settings_creditos_intactos_no_esta_permitid),
                    stringResource(R.string.settings_sin_venta_no_autorizada_musicflame),
                    stringResource(R.string.settings_incumplimiento_toda_redistribucion)
                )

                avisoReglas.forEach { regla ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("•  ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(regla, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_para_solicitar_autorizacion_o_coor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
