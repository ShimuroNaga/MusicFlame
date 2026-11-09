package com.music.musicflame.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.music.musicflame.R
import com.music.musicflame.data.ArtworkCacheRepository
import com.music.musicflame.data.ArtworkSource
import com.music.musicflame.data.LicenseRepository
import com.music.musicflame.data.OrbitaImportPreview
import com.music.musicflame.data.OrbitaInbox
import com.music.musicflame.data.OrbitaRepository
import com.music.musicflame.data.Song
import com.music.musicflame.data.SongLibraryHolder
import com.music.musicflame.data.TrashRepository
import com.music.musicflame.data.db.OrbitaMetaWithCount
import com.music.musicflame.data.db.OrbitaSongEntity
import com.music.musicflame.ui.components.AlbumArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Tema por rol
// ---------------------------------------------------------------------------------------------

/** Acento de Órbita: verde para la cuenta del dueño ("Para Lucas"), azul-violeta suave para el resto. */
fun orbitaAccent(isAdmin: Boolean): Color = if (isAdmin) Color(0xFF4ADE80) else Color(0xFF8E9BFF)

private fun orbitaAccentSoft(isAdmin: Boolean): Color = if (isAdmin) Color(0xFF6EE8A2) else Color(0xFFB4A5FF)

/** Nombre del sub-tab del pager de Mix: "Para Lucas" para el dueño, "Órbita" para los demás. */
@Composable
fun orbitaTabLabel(isAdmin: Boolean): String =
    stringResource(if (isAdmin) R.string.orbita_tab_admin else R.string.orbita_tab_default)

// Distancia máxima (fracción del lado MENOR del área) para unir dos nodos con línea punteada,
// y tope de conexiones por nodo. Ajustables aquí.
private const val LINK_MAX_DISTANCE_FRACTION = 0.55f
private const val LINK_MAX_PER_NODE = 3
private const val LINK_NEAREST_TRIED = 2

private val OrbitaPanel = Color(0xFF07140E)
private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

private fun nodeColor(id: Long): Color {
    val h = 120f + (abs(id * 37L) % 80L).toFloat() // 120 verde .. 200 azul-cian
    return Color.hsv(h, 0.55f, 0.85f)
}

private fun nodeSizeDp(id: Long): Int = 48 + (abs(id * 7919L) % 17L).toInt() // 48..64

// ---------------------------------------------------------------------------------------------
// Página principal (tercera sub-página del pager de MixScreen)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitaPage(
    isVisible: Boolean,
    isAdmin: Boolean,
    onPlaySong: (Song) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { OrbitaRepository(context) }
    val accent = orbitaAccent(isAdmin)
    val ownName = stringResource(R.string.orbita_own_name)

    var ownId by remember { mutableStateOf<Long?>(null) }
    var metas by remember { mutableStateOf<List<OrbitaMetaWithCount>?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        ownId = repo.ensureOwn(ownName)
        metas = repo.list()
    }

    // --- Importación pendiente (archivo .orbita abierto con MusicFlame) ---
    val pendingUri = OrbitaInbox.pendingUri
    var preview by remember { mutableStateOf<OrbitaImportPreview?>(null) }
    var existingForPreview by remember { mutableStateOf<Long?>(null) }
    var askReplace by remember { mutableStateOf(false) }
    val invalidMsg = stringResource(R.string.orbita_import_invalid)
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        val p = repo.readPreview(uri)
        OrbitaInbox.pendingUri = null
        if (p == null || p.canciones.isEmpty()) {
            Toast.makeText(context, invalidMsg, Toast.LENGTH_LONG).show()
        } else {
            existingForPreview = repo.findImportedByAuthor(p.autorNombre)?.id
            preview = p
        }
    }

    // Importar un .orbita desde el almacenamiento (respaldo para apps que no lo abren directo).
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) OrbitaInbox.pendingUri = uri
    }
    val openImport = { importLauncher.launch(arrayOf("*/*")) }

    val list = metas
    val onlyOwn = (list?.size ?: 0) <= 1
    val activeId: Long? = selectedId ?: if (onlyOwn) ownId else null

    // Atrás desde una órbita abierta vuelve a la selección (solo si hay selección).
    BackHandler(enabled = isVisible && selectedId != null && !onlyOwn) { selectedId = null }

    Box(Modifier.fillMaxSize()) {
        OrbitaBackground(isVisible = isVisible, accent = accentSoft(isAdmin))

        if (list != null) {
            if (activeId == null) {
                OrbitaSelector(
                    metas = list,
                    accent = accent,
                    onImport = openImport,
                    onOpen = { selectedId = it },
                    onDelete = { id ->
                        scope.launch { repo.deleteImported(id); refresh++ }
                    }
                )
            } else {
                val meta = list.firstOrNull { it.id == activeId }
                OrbitaSpace(
                    orbitaId = activeId,
                    title = if (meta == null || meta.esPropia) ownName else meta.nombre,
                    editable = meta?.esPropia ?: true,
                    accent = accent,
                    repo = repo,
                    onBack = if (!onlyOwn) ({ selectedId = null }) else null,
                    onImport = openImport,
                    onPlaySong = onPlaySong
                )
            }
        }
    }

    // --- Diálogos de importación ---
    val pv = preview
    if (pv != null && !askReplace) {
        OrbitaImportDialog(
            preview = pv,
            onDismiss = { preview = null },
            onConfirm = {
                if (existingForPreview != null) {
                    askReplace = true
                } else {
                    scope.launch {
                        repo.saveImported(pv, importedName(context, pv.autorNombre))
                        preview = null; selectedId = null; refresh++
                    }
                }
            }
        )
    }
    if (pv != null && askReplace) {
        AlertDialog(
            onDismissRequest = { askReplace = false; preview = null },
            title = { Text(stringResource(R.string.orbita_import_exists_title, pv.autorNombre.ifBlank { stringResource(R.string.orbita_import_anonymous) })) },
            confirmButton = {
                TextButton(onClick = {
                    val replaceId = existingForPreview
                    scope.launch {
                        repo.saveImported(pv, importedName(context, pv.autorNombre), replaceId)
                        askReplace = false; preview = null; selectedId = null; refresh++
                    }
                }) { Text(stringResource(R.string.orbita_import_replace)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            repo.saveImported(pv, importedName(context, pv.autorNombre), null)
                            askReplace = false; preview = null; selectedId = null; refresh++
                        }
                    }) { Text(stringResource(R.string.orbita_import_new)) }
                    TextButton(onClick = { askReplace = false; preview = null }) {
                        Text(stringResource(R.string.orbita_cancel))
                    }
                }
            }
        )
    }
}

private fun accentSoft(isAdmin: Boolean) = orbitaAccentSoft(isAdmin)

private fun importedName(context: android.content.Context, autor: String): String =
    context.getString(
        R.string.orbita_imported_name,
        autor.ifBlank { context.getString(R.string.orbita_import_anonymous) }
    )

// ---------------------------------------------------------------------------------------------
// Fondo animado (nebulosas + estrellas + aurora)
// ---------------------------------------------------------------------------------------------

private class OrbitaStar(val x: Float, val y: Float, val rDp: Float, val speed: Float, val phase: Float)

private class OrbitaNebula(val x: Float, val y: Float, val rad: Float, val color: Color, val speed: Float, val phase: Float)

private val orbitaNebulae = listOf(
    OrbitaNebula(0.25f, 0.28f, 0.60f, Color(0xFF0B4D2A), 0.07f, 0.0f),
    OrbitaNebula(0.78f, 0.62f, 0.65f, Color(0xFF0A3550), 0.05f, 1.7f),
    OrbitaNebula(0.50f, 0.92f, 0.55f, Color(0xFF0B3D2E), 0.06f, 3.1f),
    OrbitaNebula(0.12f, 0.80f, 0.45f, Color(0xFF0C2A4A), 0.04f, 4.6f)
)

@Composable
private fun OrbitaBackground(isVisible: Boolean, accent: Color) {
    val stars = remember {
        val rnd = Random(2026)
        List(280) {
            OrbitaStar(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                rDp = 0.3f + rnd.nextFloat() * 1.5f,       // 0.3dp .. 1.8dp
                speed = 0.6f + rnd.nextFloat() * 2.4f,
                phase = rnd.nextFloat() * 6.2832f
            )
        }
    }
    var timeSec by remember { mutableFloatStateOf(0f) }

    // El loop solo corre mientras la página es visible (igual que MomentsMixPage).
    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            timeSec += (now - last) / 1_000_000_000f
            last = now
        }
    }

    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        val t = timeSec
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF020B06), Color(0xFF030D10))))

        // Nebulosas: manchas grandes que derivan lentamente, alpha bajo.
        for (n in orbitaNebulae) {
            val cx = (n.x + 0.08f * sin(t * n.speed + n.phase)) * w
            val cy = (n.y + 0.06f * sin(t * n.speed * 0.8f + n.phase + 1f)) * h
            val rad = n.rad * maxOf(w, h)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(n.color.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(cx, cy), radius = rad
                ),
                radius = rad, center = Offset(cx, cy)
            )
        }

        // Aurora: banda horizontal verde translúcida que oscila.
        val ay = h * (0.28f + 0.05f * sin(t * 0.25f))
        val ah = h * 0.20f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF4ADE80).copy(alpha = 0.06f + 0.03f * sin(t * 0.4f)), Color.Transparent),
                startY = ay, endY = ay + ah
            ),
            topLeft = Offset(0f, ay), size = Size(w, ah)
        )

        // Estrella fugaz: una cada ~9s, trayectoria distinta según el ciclo.
        val cyc = (t % 9f) / 1.1f
        if (cyc in 0f..1f) {
            val k = (t / 9f).toInt() % 3
            val head = Offset(w * (0.2f + 0.25f * k) + cyc * w * 0.35f, h * (0.06f + 0.05f * k) + cyc * h * 0.22f)
            val tail = Offset(head.x - w * 0.10f, head.y - h * 0.06f)
            drawLine(
                brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * (1f - cyc))), start = tail, end = head),
                start = tail, end = head, strokeWidth = 1.6f * density
            )
        }

        // Estrellas con parpadeo individual; las grandes con halo del color de acento.
        for (s in stars) {
            val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(t * s.speed + s.phase))
            val c = Offset(s.x * w, s.y * h)
            val r = s.rDp * density
            if (s.rDp > 1.4f) {
                drawCircle(accent.copy(alpha = a * 0.22f), radius = r * 4.5f, center = c)
            }
            drawCircle(Color.White.copy(alpha = a), radius = r, center = c)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pantalla de selección de órbita
// ---------------------------------------------------------------------------------------------

@Composable
private fun OrbitaSelector(
    metas: List<OrbitaMetaWithCount>,
    accent: Color,
    onImport: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var toDelete by remember { mutableStateOf<OrbitaMetaWithCount?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.orbita_select_title),
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onImport) {
                    Icon(Icons.Filled.FileDownload, stringResource(R.string.orbita_import_file), tint = accent)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        items(metas, key = { it.id }) { m ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrbitaPanel.copy(alpha = 0.85f))
                    .border(1.dp, accent.copy(alpha = if (m.esPropia) 0.7f else 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onOpen(m.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (m.esPropia) stringResource(R.string.orbita_own_name) else m.nombre,
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (!m.esPropia && m.autorNombre.isNotBlank()) {
                        Text(
                            stringResource(R.string.orbita_by_author, m.autorNombre),
                            color = accent, fontSize = 13.sp
                        )
                    }
                    Text(
                        stringResource(R.string.orbita_songs_count, m.cantidad) +
                                if (!m.esPropia) " · " + stringResource(R.string.orbita_imported_on, dateFmt.format(Date(m.fechaImport))) else "",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                }
                if (!m.esPropia) {
                    IconButton(onClick = { toDelete = m }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.orbita_delete), tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.orbita_delete_title)) },
            text = { Text(stringResource(R.string.orbita_delete_msg)) },
            confirmButton = {
                TextButton(onClick = { onDelete(m.id); toDelete = null }) { Text(stringResource(R.string.orbita_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.orbita_cancel)) } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Espacio de nodos (canvas libre)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrbitaSpace(
    orbitaId: Long,
    title: String,
    editable: Boolean,
    accent: Color,
    repo: OrbitaRepository,
    onBack: (() -> Unit)?,
    onImport: () -> Unit,
    onPlaySong: (Song) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var songs by remember(orbitaId) { mutableStateOf<List<OrbitaSongEntity>>(emptyList()) }
    val live = remember(orbitaId) { mutableStateMapOf<Long, Offset>() }
    var reload by remember(orbitaId) { mutableIntStateOf(0) }

    LaunchedEffect(orbitaId, reload) {
        val s = repo.songs(orbitaId)
        live.clear()
        s.forEach { live[it.id] = Offset(it.posX, it.posY) }
        songs = s
    }

    // Flotación suave de los nodos (cada uno con su fase).
    val floatT by rememberInfiniteTransition(label = "orbitaFloat").animateFloat(
        initialValue = 0f, targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbitaFloatT"
    )
    var tapFraction by remember { mutableStateOf<Offset?>(null) }   // posición tocada (fracciones 0..1)
    var showAsk by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var selectedNodeId by remember { mutableStateOf<Long?>(null) }
    var exportFile by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf(false) }

    val fullMsg = stringResource(R.string.orbita_limit_reached, OrbitaRepository.MAX_SONGS)
    val dupMsg = stringResource(R.string.orbita_duplicate)
    val savedMsg = stringResource(R.string.orbita_export_saved)
    val failMsg = stringResource(R.string.orbita_export_failed)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val file = exportFile
        if (uri != null && file != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        } != null
                    } catch (e: Exception) { false }
                }
                Toast.makeText(context, if (ok) savedMsg else failMsg, Toast.LENGTH_SHORT).show()
                if (ok) exportFile = null
            }
        }
    }

    fun addSong(song: Song) {
        scope.launch {
            val (fx, fy) = tapFraction?.let { it.x to it.y } ?: pickFreeSpot(live.values.toList())
            when (repo.addSong(orbitaId, song, fx, fy)) {
                OrbitaRepository.AddResult.ADDED -> reload++
                OrbitaRepository.AddResult.FULL -> Toast.makeText(context, fullMsg, Toast.LENGTH_SHORT).show()
                OrbitaRepository.AddResult.DUPLICATE -> Toast.makeText(context, dupMsg, Toast.LENGTH_SHORT).show()
            }
            tapFraction = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // --- Barra superior ---
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.orbita_back), tint = Color.White)
                    }
                } else Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "✦ " + title.uppercase(Locale.getDefault()),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        letterSpacing = 3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(color = accent.copy(alpha = 0.9f), blurRadius = 22f)
                        )
                    )
                    Box(
                        Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text(
                            if (editable) "${songs.size}/${OrbitaRepository.MAX_SONGS}" else stringResource(R.string.orbita_readonly),
                            color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (editable) {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Filled.FileDownload, stringResource(R.string.orbita_import_file), tint = Color.White)
                    }
                }
                if (editable && songs.isNotEmpty()) {
                    IconButton(
                        enabled = !exporting,
                        onClick = {
                            scope.launch {
                                exporting = true
                                try {
                                    exportFile = repo.exportToCacheFile(orbitaId, OrbitaRepository.currentAuthorName(context))
                                } catch (e: Exception) {
                                    Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
                                }
                                exporting = false
                            }
                        }
                    ) {
                        if (exporting) CircularProgressIndicator(Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Share, stringResource(R.string.orbita_export), tint = Color.White)
                    }
                }
            }

            // --- Área de nodos, con margen fijo de 15dp en los 4 lados ---
            Box(Modifier.weight(1f).fillMaxWidth().padding(15.dp)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()
                    val density = LocalDensity.current.density

                    // Capa de toque en espacio vacío (los nodos, al estar encima, consumen sus toques).
                    Box(
                        Modifier.fillMaxSize().pointerInput(editable, wPx, hPx) {
                            if (!editable) return@pointerInput
                            detectTapGestures { off ->
                                val sz = 56f * density
                                tapFraction = Offset(
                                    ((off.x - sz / 2f) / (wPx - sz).coerceAtLeast(1f)).coerceIn(0f, 1f),
                                    ((off.y - sz / 2f) / (hPx - sz).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                )
                                showAsk = true
                            }
                        }
                    )

                    // Constelación: líneas punteadas entre nodos cercanos (máx. 3 por nodo).
                    Canvas(Modifier.fillMaxSize()) {
                        // Anillos orbitales tenues al centro: dan sensación de órbita.
                        val ringDash = PathEffect.dashPathEffect(floatArrayOf(4f * density, 14f * density))
                        for (i in 1..3) {
                            val rx = size.width * 0.17f * i
                            val ry = size.height * 0.12f * i
                            drawOval(
                                color = accent.copy(alpha = 0.07f),
                                topLeft = Offset(size.width / 2f - rx, size.height / 2f - ry),
                                size = Size(rx * 2f, ry * 2f),
                                style = Stroke(width = 1f * density, pathEffect = ringDash)
                            )
                        }
                        val items = songs.mapNotNull { s ->
                            val p = live[s.id] ?: return@mapNotNull null
                            val sz = nodeSizeDp(s.id) * density
                            Triple(s.id, Offset(p.x * (size.width - sz) + sz / 2f, p.y * (size.height - sz) + sz / 2f), sz)
                        }
                        val maxDist = LINK_MAX_DISTANCE_FRACTION * minOf(size.width, size.height)
                        val degree = HashMap<Long, Int>()
                        val drawn = HashSet<Pair<Long, Long>>()
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f * density, 10f * density))
                        for (a in items) {
                            val near = items.asSequence()
                                .filter { it.first != a.first }
                                .map { it to hypot(it.second.x - a.second.x, it.second.y - a.second.y) }
                                .filter { it.second <= maxDist }
                                .sortedBy { it.second }
                                .take(LINK_NEAREST_TRIED)
                            for ((b, _) in near) {
                                val key = if (a.first < b.first) a.first to b.first else b.first to a.first
                                if (key in drawn) continue
                                if ((degree[a.first] ?: 0) >= LINK_MAX_PER_NODE || (degree[b.first] ?: 0) >= LINK_MAX_PER_NODE) continue
                                drawn.add(key)
                                degree[a.first] = (degree[a.first] ?: 0) + 1
                                degree[b.first] = (degree[b.first] ?: 0) + 1
                                drawLine(
                                    color = accent.copy(alpha = 0.22f),
                                    start = a.second, end = b.second,
                                    strokeWidth = 1f * density,
                                    pathEffect = dash
                                )
                            }
                        }
                    }

                    // Nodos
                    songs.forEach { s ->
                        key(s.id) {
                            val sizeDp = nodeSizeDp(s.id)
                            val sizePx = sizeDp * density
                            Box(
                                Modifier
                                    .offset {
                                        val p = live[s.id] ?: Offset(s.posX, s.posY)
                                        IntOffset(
                                            (p.x * (wPx - sizePx).coerceAtLeast(0f)).roundToInt(),
                                            (p.y * (hPx - sizePx).coerceAtLeast(0f)).roundToInt()
                                        )
                                    }
                                    .graphicsLayer {
                                        translationY = sin(floatT + s.id * 1.3f) * 3.5f * density
                                    }
                                    .pointerInput(s.id) {
                                        detectTapGestures(
                                            onTap = { selectedNodeId = s.id },
                                            onLongPress = { selectedNodeId = s.id }
                                        )
                                    }
                                    .pointerInput(s.id, wPx, hPx, editable) {
                                        if (!editable) return@pointerInput
                                        val commit = {
                                            live[s.id]?.let { p -> scope.launch { repo.movePos(s.id, p.x, p.y) } }
                                            Unit
                                        }
                                        detectDragGestures(onDragEnd = { commit() }, onDragCancel = { commit() }) { change, drag ->
                                            change.consume()
                                            val spanX = (wPx - sizePx).coerceAtLeast(1f)
                                            val spanY = (hPx - sizePx).coerceAtLeast(1f)
                                            val cur = live[s.id] ?: Offset(s.posX, s.posY)
                                            live[s.id] = Offset(
                                                (cur.x + drag.x / spanX).coerceIn(0f, 1f),
                                                (cur.y + drag.y / spanY).coerceIn(0f, 1f)
                                            )
                                        }
                                    }
                            ) {
                                OrbitaNodeView(s, sizeDp, accent, repo)
                            }
                        }
                    }
                }

                if (songs.isEmpty() && editable) {
                    Text(
                        stringResource(R.string.orbita_empty_hint),
                        color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }
            }
        }

        if (editable) {
            FloatingActionButton(
                onClick = { tapFraction = null; showPicker = true },
                containerColor = accent,
                contentColor = Color(0xFF03130A),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 15.dp, bottom = 15.dp)
            ) { Icon(Icons.Filled.Add, stringResource(R.string.orbita_add_fab)) }
        }
    }

    // --- "¿Añadir canción aquí?" ---
    if (showAsk) {
        ModalBottomSheet(onDismissRequest = { showAsk = false }, containerColor = OrbitaPanel) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.orbita_add_here), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showAsk = false; showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF03130A))
                ) { Text(stringResource(R.string.orbita_pick_library)) }
            }
        }
    }

    if (showPicker) {
        OrbitaSongPickerDialog(
            takenSongIds = songs.mapNotNull { it.songId }.toSet(),
            accent = accent,
            onPick = { showPicker = false; addSong(it) },
            onDismiss = { showPicker = false; tapFraction = null }
        )
    }

    // --- Popup del nodo ---
    val node = songs.firstOrNull { it.id == selectedNodeId }
    if (node != null) {
        OrbitaNodeSheet(
            node = node,
            editable = editable,
            accent = accent,
            repo = repo,
            onDismiss = { selectedNodeId = null },
            onNoteSaved = { id, nota ->
                scope.launch { repo.setNota(id, nota); songs = songs.map { if (it.id == id) it.copy(nota = nota.trim()) else it } }
            },
            onRemove = { id ->
                scope.launch { repo.removeSong(id); selectedNodeId = null; reload++ }
            },
            onPlay = { selectedNodeId = null; onPlaySong(it) }
        )
    }

    // --- Exportación lista: guardar o compartir ---
    val file = exportFile
    if (file != null) {
        ModalBottomSheet(onDismissRequest = { exportFile = null }, containerColor = OrbitaPanel) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.orbita_export_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(file.name, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { saveLauncher.launch(file.name) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF03130A))
                ) {
                    Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.orbita_export_save))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        } catch (e: Exception) {
                            Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.orbita_export_share))
                }
            }
        }
    }
}

/** Busca un hueco libre: de varios candidatos al azar, el más lejos de los nodos existentes. */
private fun pickFreeSpot(existing: List<Offset>): Pair<Float, Float> {
    val rnd = Random(System.nanoTime())
    var best = Offset(0.5f, 0.5f)
    var bestScore = -1f
    repeat(40) {
        val c = Offset(0.05f + rnd.nextFloat() * 0.9f, 0.05f + rnd.nextFloat() * 0.9f)
        val score = existing.minOfOrNull { hypot(it.x - c.x, it.y - c.y) } ?: 1f
        if (score > bestScore) { bestScore = score; best = c }
    }
    return best.x to best.y
}

// ---------------------------------------------------------------------------------------------
// Nodo (círculo con carátula, borde, glow y puntito de nota)
// ---------------------------------------------------------------------------------------------

@Composable
private fun OrbitaNodeView(s: OrbitaSongEntity, sizeDp: Int, accent: Color, repo: OrbitaRepository) {
    val context = LocalContext.current
    val tint = remember(s.id) { nodeColor(s.id) }
    val local = if (s.caratula == null) {
        val lib = SongLibraryHolder.songs // leerlo aquí suscribe a cambios de la biblioteca
        remember(s.id, lib) { repo.findLocalSong(s) }
    } else null
    val coverBmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, s.caratula) {
        value = s.caratula?.let { b64 ->
            withContext(Dispatchers.IO) {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (e: Exception) { null }
            }
        }
    }
    val knownNoCover = remember(local?.path) {
        local?.path?.let { ArtworkCacheRepository(context).get(it) == ArtworkSource.NONE } ?: false
    }

    Box(
        Modifier
            .size(sizeDp.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.40f), Color.Transparent),
                        center = center, radius = size.minDimension * 0.95f
                    ),
                    radius = size.minDimension * 0.95f
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.22f))
                .border(1.5.dp, tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val bmp = coverBmp
            when {
                bmp != null -> Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                local != null && !knownNoCover -> AlbumArt(
                    albumArtUri = local.albumArtUri,
                    size = sizeDp.dp,
                    cornerRadius = 0.dp,
                    filePath = local.path,
                    isCustomCover = local.hasCustomCover
                )
                else -> Text(
                    s.titulo.trim().firstOrNull()?.uppercase() ?: "♪",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = (sizeDp * 0.4f).sp
                )
            }
        }
        if (s.nota.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent), radius = size.minDimension * 1.8f),
                            radius = size.minDimension * 1.8f
                        )
                    }
                    .background(accent, CircleShape)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Popup del nodo
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrbitaNodeSheet(
    node: OrbitaSongEntity,
    editable: Boolean,
    accent: Color,
    repo: OrbitaRepository,
    onDismiss: () -> Unit,
    onNoteSaved: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    onPlay: (Song) -> Unit
) {
    val local = remember(node.id) { repo.findLocalSong(node) }
    var editing by remember(node.id) { mutableStateOf(false) }
    var draft by remember(node.id, node.nota) { mutableStateOf(node.nota) }
    var confirmRemove by remember { mutableStateOf(false) }
    val tint = remember(node.id) { nodeColor(node.id) }
    val coverBmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, node.caratula) {
        value = node.caratula?.let { b64 ->
            withContext(Dispatchers.IO) {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (e: Exception) { null }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = OrbitaPanel) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(180.dp).clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.2f))
                    .border(1.5.dp, tint, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = coverBmp
                when {
                    bmp != null -> Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    local != null -> AlbumArt(
                        albumArtUri = local.albumArtUri, size = 180.dp, cornerRadius = 0.dp,
                        filePath = local.path, isCustomCover = local.hasCustomCover
                    )
                    else -> Text(node.titulo.trim().firstOrNull()?.uppercase() ?: "♪", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(node.titulo, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (node.artista.isNotBlank()) Text(node.artista, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (node.fechaAgregada > 0) {
                Text(
                    stringResource(R.string.orbita_added_on, dateFmt.format(Date(node.fechaAgregada))),
                    color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(16.dp))

            // Nota personal
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.orbita_note_label), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (editable && !editing) {
                        IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.orbita_edit), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (editing) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it.take(280) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.orbita_note_hint)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = 0.4f), cursorColor = accent
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { draft = node.nota; editing = false }) { Text(stringResource(R.string.orbita_cancel)) }
                        TextButton(onClick = { onNoteSaved(node.id, draft); editing = false }) {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.orbita_save))
                        }
                    }
                } else {
                    Text(
                        node.nota.ifBlank { if (editable) stringResource(R.string.orbita_note_empty) else "—" },
                        color = Color.White.copy(alpha = if (node.nota.isBlank()) 0.4f else 0.9f),
                        fontSize = 14.sp,
                        modifier = Modifier.then(if (editable) Modifier.clickable { editing = true } else Modifier)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { local?.let(onPlay) },
                enabled = local != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF03130A))
            ) {
                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.orbita_play))
            }
            if (local == null) {
                Text(stringResource(R.string.orbita_not_in_library), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (editable) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.orbita_remove))
                }
                Text(stringResource(R.string.orbita_remove_note), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.orbita_remove)) },
            text = { Text(stringResource(R.string.orbita_remove_note)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove(node.id) }) { Text(stringResource(R.string.orbita_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.orbita_cancel)) } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Selector de canciones locales (basado en AddToPlaylistDialog: AlertDialog + lista con carátula)
// ---------------------------------------------------------------------------------------------

@Composable
private fun OrbitaSongPickerDialog(
    takenSongIds: Set<Long>,
    accent: Color,
    onPick: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val trashedIds = remember {
        try { TrashRepository(context).getTrash().map { it.song.id }.toSet() } catch (e: Exception) { emptySet() }
    }
    val all = SongLibraryHolder.songs
    val filtered = remember(all, query, trashedIds) {
        val q = query.trim()
        all.asSequence()
            .filter { it.id !in trashedIds }
            .filter { q.isEmpty() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
            .take(300).toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.orbita_picker_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.orbita_search_hint)) }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        val taken = song.id in takenSongIds
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !taken) { onPick(song) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp))) {
                                AlbumArt(
                                    albumArtUri = song.albumArtUri, size = 40.dp, cornerRadius = 8.dp,
                                    filePath = song.path, isCustomCover = song.hasCustomCover
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (taken) 0.4f else 1f))
                                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (taken) 0.3f else 0.6f))
                            }
                            if (taken) Icon(Icons.Filled.Check, null, tint = accent)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.orbita_cancel)) } }
    )
}

// ---------------------------------------------------------------------------------------------
// Preview de importación
// ---------------------------------------------------------------------------------------------

@Composable
private fun OrbitaImportDialog(
    preview: OrbitaImportPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val thumbs by produceState<List<androidx.compose.ui.graphics.ImageBitmap>>(emptyList(), preview) {
        value = withContext(Dispatchers.IO) {
            preview.canciones.asSequence().mapNotNull { it.caratulaBase64 }.take(8).mapNotNull { b64 ->
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (e: Exception) { null }
            }.toList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.orbita_import_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.orbita_import_summary,
                        preview.autorNombre.ifBlank { stringResource(R.string.orbita_import_anonymous) },
                        preview.canciones.size
                    )
                )
                if (thumbs.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        thumbs.forEach { Image(it, null, Modifier.size(34.dp).clip(CircleShape), contentScale = ContentScale.Crop) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.orbita_import_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.orbita_cancel)) } }
    )
}