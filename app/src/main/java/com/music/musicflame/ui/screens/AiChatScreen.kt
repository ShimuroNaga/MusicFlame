package com.music.musicflame.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.music.musicflame.R
import com.music.musicflame.data.AiChatSettings
import com.music.musicflame.data.AiMessage
import com.music.musicflame.data.LocalLlm
import com.music.musicflame.ui.theme.parseCustomTextColor
import com.music.musicflame.ui.theme.rainbowColorAt
import com.music.musicflame.ui.theme.rememberRainbowPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sexta pantalla: chat con la IA, estilo WhatsApp (encabezado con avatar + nombre + frase, burbujas con
 * "colita", hora dentro de la burbuja, fondo de chat opcional y caja de texto en píldora).
 * Colores de burbuja / nombre / fondo salen de Ajustes > IA y se refrescan en vivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    nowPlayingLabel: String?,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { AiChatSettings(context) }
    val scope = rememberCoroutineScope()

    // --- Ajustes en vivo: Ajustes > IA escribe en SharedPreferences y esto se entera solo ---
    var name by remember { mutableStateOf(settings.getName()) }
    var bubbleMode by remember { mutableStateOf(settings.getBubbleMode()) }
    var bubbleHex by remember { mutableStateOf(settings.getBubbleHex()) }
    var bgUri by remember { mutableStateOf(settings.getBackgroundUri()) }
    var bgDim by remember { mutableFloatStateOf(settings.getBackgroundDim()) }
    var termsAccepted by remember { mutableStateOf(settings.isTermsAccepted()) }
    var termsDismissed by remember { mutableStateOf(false) }   // "Ahora no": no insistir hasta reabrir la app
    var showTermsAgain by remember { mutableStateOf(false) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in AiChatSettings.OBSERVED_KEYS) {
                name = settings.getName(); bubbleMode = settings.getBubbleMode(); bubbleHex = settings.getBubbleHex()
                bgUri = settings.getBackgroundUri(); bgDim = settings.getBackgroundDim()
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

    LaunchedEffect(messages.size, isThinking) {
        val extra = if (isThinking) 1 else 0
        val last = messages.size + extra - 1
        if (last >= 0) listState.animateScrollToItem(last)
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
        scope.launch {
            try {
                val answer = LocalLlm.reply(context, name, nowPlayingLabel, messages.toList())
                messages.add(AiMessage(System.nanoTime(), false, answer))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {   // Throwable: un modelo local puede fallar por falta de RAM (OutOfMemoryError)
                val reason = e.message?.take(140) ?: e.javaClass.simpleName
                messages.add(AiMessage(System.nanoTime(), false, context.getString(R.string.ai_error_message, reason), isError = true))
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
        // Fondo del chat (opcional) + oscurecido para que las burbujas siempre se lean
        if (bgUri != null) {
            AsyncImage(
                model = bgUri, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgDim)))
        }
        val isEmpty = messages.isEmpty() && !isThinking

        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // ---------- Encabezado tipo WhatsApp: avatar + nombre + frase ----------
            if (!isEmpty) Surface(color = scheme.surfaceContainer.copy(alpha = if (bgUri != null) 0.88f else 1f), tonalElevation = 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AiAvatar(size = 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                        Text(
                            if (isThinking) stringResource(R.string.ai_typing) else stringResource(R.string.ai_tagline),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isThinking) scheme.primary else scheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, null, tint = scheme.onSurfaceVariant) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_clear_chat)) },
                                onClick = { menuOpen = false; messages.clear(); settings.clearHistory() }
                            )
                        }
                    }
                }
            }

            // ---------- Estado vacío: avatar grande + nombre + frase, centrado ----------
            if (isEmpty) Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, null, tint = scheme.onSurfaceVariant) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_clear_chat)) },
                            onClick = { menuOpen = false; messages.clear(); settings.clearHistory() }
                        )
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AiAvatar(size = 112)
                    Spacer(Modifier.height(16.dp))
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.ai_tagline), fontSize = 14.sp, textAlign = TextAlign.Center, color = scheme.onSurfaceVariant)
                }
            }

            // ---------- Mensajes ----------
            if (!isEmpty) LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                if (isThinking) {
                    item(key = "typing") {
                        ChatBubble(
                            msg = AiMessage(-1L, false, "···", timeMs = 0L), mode = bubbleMode,
                            customColor = customColor, rainbowPhase = rainbowPhase, onCopy = null
                        )
                    }
                }
            }

            // ---------- Sin aceptar términos: no se puede escribir ----------
            if (!termsAccepted) {
                Surface(color = scheme.surfaceContainer.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ai_terms_required), fontSize = 13.sp, modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant)
                        Button(onClick = { showTermsAgain = true }) { Text(stringResource(R.string.ai_terms_view)) }
                    }
                }
            } else
            // ---------- Caja de texto en píldora + botón enviar circular ----------
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ai_input_hint), maxLines = 1) },
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = scheme.surfaceContainer.copy(alpha = 0.92f),
                        unfocusedContainerColor = scheme.surfaceContainer.copy(alpha = 0.92f),
                        unfocusedBorderColor = scheme.outline, focusedBorderColor = scheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
                Spacer(Modifier.width(8.dp))
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

/** Avatar de la IA: destellos dentro de un aro con degradado (colores del tema Material You). */
@Composable
private fun AiAvatar(size: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size.dp)
            .border(2.5.dp, Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary)), CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = scheme.onSurface, modifier = Modifier.size((size * 0.5f).dp))
    }
}

private val timeFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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
    val shape: Shape = RoundedCornerShape(
        topStart = if (isUser) 18.dp else 4.dp, topEnd = if (isUser) 4.dp else 18.dp,
        bottomStart = 18.dp, bottomEnd = 18.dp
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
                .widthIn(max = 300.dp)
                .clip(shape)
                .drawBehind {
                    val c = if (staticColor != null) bubbleBase else {
                        val phase = rainbowPhase?.value ?: 0f
                        val base = rainbowColorAt(phase, spreadFraction = if (isUser) 0f else 0.5f)
                        if (isUser) base else base.copy(alpha = 0.30f).compositeOver(scheme.surfaceContainerHigh)
                    }
                    drawRect(c)
                }
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
        ) {
            Column {
                Text(msg.text, color = if (staticColor == null && !isUser) scheme.onSurface else textColor, fontSize = 15.sp, lineHeight = 20.sp)
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (msg.timeMs > 0L) {
                        Text(
                            timeFormat.format(Date(msg.timeMs)), fontSize = 10.sp,
                            color = (if (staticColor == null && !isUser) scheme.onSurface else textColor).copy(alpha = 0.65f)
                        )
                    }
                    if (!isUser && onCopy != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.ContentCopy, stringResource(R.string.ai_copy),
                            tint = (if (staticColor == null) scheme.onSurface else textColor).copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp).clickable { onCopy() }
                        )
                    }
                }
            }
        }
    }
}
