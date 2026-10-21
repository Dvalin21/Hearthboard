package com.openlight.cal.ui.screens.photos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    token: String = ""
) {
    val scope = rememberCoroutineScope()
    var memories by remember { mutableStateOf<List<MemoryInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val client = remember { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build() }

    fun loadMemories() {
        scope.launch {
            loading = true
            try {
                val req = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/api/memories")
                    .header("Authorization", "Bearer $token")
                    .get().build()
                val resp = client.newCall(req).execute()
                val json = JSONArray(resp.body!!.string())
                memories = (0 until json.length()).map { i ->
                    val o = json.getJSONObject(i)
                    val ids = o.getJSONArray("photo_ids")
                    MemoryInfo(
                        id = o.getString("id"),
                        kind = o.getString("kind"),
                        title = o.optString("title", "On this day"),
                        photoIds = (0 until ids.length()).map { ids.getString(it) }
                    )
                }
            } catch (e: Exception) {
                status = "Failed: ${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(serverUrl, token) {
        if (serverUrl.isNotBlank() && token.isNotBlank()) loadMemories()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Memories") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (memories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(12.dp))
                        Text("No memories yet", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Memories appear when photos are older than a year",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(memories) { memory ->
                        Card(modifier = Modifier.width(280.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(memory.title, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                Text("${memory.photoIds.size} photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text(memory.kind, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

data class MemoryInfo(
    val id: String,
    val kind: String,
    val title: String,
    val photoIds: List<String>
)
