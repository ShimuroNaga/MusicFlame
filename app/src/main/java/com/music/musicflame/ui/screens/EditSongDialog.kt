package com.music.musicflame.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.music.musicflame.data.RealTagWriter
import com.music.musicflame.data.SettingsRepository
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongCustomizationRepository
import com.music.musicflame.data.SongLibraryHolder
import kotlinx.coroutines.launch
import com.music.musicflame.data.getDefaultAlbumArtUri
import com.music.musicflame.data.getOriginalSongTitle
import com.music.musicflame.data.getOriginalSongArtist
import com.music.musicflame.data.getOriginalSongAlbum
import com.music.musicflame.ui.components.AlbumArt

/**
 * Resultado de una edición: qué canción cambió y cuáles son sus valores FINALES
 * (no deltas), para que SongsScreen pueda aplicarlos al instante sobre la lista
 * que ya tiene en memoria, sin tener que releer todo el dispositivo de nuevo.
 * null en cualquier campo significa "ese campo no cambió".
 */
data class SongEditPatch(
    val songId: Long,
    val newTitle: String? = null,
    val newCoverUri: String? = null,
    // --- NUEVO: editor de etiquetas/metadata ---
    val newArtist: String? = null,
    val newAlbum: String? = null
)

/**
 * Diálogo de "Editar carátula y nombre", accesible desde la selección múltiple
 * de canciones (menú de Opciones > Editar carátula y nombre).
 *
 * - Si se seleccionó UNA sola canción: permite cambiar su nombre, artista,
 *   álbum y carátula (imagen o GIF), con opción de restablecer cada campo a
 *   su valor original.
 * - Si se seleccionaron VARIAS: aplica la misma carátula y/o el mismo nombre
 *   de álbum a todas a la vez (para juntarlas en un solo álbum de una sola
 *   pasada). El nombre y el artista no se editan en modo por lote, porque
 *   cada canción necesita el suyo propio.
 */
@Composable
fun EditSongDialog(
    selectedSongs: List<Song>,
    customizationRepo: SongCustomizationRepository,
    settingsRepo: SettingsRepository,
    onDismiss: () -> Unit,
    onSaved: (List<SongEditPatch>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Si el usuario activó el switch en Ajustes > Canciones y ya tiene el
    // permiso concedido, además de guardar la personalización en la app,
    // escribimos de verdad los tags ID3 en el archivo .mp3 en disco.
    val writeRealTags = settingsRepo.isRealTagWritingEnabled() && RealTagWriter.hasFileAccessPermission()
    val isSingle = selectedSongs.size == 1
    val song = selectedSongs.firstOrNull()

    val existingCustomization = remember(song?.id) { song?.let { customizationRepo.getCustomization(it.id) } }

    var titleText by remember(song?.id) { mutableStateOf(song?.title ?: "") }
    var pickedCoverUri by remember { mutableStateOf<String?>(null) }
    var resetCover by remember { mutableStateOf(false) }
    var resetTitle by remember { mutableStateOf(false) }
    // --- NUEVO: editor de etiquetas/metadata (artista y álbum) ---
    var artistText by remember(song?.id) { mutableStateOf(song?.artist ?: "") }
    // En modo lote (varias canciones) el álbum arranca vacío: no asumimos que
    // todas comparten el mismo álbum de origen, y solo se aplica si el usuario
    // escribe algo explícitamente.
    var albumText by remember(song?.id) { mutableStateOf(if (isSingle) song?.album ?: "" else "") }
    var resetArtist by remember { mutableStateOf(false) }
    var resetAlbum by remember { mutableStateOf(false) }

    val pickCoverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            pickedCoverUri = it.toString()
            resetCover = false
        }
    }

    // Lo que se ve en la vista previa del diálogo
    val previewCoverUri = when {
        pickedCoverUri != null -> pickedCoverUri
        resetCover -> song?.let { getDefaultAlbumArtUri(context, it.id) }
        isSingle -> song?.albumArtUri
        else -> null
    }

    val hasChanges = pickedCoverUri != null || resetCover ||
            (isSingle && (
                    resetTitle || (titleText.isNotBlank() && titleText != song?.title) ||
                            resetArtist || (artistText.isNotBlank() && artistText != song?.artist) ||
                            resetAlbum || (albumText.isNotBlank() && albumText != song?.album)
                    )) ||
            (!isSingle && albumText.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isSingle) "Editar etiquetas y carátula"
                else "Editar carátula (${selectedSongs.size} canciones)"
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                AlbumArt(previewCoverUri, 96.dp, 14.dp, filePath = song?.path)

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickCoverLauncher.launch("image/*") }) {
                        Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Elegir imagen o GIF")
                    }
                }

                val hadCustomCover = existingCustomization?.coverUri != null
                if (hadCustomCover || pickedCoverUri != null) {
                    TextButton(onClick = {
                        pickedCoverUri = null
                        resetCover = true
                    }) {
                        Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isSingle) "Restablecer carátula original" else "Quitar carátula personalizada a todas")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (isSingle) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it; resetTitle = false },
                        label = { Text("Nombre de la canción") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existingCustomization?.title != null) {
                        TextButton(onClick = {
                            resetTitle = true
                            titleText = getOriginalSongTitle(context, song!!.id)
                        }) {
                            Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restablecer nombre original")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // --- NUEVO: editor de etiquetas/metadata (artista) ---
                    OutlinedTextField(
                        value = artistText,
                        onValueChange = { artistText = it; resetArtist = false },
                        label = { Text("Artista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existingCustomization?.artist != null) {
                        TextButton(onClick = {
                            resetArtist = true
                            artistText = getOriginalSongArtist(context, song!!.id)
                        }) {
                            Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restablecer artista original")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // --- NUEVO: editor de etiquetas/metadata (álbum) ---
                    OutlinedTextField(
                        value = albumText,
                        onValueChange = { albumText = it; resetAlbum = false },
                        label = { Text("Álbum") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existingCustomization?.album != null) {
                        TextButton(onClick = {
                            resetAlbum = true
                            albumText = getOriginalSongAlbum(context, song!!.id)
                        }) {
                            Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restablecer álbum original")
                        }
                    }
                } else {
                    Text(
                        text = "El nombre y el artista solo se pueden editar seleccionando una sola canción.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Álbum en lote: para juntar varias canciones (p. ej. varios
                    // singles de un mismo artista) en un solo álbum de una vez,
                    // en lugar de tener que editar canción por canción.
                    OutlinedTextField(
                        value = albumText,
                        onValueChange = { albumText = it; resetAlbum = false },
                        label = { Text("Álbum (para las ${selectedSongs.size} canciones)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Si además eliges una carátula arriba, se usará esa misma para las ${selectedSongs.size} canciones y para representar el álbum.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasChanges,
                onClick = {
                    val patches = mutableListOf<SongEditPatch>()

                    if (isSingle && song != null) {
                        // Solo mandamos cada campo si el usuario realmente lo cambió;
                        // así no creamos una personalización redundante que sea igual al original.
                        val newTitle = if (!resetTitle && titleText.isNotBlank() && titleText != song.title) titleText else null
                        val newArtist = if (!resetArtist && artistText.isNotBlank() && artistText != song.artist) artistText else null
                        val newAlbum = if (!resetAlbum && albumText.isNotBlank() && albumText != song.album) albumText else null
                        customizationRepo.setCustomization(
                            songId = song.id,
                            title = newTitle,
                            coverUri = pickedCoverUri,
                            artist = newArtist,
                            album = newAlbum,
                            clearTitle = resetTitle,
                            clearCover = resetCover,
                            clearArtist = resetArtist,
                            clearAlbum = resetAlbum
                        )
                        // Invalida el cache compartido para que el resto de pantallas
                        // (no solo esta lista, que ya se actualiza al instante vía patches)
                        // vean la personalización nueva la próxima vez que la lean.
                        scope.launch { SongLibraryHolder.refresh(context) }

                        // --- Guardado real en el archivo (si el switch de Ajustes está activo) ---
                        // Nota: solo aplica a valores NUEVOS (no a "restablecer"), y solo a .mp3;
                        // ver limitaciones documentadas en RealTagWriter.
                        if (writeRealTags && (newTitle != null || newArtist != null || newAlbum != null || pickedCoverUri != null)) {
                            val wroteOk = RealTagWriter.applyTags(
                                context = context,
                                path = song.path,
                                title = newTitle,
                                artist = newArtist,
                                album = newAlbum,
                                coverUri = pickedCoverUri?.let { Uri.parse(it) }
                            )
                            if (!wroteOk) {
                                Toast.makeText(
                                    context,
                                    "No se pudo guardar en el archivo real (¿formato soportado y permiso concedido?)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        // Valores FINALES a mostrar ya mismo en la lista (sin releer MediaStore):
                        val finalTitle = newTitle ?: if (resetTitle) getOriginalSongTitle(context, song.id) else null
                        val finalCover = pickedCoverUri ?: if (resetCover) getDefaultAlbumArtUri(context, song.id) else null
                        val finalArtist = newArtist ?: if (resetArtist) getOriginalSongArtist(context, song.id) else null
                        val finalAlbum = newAlbum ?: if (resetAlbum) getOriginalSongAlbum(context, song.id) else null
                        if (finalTitle != null || finalCover != null || finalArtist != null || finalAlbum != null) {
                            patches.add(SongEditPatch(song.id, finalTitle, finalCover, finalArtist, finalAlbum))
                        }
                    } else if (selectedSongs.isNotEmpty() && (pickedCoverUri != null || resetCover || albumText.isNotBlank())) {
                        val batchAlbum = albumText.takeIf { it.isNotBlank() }

                        customizationRepo.setCoverForSongs(
                            songIds = selectedSongs.map { it.id },
                            coverUri = pickedCoverUri,
                            clearCover = resetCover
                        )
                        if (batchAlbum != null) {
                            customizationRepo.setAlbumForSongs(
                                songIds = selectedSongs.map { it.id },
                                album = batchAlbum
                            )
                        }
                        scope.launch { SongLibraryHolder.refresh(context) }
                        selectedSongs.forEach { s ->
                            val finalCover = pickedCoverUri ?: if (resetCover) getDefaultAlbumArtUri(context, s.id) else null
                            if (finalCover != null || batchAlbum != null) {
                                patches.add(SongEditPatch(s.id, newCoverUri = finalCover, newAlbum = batchAlbum))
                            }
                        }

                        // --- Guardado real en el archivo, para cada canción del lote ---
                        if (writeRealTags && (pickedCoverUri != null || batchAlbum != null)) {
                            val coverUriParsed = pickedCoverUri?.let { Uri.parse(it) }
                            var anyFailed = false
                            selectedSongs.forEach { s ->
                                val ok = RealTagWriter.applyTags(
                                    context = context,
                                    path = s.path,
                                    album = batchAlbum,
                                    coverUri = coverUriParsed
                                )
                                if (!ok && RealTagWriter.isSupportedFile(s.path)) anyFailed = true
                            }
                            if (anyFailed) {
                                Toast.makeText(context, "Algunas canciones no se pudieron actualizar en el archivo real", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    onSaved(patches)
                }
            ) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}