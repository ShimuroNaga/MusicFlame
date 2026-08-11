package com.music.musicflame.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.ui.theme.LocalAppTextColor

private const val TOTAL_STEPS = 5

/**
 * Wizard de primer uso: se muestra UNA sola vez (controlado por
 * SettingsRepository.isOnboardingCompleted()). Copia — no reemplaza — los
 * ajustes reales de la app, organizados en bloques/pasos, usando exactamente
 * los mismos colores y componentes que SettingsScreen.kt para que se sienta
 * parte de la misma app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    isUserSignedIn: Boolean,
    userName: String?,
    onSignInClick: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val highEmphasis = LocalAppTextColor.current

    var currentStep by remember { mutableIntStateOf(1) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Configura MusicFlame", color = highEmphasis) },
                    navigationIcon = {
                        if (currentStep > 1) {
                            IconButton(onClick = { currentStep-- }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Atrás",
                                    tint = highEmphasis
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                LinearProgressIndicator(
                    progress = { currentStep / TOTAL_STEPS.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep == 4) {
                    TextButton(onClick = { currentStep++ }) { Text("Omitir por ahora") }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep < TOTAL_STEPS) {
                            currentStep++
                        } else {
                            settingsRepo.setOnboardingCompleted(true)
                            onFinished()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (currentStep == TOTAL_STEPS) "Empezar a usar MusicFlame" else "Siguiente")
                }
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalContentColor provides highEmphasis) {
            AnimatedContent(
                targetState = currentStep,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    1 -> OnboardingPermissionsStep()
                    2 -> OnboardingAppearanceStep(settingsRepo = settingsRepo)
                    3 -> OnboardingSongsStep(settingsRepo = settingsRepo)
                    4 -> OnboardingAccountStep(
                        isUserSignedIn = isUserSignedIn,
                        userName = userName,
                        onSignInClick = onSignInClick
                    )
                    5 -> OnboardingCommunityStep()
                }
            }
        }
    }
}
