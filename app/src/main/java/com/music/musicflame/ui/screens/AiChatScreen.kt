package com.music.musicflame.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import com.music.musicflame.data.AiChatSettings
import com.music.musicflame.data.AiCommand
import com.music.musicflame.data.AiCommands
import com.music.musicflame.data.PlaylistRepository
import com.music.musicflame.data.AiMessage
import com.music.musicflame.data.AiLibraryContext
import com.music.musicflame.data.LocalLlm
import com.music.musicflame.data.OnlineAiException
import com.music.musicflame.data.OnlineErrorKind
import com.music.musicflame.data.OnlineLlm
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.SongStat
import com.music.musicflame.data.StatsRepository
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.parseCustomTextColor
import com.music.musicflame.ui.theme.rainbowColorAt
import com.music.musicflame.ui.theme.rememberRainbowPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/** Playlist armada por /playlist que espera el "sí" del usuario antes de guardarse. */
private class PendingPlaylist(val name: String, val songs: List<Song>)

/** Pregunta de /quiz esperando respuesta (las opciones ya vienen barajadas). */
private class PendingQuiz(val options: List<String>, val correct: Int, val why: String)

/**
 * Pantalla de chat con la IA (6ª pestaña), según el boceto:
 *   tarjeta superior (orbe + nombre + frase)  →  burbujas (IA a la izquierda, tú a la derecha)  →
 *   caja de texto en píldora + botón enviar circular  →  (barra de navegación de la app).
 *
 * - Fondo: si la app tiene imagen de fondo, el chat es transparente y las superficies son translúcidas;
 *   si no, usa el fondo del tema. Nunca fuerza un fondo oscuro ni dibuja una franja detrás del input.
 * - Teclado: la zona de mensajes se adapta a la altura REAL disponible (BoxWithConstraints) y la tarjeta
 *   superior se compacta cuando hay poco espacio, así el input nunca se sale ni deja huecos.
 * - El orbe solo anima (suave) mientras la IA responde; el resto del tiempo no consume frames.
 * - La respuesta llega en streaming; la UI se refresca a ~22 fps como máximo para no frenar al modelo.
 */
@Composable
fun AiChatScreen(
    nowPlayingLabel: String?,
    isActive: Boolean = true,
    hasBackgroundImage: Boolean = false,
    nowPlayingSongId: Long? = null,
    onPlaySong: (Song, List<Song>) -> Unit = { _, _ -> },
    /** Se llama cuando un comando guarda una playlist, para que la pestaña Playlists se refresque. */
    onPlaylistsChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { AiChatSettings(context).also { it.purgeLegacyBackground() } }
    val scope = rememberCoroutineScope()

    // --- Ajustes en vivo: Ajustes > IA escribe en SharedPreferences y esto se entera solo ---
    var name by remember { mutableStateOf(settings.getName()) }
    var bubbleMode by remember { mutableStateOf(settings.getBubbleMode()) }
    var bubbleHex by remember { mutableStateOf(settings.getBubbleHex()) }
    var termsAccepted by remember { mutableStateOf(settings.isTermsAccepted()) }
    // IA online (Groq): el modo y el aviso de privacidad también se leen en vivo desde Ajustes > IA.
    var onlineMode by remember { mutableStateOf(settings.isOnlineMode()) }
    var onlineModel by remember { mutableStateOf(settings.getOnlineModel()) }
    var onlineTermsAccepted by remember { mutableStateOf(settings.isOnlineTermsAccepted()) }
    // Cada modo exige SU aviso: el offline (modelo local) o el online (los mensajes salen hacia Groq).
    val activeTermsAccepted = if (onlineMode) onlineTermsAccepted else termsAccepted
    var termsDismissed by remember { mutableStateOf(false) }   // "Ahora no": no insistir hasta reabrir la app
    var showTermsAgain by remember { mutableStateOf(false) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in AiChatSettings.OBSERVED_KEYS) {
                name = settings.getName(); bubbleMode = settings.getBubbleMode(); bubbleHex = settings.getBubbleHex()
                termsAccepted = settings.isTermsAccepted()
                onlineMode = settings.isOnlineMode(); onlineModel = settings.getOnlineModel()
                onlineTermsAccepted = settings.isOnlineTermsAccepted()
            }
        }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val messages = remember { mutableStateListOf<AiMessage>().apply { addAll(settings.loadHistory()) } }
    var input by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // --- Comandos "/": menú, selector de canciones y acciones que esperan una respuesta del usuario ---
    var slashOpen by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<AiCommand?>(null) }
    var pendingPlaylist by remember { mutableStateOf<PendingPlaylist?>(null) }
    var pendingQuiz by remember { mutableStateOf<PendingQuiz?>(null) }

    // --- Teclado ---
    // La app es edge-to-edge (targetSdk 36) y el chat ya se acomoda con imePadding(). Con el modo por defecto
    // (adjustUnspecified) Android ADEMÁS desplaza (pan) toda la ventana hacia arriba: el desplazamiento y el
    // imePadding se SUMAN y la caja de texto quedaba muy arriba. Mientras esta pestaña está activa se fuerza
    // adjustResize (sin pan, solo insets) y al salir se restaura el modo anterior para no afectar otras pantallas.
    DisposableEffect(isActive) {
        val window = context.findActivity()?.window
        val previous = window?.attributes?.softInputMode
        if (isActive && window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (previous != null) window?.setSoftInputMode(previous) }
    }

    // --- Estadísticas de escuchas para las tarjetas (se leen en IO, nunca en el hilo principal) ---
    var stats by remember { mutableStateOf<Map<Long, SongStat>>(emptyMap()) }
    // --- Modelo: se carga al entrar a la pestaña y se LIBERA tras 90 s fuera de ella ---
    // (el modelo ocupa >1.6 GB de RAM; dejarlo cargado mientras suena música ralentizaba toda la app).
    LaunchedEffect(isActive, onlineMode) {
        if (isActive) {
            stats = withContext(Dispatchers.IO) { StatsRepository(context).getAllStats() }
            // En modo online el modelo local no hace falta: se libera su RAM y solo se vuelve a cargar si se usa de respaldo.
            if (onlineMode) LocalLlm.unloadWhenIdle() else LocalLlm.preload(context)
        } else {
            delay(90_000L)
            LocalLlm.unloadWhenIdle()
        }
    }
    val librarySongs = SongLibraryHolder.songs
    val songsById = remember(librarySongs) { librarySongs.associateBy { it.id } }
    val persistLock = remember { Mutex() }
    fun persist() {
        val snapshot = messages.toList()
        scope.launch(Dispatchers.IO) { persistLock.withLock { settings.saveHistory(snapshot) } }   // nunca escribir a disco en el hilo principal
    }

    // Puntos animados solo mientras no haya llegado ningún trozo de la respuesta
    val showTyping = isThinking && messages.lastOrNull()?.fromUser != false
    LaunchedEffect(messages.size, showTyping) {
        val last = messages.size + (if (showTyping) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    // Al aparecer la confirmación de playlist o las opciones del quiz, dejarlas a la vista
    LaunchedEffect(pendingPlaylist, pendingQuiz) {
        val extra = (if (pendingPlaylist != null) 1 else 0) + (if (pendingQuiz != null) 1 else 0)
        if (extra > 0) listState.animateScrollToItem(messages.size + extra - 1)
    }
    // Mientras la respuesta va llegando, mantener la vista abajo
    val streamLen = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(streamLen) {
        if (isThinking && messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    // --- Estilo de burbujas ---
    val rainbowPhase = if (bubbleMode == AiChatSettings.MODE_RAINBOW && isActive)
        rememberRainbowPhase(AiChatSettings.BUBBLE_RAINBOW_CYCLE_SECONDS) else null
    val scheme = MaterialTheme.colorScheme
    val customColor = remember(bubbleHex) { parseCustomTextColor(bubbleHex) }

    // ---------- Comandos "/" (solo IA online) ----------
    fun upsertReply(replyId: Long, text: String, error: Boolean = false, songIds: List<Long>? = null) {
        val i = messages.indexOfFirst { it.id == replyId }
        if (i < 0) messages.add(AiMessage(replyId, false, text, isError = error, songIds = songIds ?: emptyList()))
        else messages[i] = messages[i].copy(text = text, isError = error, songIds = songIds ?: messages[i].songIds)
    }

    fun runCommand(text: String, preselected: List<Song>?) {
        pendingPlaylist = null; pendingQuiz = null
        messages.add(AiMessage(System.nanoTime(), true, text))
        persist()
        val replyId = System.nanoTime()
        val lang = context.resources.configuration.locales[0].language
        val parsed = AiCommands.parse(text)
        if (parsed == null) {
            upsertReply(replyId, context.getString(R.string.ai_cmd_unknown, AiCommands.labels(lang)))
            persist(); return
        }
        if (!onlineMode) {
            upsertReply(replyId, context.getString(R.string.ai_cmd_online_only))
            persist(); return
        }
        val library = SongLibraryHolder.songs
        val statsSnapshot = stats
        val nowId = nowPlayingSongId
        val aiName = name
        val args = parsed.args
        val needsLibrary = parsed.cmd in setOf(AiCommand.ANALYZE, AiCommand.OPINION, AiCommand.PLAYLIST, AiCommand.WRAPPED, AiCommand.ROAST, AiCommand.QUIZ)
        if (library.isEmpty() && needsLibrary) {
            upsertReply(replyId, context.getString(R.string.ai_cmd_no_library))
            persist(); return
        }
        isThinking = true
        val lastUi = AtomicLong(0L)
        fun streamTo(partial: String) {
            val now = SystemClock.uptimeMillis()
            if (now - lastUi.get() >= 45L) {
                lastUi.set(now)
                scope.launch(Dispatchers.Main.immediate) { upsertReply(replyId, partial) }
            }
        }
        scope.launch {
            try {
                when (parsed.cmd) {
                    AiCommand.ANALYZE, AiCommand.OPINION -> {
                        val res = if (preselected != null) AiCommands.Resolved(preselected.take(AiCommands.MAX_SONGS), emptyList(), false)
                        else withContext(Dispatchers.Default) { AiCommands.resolveSongs(args, library) }
                        if (res.songs.isEmpty()) {
                            val extra = if (res.notFound.isNotEmpty()) "\n" + context.getString(R.string.ai_cmd_not_found, res.notFound.joinToString(", ")) else ""
                            upsertReply(replyId, context.getString(R.string.ai_cmd_need_songs) + extra, error = true)
                            return@launch
                        }
                        val req = if (parsed.cmd == AiCommand.ANALYZE) AiCommands.analyze(aiName, lang, res.songs, statsSnapshot)
                        else AiCommands.opinion(aiName, lang, res.songs, statsSnapshot)
                        val answer = OnlineLlm.command(context, req.system, req.user, req.maxTokens, req.temperature, ::streamTo)
                        val notes = ArrayList<String>(2)
                        if (res.notFound.isNotEmpty()) notes += context.getString(R.string.ai_cmd_not_found, res.notFound.joinToString(", "))
                        if (res.truncated) notes += context.getString(R.string.ai_cmd_too_many, AiCommands.MAX_SONGS)
                        upsertReply(replyId, (listOf(answer) + notes).joinToString("\n\n"))
                    }
                    AiCommand.TELL -> {
                        val subject = AiCommands.tellSubject(args, library, statsSnapshot, nowId, lang)
                        subject.song?.let { upsertReply(replyId, "", songIds = listOf(it.id)) }
                        val req = AiCommands.tell(aiName, lang, subject)
                        upsertReply(replyId, OnlineLlm.command(context, req.system, req.user, req.maxTokens, req.temperature, ::streamTo))
                    }
                    AiCommand.WRAPPED, AiCommand.ROAST -> {
                        val data = withContext(Dispatchers.Default) { AiCommands.wrappedData(library, statsSnapshot, lang) }
                        if (parsed.cmd == AiCommand.WRAPPED && !data.hasPlays) {
                            upsertReply(replyId, context.getString(R.string.ai_cmd_wrapped_empty))
                            return@launch
                        }
                        val req = if (parsed.cmd == AiCommand.WRAPPED) {
                            upsertReply(replyId, "", songIds = data.topSongs.map { it.id })
                            AiCommands.wrapped(aiName, lang, data)
                        } else {
                            val summary = withContext(Dispatchers.Default) { AiLibraryContext.summary(library, statsSnapshot, lang) }
                            AiCommands.roast(aiName, lang, summary, data)
                        }
                        upsertReply(replyId, OnlineLlm.command(context, req.system, req.user, req.maxTokens, req.temperature, ::streamTo))
                    }
                    AiCommand.DUEL -> {
                        val pair = AiCommands.splitDuel(args)
                        if (pair == null) {
                            upsertReply(replyId, context.getString(R.string.ai_cmd_duel_need), error = true)
                            return@launch
                        }
                        val sides = withContext(Dispatchers.Default) {
                            AiCommands.duelSide(pair.first, library, statsSnapshot, lang) to AiCommands.duelSide(pair.second, library, statsSnapshot, lang)
                        }
                        val ids = (sides.first.songs + sides.second.songs).map { it.id }
                        if (ids.isNotEmpty()) upsertReply(replyId, "", songIds = ids)
                        val req = AiCommands.duel(aiName, lang, sides.first, sides.second)
                        upsertReply(replyId, OnlineLlm.command(context, req.system, req.user, req.maxTokens, req.temperature, ::streamTo))
                    }
                    AiCommand.PLAYLIST -> {
                        val cands = withContext(Dispatchers.Default) { AiCommands.playlistCandidates(args, library, statsSnapshot) }
                        val req = AiCommands.playlist(aiName, lang, args, cands)
                        val raw = OnlineLlm.command(context, req.system, req.user, req.maxTokens, req.temperature)
                        val result = AiCommands.parsePlaylist(raw, cands)
                        if (result == null) {
                            upsertReply(replyId, context.getString(R.string.ai_cmd_parse_fail), error = true)
                            return@launch
                        }
                        upsertReply(
                            replyId,
                            context.getString(R.string.ai_cmd_playlist_ready, result.name, result.songs.size, result.why).trim(),
                            songIds = result.songs.map { it.id }
                        )
                        pendingPlaylist = PendingPlaylist(result.name, result.songs)   // se pregunta antes de guardar nada
                    }
                    AiCommand.QUIZ -> {
                        val req = AiCommands.quiz(aiName, lang, library, statsSnapshot)
                        val raw = OnlineLlm.command(context, req.system, req.user, req.maxTokens, req.temperature)
                        val quiz = AiCommands.parseQuiz(raw)
                        if (quiz == null) {
                            upsertReply(replyId, context.getString(R.string.ai_cmd_parse_fail), error = true)
                            return@launch
                        }
                        upsertReply(replyId, quiz.question)
                        pendingQuiz = PendingQuiz(quiz.options, quiz.correct, quiz.why)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val why = if (e is OnlineAiException) onlineErrorText(context, e)
                else context.getString(R.string.ai_error_message, e.message?.take(140) ?: e.javaClass.simpleName)
                val existing = messages.firstOrNull { it.id == replyId }
                if (existing == null || existing.text.isBlank()) upsertReply(replyId, why, error = true)
                else upsertReply(replyId, existing.text + "\n\n" + why)   // se conserva lo que ya había llegado
            } finally {
                isThinking = false
                persist()
            }
        }
    }

    fun answerQuiz(i: Int) {
        val q = pendingQuiz ?: return
        pendingQuiz = null
        val now = System.nanoTime()
        messages.add(AiMessage(now, true, "${'A' + i}) ${q.options[i]}"))
        val head = if (i == q.correct) context.getString(R.string.ai_cmd_quiz_correct)
        else context.getString(R.string.ai_cmd_quiz_wrong, "${'A' + q.correct}) ${q.options[q.correct]}")
        val body = listOfNotNull(head, q.why.takeIf { it.isNotBlank() }, context.getString(R.string.ai_cmd_quiz_again)).joinToString("\n\n")
        messages.add(AiMessage(now + 1, false, body))
        persist()
    }

    fun savePendingPlaylist() {
        val p = pendingPlaylist ?: return
        pendingPlaylist = null
        scope.launch {
            val created = withContext(Dispatchers.IO) { PlaylistRepository(context).createPlaylistWithSongs(p.name, p.songs.map { it.id }) }
            messages.add(AiMessage(System.nanoTime(), false, context.getString(R.string.ai_cmd_playlist_saved, created.name, created.songIds.size)))
            persist()
            onPlaylistsChanged()
        }
    }

    fun discardPendingPlaylist() {
        pendingPlaylist = null
        messages.add(AiMessage(System.nanoTime(), false, context.getString(R.string.ai_cmd_playlist_discarded)))
        persist()
    }

    /** Toca un comando del menú: abre el selector, lo ejecuta o lo deja escrito para completar el argumento. */
    fun pickCommand(cmd: AiCommand, send: (String) -> Unit) {
        val lang = context.resources.configuration.locales[0].language
        slashOpen = false
        if (!onlineMode) {
            input = ""
            Toast.makeText(context, context.getString(R.string.ai_cmd_online_only), Toast.LENGTH_LONG).show()
            return
        }
        when {
            cmd.usesPicker -> { input = ""; pickerFor = cmd }
            cmd.runsAlone -> { input = ""; send("/" + cmd.label(lang)) }
            else -> input = "/" + cmd.label(lang) + " "
        }
    }

    fun send(override: String? = null) {
        val text = (override ?: input).trim()
        if (text.isEmpty() || isThinking) return
        if (!activeTermsAccepted) return
        if (text.startsWith("/")) {
            val parsedCmd = AiCommands.parse(text)
            input = ""; slashOpen = false
            // /analiza y /opinion sin canciones: se abre el selector en vez de mandar un mensaje vacío.
            if (parsedCmd != null && parsedCmd.cmd.usesPicker && parsedCmd.args.isBlank() && onlineMode) pickerFor = parsedCmd.cmd
            else runCommand(text, null)
            return
        }
        pendingPlaylist = null; pendingQuiz = null
        if (override == null) input = ""
        messages.add(AiMessage(System.nanoTime(), true, text))
        persist()
        isThinking = true
        val replyId = System.nanoTime()
        fun upsert(text: String, error: Boolean = false, songIds: List<Long>? = null) {
            val i = messages.indexOfFirst { it.id == replyId }
            if (i < 0) messages.add(AiMessage(replyId, false, text, isError = error, songIds = songIds ?: emptyList()))
            else messages[i] = messages[i].copy(text = text, isError = error)
        }
        val lastUiUpdate = AtomicLong(0L)
        val librarySnapshot = SongLibraryHolder.songs
        val lang = context.resources.configuration.locales[0].language   // idioma de la app (es/en)
        val statsSnapshot = stats
        scope.launch {
            var fallbackText: String? = null
            try {
                // 1) Búsqueda en la biblioteca REAL (sin IA, instantánea): las tarjetas aparecen YA,
                //    sin esperar al modelo, y la IA solo comenta datos verdaderos en vez de adivinar.
                val lib = withContext(Dispatchers.Default) {
                    AiLibraryContext.build(text, librarySnapshot, statsSnapshot, nowPlayingSongId)
                }
                // Resumen fijo (cuántas canciones, artistas y géneros top): la IA siempre conoce la biblioteca.
                val summary = withContext(Dispatchers.Default) { AiLibraryContext.summary(librarySnapshot, statsSnapshot, lang) }
                fallbackText = lib.fallbackText
                if (lib.picks.isNotEmpty()) upsert("", songIds = lib.picks.map { it.id })

                if (!onlineMode && !LocalLlm.isReady(context)) {
                    val hint = context.getString(R.string.ai_no_model_message)
                    if (lib.picks.isNotEmpty()) upsert(listOfNotNull(lib.fallbackText, hint).joinToString("\n\n"))
                    else upsert(hint, error = true)
                    return@launch
                }

                // 2) El modelo redacta el comentario con esos datos (online o local según el modo elegido).
                val history = messages.toList()
                var prefix = ""   // aviso que se antepone si la IA online falla y responde la offline de respaldo
                fun onPartial(partial: String) {
                    // callback desde hilo de fondo: se limita a ~22 fps y se pasa al hilo principal
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUiUpdate.get() >= 45L) {
                        lastUiUpdate.set(now)
                        val shown = prefix + partial
                        scope.launch(Dispatchers.Main.immediate) { upsert(shown) }
                    }
                }
                val answer = if (onlineMode) {
                    try {
                        OnlineLlm.reply(context, name, nowPlayingLabel, history, lib.promptBlock, lang, librarySummary = summary, onPartial = ::onPartial)
                    } catch (e: OnlineAiException) {
                        // Sin internet, cuota agotada o Groq caído: si hay modelo offline descargado, responde ese.
                        val canFallBack = e.kind in setOf(OnlineErrorKind.RATE_LIMIT, OnlineErrorKind.NETWORK, OnlineErrorKind.SERVER) &&
                            LocalLlm.isReady(context)
                        if (!canFallBack) throw e
                        prefix = context.getString(R.string.ai_online_fallback_notice) + "\n\n"
                        upsert(prefix.trim())
                        LocalLlm.reply(context, name, nowPlayingLabel, history, lib.promptBlock, lang, librarySummary = summary, onPartial = ::onPartial)
                    }
                } else {
                    LocalLlm.reply(context, name, nowPlayingLabel, history, lib.promptBlock, lang, librarySummary = summary, onPartial = ::onPartial)
                }
                upsert(prefix + answer)   // texto final completo, siempre
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {   // Throwable: un modelo local puede fallar por falta de RAM (OutOfMemoryError)
                val existing = messages.firstOrNull { it.id == replyId }
                if (e is OnlineAiException) {
                    // Error de la IA online: se explica con un texto claro (y se conservan las tarjetas si las hay).
                    if (existing == null || existing.text.isBlank()) {
                        val why = onlineErrorText(context, e)
                        val fb = fallbackText
                        if (existing != null && fb != null) upsert(listOf(fb, why).joinToString("\n\n"))
                        else upsert(why, error = true)
                    }
                } else if (existing == null || existing.text.isBlank()) {
                    val fb = fallbackText
                    if (existing != null && fb != null) upsert(fb)   // hay tarjetas: se conservan con un texto simple
                    else {
                        val reason = e.message?.take(140) ?: e.javaClass.simpleName
                        upsert(context.getString(R.string.ai_error_message, reason), error = true)
                    }
                }
            } finally {
                isThinking = false
                persist()
            }
        }
    }

    if (isActive && !activeTermsAccepted && !termsDismissed) {
        if (onlineMode) AiOnlineTermsDialog(
            onAccept = { settings.saveOnlineTermsAccepted(true); onlineTermsAccepted = true },
            onDismiss = { termsDismissed = true }
        ) else AiTermsDialog(
            onAccept = { settings.saveTermsAccepted(true); termsAccepted = true },
            onDismiss = { termsDismissed = true }
        )
    }
    if (showTermsAgain) {
        if (onlineMode) AiOnlineTermsDialog(
            onAccept = { settings.saveOnlineTermsAccepted(true); onlineTermsAccepted = true; showTermsAgain = false },
            onDismiss = { showTermsAgain = false }
        ) else AiTermsDialog(
            onAccept = { settings.saveTermsAccepted(true); termsAccepted = true; showTermsAgain = false },
            onDismiss = { showTermsAgain = false }
        )
    }

    pickerFor?.let { cmd ->
        SongPickerDialog(
            songs = librarySongs, max = AiCommands.MAX_SONGS,
            onConfirm = { picked ->
                pickerFor = null
                val pickLang = context.resources.configuration.locales[0].language
                // Los títulos escritos en el mensaje son solo para que el historial se lea bien: las canciones ya vienen elegidas.
                runCommand("/" + cmd.label(pickLang) + " " + picked.joinToString("; ") { it.title }, picked)
            },
            onDismiss = { pickerFor = null }
        )
    }

    // Colores de superficie: mismo diseño "cristal" con o sin imagen de fondo.
    // Mismo diseño "cristal" con o sin imagen: superficies translúcidas + borde fino. Con imagen son un poco más
    // opacas y hay un velo detrás de todo el chat (ver abajo), así el texto siempre se lee.
    val panelColor = scheme.surfaceContainer.copy(alpha = if (hasBackgroundImage) 0.78f else 0.92f)
    val onPanel = scheme.onSurface
    val pillColor = scheme.surfaceContainerHigh.copy(alpha = if (hasBackgroundImage) 0.90f else 0.97f)
    val lang = context.resources.configuration.locales[0].language
    val showPanel = activeTermsAccepted && ((slashOpen && input.isEmpty()) || (input.startsWith("/") && !input.any { it.isWhitespace() }))
    val panelCommands = remember(input, slashOpen, lang) {
        val q = input.removePrefix("/").lowercase()
        if (!input.startsWith("/") || q.isEmpty()) AiCommand.values().toList()
        else AiCommand.values().filter { c -> c.label(lang).startsWith(q) || c.aliases.any { it.startsWith(q) } }
    }

    val phrase = when {
        !nowPlayingLabel.isNullOrBlank() -> stringResource(R.string.ai_listening, nowPlayingLabel.orEmpty())
        else -> stringResource(R.string.ai_tagline)
    }

    Box(modifier = modifier.fillMaxSize().background(if (hasBackgroundImage) Color.Transparent else scheme.background)) {
        // Ambiente detrás del chat. Con imagen de fondo: velo degradado del color del tema (más marcado arriba, donde
        // está la tarjeta, y abajo, donde está la caja de texto) para que se lea sobre cualquier foto.
        // Sin imagen: un resplandor suave del color principal para que no se vea plano.
        Box(
            Modifier.matchParentSize().drawBehind {
                if (hasBackgroundImage) {
                    drawRect(Brush.verticalGradient(listOf(scheme.background.copy(alpha = 0.55f), scheme.background.copy(alpha = 0.30f), scheme.background.copy(alpha = 0.62f))))
                } else {
                    drawRect(Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width / 2f, 0f), radius = size.width * 1.2f))
                    drawRect(Brush.radialGradient(listOf(scheme.tertiary.copy(alpha = 0.08f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width))
                }
            }
        )
        // imePadding UNA sola vez; la altura útil real se mide justo debajo (ya sin el teclado).
        Box(Modifier.fillMaxSize().imePadding()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // La tarjeta de la IA NO se compacta al abrir el teclado; solo en alturas absurdamente chicas (p. ej. horizontal).
                val compact = maxHeight < 260.dp
                val isEmpty = messages.isEmpty() && !isThinking

                // Al abrir/cerrar el teclado, dejar el último mensaje a la vista
                LaunchedEffect(maxHeight) { if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1) }

                Column(Modifier.fillMaxSize()) {
                    // ---------- Tarjeta superior: orbe + nombre + frase ----------
                    AiHeader(
                        name = name, phrase = phrase, thinking = isThinking, compact = compact,
                        panelColor = panelColor, onPanel = onPanel,
                        statusText = if (onlineMode) stringResource(R.string.ai_status_online, onlineModel.displayName) else stringResource(R.string.ai_status_local),
                        statusOnline = onlineMode,
                        onClear = { messages.clear(); settings.clearHistory(); pendingPlaylist = null; pendingQuiz = null }
                    )
                    // Barra fina de tokens de hoy, solo en modo online (se actualiza sola con cada respuesta).
                    if (onlineMode && activeTermsAccepted) AiUsageBar(
                        model = onlineModel, compact = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp)
                    )

                    // ---------- Mensajes ----------
                    if (isEmpty) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // Solo una línea discreta (los atajos ahora son los comandos "/"), y solo con la IA online.
                        if (activeTermsAccepted && onlineMode && !compact) EmptyCommandHint(color = pillColor)
                    } else LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val picks = if (msg.songIds.isEmpty()) emptyList() else msg.songIds.mapNotNull { songsById[it] }
                            ChatBubble(
                                msg = msg, typing = false, mode = bubbleMode, customColor = customColor, rainbowPhase = rainbowPhase,
                                glass = hasBackgroundImage, songs = picks, stats = stats, waiting = isThinking && msg.id == messages.lastOrNull()?.id && !msg.fromUser,
                                onPlaySong = { song -> onPlaySong(song, picks) },
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("chat", msg.text))
                                    Toast.makeText(context, context.getString(R.string.ai_copied), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        pendingPlaylist?.let { _ ->
                            item(key = "pending_playlist") {
                                PlaylistConfirmCard(color = pillColor, onSave = { savePendingPlaylist() }, onDiscard = { discardPendingPlaylist() })
                            }
                        }
                        pendingQuiz?.let { quiz ->
                            item(key = "pending_quiz") {
                                QuizOptionsCard(options = quiz.options, color = pillColor, onPick = { answerQuiz(it) })
                            }
                        }
                        if (showTyping) {
                            item(key = "typing") {
                                ChatBubble(
                                    msg = AiMessage(-1L, false, "", timeMs = 0L), typing = true, mode = bubbleMode,
                                    customColor = customColor, rainbowPhase = rainbowPhase, onCopy = null
                                )
                            }
                        }
                    }

                    // ---------- Caja de texto en píldora + botón enviar circular (sin franja de fondo) ----------
                    if (!activeTermsAccepted) {
                        Surface(
                            shape = RoundedCornerShape(24.dp), color = pillColor,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ai_terms_required), fontSize = 13.sp, modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant)
                                Button(onClick = { showTermsAgain = true }) { Text(stringResource(R.string.ai_terms_view)) }
                            }
                        }
                    } else {
                        var focused by remember { mutableStateOf(false) }
                        val canSend = input.isNotBlank() && !isThinking
                        val sendColor by animateColorAsState(
                            if (canSend || isThinking) scheme.primary else scheme.surfaceContainerHighest.copy(alpha = 0.92f),
                            label = "sendColor"
                        )
                        AnimatedVisibility(
                            visible = showPanel && panelCommands.isNotEmpty(),
                            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
                        ) {
                            CommandPanel(
                                commands = panelCommands, lang = lang, online = onlineMode, color = pillColor,
                                onPick = { pickCommand(it) { t -> send(t) } }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Botón "/": abre/cierra el menú de comandos (visible también sin IA online; ahí avisa que la requiere).
                            Box(
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                                    .background(if (showPanel) scheme.primary else pillColor)
                                    .border(1.dp, if (showPanel) scheme.primary else scheme.outline.copy(alpha = 0.45f), CircleShape)
                                    .clickable {
                                        if (showPanel) { slashOpen = false; if (input.startsWith("/")) input = "" }
                                        else slashOpen = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "/", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                    color = if (showPanel) scheme.onPrimary else scheme.primary.copy(alpha = if (onlineMode) 1f else 0.55f)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(26.dp),
                                color = pillColor,
                                shadowElevation = 3.dp,
                                border = BorderStroke(
                                    if (focused) 1.5.dp else 1.dp,
                                    if (focused) scheme.primary else scheme.outline.copy(alpha = 0.45f)
                                )
                            ) {
                                Box(
                                    Modifier.heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (input.isEmpty()) Text(
                                        stringResource(R.string.ai_input_hint), fontSize = 16.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    BasicTextField(
                                        value = input, onValueChange = { input = it },
                                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                                        textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp, lineHeight = 22.sp),
                                        cursorBrush = SolidColor(scheme.primary),
                                        maxLines = 4,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = { send() })
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(
                                        if (canSend || isThinking) Brush.linearGradient(listOf(scheme.primary, lerp(scheme.primary, scheme.tertiary, 0.55f)))
                                        else SolidColor(sendColor)
                                    )
                                    .clickable(enabled = canSend) { send() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isThinking) CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = scheme.onPrimary
                                ) else Icon(
                                    Icons.AutoMirrored.Filled.Send, stringResource(R.string.ai_send),
                                    tint = if (canSend) scheme.onPrimary else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta superior en formato "cristal": orbe a la izquierda, nombre + frase + estado (online/en tu teléfono) a su lado.
 * Ocupa mucho menos alto que antes, así queda más sitio para la conversación. Con poco espacio todo se encoge.
 */
@Composable
private fun AiHeader(
    name: String, phrase: String, thinking: Boolean, compact: Boolean,
    panelColor: Color, onPanel: Color, statusText: String, statusOnline: Boolean, onClear: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(28.dp), color = panelColor,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.55f), scheme.outline.copy(alpha = 0.18f), scheme.tertiary.copy(alpha = 0.40f)))
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).animateContentSize()
    ) {
        Box(
            Modifier.background(
                Brush.horizontalGradient(listOf(scheme.primary.copy(alpha = 0.18f), scheme.tertiary.copy(alpha = 0.06f), Color.Transparent))
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 52.dp, top = if (compact) 8.dp else 12.dp, bottom = if (compact) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AiAvatar(size = if (compact) 34.dp else 44.dp, thinking = thinking)
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name, fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold, color = onPanel,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Crossfade(targetState = phrase, label = "phrase") {
                        Text(it, fontSize = 13.sp, color = onPanel.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!compact) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(onPanel.copy(alpha = 0.08f))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (statusOnline) Color(0xFF34C759) else scheme.outline))
                            Spacer(Modifier.width(6.dp))
                            Text(statusText, fontSize = 11.sp, color = onPanel.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, null, tint = onPanel.copy(alpha = 0.8f)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ai_clear_chat)) },
                        onClick = { menuOpen = false; onClear() }
                    )
                }
            }
        }
    }
}

/**
 * Orbe de la IA. Quieto = aro con degradado + brillo suave. Respondiendo = "respira" (escala ±5 %),
 * el aro gira despacio y el brillo late. La animación solo corre mientras [thinking] es true y se lee
 * dentro de graphicsLayer/drawBehind (no recompone nada).
 */
@Composable
private fun AiAvatar(size: Dp, thinking: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val active = animateFloatAsState(if (thinking) 1f else 0f, tween(400), label = "orbActive")
    val phase = remember { mutableFloatStateOf(0f) }   // 0..1 cada 4.8 s
    LaunchedEffect(thinking) {
        if (thinking) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> phase.floatValue = ((now - start) / 4_800_000_000.0).toFloat() % 1f }
            }
        }
    }
    fun breath(): Float = (sin(phase.floatValue * 2f * PI.toFloat() * 2f) + 1f) / 2f   // 0..1, una respiración cada 2.4 s

    Box(
        modifier = Modifier.size(size * 1.5f).graphicsLayer {
            val s = 1f + 0.05f * active.value * breath()
            scaleX = s; scaleY = s
        },
        contentAlignment = Alignment.Center
    ) {
        // brillo
        Box(
            Modifier.matchParentSize().drawBehind {
                val a = 0.16f + 0.16f * active.value * breath()
                val r = this.size.minDimension / 2f
                drawCircle(Brush.radialGradient(listOf(scheme.primary.copy(alpha = a), Color.Transparent), radius = r), radius = r)
            }
        )
        // aro (gira solo mientras responde)
        Box(
            Modifier.size(size)
                .graphicsLayer { rotationZ = phase.floatValue * 360f * active.value }
                .border(4.dp, Brush.sweepGradient(listOf(scheme.tertiary, scheme.primary, scheme.secondary, scheme.tertiary)), CircleShape)
        )
        // centro (no gira)
        Box(
            Modifier.size(size - 10.dp).clip(CircleShape).background(scheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = scheme.primary, modifier = Modifier.size(size * 0.42f))
        }
    }
}

@Composable
private fun TypingDots(color: Color) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(
        Modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 160)
                ),
                label = "dot$i"
            )
            Box(Modifier.size(8.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun ChatBubble(
    msg: AiMessage,
    typing: Boolean,
    mode: String,
    customColor: Color,
    rainbowPhase: State<Float>?,
    onCopy: (() -> Unit)?,
    songs: List<Song> = emptyList(),
    stats: Map<Long, SongStat> = emptyMap(),
    waiting: Boolean = false,
    glass: Boolean = false,
    onPlaySong: ((Song) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val isUser = msg.fromUser
    // Esquina inferior del lado de quien habla casi recta ("colita").
    val shape: Shape = RoundedCornerShape(
        topStart = 22.dp, topEnd = 22.dp,
        bottomStart = if (isUser) 22.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 22.dp
    )

    // Color base de la burbuja según el modo (los errores siempre usan el color de error del tema).
    val staticColor: Color? = when {
        msg.isError -> scheme.errorContainer
        mode == AiChatSettings.MODE_CUSTOM ->
            if (isUser) customColor else customColor.copy(alpha = 0.22f).compositeOver(scheme.surfaceContainerHigh)
        mode == AiChatSettings.MODE_RAINBOW -> null
        else -> if (isUser) scheme.primary else scheme.surfaceContainerHigh   // Adaptativo (Material You)
    }
    // El Arcoíris se pinta en la fase de dibujo (drawBehind) para no recomponer el chat en cada tick.
    val bubbleBase: Color = staticColor ?: Color.White
    val textColor = when {
        msg.isError -> scheme.onErrorContainer
        staticColor == null -> Color(0xFF101010)
        (staticColor?.luminance() ?: 0f) > 0.5f -> Color(0xFF101010)
        else -> Color.White
    }
    val contentColor = if (staticColor == null && !isUser) scheme.onSurface else textColor

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .drawBehind {
                    if (isUser && staticColor != null && !msg.isError && mode != AiChatSettings.MODE_CUSTOM) {
                        // Adaptativo: tus burbujas llevan un degradado sutil del color del tema.
                        drawRect(
                            Brush.linearGradient(
                                listOf(scheme.primary, lerp(scheme.primary, scheme.tertiary, 0.55f)),
                                start = Offset.Zero, end = Offset(size.width, size.height)
                            )
                        )
                    } else {
                        val c = if (staticColor != null) bubbleBase else {
                            val phase = rainbowPhase?.value ?: 0f
                            val base = rainbowColorAt(phase, spreadFraction = if (isUser) 0f else 0.5f)
                            if (isUser) base else base.copy(alpha = 0.30f).compositeOver(scheme.surfaceContainerHigh)
                        }
                        // Con imagen de fondo las burbujas de la IA son un poco translúcidas (efecto cristal).
                        drawRect(if (!isUser && glass && !msg.isError) c.copy(alpha = c.alpha * 0.9f) else c)
                    }
                }
                .then(if (isUser) Modifier else Modifier.border(1.dp, scheme.outline.copy(alpha = 0.22f), shape))
                .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp)
        ) {
            if (typing) TypingDots(contentColor.copy(alpha = 0.7f)) else Column {
                if (msg.text.isBlank() && waiting) TypingDots(contentColor.copy(alpha = 0.7f))
                else if (msg.text.isNotBlank()) Text(msg.text, color = contentColor, fontSize = 16.sp, lineHeight = 23.sp)
                if (songs.isNotEmpty() && onPlaySong != null) SongPickCards(songs, stats, contentColor, onPlaySong)
                if (!isUser && onCopy != null && msg.text.isNotBlank()) {
                    Icon(
                        Icons.Outlined.ContentCopy, stringResource(R.string.ai_copy),
                        tint = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End).padding(top = 10.dp).size(20.dp).clickable { onCopy() }
                    )
                }
            }
        }
    }
}

/** Busca la Activity detrás de un Context (puede venir envuelto en ContextWrapper). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Canciones reales de la biblioteca sugeridas por la IA: portada, título, artista · álbum y sus características
 * (género, año, duración, escuchas). Tocar una la reproduce; "Reproducir todas" pone la lista completa.
 */
@Composable
private fun SongPickCards(songs: List<Song>, stats: Map<Long, SongStat>, contentColor: Color, onPlay: (Song) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        songs.forEach { song ->
            val plays = stats[song.id]?.playCount ?: 0
            val playsLabel = if (plays > 0) stringResource(R.string.ai_trait_plays, plays) else stringResource(R.string.ai_trait_unplayed)
            val traits = remember(song.id, plays) { AiLibraryContext.baseTraits(song) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(contentColor.copy(alpha = 0.10f))
                    .clickable { onPlay(song) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(song.albumArtUri, 44.dp, 10.dp, filePath = song.path, isCustomCover = song.hasCustomCover)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(song.artist, song.album.takeIf { it.isNotBlank() && !it.equals("Desconocido", true) }).filterNotNull().joinToString(" · "),
                        color = contentColor.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        (traits + playsLabel).joinToString(" · "),
                        color = contentColor.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Filled.PlayArrow, null, tint = scheme.primary, modifier = Modifier.size(26.dp))
            }
        }
        if (songs.size > 1) Text(
            stringResource(R.string.ai_play_all), color = scheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(10.dp)).clickable { onPlay(songs.first()) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Texto claro (en el idioma de la app) para cada tipo de error de la IA online. */
private fun onlineErrorText(context: Context, e: OnlineAiException): String = when (e.kind) {
    OnlineErrorKind.NO_KEY -> context.getString(R.string.ai_online_err_no_key)
    OnlineErrorKind.INVALID_KEY -> context.getString(R.string.ai_online_err_invalid_key)
    OnlineErrorKind.RATE_LIMIT ->
        if (e.retryAfterSec != null && e.retryAfterSec in 1..120) context.getString(R.string.ai_online_err_rate_limit_wait, e.retryAfterSec.toString())
        else context.getString(R.string.ai_online_err_rate_limit)
    OnlineErrorKind.NETWORK -> context.getString(R.string.ai_online_err_network)
    OnlineErrorKind.SERVER -> context.getString(R.string.ai_online_err_server)
    OnlineErrorKind.OTHER -> context.getString(R.string.ai_online_err_other, e.message?.take(140) ?: "")
}
