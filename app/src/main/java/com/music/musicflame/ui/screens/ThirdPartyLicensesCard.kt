package com.music.musicflame.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R

/** Una librería de terceros: nombre, licencia y enlaces al proyecto y al texto de la licencia. */
private data class ThirdPartyLib(
    val name: String,
    val license: String,
    val projectUrl: String,
    val licenseUrl: String
)

private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

private val thirdPartyLibs = listOf(
    ThirdPartyLib("JAudioTagger (Adonai fork)", "LGPL 2.1+",
        "https://github.com/Adonai/jaudiotagger",
        "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"),
    ThirdPartyLib("android-youtube-player", "MIT",
        "https://github.com/PierfrancescoSoffritti/android-youtube-player",
        "https://github.com/PierfrancescoSoffritti/android-youtube-player/blob/master/LICENSE"),
    ThirdPartyLib("AndroidX (Core, Lifecycle, Activity, DataStore, Security, ExifInterface, SplashScreen)", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx", APACHE_URL),
    ThirdPartyLib("Jetpack Compose y Material 3", "Apache-2.0",
        "https://developer.android.com/jetpack/compose", APACHE_URL),
    ThirdPartyLib("AndroidX Media3 (ExoPlayer, Session, UI)", "Apache-2.0",
        "https://github.com/androidx/media", APACHE_URL),
    ThirdPartyLib("Coil (compose, gif)", "Apache-2.0",
        "https://github.com/coil-kt/coil", APACHE_URL),
    ThirdPartyLib("Retrofit", "Apache-2.0",
        "https://github.com/square/retrofit", APACHE_URL),
    ThirdPartyLib("Accompanist Permissions", "Apache-2.0",
        "https://github.com/google/accompanist", APACHE_URL),
    ThirdPartyLib("Kotlin Coroutines", "Apache-2.0",
        "https://github.com/Kotlin/kotlinx.coroutines", APACHE_URL),
    ThirdPartyLib("Google API Client / Drive API", "Apache-2.0",
        "https://github.com/googleapis/google-api-java-client", APACHE_URL),
    ThirdPartyLib("Firebase (Auth, Realtime Database, Analytics)", "Apache-2.0",
        "https://github.com/firebase/firebase-android-sdk", APACHE_URL),
    ThirdPartyLib("MediaPipe Tasks GenAI (IA local)", "Apache-2.0",
        "https://github.com/google-ai-edge/mediapipe", APACHE_URL),
    ThirdPartyLib("LiteRT-LM (IA local)", "Apache-2.0",
        "https://github.com/google-ai-edge/LiteRT-LM", APACHE_URL),
    ThirdPartyLib("Qwen2.5-1.5B-Instruct (Alibaba Cloud / Qwen team, modelo de IA local; conversión litert-community)", "Apache-2.0",
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct", APACHE_URL),
    ThirdPartyLib("Qwen3-0.6B (Alibaba Cloud / Qwen team, modelo de IA local; conversión litert-community)", "Apache-2.0",
        "https://huggingface.co/Qwen/Qwen3-0.6B", APACHE_URL),
    ThirdPartyLib("Gemma 4 E2B (Google DeepMind, modelo de IA local; conversión litert-community)", "Apache-2.0",
        "https://huggingface.co/google/gemma-4-E2B-it", APACHE_URL),
    ThirdPartyLib("Gemma 3 1B IT (Google, modelo de IA local). Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms", "Gemma Terms of Use",
        "https://huggingface.co/google/gemma-3-1b-it", "https://ai.google.dev/gemma/terms")
)

/** Contenido de Ajustes > Licencias de terceros. */
@Composable
fun ThirdPartyLicensesCard() {
    val uriHandler = LocalUriHandler.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.tpl_intro), fontSize = 13.sp, color = onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        thirdPartyLibs.forEach { lib ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                    Text(lib.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = onSurface)
                    Text(stringResource(R.string.tpl_license_label, lib.license), fontSize = 12.sp, color = onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { runCatching { uriHandler.openUri(lib.projectUrl) } }) {
                            Text(stringResource(R.string.tpl_open_source), fontSize = 12.sp)
                        }
                        TextButton(onClick = { runCatching { uriHandler.openUri(lib.licenseUrl) } }) {
                            Text(stringResource(R.string.tpl_open_license), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.tpl_lgpl_title),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.tpl_lgpl_body),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.tpl_google_note), fontSize = 11.sp, color = onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
