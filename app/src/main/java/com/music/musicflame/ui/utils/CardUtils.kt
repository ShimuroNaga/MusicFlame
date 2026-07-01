package com.music.musicflame.ui.utils

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Utilidad centralizada para gestionar transparencia de Cards automáticamente
 * según si hay imagen de fondo o no.
 *
 * Lógica:
 * - Si hasBackgroundImage = true → alpha 0.70f (semi-transparente)
 * - Si hasBackgroundImage = false → opaco (sin transparencia)
 */
object TransparentCardDefaults {

    @Composable
    fun surfaceContainerHigh(hasBackgroundImage: Boolean = false) =
        CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )

    @Composable
    fun surfaceContainer(hasBackgroundImage: Boolean = false) =
        CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )

    @Composable
    fun surfaceContainerHighest(hasBackgroundImage: Boolean = false) =
        CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        )

    @Composable
    fun errorContainer(hasBackgroundImage: Boolean = false) =
        CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )

    @Composable
    fun primaryContainer(hasBackgroundImage: Boolean = false) =
        CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )

    @Composable
    fun tertiaryContainer(hasBackgroundImage: Boolean = false) =
        CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        )

    @Composable
    fun secondaryContainer(hasBackgroundImage: Boolean = false) =
        CardDefaults.cardColors(
            containerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
}
