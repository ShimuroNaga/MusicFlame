package com.music.musicflame.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.musicflame.ui.theme.LocalAppTextColor

@Composable
fun OnboardingAccountStep(
    isUserSignedIn: Boolean,
    userName: String?,
    onSignInClick: () -> Unit
) {
    val highEmphasis = LocalAppTextColor.current
    val mediumEmphasis = LocalAppTextColor.current.copy(alpha = 0.7f)

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isUserSignedIn) Icons.Filled.CheckCircle else Icons.Filled.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Cuenta de Google",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = highEmphasis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Opcional — vincula tu cuenta para usar  Google Drive dentro de MusicFlame. Puedes hacerlo luego desde Ajustes si prefieres saltarlo ahora.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = mediumEmphasis
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isUserSignedIn) {
            Text("Sesión iniciada como ${userName ?: "usuario"}", fontWeight = FontWeight.SemiBold, color = highEmphasis)
        } else {
            Button(
                onClick = onSignInClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Vincular cuenta de Google")
            }
        }
    }
}
