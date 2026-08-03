package com.music.musicflame.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.musicflame.data.AlbumRepository
import com.music.musicflame.data.Song

/**
 * Pantalla de Gemini EN PAUSA. Antes usaba Firebase AI Logic para el chat;
 * se quitó junto con todo lo demás de Firebase. Se deja este placeholder con
 * la MISMA firma que antes para no tener que tocar MainActivity ni las
 * pantallas que le mandan un "initialPrompt" (SongScreen, PlaylistDetailScreen,
 * FullScreenPlayer, etc.).
 *
 * Cuando se decida cómo reemplazar el chat (API key directa, backend propio,
 * u otra cosa), esta función es el punto de entrada a reescribir.
 */
@Composable
fun GeminiScreen(
    modifier: Modifier = Modifier,
    messages: MutableList<AlbumRepository> = mutableListOf(),
    currentSong: Song? = null,
    initialPrompt: String = "",
    hasBackgroundImage: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                "Próximamente",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "El chat con IA está en pausa mientras se decide cómo reemplazarlo.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
