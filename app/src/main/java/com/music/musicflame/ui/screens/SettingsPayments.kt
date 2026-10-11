package com.music.musicflame.ui.screens

import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.music.musicflame.R
import com.music.musicflame.audio.EqPresets
import com.music.musicflame.data.AppIconManager
import com.music.musicflame.data.DeviceInfoProvider
import com.music.musicflame.data.LicenseRepository
import com.music.musicflame.data.LicenseStatus
import com.music.musicflame.data.LicenseValidationResult
import com.music.musicflame.data.SavingsGoalRepository
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.together.SupabaseSalas
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.widget.MusicFlameWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sección "Pagos (opcional)" de Ajustes. Código movido tal cual desde SettingsScreen.kt (sin cambios de comportamiento). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun LazyListScope.settingsPaymentsSection(
    style: SettingsStyle,
    context: Context,
    licenseRepo: LicenseRepository,
    licenseInputState: MutableState<String>,
    savedLicensesState: MutableState<List<com.music.musicflame.data.SavedLicense>>,
    isValidatingState: MutableState<Boolean>,
    errorMessageState: MutableState<String?>,
    paymentsScope: kotlinx.coroutines.CoroutineScope,
    paymentsSectionLocked: Boolean,
    unlockedIds: Set<String>
) {
    var licenseInput by licenseInputState
    var savedLicenses by savedLicensesState
    var isValidating by isValidatingState
    var errorMessage by errorMessageState

    item { SettingsSectionHeader(style, stringResource(R.string.settings_licencia_de_apoyo_opcional)) }

    // --- PASO A PASO: cómo comprar y activar ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_como_funciona),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    stringResource(R.string.settings_es_pago_unico_no_es_suscripcion_pa),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                val steps = listOf(
                    stringResource(R.string.settings_elegi_que_comprar_un_producto_suel, com.music.musicflame.data.PaymentCatalog.ITEMS.size),
                    stringResource(R.string.settings_toca_comprar_se_abre_el_checkout_d),
                    stringResource(R.string.settings_paga_ahi_con_tarjeta_o_el_metodo_q),
                    stringResource(R.string.settings_te_llega_un_correo_con_tu_license),
                    stringResource(R.string.settings_volve_a_la_app_pega_esa_key_en_tus)
                )
                steps.forEachIndexed { index, step ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(step, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_compraste_varios_productos_sueltos),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    item {
        Text(
            stringResource(R.string.settings_compra_solo_lo_que_te_interesa_o_t, com.music.musicflame.data.PaymentCatalog.TOTAL_PRICE_MXN),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    // --- "COMPRAR TODO" (producto TODO de Lemon Squeezy) ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(if (paymentsSectionLocked) 0.6f else 1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_una_sola_compra_de_todo_desbloquea, com.music.musicflame.data.PaymentCatalog.ITEMS.size),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                if (paymentsSectionLocked) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_disponible_proximamente_esta_secci),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val hasEverything = com.music.musicflame.data.PaymentCatalog.ITEMS.all { unlockedIds.contains(it.id) }
                if (hasEverything) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_ya_tenes_todo_desbloqueado),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(com.music.musicflame.data.LicenseRepository.CHECKOUT_URL_TODO))
                            context.startActivity(intent)
                        },
                        enabled = !paymentsSectionLocked,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_comprar_todo_por_1_s_mxn, com.music.musicflame.data.PaymentCatalog.TOTAL_PRICE_MXN), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- TABLA DEL CATÁLOGO: estado real por ítem + botón "Comprar" por producto ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_o_compra_por_separado),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    stringResource(R.string.settings_cada_fila_es_un_producto_real_de_l),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Un "producto" = una entrada del mapa (no un ítem del catálogo
                // suelto), así que si 2 ítems están agrupados aparecen en UNA
                // sola fila con un solo botón "Comprar" (mismo checkout link).
                com.music.musicflame.data.LicenseRepository.PRODUCT_NAME_TO_CATALOG_IDS
                    .filterKeys { it != "TODO" }
                    .forEach { (productName, ids) ->
                        val itemsInProduct = com.music.musicflame.data.PaymentCatalog.ITEMS.filter { it.id in ids }
                        val label = itemsInProduct.joinToString(" + ") { it.displayLabel(context) }
                        val priceMxn = itemsInProduct.sumOf { it.priceMxn }
                        val owned = ids.all { unlockedIds.contains(it) }
                        val checkoutUrl = com.music.musicflame.data.LicenseRepository.PRODUCT_NAME_TO_CHECKOUT_URL[productName]

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(
                                if (owned) Icons.Filled.CloudDone else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (owned) {
                                Text(
                                    stringResource(R.string.settings_ya_la_tenes),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        if (checkoutUrl != null) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                                        }
                                    },
                                    enabled = !paymentsSectionLocked && checkoutUrl != null,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("$$priceMxn MXN", fontSize = 12.sp)
                                }
                            }
                        }
                    }
            }
        }
    }

    // --- LICENCIAS GUARDADAS + CAMPO PARA PEGAR UNA NUEVA ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(if (paymentsSectionLocked) 0.6f else 1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_tus_licencias),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    stringResource(R.string.settings_cada_compra_te_llega_por_correo_co),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (savedLicenses.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_todavia_no_activaste_ninguna_licen),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    savedLicenses.forEach { lic ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Icon(
                                if (lic.status == LicenseStatus.ACTIVE.name) Icons.Filled.CloudDone else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (lic.status == LicenseStatus.ACTIVE.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    licenseRepo.labelFor(lic.unlockedItemIds),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    licenseRepo.mask(lic.key) + (lic.lastError?.let { " · $it" } ?: ""),
                                    fontSize = 11.sp,
                                    color = if (lic.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    licenseRepo.removeLicense(lic.key)
                                    savedLicenses = licenseRepo.getSavedLicenses()
                                    com.music.musicflame.data.ProStatusHolder.refresh(context)
                                },
                                enabled = !paymentsSectionLocked
                            ) {
                                Text(stringResource(R.string.settings_quitar), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(16.dp))

                // --- CAMPO PARA PEGAR UNA LICENSE KEY NUEVA ---
                OutlinedTextField(
                    value = licenseInput,
                    onValueChange = { licenseInput = it },
                    label = { Text("License key") },
                    placeholder = { Text(stringResource(R.string.settings_pega_aqui_la_key_que_te_llego_por)) },
                    singleLine = true,
                    enabled = !isValidating && !paymentsSectionLocked,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val keyToValidate = licenseInput
                        isValidating = true
                        errorMessage = null
                        paymentsScope.launch {
                            when (val result = licenseRepo.validateAndAdd(keyToValidate)) {
                                is LicenseValidationResult.Success -> {
                                    savedLicenses = licenseRepo.getSavedLicenses()
                                    licenseInput = ""
                                    errorMessage = null
                                    // Activa la personalización comprada al instante
                                    // en toda la app (tema, reproductor, etc.).
                                    com.music.musicflame.data.ProStatusHolder.refresh(context)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_activado_1_s_gracias_por_tu_apoyo, result.unlockedLabel),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is LicenseValidationResult.Invalid -> {
                                    savedLicenses = licenseRepo.getSavedLicenses()
                                    errorMessage = result.reason
                                }
                                LicenseValidationResult.AlreadyAdded -> {
                                    errorMessage = context.getString(R.string.settings_esa_key_ya_esta_activada)
                                }
                                LicenseValidationResult.NetworkError -> {
                                    errorMessage = context.getString(R.string.settings_sin_conexion_revisa_tu_internet_e)
                                }
                            }
                            isValidating = false
                        }
                    },
                    enabled = !isValidating && !paymentsSectionLocked && licenseInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.settings_activar))
                    }
                }
            }
        }
    }
}
