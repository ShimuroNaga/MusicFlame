package com.music.musicflame.ui.screens.onboarding

import com.music.musicflame.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.music.musicflame.ui.theme.LocalAppTextColor

/**
 * Paso 1 del onboarding. Copia el mismo patrón que usa SettingsScreen.kt para
 * la optimización de batería (Switch + Intent + refresco por LifecycleEventObserver
 * al volver del sistema), en vez de depender de un ActivityResult que no
 * siempre dispara al volver de Ajustes del sistema.
 */
@Composable
fun OnboardingPermissionsStep() {
    val context = LocalContext.current
    val listItemColors = onboardingListItemColors()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    val audioPermission = if (SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    var audioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (SDK_INT >= 33) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    // Si el sistema ya negó el permiso una vez, launch() de RequestPermission
    // deja de mostrar el diálogo (Android lo hace en silencio para no ser
    // invasivo) — hay que detectarlo y mandar a Ajustes de notificaciones,
    // igual que ya haces con la batería.
    var notificationPermanentlyDenied by remember {
        mutableStateOf(
            SDK_INT >= 33 &&
                !notificationsGranted &&
                context is Activity &&
                !ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.POST_NOTIFICATIONS) &&
                context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notification_permission_asked", false)
        )
    }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isIgnoringBattery by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    var recordAudioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var showVisualizerExplanationDialog by remember { mutableStateOf(false) }

    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioGranted = granted
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("notification_permission_asked", true).apply()
        if (!granted && context is Activity) {
            notificationPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        recordAudioGranted = granted
    }

    // Igual que en SettingsScreen.kt: al volver de Ajustes del sistema (batería,
    // o si el usuario concede el permiso manualmente), refrescamos el estado real.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                audioGranted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
                if (SDK_INT >= 33) {
                    notificationsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!notificationsGranted && context is Activity) {
                        notificationPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.POST_NOTIFICATIONS) &&
                            context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notification_permission_asked", false)
                    }
                }
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                recordAudioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item {
            Text(
                stringResource(R.string.ob_pm_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LocalAppTextColor.current
            )
            Text(
                stringResource(R.string.ob_pm_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppTextColor.current.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item { onboardingSectionHeader(stringResource(R.string.ob_pm_music)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ob_pm_music_title)) },
                supportingContent = { Text(stringResource(R.string.ob_pm_music_desc)) },
                trailingContent = {
                    if (audioGranted) {
                        Text(stringResource(R.string.ob_pm_granted), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    } else {
                        TextButton(onClick = { audioLauncher.launch(audioPermission) }) {
                            Text(stringResource(R.string.ob_pm_enable), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader(stringResource(R.string.ob_pm_notif)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ob_pm_notif_title)) },
                supportingContent = {
                    Text(
                        if (notificationPermanentlyDenied)
                            stringResource(R.string.ob_pm_notif_blocked)
                        else stringResource(R.string.ob_pm_notif_desc)
                    )
                },
                trailingContent = {
                    if (notificationsGranted) {
                        Text(stringResource(R.string.ob_pm_granted), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    } else {
                        TextButton(onClick = {
                            if (SDK_INT < 33) return@TextButton
                            if (notificationPermanentlyDenied) {
                                // El sistema ya no muestra el diálogo: hay que ir a Ajustes,
                                // igual que con la optimización de batería.
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                try { context.startActivity(intent) } catch (e: Exception) { /* dispositivo sin ese ajuste */ }
                            } else {
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("notification_permission_asked", true).apply()
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) {
                            Text(if (notificationPermanentlyDenied) stringResource(R.string.ob_pm_open_settings) else stringResource(R.string.ob_pm_enable), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader(stringResource(R.string.ob_pm_battery)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ob_pm_battery_title)) },
                supportingContent = {
                    Text(
                        text = if (isIgnoringBattery)
                            stringResource(R.string.ob_pm_battery_ok)
                        else
                            stringResource(R.string.ob_pm_battery_bad),
                        color = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isIgnoringBattery,
                        onCheckedChange = { checked ->
                            val intent = if (checked) {
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            } else {
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            }
                            try { context.startActivity(intent) } catch (e: Exception) { /* dispositivo sin ese ajuste */ }
                        }
                    )
                },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }

        item { onboardingSectionHeader(stringResource(R.string.ob_pm_eq)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.fp_viz_title)) },
                supportingContent = {
                    Text(
                        if (recordAudioGranted) stringResource(R.string.ob_pm_viz_ok)
                        else stringResource(R.string.ob_pm_viz_why)
                    )
                },
                trailingContent = {
                    if (recordAudioGranted) {
                        Text(stringResource(R.string.ob_pm_granted), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    } else {
                        TextButton(onClick = { showVisualizerExplanationDialog = true }) {
                            Text(stringResource(R.string.ob_pm_enable), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                },
                colors = listItemColors
            )
            HorizontalDivider(color = dividerColor)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Mismo diálogo explicativo que ya usas en FullScreenPlayer.kt antes de pedir
    // RECORD_AUDIO, para que el usuario no vea un permiso de "micrófono" sin contexto.
    if (showVisualizerExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showVisualizerExplanationDialog = false },
            title = { Text(stringResource(R.string.fp_viz_title)) },
            text = {
                Text(
                    stringResource(R.string.fp_viz_msg)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showVisualizerExplanationDialog = false
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("visualizer_permission_asked", true).apply()
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(stringResource(R.string.fp_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showVisualizerExplanationDialog = false
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("visualizer_permission_asked", true).apply()
                }) { Text(stringResource(R.string.fp_not_now)) }
            }
        )
    }
}
