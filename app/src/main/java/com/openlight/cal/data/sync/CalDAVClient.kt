package com.openlight.cal.data.sync

import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal CalDAV client using OkHttp.
 * Implements RFC 4791 (CalDAV) using PROPFIND / REPORT / GET / PUT / DELETE.
 * No tracking, no analytics, no third-party SDKs.
 */
class CalDAVClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    companion object {
        private const val TAG = "CalDAVClient"
        val ICAL_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        val XML_MEDIA_TYPE  = "application/xml; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val creds = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            val req   = chain.request().newBuilder()
                .header("Authorization", "Basic $creds")
                .header("User-Agent", "OpenLight/1.0 (CalDAV)")
                .build()
            chain.proceed(req)
        }
        .build()

    // ─────────────────────────────────────────────────────────
    // Discover home-set & calendar collection paths
    // ─────────────────────────────────────────────────────────
    data class CalendarInfo(
        val path: String,
        val displayName: String,
        val ctag: String,
        val supportsVTODO: Boolean
    )

    suspend fun discoverCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        try {
            // Step 1: Find calendar-home-set via PROPFIND on principal
            val principalPath = findPrincipalPath()
            val homeSet = findCalendarHomeSet(principalPath)

            // Step 2: List calendars under home-set
            val listXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:CS="http://calendarserver.org/ns/">
                  <D:prop>
                    <D:displayname/>
                    <D:resourcetype/>
                    <CS:getctag/>
                    <C:supported-calendar-component-set/>
                  </D:prop>
                </D:propfind>
            """.trimIndent()

            val resp = propfind(homeSet, listXml, depth = "1")
            // Parse each calendar response
            val responseBlocks = resp.split("<D:response>").drop(1)
            for (block in responseBlocks) {
                if (!block.contains("calendar")) continue
                val path        = extractXml(block, "D:href") ?: continue
                val name        = extractXml(block, "D:displayname") ?: path.split("/").lastOrNull { it.isNotBlank() } ?: path
                val ctag        = extractXml(block, "CS:getctag") ?: extractXml(block, "getctag") ?: ""
                val supportsVTODO = block.contains("VTODO")
                if (block.contains("calendar") && !block.contains("inbox") && !block.contains("outbox")) {
                    calendars.add(CalendarInfo(
                        path         = path,
                        displayName  = name,
                        ctag         = ctag,
                        supportsVTODO= supportsVTODO
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed: ${e.message}")
        }
        return calendars
    }

    private fun findPrincipalPath(): String {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:current-user-principal/></D:prop>
            </D:propfind>
        """.trimIndent()
        val resp = propfind(serverUrl, xml, depth = "0")
        return extractXml(resp, "D:href") ?: "/"
    }

    private fun findCalendarHomeSet(principalPath: String): String {
        val base = buildUrl(principalPath)
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:prop><C:calendar-home-set/></D:prop>
            </D:propfind>
        """.trimIndent()
        val resp = propfind(base, xml, depth = "0")
        return extractXml(resp, "D:href") ?: principalPath
    }

    // ─────────────────────────────────────────────────────────
    // Sync: get ETags for all items in a calendar collection
    // ─────────────────────────────────────────────────────────
    data class ETagEntry(val href: String, val etag: String)

    fun getETagList(calendarPath: String): List<ETagEntry> {
        val url = buildUrl(calendarPath)
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/></D:prop>
            </D:propfind>
        """.trimIndent()
        return try {
            val resp = propfind(url, xml, depth = "1")
            val blocks = resp.split("<D:response>").drop(1)
            blocks.mapNotNull { block ->
                val href  = extractXml(block, "D:href") ?: return@mapNotNull null
                val etag  = extractXml(block, "D:getetag")?.trim('"') ?: ""
                if (href.endsWith(".ics")) ETagEntry(href, etag) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getETagList failed: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Fetch individual .ics resource
    // ─────────────────────────────────────────────────────────
    data class IcsResource(val href: String, val etag: String, val ical: String)

    fun fetchIcs(href: String): IcsResource? {
        val url = buildUrl(href)
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val etag = resp.header("ETag")?.trim('"') ?: ""
                    val body = resp.body?.string() ?: ""
                    IcsResource(href, etag, body)
                } else null
            }
        } catch (e: IOException) {
            Log.e(TAG, "fetchIcs failed for $href: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────
    // Multi-get: fetch multiple .ics at once (RFC 4791 §7.9)
    // ─────────────────────────────────────────────────────────
    fun multiGet(calendarPath: String, hrefs: List<String>): List<IcsResource> {
        if (hrefs.isEmpty()) return emptyList()
        val url = buildUrl(calendarPath)
        val hrefLines = hrefs.joinToString("") { "  <D:href>$it</D:href>\n" }
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <C:calendar-multiget xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:prop>
                <D:getetag/>
                <C:calendar-data/>
              </D:prop>
              $hrefLines
            </C:calendar-multiget>
        """.trimIndent()

        return try {
            val req = Request.Builder()
                .url(url)
                .method("REPORT", xml.toRequestBody(XML_MEDIA_TYPE))
                .header("Depth", "1")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return emptyList()
                val blocks = body.split("<D:response>").drop(1)
                blocks.mapNotNull { block ->
                    val href  = extractXml(block, "D:href") ?: return@mapNotNull null
                    val etag  = extractXml(block, "D:getetag")?.trim('"') ?: ""
                    val ical  = extractXml(block, "C:calendar-data") ?: return@mapNotNull null
                    IcsResource(href, etag, ical)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "multiGet failed: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────
    // PUT (create / update) an .ics resource
    // ─────────────────────────────────────────────────────────
    /** Returns new ETag on success */
    fun putIcs(href: String, ical: String, etag: String? = null): String? {
        val url = buildUrl(href)
        val builder = Request.Builder()
            .url(url)
            .put(ical.toRequestBody(ICAL_MEDIA_TYPE))
        if (etag != null) builder.header("If-Match", "\"$etag\"")
        else              builder.header("If-None-Match", "*")  // create only

        return try {
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.header("ETag")?.trim('"') ?: ""
                else {
                    Log.e(TAG, "PUT failed ${resp.code}: ${resp.message}")
                    null
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "putIcs failed: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────
    // DELETE an .ics resource
    // ─────────────────────────────────────────────────────────
    fun deleteIcs(href: String, etag: String? = null): Boolean {
        val url = buildUrl(href)
        val builder = Request.Builder().url(url).delete()
        if (etag != null) builder.header("If-Match", "\"$etag\"")
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                resp.isSuccessful || resp.code == 404
            }
        } catch (e: IOException) {
            Log.e(TAG, "deleteIcs failed: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────
    // Get current CTag (change tag) for a calendar
    // ─────────────────────────────────────────────────────────
    fun getCTag(calendarPath: String): String {
        val url = buildUrl(calendarPath)
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:CS="http://calendarserver.org/ns/">
              <D:prop><CS:getctag/></D:prop>
            </D:propfind>
        """.trimIndent()
        return try {
            val resp = propfind(url, xml, depth = "0")
            extractXml(resp, "CS:getctag") ?: extractXml(resp, "getctag") ?: ""
        } catch (e: Exception) { "" }
    }

    // ─────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────
    private fun propfind(url: String, body: String, depth: String = "1"): String {
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${resp.message}")
            }
            return resp.body?.string() ?: ""
        }
    }

    private fun buildUrl(path: String): String {
        // If it's already a full URL, use it directly
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        
        // For relative paths, combine with server base
        // But handle SOGo-specific path structure
        val base = serverUrl.trimEnd('/')
        val p = path.trimStart('/')
        
        // Special handling: if path starts with SOGo/dav, prepend whole server path
        if (p.startsWith("SOGo/dav") || p.startsWith("SOGo/")) {
            // User provided full calendar URL but we stripped it to base
            // Need to reconstruct the proper path
            return "$base/$p"
        }
        
        return "$base/$p"
    }

    private fun extractXml(xml: String, tag: String): String? {
        // Handle both prefixed and local tags
        // Examples: <D:href>/path</D:href>, <href>/path</href>
        val simpleTag = tag.substringAfter(':')  // "D:href" -> "href"
        
        // Try multiple patterns for robustness
        val patterns = mutableListOf<Regex>()
        
        // Pattern 1: <tag>value</tag> (with potential attributes)
        patterns.add(Regex("<$tag[^>]*>([^<]*)</$tag>", RegexOption.IGNORE_CASE))
        
        // Pattern 2: <simpleTag>value</simpleTag>
        if (simpleTag != tag) {
            patterns.add(Regex("<$simpleTag[^>]*>([^<]*)</$simpleTag>", RegexOption.IGNORE_CASE))
        }
        
        for (p in patterns) {
            val m = p.find(xml)
            if (m != null && m.groupValues[1].isNotBlank()) {
                return m.groupValues[1].trim()
            }
        }
        
        // Fallback: find any tag containing the simple name with any prefix
        val fallbackPattern = Regex("<[^:]*:$simpleTag[^>]*>([^<]+)</[^:]*:$simpleTag>", RegexOption.IGNORE_CASE)
        val fallback = fallbackPattern.find(xml)
        return fallback?.groupValues?.getOrNull(1)?.trim()
    }
}
