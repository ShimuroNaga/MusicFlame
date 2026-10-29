package com.music.musicflame.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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

/** Una librería, fuente o tecnología de terceros: nombre, licencia y enlaces al proyecto y al texto de la licencia. */
private data class ThirdPartyLib(
    val name: String,
    val license: String,
    val projectUrl: String,
    val licenseUrl: String
)

private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val OFL_URL = "https://openfontlicense.org/open-font-license-official-text/"

private val thirdPartyLibs = listOf(
    // --- Interfaz y diseño ---
    ThirdPartyLib("Jetpack Compose (UI declarativa y modular)", "Apache-2.0",
        "https://developer.android.com/jetpack/compose", APACHE_URL),
    ThirdPartyLib("Material Design 3 y Material You (color dinámico)", "Apache-2.0",
        "https://m3.material.io", APACHE_URL),
    ThirdPartyLib("Material Icons Extended", "Apache-2.0",
        "https://github.com/google/material-design-icons", APACHE_URL),
    ThirdPartyLib("Accompanist Permissions", "Apache-2.0",
        "https://github.com/google/accompanist", APACHE_URL),
    ThirdPartyLib("Coil (compose, gif)", "Apache-2.0",
        "https://github.com/coil-kt/coil", APACHE_URL),
    // --- AndroidX ---
    ThirdPartyLib("AndroidX (Core, Lifecycle, Activity, SplashScreen, ExifInterface)", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx", APACHE_URL),
    ThirdPartyLib("AndroidX DataStore", "Apache-2.0",
        "https://developer.android.com/topic/libraries/architecture/datastore", APACHE_URL),
    ThirdPartyLib("AndroidX Room", "Apache-2.0",
        "https://developer.android.com/training/data-storage/room", APACHE_URL),
    ThirdPartyLib("AndroidX Security (Crypto)", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/security", APACHE_URL),
    // --- Audio y metadatos ---
    ThirdPartyLib("AndroidX Media3 (ExoPlayer, Session, UI)", "Apache-2.0",
        "https://github.com/androidx/media", APACHE_URL),
    ThirdPartyLib("JAudioTagger (Adonai fork)", "LGPL 2.1+",
        "https://github.com/Adonai/jaudiotagger",
        "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"),
    ThirdPartyLib("android-youtube-player", "MIT",
        "https://github.com/PierfrancescoSoffritti/android-youtube-player",
        "https://github.com/PierfrancescoSoffritti/android-youtube-player/blob/master/LICENSE"),
    // --- Red y datos ---
    ThirdPartyLib("Retrofit (con convertidor Gson)", "Apache-2.0",
        "https://github.com/square/retrofit", APACHE_URL),
    ThirdPartyLib("Gson", "Apache-2.0",
        "https://github.com/google/gson", APACHE_URL),
    ThirdPartyLib("Guava", "Apache-2.0",
        "https://github.com/google/guava", APACHE_URL),
    ThirdPartyLib("Kotlin y Kotlin Coroutines (incl. Play Services)", "Apache-2.0",
        "https://github.com/Kotlin/kotlinx.coroutines", APACHE_URL),
    ThirdPartyLib("ZXing (código QR de las salas)", "Apache-2.0",
        "https://github.com/zxing/zxing", APACHE_URL),
    // --- Google y Firebase ---
    ThirdPartyLib("Google API Client / HTTP Client / Drive API", "Apache-2.0",
        "https://github.com/googleapis/google-api-java-client", APACHE_URL),
    ThirdPartyLib("Google Play Services Auth (Google Sign-In)", "Google ToS",
        "https://developers.google.com/identity/sign-in/android",
        "https://developers.google.com/terms"),
    ThirdPartyLib("Firebase (Auth, Realtime Database, Analytics)", "Apache-2.0",
        "https://github.com/firebase/firebase-android-sdk", APACHE_URL),
    // --- IA local ---
    ThirdPartyLib("LiteRT-LM (IA local)", "Apache-2.0",
        "https://github.com/google-ai-edge/LiteRT-LM", APACHE_URL),
    ThirdPartyLib("Gemma 4 E2B (Google DeepMind, modelo de IA local; conversión litert-community)", "Apache-2.0",
        "https://huggingface.co/google/gemma-4-E2B-it", APACHE_URL),
    // --- Tipografías incluidas ---
    ThirdPartyLib("Fuente Roboto", "Apache-2.0",
        "https://fonts.google.com/specimen/Roboto", APACHE_URL),
    ThirdPartyLib("Fuente Inter", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Inter", OFL_URL),
    ThirdPartyLib("Fuente Open Sans", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Open+Sans", OFL_URL),
    ThirdPartyLib("Fuente Lato", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Lato", OFL_URL),
    ThirdPartyLib("Fuente Nunito", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Nunito", OFL_URL),
    ThirdPartyLib("Fuente Comfortaa", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Comfortaa", OFL_URL),
    ThirdPartyLib("Fuente Asap", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Asap", OFL_URL),
    ThirdPartyLib("Fuente Orbitron", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Orbitron", OFL_URL),
    ThirdPartyLib("Fuente Playfair Display", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Playfair+Display", OFL_URL),
    ThirdPartyLib("Fuente Press Start 2P", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Press+Start+2P", OFL_URL),
    ThirdPartyLib("Fuente Space Mono", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Space+Mono", OFL_URL),
    ThirdPartyLib("Fuente Black Ops One", "SIL OFL 1.1",
        "https://fonts.google.com/specimen/Black+Ops+One", OFL_URL)
)

/** Contenido de Ajustes > Licencias de terceros (lista plana, sin tarjetas ni esquinas redondeadas). */
@Composable
fun ThirdPartyLicensesCard() {
    val uriHandler = LocalUriHandler.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.tpl_intro), fontSize = 13.sp, color = onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()

        thirdPartyLibs.forEach { lib ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
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
            HorizontalDivider()
        }

        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.tpl_lgpl_title),
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tpl_lgpl_body), fontSize = 12.sp, color = onSurfaceVariant)
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.tpl_google_note), fontSize = 11.sp, color = onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
