package com.music.musicflame.together

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHAT_INPUT_MAX = 300

/** Colores para el nombre de cada persona (estable por uid), como en los chats de grupo. */
private val NameColors = listOf(
    Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
    Color(0xFFBA68C8), Color(0xFF4DD0E1), Color(0xFFF06292), Color(0xFFAED581)
)

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private fun dayKey(at: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = at }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

/**
 * Botón "Chats" (burbujas + texto) de la esquina superior derecha de la sala.
 * [unread] > 0 muestra un puntito de mensajes sin leer.
 */
@Composable
internal fun ChatsButton(accent: State<Color>, unread: Int, onClick: () -> Unit) {
    val unreadDesc = stringResource(R.string.tgc_unread_desc)
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, accent.value.copy(alpha = 0.5f), RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Forum, null, tint = accent.value, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.tgc_chats), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .semantics { contentDescription = unreadDesc }
            )
        }
    }
}

/**
 * Chat de la sala, a PANTALLA COMPLETA. MainActivity oculta su barra superior e inferior mientras
 * [TogetherManager.chatScreenOpen] es true, así que aquí se manejan los insets: la barra de arriba
 * cubre la barra de estado y la de abajo sube con el teclado.
 * Solo texto. Mantener presionado un mensaje: Responder / Copiar / Borrar / (propietarios) Expulsar del chat o Perdonar.
 * Responder también se hace deslizando el mensaje hacia la derecha (como WhatsApp).
 */
@Composable
internal fun TogetherChatPanel(
    manager: TogetherManager,
    accent: State<Color>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = manager.chat
    val scope = rememberCoroutineScope()

    // Mensaje al que se está respondiendo (null = mensaje normal) y el que se resalta al tocar una cita.
    var replyingTo by remember { mutableStateOf<TogetherManager.ChatMessage?>(null) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(replyingTo?.id) { if (replyingTo != null) runCatching { inputFocus.requestFocus() } }
    LaunchedEffect(highlightId) { if (highlightId != null) { delay(1200); highlightId = null } }
    val goneText = stringResource(R.string.tgc_reply_gone)
    fun jumpTo(id: String) {
        val i = messages.indexOfFirst { it.id == id }
        if (i < 0) { Toast.makeText(context, goneText, Toast.LENGTH_SHORT).show(); return }
        highlightId = id
        scope.launch { listState.animateScrollToItem(i) }
    }

    BackHandler(onBack = onBack)

    // Mientras el chat está abierto no se cuentan no leídos ni se manda notificación.
    DisposableEffect(Unit) {
        manager.setChatOpen(true)
        onDispose { manager.setChatOpen(false) }
    }

    // Teclado: misma solución que el chat de la IA (adjustResize sin pan; los insets se manejan aquí).
    DisposableEffect(Unit) {
        val window = context.findHostActivity()?.window
        val previous = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (previous != null) window?.setSoftInputMode(previous) }
    }

    // Primer vistazo: saltar directo al último mensaje; después, bajar solo si ya estabas abajo o el mensaje es tuyo.
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val last = messages.size - 1
        if (!didInitialScroll) {
            listState.scrollToItem(last); didInitialScroll = true
        } else {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible >= last - 2 || messages.last().uid == manager.currentUid) {
                listState.animateScrollToItem(last)
            }
        }
    }
    // Al salir el teclado, dejar visible el último mensaje.
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeOpen) {
        if (imeOpen && messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFormat = remember { android.text.format.DateFormat.getMediumDateFormat(context) }
    val todayLabel = stringResource(R.string.tgc_today)

    Column(modifier.fillMaxSize()) {
        ChatTopBar(manager = manager, accent = accent, onBack = onBack)

        // ---- Mensajes ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                ChatEmptyState(accent)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                        val prev = messages.getOrNull(index - 1)
                        Column(Modifier.fillMaxWidth()) {
                            if (prev == null || dayKey(prev.at) != dayKey(msg.at)) {
                                ChatDayChip(if (DateUtils.isToday(msg.at)) todayLabel else dateFormat.format(Date(msg.at)))
                            }
                            if (msg.isSystem) {
                                ChatSystemLine(msg)
                            } else {
                                val mine = msg.uid == manager.currentUid
                                val startsGroup = prev == null || prev.isSystem || prev.uid != msg.uid ||
                                    dayKey(prev.at) != dayKey(msg.at)
                                ChatBubbleRow(
                                    manager = manager,
                                    accent = accent,
                                    msg = msg,
                                    mine = mine,
                                    showHeader = startsGroup,
                                    time = timeFormat.format(Date(msg.at)),
                                    highlighted = highlightId == msg.id,
                                    canReply = !manager.iAmChatMuted,
                                    onReply = { replyingTo = msg },
                                    onJumpTo = { jumpTo(it) },
                                    onCopy = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("chat", msg.text))
                                        Toast.makeText(context, context.getString(R.string.tgc_copied), Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Caja de escribir (solo texto: sin imágenes, audios, "+" ni stickers) ----
        Column(
            Modifier
                .fillMaxWidth()
                .background(cs.surface.copy(alpha = 0.95f))
                // El teclado ya incluye la barra de navegación: se toma el mayor de los dos, no se suman.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            if (manager.iAmChatMuted) {
                Text(
                    stringResource(R.string.tgc_you_muted),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = cs.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                fun send() { if (manager.sendChat(draft, replyingTo)) { draft = ""; replyingTo = null } }
                val canSend = draft.isNotBlank() && manager.status == TogetherManager.Status.CONNECTED
                replyingTo?.let { r ->
                    ReplyPreviewBar(
                        name = if (r.uid == manager.currentUid) stringResource(R.string.tgc_you) else r.name,
                        text = r.text,
                        accent = accent,
                        onClose = { replyingTo = null }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it.take(CHAT_INPUT_MAX) },
                        placeholder = { Text(stringResource(R.string.tgc_hint)) },
                        maxLines = 4,
                        shape = RoundedCornerShape(26.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.55f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = accent.value
                        ),
                        modifier = Modifier.weight(1f).focusRequester(inputFocus)
                    )
                    val sendBg = accent.value.copy(alpha = if (canSend) 1f else 0.3f)
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(sendBg)
                            .clickable(enabled = canSend) { send() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.tgc_send),
                            tint = onAccent(accent.value).copy(alpha = if (canSend) 1f else 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (draft.length >= CHAT_INPUT_MAX - 60) {
                    Text(
                        "${draft.length}/$CHAT_INPUT_MAX",
                        fontSize = 11.sp,
                        color = if (draft.length >= CHAT_INPUT_MAX) cs.error else cs.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(end = 22.dp, bottom = 6.dp)
                    )
                }
            }
        }
    }
}

/** Barra de arriba propia del chat: regresar, iconos de quienes están en la sala, título y cuántos hay conectados. */
@Composable
private fun ChatTopBar(manager: TogetherManager, accent: State<Color>, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val online = manager.members.filter { it.online }
    Column(Modifier.fillMaxWidth().background(cs.surface.copy(alpha = 0.95f))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tgc_back))
            }
            val shown = online.take(3)
            if (shown.isNotEmpty()) {
                Box(Modifier.width((32 + 20 * (shown.size - 1)).dp).height(32.dp)) {
                    shown.forEachIndexed { i, m ->
                        Box(Modifier.padding(start = (i * 20).dp)) {
                            MemberAvatar(accent = accent, name = m.name, online = true, avatar = m.avatar, avatarSize = 32.dp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.tgc_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.tgc_members_online, online.size),
                    fontSize = 12.sp,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
        // Filete de color de acento bajo la barra.
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.value.copy(alpha = 0f), accent.value, accent.value.copy(alpha = 0f))
                    )
                )
        )
    }
}

@Composable
private fun ChatEmptyState(accent: State<Color>) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(cs.surface.copy(alpha = 0.92f))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(accent.value.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Forum, null, tint = accent.value, modifier = Modifier.size(28.dp))
            }
            Text(
                stringResource(R.string.tgc_empty),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = cs.onSurfaceVariant
            )
        }
    }
}

/** Separador de día ("Hoy" / fecha) en un chip legible sobre cualquier fondo. */
@Composable
private fun ChatDayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** Aviso discreto centrado: "X fue expulsado del chat" / "X fue perdonado…". */
@Composable
private fun ChatSystemLine(msg: TogetherManager.ChatMessage) {
    val text = if (msg.event == "unmute") stringResource(R.string.tgc_sys_unmuted, msg.text)
    else stringResource(R.string.tgc_sys_muted, msg.text)
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/** Un mensaje con su icono (como en WhatsApp) y el menú de mantener presionado. */
@Composable
private fun ChatBubbleRow(
    manager: TogetherManager,
    accent: State<Color>,
    msg: TogetherManager.ChatMessage,
    mine: Boolean,
    showHeader: Boolean,
    time: String,
    highlighted: Boolean,
    canReply: Boolean,
    onReply: () -> Unit,
    onJumpTo: (String) -> Unit,
    onCopy: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    // La esquina "de la cola" (junto al icono) es chica solo en el primer mensaje de cada grupo.
    val tail = if (showHeader) 4.dp else 18.dp
    val shape = RoundedCornerShape(
        topStart = if (mine) 18.dp else tail,
        topEnd = if (mine) tail else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
    // Icono: el de la sala si sigue ahí; si ya se fue, el último que se vio de esa persona.
    val avatar = manager.chatAvatars[msg.uid]
    val topPad = if (showHeader) 6.dp else 0.dp
    val bubbleColor = if (mine) accent.value else cs.surfaceContainerHigh.copy(alpha = 0.97f)
    val textColor = if (mine) onAccent(accent.value) else cs.onSurface
    val timeColor = textColor.copy(alpha = 0.65f)

    // Deslizar a la derecha para responder (como WhatsApp): la burbuja se desplaza con resistencia y,
    // al pasar el umbral, vibra y aparece el icono de responder; al soltar se manda a la caja de escribir.
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val maxDragPx = with(density) { 72.dp.toPx() }
    val triggerPx = with(density) { 56.dp.toPx() }
    var dragPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var passed by remember { mutableStateOf(false) }
    val shownDrag by animateFloatAsState(
        targetValue = dragPx,
        animationSpec = if (dragging) snap<Float>() else androidx.compose.animation.core.spring<Float>(),
        label = "chatSwipe"
    )
    val highlightAlpha by animateFloatAsState(if (highlighted) 0.28f else 0f, label = "chatHighlight")
    val replyMsgId = msg.replyId

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPad)
            .then(
                if (canReply) Modifier.pointerInput(msg.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; passed = false },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragPx = (dragPx + delta * 0.6f).coerceIn(0f, maxDragPx)
                            val nowPassed = dragPx >= triggerPx
                            if (nowPassed && !passed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            passed = nowPassed
                        },
                        onDragEnd = {
                            val fire = dragPx >= triggerPx
                            dragging = false; dragPx = 0f; passed = false
                            if (fire) onReply()
                        },
                        onDragCancel = { dragging = false; dragPx = 0f; passed = false }
                    )
                } else Modifier
            )
    ) {
        // Icono que aparece detrás al deslizar.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(32.dp)
                .alpha((shownDrag / triggerPx).coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(accent.value.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = accent.value,
                modifier = Modifier.size(18.dp)
            )
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(shownDrag.roundToInt(), 0) },
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!mine) {
            if (showHeader) {
                MemberAvatar(accent = accent, name = msg.name, online = true, avatar = avatar, avatarSize = 34.dp)
            } else {
                Spacer(Modifier.size(34.dp))
            }
            Spacer(Modifier.size(8.dp))
        }

        Box(modifier = Modifier.widthIn(max = 290.dp)) {
            Column(
                modifier = Modifier
                    .clip(shape)
                    .background(bubbleColor)
                    .background(textColor.copy(alpha = highlightAlpha))
                    .pointerInput(msg.id) {
                        detectTapGestures(onLongPress = { menuOpen = true })
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    // Con cita, la cita ocupa todo el ancho de la burbuja.
                    .then(if (replyMsgId != null) Modifier.width(IntrinsicSize.Max) else Modifier)
            ) {
                if (!mine && showHeader) {
                    Text(
                        msg.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NameColors[(msg.uid.hashCode() and 0x7fffffff) % NameColors.size],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (replyMsgId != null) {
                    val quotedMine = msg.replyUid == manager.currentUid
                    ReplyQuote(
                        name = if (quotedMine) stringResource(R.string.tgc_you) else (msg.replyName ?: ""),
                        text = msg.replyText ?: "",
                        barColor = if (mine) textColor.copy(alpha = 0.85f)
                        else NameColors[((msg.replyUid ?: "").hashCode() and 0x7fffffff) % NameColors.size],
                        textColor = textColor,
                        onClick = { onJumpTo(replyMsgId) }
                    )
                }
                Text(msg.text, fontSize = 15.sp, color = textColor)
                Text(
                    time,
                    fontSize = 10.sp,
                    color = timeColor,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canReply) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tgc_reply)) },
                        onClick = { menuOpen = false; onReply() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tgc_copy)) },
                    onClick = { menuOpen = false; onCopy() }
                )
                if (manager.canDeleteChat(msg)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tgc_delete)) },
                        onClick = { menuOpen = false; manager.deleteChat(msg) }
                    )
                }
                if (manager.canModerateChat(msg)) {
                    val muted = manager.isChatMuted(msg.uid)
                    DropdownMenuItem(
                        text = { Text(stringResource(if (muted) R.string.tgc_unmute else R.string.tgc_mute)) },
                        onClick = { menuOpen = false; manager.setChatMuted(msg, !muted) }
                    )
                }
            }
        }

        if (mine) {
            Spacer(Modifier.size(8.dp))
            if (showHeader) {
                MemberAvatar(accent = accent, name = msg.name, online = true, avatar = avatar, avatarSize = 34.dp)
            } else {
                Spacer(Modifier.size(34.dp))
            }
        }
    }
    }
}

/** Cita dentro de la burbuja: barra de color + autor + texto (2 líneas). Tocarla salta al mensaje original. */
@Composable
private fun ReplyQuote(name: String, text: String, barColor: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 2.dp, bottom = 5.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(barColor))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text,
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Barra sobre la caja de escribir: "Respondiendo a X" + el texto citado + botón para cancelar. */
@Composable
private fun ReplyPreviewBar(name: String, text: String, accent: State<Color>, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.55f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent.value))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.tgc_replying_to, name),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accent.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tgc_reply_cancel), modifier = Modifier.size(20.dp))
        }
    }
}
