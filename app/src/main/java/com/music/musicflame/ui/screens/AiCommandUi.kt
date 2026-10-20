package com.music.musicflame.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R
import com.music.musicflame.data.AiCommand
import com.music.musicflame.data.AiLibraryContext
import com.music.musicflame.data.Song

/**
 * Piezas de interfaz de los comandos del chat de IA ("/analiza", "/playlist"...): menú de comandos,
 * selector de canciones, confirmación de playlist y opciones del quiz.
 */

/** Lista de comandos que se abre con el botón "/" o al escribir "/" en la caja de texto. */
@Composable
internal fun CommandPanel(
    commands: List<AiCommand>, lang: String, online: Boolean, color: Color,
    onPick: (AiCommand) -> Unit, modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp), color = color,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.28f)),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            if (!online) Text(
                stringResource(R.string.ai_cmd_online_only), fontSize = 12.sp, color = scheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            commands.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(c) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .alpha(if (online) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) { Text("/", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = scheme.primary) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("/" + c.label(lang), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = scheme.onSurface)
                            c.argHintRes?.let {
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(it), fontSize = 13.sp, color = scheme.onSurfaceVariant.copy(alpha = 0.85f))
                            }
                        }
                        Text(stringResource(c.descRes), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Línea discreta cuando el chat está vacío (sustituye a las 3 sugerencias). */
@Composable
internal fun EmptyCommandHint(color: Color) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) { Text("/", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = scheme.primary) }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.ai_cmd_empty_hint), fontSize = 13.sp,
            color = scheme.onBackground.copy(alpha = 0.7f), textAlign = TextAlign.Center
        )
    }
}

/** Selector de hasta [max] canciones de la biblioteca (para /analiza y /opinion). */
@Composable
internal fun SongPickerDialog(songs: List<Song>, max: Int, onConfirm: (List<Song>) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }
    val sorted = remember(songs) { songs.sortedBy { it.title.lowercase() } }
    val byId = remember(songs) { songs.associateBy { it.id } }
    val keys = remember(sorted) { sorted.map { AiLibraryContext.normalize(it.title + " " + it.artist) } }
    val filtered = remember(sorted, keys, query) {
        val q = AiLibraryContext.normalize(query)
        if (q.isEmpty()) sorted else sorted.filterIndexed { i, _ -> keys[i].contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_cmd_picker_title, max)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.ai_cmd_picker_search)) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) Text(
                    stringResource(R.string.ai_cmd_picker_empty), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)
                ) else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        val on = song.id in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { if (on) selected.remove(song.id) else if (selected.size < max) selected.add(song.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    song.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(selected.mapNotNull { byId[it] }) }
            ) { Text(stringResource(R.string.ai_cmd_picker_confirm, selected.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_cmd_cancel)) } }
    )
}

/** Pregunta antes de meter la playlist armada por la IA en las playlists del usuario. */
@Composable
internal fun PlaylistConfirmCard(color: Color, onSave: () -> Unit, onDiscard: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp), color = color,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.ai_cmd_playlist_ask), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(stringResource(R.string.ai_cmd_playlist_save)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.ai_cmd_playlist_discard)) }
            }
        }
    }
}

/** Las 4 opciones del /quiz como botones; al tocar una se responde en el chat. */
@Composable
internal fun QuizOptionsCard(options: List<String>, color: Color, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(0.92f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, text ->
            Surface(
                shape = RoundedCornerShape(18.dp), color = color,
                border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onPick(i) }
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) { Text(('A' + i).toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = scheme.primary) }
                    Spacer(Modifier.width(10.dp))
                    Text(text, fontSize = 14.sp, color = scheme.onSurface)
                }
            }
        }
    }
}
