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

        // Step 1: Find the principal path
        val principalPath = findPrincipalPath()
        Log.d(TAG, "Principal path: $principalPath")
        if (principalPath.isBlank() || principalPath == "/") {
            Log.e(TAG, "Could not determine principal path — aborting calendar discovery")
            return calendars
        }

        // Step 2: Find the calendar home set
        val homeSet = findCalendarHomeSet(principalPath)
        Log.d(TAG, "Calendar home set: $homeSet")
        if (homeSet.isBlank()) {
            Log.e(TAG, "Calendar home set is blank — aborting")
            return calendars
        }

        // Step 3: List calendars under home set (recursively scan sub-collections)
        val homeSetUrl = buildUrl(homeSet)
        scanForCalendars(homeSetUrl, calendars)
        Log.i(TAG, "Discovered ${calendars.size} calendar(s)")
        return calendars
    }

    /**
     * PROPFIND depth="1" on [url], collecting any child resources whose
     * resourcetype includes "calendar". For any child that is a plain
     * collection but NOT a calendar, recurse into it — this handles
     * SOGo/Mailcow where shared calendars live under a `shared/`
     * sub-collection one level deeper than the home set root.
     */
    private suspend fun scanForCalendars(
        url: String,
        result: MutableList<CalendarInfo>,
        visited: MutableSet<String> = mutableSetOf()
    ) {
        val normalised = url.trimEnd('/')
        if (normalised in visited) return
        visited.add(normalised)

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

        val resp = try {
            propfind(url, listXml, depth = "1")
        } catch (e: Exception) {
            Log.d(TAG, "scanForCalendars skipped $url (${e::class.simpleName}: ${e.message})")
            return
        }

        val responses = safeParseMultistatus(resp)
        for (r in responses) {
            val href = r.href?.trimEnd('/') ?: continue
            if (href == normalised) continue // skip the collection itself
            val isCollection = r.props.any {
                it.localName.equals("resourcetype", ignoreCase = true) &&
                it.children.any { c -> c.equals("collection", ignoreCase = true) }
            }
            val isCalendar = r.props.any {
                it.localName.equals("resourcetype", ignoreCase = true) &&
                it.children.any { c -> c.equals("calendar", ignoreCase = true) }
            }
            val subUrl = buildUrl(href)

            if (isCalendar) {
                val name = propText(r, "displayname")
                    ?: href.split("/").lastOrNull { it.isNotBlank() }
                    ?: href
                val ctag = propText(r, "getctag").orEmpty()
                val hasVTODO = r.props.any {
                    it.localName.equals("supported-calendar-component-set", ignoreCase = true)
                            && it.children.any { child -> child.equals("VTODO", ignoreCase = true) }
                }
                result.add(CalendarInfo(path = href, displayName = name, ctag = ctag, supportsVTODO = hasVTODO))
            } else if (isCollection) {
                // Plain collection — recurse (catches shared/ tasks/ etc.)
                scanForCalendars(subUrl, result, visited)
            }
        }
    }

    /**
     * Probe a single URL for the current-user-principal.
     * Returns the discovered principal path, or null if the URL is unreachable
     * or doesn't support CalDAV PROPFIND.
     */
    private fun tryFindPrincipalAt(url: String): String? {
        return try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                  <D:prop><D:current-user-principal/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(url, xml, depth = "0")
            val r = safeParseMultistatus(resp).firstOrNull()
            val principalHref = findPropHref("current-user-principal", r)
            if (principalHref != null) {
                Log.d(TAG, "Found principal $principalHref via current-user-principal at $url")
                return principalHref
            }
            val fallback = r?.href
            if (fallback != null) {
                Log.d(TAG, "No current-user-principal, using response href $fallback at $url")
            }
            fallback
        } catch (e: Exception) {
            Log.d(TAG, "tryFindPrincipalAt failed for $url: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Common CalDAV base paths to try when the user-supplied serverUrl
     * doesn't directly serve PROPFIND. Covers Mailcow/SOGo, Nextcloud,
     * and generic CalDAV deployments.
     */
    private val COMMON_CALDAV_PATHS = listOf(
        "/SOGo/dav/",          // Mailcow / SOGo
        "/.well-known/caldav", // Standard RFC 6764 autodiscovery
        "/remote.php/dav/",    // Nextcloud / ownCloud
        "/caldav/",            // Generic CalDAV
        "/dav/"                // Generic WebDAV
    )

    /**
     * Discover the CalDAV principal path by probing the server at the
     * user-supplied URL and, if that fails, trying well-known CalDAV
     * base paths. Returns "/" only if all probes fail (the caller in
     * [discoverCalendars] will abort on that).
     */
    private fun findPrincipalPath(): String {
        // 1. Try the user-supplied URL as-is
        val direct = tryFindPrincipalAt(serverUrl)
        if (direct != null) return direct

        // 2. Try common CalDAV paths appended to the server ORIGIN.
        //    Using origin avoids garbage URLs when serverUrl already
        //    contains a deep path (e.g. /SOGo/dav/email/personal/).
        val origin = extractOrigin(serverUrl)
        for (suffix in COMMON_CALDAV_PATHS) {
            val url = "$origin$suffix"
            Log.d(TAG, "findPrincipalPath: fallback probing $url")
            val p = tryFindPrincipalAt(url)
            if (p != null) {
                Log.i(TAG, "Discovered principal at $url")
                return p
            }
        }

        Log.w(TAG, "findPrincipalPath: all probes failed, returning /")
        return "/"
    }

    private fun findCalendarHomeSet(principalPath: String): String {
        val base = buildUrl(principalPath)
        return try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                  <D:prop><C:calendar-home-set/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(base, xml, depth = "0")
            val r = safeParseMultistatus(resp).firstOrNull()
            // Extract calendar-home-set href from props, NOT the response href.
            // The XML looks like:
            //   <response>
            //     <href>/principals/users/admin/</href>
            //     <propstat><prop>
            //       <calendar-home-set><href>/calendars/admin/</href></calendar-home-set>
            //     </prop></propstat>
            //   </response>
            // Our flat parser gives us all props in order. We find "calendar-home-set"
            // then take the next "href" text after it.
            val homeSetHref = findPropHref("calendar-home-set", r)
            homeSetHref ?: principalPath
        } catch (e: Exception) {
            Log.w(TAG, "findCalendarHomeSet failed for $base: ${e::class.simpleName}: ${e.message}")
            principalPath
        }
    }

    /**
     * Given a flat list of [ParsedProp] from a PROPFIND response, find
     * the property named [propName] and return the text of the next
     * <href> element after it.
     *
     * This handles the common CalDAV pattern:
     *   <X:some-prop><D:href>/value/</D:href></X:some-prop>
     * where the flat list is [..., ParsedProp("some-prop"),
     * ParsedProp("href", text="/value/"), ...]
     */
    private fun findPropHref(propName: String, r: ParsedResponse?): String? {
        if (r == null) return null
        val idx = r.props.indexOfFirst {
            it.localName.equals(propName, ignoreCase = true)
        }
        if (idx < 0) return null
        // Scan forward from idx+1 for the next href element
        for (i in (idx + 1) until r.props.size) {
            if (r.props[i].localName.equals("href", ignoreCase = true)) {
                return r.props[i].text?.takeIf { it.isNotBlank() }
            }
        }
        return null
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

    /**
     * Resolve a WebDAV href against the configured [serverUrl].
     *
     * Per RFC 4918 §10.2, an href in a PROPFIND response is either:
     * - an absolute URL   → returned as-is
     * - an absolute path  → resolved against scheme+host+port (origin)
     * - a relative path   → resolved against the request URL ([serverUrl])
     */
    private fun buildUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.isBlank()) return serverUrl

        // Absolute path reference — resolve against origin only.
        // This avoids path doubling when serverUrl itself has a path
        // (e.g. "/SOGo/dav/") and the server returns an absolute-path
        // href like "/SOGo/dav/user@example.com/".
        if (path.startsWith("/")) {
            val origin = extractOrigin(serverUrl)
            return "${origin.trimEnd('/')}$path"
        }

        // Relative path — append to the configured base URL.
        val base = serverUrl.trimEnd('/')
        return "$base/$path"
    }

    /** Extract scheme + host + port from a URL. */
    private fun extractOrigin(url: String): String {
        val idx = url.indexOf('/', url.indexOf("//") + 2)
        return if (idx < 0) url else url.substring(0, idx)
    }
}
