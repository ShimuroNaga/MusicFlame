package com.music.musicflame.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    val inRoom = manager.roomCode != null
    val connected = manager.status == TogetherManager.Status.CONNECTED
    val busy by manager.busy

    // Color de acento elegido en Ajustes > Apariencia > "Color de En compañía".
    // Se guarda como State y se LEE dentro de cada item/botón (no acá arriba) para que, en
    // modo Arcoíris, solo se recomponga lo que usa el color y no toda la pantalla.
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                "Escucha música a la vez con otras personas, juntas o a distancia. " +
                        "Cada quien reproduce la canción desde su propia biblioteca; MusicFlame sincroniza " +
                        "qué suena, play/pausa y el momento exacto. Si a alguien le falta una canción, el anfitrión " +
                        "puede subirla (máx. 25 MB) y se descarga sola; se borra al cerrar la sala. " +
                        "De 2 a ${TogetherManager.MAX_MEMBERS} personas.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Crear sala")
                }
            }
            // ---- Unirse ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = joinInput,
                        onValueChange = { joinInput = it },
                        label = { Text("Pega el código o el enlace") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent.value,
                            focusedLabelColor = accent.value,
                            cursorColor = accent.value
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    AccentButton(
                        accent = accent,
                        onClick = { manager.joinRoom(joinInput) },
                        enabled = !busy && joinInput.isNotBlank()
                    ) { Text("Unirse") }
                }
            }
            item {
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = accent.value), onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (text.isNotBlank()) joinInput = text
                }) {
                    Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pegar del portapapeles")
                }
            }
        } else {
            // ---- Sala actual ----
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = accent.value.copy(alpha = 0.14f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (manager.isHost) "Tu sala (anfitrión)" else "Sala", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(manager.roomCode ?: "", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = accent.value)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccentOutlinedButton(accent, onClick = { copy(context, "Código", manager.roomCode.orEmpty()) }) {
                                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Código")
                            }
                            AccentOutlinedButton(accent, onClick = { copy(context, "Enlace", manager.roomLink.orEmpty()) }) {
                                Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Enlace")
                            }
                        }
                        // "Invitar" va solo, en su propia fila debajo de Código y Enlace.
                        AccentOutlinedButton(accent, onClick = { share(context, manager) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Invitar")
                        }
                    }
                }
            }

            // ---- Estado ----
            item {
                val (label, color) = when (manager.status) {
                    TogetherManager.Status.CONNECTED -> "Conectado" to accent.value
                    TogetherManager.Status.CONNECTING -> "Conectando…" to Color(0xFFF9A825)
                    TogetherManager.Status.DISCONNECTED -> "Desconectado" to Color(0xFFC62828)
                    else -> "Sin sala" to Color.Gray
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontWeight = FontWeight.SemiBold, color = accent.value)
                    }
                    if (manager.statusDetail.isNotBlank()) {
                        Text(manager.statusDetail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (connected && manager.onlineCount < 2) {
                        Text("Esperando a más personas… (se necesitan al menos 2)", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (connected && !manager.isHost) {
                        Text(
                            manager.nowPlayingText?.let { "Suena: $it" } ?: "Esperando a que el anfitrión ponga música…",
                            fontSize = 13.sp
                        )
                        manager.missingSongText?.let {
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
                    if (connected && manager.isHost) {
                        Text("Lo que reproduzcas (canciones de tu biblioteca) lo escuchan todos.", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                TextButton(onClick = { manager.leave() }, enabled = !busy) {
                    Text(if (manager.isHost) "Cerrar sala" else "Salir de la sala", color = MaterialTheme.colorScheme.error)
                }
            }

            // ---- Miembros ----
            item {
                Text("En la sala (${manager.onlineCount} conectados)", fontWeight = FontWeight.SemiBold, color = accent.value)
            }
            items(manager.members, key = { it.uid }) { m ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(10.dp).clip(CircleShape)
                        .background(if (m.online) accent.value else Color.Gray))
                    Spacer(Modifier.width(10.dp))
                    Text(m.name + if (m.isHost) "  👑" else "", modifier = Modifier.weight(1f), color = accent.value)
                    if (!m.online) Text("desconectado", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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