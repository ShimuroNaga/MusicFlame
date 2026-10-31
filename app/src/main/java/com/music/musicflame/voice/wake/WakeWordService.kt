package com.music.musicflame.voice.wake

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.media.AudioRecord
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.music.musicflame.R
import com.music.musicflame.data.AiChatSettings
import com.music.musicflame.voice.VoiceAssistantActivity
import com.music.musicflame.voice.VoiceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Escucha siempre ("oye <nombre>"). Servicio en primer plano de tipo micrófono con notificación fija.
 *
 * Android 12–15: un servicio de micrófono solo puede arrancar desde la app a la vista (Ajustes o la notificación de
 * "Reactivar"); NO puede arrancar solo al reiniciar el teléfono. Por eso no es START_STICKY: si el sistema lo detiene o
 * se reinicia el teléfono, se avisa con una notificación para reactivarlo.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WakeWord.serviceAlive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vs = VoiceSettings(this)
        if (intent?.action == ACTION_STOP) {          // botón "Apagar" de la notificación
            vs.setWakeEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(WakeNotifications.ID_LISTENING, WakeNotifications.listening(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (_: Exception) {
            vs.setWakeRunning(false)
            WakeNotifications.stopped(this, R.string.wake_notif_stopped_error)
            stopSelf()
            return START_NOT_STICKY
        }
        vs.setWakeRunning(true)
        WakeNotifications.cancelStopped(this)
        WakeWord.state.value = WakeState.STARTING
        acquireWakeLock()
        job?.cancel()
        job = scope.launch { listenLoop(vs) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        val vs = VoiceSettings(this)
        // Si el usuario no lo apagó (el interruptor sigue encendido), lo detuvo el sistema: se avisa.
        if (vs.isWakeEnabled() && vs.isWakeRunning()) WakeNotifications.stopped(this, R.string.wake_notif_stopped_system)
        vs.setWakeRunning(false)
        WakeWord.serviceAlive = false
        WakeWord.state.value = WakeState.OFF
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicFlame:wake")?.apply { setReferenceCounted(false) }
            }
            wakeLock?.takeIf { !it.isHeld }?.acquire()
        } catch (_: Exception) { /* sin wake lock igual funciona, solo menos fiable con la pantalla apagada */ }
    }

    private suspend fun listenLoop(vs: VoiceSettings) {
        val aiName = AiChatSettings(this).getName()
        val data = WakeTemplates.load(this)
        if (data == null || WakeTemplates.normName(data.name) != WakeTemplates.normName(aiName)) {
            fail(vs, R.string.wake_notif_stopped_error); return
        }
        val detector = try { WakeDetector(this, data, vs.getWakeSensitivity()) } catch (_: Throwable) {
            fail(vs, R.string.wake_notif_stopped_error); return
        }
        val buf = ShortArray(WakeAudio.CHUNK)
        var rec: AudioRecord? = null
        var sysPause: WakeState? = null
        var lastSys = 0L
        var music = false
        try {
            while (currentCoroutineContext().isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastSys >= 1000L) {                    // cada ~1 s: pregunta al sistema (llamada, música alta)
                    lastSys = now
                    sysPause = WakeWord.systemPause(this)
                    music = WakeWord.musicLeaking(this)
                    detector.sensitivity = vs.getWakeSensitivity()
                }
                val pause = WakeWord.quickPause() ?: sysPause
                if (pause != null) {
                    if (rec != null) { WakeAudio.releaseSafely(rec); rec = null; detector.reset() }
                    WakeWord.state.value = pause
                    delay(if (pause == WakeState.PAUSED_ASSISTANT || pause == WakeState.PAUSED_TEST) 150 else 500)
                    continue
                }
                if (rec == null) {
                    val r = WakeAudio.open()
                    if (r == null || !WakeAudio.startSafely(r)) {
                        WakeAudio.releaseSafely(r)
                        WakeWord.state.value = WakeState.PAUSED_BUSY_MIC
                        delay(1500); continue
                    }
                    rec = r
                    detector.reset()
                }
                if (!WakeAudio.readChunk(rec, buf)) {
                    WakeAudio.releaseSafely(rec); rec = null
                    WakeWord.state.value = WakeState.PAUSED_BUSY_MIC
                    delay(800); continue
                }
                WakeWord.state.value = WakeState.LISTENING
                val step = detector.process(buf, music)
                if (step.fired) onFired()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            fail(vs, R.string.wake_notif_stopped_error)
        } finally {
            WakeAudio.releaseSafely(rec)
            detector.close()
        }
    }

    /** Se oyó "oye <nombre>": se suelta el micrófono y se abre el asistente (que graba la orden y la manda a Whisper). */
    private fun onFired() {
        WakeWord.launching()
        WakeWord.state.value = WakeState.PAUSED_ASSISTANT
        WakeLauncher.launch(this)
        scope.launch {
            delay(2500)
            // Si Android no dejó abrir el asistente solo, se avisa con una notificación que sí se puede tocar.
            if (!WakeWord.isAssistantOpen()) WakeNotifications.tapToTalk(this@WakeWordService)
        }
    }

    private fun fail(vs: VoiceSettings, msg: Int) {
        vs.setWakeRunning(false)
        WakeNotifications.stopped(this, msg)
        WakeWord.state.value = WakeState.OFF
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.music.musicflame.wake.START"
        const val ACTION_STOP = "com.music.musicflame.wake.STOP"
        const val EXTRA_FROM_WAKE = "from_wake_word"

        /** Arranca (o recarga) la escucha. Debe llamarse con la app a la vista. false si Android no lo permitió. */
        fun start(ctx: Context): Boolean {
            val app = ctx.applicationContext
            if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
            return try {
                app.startForegroundService(Intent(app, WakeWordService::class.java).setAction(ACTION_START))
                true
            } catch (_: Exception) { false }
        }

        /** Apaga la escucha a petición del usuario (no genera aviso de "se detuvo"). */
        fun stop(ctx: Context) {
            val app = ctx.applicationContext
            VoiceSettings(app).setWakeEnabled(false)
            try { app.stopService(Intent(app, WakeWordService::class.java)) } catch (_: Exception) {}
            WakeWord.state.value = WakeState.OFF
        }

        /** Revisa si la escucha debía estar activa pero el proceso murió (el sistema la detuvo) y avisa una vez. */
        fun checkStopped(ctx: Context) {
            val app = ctx.applicationContext
            val vs = VoiceSettings(app)
            if (vs.isWakeEnabled() && vs.isWakeRunning() && !WakeWord.serviceAlive) {
                vs.setWakeRunning(false)
                WakeNotifications.stopped(app, R.string.wake_notif_stopped_system)
            }
        }
    }
}

/** Abre el asistente desde el servicio. Android 10+ limita abrir pantallas desde segundo plano: se usa el permiso "sobre otras apps". */
object WakeLauncher {
    fun launch(ctx: Context) {
        val app = ctx.applicationContext
        val intent = Intent(app, VoiceAssistantActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(WakeWordService.EXTRA_FROM_WAKE, true)
        val main = Handler(Looper.getMainLooper())
        main.post {
            var overlay: View? = null
            val wm = app.getSystemService(WindowManager::class.java)
            try {
                // Con una ventana invisible a la vista, el sistema permite abrir la actividad desde segundo plano.
                if (Settings.canDrawOverlays(app) && wm != null) {
                    val v = View(app)
                    val lp = WindowManager.LayoutParams(
                        1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    )
                    wm.addView(v, lp)
                    overlay = v
                }
            } catch (_: Exception) {}
            try { app.startActivity(intent) } catch (_: Exception) {}
            val v = overlay
            if (v != null && wm != null) main.postDelayed({ try { wm.removeView(v) } catch (_: Exception) {} }, 3000)
        }
    }
}

/** Notificaciones de la escucha siempre. */
object WakeNotifications {
    const val ID_LISTENING = 4701
    private const val ID_STOPPED = 4702
    private const val ID_TAP = 4703
    private const val CH_LISTENING = "wake_listening"
    private const val CH_ALERTS = "wake_alerts"

    private fun nm(ctx: Context): NotificationManager {
        val m = ctx.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(CH_LISTENING, ctx.getString(R.string.wake_notif_channel_listening), NotificationManager.IMPORTANCE_LOW))
        m.createNotificationChannel(NotificationChannel(CH_ALERTS, ctx.getString(R.string.wake_notif_channel_alerts), NotificationManager.IMPORTANCE_HIGH))
        return m
    }

    private fun openApp(ctx: Context): PendingIntent {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun listening(ctx: Context): Notification {
        nm(ctx)
        val name = AiChatSettings(ctx).getName()
        val stop = PendingIntent.getService(
            ctx, 1, Intent(ctx, WakeWordService::class.java).setAction(WakeWordService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_tile_voice), ctx.getString(R.string.wake_notif_stop), stop
        ).build()
        return Notification.Builder(ctx, CH_LISTENING)
            .setSmallIcon(R.drawable.ic_tile_voice)
            .setContentTitle(ctx.getString(R.string.wake_notif_title, name))
            .setContentText(ctx.getString(R.string.wake_notif_text))
            .setContentIntent(openApp(ctx))
            .addAction(action)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** "La escucha se detuvo": al tocarla se reactiva (la pantalla de reactivar arranca el servicio con la app a la vista). */
    fun stopped(ctx: Context, reason: Int) {
        val pi = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, WakeReactivateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_tile_voice)
            .setContentTitle(ctx.getString(R.string.wake_notif_stopped_title))
            .setContentText(ctx.getString(reason))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm(ctx).notify(ID_STOPPED, n) } catch (_: Exception) {}
    }

    /** Respaldo si Android no dejó abrir el asistente solo: "Te escuché, toca para hablar". */
    fun tapToTalk(ctx: Context) {
        val intent = Intent(ctx, VoiceAssistantActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(WakeWordService.EXTRA_FROM_WAKE, true)
        val pi = PendingIntent.getActivity(ctx, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_tile_voice)
            .setContentTitle(ctx.getString(R.string.wake_notif_tap_title))
            .setContentText(ctx.getString(R.string.wake_notif_tap_text, AiChatSettings(ctx).getName()))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        try { nm(ctx).notify(ID_TAP, n) } catch (_: Exception) {}
    }

    fun cancelStopped(ctx: Context) {
        try { ctx.getSystemService(NotificationManager::class.java).cancel(ID_STOPPED) } catch (_: Exception) {}
    }
}

/** Tras reiniciar el teléfono o actualizar la app el servicio de micrófono NO puede arrancar solo: se avisa para reactivarlo. */
class WakeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val vs = VoiceSettings(context)
        if (!vs.isWakeEnabled()) return
        vs.setWakeRunning(false)
        WakeNotifications.stopped(context, R.string.wake_notif_stopped_boot)
    }
}

/** Pantalla invisible a la que lleva el aviso "Reactivar": como se abre con la app a la vista, ya puede arrancar el micrófono. */
class WakeReactivateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WakeNotifications.cancelStopped(this)
        val vs = VoiceSettings(this)
        val ready = vs.isWakeEnabled() && WakeTemplates.isReadyFor(this, AiChatSettings(this).getName())
        if (ready && WakeWordService.start(this)) {
            Toast.makeText(this, R.string.wake_reactivated, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.wake_reactivate_failed, Toast.LENGTH_LONG).show()
            packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
        }
        finish()
    }
}
