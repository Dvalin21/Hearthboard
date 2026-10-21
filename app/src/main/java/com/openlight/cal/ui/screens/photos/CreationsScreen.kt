package com.openlight.cal.ui.screens.photos

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openlight.cal.data.model.Photo
import com.openlight.cal.data.repository.PhotoRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationsScreen(
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    token: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PhotoRepository(context) }
    val photos by repo.getAllFlow().collectAsState(initial = emptyList())
    val client = remember { OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build() }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var creating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var creationType by remember { mutableStateOf("collage") } // collage or gif

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Create") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Type selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = creationType == "collage", onClick = { creationType = "collage" },
                    label = { Text("Collage") }, leadingIcon = { Icon(Icons.Default.GridOn, null, Modifier.size(16.dp)) })
                FilterChip(selected = creationType == "gif", onClick = { creationType = "gif" },
                    label = { Text("GIF") }, leadingIcon = { Icon(Icons.Default.Gif, null, Modifier.size(16.dp)) })
            }

            Text("Select ${if (creationType == "collage") "2-9" else "2-20"} photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(8.dp))

            // Photo grid for selection
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 80.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(count = photos.size, key = { index -> photos[index].id }) { index ->
                    val photo = photos[index]
                    val selected = selectedIds.contains(photo.id)
                    Box(
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                            .clickable {
                                selectedIds = if (selected) selectedIds - photo.id else selectedIds + photo.id
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Thumbnail
                        val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.localPath) {
                            value = loadThumbnail(photo.localPath, 200)
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap!!, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Image, null, Modifier.size(32.dp))
                        }
                        if (selected) {
                            Box(Modifier.fillMaxSize().background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                            Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            // Create button
            Button(
                onClick = {
                    if (serverUrl.isBlank() || token.isBlank()) {
                        result = "Configure sync server in Settings first"
                        return@Button
                    }
                    val maxPhotos = if (creationType == "collage") 9 else 20
                    val minPhotos = 2
                    if (selectedIds.size < minPhotos || selectedIds.size > maxPhotos) {
                        result = "Select $minPhotos-$maxPhotos photos"
                        return@Button
                    }
                    creating = true
                    result = ""
                    scope.launch {
                        try {
                            val endpoint = if (creationType == "collage") "collage" else "gif"
                            val body = JSONObject().apply {
                                put("photo_ids", JSONArray(selectedIds.toList()))
                            }
                            val req = Request.Builder()
                                .url("${serverUrl.trimEnd('/')}/api/creations/$endpoint")
                                .header("Authorization", "Bearer $token")
                                .post(okhttp3.RequestBody.create(
                                    "application/json".toMediaType(),
                                    body.toString().toByteArray()
                                ))
                                .build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val json = JSONObject(resp.body!!.string())
                                result = "Created ${json.getString("kind")}! Hash: ${json.getString("content_hash").take(12)}..."
                                selectedIds = emptySet()
                            } else {
                                result = "Failed: ${resp.code} ${resp.body?.string()?.take(100)}"
                            }
                        } catch (e: Exception) {
                            result = "Error: ${e.message}"
                        }
                        creating = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = !creating && selectedIds.isNotEmpty()
            ) {
                if (creating) CircularProgressIndicator(Modifier.size(18.dp))
                else Text("Create ${creationType.replaceFirstChar { it.uppercase() }} (${selectedIds.size} selected)")
            }

            if (result.isNotBlank()) {
                Text(result, style = MaterialTheme.typography.bodySmall,
                    color = if (result.startsWith("Created")) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

private suspend fun loadThumbnail(path: String, size: Int): androidx.compose.ui.graphics.ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = false; inSampleSize = 4 }
            android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        } catch (_: Exception) { null }
    }
