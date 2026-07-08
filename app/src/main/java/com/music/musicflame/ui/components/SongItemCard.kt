package com.music.musicflame.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.data.Song
import com.music.musicflame.ui.theme.LocalAppTextColor // <-- IMPORT AÑADIDO

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItemCard(
    song: Song,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    hasBackgroundImage: Boolean = false,
    radius: Dp = 12.dp,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    // <-- CAMBIO APLICADO: Lógica de color de fondo dependiente del tema
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        hasBackgroundImage -> {
            if (MaterialTheme.colorScheme.surface.red > 0.5f) Color.White.copy(alpha = 0.8f)
            else Color.Black.copy(alpha = 0.5f)
        }
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onClick() },
                onLongClick = { onToggleSelection() }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(radius),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasBackgroundImage || isSelected) 0.dp else 4.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de selección o carátula
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(if (radius > 0.dp) 8.dp else 0.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, "Seleccionada", tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                AlbumArt(albumArtUri = song.albumArtUri, size = 48.dp, cornerRadius = if (radius > 0.dp) 8.dp else 0.dp)
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // <-- APLICANDO EL COLOR GLOBAL (Mantiene el color de selección si está activa)
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else LocalAppTextColor.current
                )
                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    // <-- APLICANDO EL COLOR GLOBAL CON TRANSPARENCIA
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else LocalAppTextColor.current.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onDelete != null && !isSelectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}