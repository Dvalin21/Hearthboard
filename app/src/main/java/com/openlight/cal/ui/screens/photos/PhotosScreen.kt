package com.openlight.cal.ui.screens.photos

import android.content.Context
import android.graphics.ImageDecoder
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────
// PhotosScreen — family photo gallery using system PhotoPicker
// Uses PhotoPicker API (Android 13+) — no storage permissions
// needed. Falls back to Intent.ACTION_PICK on older versions.
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var viewerUri by remember { mutableStateOf<Uri?>(null) }

    // PhotoPicker launcher — zero permissions on API 33+
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            photoUris = (photoUris + uris).distinct()
        }
    }

    // Full-screen viewer
    if (viewerUri != null) {
        FullScreenViewer(
            uri       = viewerUri!!,
            onClose   = { viewerUri = null },
            onDelete  = {
                photoUris = photoUris.filter { it != viewerUri }
                viewerUri = null
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
        if (photoUris.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Family Photos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pick photos from your device\nto display them here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 48.dp),
                        lineHeight = 20.sp
                    )
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
            // Photo grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(photoUris, key = { it.toString() }) { uri ->
                    PhotoThumbnail(
                        uri      = uri,
                        onClick  = { viewerUri = uri },
                        modifier = Modifier.aspectRatio(1f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PhotoThumbnail — loads a single photo from content URI
// ─────────────────────────────────────────────────────────────
@Composable
private fun PhotoThumbnail(
    uri: Uri,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = loadThumbnail(context, uri, 300)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap       = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// FullScreenViewer — overlay that shows a photo at full size
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenViewer(
    uri: Uri,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = loadFullImage(context, uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap       = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clickable(onClick = {}) // prevent close when tapping image
            )
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Close button (top-left)
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                    RoundedCornerShape(50)
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Delete button (top-right)
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.errorContainer,
                    RoundedCornerShape(50)
                )
        ) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Remove photo",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Thumbnail loader — fast thumbnail via ContentResolver
// ─────────────────────────────────────────────────────────────
// minSdk 31 ensures ImageDecoder is always available.
private suspend fun loadThumbnail(context: Context, uri: Uri, size: Int): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setTargetSize(size, size)
            }
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Full image loader — higher quality for viewer
private suspend fun loadFullImage(context: Context, uri: Uri): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
