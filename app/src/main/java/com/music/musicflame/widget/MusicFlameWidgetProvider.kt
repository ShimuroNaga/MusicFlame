package com.music.musicflame.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.util.SizeF
import android.widget.RemoteViews
import com.music.musicflame.MainActivity
import com.music.musicflame.R
import com.music.musicflame.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Widget de home screen de MusicFlame:
 * [ Carátula + nombre ]  [ Play/Pause ]  [ Siguiente ]
 * (variante ancha de 4 celdas: agrega un botón dedicado de "Atrás")
 *
 * A diferencia del CompactSongWidget de dentro de la app (que sí soporta swipe
 * porque vive en Compose), RemoteViews solo entiende taps, por eso aquí todo
 * son botones normales de 1 toque.
 */
class MusicFlameWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = WidgetPrefs.read(context)
                appWidgetIds.forEach { id ->
                    val views = buildRemoteViews(context, state)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        // Scope propio para refrescos disparados desde el servicio de reproducción
        // (que vive todo el tiempo que suene música, con o sin la app abierta).
        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Llamado por MusicPlaybackService cada vez que cambia la canción o el
         * estado de play/pause. Si no hay ningún widget añadido al home screen,
         * appWidgetIds queda vacío y esto no hace nada (barato de llamar siempre).
         */
        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, MusicFlameWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            refreshScope.launch {
                val state = WidgetPrefs.read(appContext)
                val views = buildRemoteViews(appContext, state)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }

        /**
         * Actualización "optimista" e instantánea de solo el ícono play/pause,
         * para que el widget responda al toque sin esperar a que el reproductor
         * confirme el cambio de estado (se siente inmediato).
         */
        fun refreshPlayPauseOnly(context: Context, isPlaying: Boolean) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, MusicFlameWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            val icon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            ids.forEach { id ->
                manager.partiallyUpdateAppWidget(
                    id,
                    RemoteViews(appContext.packageName, R.layout.widget_music_flame).apply {
                        setImageViewResource(R.id.widget_play_pause, icon)
                    }
                )
            }
        }

        private suspend fun buildRemoteViews(
            context: Context,
            state: WidgetPrefs.WidgetSongState
        ): RemoteViews {
            // Carátula redondeada: se calcula una sola vez y se reutiliza en ambas variantes.
            val artBitmap = loadRoundedAlbumArt(context, state.albumArtUri)
            val backgroundAlpha = (SettingsRepository(context).getWidgetBackgroundOpacity() * 255).toInt().coerceIn(0, 255)

            val compact = buildBaseViews(context, state, artBitmap, backgroundAlpha, R.layout.widget_music_flame)
            compact.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent(context))
            compact.setOnClickPendingIntent(R.id.widget_next_or_prev, nextOnlyPendingIntent(context))
            compact.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

            val wide = buildBaseViews(context, state, artBitmap, backgroundAlpha, R.layout.widget_music_flame_wide)
            wide.setOnClickPendingIntent(R.id.widget_previous, previousPendingIntent(context))
            wide.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent(context))
            wide.setOnClickPendingIntent(R.id.widget_next_or_prev, nextOnlyPendingIntent(context))
            wide.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

            // El sistema elige automáticamente cuál RemoteViews mostrar según el tamaño
            // real que el usuario le dio al widget en su home screen (API 31+, ya cubierto
            // por el minSdk del proyecto). 180x40dp = 3 celdas (compacta), 250x40dp = 4 celdas (ancha).
            return RemoteViews(
                mapOf(
                    SizeF(180f, 40f) to compact,
                    SizeF(250f, 40f) to wide
                )
            )
        }

        /** Contenido común a ambas variantes: texto, ícono play/pause, carátula y opacidad del fondo. Los PendingIntents se agregan aparte porque cada variante tiene botones distintos. */
        private fun buildBaseViews(
            context: Context,
            state: WidgetPrefs.WidgetSongState,
            artBitmap: Bitmap?,
            backgroundAlpha: Int,
            layoutRes: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, layoutRes)

            if (state.hasSong) {
                views.setTextViewText(R.id.widget_song_title, state.title)
                views.setTextViewText(R.id.widget_song_artist, state.artist)
            } else {
                views.setTextViewText(R.id.widget_song_title, context.getString(R.string.widget_no_song))
                views.setTextViewText(R.id.widget_song_artist, context.getString(R.string.widget_select_song))
            }

            views.setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            if (artBitmap != null) {
                views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_widget_music_placeholder)
            }

            // Solo cambiamos el ALFA del fondo (vía tint), no el drawable -> conserva las
            // esquinas redondeadas del shape original sin importar la opacidad elegida.
            views.setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                ColorStateList.valueOf(Color.argb(backgroundAlpha, 28, 27, 31))
            )

            return views
        }

        private fun playPausePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_PLAY_PAUSE
            }
            return PendingIntent.getBroadcast(
                context, WidgetActionReceiver.REQUEST_CODE_PLAY_PAUSE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** "Siguiente" de un solo propósito: 1 toque, respuesta instantánea. */
        private fun nextOnlyPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_NEXT_ONLY
            }
            return PendingIntent.getBroadcast(
                context, WidgetActionReceiver.REQUEST_CODE_NEXT_ONLY, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Botón dedicado de "canción anterior", solo existe en la variante ancha. */
        private fun previousPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_PREVIOUS
            }
            return PendingIntent.getBroadcast(
                context, WidgetActionReceiver.REQUEST_CODE_PREVIOUS, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Descarga/lee la carátula y la recorta a un cuadrado con esquinas redondeadas. */
        private suspend fun loadRoundedAlbumArt(context: Context, artUriString: String?): Bitmap? {
            if (artUriString.isNullOrEmpty()) return null

            return withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(artUriString)
                    val source = when (uri.scheme) {
                        "content", "file" -> {
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        }
                        else -> null
                    } ?: return@withContext null

                    roundCorners(source, cornerRadiusPx = 20f)
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
        }

        private fun roundCorners(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            return output
        }
    }
}
