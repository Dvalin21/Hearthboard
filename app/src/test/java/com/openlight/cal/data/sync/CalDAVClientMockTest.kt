package com.openlight.cal.data.sync

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Local JVM CalDAV round-trip tests via MockWebServer + real xpp3 parser.
 *
 * [CalDAVClient] exposes a `parserFactory` seam so we can inject a real
 * pull parser without relying on `XmlPullParserFactory.newInstance()` directly,
 * which has historically tripped up Android JVM test runtimes.
 *
 * Requests are routed through a path-rewriting interceptor so the client's
 * absolute `serverUrl` transparently hits the mock server.
 */
class CalDAVClientMockTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun parser() = XmlPullParserFactory.newInstance().newPullParser()

    private fun mockHttpClient() = okhttp3.OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val target = server.url(original.url.encodedPath)
            chain.proceed(original.newBuilder().url(target).build())
        }
        .build()

    private fun client(accountUrl: String = baseUrl): CalDAVClient = CalDAVClient(
        serverUrl     = accountUrl,
        username      = "user",
        password      = "pass",
        httpClient    = mockHttpClient(),
        parserFactory = ::parser
    )

    @Test
    fun `safeParseMultistatus parses basic multistatus XML`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/cal/primary/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>Primary</D:displayname>
                    <CS:getctag xmlns:CS="http://calendarserver.org/ns/"/>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/cal/kids/birthdays/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>Birthdays</D:displayname>
                    <CS:getctag xmlns:CS="http://calendarserver.org/ns/"/>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val parsed = client().safeParseMultistatus(xml, parser())
        org.junit.Assert.assertEquals(
            listOf("/cal/primary/", "/cal/kids/birthdays/"),
            parsed.map { it.href }
        )
    }

    @Test
    fun `propfindResources parses getetag and getcontenttype`() {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/cal/primary/abc123.ics</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getetag>"abc123"</D:getetag>
                    <D:getcontenttype>text/calendar; charset=utf-8</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val resources = client().propfindResources("/cal/primary/")
        org.junit.Assert.assertEquals(1, resources.size)
        val r = resources.first()
        org.junit.Assert.assertEquals("/cal/primary/abc123.ics", r.href)
        org.junit.Assert.assertEquals("abc123", r.etag)
        org.junit.Assert.assertEquals("text/calendar; charset=utf-8", r.contentType)
    }

    @Test
    fun `getETagList returns matching href-etag pairs`() {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/cal/primary/a.ics</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getetag>"etag-a"</D:getetag>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/cal/primary/b.ics</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getetag>"etag-b"</D:getetag>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(207))

        val list = client().getETagList("/cal/primary/")
        org.junit.Assert.assertEquals(2, list.size)
        org.junit.Assert.assertEquals("/cal/primary/a.ics", list[0].href)
        org.junit.Assert.assertEquals("etag-a", list[0].etag)
        org.junit.Assert.assertEquals("/cal/primary/b.ics", list[1].href)
        org.junit.Assert.assertEquals("etag-b", list[1].etag)
    }

    @Test
    fun `putIcs includes if-match header and returns echoed etag`() {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setHeader("ETag", "\"server-etag\"")
        )

        val path = "/cal/primary/new.ics"
        val etag = client().putIcs(path, "BEGIN:VCALENDAR\r\nEND:VCALENDAR", "client-etag")
        org.junit.Assert.assertEquals("server-etag", etag)

        val recorded = server.takeRequest()
        org.junit.Assert.assertEquals("PUT", recorded.method)
        org.junit.Assert.assertEquals(path, recorded.path)
        org.junit.Assert.assertEquals("\"client-etag\"", recorded.getHeader("If-Match"))
    }

    @Test
    fun `deleteIcs issues delete with if-match`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val ok = client().deleteIcs("/cal/primary/old.ics", "client-etag")
        org.junit.Assert.assertTrue(ok)

        val recorded = server.takeRequest()
        org.junit.Assert.assertEquals("DELETE", recorded.method)
        org.junit.Assert.assertEquals("\"client-etag\"", recorded.getHeader("If-Match"))
    }
}
