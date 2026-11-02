package com.music.musicflame.voice.wake

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.music.musicflame.R
import com.music.musicflame.voice.VoiceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Registro guiado: grabas "oye <nombre>" 4 veces; se guardan solo las huellas (el audio se borra). */
@Composable
fun WakeEnrollDialog(aiName: String, onFinished: (saved: Boolean) -> Unit) {
    val ctx = LocalContext.current
    var take by remember { mutableIntStateOf(1) }
    var level by remember { mutableStateOf(0f) }
    var status by remember { mutableIntStateOf(R.string.wake_enroll_waiting) }
    var finished by remember { mutableStateOf(false) }
    var inconsistent by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<Int?>(null) }
    var phrase by remember { mutableIntStateOf(0) }          // 0 = «oye <nombre>», 1 = «<nombre>» solo

    DisposableEffect(Unit) {
        WakeWord.testing = true                 // el servicio de escucha suelta el micrófono
        onDispose { WakeWord.testing = false }
    }
    LaunchedEffect(Unit) {
        delay(450)
        val extractor = withContext(Dispatchers.Default) { try { WakeFeatureExtractor(ctx) } catch (_: Throwable) { null } }
        if (extractor == null) { failed = R.string.wake_enroll_error; return@LaunchedEffect }
        try {
            val groups = ArrayList<List<Array<FloatArray>>>()
            for (g in 0..1) {
                phrase = g
            val takes = ArrayList<Array<FloatArray>>()
            while (takes.size < WAKE_TAKES) {
                take = takes.size + 1
                status = R.string.wake_enroll_waiting
                level = 0f
                val c = WakeAudio.captureClip({ level = it }, { status = R.string.wake_enroll_heard })
                if (c is WakeAudio.Capture.MicError) { failed = R.string.wake_enroll_mic_error; return@LaunchedEffect }
                if (c is WakeAudio.Capture.NoSpeech) { status = R.string.wake_enroll_retry_silent; delay(900); continue }
                val clip = (c as WakeAudio.Capture.Clip).samples
                val e = withContext(Dispatchers.Default) { WakeTemplates.embedClip(extractor, clip) }
                if (e.size < 6) { status = R.string.wake_enroll_retry_short; delay(900); continue }
                takes += e
                delay(350)
            }
                groups += takes
            }
            val data = withContext(Dispatchers.Default) { WakeTemplates.buildGroups(aiName, groups) }
            withContext(Dispatchers.IO) { WakeTemplates.save(ctx, data) }
            inconsistent = data.spread > WakeTemplates.INCONSISTENT_SPREAD
            finished = true
        } finally { extractor.close() }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.wake_enroll_title, aiName)) },
        text = {
            Column {
                val err = failed
                if (err != null) {
                    Text(stringResource(err), color = MaterialTheme.colorScheme.error)
                } else if (finished) {
                    Text(stringResource(R.string.wake_enroll_done))
                    if (inconsistent) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wake_enroll_inconsistent), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(stringResource(R.string.wake_enroll_take, take, WAKE_TAKES), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(if (phrase == 0) R.string.wake_enroll_prompt_oye else R.string.wake_enroll_prompt, aiName))
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(status), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.wake_enroll_tip), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (finished || failed != null) TextButton(onClick = { onFinished(finished) }) { Text(stringResource(R.string.voice_close)) }
        },
        dismissButton = {
            if (!finished && failed == null) TextButton(onClick = { onFinished(false) }) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

const val WAKE_TAKES = 4

private enum class TestStep { INTRO, A_RUN, A_DONE, B_RUN, RESULT, ERROR }

/** "Probar nombre": paso 1 dices la frase varias veces; paso 2 hablas normal para ver si se activa de más. */
@Composable
fun WakeTestDialog(aiName: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(TestStep.INTRO) }
    var live by remember { mutableStateOf<WakeTester.Live?>(null) }
    var hits by remember { mutableIntStateOf(0) }
    var misses by remember { mutableIntStateOf(0) }
    var falseCount by remember { mutableIntStateOf(0) }
    val holder = remember { arrayOfNulls<WakeDetector>(1) }

    DisposableEffect(Unit) {
        WakeWord.testing = true
        onDispose { WakeWord.testing = false; holder[0]?.close(); holder[0] = null }
    }

    LaunchedEffect(step) {
        if (step != TestStep.A_RUN && step != TestStep.B_RUN) return@LaunchedEffect
        delay(450)                               // deja que el servicio suelte el micrófono
        if (holder[0] == null) {
            val data = WakeTemplates.load(ctx)
            holder[0] = if (data == null) null else withContext(Dispatchers.Default) {
                try { WakeDetector(ctx, data, VoiceSettings(ctx).getWakeSensitivity()) } catch (_: Throwable) { null }
            }
        }
        val det = holder[0]
        if (det == null) { step = TestStep.ERROR; return@LaunchedEffect }
        det.sensitivity = VoiceSettings(ctx).getWakeSensitivity()
        val tester = WakeTester(ctx, det)
        if (step == TestStep.A_RUN) {
            val r = tester.run(25_000L, 5) { live = it }
            if (r.micError) { step = TestStep.ERROR; return@LaunchedEffect }
            hits = r.hits; misses = r.misses
            step = TestStep.A_DONE
        } else {
            val r = tester.run(10_000L, 0) { live = it }
            if (r.micError) { step = TestStep.ERROR; return@LaunchedEffect }
            falseCount = r.hits
            step = TestStep.RESULT
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.wake_test_title, aiName)) },
        text = {
            Column {
                when (step) {
                    TestStep.INTRO -> Text(stringResource(R.string.wake_test_intro))
                    TestStep.A_RUN, TestStep.B_RUN -> {
                        val a = step == TestStep.A_RUN
                        Text(stringResource(if (a) R.string.wake_test_a_title else R.string.wake_test_b_title, aiName), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(if (a) R.string.wake_test_a_body else R.string.wake_test_b_body), fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        val l = live
                        LinearProgressIndicator(progress = { l?.level ?: 0f }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        if (l != null) {
                            Text(
                                if (a) stringResource(R.string.wake_test_counts, l.hits, l.misses) else stringResource(R.string.wake_test_false, l.hits),
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                            )
                            Text(stringResource(R.string.wake_test_time, (l.msLeft / 1000L).toInt() + 1), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (l.score < 1f) {
                                Text(stringResource(R.string.wake_test_score, l.score, l.threshold), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    TestStep.A_DONE -> {
                        Text(stringResource(R.string.wake_test_counts, hits, misses), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wake_test_b_title, aiName), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.wake_test_b_body), fontSize = 13.sp)
                    }
                    TestStep.RESULT -> {
                        val attempts = hits + misses
                        val rate = if (attempts == 0) 0 else hits * 100 / attempts
                        Text(stringResource(R.string.wake_test_result, hits, attempts, rate, falseCount), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val lowRate = attempts > 0 && rate < 60
                        val manyFalse = falseCount >= 2
                        if (attempts == 0) Text(stringResource(R.string.wake_test_verdict_none), fontSize = 13.sp)
                        if (lowRate) Text(stringResource(R.string.wake_test_verdict_low), fontSize = 13.sp)
                        if (lowRate && manyFalse) Spacer(Modifier.height(6.dp))
                        if (manyFalse) Text(stringResource(R.string.wake_test_verdict_false), fontSize = 13.sp)
                        if (attempts > 0 && !lowRate && !manyFalse) Text(stringResource(R.string.wake_test_verdict_ok), fontSize = 13.sp)
                    }
                    TestStep.ERROR -> Text(stringResource(R.string.wake_enroll_mic_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (step) {
                TestStep.INTRO -> TextButton(onClick = { step = TestStep.A_RUN }) { Text(stringResource(R.string.wake_start)) }
                TestStep.A_DONE -> TextButton(onClick = { step = TestStep.B_RUN }) { Text(stringResource(R.string.wake_continue)) }
                TestStep.RESULT, TestStep.ERROR -> TextButton(onClick = onClose) { Text(stringResource(R.string.voice_close)) }
                else -> {}
            }
        },
        dismissButton = {
            if (step == TestStep.INTRO || step == TestStep.A_RUN || step == TestStep.B_RUN || step == TestStep.A_DONE) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
