package com.music.musicflame.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.data.LyricsParser
import com.music.musicflame.data.LyricsRepository
import com.music.musicflame.data.LyricsSource
import com.music.musicflame.data.ParsedLyrics
import com.music.musicflame.data.Song
import com.music.musicflame.ui.theme.parseCustomTextColor
import kotlinx.coroutines.launch

/** Resuelve el color de texto de la letra según lo elegido en Ajustes. */
@Composable
fun resolveLyricsTextColor(mode: String, customHex: String): Color = when (mode) {
    "Blanco" -> Color.White
    "Negro" -> Color.Black
    "Personalizado" -> parseCustomTextColor(customHex)
    else -> MaterialTheme.colorScheme.onSurface // Adaptativo (Material You)
}

/**
 * Vista de letras sincronizadas. Hace scroll automático a la línea activa
 * según `positionMs`, resaltándola y atenuando el resto. `speed` (0.5–2.0)
 * controla la duración de la animación entre líneas: valores bajos = lenta,
 * altos = rápida.
 */
@Composable
fun LyricsView(
    song: Song?,
    positionMs: Long,
    lyrics: ParsedLyrics,
    speed: Float,
    animationType: String,
    textColor: Color,
    isLoading: Boolean,
    searchFailed: Boolean,
    onSearchOnline: () -> Unit,
    onInsertManual: (String) -> Unit,
    onSearchYoutube: () -> Unit,
    onDeleteLyrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = lyrics.activeIndex(positionMs)
    var showInsertDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -200)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Column(
                    Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Buscando la letra...",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
            lyrics.lines.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (searchFailed) "No encontramos la letra de esta canción" else "Sin letra para esta canción",
                        color = textColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    if (searchFailed) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Puede que el título/artista no coincida exacto. Prueba insertándola tú a mano, o usa \"Verificar en YouTube\" y toca el video correcto: MusicFlame intentará traer la letra automáticamente.",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (searchFailed) {
                            Button(onClick = onSearchOnline) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.height(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reintentar")
                            }
                        } else {
                            Button(onClick = onSearchOnline) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.height(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Buscar")
                            }
                        }
                        TextButton(onClick = { showInsertDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Insertar")
                        }
                    }
                    if (searchFailed) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onSearchYoutube) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Verificar en YouTube")
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Se abre YouTube dentro de la app. Al tocar el video correcto, MusicFlame lee su título e intenta buscar la letra automáticamente. Si aun así no la encuentra, podrás insertarla tú con el botón \"Insertar\".",
                            color = textColor.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 120.dp, horizontal = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        val targetAlpha = if (!lyrics.isSynced) 1f else if (isActive) 1f else 0.35f
                        // "Rebote": la línea activa crece un poco más y con overshoot (spring).
                        val targetScale = if (lyrics.isSynced && isActive && animationType == "Rebote") 1.14f else 1f
                        // "Deslizar": la línea sube ligeramente al activarse; el resto descansa un poco más abajo.
                        val targetOffset = if (lyrics.isSynced && animationType == "Deslizar") {
                            if (isActive) 0.dp else 8.dp
                        } else 0.dp
                        val animDurationMs = (350 / speed.coerceIn(0.5f, 2f)).toInt().coerceIn(120, 700)

                        val alpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = animDurationMs),
                            label = "lyricAlpha"
                        )
                        val scale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = if (animationType == "Rebote") {
                                spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy)
                            } else {
                                tween(durationMillis = animDurationMs)
                            },
                            label = "lyricScale"
                        )
                        val offsetY by animateDpAsState(
                            targetValue = targetOffset,
                            animationSpec = tween(durationMillis = animDurationMs),
                            label = "lyricOffsetY"
                        )

                        Text(
                            text = line.text,
                            color = textColor.copy(alpha = alpha),
                            fontSize = if (isActive) 22.sp else 19.sp,
                            fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = offsetY)
                                .let { m ->
                                    if (animationType == "Rebote") m.scale(scale) else m
                                }
                        )
                    }
                }
            }
        }

        // Papelera: borra DEFINITIVAMENTE la letra de esta canción (y solo esta),
        // para cuando el usuario se equivocó de letra. Solo aparece si hay una
        // letra cargada (no tiene sentido "borrar" cuando ya está vacía).
        if (lyrics.lines.isNotEmpty()) {
            FilledTonalIconButton(
                onClick = { showDeleteConfirm = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar letra de esta canción")
            }
        }
    }

    if (showInsertDialog) {
        InsertLyricsDialog(
            initialText = "",
            onDismiss = { showInsertDialog = false },
            onConfirm = { text ->
                onInsertManual(text)
                showInsertDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Borrar letra", fontWeight = FontWeight.Black) },
            text = { Text("Esto borrará definitivamente la letra guardada de esta canción (solo de esta). ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteLyrics()
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertLyricsDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insertar letra", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(
                    "Pega la letra en formato LRC (con [mm:ss.xx]) para sincronizarla con la canción, o como texto plano si no tienes marcas de tiempo.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    placeholder = { Text("[00:12.50] Primera línea de la canción...") },
                    keyboardOptions = KeyboardOptions.Default
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Estado y lógica de carga de letras para una canción: lee lo guardado
 * localmente y, si no hay nada guardado, busca online AUTOMÁTICAMENTE (sin
 * que el usuario tenga que tocar nada). Si la búsqueda no encuentra nada,
 * `searchFailed` queda en true para que la UI ofrezca insertar a mano o
 * buscar la canción exacta en YouTube.
 */
@Composable
fun rememberLyricsState(song: Song?, repo: LyricsRepository): LyricsState {
    val scope = rememberCoroutineScope()
    var parsed by remember(song?.id) { mutableStateOf(ParsedLyrics.EMPTY) }
    var isLoading by remember(song?.id) { mutableStateOf(false) }
    var searchFailed by remember(song?.id) { mutableStateOf(false) }

    fun searchOnline() {
        val s = song ?: return
        isLoading = true
        searchFailed = false
        repo.clearChecked(s.id) // fuerza el reintento aunque el escaneo automático ya la haya revisado
        scope.launch {
            val result = repo.searchOnline(
                title = s.title,
                artist = s.artist,
                durationSeconds = (s.duration / 1000).toInt().takeIf { it > 0 }
            )
            val raw = result?.syncedLyrics?.takeIf { it.isNotBlank() } ?: result?.plainLyrics
            if (raw != null) {
                repo.saveLyrics(s.id, raw, LyricsSource.ONLINE)
                parsed = LyricsParser.parse(raw)
                searchFailed = false
            } else {
                repo.markChecked(s.id)
                searchFailed = true
            }
            isLoading = false
        }
    }

    // Carga lo guardado y, si no hay nada, dispara la búsqueda automática.
    LaunchedEffect(song?.id) {
        searchFailed = false
        if (song == null) {
            parsed = ParsedLyrics.EMPTY
            return@LaunchedEffect
        }
        val stored = repo.getLyrics(song.id)
        if (stored != null) {
            parsed = LyricsParser.parse(stored.raw)
        } else if (repo.isChecked(song.id)) {
            // El escaneo automático de la biblioteca ya revisó esta canción y
            // no encontró letra; evitamos repetir la misma búsqueda sin necesidad.
            parsed = ParsedLyrics.EMPTY
            searchFailed = true
        } else {
            parsed = ParsedLyrics.EMPTY
            searchOnline() // auto-búsqueda: el usuario no tiene que tocar nada
        }
    }

    fun insertManual(raw: String) {
        val s = song ?: return
        repo.saveLyrics(s.id, raw, LyricsSource.MANUAL)
        parsed = LyricsParser.parse(raw)
        searchFailed = false
    }

    /**
     * Borra definitivamente la letra de la canción ACTUAL (y solo esa), pensado
     * para el botón de papelera cuando el usuario se equivocó de letra. Deja el
     * estado como si nunca se hubiera buscado, para que se pueda reintentar.
     */
    fun deleteLyrics() {
        val s = song ?: return
        repo.clearLyrics(s.id)
        parsed = ParsedLyrics.EMPTY
        searchFailed = false
    }

    /**
     * Se llama cuando "Verificar en YouTube" extrae automáticamente el título
     * del video que el usuario tocó. Intenta encontrar la letra guiándose por
     * ese título (con la misma cascada: lrclib -> otras plataformas), sin que
     * el usuario tenga que insertar nada a mano si sí se encuentra.
     */
    fun searchFromYoutubeTitle(extractedTitle: String) {
        val s = song ?: return
        isLoading = true
        scope.launch {
            val result = repo.searchByFreeText(extractedTitle)
            val raw = result?.syncedLyrics?.takeIf { it.isNotBlank() } ?: result?.plainLyrics
            if (raw != null) {
                repo.saveLyrics(s.id, raw, LyricsSource.ONLINE)
                parsed = LyricsParser.parse(raw)
                searchFailed = false
            } else {
                // No se pudo extraer letra ni siquiera con el título confirmado en YouTube;
                // se deja la opción de insertar a mano, sin fingir que sí se encontró algo.
                searchFailed = true
            }
            isLoading = false
        }
    }

    return LyricsState(
        lyrics = parsed,
        isLoading = isLoading,
        searchFailed = searchFailed,
        onSearchOnline = ::searchOnline,
        onInsertManual = ::insertManual,
        onDeleteLyrics = ::deleteLyrics,
        onYoutubeTitleFound = ::searchFromYoutubeTitle
    )
}

data class LyricsState(
    val lyrics: ParsedLyrics,
    val isLoading: Boolean,
    val searchFailed: Boolean,
    val onSearchOnline: () -> Unit,
    val onInsertManual: (String) -> Unit,
    val onDeleteLyrics: () -> Unit = {},
    val onYoutubeTitleFound: (String) -> Unit = {}
)
