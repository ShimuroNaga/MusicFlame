package com.music.musicflame.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.music.musicflame.R
import com.music.musicflame.data.AiChatSettings
import com.music.musicflame.data.FavoritesRepository
import com.music.musicflame.data.JarvisBrain
import com.music.musicflame.data.JarvisStep
import com.music.musicflame.data.JarvisTool
import com.music.musicflame.data.AiLibraryContext
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.OnlineAiException
import com.music.musicflame.data.OnlineAiStore
import com.music.musicflame.data.PlaylistKind
import com.music.musicflame.data.PlaylistRepository
import com.music.musicflame.data.SmartPlaylistIds
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.buildMostPlayedPlaylist
import com.music.musicflame.data.buildNeverPlayedPlaylist
import com.music.musicflame.together.TogetherManager
import com.music.musicflame.ui.theme.MusicFlameTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Asistente de voz (superposición translúcida). Lo abren: el botón power (si MusicFlame es el asistente del
 * teléfono), el tile del panel rápido y "Probar ahora" en Ajustes > IA.
 *
 * Conversación continua: escucha → Whisper (Groq) o reconocimiento de Android → la IA contesta con
 * personalidad y, si hace falta, ejecuta una orden (playlist, canción, pausa, siguiente...) → habla con la
 * voz del sistema. Sigue escuchando mientras la charla continúe.
 */
class VoiceAssistantActivity : ComponentActivity() {

    private enum class Phase { LISTENING, THINKING, SPEAKING, DONE }

    private var phase by mutableStateOf(Phase.LISTENING)
    private var heard by mutableStateOf("")
    private var reply by mutableStateOf("")
    private var level by mutableStateOf(0f)
    private var userName by mutableStateOf("")
    private var aiName by mutableStateOf("Flame")
    private var chips by mutableStateOf<List<String>>(emptyList())

    /** Borrador de playlist que se muestra como tabla con casillas (Guardar / Descartar, o "sí" / "no" por voz). */
    private class DraftRow(val song: Song, initial: Boolean) { var checked by mutableStateOf(initial) }
    private var draftName by mutableStateOf("")
    private var draftRows by mutableStateOf<List<DraftRow>>(emptyList())
    private var buttonAnswer: Boolean? = null

    private companion object {
        /** Última acción reversible del asistente (para "deshaz eso"); vive mientras viva el proceso. */
        @Volatile var lastUndo: VoiceUndo? = null
    }

    private var stopRequested = false
    private var chipText: String? = null
    private var cancelSystemRecognizer: (() -> Unit)? = null

    private var session: Job? = null
    private var recorder: VoiceRecorder? = null
    private var tts: TextToSpeech? = null
    private var player: MusicPlayerManager? = null
    private lateinit var settings: VoiceSettings
    private lateinit var lang: String

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startSession() else finishWith(getString(R.string.voice_no_mic_permission))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = VoiceSettings(this)
        lang = resources.configuration.locales[0].language.let { if (it == "en") "en" else "es" }
        reply = greeting(null)

        setContent { MusicFlameTheme { VoiceOverlay() } }

        if (!settings.isEnabled()) { finishWith(getString(R.string.voice_disabled)); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else startSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (settings.isEnabled() && phase == Phase.DONE) { session?.cancel(); startSession() }
    }

    override fun onDestroy() {
        session?.cancel()
        recorder?.cancel()
        tts?.shutdown()
        player?.release()
        super.onDestroy()
    }

    private fun greeting(name: String?): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val res = when (h) { in 5..11 -> R.string.voice_greet_morning; in 12..18 -> R.string.voice_greet_afternoon; else -> R.string.voice_greet_night }
        val base = getString(res)
        return if (name.isNullOrBlank()) base else "$base, $name"
    }

    // ------------------------------------------------------------------ flujo principal

    private fun startSession() {
        stopRequested = false; chipText = null; heard = ""
        session = lifecycleScope.launch {
            val appCtx = applicationContext
            // Todo lo pesado arranca en paralelo para que el micrófono abra YA.
            val keyD = async(Dispatchers.IO) { OnlineAiStore(appCtx).getApiKey() }
            val nameD = async(Dispatchers.IO) { settings.resolveUserName(appCtx) }
            val listsD = async(Dispatchers.IO) { loadPlaylists() }
            val memD = async(Dispatchers.IO) {
                try { SongLibraryHolder.ensureLoaded(appCtx); VoiceMemory.build(appCtx, lang) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { "" }
            }
            val pm = MusicPlayerManager(appCtx).also { player = it }
            var wasPlaying = false
            pm.whenReady { if (pm.isPlayingState.value) { wasPlaying = true; pm.pause() } }   // pausa sin esperar
            if (settings.speaksReplies()) launch { ensureTts() }

            val key = keyD.await()
            aiName = AiChatSettings(appCtx).getName()
            launch { userName = nameD.await(); if (heard.isBlank() && phase == Phase.LISTENING) reply = greeting(userName) }

            var musicHandled = false
            val dialog = ArrayList<Pair<Boolean, String>>()
            var turns = 0

            try {
                while (turns < 8) {
                    val first = turns == 0
                    val said = listen(key, silentIfNoSpeech = !first)
                    if (said.isNullOrBlank()) { if (first) speak(getString(R.string.voice_not_heard)); break }
                    turns++
                    heard = said
                    dialog += true to said
                    phase = Phase.THINKING

                    val playlists = listsD.await()
                    val vc = VoiceContext(
                        aiName, nameD.await(), lang, Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                        pm.currentSong.value?.let { "«${it.title}» — ${it.artist}" }, playlists,
                        settings.getMode(), settings.getCustomPrompt(), memD.await()
                    )
                    val action = think(key, vc, dialog, said)
                    dialog += false to action.say
                    launch(Dispatchers.IO) { VoiceMemory.remember(appCtx, said, action.say) }

                    when (action) {
                        is VoiceAction.PlayPlaylist -> {
                            speak(action.say)
                            if (playPlaylist(action)) { musicHandled = true; break }
                            reply = getString(R.string.voice_playlist_empty); speak(reply)
                        }
                        is VoiceAction.PlaySong -> {
                            SongLibraryHolder.ensureLoaded(appCtx)
                            val library = SongLibraryHolder.songs
                            val song = withContext(Dispatchers.Default) { AiLibraryContext.findSong(action.query, library) }
                            if (song == null) {
                                val m = getString(R.string.voice_song_not_found, action.query)
                                dialog += false to m; speak(m)
                            } else {
                                speak(action.say)
                                pm.whenReady { pm.playSong(song, SongLibraryHolder.songs) }
                                delay(300); musicHandled = true; break
                            }
                        }
                        is VoiceAction.Control -> {
                            if (action.say.isNotBlank()) speak(action.say)
                            musicHandled = true
                            runControl(pm, action.kind)
                            break
                        }
                        is VoiceAction.Chat -> {
                            speak(action.say)
                            if (!action.listenAgain) break
                        }
                        is VoiceAction.End -> { if (action.say.isNotBlank()) speak(action.say); break }
                        is VoiceAction.Act -> {
                            val (spoken, handled) = runAct(key, pm, action)
                            dialog += false to spoken
                            if (handled) { musicHandled = true; break }
                            if (!action.listenAgain) break
                        }
                        is VoiceAction.Stats -> {
                            SongLibraryHolder.ensureLoaded(appCtx)
                            val now = pm.currentSong.value
                            val text = withContext(Dispatchers.IO) { VoiceStats.answer(appCtx, action.kind, lang, now) }
                            dialog += false to text
                            speak(text); break
                        }
                        is VoiceAction.Undo -> {
                            val u = lastUndo
                            val msg = if (u == null || u.isEmpty) getString(R.string.voice_nothing_to_undo)
                                else { u.run(pm); lastUndo = null; getString(R.string.voice_undone) }
                            dialog += false to msg
                            speak(msg); break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reply = getString(R.string.voice_error); phase = Phase.SPEAKING
                delay(1500)
            }
            if (wasPlaying && !musicHandled && !pm.isPlayingState.value) pm.togglePlayPause()
            finishWith(null)
        }
    }

    private suspend fun think(key: String?, vc: VoiceContext, dialog: List<Pair<Boolean, String>>, said: String): VoiceAction {
        if (key != null && hasInternet()) {
            try { return VoiceBrain.decide(applicationContext, vc, dialog) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* cae al respaldo local */ }
        }
        return VoiceBrain.local(said, vc)
    }

    private fun loadPlaylists(): List<VoicePlaylist> {
        val ctx = applicationContext
        val list = ArrayList<VoicePlaylist>()
        // Las 3 predeterminadas primero; las vacías no se ofrecen.
        if (FavoritesRepository(ctx).getAllFavoriteIds().isNotEmpty())
            list += VoicePlaylist("favorites", getString(R.string.voice_playlist_favorites), PlaylistKind.FAVORITES)
        try {
            buildMostPlayedPlaylist(ctx).takeIf { it.songIds.isNotEmpty() }
                ?.let { list += VoicePlaylist(SmartPlaylistIds.MOST_PLAYED, it.name, PlaylistKind.MOST_PLAYED) }
            buildNeverPlayedPlaylist(ctx).takeIf { it.songIds.isNotEmpty() }
                ?.let { list += VoicePlaylist(SmartPlaylistIds.NEVER_PLAYED, it.name, PlaylistKind.NEVER_PLAYED) }
        } catch (_: Exception) { /* biblioteca aún sin cargar: se ofrecen solo las demás */ }
        PlaylistRepository(ctx).getAllPlaylists()
            .filter { it.songIds.isNotEmpty() && it.name.isNotBlank() }
            .forEach { list += VoicePlaylist(it.id, it.name, PlaylistKind.REGULAR) }
        chips = list.take(4).map { it.name }
        return list
    }

    private suspend fun playPlaylist(a: VoiceAction.PlayPlaylist): Boolean {
        val pm = player ?: return false
        val ctx = applicationContext
        SongLibraryHolder.ensureLoaded(ctx)
        val ids: List<Long> = withContext(Dispatchers.IO) {
            when (a.playlist.kind) {
                PlaylistKind.FAVORITES -> FavoritesRepository(ctx).getAllFavoriteIds().toList()
                PlaylistKind.MOST_PLAYED -> buildMostPlayedPlaylist(ctx).songIds
                PlaylistKind.NEVER_PLAYED -> buildNeverPlayedPlaylist(ctx).songIds
                PlaylistKind.REGULAR -> PlaylistRepository(ctx).getAllPlaylists().find { it.id == a.playlist.id }?.songIds ?: emptyList()
            }
        }
        val byId = SongLibraryHolder.songs.associateBy { it.id }
        var songs = ids.mapNotNull { byId[it] }
        if (songs.isEmpty()) return false
        if (a.shuffle) songs = songs.shuffled()
        pm.whenReady { pm.playSong(songs.first(), songs, a.playlist.id, a.playlist.kind) }
        delay(300)
        return true
    }

    private data class ActResult(val spoken: String, val musicHandled: Boolean)

    /**
     * Ejecuta las acciones de la lista blanca. La IA solo describe; [JarvisBrain.prepare] las resuelve contra la
     * biblioteca REAL. Los cambios masivos piden "sí"/"no" y las playlists nuevas pasan por la tabla de borrador.
     */
    private suspend fun runAct(key: String?, pm: MusicPlayerManager, a: VoiceAction.Act): ActResult {
        val ctx = applicationContext
        SongLibraryHolder.ensureLoaded(ctx)
        val steps = JarvisBrain.prepare(ctx, lang, a.calls, pm.currentSong.value?.id)
        val creates = steps.filter { it.call.tool == JarvisTool.CREATE_PLAYLIST }
        val timers = steps.filter { it.call.tool == JarvisTool.SLEEP_TIMER || it.call.tool == JarvisTool.CANCEL_SLEEP_TIMER }
        val together = steps.filter { it.call.tool == JarvisTool.TOGETHER_CREATE || it.call.tool == JarvisTool.TOGETHER_INVITE }
        val others = steps.filter { it !in creates && it !in timers && it !in together }
        val out = ArrayList<String>()
        var musicHandled = false

        var go = true
        JarvisBrain.confirmQuestion(others, lang)?.let { q ->
            val ans = askYesNo(key, q)
            go = ans == true
            if (!go) { val m = getString(if (ans == null) R.string.voice_no_answer else R.string.voice_cancelled); speak(m); return ActResult(m, false) }
        }

        if (go && (others.isNotEmpty() || timers.isNotEmpty())) {
            val undo = VoiceUndo.capture(ctx, others + timers, pm)
            val plays = others.any { it.problem == null && it.call.tool.id.startsWith("play_") }
            var spokenFirst = false
            if (plays) { speak(a.say.ifBlank { if (lang == "en") "On it." else "Enseguida." }); spokenFirst = true }

            val lines = ArrayList<String>()
            for (t in timers) {
                if (t.call.tool == JarvisTool.SLEEP_TIMER) {
                    val m = (t.call.value ?: 0).coerceIn(1, 600)
                    VoiceSleepTimer.start(ctx, m); lines += getString(R.string.voice_sleep_set, m)
                } else { VoiceSleepTimer.cancel(); lines += getString(R.string.voice_sleep_off) }
            }
            val res = JarvisBrain.execute(ctx, lang, others, pm) { first, list -> pm.whenReady { pm.playSong(first, list) } }
            lines += res.lines
            if (!undo.isEmpty) lastUndo = undo
            musicHandled = plays || others.any { it.problem == null && it.call.tool == JarvisTool.PAUSE }
            if (plays) delay(300)
            // Si el modelo dijo una frase, esa es la voz; se añaden solo los avisos de lo que no se pudo hacer.
            val problems = others.mapNotNull { it.problem }
            val text = if (a.say.isNotBlank()) (listOf(a.say) + problems).joinToString(" ") else lines.joinToString(". ")
            if (!spokenFirst && text.isNotBlank()) speak(text)
            out += if (spokenFirst) a.say else text
        }

        if (go) for (t in together) {
            val r = handleTogether(invite = t.call.tool == JarvisTool.TOGETHER_INVITE)
            speak(r); out += r
        }

        for (c in creates) {
            val r = handleDraft(key, c)
            if (r.isNotBlank()) { speak(r); out += r }
        }
        return ActResult(out.joinToString(" "), musicHandled)
    }

    /**
     * Sala de Juntos. Usa el TogetherManager de la app (el mismo de la pantalla "En compañía"); si MusicFlame no
     * está abierta no hay manager y se pide abrirla. Si ya estás en una sala NO se crea otra: se reutiliza.
     */
    private suspend fun handleTogether(invite: Boolean): String {
        val tm = TogetherManager.current ?: return getString(R.string.voice_together_no_app)
        var created = false
        if (tm.roomCode == null) {
            tm.createRoom()
            delay(400)
            val ok = withTimeoutOrNull(15_000) {
                while (tm.roomCode == null && tm.status != TogetherManager.Status.ERROR) delay(250)
                tm.roomCode != null
            } ?: false
            if (!ok) return getString(R.string.voice_together_failed)
            created = true
        }
        val code = tm.roomCode.orEmpty()
        if (!invite) return getString(if (created) R.string.voice_together_created else R.string.voice_together_existing, code)
        val msg = getString(R.string.tg_share_message, code, tm.roomLink.orEmpty())
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg) }
        startActivity(Intent.createChooser(send, getString(R.string.tg_invite_to_room)))
        return getString(R.string.voice_together_invite, code)
    }

    /** Muestra la tabla del borrador y espera "sí"/"no" (por voz o con los botones). Devuelve lo que hay que decir. */
    private suspend fun handleDraft(key: String?, step: JarvisStep): String {
        val name = (step.label ?: "MusicFlame").take(40)
        val candidatesMode = step.problem != null
        if (candidatesMode && step.candidates.isEmpty()) return step.problem ?: ""
        val rows = if (candidatesMode) step.candidates.map { DraftRow(it, false) } else step.songs.map { DraftRow(it, true) }
        draftName = name; draftRows = rows
        val q = if (candidatesMode) getString(R.string.voice_draft_ask_candidates, name) else getString(R.string.voice_draft_ask, name, rows.size)
        val ans = askYesNo(key, q)
        val chosen = rows.filter { it.checked }.map { it.song }
        draftRows = emptyList()
        return when {
            ans == null -> getString(R.string.voice_no_answer)
            ans == false -> getString(R.string.voice_draft_discarded)
            chosen.isEmpty() -> getString(R.string.voice_draft_none_checked)
            else -> JarvisBrain.execute(applicationContext, lang, listOf(JarvisStep(step.call, songs = chosen, label = name)), null) { _, _ -> }
                .lines.joinToString(". ")
        }
    }

    /** Dice la pregunta y escucha un sí/no. null = no quedó claro (se trata como "no cambies nada"). */
    private suspend fun askYesNo(key: String?, question: String): Boolean? {
        buttonAnswer = null
        speak(question)
        buttonAnswer?.let { buttonAnswer = null; return it }
        for (i in 0 until 2) {
            val said = listen(key, silentIfNoSpeech = false)
            if (said.isNullOrBlank()) return null
            heard = said
            VoiceBrain.yesNo(said)?.let { return it }
        }
        return null
    }

    /** Botones Guardar / Descartar de la tabla: equivalen a decir "sí" / "no". */
    private fun answerDraft(yes: Boolean) {
        buttonAnswer = yes
        if (phase == Phase.SPEAKING) tts?.stop() else submit(if (yes) "sí" else "no")
    }

    private suspend fun runControl(pm: MusicPlayerManager, kind: VoiceControl) {
        when (kind) {
            VoiceControl.PAUSE -> pm.pause()
            VoiceControl.RESUME -> if (!pm.isPlayingState.value) pm.togglePlayPause()
            VoiceControl.NEXT -> { pm.skipNext(); delay(250); if (!pm.isPlayingState.value) pm.togglePlayPause() }
            VoiceControl.PREVIOUS -> { pm.skipPrevious(); delay(250); if (!pm.isPlayingState.value) pm.togglePlayPause() }
        }
        delay(250)
    }

    // ------------------------------------------------------------------ escuchar

    /** Whisper si hay key e internet; si no (o si falla), reconocimiento de Android. Un chip tocado gana siempre. */
    private suspend fun listen(key: String?, silentIfNoSpeech: Boolean): String? {
        phase = Phase.LISTENING; level = 0f; stopRequested = false
        chipText = null; heard = ""
        var text: String? = null
        if (key != null && hasInternet()) {
            val file = recordUntilSilence(if (silentIfNoSpeech) 6000 else 7000)
            chipText?.let { file?.delete(); return it }
            if (file == null) return null
            phase = Phase.THINKING
            try {
                text = WhisperClient.transcribe(key, file, lang).ifBlank { null }
                return text
            } catch (e: OnlineAiException) {
                phase = Phase.LISTENING; reply = getString(R.string.voice_whisper_fallback)
            } finally { file.delete() }
        }
        text = systemRecognize()
        return chipText ?: text
    }

    private suspend fun recordUntilSilence(noSpeechMs: Long): java.io.File? {
        val rec = VoiceRecorder(this).also { recorder = it }
        try { rec.start() } catch (e: Exception) {
            rec.cancel(); recorder = null; reply = getString(R.string.voice_mic_busy); delay(1500); return null
        }
        val t0 = System.currentTimeMillis()
        var lastVoice = t0
        var spoke = false
        try {
            while (true) {
                delay(60)
                val now = System.currentTimeMillis()
                val amp = rec.level()
                level = (amp / 12000f).coerceIn(0f, 1f)
                if (amp > 2200) { spoke = true; lastVoice = now }
                if (stopRequested || chipText != null) break
                if (spoke && now - lastVoice > 900) break      // fin de frase: respuesta más rápida
                if (!spoke && now - t0 > noSpeechMs) break
                if (now - t0 > 15000) break
            }
        } finally { level = 0f }
        val f = rec.stop(); recorder = null
        return if (spoke || chipText != null) f else { f?.delete(); null }
    }

    private suspend fun systemRecognize(): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return null
        return withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<String?> { cont ->
                val sr = SpeechRecognizer.createSpeechRecognizer(this@VoiceAssistantActivity)
                cancelSystemRecognizer = { try { sr.destroy() } catch (_: Exception) {}; if (cont.isActive) cont.resume(null) }
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        sr.destroy(); if (cont.isActive) cont.resume(t)
                    }
                    override fun onError(error: Int) { sr.destroy(); if (cont.isActive) cont.resume(null) }
                    override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "en") "en-US" else "es-MX")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
                cont.invokeOnCancellation { try { sr.destroy() } catch (_: Exception) {} }
            }
        }.also { cancelSystemRecognizer = null }
    }

    /** Un chip de sugerencia hace de "frase dicha": corta la escucha y se usa su texto. */
    private fun submit(text: String) {
        if (phase != Phase.LISTENING) return
        chipText = text
        stopRequested = true
        cancelSystemRecognizer?.invoke()
    }

    // ------------------------------------------------------------------ hablar (voz del sistema)

    private suspend fun speak(text: String) {
        if (text.isBlank()) return
        phase = Phase.SPEAKING; reply = text; level = 0f
        if (!settings.speaksReplies()) { delay((900 + text.length * 35L).coerceAtMost(12_000)); return }
        val engine = ensureTts() ?: run { delay((900 + text.length * 35L).coerceAtMost(12_000)); return }
        withTimeoutOrNull((10_000L + text.length * 90L).coerceIn(25_000L, 90_000L)) {
            suspendCancellableCoroutine<Unit> { cont ->
                val id = "voice${System.nanoTime()}"
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { if (cont.isActive) cont.resume(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                })
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
                cont.invokeOnCancellation { engine.stop() }
            }
        }
    }

    private var ttsInit: CompletableDeferred<TextToSpeech?>? = null

    private suspend fun ensureTts(): TextToSpeech? {
        ttsInit?.let { return it.await() }
        val d = CompletableDeferred<TextToSpeech?>().also { ttsInit = it }
        tts = TextToSpeech(applicationContext) { st ->
            val t = tts
            if (st == TextToSpeech.SUCCESS && t != null) {
                t.language = Locale(lang)
                settings.getMode().let { m -> t.setSpeechRate(m.speechRate); t.setPitch(m.pitch) }
                d.complete(t)
            } else d.complete(null)
        }
        return d.await().also { tts = it }
    }

    // ------------------------------------------------------------------ utilidades

    private fun hasInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun finishWith(msg: String?) {
        lifecycleScope.launch {
            phase = Phase.DONE
            if (msg != null) { reply = msg; level = 0f; delay(2200) } else delay(120)
            finish()
        }
    }

    // ------------------------------------------------------------------ UI

    @Composable
    private fun VoiceOverlay() {
        val cs = MaterialTheme.colorScheme
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.15f), Color.Black.copy(0.65f))))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .background(Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.surface)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.35f)))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(aiName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { finish() }) { Icon(Icons.Filled.Close, stringResource(R.string.voice_close), tint = cs.onSurfaceVariant) }
                }

                // Lo que dijiste (arriba, pequeño) y lo que responde el asistente (grande)
                if (heard.isNotBlank()) {
                    Text("“$heard”", fontSize = 13.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = cs.onSurfaceVariant, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                }
                // Las respuestas largas ("cuéntame...") bajan de tamaño y se pueden desplazar.
                Box(Modifier.heightIn(max = if (draftRows.isNotEmpty()) 80.dp else 220.dp).verticalScroll(rememberScrollState())) {
                    AnimatedContent(targetState = reply, label = "reply") { r ->
                        val long = r.length > 140
                        Text(r, fontSize = if (long) 16.sp else 21.sp, lineHeight = if (long) 22.sp else 27.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = cs.onSurface)
                    }
                }

                if (draftRows.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.voice_draft_title, draftName), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.primary)
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 150.dp)
                            .clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer).verticalScroll(rememberScrollState())
                    ) {
                        draftRows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().clickable { row.checked = !row.checked }.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = row.checked, onCheckedChange = { row.checked = it })
                                Column(Modifier.weight(1f)) {
                                    Text(row.song.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
                                    Text(row.song.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { answerDraft(false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.voice_draft_discard)) }
                        Button(onClick = { answerDraft(true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.voice_draft_save)) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Orb()
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(when (phase) {
                        Phase.LISTENING -> R.string.voice_hint_listening
                        Phase.THINKING -> R.string.voice_thinking
                        Phase.SPEAKING -> R.string.voice_hint_speaking
                        Phase.DONE -> R.string.voice_thinking
                    }),
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )

                if (phase == Phase.LISTENING) {
                    val quick = listOf(
                        stringResource(R.string.voice_chip_my_playlists),
                        stringResource(R.string.voice_chip_surprise),
                        stringResource(R.string.voice_chip_next)
                    ) + chips.take(3)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.forEach { q ->
                            AssistChip(onClick = { submit(q) }, label = { Text(q, fontSize = 13.sp) })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    /** Esfera animada: gira con degradado, respira y reacciona al volumen de tu voz; barras de onda debajo. */
    @Composable
    private fun Orb() {
        val cs = MaterialTheme.colorScheme
        val t = rememberInfiniteTransition(label = "orb")
        val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "spin")
        val breathe by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
        val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "wave")
        val lv by animateFloatAsState(level, tween(90), label = "lv")
        val ph = phase
        val amp = when (ph) {
            Phase.LISTENING -> 0.12f + lv
            Phase.SPEAKING -> 0.35f + 0.25f * abs(sin(wave * 2f * PI.toFloat()))
            Phase.THINKING -> 0.18f
            Phase.DONE -> 0f
        }
        val c1 = cs.primary; val c2 = cs.tertiary; val c3 = cs.secondary

        Box(Modifier.size(150.dp).clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                when (ph) { Phase.LISTENING -> stopRequested = true; Phase.SPEAKING -> tts?.stop(); else -> {} }
            }, contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                for (i in 3 downTo 1) {
                    drawCircle(c1.copy(alpha = 0.09f * (4 - i)), radius = r * (0.62f + 0.11f * i + 0.22f * amp) * breathe)
                }
                rotate(if (ph == Phase.THINKING) spin * 3f else spin) {
                    drawCircle(Brush.sweepGradient(listOf(c1, c3, c2, c1)), radius = r * 0.56f * (1f + 0.10f * amp))
                }
                drawCircle(Color.Black.copy(alpha = 0.22f), radius = r * 0.46f)
            }
            Icon(
                when (ph) { Phase.LISTENING -> Icons.Filled.Mic; Phase.SPEAKING -> Icons.Filled.MusicNote; else -> Icons.Filled.GraphicEq },
                contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)
            )
        }
        Canvas(Modifier.fillMaxWidth().height(28.dp).padding(top = 6.dp)) {
            val n = 21
            val gap = 6.dp.toPx()
            val bw = ((size.width.coerceAtMost(260.dp.toPx())) - gap * (n - 1)) / n
            val startX = (size.width - (bw * n + gap * (n - 1))) / 2f
            for (i in 0 until n) {
                val shape = 0.35f + 0.65f * abs(sin((wave * 2f * PI.toFloat()) + i * 0.55f))
                val h = (size.height * (0.15f + 0.85f * (amp * shape).coerceIn(0f, 1f))).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    c1.copy(alpha = 0.85f), Offset(startX + i * (bw + gap), (size.height - h) / 2f),
                    Size(bw, h), CornerRadius(bw / 2f)
                )
            }
        }
    }
}
