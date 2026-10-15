package com.music.musicflame.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import com.music.musicflame.data.AiChatSettings
import com.music.musicflame.data.AiMessage
import com.music.musicflame.data.LocalLlm
import com.music.musicflame.ui.theme.parseCustomTextColor
import com.music.musicflame.ui.theme.rainbowColorAt
import com.music.musicflame.ui.theme.rememberRainbowPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sexta pantalla: chat con la IA (panel superior con el orbe, burbujas con esquina recta del lado de quien habla,
 * botón de copiar dentro de la burbuja de la IA y caja de texto en píldora sobre una franja).
 * La respuesta aparece en streaming (trozo a trozo). Colores de burbuja / nombre salen de Ajustes > IA y se refrescan en vivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    nowPlayingLabel: String?,
    isActive: Boolean = true,
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
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Carga el modelo en segundo plano al abrir la pestaña: el primer mensaje ya no espera la carga.
    LaunchedEffect(isActive) { if (isActive) LocalLlm.preload(context) }

    // "···" solo mientras no haya llegado ningún trozo de la respuesta
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
    val rainbowPhase = if (bubbleMode == AiChatSettings.MODE_RAINBOW)
        rememberRainbowPhase(AiChatSettings.BUBBLE_RAINBOW_CYCLE_SECONDS) else null
    val scheme = MaterialTheme.colorScheme
    val customColor = remember(bubbleHex) { parseCustomTextColor(bubbleHex) }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || isThinking) return
        if (!termsAccepted) return
        input = ""
        messages.add(AiMessage(System.nanoTime(), true, text))
        settings.saveHistory(messages.toList())
        if (!LocalLlm.isReady(context)) {
            messages.add(AiMessage(System.nanoTime(), false, context.getString(R.string.ai_no_model_message), isError = true))
            return
        }
        isThinking = true
        val replyId = System.nanoTime()
        fun upsert(text: String, error: Boolean = false) {
            val i = messages.indexOfFirst { it.id == replyId }
            if (i < 0) messages.add(AiMessage(replyId, false, text, isError = error))
            else messages[i] = messages[i].copy(text = text, isError = error)
        }
        scope.launch {
            try {
                val answer = LocalLlm.reply(context, name, nowPlayingLabel, messages.toList()) { partial ->
                    // callback desde hilo de fondo -> pasar al hilo principal, en orden
                    scope.launch(Dispatchers.Main.immediate) { upsert(partial) }
                }
                upsert(answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {   // Throwable: un modelo local puede fallar por falta de RAM (OutOfMemoryError)
                val reason = e.message?.take(140) ?: e.javaClass.simpleName
                if (messages.none { it.id == replyId }) upsert(context.getString(R.string.ai_error_message, reason), error = true)
            } finally {
                isThinking = false
                settings.saveHistory(messages.toList())
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

    Box(modifier = modifier.fillMaxSize().background(scheme.surface)) {
        val isEmpty = messages.isEmpty() && !isThinking

        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // ---------- Panel superior: orbe de la IA centrado (como la versión anterior) ----------
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(scheme.surfaceContainer)
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AiAvatar(size = 84, glow = true, pulsing = isThinking)
                    AnimatedVisibility(visible = isThinking) {
                        Text(
                            stringResource(R.string.ai_typing), fontSize = 12.sp, color = scheme.primary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, null, tint = scheme.onSurfaceVariant) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_clear_chat)) },
                            onClick = { menuOpen = false; messages.clear(); settings.clearHistory() }
                        )
                    }
                }
            }

            // ---------- Mensajes ----------
            if (isEmpty) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.ai_tagline), fontSize = 15.sp, textAlign = TextAlign.Center,
                    color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp)
                )
            } else LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        msg = msg, mode = bubbleMode, customColor = customColor, rainbowPhase = rainbowPhase,
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
                            msg = AiMessage(-1L, false, "···", timeMs = 0L), mode = bubbleMode,
                            customColor = customColor, rainbowPhase = rainbowPhase, onCopy = null
                        )
                    }
                }
            }

            // ---------- Franja inferior: caja de texto en píldora + botón enviar circular ----------
            Surface(color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                if (!termsAccepted) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ai_terms_required), fontSize = 13.sp, modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant)
                        Button(onClick = { showTermsAgain = true }) { Text(stringResource(R.string.ai_terms_view)) }
                    }
                } else Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.ai_input_hint), maxLines = 1, fontSize = 17.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp),
                        shape = RoundedCornerShape(50),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            unfocusedBorderColor = scheme.onSurface.copy(alpha = 0.85f), focusedBorderColor = scheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() })
                    )
                    Spacer(Modifier.width(12.dp))
                    val canSend = input.isNotBlank() && !isThinking
                    FilledIconButton(
                        onClick = { send() }, enabled = canSend,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = scheme.primary, contentColor = scheme.onPrimary,
                            disabledContainerColor = scheme.surfaceContainerHighest, disabledContentColor = scheme.onSurfaceVariant
                        )
                    ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ai_send)) }
                }
            }
        }
    }
}

/** Orbe de la IA: destellos dentro de un aro con degradado y un brillo suave detrás (colores del tema Material You). */
@Composable
private fun AiAvatar(size: Int, glow: Boolean = false, pulsing: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 1f, targetValue = if (pulsing) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orbPulse"
    )
    Box(contentAlignment = Alignment.Center) {
        if (glow) Box(
            Modifier.size((size * 1.9f * pulse).dp).background(
                Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.28f), Color.Transparent)), CircleShape
            )
        )
        Box(
            modifier = Modifier
                .size(size.dp)
                .border(5.dp, Brush.sweepGradient(listOf(scheme.tertiary, scheme.primary, scheme.secondary, scheme.tertiary)), CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = scheme.onSurface, modifier = Modifier.size((size * 0.46f).dp))
        }
    }
}

@Composable
private fun ChatBubble(
    msg: AiMessage,
    mode: String,
    customColor: Color,
    rainbowPhase: State<Float>?,
    onCopy: (() -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    val isUser = msg.fromUser
    // "Colita" tipo WhatsApp: la esquina superior del lado de quien habla es casi recta.
    // Esquina inferior del lado de quien habla casi recta (como la versión anterior).
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .animateContentSize()
                .drawBehind {
                    val c = if (staticColor != null) bubbleBase else {
                        val phase = rainbowPhase?.value ?: 0f
                        val base = rainbowColorAt(phase, spreadFraction = if (isUser) 0f else 0.5f)
                        if (isUser) base else base.copy(alpha = 0.30f).compositeOver(scheme.surfaceContainerHigh)
                    }
                    drawRect(c)
                }
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Column {
                Text(msg.text, color = if (staticColor == null && !isUser) scheme.onSurface else textColor, fontSize = 17.sp, lineHeight = 24.sp)
                if (!isUser && onCopy != null) {
                    Icon(
                        Icons.Outlined.ContentCopy, stringResource(R.string.ai_copy),
                        tint = (if (staticColor == null) scheme.onSurface else textColor).copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End).padding(top = 14.dp).size(22.dp).clickable { onCopy() }
                    )
                }
            }
        }
    }
}
