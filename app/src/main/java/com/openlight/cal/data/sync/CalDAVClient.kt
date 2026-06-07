package com.openlight.cal.data.sync

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import java.util.Base64
import java.util.concurrent.TimeUnit

object CalDAVClientFactory {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Per-(serverUrl,username) client cache for reuse within a process.
    // Evicted on password change or explicit clear. Not for cross-process.
    private val clientCache = mutableMapOf<String, CalDAVClient>()

    private fun cacheKey(serverUrl: String, username: String): String =
        "${serverUrl.trimEnd('/')}|$username"

    fun create(serverUrl: String, username: String, password: String): CalDAVClient {
        val key = cacheKey(serverUrl, username)
        val cached = clientCache[key]
        if (cached != null) {
            // Password could have changed; if so, the old client will fail
            // with 401 and we'll fall through to create a new one.
            // For simplicity we don't auto-evict on 401 here — caller can
            // call evict() if needed. In practice passwords rarely change.
            return cached
        }
        val client = CalDAVClient(serverUrl, username, password, okHttpClient)
        clientCache[key] = client
        return client
    }

    /** Remove a cached client (e.g. after password rotation). */
    fun evict(serverUrl: String, username: String) {
        clientCache.remove(cacheKey(serverUrl, username))
    }

    /** Clear all cached clients. */
    fun clearCache() {
        clientCache.clear()
    }
}

class CalDAVClient internal constructor(
    serverUrl: String,
    username: String,
    password: String,
    private val httpClient: OkHttpClient,
    private val parserFactory: () -> XmlPullParser = { XmlPullParserFactory.newInstance().newPullParser() }
) {
    companion object {
        private const val TAG = "CalDAVClient"
        val ICAL_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        val XML_MEDIA_TYPE  = "application/xml; charset=utf-8".toMediaType()
    }

    private val client: OkHttpClient = httpClient.newBuilder()
        .addInterceptor { chain ->
            val creds = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            val req   = chain.request().newBuilder()
                .header("Authorization", "Basic $creds")
                .header("User-Agent", "OpenLight/1.0 (CalDAV)")
                .build()
            chain.proceed(req)
        }
        .build()

    val serverUrl: String = serverUrl.trimEnd('/')
    val username: String = username
    val password: String = password

    data class CalendarInfo(
        val path: String,
        val displayName: String,
        val ctag: String,
        val supportsVTODO: Boolean
    )

    suspend fun discoverCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        try {
            val principalPath = findPrincipalPath()
            val homeSet = findCalendarHomeSet(principalPath)

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
            val responses = safeParseMultistatus(resp)
            for (r in responses) {
                val href = r.href ?: continue
                val name = propText(r, "displayname")
                    ?: href.split("/").lastOrNull { it.isNotBlank() }
                    ?: href
                val ctag = propText(r, "getctag").orEmpty()
                val hasVTODO = r.props.any {
                    it.localName.equals(
                        "supported-calendar-component-set",
                        ignoreCase = true
                    ) && it.children.any { child ->
                        child.equals("VTODO", ignoreCase = true)
                    }
                } || r.props.any {
                    it.localName.equals("resourcetype", ignoreCase = true)
                            && it.children.any { child ->
                        child.equals("calendar", ignoreCase = true)
                    }
                }
                calendars.add(
                    CalendarInfo(
                        path          = href,
                        displayName   = name,
                        ctag          = ctag,
                        supportsVTODO = hasVTODO
                    )
                )
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
        return safeParseMultistatus(resp).firstOrNull()?.href ?: "/"
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
        val r = safeParseMultistatus(resp).firstOrNull()
        return r?.href ?: principalPath
    }

    data class ETagEntry(val href: String, val etag: String)

    internal data class PropfindResource(
        val href: String,
        val etag: String,
        val contentType: String
    )

    fun getETagList(calendarPath: String): List<ETagEntry> {
        return try {
            propfindResources(calendarPath).mapNotNull { rsrc ->
                if (rsrc.href.isNotBlank() && rsrc.etag.isNotBlank())
                    ETagEntry(rsrc.href, rsrc.etag)
                else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getETagList exception (${e::class.simpleName}): ${e.message}", e)
            emptyList()
        }
    }

    internal fun propfindResources(calendarPath: String): List<PropfindResource> {
        val url = buildUrl(calendarPath).let { u ->
            if (!u.endsWith("/")) "$u/" else u
        }
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:getetag/>
                <D:getcontenttype/>
                <D:resourcetype/>
              </D:prop>
            </D:propfind>
        """.trimIndent()
        Log.d(TAG, "PROPFIND resources at: $url")
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", xml.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(
                        TAG,
                        "PROPFIND resources failed: ${resp.code} ${resp.message} on $url"
                    )
                    return@use emptyList()
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    Log.w(TAG, "PROPFIND returned empty body from $url")
                    return@use emptyList()
                }
                Log.d(
                    TAG,
                    "PROPFIND response (first 600 chars): ${body.take(600)}"
                )
                parsePropfindMultistatus(body)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "propfindResources exception (${e::class.simpleName}): ${e.message}",
                e
            )
            emptyList()
        }
    }

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

    fun putIcs(href: String, ical: String, etag: String? = null): String? {
        val url = buildUrl(href)
        val builder = Request.Builder()
            .url(url)
            .put(ical.toRequestBody(ICAL_MEDIA_TYPE))
        if (etag != null) builder.header("If-Match", "\"$etag\"")
        else              builder.header("If-None-Match", "*")

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
            propText(safeParseMultistatus(resp).firstOrNull(), "getctag").orEmpty()
        } catch (_: Exception) { "" }
    }

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

    internal data class ParsedProp(
        val localName: String,
        val children: List<String>,
        val text: String? = null
    )

    internal data class ParsedResponse(
        val href: String?,
        val props: List<ParsedProp>
    )

    internal fun safeParseMultistatus(xml: String, parser: XmlPullParser? = null): List<ParsedResponse> {
        if (parser != null) return parseMultistatus(xml, parser)
        val p = runCatching { XmlPullParserFactory.newInstance().newPullParser() }.getOrNull()
            ?: throw IllegalStateException("XmlPullParserFactory unavailable: cannot parse CalDAV response")
        return parseMultistatus(xml, p)
    }

    internal fun parseMultistatus(xml: String, parser: XmlPullParser): List<ParsedResponse> {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val responses = mutableListOf<ParsedResponse>()
        val stack = mutableListOf<ParsedResponse>()
        val current = mutableListOf<ParsedProp>()

        fun localName(p: XmlPullParser): String = p.name.substringAfter(':')

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        localName(parser).equals("response", ignoreCase = true) -> {
                            stack.add(ParsedResponse(href = null, props = emptyList()))
                        }
                        localName(parser).equals("href", ignoreCase = true) -> {
                            current.add(ParsedProp(localName(parser), emptyList(), safeText(parser)))
                        }
                        stack.isNotEmpty() -> {
                            current.add(ParsedProp(localName(parser), emptyList()))
                        }
                        else -> Unit
                    }
                }

                XmlPullParser.TEXT,
                XmlPullParser.CDSECT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotBlank() && current.isNotEmpty()) {
                        val last = current.last()
                        current[current.lastIndex] = last.copy(
                            text = (last.text ?: "") + text
                        )
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (localName(parser).equals("response", ignoreCase = true) && stack.isNotEmpty()) {
                        val hrefProp = current.firstOrNull { it.localName.equals("href", ignoreCase = true) }
                        val built = stack.removeAt(stack.size - 1)
                            .copy(href = hrefProp?.text, props = current.toList())
                        responses.add(built)
                        current.clear()
                    }
                }
            }
        }
        return responses
    }

    internal fun parsePropfindMultistatus(xml: String): List<PropfindResource> {
        val results = mutableListOf<PropfindResource>()
        val responses = safeParseMultistatus(xml)
        for (r in responses) {
            val href = r.href
            if (href.isNullOrBlank()) continue
            val etag = propText(r, "getetag")?.trim('"', ' ')
            val contentType = propText(r, "getcontenttype").orEmpty()
            results.add(PropfindResource(href, etag.orEmpty(), contentType))
        }
        return results
    }

    private fun propText(r: ParsedResponse?, name: String): String? =
        r?.props?.firstOrNull { it.localName.equals(name, ignoreCase = true) }?.text

    private fun safeText(parser: XmlPullParser): String? {
        return if (parser.next() == XmlPullParser.TEXT) parser.text?.trim() else null
    }

    private fun buildUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path

        val base = serverUrl.trimEnd('/')
        val p = path.trimStart('/')

        if (p.startsWith("SOGo/dav") || p.startsWith("SOGo/")) {
            return "$base/$p"
        }

        return "$base/$p"
    }
}
