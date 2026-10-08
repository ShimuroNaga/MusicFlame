package com.music.musicflame.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.AlbumArtShapeType
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
    albumArtShape: AlbumArtShapeType = AlbumArtShapeType.SQUARE,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {},
    // NUEVO: true cuando esta es la canción que está sonando ahora mismo.
    // Muestra el mismo icono de "sonando" (barritas) que ya usa QueueScreen,
    // a un lado del título.
    isCurrentlyPlaying: Boolean = false,
    // NUEVO: true = mismas medidas que la card de SongScreen (carátula 50dp,
    // separación 16dp, título 16sp en negrita). Lo usa MixScreen.
    matchSongScreenStyle: Boolean = false
) {
    val artSize = if (matchSongScreenStyle) 50.dp else 48.dp
    val textStart = if (matchSongScreenStyle) 16.dp else 12.dp
    val titleSize = if (matchSongScreenStyle) 16.sp else 15.sp
    val titleWeight = if (matchSongScreenStyle) FontWeight.Bold else FontWeight.Medium
    val artRadius = if (radius > 0.dp) 8.dp else 0.dp
    // <-- CAMBIO APLICADO: Lógica de color de fondo dependiente del tema
    // La card que suena se "activa" con el color del Now Playing (sin ícono animado).
    val npColors = if (isCurrentlyPlaying && !isSelected) nowPlayingCardColors() else null
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        npColors != null -> npColors.container
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
            defaultElevation = if (hasBackgroundImage || isSelected || npColors != null) 0.dp else 4.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Carátula siempre visible; al seleccionar se superpone un overlay + check (estilo unificado)
            Box(contentAlignment = Alignment.Center) {
                AlbumArt(albumArtUri = song.albumArtUri, size = artSize, cornerRadius = artRadius, shape = albumArtShape, filePath = song.path, isCustomCover = song.hasCustomCover)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(artSize)
                            .clip(RoundedCornerShape(artRadius))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Seleccionada",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = textStart)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // NUEVO: respeta el "Subtítulo de visualización" con códigos §
                    // (EditSongDialog); si la canción no tiene uno guardado, cae
                    // en el título real como antes.
                    FormatCodeText(
                        rawTitle = song.rawDisplayTitle(),
                        style = TextStyle(
                            fontWeight = titleWeight,
                            fontSize = titleSize,
                            // <-- APLICANDO EL COLOR GLOBAL (Mantiene el color de selección si está activa)
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else (npColors?.content ?: LocalAppTextColor.current)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    // <-- APLICANDO EL COLOR GLOBAL CON TRANSPARENCIA
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else (npColors?.content ?: LocalAppTextColor.current).copy(alpha = 0.7f),
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