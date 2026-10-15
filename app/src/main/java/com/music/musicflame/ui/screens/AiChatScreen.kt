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
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
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
import com.music.musicflame.data.AiMessage
import com.music.musicflame.data.AiLibraryContext
import com.music.musicflame.data.LocalLlm
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
    var termsDismissed by remember { mutableStateOf(false) }   // "Ahora no": no insistir hasta reabrir la app
    var showTermsAgain by remember { mutableStateOf(false) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in AiChatSettings.OBSERVED_KEYS) {
                name = settings.getName(); bubbleMode = settings.getBubbleMode(); bubbleHex = settings.getBubbleHex()
                termsAccepted = settings.isTermsAccepted()
            }
        }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val messages = remember { mutableStateListOf<AiMessage>().apply { addAll(settings.loadHistory()) } }
    var input by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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
    LaunchedEffect(isActive) {
        if (isActive) {
            stats = withContext(Dispatchers.IO) { StatsRepository(context).getAllStats() }
            LocalLlm.preload(context)
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

    fun send(override: String? = null) {
        val text = (override ?: input).trim()
        if (text.isEmpty() || isThinking) return
        if (!termsAccepted) return
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
                fallbackText = lib.fallbackText
                if (lib.picks.isNotEmpty()) upsert("", songIds = lib.picks.map { it.id })

                if (!LocalLlm.isReady(context)) {
                    val hint = context.getString(R.string.ai_no_model_message)
                    if (lib.picks.isNotEmpty()) upsert(listOfNotNull(lib.fallbackText, hint).joinToString("\n\n"))
                    else upsert(hint, error = true)
                    return@launch
                }

                // 2) El modelo redacta el comentario con esos datos.
                val answer = LocalLlm.reply(context, name, nowPlayingLabel, messages.toList(), lib.promptBlock, lang) { partial ->
                    // callback desde hilo de fondo: se limita a ~22 fps y se pasa al hilo principal
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUiUpdate.get() >= 45L) {
                        lastUiUpdate.set(now)
                        scope.launch(Dispatchers.Main.immediate) { upsert(partial) }
                    }
                }
                upsert(answer)   // texto final completo, siempre
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {   // Throwable: un modelo local puede fallar por falta de RAM (OutOfMemoryError)
                val existing = messages.firstOrNull { it.id == replyId }
                if (existing == null || existing.text.isBlank()) {
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

    if (isActive && !termsAccepted && !termsDismissed) {
        AiTermsDialog(
            onAccept = { settings.saveTermsAccepted(true); termsAccepted = true },
            onDismiss = { termsDismissed = true }
        )
    }
    if (showTermsAgain) AiTermsDialog(
        onAccept = { settings.saveTermsAccepted(true); termsAccepted = true; showTermsAgain = false },
        onDismiss = { showTermsAgain = false }
    )

    // Colores de superficie según haya o no imagen de fondo en la app.
    val panelColor = if (hasBackgroundImage) scheme.surfaceContainer.copy(alpha = 0.80f) else scheme.primaryContainer
    val onPanel = if (hasBackgroundImage) scheme.onSurface else scheme.onPrimaryContainer
    val pillColor = if (hasBackgroundImage) scheme.surfaceContainer.copy(alpha = 0.88f) else scheme.surfaceContainerHigh

    val phrase = when {
        !nowPlayingLabel.isNullOrBlank() -> stringResource(R.string.ai_listening, nowPlayingLabel.orEmpty())
        else -> stringResource(R.string.ai_tagline)
    }

    Box(modifier = modifier.fillMaxSize().background(if (hasBackgroundImage) Color.Transparent else scheme.background)) {
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
                        onClear = { messages.clear(); settings.clearHistory() }
                    )

                    // ---------- Mensajes ----------
                    if (isEmpty) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (termsAccepted && !compact) SuggestionList(
                            items = listOf(
                                stringResource(R.string.ai_suggest_1),
                                stringResource(R.string.ai_suggest_2),
                                stringResource(R.string.ai_suggest_3)
                            ),
                            color = pillColor,
                            onPick = { send(it) }
                        )
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
                                songs = picks, stats = stats, waiting = isThinking && msg.id == messages.lastOrNull()?.id && !msg.fromUser,
                                onPlaySong = { song -> onPlaySong(song, picks) },
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("chat", msg.text))
                                    Toast.makeText(context, context.getString(R.string.ai_copied), Toast.LENGTH_SHORT).show()
                                }
                            )
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
                    if (!termsAccepted) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(26.dp),
                                color = pillColor,
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
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(sendColor)
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
 * Tarjeta superior. Normal: orbe centrado con nombre y frase debajo (como el boceto).
 * Compacta (poco espacio, p. ej. teclado abierto): orbe pequeño a la izquierda y texto a su lado.
 */
@Composable
private fun AiHeader(
    name: String, phrase: String, thinking: Boolean, compact: Boolean,
    panelColor: Color, onPanel: Color, onClear: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(28.dp), color = panelColor,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).animateContentSize()
    ) {
        Box {
            if (compact) Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 52.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AiAvatar(size = 40.dp, thinking = thinking)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = onPanel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Crossfade(targetState = phrase, label = "phrase") {
                        Text(it, fontSize = 13.sp, color = onPanel.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AiAvatar(size = 76.dp, thinking = thinking)
                Spacer(Modifier.height(10.dp))
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onPanel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Crossfade(targetState = phrase, label = "phrase") {
                    Text(
                        it, fontSize = 14.sp, color = onPanel.copy(alpha = 0.78f), textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
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

/** Atajos de conversación cuando el chat está vacío. */
@Composable
private fun SuggestionList(items: List<String>, color: Color, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEach { text ->
            Surface(
                shape = RoundedCornerShape(20.dp), color = color,
                border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onPick(text) }
            ) {
                Text(
                    text, fontSize = 14.sp, color = scheme.onSurface, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
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
                    val c = if (staticColor != null) bubbleBase else {
                        val phase = rainbowPhase?.value ?: 0f
                        val base = rainbowColorAt(phase, spreadFraction = if (isUser) 0f else 0.5f)
                        if (isUser) base else base.copy(alpha = 0.30f).compositeOver(scheme.surfaceContainerHigh)
                    }
                    drawRect(c)
                }
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
