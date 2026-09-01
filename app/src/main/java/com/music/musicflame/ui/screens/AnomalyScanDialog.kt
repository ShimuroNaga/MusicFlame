package com.music.musicflame.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.musicflame.data.AnomalyRecord
import com.music.musicflame.data.AnomalyRepository
import com.music.musicflame.data.AnomalyScanner
import com.music.musicflame.data.AnomalyType
import com.music.musicflame.data.MusicPlayerManager
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongCustomizationRepository
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.ui.components.AlbumArt
import com.music.musicflame.ui.theme.LocalAppTextColor
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Diálogo pantalla completa de "Búsqueda de anomalías" (Ajustes > Canciones
 * > Manejo de Canciones). Se abre vacío o mostrando el último análisis
 * guardado (AnomalyRepository) — NUNCA escanea solo al abrirse. El único
 * disparador del escaneo es el botón "Buscar anomalías" / "Volver a
 * analizar", tal como pide la feature: activación 100% manual, nada
 * automático ni en segundo plano.
 */
@Composable
fun AnomalyScanDialog(
    settingsRepo: SettingsRepository,
    playerManager: MusicPlayerManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AnomalyRepository(context) }
    val customizationRepo = remember { SongCustomizationRepository(context) }
    val textColor = LocalAppTextColor.current

    var scanning by remember { mutableStateOf(false) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var hasScannedOnce by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<AnomalyRecord>>(emptyList()) }

    var editingSong by remember { mutableStateOf<Song?>(null) }
    var viewingRecord by remember { mutableStateOf<AnomalyRecord?>(null) }

    // Al abrir el diálogo, solo MUESTRA el último análisis guardado (si hay
    // uno) — no dispara ningún escaneo por su cuenta.
    LaunchedEffect(Unit) {
        results = repository.getAll().values.filter { it.visibleTypes.isNotEmpty() }
        hasScannedOnce = results.isNotEmpty()
    }

    fun runScan() {
        scope.launch {
            scanning = true
            val songs = SongLibraryHolder.songs
            scannedCount = 0
            totalCount = songs.size
            repository.pruneMissing(songs.map { it.path }.toSet())
            val fresh = AnomalyScanner.scan(
                songs = songs,
                repository = repository,
                onProgress = { done, total ->
                    scannedCount = done
                    totalCount = total
                }
            )
            repository.saveAll(fresh)
            results = fresh.filter { it.visibleTypes.isNotEmpty() }
            hasScannedOnce = true
            scanning = false
        }
    }

    fun ignore(record: AnomalyRecord, type: AnomalyType) {
        repository.ignore(record.path, type)
        results = results
            .map { if (it.path == record.path) it.copy(ignoredTypes = it.ignoredTypes + type) else it }
            .filter { it.visibleTypes.isNotEmpty() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = textColor)
                        }
                        Text("Búsqueda de anomalías", fontSize = 20.sp, fontWeight = FontWeight.Black, color = textColor)
                    }
                    if (hasScannedOnce && !scanning) {
                        IconButton(onClick = { runScan() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Volver a analizar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                when {
                    scanning -> ScanningProgress(scannedCount, totalCount, textColor)
                    results.isEmpty() -> EmptyState(
                        hasScannedOnce = hasScannedOnce,
                        textColor = textColor,
                        onScanClick = { runScan() }
                    )
                    else -> ResultsList(
                        results = results,
                        textColor = textColor,
                        onEdit = { record ->
                            editingSong = SongLibraryHolder.songs.find { it.id == record.songId }
                        },
                        onView = { record -> viewingRecord = record },
                        onIgnore = { record, type -> ignore(record, type) }
                    )
                }
            }
        }
    }

    // --- Acción rápida "Editar metadata": reusa el mismo diálogo de edición
    // que ya usa la selección múltiple de canciones (EditSongDialog.kt). ---
    editingSong?.let { song ->
        EditSongDialog(
            selectedSongs = listOf(song),
            customizationRepo = customizationRepo,
            settingsRepo = settingsRepo,
            onDismiss = { editingSong = null },
            onSaved = {
                editingSong = null
                scope.launch { SongLibraryHolder.refresh(context) }
                Toast.makeText(context, "Metadata actualizada. Vuelve a analizar para reflejar el cambio en la lista.", Toast.LENGTH_LONG).show()
            }
        )
    }

    // --- Acción rápida "Ver archivo" ---
    viewingRecord?.let { record ->
        FileInfoDialog(
            record = record,
            onDismiss = { viewingRecord = null },
            onPlay = {
                val song = SongLibraryHolder.songs.find { it.id == record.songId }
                if (song != null) {
                    playerManager.playSong(song, SongLibraryHolder.songs)
                    viewingRecord = null
                } else {
                    Toast.makeText(context, "La canción ya no está en tu biblioteca", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun ScanningProgress(scanned: Int, total: Int, textColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val progress = if (total > 0) scanned.toFloat() / total.toFloat() else 0f
        Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Analizando ${scanned}/${total}", fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "${(progress * 100).toInt()}%",
            color = textColor.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun EmptyState(hasScannedOnce: Boolean, textColor: Color, onScanClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.BugReport, contentDescription = null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (hasScannedOnce) "No se encontraron problemas en tu biblioteca 🎉" else "Analiza tu biblioteca en busca de carátulas corruptas, metadata sospechosa, formatos sin soporte, canciones truncadas y posibles duplicados.",
            color = textColor.copy(alpha = 0.8f),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(
            onClick = onScanClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (hasScannedOnce) "Volver a analizar" else "Buscar anomalías", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultsList(
    results: List<AnomalyRecord>,
    textColor: Color,
    onEdit: (AnomalyRecord) -> Unit,
    onView: (AnomalyRecord) -> Unit,
    onIgnore: (AnomalyRecord, AnomalyType) -> Unit
) {
    // Agrupado por tipo de problema (no plano): una canción con varios
    // problemas a la vez aparece en cada grupo que le corresponde.
    val grouped: Map<AnomalyType, List<AnomalyRecord>> = AnomalyType.entries.associateWith { type ->
        results.filter { type in it.visibleTypes }.sortedBy { it.title.lowercase() }
    }.filterValues { it.isNotEmpty() }

    val totalProblems = grouped.values.sumOf { it.size }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text(
                "$totalProblems problema(s) en ${results.size} canción(es)",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        grouped.forEach { (type, records) ->
            item {
                Text(
                    "${type.label} (${records.size})",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
            items(records, key = { "${type.name}_${it.path}" }) { record ->
                AnomalyRow(
                    record = record,
                    textColor = textColor,
                    onEdit = { onEdit(record) },
                    onView = { onView(record) },
                    onIgnore = { onIgnore(record, type) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AnomalyRow(
    record: AnomalyRecord,
    textColor: Color,
    onEdit: () -> Unit,
    onView: () -> Unit,
    onIgnore: () -> Unit
) {
    val song = remember(record.path) { SongLibraryHolder.songs.find { it.id == record.songId } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(
                    albumArtUri = song?.albumArtUri,
                    size = 40.dp,
                    cornerRadius = 8.dp,
                    filePath = record.path,
                    isCustomCover = song?.hasCustomCover ?: false
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.title.ifBlank { "(sin título)" }, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(record.artist.ifBlank { "(sin artista)" }, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionButton(text = "Editar metadata", icon = Icons.Filled.Edit, onClick = onEdit, modifier = Modifier.weight(1f))
                QuickActionButton(text = "Ver archivo", icon = Icons.Filled.FolderOpen, onClick = onView, modifier = Modifier.weight(1f))
                QuickActionButton(text = "Ignorar", icon = Icons.Filled.VisibilityOff, onClick = onIgnore, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickActionButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, maxLines = 1)
    }
}

/** "Ver archivo": info del archivo físico + problemas detectados, sin salir de la app. */
@Composable
private fun FileInfoDialog(record: AnomalyRecord, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.title.ifBlank { "(sin título)" }, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(record.artist.ifBlank { "(sin artista)" }, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text("Ruta del archivo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(record.path, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Tamaño: ${formatFileSize(record.fileSize)}", fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("Problemas detectados", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                record.visibleTypes.forEach { type ->
                    Text("• ${type.label}", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reproducir")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ruta de archivo", record.path))
                    Toast.makeText(context, "Ruta copiada", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copiar ruta")
                }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "desconocido"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
