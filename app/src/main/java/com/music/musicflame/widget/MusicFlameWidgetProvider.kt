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
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.music.musicflame.AlbumArtShapeType
import com.music.musicflame.MainActivity
import com.music.musicflame.R
import com.music.musicflame.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

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

        // Tamaño (en px) del placeholder generado cuando no hay carátula real.
        // No depende de la densidad del dispositivo: es solo el lienzo sobre el
        // que se dibuja el ícono antes de recortarlo a la forma elegida; Android
        // lo escala igual que cualquier otro bitmap dentro del ImageView.
        private const val PLACEHOLDER_ART_SIZE_PX = 200

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

        /** true si el usuario tiene al menos un widget de MusicFlame añadido al home screen. */
        fun hasWidgets(context: Context): Boolean {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, MusicFlameWidgetProvider::class.java)
            return manager.getAppWidgetIds(componentName).isNotEmpty()
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
            // NUEVO: misma forma elegida en Ajustes > Apariencia (LocalAlbumArtShape /
            // SettingsRepository.getAlbumArtShape()), en vez de la esquina redondeada
            // fija que usaba el widget antes sin importar la preferencia del usuario.
            val settingsRepo = SettingsRepository(context)
            val albumArtShape = settingsRepo.getAlbumArtShape()

            // Carátula recortada a la forma elegida; si no hay carátula (o falló la
            // carga), se arma un placeholder recortado a esa misma forma en vez de
            // caer en el cuadrado fijo de siempre.
            val artBitmap = loadRoundedAlbumArt(context, state.albumArtUri, albumArtShape)
                ?: buildPlaceholderArt(context, albumArtShape)
            val backgroundAlpha = (settingsRepo.getWidgetBackgroundOpacity() * 255).toInt().coerceIn(0, 255)

            // LETRA EN VIVO: líneas guardadas por MusicPlaybackService cada vez que
            // cambia la línea activa (ver LyricsTicker). Solo se usan si siguen
            // siendo de la canción actual y si el usuario no lo desactivó en
            // Ajustes > Lyrics.
            val lyricsEnabled = settingsRepo.isLyricsInWidgetEnabled()
            val lyricsLines = if (lyricsEnabled) WidgetPrefs.readLyricsLines(context, state.mediaId) else emptyList()
            val lyricsColor = resolveLyricsColorInt(settingsRepo)

            val compact = buildBaseViews(
                context, state, artBitmap, backgroundAlpha, R.layout.widget_music_flame,
                lyricsLines, lyricsColor, extraLineIds = emptyList()
            )
            compact.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent(context))
            compact.setOnClickPendingIntent(R.id.widget_next_or_prev, nextOnlyPendingIntent(context))
            compact.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

            val wide = buildBaseViews(
                context, state, artBitmap, backgroundAlpha, R.layout.widget_music_flame_wide,
                lyricsLines, lyricsColor, extraLineIds = emptyList()
            )
            wide.setOnClickPendingIntent(R.id.widget_previous, previousPendingIntent(context))
            wide.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent(context))
            wide.setOnClickPendingIntent(R.id.widget_next_or_prev, nextOnlyPendingIntent(context))
            wide.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

            // Variante ancha EXPANDIDA (250x74dp): activa + siguiente línea (2).
            val wideLyrics = buildBaseViews(
                context, state, artBitmap, backgroundAlpha, R.layout.widget_music_flame_wide_lyrics,
                lyricsLines, lyricsColor, extraLineIds = listOf(R.id.widget_lyrics_line2)
            )
            wideLyrics.setOnClickPendingIntent(R.id.widget_previous, previousPendingIntent(context))
            wideLyrics.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent(context))
            wideLyrics.setOnClickPendingIntent(R.id.widget_next_or_prev, nextOnlyPendingIntent(context))
            wideLyrics.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

            // Variante compacta EXPANDIDA (180x90dp): activa + 2 siguientes líneas (3),
            // compensando el ancho más angosto con una línea extra de contexto.
            val compactLyrics = buildBaseViews(
                context, state, artBitmap, backgroundAlpha, R.layout.widget_music_flame_compact_lyrics,
                lyricsLines, lyricsColor, extraLineIds = listOf(R.id.widget_lyrics_line2, R.id.widget_lyrics_line3)
            )
            compactLyrics.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent(context))
            compactLyrics.setOnClickPendingIntent(R.id.widget_next_or_prev, nextOnlyPendingIntent(context))
            compactLyrics.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

            // El sistema elige automáticamente cuál RemoteViews mostrar según el tamaño
            // real que el usuario le dio al widget en su home screen (API 31+, ya cubierto
            // por el minSdk del proyecto). 180x40dp = 3 celdas (compacta), 250x40dp = 4
            // celdas (ancha). Los tamaños expandidos (74/90dp de alto) solo se eligen si
            // el usuario agranda el widget verticalmente en un launcher que lo soporte
            // (ver resizeMode="horizontal|vertical" en musicflame_widget_info.xml); en
            // cualquier otro caso el widget se comporta exactamente igual que antes.
            val sizeMap = mutableMapOf(
                SizeF(180f, 40f) to compact,
                SizeF(250f, 40f) to wide,
                SizeF(250f, 74f) to wideLyrics,
                SizeF(180f, 90f) to compactLyrics
            )

            // Variante de letra completa con grid de controles: opt-in vía
            // "Widget cuadrado de letra completa" en Ajustes > Lyrics, switch
            // SEPARADO del de "Letra en vivo en el widget" de arriba.
            //
            // A diferencia del intento anterior (que la agregaba como un tamaño
            // más dentro del sizeMap responsivo, obligando al usuario a agrandar
            // el widget a mano en el home screen para verla), ahora es automático:
            // en cuanto el switch está activo se devuelve directamente este único
            // RemoteViews fijo, ignorando el mapa de tamaños. Al desactivar el
            // switch, se vuelve a devolver el sizeMap de siempre (compact/wide/etc).
            if (settingsRepo.isFullLyricsSquareWidgetEnabled()) {
                // Todas las líneas de contexto guardadas (hasta MAX_LYRICS_CONTEXT_LINES),
                // unidas en un solo bloque; si no hay letra sincronizada (o está
                // desactivada la letra en general) cae al nombre del artista, igual
                // que el resto de las variantes.
                val fullLyricsText = if (lyricsLines.isNotEmpty()) {
                    lyricsLines.joinToString("\n")
                } else {
                    state.artist
                }

                val quadLyrics = buildSquareLyricsViews(
                    context, state, artBitmap, backgroundAlpha, fullLyricsText, lyricsColor
                )
                quadLyrics.setOnClickPendingIntent(R.id.widget_previous, previousPendingIntent(context))
                quadLyrics.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent(context))
                quadLyrics.setOnClickPendingIntent(R.id.widget_next_or_prev, nextOnlyPendingIntent(context))
                quadLyrics.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

                return quadLyrics
            }

            return RemoteViews(sizeMap)
        }

        /**
         * Variante de letra completa con grid de controles: a diferencia de
         * buildBaseViews() (que arma wide/compact con hasta 1-2 líneas separadas
         * en TextViews propios), esta variante junta TODAS las líneas de contexto
         * en un solo TextView multilinea con wrap y sin ellipsize
         * (widget_lyrics_full_block, mínimo 3 líneas), a la izquierda; a la
         * derecha va un grid fijo de 2x2 con Play/Pause y Siguiente arriba,
         * Carátula y Anterior abajo. No se reusa buildBaseViews() porque este
         * layout no tiene widget_song_artist ni TextViews de línea individual
         * (ver widget_music_flame_square_lyrics.xml).
         */
        private fun buildSquareLyricsViews(
            context: Context,
            state: WidgetPrefs.WidgetSongState,
            artBitmap: Bitmap,
            backgroundAlpha: Int,
            fullLyricsText: String,
            lyricsColor: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_music_flame_square_lyrics)

            if (state.hasSong) {
                views.setTextViewText(R.id.widget_song_title, state.title)
                views.setTextViewText(R.id.widget_lyrics_full_block, fullLyricsText)
                views.setTextColor(R.id.widget_lyrics_full_block, lyricsColor)
            } else {
                views.setTextViewText(R.id.widget_song_title, context.getString(R.string.widget_no_song))
                views.setTextViewText(R.id.widget_lyrics_full_block, context.getString(R.string.widget_select_song))
                views.setTextColor(R.id.widget_lyrics_full_block, Color.parseColor("#E6FFFFFF"))
            }

            views.setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            views.setInt(R.id.widget_album_art, "setBackgroundColor", Color.TRANSPARENT)
            views.setImageViewBitmap(R.id.widget_album_art, artBitmap)

            views.setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                ColorStateList.valueOf(Color.argb(backgroundAlpha, 28, 27, 31))
            )

            return views
        }

        /**
         * Resuelve el color de la letra en el widget según Ajustes > Lyrics
         * (mismo modo que usa LyricsView en el reproductor): "Blanco", "Negro" o
         * "Personalizado" se aplican tal cual; "Adaptativo" no tiene un
         * equivalente real en RemoteViews (no hay Material You aquí), así que
         * cae a blanco, que es lo más legible sobre el fondo oscuro del widget.
         *
         * "Arcoíris" (catálogo de pago) TAMPOCO tiene equivalente animado en
         * RemoteViews y cae a este mismo blanco por defecto (el `else` de
         * abajo) — a propósito: solo Personalizado/Blanco/Negro deben
         * reflejarse en el widget, Arcoíris nunca se aplica ahí aunque el
         * usuario lo tenga activo en la pantalla completa.
         */
        private fun resolveLyricsColorInt(settingsRepo: SettingsRepository): Int {
            return when (settingsRepo.getLyricsTextColorMode()) {
                "Blanco" -> Color.WHITE
                "Negro" -> Color.BLACK
                "Personalizado" -> parseHexColorOrNull(settingsRepo.getLyricsCustomColorHex()) ?: Color.WHITE
                else -> Color.WHITE
            }
        }

        private fun parseHexColorOrNull(hex: String): Int? = try {
            Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        } catch (e: Exception) {
            null
        }

        /**
         * Contenido común a las 4 variantes: texto, ícono play/pause, carátula y
         * opacidad del fondo. Los PendingIntents se agregan aparte porque cada
         * variante tiene botones distintos.
         *
         * [lyricsLines] trae hasta 3 líneas (activa + hasta 2 siguientes) cuando hay
         * letra sincronizada y el usuario no la desactivó en Ajustes; si viene vacía
         * (sin letra, canción no sincronizada, o desactivada) el widget se comporta
         * exactamente como antes, mostrando el nombre del artista.
         *
         * [extraLineIds] son los TextViews de líneas siguientes que existen SOLO en
         * las variantes expandidas (vacío en las clásicas de 40dp): se completan con
         * lyricsLines[1], lyricsLines[2]... y quedan en GONE si no hay texto para esa
         * línea, para que el layout no deje un hueco vacío.
         */
        private fun buildBaseViews(
            context: Context,
            state: WidgetPrefs.WidgetSongState,
            artBitmap: Bitmap,
            backgroundAlpha: Int,
            layoutRes: Int,
            lyricsLines: List<String>,
            lyricsColor: Int,
            extraLineIds: List<Int>
        ): RemoteViews {
            val views = RemoteViews(context.packageName, layoutRes)

            if (state.hasSong) {
                views.setTextViewText(R.id.widget_song_title, state.title)

                val activeLyricLine = lyricsLines.getOrNull(0)
                if (activeLyricLine != null) {
                    // Letra en vivo: la línea de "artista" pasa a mostrar la línea
                    // activa, con el color elegido en Ajustes > Lyrics.
                    views.setTextViewText(R.id.widget_song_artist, activeLyricLine)
                    views.setTextColor(R.id.widget_song_artist, lyricsColor)
                } else {
                    views.setTextViewText(R.id.widget_song_artist, state.artist)
                    views.setTextColor(R.id.widget_song_artist, Color.parseColor("#E6FFFFFF"))
                }
            } else {
                views.setTextViewText(R.id.widget_song_title, context.getString(R.string.widget_no_song))
                views.setTextViewText(R.id.widget_song_artist, context.getString(R.string.widget_select_song))
                views.setTextColor(R.id.widget_song_artist, Color.parseColor("#E6FFFFFF"))
            }

            // Líneas siguientes (solo en variantes expandidas): una por cada id en
            // extraLineIds, tomando lyricsLines[1], lyricsLines[2]... GONE si no hay
            // texto para esa línea (canción sin letra, o llegando al final de la letra).
            extraLineIds.forEachIndexed { index, viewId ->
                val text = lyricsLines.getOrNull(index + 1)
                if (!text.isNullOrBlank()) {
                    views.setTextViewText(viewId, text)
                    views.setTextColor(viewId, lyricsColor)
                    views.setViewVisibility(viewId, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(viewId, android.view.View.GONE)
                }
            }

            views.setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            // El fondo decorativo fijo del layout (rectángulo translúcido detrás del
            // ícono/carátula) queda redundante ahora: el bitmap que armamos (carátula
            // real o placeholder) ya es autocontenido y cubre toda su forma. Si lo
            // dejábamos prendido, en formas no cuadradas (Círculo, Hexágono, Vinilo,
            // Squircle) se veía como un halo cuadrado asomando detrás del recorte.
            views.setInt(R.id.widget_album_art, "setBackgroundColor", Color.TRANSPARENT)

            // Ya viene recortada a la forma elegida (carátula real o placeholder,
            // ver buildRemoteViews), así que siempre se aplica directo como bitmap.
            views.setImageViewBitmap(R.id.widget_album_art, artBitmap)

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

        /** Descarga/lee la carátula y la recorta a la forma elegida por el usuario. */
        private suspend fun loadRoundedAlbumArt(
            context: Context,
            artUriString: String?,
            shape: AlbumArtShapeType
        ): Bitmap? {
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

                    clipToShape(source, shape, cornerRadiusPx = 20f)
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
        }

        /**
         * Bitmap de respaldo cuando no hay carátula (o falló la carga): el mismo
         * ícono de nota musical de siempre, pero ahora sobre un fondo recortado a
         * la forma elegida en Ajustes > Apariencia, en vez del cuadrado fijo.
         */
        private fun buildPlaceholderArt(context: Context, shape: AlbumArtShapeType): Bitmap {
            val size = PLACEHOLDER_ART_SIZE_PX
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

            return clipToShape(base, shape, cornerRadiusPx = size * 20f / 48f)
        }

        /**
         * Recorta [bitmap] a la forma elegida en Ajustes > Apariencia, replicando
         * la misma geometría que clipShapeFor() en ui/components/AlbumArt.kt
         * (Compose). Antes el widget siempre recortaba a esquinas redondas fijas
         * sin importar la preferencia del usuario.
         */
        // internal (antes private): la reusa MusicFlameVinylWidgetProvider para
        // recortar el disco del widget "Vinilo" a la misma forma VINYL exacta
        // (círculo + surcos + hoyo central) sin duplicar esta geometría.
        internal fun clipToShape(bitmap: Bitmap, shape: AlbumArtShapeType, cornerRadiusPx: Float): Bitmap {
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            canvas.drawPath(clipPathFor(shape, w, h, cornerRadiusPx), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            // Detalle de disco de vinilo (surcos + hoyo central), igual que
            // VinylOverlay() en AlbumArt.kt: se dibuja ENCIMA, después del recorte.
            if (shape == AlbumArtShapeType.VINYL) {
                drawVinylOverlay(canvas, w, h)
            }

            return output
        }

        /**
         * Path de recorte por forma. Misma geometría, punto por punto, que
         * clipShapeFor() + HexagonShape/SquircleShape en AlbumArt.kt (Compose) —
         * portada de androidx.compose.ui.graphics.Path a android.graphics.Path
         * porque RemoteViews no puede usar Compose.
         */
        private fun clipPathFor(shape: AlbumArtShapeType, w: Float, h: Float, cornerRadiusPx: Float): Path {
            return when (shape) {
                AlbumArtShapeType.CIRCLE, AlbumArtShapeType.VINYL -> Path().apply {
                    addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
                }
                AlbumArtShapeType.HEXAGON -> Path().apply {
                    moveTo(w * 0.5f, 0f)
                    lineTo(w, h * 0.25f)
                    lineTo(w, h * 0.75f)
                    lineTo(w * 0.5f, h)
                    lineTo(0f, h * 0.75f)
                    lineTo(0f, h * 0.25f)
                    close()
                }
                AlbumArtShapeType.SQUIRCLE -> Path().apply {
                    // Superelipse |x|^n + |y|^n = 1 con n=4.0, mismos 72 pasos que
                    // SquircleShape en AlbumArt.kt, para que la curvatura coincida.
                    val cx = w / 2f
                    val cy = h / 2f
                    val steps = 72
                    val n = 4.0
                    for (i in 0..steps) {
                        val t = (i.toDouble() / steps) * 2 * Math.PI
                        val cosT = cos(t)
                        val sinT = sin(t)
                        val x = (sign(cosT) * kotlin.math.abs(cosT).pow(2.0 / n)) * cx + cx
                        val y = (sign(sinT) * kotlin.math.abs(sinT).pow(2.0 / n)) * cy + cy
                        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                    }
                    close()
                }
                AlbumArtShapeType.SQUARE -> Path().apply {
                    addRoundRect(RectF(0f, 0f, w, h), cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
                }
            }
        }

        /** Surcos finos + hoyo central, misma geometría que VinylOverlay() en AlbumArt.kt (Compose). */
        private fun drawVinylOverlay(canvas: Canvas, w: Float, h: Float) {
            val radius = minOf(w, h) / 2f
            val cx = w / 2f
            val cy = h / 2f

            val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((0.18f * 255).toInt(), 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.02f
            }
            listOf(0.62f, 0.75f, 0.88f).forEach { fraction ->
                canvas.drawCircle(cx, cy, radius * fraction, groovePaint)
            }

            val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((0.85f * 255).toInt(), 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius * 0.14f, holePaint)
        }
    }
}