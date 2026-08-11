package com.music.musicflame.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.musicflame.ui.theme.LocalAppTextColor

@Composable
fun OnboardingCommunityStep() {
    val context = LocalContext.current
    val highEmphasis = LocalAppTextColor.current
    val mediumEmphasis = LocalAppTextColor.current.copy(alpha = 0.7f)

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Celebration,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "¡Todo listo!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = highEmphasis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Si te gusta MusicFlame, te invito a seguir el proyecto:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = mediumEmphasis
        )
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ShimuroNaga/MusicFlame"))
            context.startActivity(intent)
        }) {
            Text("Repositorio en GitHub")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = {
            // Mismo enlace que ya usas en la sección "Comunidad" de SettingsScreen.kt
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/gGZ4zCZvab"))
            context.startActivity(intent)
        }) {
            Text("Únete al Discord")
        }
    }
}
