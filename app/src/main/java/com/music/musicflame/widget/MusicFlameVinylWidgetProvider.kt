package com.music.musicflame.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.music.musicflame.AlbumArtShapeType
import com.music.musicflame.MainActivity
import com.music.musicflame.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Widget "Vinilo" de home screen de MusicFlame (adicional a
 * MusicFlameWidgetProvider): una funda con el disco asomando y girando
 * mientras suena música. Redimensionable entre 1x1 y 2x2 celdas.
 *
 * FEATURE COSMÉTICA DE PAGO (catálogo punto 5). Conectado a
 * LicenseRepository.isProUnlocked() vía isUnlocked() (abajo): mientras no
 * esté desbloqueado (nadie compró, y la sesión de Google activa no es la del
 * dueño), el widget se sigue pudiendo AGREGAR desde el selector de widgets
 * del sistema, pero muestra un candado en vez del disco (paintLockedWidgets)
 * y no gira. Si el plan a futuro es impedir directamente que alguien sin
 * licencia lo AGREGUE (no solo bloquear su contenido), hace falta además
 * deshabilitar el <receiver> vía PackageManager.setComponentEnabledSetting,
 * algo que no se toca en esta sesión para no complicar el alcance.
 *
 * ROTACIÓN Y BATERÍA — por qué bitmaps pre-rotados en vez de AnimationDrawable:
 * RemoteViews no expone ningún método remotable equivalente a
 * Drawable.start()/stop(); un AnimationDrawable asignado por XML se infla en
 * el proceso del launcher, pero nadie del lado de la app puede pedirle que
 * arranque el loop, así que nunca se movería solo. La alternativa elegida es
 * pre-renderizar VINYL_FRAME_COUNT bitmaps del disco ya rotado (una sola vez
 * por canción/carátula, cacheados) e ir pisando el ImageView con
 * partiallyUpdateAppWidget() —más liviano que un update completo— a un
 * intervalo fijo mientras hay reproducción. El ciclo se detiene por completo
 * en pausa, sin canción, sin ningún widget Vinilo agregado, o al destruirse el
 * servicio. VINYL_FRAME_INTERVAL_MS=200ms con 18 frames da una vuelta cada
 * 3.6s: notoriamente más lento que un vinilo real (33-45rpm), a propósito,
 * para no forzar tantas actualizaciones por segundo. Si se quiere un giro más
 * realista a costa de más batería, basta con bajar el intervalo o subir la
 * cantidad de frames acá abajo.
 *
 * AHORRO DE BATERÍA CON PANTALLA APAGADA: la rotación se corta también cuando
 * la pantalla se apaga, y se reanuda al encenderla (si en ese momento sigue
 * sonando música). ACTION_SCREEN_OFF/ON son "implicit broadcasts" que Android
 * ya NO entrega a receivers declarados en el Manifest desde la API 26, así que
 * no hay forma de escucharlos desde este AppWidgetProvider ni desde ningún
 * <receiver> estático: MusicPlaybackService los registra dinámicamente
 * (registerReceiver en runtime) mientras el servicio está vivo y llama a
 * onScreenStateChanged() de acá abajo.
 */
class MusicFlameVinylWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAllWidgets(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        if (!hasWidgets(context)) {
            wantsRotation = false
            stopRotation()
        }
    }

    override fun onDisabled(context: Context) {
        wantsRotation = false
        stopRotation()
    }

    companion object {
        /**
         * true si hay licencia activa o si la cuenta de Google iniciada en la
         * app es la del dueño (ver LicenseRepository.isOwnerAccount) — mismo
         * criterio que el resto de features de pago de la app.
         */
        private fun isUnlocked(context: Context): Boolean =
            com.music.musicflame.data.LicenseRepository(context).isProUnlocked()

        private const val VINYL_FRAME_COUNT = 18
        private const val VINYL_FRAME_INTERVAL_MS = 200L
        private const val DISC_RENDER_SIZE_PX = 180

        private val mainHandler = Handler(Looper.getMainLooper())
        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private var isRotating = false
        private var frameIndex = 0
        private var cachedFrames: List<Bitmap>? = null
        private var cachedFramesKey: String? = null
        private var lastAppContext: Context? = null

        // Estado DESEADO según reproducción (isPlaying && hasSong), independiente
        // de si la pantalla está prendida. isRotating (arriba) es el estado REAL
        // del handler. applyRotationState() concilia ambos: solo gira si las dos
        // condiciones se cumplen a la vez.
        private var wantsRotation = false
        private var isScreenOn = true

        private val rotationRunnable = object : Runnable {
            override fun run() {
                val context = lastAppContext
                if (context != null) tickFrame(context)
                mainHandler.postDelayed(this, VINYL_FRAME_INTERVAL_MS)
            }
        }

        /** true si el usuario tiene al menos un widget Vinilo añadido al home screen. */
        fun hasWidgets(context: Context): Boolean {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, MusicFlameVinylWidgetProvider::class.java)
            return manager.getAppWidgetIds(componentName).isNotEmpty()
        }

        /**
         * Llamado por MusicPlaybackService.syncWidgetState() cada vez que cambia
         * la canción o el estado de play/pause (mismo punto de enganche que ya
         * usa MusicFlameWidgetProvider). Pinta el estado actual y arranca o
         * detiene el giro según corresponda. Barato de llamar siempre: si no hay
         * ningún widget Vinilo agregado, no hace nada.
         */
        fun onPlaybackStateChanged(
            context: Context,
            isPlaying: Boolean,
            hasSong: Boolean,
            albumArtUri: String?,
            mediaId: String?
        ) {
            val appContext = context.applicationContext
            lastAppContext = appContext

            if (!hasWidgets(appContext)) {
                stopRotation()
                return
            }

            if (!isUnlocked(appContext)) {
                wantsRotation = false
                stopRotation()
                refreshScope.launch { paintLockedWidgets(appContext) }
                return
            }

            val key = "$mediaId|$albumArtUri"
            if (key != cachedFramesKey) {
                cachedFrames = null
                frameIndex = 0
            }

            refreshScope.launch {
                paintAllWidgets(appContext, hasSong, albumArtUri, key)
            }

            wantsRotation = isPlaying && hasSong
            applyRotationState()
        }

        /**
         * Llamado por MusicPlaybackService desde su receiver dinámico de
         * ACTION_SCREEN_OFF/ON. Al apagar pantalla corta el giro aunque siga
         * sonando música (ahorro de batería); al encenderla lo retoma solo si
         * la música seguía sonando mientras tanto.
         */
        fun onScreenStateChanged(context: Context, isScreenOnNow: Boolean) {
            isScreenOn = isScreenOnNow
            lastAppContext = context.applicationContext
            applyRotationState()
        }

        /** Concilia el estado deseado (reproducción) con el de pantalla; arranca o para el handler según corresponda. */
        private fun applyRotationState() {
            val context = lastAppContext ?: return
            if (wantsRotation && isScreenOn) startRotation(context) else stopRotation()
        }

        /** Repintado completo (título + disco en el frame actual) para todos los ids. */
        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            lastAppContext = appContext
            if (!hasWidgets(appContext)) return

            if (!isUnlocked(appContext)) {
                refreshScope.launch { paintLockedWidgets(appContext) }
                return
            }

            refreshScope.launch {
                val state = WidgetPrefs.read(appContext)
                val key = "${state.mediaId}|${state.albumArtUri}"
                paintAllWidgets(appContext, state.hasSong, state.albumArtUri, key)
            }
        }

        private fun startRotation(context: Context) {
            if (isRotating) return
            isRotating = true
            mainHandler.postDelayed(rotationRunnable, VINYL_FRAME_INTERVAL_MS)
        }

        // internal (antes private): MusicPlaybackService.onDestroy() la llama
        // para cortar el giro si el servicio se destruye mientras sonaba música.
        internal fun stopRotation() {
            if (!isRotating) return
            isRotating = false
            mainHandler.removeCallbacks(rotationRunnable)
        }

        /**
         * Avanza un frame y actualiza SOLO el ImageView del disco (partial
         * update, más barato que reconstruir todo el RemoteViews en cada tick).
         * No hace nada si los frames todavía no están cacheados (el primer
         * paintAllWidgets() de la canción actual se encarga de generarlos).
         */
        private fun tickFrame(context: Context) {
            val frames = cachedFrames ?: return
            if (frames.isEmpty()) return
            frameIndex = (frameIndex + 1) % frames.size

            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicFlameVinylWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) {
                stopRotation()
                return
            }

            val partial = RemoteViews(context.packageName, R.layout.widget_music_flame_vinyl).apply {
                setImageViewBitmap(R.id.widget_vinyl_disc, frames[frameIndex])
            }
            ids.forEach { id -> manager.partiallyUpdateAppWidget(id, partial) }
        }

        private suspend fun paintAllWidgets(
            context: Context,
            hasSong: Boolean,
            albumArtUri: String?,
            framesKey: String
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicFlameVinylWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            val frames = ensureFrames(context, albumArtUri, framesKey)
            val state = WidgetPrefs.read(context)
            val views = buildViews(context, state, hasSong, frames[frameIndex])
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        /**
         * Estado bloqueado: candado en vez del disco, título fijo avisando el
         * precio, y tocar en cualquier parte del widget abre la app (no hay
         * play/pause ni giro) para que el usuario vaya a Ajustes > Pagos.
         */
        private suspend fun paintLockedWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicFlameVinylWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            val lockBitmap = withContext(Dispatchers.IO) { buildLockedDisc(context) }
            val views = RemoteViews(context.packageName, R.layout.widget_music_flame_vinyl).apply {
                setTextViewText(R.id.widget_vinyl_title, context.getString(R.string.widget_vinyl_locked))
                setImageViewBitmap(R.id.widget_vinyl_disc, lockBitmap)
                setOnClickPendingIntent(R.id.widget_vinyl_disc, openAppPendingIntent(context))
                setOnClickPendingIntent(R.id.widget_vinyl_sleeve_zone, openAppPendingIntent(context))
            }
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        /** Disco de fondo oscuro con un ícono de candado centrado, mismo tamaño que un frame real. */
        private fun buildLockedDisc(context: Context): Bitmap {
            val base = Bitmap.createBitmap(DISC_RENDER_SIZE_PX, DISC_RENDER_SIZE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(base)
            canvas.drawColor(Color.rgb(40, 40, 40))

            ContextCompat.getDrawable(context, android.R.drawable.ic_lock_lock)?.let { icon ->
                val iconSize = (DISC_RENDER_SIZE_PX * 0.42f).toInt()
                val offset = (DISC_RENDER_SIZE_PX - iconSize) / 2
                icon.setBounds(offset, offset, offset + iconSize, offset + iconSize)
                icon.setTint(Color.argb((0.9f * 255).toInt(), 255, 255, 255))
                icon.draw(canvas)
            }
            return base
        }

        private fun buildViews(
            context: Context,
            state: WidgetPrefs.WidgetSongState,
            hasSong: Boolean,
            discFrame: Bitmap
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_music_flame_vinyl)
            views.setTextViewText(
                R.id.widget_vinyl_title,
                if (hasSong) state.title else context.getString(R.string.widget_no_song)
            )
            views.setImageViewBitmap(R.id.widget_vinyl_disc, discFrame)
            views.setOnClickPendingIntent(R.id.widget_vinyl_disc, playPausePendingIntent(context))
            views.setOnClickPendingIntent(R.id.widget_vinyl_sleeve_zone, openAppPendingIntent(context))
            return views
        }

        /**
         * Genera (o reusa del cache) los VINYL_FRAME_COUNT bitmaps rotados del
         * disco para la carátula/canción identificada por [key]. Se recalculan
         * solo cuando cambia la canción o su carátula.
         */
        private suspend fun ensureFrames(context: Context, albumArtUri: String?, key: String): List<Bitmap> {
            cachedFrames?.let { if (cachedFramesKey == key) return it }

            val frames = withContext(Dispatchers.IO) {
                val baseDisc = buildBaseDisc(context, albumArtUri)
                (0 until VINYL_FRAME_COUNT).map { i -> rotateBitmap(baseDisc, i * (360f / VINYL_FRAME_COUNT)) }
            }
            cachedFrames = frames
            cachedFramesKey = key
            frameIndex = 0
            return frames
        }

        /** Carátula real recortada a cuadrado + forma VINYL, o el mismo placeholder de siempre si no hay carátula. */
        private fun buildBaseDisc(context: Context, albumArtUriString: String?): Bitmap {
            val square = loadAlbumArtSquare(context, albumArtUriString)
                ?: buildPlaceholderSquare(context, DISC_RENDER_SIZE_PX)
            // Reusa exactamente la misma geometría de recorte (círculo + surcos +
            // hoyo central) que el selector "Vinilo" de forma de carátula, para
            // que este widget se vea consistente con esa opción del resto de la app.
            return MusicFlameWidgetProvider.clipToShape(square, AlbumArtShapeType.VINYL, cornerRadiusPx = 0f)
        }

        private fun loadAlbumArtSquare(context: Context, artUriString: String?): Bitmap? {
            if (artUriString.isNullOrEmpty()) return null
            return try {
                val uri = Uri.parse(artUriString)
                val source = when (uri.scheme) {
                    "content", "file" -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    else -> null
                } ?: return null
                centerCropSquare(source, DISC_RENDER_SIZE_PX)
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }

        private fun centerCropSquare(bitmap: Bitmap, size: Int): Bitmap {
            val srcSize = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - srcSize) / 2
            val y = (bitmap.height - srcSize) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, srcSize, srcSize)
            return Bitmap.createScaledBitmap(cropped, size, size, true)
        }

        /** Mismo ícono de nota musical de siempre, sobre fondo oscuro, para cuando no hay carátula. */
        private fun buildPlaceholderSquare(context: Context, size: Int): Bitmap {
            val base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(base)
            canvas.drawColor(Color.rgb(58, 56, 64))

            ContextCompat.getDrawable(context, R.drawable.ic_widget_music_placeholder)?.let { icon ->
                val iconSize = (size * 0.5f).toInt()
                val offset = (size - iconSize) / 2
                icon.setBounds(offset, offset, offset + iconSize, offset + iconSize)
                icon.setTint(Color.argb((0.4f * 255).toInt(), 255, 255, 255))
                icon.draw(canvas)
            }
            return base
        }

        private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val matrix = Matrix().apply { setRotate(degrees, source.width / 2f, source.height / 2f) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, matrix, paint)
            return output
        }

        // Reusa el mismo receiver/acción que el widget clásico: no hace falta
        // duplicar la lógica de play/pause en un segundo BroadcastReceiver.
        private fun playPausePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_PLAY_PAUSE
            }
            return PendingIntent.getBroadcast(
                context, WidgetActionReceiver.REQUEST_CODE_PLAY_PAUSE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // NOTA sobre "mantener presionado abre el reproductor completo": RemoteViews
        // no tiene ningún equivalente a un long-click pendingintent (solo
        // setOnClickPendingIntent existe en la API pública), así que no hay forma
        // de detectar una pulsación mantenida desde un AppWidget. En su lugar,
        // tocar la funda (fuera del disco) abre la app directo, igual que el
        // widget clásico al tocar fuera de sus botones.
        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
