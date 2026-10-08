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
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pantalla "En compañía". Se abre desde el botón junto a Configuración. */
@Composable
fun TogetherScreen(
    manager: TogetherManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var joinInput by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }
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
                infoText = "Cada quien reproduce la canción desde su propia biblioteca; MusicFlame sincroniza " +
                        "qué suena, play/pausa y el momento exacto. Si a alguien le falta una canción, el anfitrión " +
                        "puede subirla (máx. 25 MB) y se descarga sola; se borra al cerrar la sala. " +
                        "De 2 a ${TogetherManager.MAX_MEMBERS} personas."
            )
        }

        // ---- Nombre ----
        item {
            OutlinedTextField(
                value = manager.userName,
                onValueChange = { manager.changeUserName(it) },
                label = { Text("Tu nombre en la sala") },
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
                    Text("Crear sala", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text("o únete a una", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
            }
            // ---- Unirse ----
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = joinInput,
                            onValueChange = { joinInput = it },
                            label = { Text("Pega el código o el enlace") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (text.isNotBlank()) joinInput = text
                                }) {
                                    Icon(Icons.Filled.ContentPaste, "Pegar del portapapeles", tint = accent.value)
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
                            Text("Unirse", fontWeight = FontWeight.Bold)
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
                        if (manager.isHost) "Tu sala · eres el anfitrión 👑" else "Sala",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RoomCodeChips(accent = accent, code = manager.roomCode ?: "")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickAction(accent, Icons.Filled.ContentCopy, "Código", Modifier.weight(1f)) {
                            copy(context, "Código", manager.roomCode.orEmpty())
                        }
                        QuickAction(accent, Icons.Filled.Link, "Enlace", Modifier.weight(1f)) {
                            copy(context, "Enlace", manager.roomLink.orEmpty())
                        }
                        QuickAction(accent, Icons.Filled.Share, "Invitar", Modifier.weight(1f)) {
                            share(context, manager)
                        }
                    }
                }
            }

            // ---- Estado ----
            item {
                val (label, fixedColor) = when (manager.status) {
                    TogetherManager.Status.CONNECTED -> "Conectado" to null
                    TogetherManager.Status.CONNECTING -> "Conectando…" to Color(0xFFF9A825)
                    TogetherManager.Status.DISCONNECTED -> "Desconectado" to Color(0xFFC62828)
                    else -> "Sin sala" to Color.Gray
                }
                val colorProvider: () -> Color = { fixedColor ?: accent.value }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(label = label, color = colorProvider, pulsing = connected || manager.status == TogetherManager.Status.CONNECTING)
                    if (manager.statusDetail.isNotBlank()) {
                        Text(manager.statusDetail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (connected && manager.onlineCount < 2) {
                        Text("Esperando a más personas… (se necesitan al menos 2)", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    manager.missingSongText?.let {
                        if (connected && !manager.isHost) {
                            Text(
                                if (manager.transferText != null) "No la tienes en tu biblioteca: $it"
                                else "No la tienes en tu biblioteca: $it. Se le avisó al anfitrión para que la suba.",
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

            // ---- Ahora suena (con ecualizador animado) ----
            if (connected) {
                item {
                    val playingText = if (manager.isHost) null else manager.nowPlayingText
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            EqualizerBars(color = { accent.value }, active = playingText != null || manager.isHost)
                            Column(Modifier.weight(1f)) {
                                if (manager.isHost) {
                                    Text("Tú eres el DJ", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Lo que reproduzcas (canciones de tu biblioteca) lo escuchan todos.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text("Ahora suena", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        playingText ?: "Esperando a que el anfitrión ponga música…",
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Botones de conexión ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (connected) {
                        AccentOutlinedButton(accent, onClick = { manager.disconnect() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.LinkOff, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Desconectar")
                        }
                    } else {
                        AccentButton(accent, onClick = { manager.connect() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Conectar")
                        }
                    }
                    AccentOutlinedButton(accent, onClick = { manager.reconnect() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Reconectar")
                    }
                }
            }
            item {
                TextButton(onClick = { manager.leave() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (manager.isHost) "Cerrar sala" else "Salir de la sala", color = MaterialTheme.colorScheme.error)
                }
            }

            // ---- Miembros ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("En la sala", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent.value)
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .drawBehind { drawRect(accent.value.copy(alpha = 0.2f)) }
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("${manager.onlineCount} conectados", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent.value)
                    }
                }
            }
            items(manager.members, key = { it.uid }) { m ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        MemberAvatar(accent = accent, name = m.name, online = m.online)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.name + if (m.isHost) "  👑" else "",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    !m.online -> "desconectado"
                                    m.isHost -> "anfitrión · en línea"
                                    else -> "en línea"
                                },
                                fontSize = 12.sp,
                                color = if (m.online) accent.value else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
    infoText: String
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
        Text("Escucha juntos", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            "La misma música, al mismo tiempo. Juntos o a distancia.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onToggleInfo, colors = ButtonDefaults.textButtonColors(contentColor = accent.value)) {
            Text("¿Cómo funciona?", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(if (showInfo) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp))
        }
        if (showInfo) {
            Text(
                infoText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

/** Avatar circular con la inicial del nombre; apagado (gris) si la persona está desconectada. */
@Composable
private fun MemberAvatar(accent: State<Color>, name: String, online: Boolean, avatarSize: Dp = 42.dp) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
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
        Text(
            initial,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = if (online) accent.value else Color.Gray
        )
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
private fun onAccent(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

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
            title = { Text("Canción muy pesada") },
            text = {
                Text("$names no tiene «$song», pero pesa ${"%.1f".format(mb)} MB y el límite para compartir es 25 MB.")
            },
            confirmButton = { TextButton(onClick = { manager.dismissUploadPrompt() }) { Text("Entendido") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = { manager.dismissUploadPrompt() },
            title = { Text(if (p.who.size == 1) "A tu amigo le falta esta canción" else "A tus amigos les falta esta canción") },
            text = {
                Text("$names no tiene «$song» (${"%.1f".format(mb)} MB). ¿Subirla para que la escuche contigo? Se borra al cerrar la sala.")
            },
            confirmButton = { TextButton(onClick = { manager.confirmUpload() }) { Text("Subir") } },
            dismissButton = { TextButton(onClick = { manager.dismissUploadPrompt() }) { Text("Ahora no") } }
        )
    }
}

private fun copy(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copiado", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, manager: TogetherManager) {
    val msg = "Escucha música conmigo en MusicFlame 🔥\nCódigo: ${manager.roomCode}\nEnlace: ${manager.roomLink}"
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg) }
    context.startActivity(Intent.createChooser(send, "Invitar a la sala"))
}