package com.openlight.cal.ui.screens.photos

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.Photo
import com.openlight.cal.data.repository.PhotoRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────
// PhotosScreen — persisted family photo gallery with date grouping
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PhotoRepository(context) }
    val photos by repo.getAllFlow().collectAsState(initial = emptyList())
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }
    var filter by remember { mutableStateOf("all") } // all, favorites, archived, trashed
    var searchQuery by remember { mutableStateOf("") }

    val filteredPhotos = remember(photos, filter, searchQuery) {
        val base = when (filter) {
            "favorites" -> photos.filter { it.isFavorite && !it.isTrashed }
            "archived" -> photos.filter { it.isArchived && !it.isTrashed }
            "trashed" -> photos.filter { it.isTrashed }
            else -> photos.filter { !it.isTrashed && !it.isArchived }
        }
        if (searchQuery.isBlank()) base
        else base.filter { it.caption.contains(searchQuery, ignoreCase = true) || it.localPath.contains(searchQuery, ignoreCase = true) }
    }

    // Group by month/year
    val grouped = remember(filteredPhotos) {
        filteredPhotos.groupBy { photo ->
            val ts = if (photo.takenAtMs > 0) photo.takenAtMs else photo.importedAtMs
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    try {
                        val photo = repo.importPhoto(uri)

                    } catch (e: Exception) {
                        android.util.Log.e("PhotosScreen", "Failed to import photo: ${e.message}", e)
                    }
                }
            }
        }
    }

    if (viewerPhoto != null) {
        PhotoViewer(
            photo = viewerPhoto!!,
            onClose = { viewerPhoto = null },
            onDelete = { scope.launch { repo.delete(viewerPhoto!!); viewerPhoto = null } },
            onFavorite = { scope.launch { repo.setFavorite(viewerPhoto!!.id, !viewerPhoto!!.isFavorite) } },
            onArchive = { scope.launch { repo.setArchived(viewerPhoto!!.id, !viewerPhoto!!.isArchived); viewerPhoto = null } },
            onTrash = { scope.launch { repo.setTrashed(viewerPhoto!!.id, !viewerPhoto!!.isTrashed); viewerPhoto = null } }
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Photos (${photos.size})") },
                actions = {
                    IconButton(onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            com.openlight.cal.data.sync.PhotoSyncWorker.syncNow(context)
                        }
                    }) {
                        Icon(Icons.Default.Sync, "Sync now")
                    }
                    IconButton(onClick = {
                        picker.launch(androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                        ))
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, "Add photos")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search photos...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = if (searchQuery.isNotBlank()) { { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) } } } else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("all" to "All", "favorites" to "Favorites", "archived" to "Archive", "trashed" to "Trash").forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (filteredPhotos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(16.dp))
                        Text(if (filter == "all") "Family Photos" else "No ${filter} photos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (filter == "all") {
                            Spacer(Modifier.height(8.dp))
                            Text("Pick photos from your device\nto display them here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 48.dp), lineHeight = 20.sp)
                            Spacer(Modifier.height(24.dp))
                            FilledTonalButton(onClick = {
                                picker.launch(androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                ))
                            }) {
                                Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Browse photos")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    grouped.forEach { (month, monthPhotos) ->
                        // Month header
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(month, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(monthPhotos, key = { it.id }) { photo ->
                            PhotoThumbnail(photo = photo, onClick = { viewerPhoto = photo },
                                modifier = Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: Photo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photo.localPath) {
        bitmap = loadThumbnail(photo.localPath, 300)
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Image, null, Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PhotoViewer — full-screen with pinch-to-zoom and actions
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoViewer(
    photo: Photo, onClose: () -> Unit, onDelete: () -> Unit,
    onFavorite: () -> Unit, onArchive: () -> Unit, onTrash: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val bitmap by produceState<ImageBitmap?>(null, photo.localPath) {
        value = loadFullImage(photo.localPath)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var showInfo by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f; offsetY = 0f
                    }
                }
            }
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    .clickable(onClick = {}))
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
        }

        // Top bar
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
            }
            Row {
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favorite", tint = if (photo.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onArchive) {
                    Icon(Icons.Default.Archive, "Archive", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onTrash) {
                    Icon(Icons.Default.DeleteOutline, "Trash", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("Photo info") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Dimensions: ${photo.width} × ${photo.height}", style = MaterialTheme.typography.bodySmall)
                        Text("Size: ${(photo.sizeBytes / 1024)} KB", style = MaterialTheme.typography.bodySmall)
                        if (photo.takenAtMs > 0) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            Text("Taken: ${sdf.format(java.util.Date(photo.takenAtMs))}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (photo.cameraMake.isNotBlank()) Text("Camera: ${photo.cameraMake} ${photo.cameraModel}", style = MaterialTheme.typography.bodySmall)
                        if (photo.locationLat != 0.0 || photo.locationLon != 0.0) {
                            Text("Location: ${photo.locationLat}, ${photo.locationLon}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Sync: ${photo.syncState.name}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } }
            )
        }

        // Info bar at bottom
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${photo.width}×${photo.height}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text("${(photo.sizeBytes / 1024)} KB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

private suspend fun loadThumbnail(path: String, size: Int): ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = false; inSampleSize = 2 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        } catch (e: Exception) {
            android.util.Log.e("PhotosScreen", "loadThumbnail error: ${e.message}", e)
            null
        }
    }

private suspend fun loadFullImage(path: String): ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (_: Exception) { null }
    }
