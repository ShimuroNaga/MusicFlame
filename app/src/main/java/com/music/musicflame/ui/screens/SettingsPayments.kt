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
import com.music.musicflame.data.RestoreResult
import com.music.musicflame.data.SavingsGoalRepository
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.together.SupabaseSalas
import com.music.musicflame.ui.theme.LocalAppTextColor
import com.music.musicflame.widget.MusicFlameWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sección "Pagos (opcional)" de Ajustes: compra en la tienda de Ko-fi y "Restaurar compras" (consulta a Supabase). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun LazyListScope.settingsPaymentsSection(
    style: SettingsStyle,
    context: Context,
    licenseRepo: LicenseRepository,
    restoreEmailState: MutableState<String>,
    purchasedState: MutableState<List<com.music.musicflame.data.KofiProduct>>,
    isRestoringState: MutableState<Boolean>,
    errorMessageState: MutableState<String?>,
    paymentsScope: kotlinx.coroutines.CoroutineScope,
    paymentsSectionLocked: Boolean,
    unlockedIds: Set<String>
) {
    var restoreEmail by restoreEmailState
    var purchased by purchasedState
    var isRestoring by isRestoringState
    var errorMessage by errorMessageState

    item { SettingsSectionHeader(style, stringResource(R.string.settings_licencia_de_apoyo_opcional)) }

    // --- PASO A PASO: cómo comprar y restaurar ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
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
                    stringResource(R.string.kofi_one_time),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                val steps = listOf(
                    stringResource(R.string.kofi_step_choose, com.music.musicflame.data.PaymentCatalog.ITEMS.size),
                    stringResource(R.string.kofi_step_open),
                    stringResource(R.string.kofi_step_pay),
                    stringResource(R.string.kofi_step_email),
                    stringResource(R.string.kofi_step_restore)
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
            }
        }
    }

    item {
        Text(
            stringResource(R.string.settings_compra_solo_lo_que_te_interesa_o_t, LicenseRepository.buyAllPriceMxn()),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    // --- "COMPRAR TODO" (producto TODO de la tienda de Ko-fi) ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(if (paymentsSectionLocked) 0.6f else 1f),
            shape = RoundedCornerShape(20.dp),
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

                Spacer(Modifier.height(12.dp))

                val hasEverything = com.music.musicflame.data.PaymentCatalog.ITEMS.all { unlockedIds.contains(it.id) }
                val buyAllUrl = LicenseRepository.buyAllUrl()
                if (hasEverything) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.kofi_have_all),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (buyAllUrl != null) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buyAllUrl)))
                            }
                        },
                        enabled = !paymentsSectionLocked && buyAllUrl != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (buyAllUrl != null) stringResource(R.string.settings_comprar_todo_por_1_s_mxn, LicenseRepository.buyAllPriceMxn())
                            else stringResource(R.string.kofi_soon),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // --- TABLA DEL CATÁLOGO: estado real por producto + botón de compra ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
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
                    stringResource(R.string.kofi_catalog_sub),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Un "producto" = una entrada de KOFI_PRODUCTS (no un ítem del catálogo suelto),
                // así que si 2 ítems están agrupados aparecen en UNA sola fila con un solo botón.
                LicenseRepository.KOFI_PRODUCTS
                    .filter { !it.catalogIds.contains(LicenseRepository.ALL_ITEMS_ID) }
                    .forEach { product ->
                        // Nombre del pack (no la unión de los ítems: varios se llaman igual,
                        // ej. "Arcoíris" existe en ecualizador, Now Playing y lyrics).
                        val label = product.displayName(context)
                        // Qué incluye el pack, agrupado por sección (varios ítems se llaman igual,
                        // ej. "Arcoíris"); con una sola sección se omite el prefijo.
                        val itemsInProduct = com.music.musicflame.data.PaymentCatalog.ITEMS.filter { it.id in product.catalogIds }
                        val bySection = itemsInProduct.groupBy { it.sectionRes }
                        val contents = bySection.entries.joinToString(" · ") { (sectionRes, sectionItems) ->
                            val names = sectionItems.joinToString(", ") { it.displayLabel(context) }
                            if (bySection.size > 1) "${context.getString(sectionRes)}: $names" else names
                        }
                        val priceMxn = LicenseRepository.productPriceMxn(product)
                        val owned = product.catalogIds.all { unlockedIds.contains(it) }
                        val checkoutUrl = product.url

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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (contents.isNotBlank()) {
                                    Text(
                                        contents,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (owned) {
                                Text(
                                    stringResource(R.string.kofi_have_it),
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
                                    Text(
                                        if (checkoutUrl != null) "$$priceMxn MXN" else stringResource(R.string.kofi_soon),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }

    // --- TUS COMPRAS + RESTAURAR ---
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(if (paymentsSectionLocked) 0.6f else 1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(R.string.kofi_purchases_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    stringResource(R.string.kofi_purchases_sub),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (purchased.isEmpty()) {
                    Text(
                        stringResource(R.string.kofi_none_yet),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    purchased.forEach { product ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                product.displayName(context),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            licenseRepo.clearPurchases()
                            purchased = licenseRepo.recognizedProducts()
                            com.music.musicflame.data.ProStatusHolder.refresh(context)
                        },
                        enabled = !paymentsSectionLocked
                    ) {
                        Text(stringResource(R.string.kofi_clear), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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

                // Opcional: solo hace falta si pagaste en Ko-fi con un correo distinto al de tu cuenta de Google.
                OutlinedTextField(
                    value = restoreEmail,
                    onValueChange = { restoreEmail = it },
                    label = { Text(stringResource(R.string.kofi_email_label)) },
                    placeholder = { Text(stringResource(R.string.kofi_email_hint)) },
                    singleLine = true,
                    enabled = !isRestoring && !paymentsSectionLocked,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val typedEmail = restoreEmail
                        isRestoring = true
                        errorMessage = null
                        paymentsScope.launch {
                            when (val result = licenseRepo.restorePurchases(typedEmail)) {
                                is RestoreResult.Success -> {
                                    purchased = licenseRepo.recognizedProducts()
                                    restoreEmail = ""
                                    errorMessage = null
                                    // Activa lo comprado al instante en toda la app (tema, reproductor, etc.).
                                    com.music.musicflame.data.ProStatusHolder.refresh(context)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.kofi_restored, result.unlockedLabel),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                RestoreResult.Unrecognized -> errorMessage = context.getString(R.string.kofi_unrecognized)
                                RestoreResult.NothingFound -> errorMessage = context.getString(R.string.kofi_nothing_found)
                                RestoreResult.NeedEmail -> errorMessage = context.getString(R.string.kofi_need_email)
                                RestoreResult.NetworkError -> errorMessage = context.getString(R.string.kofi_network_error)
                            }
                            isRestoring = false
                        }
                    },
                    enabled = !isRestoring && !paymentsSectionLocked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.kofi_restore))
                    }
                }
            }
        }
    }
}
