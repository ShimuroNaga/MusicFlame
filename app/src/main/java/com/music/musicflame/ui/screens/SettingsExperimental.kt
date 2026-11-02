package com.music.musicflame.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.musicflame.R

private val ExperimentalYellow = Color(0xFFFFD600)

/** Título de sección en amarillo que "brilla" (el resplandor late en bucle) + etiqueta "Experimental". */
@Composable
internal fun ExperimentalSectionHeader(text: String) {
    val t = rememberInfiniteTransition(label = "experimental_glow")
    val glow by t.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Row(
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(color = ExperimentalYellow.copy(alpha = glow), offset = Offset.Zero, blurRadius = 6f + 20f * glow)
            ),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = ExperimentalYellow
        )
        Text(
            text = stringResource(R.string.voice_exp_badge),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ExperimentalYellow.copy(alpha = 0.7f + 0.3f * glow),
            modifier = Modifier
                .border(BorderStroke(1.dp, ExperimentalYellow.copy(alpha = 0.4f + 0.5f * glow)), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Cuadro pequeño: qué puede y qué no puede hacer (todavía) el asistente de voz. */
@Composable
internal fun VoiceCapabilitiesCard(style: SettingsStyle) {
    Card(
        Modifier.fillMaxWidth().border(BorderStroke(1.dp, ExperimentalYellow.copy(alpha = 0.45f)), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.voice_exp_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = style.highEmphasis)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.voice_exp_intro), fontSize = 12.sp, color = style.mediumEmphasis)

            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.voice_exp_can_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Text("• " + stringResource(R.string.voice_exp_can1), fontSize = 12.sp, color = style.highEmphasis)
            Text("• " + stringResource(R.string.voice_exp_can2), fontSize = 12.sp, color = style.highEmphasis)
            Text("• " + stringResource(R.string.voice_exp_can3), fontSize = 12.sp, color = style.highEmphasis)

            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.voice_exp_why), fontSize = 12.sp, color = style.mediumEmphasis)
        }
    }
    Spacer(Modifier.height(12.dp))
}
