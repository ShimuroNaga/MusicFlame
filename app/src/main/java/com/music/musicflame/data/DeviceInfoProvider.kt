package com.music.musicflame.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Locale

data class DeviceBatteryInfo(
    val percent: Int,
    val isCharging: Boolean,
    val statusLabel: String,
    val healthLabel: String,
    val temperatureC: Float?,
    val voltageMv: Int?,
    val technology: String?,
    val plugLabel: String?
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val hardware: String,
    val androidRelease: String,
    val codename: String,
    val apiLevel: Int,
    val securityPatch: String,
    val buildDisplay: String,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val battery: DeviceBatteryInfo
)

/**
 * Lee datos reales del dispositivo (Build, StatFs, ActivityManager, BatteryManager) para
 * mostrarlos en Ajustes > Cuenta. No requiere permisos nuevos: todo son APIs públicas
 * disponibles desde minSdk 31.
 */
object DeviceInfoProvider {

    // Google dejó de anunciar el nombre público de postre desde Android 10, pero el codename
    // interno de AOSP sigue existiendo para cada API level. Cubre 30 (Android 11) en adelante,
    // que es lo relevante para minSdk 31 / targetSdk 36 de este proyecto.
    private val CODENAMES = mapOf(
        30 to "Red Velvet Cake",
        31 to "Snow Cone",
        32 to "Snow Cone v2",
        33 to "Tiramisu",
        34 to "Upside Down Cake",
        35 to "Vanilla Ice Cream",
        36 to "Baklava"
    )

    fun get(context: Context): DeviceInfo {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: ""
        val hardware = Build.HARDWARE ?: Build.BOARD ?: ""

        val apiLevel = Build.VERSION.SDK_INT
        val codename = CODENAMES[apiLevel] ?: ""
        val patch = formatSecurityPatch(Build.VERSION.SECURITY_PATCH)

        val storage = StatFs(Environment.getDataDirectory().path)
        val storageTotal = storage.totalBytes
        val storageFree = storage.availableBytes

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        return DeviceInfo(
            manufacturer = manufacturer,
            model = model,
            hardware = hardware,
            androidRelease = Build.VERSION.RELEASE ?: "",
            codename = codename,
            apiLevel = apiLevel,
            securityPatch = patch,
            buildDisplay = Build.DISPLAY ?: "",
            storageTotalBytes = storageTotal,
            storageFreeBytes = storageFree,
            ramTotalBytes = memInfo.totalMem,
            ramAvailableBytes = memInfo.availMem,
            battery = readBattery(context)
        )
    }

    private fun readBattery(context: Context): DeviceBatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
            BatteryManager.BATTERY_STATUS_FULL -> "Completa"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "No cargando"
            else -> "Desconocido"
        }

        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthLabel = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Agotada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrevoltaje"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Falla no especificada"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
            else -> "Desconocida"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val temperatureC = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else null

        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Cargador"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrico"
            else -> null
        }

        return DeviceBatteryInfo(
            percent = percent.coerceIn(0, 100),
            isCharging = isCharging,
            statusLabel = statusLabel,
            healthLabel = healthLabel,
            temperatureC = temperatureC,
            voltageMv = voltage,
            technology = technology,
            plugLabel = plugLabel
        )
    }

    private fun formatSecurityPatch(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(raw) ?: return raw
            val formatter = SimpleDateFormat("d MMMM, yyyy", Locale("es", "MX"))
            formatter.format(date)
        } catch (e: Exception) {
            raw
        }
    }

    fun bytesToGbLabel(bytes: Long): String {
        if (bytes <= 0) return "0 GB"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return "${Math.round(gb)} GB"
    }
}
