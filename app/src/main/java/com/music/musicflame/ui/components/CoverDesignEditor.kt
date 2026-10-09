package com.music.musicflame.ui.components

import com.music.musicflame.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.musicflame.AlbumArtShapeType
import com.music.musicflame.data.CoverDesign
import com.music.musicflame.data.CoverFigure
import com.music.musicflame.data.Song
import com.music.musicflame.ui.utils.CoverShapeGeometry
import kotlin.math.roundToInt

/**
 * Editor de diseños de carátula (Ajustes > Apariencia > Forma de la carátula > "+ Crear diseño").
 *
 * Pantalla completa con una vista previa en vivo arriba y los controles abajo:
 * figura (7 opciones), cantidad de caras/puntas/pétalos/dientes, profundidad, redondeado,
 * rotación y transparencia, más el nombre del diseño.
 * La vista previa usa el mismo AlbumArt() que las listas, así que lo que se ve aquí
 * es exactamente lo que se verá en la app (y, con la misma geometría, en los widgets).
 *
 * @param initial diseño a editar, o null para crear uno nuevo.
 * @param previewSong canción con la que probar el diseño; si no hay (o no tiene
 *        carátula) se usa un degradado de muestra.
 * @param onSave recibe el diseño ya validado (ver CoverDesign.sanitized()).
 */
@Composable
fun CoverDesignEditorDialog(
    initial: CoverDesign?,
    previewSong: Song? = null,
    onDismiss: () -> Unit,
    onSave: (CoverDesign) -> Unit
) {
    var draft by remember { mutableStateOf(initial ?: CoverDesign()) }
    val isNew = initial == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- Barra superior ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.main_close))
                    }
                    Text(
                        if (isNew) stringResource(R.string.ce_create) else stringResource(R.string.ce_edit),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onSave(draft.sanitized()) }) {
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                HorizontalDivider()

                // --- Vista previa (fija arriba) ---
                CoverPreviewPanel(design = draft, previewSong = previewSong)
                HorizontalDivider()

                // --- Controles (con scroll por si la pantalla es chica) ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it.take(CoverDesign.MAX_NAME_LENGTH)) },
                        label = { Text(stringResource(R.string.ce_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Figura
                    Column {
                        Text(stringResource(R.string.ce_shape), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            CoverFigure.entries.forEach { figure ->
                                FilterChip(
                                    selected = draft.figure == figure,
                                    onClick = { draft = draft.copy(figure = figure) },
                                    label = { Text(figure.label) }
                                )
                            }
                        }
                    }

                    // Cantidad de caras / puntas / pétalos / dientes (solo si la figura lo usa)
                    draft.figure.sidesLabel?.let { sidesLabel ->
                        LabeledSlider(
                            label = "$sidesLabel: ${draft.sides}",
                            value = draft.sides.toFloat(),
                            onValueChange = { draft = draft.copy(sides = it.roundToInt()) },
                            valueRange = CoverShapeGeometry.MIN_SIDES.toFloat()..CoverShapeGeometry.MAX_SIDES.toFloat(),
                            steps = CoverShapeGeometry.MAX_SIDES - CoverShapeGeometry.MIN_SIDES - 1
                        )
                    }

                    // Profundidad / grosor / altura / anchura (según la figura)
                    draft.figure.depthLabel?.let { depthLabel ->
                        LabeledSlider(
                            label = "$depthLabel: ${(draft.depth * 100).roundToInt()}%",
                            value = draft.depth,
                            onValueChange = { draft = draft.copy(depth = it) },
                            valueRange = 0f..1f
                        )
                    }

                    // Redondeado
                    LabeledSlider(
                        label = stringResource(R.string.cd_rounding, (draft.roundness * 100).roundToInt()),
                        value = draft.roundness,
                        onValueChange = { draft = draft.copy(roundness = it) },
                        valueRange = 0f..1f
                    )

                    // Rotación
                    LabeledSlider(
                        label = stringResource(R.string.cd_rotation, draft.rotation.roundToInt()),
                        value = draft.rotation,
                        onValueChange = { draft = draft.copy(rotation = it) },
                        valueRange = 0f..360f
                    )

                    // Transparencia
                    LabeledSlider(
                        label = stringResource(R.string.cd_transparency, (draft.transparency * 100).roundToInt()),
                        value = draft.transparency,
                        onValueChange = { draft = draft.copy(transparency = it) },
                        valueRange = 0f..CoverDesign.MAX_TRANSPARENCY
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

// Vista previa grande sobre un fondo de cuadros (para que la transparencia se note) y,
// debajo, una fila de ejemplo con el tamaño real que tiene la carátula en las listas.
@Composable
private fun CoverPreviewPanel(design: CoverDesign, previewSong: Song?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            CheckerBackground(modifier = Modifier.size(190.dp))
            PreviewArt(design = design, previewSong = previewSong, size = 164.dp)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewArt(design = design, previewSong = previewSong, size = 50.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        previewSong?.title ?: stringResource(R.string.ce_preview),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    Text(
                        previewSong?.artist ?: design.name,
                        fontSize = 13.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// La canción actual si tiene carátula (AlbumArt() real), si no el degradado de muestra.
@Composable
private fun PreviewArt(design: CoverDesign, previewSong: Song?, size: androidx.compose.ui.unit.Dp) {
    if (previewSong?.albumArtUri != null) {
        AlbumArt(
            albumArtUri = previewSong.albumArtUri,
            size = size,
            shape = AlbumArtShapeType.CUSTOM,
            filePath = previewSong.path,
            isCustomCover = previewSong.hasCustomCover,
            customDesign = design
        )
    } else {
        CoverDesignSwatch(design = design, size = size)
    }
}

// Fondo de cuadros claro/oscuro, el clásico para ver transparencias.
@Composable
private fun CheckerBackground(modifier: Modifier = Modifier) {
    val light = Color(0xFFCCCCCC)
    val dark = Color(0xFF999999)
    Canvas(modifier = modifier) {
        val cell = 14.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                drawRect(
                    color = if ((r + c) % 2 == 0) light else dark,
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}
