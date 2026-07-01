package com.music.musicflame.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Clase de datos para el historial de chat en la UI
data class ChatMessage(val role: String, val text: String)

class GeminiRepository(
    private val systemPrompt: String,
    modelName: String // viene de RemoteConfigManager.getModelName(), ej. "gemini-2.5-flash"
) {

    // Inicialización mediante el SDK nuevo: Firebase AI Logic (antes "Vertex AI in Firebase")
    // Usamos el backend de Vertex AI, que es el reemplazo directo de lo que ya usabas.
    private val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = modelName, // ahora viene de Remote Config, no está fijo en el código
            systemInstruction = content { text(systemPrompt) }
        )

    // Gestión nativa del historial de conversación
    private val chat = generativeModel.startChat()

    suspend fun sendMessage(prompt: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = chat.sendMessage(prompt)
            response.text ?: "Sin respuesta"
        } catch (e: Exception) {
            "Error de conexión: ${e.localizedMessage}"
        }
    }

    // Analizar canción individual
    suspend fun analyzeSong(song: Song): String {
        return sendMessage("Analiza esta canción: ${song.title} de ${song.artist}. Dame el género y curiosidades.")
    }

    // Analizar playlist completa
    suspend fun analyzePlaylist(playlist: Playlist, songs: List<Song>): String {
        val songList = songs.take(10).joinToString("\n") { "- ${it.title} de ${it.artist}" }
        val prompt = "Analiza esta playlist: \"${playlist.name}\". Canciones: \n$songList\nDame un análisis del estilo y recomendaciones."
        return sendMessage(prompt)
    }
}