package com.openlight.cal.ui.screens.photos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAlbumsScreen(
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    token: String = ""
) {
    val scope = rememberCoroutineScope()
    var sharedAlbums by remember { mutableStateOf<List<SharedAlbumInfo>>(emptyList()) }
    var inviteCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val client = remember { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build() }

    fun loadShared() {
        scope.launch {
            loading = true
            try {
                val req = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/api/albums/shared")
                    .header("Authorization", "Bearer $token")
                    .get().build()
                val resp = client.newCall(req).execute()
                val json = JSONArray(resp.body!!.string())
                sharedAlbums = (0 until json.length()).map { i ->
                    val o = json.getJSONObject(i)
                    SharedAlbumInfo(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        ownerId = o.getString("owner_id"),
                        permission = o.getString("permission"),
                        photoCount = o.getLong("photo_count")
                    )
                }
            } catch (e: Exception) {
                status = "Failed to load: ${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(serverUrl, token) {
        if (serverUrl.isNotBlank() && token.isNotBlank()) loadShared()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Shared Albums") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Accept invite section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    label = { Text("Invite code") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val body = JSONObject().apply { put("code", inviteCode) }
                                val req = Request.Builder()
                                    .url("${serverUrl.trimEnd('/')}/api/albums/invites/accept")
                                    .header("Authorization", "Bearer $token")
                                    .post(okhttp3.RequestBody.create(
                                        "application/json".toMediaType(),
                                        body.toString().toByteArray()
                                    ))
                                    .build()
                                val resp = client.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    status = "Joined album!"
                                    inviteCode = ""
                                    loadShared()
                                } else {
                                    status = "Failed: ${resp.code}"
                                }
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                            }
                        }
                    },
                    enabled = inviteCode.isNotBlank() && token.isNotBlank()
                ) { Text("Join") }
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = if (status.startsWith("Joined")) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (sharedAlbums.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.People, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(12.dp))
                        Text("No shared albums", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Accept an invite or share one of your albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sharedAlbums) { album ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhotoAlbum, null, Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(album.name, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text("${album.photoCount} photos • ${album.permission}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class SharedAlbumInfo(
    val id: String,
    val name: String,
    val ownerId: String,
    val permission: String,
    val photoCount: Long
)
