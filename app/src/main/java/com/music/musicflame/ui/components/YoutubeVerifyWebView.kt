package com.music.musicflame.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Pantalla dentro de la app (no navegador externo) que muestra los resultados
 * de búsqueda de YouTube para la canción actual. En cuanto el usuario toca un
 * video, se lee el título real de esa página (el mismo texto que ya se ve en
 * pantalla) y se manda a [onTitleExtracted] para que la app intente buscar la
 * letra automáticamente con ese título, sin que el usuario tenga que copiar
 * ni escribir nada a mano.
 *
 * No se descarga ni se toca la letra desde aquí: solo se lee el título de la
 * página que YouTube ya muestra.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeVerifyWebView(
    query: String,
    onTitleExtracted: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var lastExtractedRaw by remember { mutableStateOf<String?>(null) }
    // NUEVO: antes, si la carga fallaba (sin internet en ese momento, DNS, lo
    // que sea), no pasaba nada visible — la pantalla se quedaba con la barra
    // de carga trabada indefinidamente, sin ningún mensaje ni forma de saber
    // qué pasó. Ahora se muestra un error real y se loguea la causa.
    var loadErrorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Toca el video correcto",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "MusicFlame intentará buscar la letra automáticamente",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (loadErrorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "No se pudo cargar YouTube: $loadErrorMessage. Revisá tu conexión y volvé a intentar.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString +
                                " MusicFlame/LyricsVerify"

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                loadErrorMessage = null
                            }

                            // NUEVO: ver comentario de loadErrorMessage más arriba. Solo nos
                            // interesa el error del frame principal (request.isForMainFrame);
                            // ignorar los de recursos sueltos (analytics, ads, fonts, etc. que
                            // YouTube carga aparte) evita mostrar un error falso cuando la
                            // página principal en realidad sí cargó bien.
                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    val description = error?.description?.toString() ?: "error desconocido"
                                    loadErrorMessage = description
                                    android.util.Log.e("YOUTUBE_VERIFY_DEBUG", "Falló la carga de YouTube: $description (url=${request.url})")
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                if (view == null || url == null) return
                                // Solo nos interesa cuando el usuario ya entró a ver un video.
                                if (url.contains("/watch")) {
                                    view.evaluateJavascript("(function(){return document.title;})();") { rawResult ->
                                        val decoded = decodeJsStringResult(rawResult)
                                        if (!decoded.isNullOrBlank() && decoded != lastExtractedRaw) {
                                            lastExtractedRaw = decoded
                                            val cleanedTitle = decoded
                                                .replace(Regex("(?i)\\s*-\\s*YouTube\\s*$"), "")
                                                .trim()
                                            if (cleanedTitle.isNotBlank()) {
                                                onTitleExtracted(cleanedTitle)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        loadUrl("https://m.youtube.com/results?search_query=${android.net.Uri.encode(query)}")
                    }
                }
            )
        }
    }
}

/**
 * evaluateJavascript devuelve el resultado como un string JSON-encoded
 * (ej. "\"Cancion - Artista - YouTube\""). Esto le quita las comillas
 * externas y des-escapa lo básico para dejar el texto plano real.
 */
private fun decodeJsStringResult(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    var s = raw.trim()
    if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
        s = s.substring(1, s.length - 1)
    }
    return s
        .replace("\\u003C", "<")
        .replace("\\u003E", ">")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}