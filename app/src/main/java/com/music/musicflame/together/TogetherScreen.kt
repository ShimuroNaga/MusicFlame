package com.music.musicflame.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.music.musicflame.data.Song
import com.music.musicflame.ui.components.AlbumArt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.music.musicflame.R

/**
 * Pantalla "En compañía". Se abre desde el botón junto a Configuración.
 * Envuelve todo en el color de TEXTO elegido en Ajustes > Apariencia > "Color de texto de En compañía"
 * (Adaptativo = el color de texto que ya usa la app, Personalizado o Arcoíris). Así el encabezado, los
 * botones y la barra del chat lo siguen sin tocar cada Text uno por uno.
 */
@Composable
fun TogetherScreen(
    manager: TogetherManager,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val repo = remember { com.music.musicflame.data.SettingsRepository(ctx) }
    val textColor by rememberTogetherTextColor(
        mode = remember { repo.getTogetherTextColorMode() },
        hex = remember { repo.getTogetherTextCustomColorHex() }
    )
    CompositionLocalProvider(
        LocalContentColor provides textColor,
        com.music.musicflame.ui.theme.LocalAppTextColor provides textColor
    ) {
        TogetherScreenContent(manager = manager, modifier = modifier)
    }
}

/** Color de texto de "En compañía". Adaptativo = color de texto actual de la app (LocalAppTextColor). */
@Composable
private fun rememberTogetherTextColor(mode: String, hex: String): State<Color> {
    val adaptive = com.music.musicflame.ui.theme.LocalAppTextColor.current
    val phase = if (mode == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW)
        com.music.musicflame.ui.theme.rememberRainbowPhase() else null
    return remember(mode, hex, adaptive, phase) {
        derivedStateOf {
            when {
                phase != null -> com.music.musicflame.ui.theme.rainbowColorAt(phase.value)
                mode == "Personalizado" -> com.music.musicflame.ui.theme.parseCustomTextColor(hex)
                else -> adaptive
            }
        }
    }
}

@Composable
private fun TogetherScreenContent(
    manager: TogetherManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var joinInput by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }
    // Chat de la sala: pantalla completa dentro de esta misma pantalla (estado en el manager para que MainActivity oculte sus barras).
    DisposableEffect(Unit) { onDispose { manager.chatScreenOpen = false } }
    val inRoom = manager.roomCode != null
    val connected = manager.status == TogetherManager.Status.CONNECTED
    val busy by manager.busy

    // Color de acento elegido en Ajustes > Apariencia > "Color de En compañía".
    // Se guarda como State y se LEE dentro de cada item/botón (o en la fase de dibujo con
    // drawBehind) para que, en modo Arcoíris, solo se recomponga lo mínimo y no toda la pantalla.
    val settingsRepo = remember { com.music.musicflame.data.SettingsRepository(context) }
    val accent = rememberTogetherAccent(
        mode = remember { settingsRepo.getTogetherColorMode() },
        hex = remember { settingsRepo.getTogetherCustomColorHex() }
    )

    // Si llegaste por un enlace musicflame://sala/CODIGO, entra solo.
    LaunchedEffect(manager.pendingDeepLinkCode) {
        manager.pendingDeepLinkCode?.let {
            manager.pendingDeepLinkCode = null
            manager.joinRoom(it)
        }
    }

    // Si llegaste desde la notificación del chat, abre el chat directo (solo si ya hay conexión).
    LaunchedEffect(manager.openChatRequested, connected, manager.status) {
        if (manager.openChatRequested) {
            if (connected) { manager.chatScreenOpen = true; manager.openChatRequested = false }
            else if (manager.status != TogetherManager.Status.CONNECTING) manager.openChatRequested = false
        }
    }
    LaunchedEffect(inRoom) { if (!inRoom) manager.chatScreenOpen = false }

    if (manager.chatScreenOpen && inRoom) {
        TogetherChatPanel(
            manager = manager,
            accent = accent,
            onBack = { manager.chatScreenOpen = false },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // ---- Cabecera con anillos que laten + explicación desplegable ----
        item {
            TogetherHero(
                accent = accent,
                showInfo = showInfo,
                onToggleInfo = { showInfo = !showInfo },
                infoText = stringResource(R.string.tg_info, TogetherManager.MAX_MEMBERS),
                // Botón "Chats" arriba a la derecha (solo dentro de una sala)
                chatUnread = manager.chatUnread,
                onOpenChat = if (inRoom) ({ manager.chatScreenOpen = true }) else null
            )
        }

        // ---- Nombre ----
        item {
            OutlinedTextField(
                value = manager.userName,
                onValueChange = { manager.changeUserName(it) },
                label = { Text(stringResource(R.string.tg_your_name)) },
                leadingIcon = { Icon(Icons.Filled.Person, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.value,
                    focusedLabelColor = accent.value,
                    cursorColor = accent.value
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!inRoom) {
            // ---- Crear ----
            item {
                AccentButton(
                    accent = accent,
                    onClick = { manager.createRoom() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tg_create_room), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.tg_or_join), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
            }
            // ---- Unirse ----
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), contentColor = LocalContentColor.current),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = joinInput,
                            onValueChange = { joinInput = it },
                            label = { Text(stringResource(R.string.tg_paste_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (text.isNotBlank()) joinInput = text
                                }) {
                                    Icon(Icons.Filled.ContentPaste, stringResource(R.string.tg_paste_clipboard), tint = accent.value)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent.value,
                                focusedLabelColor = accent.value,
                                cursorColor = accent.value
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AccentButton(
                            accent = accent,
                            onClick = { manager.joinRoom(joinInput) },
                            enabled = !busy && joinInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tg_join), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // ---- Sala actual: código en casillas + acciones rápidas ----
            item {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .drawBehind {
                            val c = accent.value
                            drawRect(Brush.verticalGradient(listOf(c.copy(alpha = 0.30f), c.copy(alpha = 0.07f))))
                        }
                        .border(1.dp, accent.value.copy(alpha = 0.35f), shape)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (manager.isHost) stringResource(R.string.tg_your_room_host) else stringResource(R.string.tg_room),
                        fontSize = 12.sp,
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                    RoomCodeChips(accent = accent, code = manager.roomCode ?: "")
                    var showQr by remember { mutableStateOf(false) }
                    if (showQr) {
                        RoomQrDialog(link = manager.roomLink.orEmpty(), code = manager.roomCode.orEmpty()) { showQr = false }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickAction(accent, Icons.Filled.ContentCopy, stringResource(R.string.tg_code), Modifier.weight(1f)) {
                            copy(context, context.getString(R.string.tg_code), manager.roomCode.orEmpty())
                        }
                        QuickAction(accent, Icons.Filled.Link, stringResource(R.string.tg_link), Modifier.weight(1f)) {
                            copy(context, context.getString(R.string.tg_link), manager.roomLink.orEmpty())
                        }
                        QuickAction(accent, Icons.Filled.QrCode2, stringResource(R.string.tg_qr), Modifier.weight(1f)) {
                            showQr = true
                        }
                        QuickAction(accent, Icons.Filled.Share, stringResource(R.string.tg_invite), Modifier.weight(1f)) {
                            share(context, manager)
                        }
                    }
                }
            }

            // ---- Estado ----
            item {
                val (label, fixedColor) = when (manager.status) {
                    TogetherManager.Status.CONNECTED -> stringResource(R.string.tg_connected) to null
                    TogetherManager.Status.CONNECTING -> stringResource(R.string.tg_connecting) to Color(0xFFF9A825)
                    TogetherManager.Status.DISCONNECTED -> stringResource(R.string.tg_disconnected) to Color(0xFFC62828)
                    else -> stringResource(R.string.tg_no_room) to Color.Gray
                }
                val colorProvider: () -> Color = { fixedColor ?: accent.value }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(label = label, color = colorProvider, pulsing = connected || manager.status == TogetherManager.Status.CONNECTING)
                    if (manager.statusDetail.isNotBlank()) {
                        Text(manager.statusDetail, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
                    }
                    if (connected && manager.onlineCount < 2) {
                        Text(stringResource(R.string.tg_waiting_more), fontSize = 12.sp,
                            color = LocalContentColor.current.copy(alpha = 0.7f))
                    }
                    manager.missingSongText?.let {
                        if (connected && !manager.isController) {
                            Text(
                                if (manager.transferText != null) stringResource(R.string.tg_missing_song, it)
                                else stringResource(R.string.tg_missing_song_notified, it),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    manager.transferText?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = accent.value)
                            Spacer(Modifier.width(8.dp))
                            Text(it, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ---- Ahora suena (carátula con "añadida por", ecualizador y saltar/votar) ----
            if (connected) {
                item {
                    val playingText = manager.nowPlayingText
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), contentColor = LocalContentColor.current),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CoverWithAddedBy(
                                    song = manager.nowPlayingSong(),
                                    addedBy = if (playingText != null) manager.nowAddedByName else null,
                                    size = 72.dp,
                                    labelSize = 10.sp
                                )
                                Column(Modifier.weight(1f)) {
                                    if (manager.isController) {
                                        Text(stringResource(R.string.tgq_you_control), fontWeight = FontWeight.Bold)
                                        Text(
                                            stringResource(R.string.tgq_you_control_hint),
                                            fontSize = 12.sp,
                                            color = LocalContentColor.current.copy(alpha = 0.7f)
                                        )
                                        if (playingText != null) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(playingText, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    } else {
                                        Text(stringResource(R.string.tg_now_playing), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
                                        Text(
                                            playingText ?: if (manager.isOwner) stringResource(R.string.tgq_pick_a_song) else stringResource(R.string.tg_waiting_host),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                EqualizerBars(color = { accent.value }, active = playingText != null)
                            }
                            // Saltar: el controlador salta solo; los demás propietarios votan.
                            if (manager.isOwner && playingText != null) {
                                if (manager.isController) {
                                    AccentOutlinedButton(accent, onClick = { manager.skipCurrent() }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tgq_skip_song))
                                    }
                                } else if (manager.iVotedSkip) {
                                    AccentButton(accent, onClick = { manager.toggleSkipVote() }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.tgq_vote_skip_done, manager.skipVotes, manager.skipNeeded))
                                    }
                                } else {
                                    AccentOutlinedButton(accent, onClick = { manager.toggleSkipVote() }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.tgq_vote_skip, manager.skipVotes, manager.skipNeeded))
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- Lista de espera ----
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.tgq_queue_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent.value)
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .drawBehind { drawRect(accent.value.copy(alpha = 0.2f)) }
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("${manager.queue.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent.value)
                        }
                    }
                }
                if (manager.queue.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.tgq_queue_empty),
                            fontSize = 12.sp,
                            color = LocalContentColor.current.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    item {
                        if (manager.isOwner && manager.queue.size > 1) {
                            Text(
                                stringResource(R.string.tgq_queue_hint_owner),
                                fontSize = 11.sp,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        QueueList(manager = manager, accent = accent)
                    }
                }
            }

            // ---- Botones de conexión ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (connected) {
                        AccentOutlinedButton(accent, onClick = { manager.disconnect() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.LinkOff, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tg_disconnect))
                        }
                    } else {
                        AccentButton(accent, onClick = { manager.connect() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tg_connect))
                        }
                    }
                    AccentOutlinedButton(accent, onClick = { manager.reconnect() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tg_reconnect))
                    }
                }
            }
            item {
                TextButton(onClick = { manager.leave() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (manager.isHost) stringResource(R.string.tg_close_room) else stringResource(R.string.tg_leave_room), color = MaterialTheme.colorScheme.error)
                }
            }

            // ---- Miembros ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tg_in_room), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent.value)
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .drawBehind { drawRect(accent.value.copy(alpha = 0.2f)) }
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.tg_n_connected, manager.onlineCount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent.value)
                    }
                }
            }
            items(manager.members, key = { it.uid }) { m ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), contentColor = LocalContentColor.current),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        MemberAvatar(accent = accent, name = m.name, online = m.online, avatar = m.avatar)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.name + (if (m.isHost) "  👑" else "") + (if (!m.isHost && manager.owners.any { it.uid == m.uid }) "  🎧" else ""),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    !m.online -> stringResource(R.string.tg_member_offline)
                                    m.isHost -> stringResource(R.string.tg_member_host_online)
                                    manager.owners.any { it.uid == m.uid } -> stringResource(R.string.tgq_member_owner_online)
                                    else -> stringResource(R.string.tg_member_online)
                                },
                                fontSize = 12.sp,
                                color = if (m.online) accent.value else LocalContentColor.current.copy(alpha = 0.7f)
                            )
                        }
                        // Solo el anfitrión administra propietarios (no sobre sí mismo).
                        if (manager.isHost && m.uid != manager.currentUid) {
                            var menuOpen by remember { mutableStateOf(false) }
                            val isOwnerMember = manager.owners.any { it.uid == m.uid }
                            var confirmKick by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, stringResource(R.string.tgq_member_menu), tint = accent.value)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (isOwnerMember) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.tgq_remove_owner)) },
                                            leadingIcon = { Icon(Icons.Filled.PersonRemove, null) },
                                            onClick = { menuOpen = false; manager.removeOwner(m.uid) }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.tgq_make_owner)) },
                                            leadingIcon = { Icon(Icons.Filled.PersonAdd, null) },
                                            onClick = { menuOpen = false; manager.addOwner(m.uid) }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tgq_make_host)) },
                                        leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) },
                                        onClick = { menuOpen = false; manager.transferHost(m.uid) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tgq_kick_member), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; confirmKick = true }
                                    )
                                }
                                if (confirmKick) {
                                    AlertDialog(
                                        onDismissRequest = { confirmKick = false },
                                        title = { Text(stringResource(R.string.tgq_kick_title, m.name)) },
                                        text = { Text(stringResource(R.string.tgq_kick_text)) },
                                        confirmButton = {
                                            TextButton(onClick = { confirmKick = false; manager.kickMember(m.uid) }) {
                                                Text(stringResource(R.string.tgq_kick_confirm))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { confirmKick = false }) { Text(stringResource(R.string.action_cancel)) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Piezas visuales
// ---------------------------------------------------------------------------------------------

/** Cabecera: ícono con anillos que laten, título, frase corta y "¿Cómo funciona?" desplegable. */
@Composable
private fun TogetherHero(
    accent: State<Color>,
    showInfo: Boolean,
    onToggleInfo: () -> Unit,
    infoText: String,
    chatUnread: Int = 0,
    onOpenChat: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(28.dp)
    val t = rememberInfiniteTransition(label = "heroRings")
    val ring1 by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1"
    )
    val ring2 by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart, initialStartOffset = StartOffset(1200)),
        label = "ring2"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                val c = accent.value
                drawRect(Brush.horizontalGradient(listOf(c.copy(alpha = 0.32f), c.copy(alpha = 0.06f))))
            }
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onOpenChat != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ChatsButton(accent = accent, unread = chatUnread, onClick = onOpenChat)
            }
        }
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBehind {
                    val c = accent.value
                    val maxR = size.minDimension / 2f
                    val coreR = 34.dp.toPx()
                    listOf(ring1, ring2).forEach { p ->
                        drawCircle(
                            color = c.copy(alpha = 0.35f * (1f - p)),
                            radius = coreR + (maxR - coreR) * p
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .drawBehind { drawRect(accent.value) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, null, tint = onAccent(accent.value), modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.tg_listen_together), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.tg_tagline),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
        TextButton(onClick = onToggleInfo, colors = ButtonDefaults.textButtonColors(contentColor = accent.value)) {
            Text(stringResource(R.string.tg_how_it_works), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(if (showInfo) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp))
        }
        if (showInfo) {
            Text(
                infoText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
    }
}

/** QR con el enlace https de la sala: se escanea con la cámara del teléfono y abre la página que lleva a la app. */
@Composable
private fun RoomQrDialog(link: String, code: String, onDismiss: () -> Unit) {
    val bmp = remember(link) { TogetherQr.bitmap(link) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tg_qr_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.tg_qr_title),
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text(stringResource(R.string.tg_qr_error), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(10.dp))
                Text(code, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.tg_qr_hint),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tg_got_it)) } }
    )
}

/** Código de la sala: una casilla por carácter, todas del mismo ancho para que quepan siempre. */
@Composable
private fun RoomCodeChips(accent: State<Color>, code: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        code.forEach { ch ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(shape)
                    .drawBehind { drawRect(accent.value.copy(alpha = 0.18f)) }
                    .border(1.dp, accent.value.copy(alpha = 0.5f), shape),
                contentAlignment = Alignment.Center
            ) {
                Text(ch.toString(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = accent.value)
            }
        }
    }
}

/** Acción rápida: ícono arriba y texto abajo, en una casilla redondeada con el color de acento. */
@Composable
private fun QuickAction(
    accent: State<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .drawBehind { drawRect(accent.value.copy(alpha = 0.14f)) }
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = accent.value, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent.value)
    }
}

/** Píldora de estado con punto que late mientras está conectado/conectando. */
@Composable
private fun StatusPill(label: String, color: () -> Color, pulsing: Boolean) {
    val t = rememberInfiniteTransition(label = "pill")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pillPulse"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .drawBehind { drawRect(color().copy(alpha = 0.16f)) }
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .drawBehind {
                    if (pulsing) {
                        drawCircle(color().copy(alpha = 0.4f * (1f - p)), radius = (size.minDimension / 2f) * (0.45f + 0.55f * p))
                    }
                    drawCircle(color(), radius = 5.dp.toPx())
                }
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.SemiBold, color = color())
    }
}

/** Ecualizador de 4 barras que se mueven (solo se anima la capa gráfica, no recompone). */
@Composable
private fun EqualizerBars(color: () -> Color, active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val durations = listOf(520, 740, 610, 880)
    val heights = durations.mapIndexed { i, d ->
        t.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar$i"
        )
    }
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = if (active) h.value else 0.2f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .drawBehind { drawRect(color()) }
            )
        }
    }
}

/**
 * Carátula de una canción con la etiqueta "por Fulano" (quién la añadió) sobre la parte baja.
 * Si esta canción no está en tu teléfono se muestra un cuadro con una nota musical.
 */
@Composable
private fun CoverWithAddedBy(
    song: Song?,
    addedBy: String?,
    size: Dp,
    labelSize: androidx.compose.ui.unit.TextUnit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(size).clip(shape)) {
        if (song != null) {
            AlbumArt(
                albumArtUri = song.albumArtUri,
                size = size,
                cornerRadius = 14.dp,
                filePath = song.path,
                isCustomCover = song.hasCustomCover
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!addedBy.isNullOrBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.tgq_added_by_short, addedBy),
                    color = Color.White,
                    fontSize = labelSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Lista de espera. Mantén pulsada una fila y arrástrala para cambiar el orden (solo propietarios).
 * La X quita lo que tú añadiste (el anfitrión puede quitar cualquiera).
 */
@Composable
private fun QueueList(manager: TogetherManager, accent: State<Color>) {
    val density = LocalDensity.current
    val rowHeight = 68.dp
    val gap = 6.dp
    val pitchPx = with(density) { (rowHeight + gap).toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    val canReorder = manager.isOwner
    val items = manager.queue.toList()

    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        items.forEachIndexed { index, item ->
            key(item.id) {
                val dragging = draggingId == item.id
                val canRemove = item.addedBy == manager.currentUid || manager.isHost
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dragging) 0.8f else 0.35f),
                        contentColor = LocalContentColor.current
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragDy else 0f
                            scaleX = if (dragging) 1.02f else 1f
                            scaleY = if (dragging) 1.02f else 1f
                        }
                        .then(
                            if (canReorder) Modifier.pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingId = item.id; dragDy = 0f },
                                    onDrag = { change, amount -> change.consume(); dragDy += amount.y },
                                    onDragEnd = {
                                        val from = manager.queue.indexOfFirst { it.id == item.id }
                                        val to = TogetherQueueLogic.dragTarget(from, dragDy, pitchPx, manager.queue.size)
                                        draggingId = null; dragDy = 0f
                                        if (from >= 0 && from != to) manager.moveQueueItem(from, to)
                                    },
                                    onDragCancel = { draggingId = null; dragDy = 0f }
                                )
                            } else Modifier
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${index + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accent.value,
                            modifier = Modifier.width(22.dp)
                        )
                        CoverWithAddedBy(
                            song = manager.localSongFor(item),
                            addedBy = item.addedByName,
                            size = 54.dp,
                            labelSize = 9.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.artist.isNotBlank()) {
                                Text(
                                    item.artist,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = LocalContentColor.current.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                stringResource(R.string.tgq_added_by, item.addedByName),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = accent.value
                            )
                        }
                        if (canRemove) {
                            IconButton(onClick = { manager.removeFromQueue(item) }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.tgq_remove_from_queue))
                            }
                        }
                        if (canReorder) {
                            Icon(
                                Icons.Filled.DragHandle, null,
                                tint = LocalContentColor.current.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Avatar circular: icono de la persona si lo puso; si no, la inicial. Gris si está desconectada. */
@Composable
internal fun MemberAvatar(accent: State<Color>, name: String, online: Boolean, avatar: String? = null, avatarSize: Dp = 42.dp) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    val image = remember(avatar) { TogetherAvatar.decode(avatar)?.asImageBitmap() }
    Box(
        modifier = Modifier
            .size(avatarSize)
            .clip(CircleShape)
            .drawBehind {
                drawRect(if (online) accent.value.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.22f))
            }
            .border(1.5.dp, if (online) accent.value else Color.Gray.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                initial,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (online) accent.value else Color.Gray
            )
        }
    }
}

/**
 * Color de acento de "En compañía". Devuelve un State: en Arcoíris cambia con el tiempo, así que
 * quien lo use debe leer `.value` lo más adentro posible (botón/item) para no recomponer todo.
 * "Adaptativo" = color primario del tema (idéntico al aspecto anterior).
 */
@Composable
private fun rememberTogetherAccent(mode: String, hex: String): State<Color> {
    val adaptive = MaterialTheme.colorScheme.primary
    val phase = if (mode == com.music.musicflame.ui.theme.COLOR_MODE_RAINBOW)
        com.music.musicflame.ui.theme.rememberRainbowPhase() else null
    return remember(mode, hex, adaptive, phase) {
        derivedStateOf {
            when {
                phase != null -> com.music.musicflame.ui.theme.rainbowColorAt(phase.value)
                mode == "Personalizado" -> com.music.musicflame.ui.theme.parseCustomTextColor(hex)
                else -> adaptive
            }
        }
    }
}

/** Texto/ícono legible sobre el color de acento (negro sobre claros, blanco sobre oscuros). */
internal fun onAccent(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

@Composable
private fun AccentButton(
    accent: State<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val c = accent.value
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c, contentColor = onAccent(c)),
        content = content
    )
}

@Composable
private fun AccentOutlinedButton(
    accent: State<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val c = accent.value
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c),
        border = BorderStroke(1.dp, c.copy(alpha = 0.6f)),
        content = content
    )
}

/**
 * Diálogo para el ANFITRIÓN: "a tu amigo le falta esta canción, ¿subirla?".
 * Se llama una sola vez desde MainActivity para que salga en cualquier pantalla.
 */
@Composable
fun TogetherUploadPrompt(manager: TogetherManager) {
    val p = manager.uploadPrompt ?: return
    val names = p.who.joinToString(", ")
    val mb = p.sizeBytes / 1_048_576.0
    val song = if (p.artist.isBlank()) p.title else "${p.title} – ${p.artist}"
    if (p.tooBig) {
        AlertDialog(
            onDismissRequest = { manager.dismissUploadPrompt() },
            title = { Text(stringResource(R.string.tg_too_big_title)) },
            text = {
                Text(stringResource(R.string.tg_too_big_text, names, song, "%.1f".format(mb)))
            },
            confirmButton = { TextButton(onClick = { manager.dismissUploadPrompt() }) { Text(stringResource(R.string.tg_got_it)) } }
        )
    } else {
        AlertDialog(
            onDismissRequest = { manager.dismissUploadPrompt() },
            title = { Text(if (p.who.size == 1) stringResource(R.string.tg_friend_missing_one) else stringResource(R.string.tg_friend_missing_many)) },
            text = {
                Text(stringResource(R.string.tg_upload_text, names, song, "%.1f".format(mb)))
            },
            confirmButton = { TextButton(onClick = { manager.confirmUpload() }) { Text(stringResource(R.string.tg_upload)) } },
            dismissButton = { TextButton(onClick = { manager.dismissUploadPrompt() }) { Text(stringResource(R.string.fp_not_now)) } }
        )
    }
}

private fun copy(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.tg_copied, label), Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, manager: TogetherManager) {
    val msg = context.getString(R.string.tg_share_message, manager.roomCode.orEmpty(), manager.roomLink.orEmpty())
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg) }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.tg_invite_to_room)))
}