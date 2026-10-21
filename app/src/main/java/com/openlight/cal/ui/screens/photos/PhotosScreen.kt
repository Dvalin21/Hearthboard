package com.openlight.cal.ui.screens.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.Photo
import com.openlight.cal.data.repository.PhotoRepository
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// PhotosScreen — persisted family photo gallery
// Photos are copied to app-private storage and tracked in Room.
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PhotoRepository(context) }
    val photos by repo.getAllFlow().collectAsState(initial = emptyList())
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    try { repo.importPhoto(uri) } catch (_: Exception) { }
                }
            }
        }
    }

    if (viewerPhoto != null) {
        FullScreenViewer(
            photo = viewerPhoto!!,
            onClose = { viewerPhoto = null },
            onDelete = {
                scope.launch { repo.delete(viewerPhoto!!); viewerPhoto = null }
            }
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                actions = {
                    IconButton(onClick = {
                        picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        ))
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, "Add photos")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.height(16.dp))
                    Text("Family Photos", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Pick photos from your device\nto display them here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 48.dp), lineHeight = 20.sp)
                    Spacer(Modifier.height(24.dp))
                    FilledTonalButton(onClick = {
                        picker.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        ))
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse photos")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        onClick = { viewerPhoto = photo },
                        modifier = Modifier.aspectRatio(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: Photo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, photo.localPath) {
        value = loadThumbnail(photo.localPath, 300)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenViewer(photo: Photo, onClose: () -> Unit, onDelete: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, photo.localPath) {
        value = loadFullImage(photo.localPath)
    }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp).clickable(onClick = {}))
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), RoundedCornerShape(50))) {
            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50))) {
            Icon(Icons.Default.DeleteOutline, "Remove photo", tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

private suspend fun loadThumbnail(path: String, size: Int): ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = false; inSampleSize = 2 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        } catch (_: Exception) { null }
    }

private suspend fun loadFullImage(path: String): ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (_: Exception) { null }
    }
KOTLIN
echo done