package eu.tintera.plume.core.url

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlCleanerTest {

    @Test
    fun urlBezQueryNeboTrackeruZustaneBezeZmeny() {
        assertEquals("https://example.com", UrlCleaner.cleanUrl("https://example.com"))
        assertEquals("https://example.com/path/to/page", UrlCleaner.cleanUrl("https://example.com/path/to/page"))
        assertEquals("https://example.com?q=kotlin&page=2", UrlCleaner.cleanUrl("https://example.com?q=kotlin&page=2"))
    }

    @Test
    fun odstraneniUtmParametru() {
        val input = "https://example.com/article?utm_source=newsletter&utm_medium=email&utm_campaign=spring2026"
        assertEquals("https://example.com/article", UrlCleaner.cleanUrl(input))

        val mixed = "https://example.com/search?q=phone&utm_source=google&page=1"
        assertEquals("https://example.com/search?q=phone&page=1", UrlCleaner.cleanUrl(mixed))
    }

    @Test
    fun odstraneniReklamnichClickId() {
        val fb = "https://example.com/product?id=42&fbclid=IwAR12345"
        assertEquals("https://example.com/product?id=42", UrlCleaner.cleanUrl(fb))

        val google = "https://example.com/landing?gclid=xyz987&gclsrc=aw.ds"
        assertEquals("https://example.com/landing", UrlCleaner.cleanUrl(google))

        val bingAndOthers = "https://example.com/?msclkid=123&twclid=456&ttclid=789&yclid=000"
        assertEquals("https://example.com/", UrlCleaner.cleanUrl(bingAndOthers))
    }

    @Test
    fun zachovaniFragmentuAKotvy() {
        val withHash = "https://example.com/docs?utm_source=web#section-2"
        assertEquals("https://example.com/docs#section-2", UrlCleaner.cleanUrl(withHash))

        val queryAndHash = "https://example.com/view?id=123&utm_medium=cpc#comments"
        assertEquals("https://example.com/view?id=123#comments", UrlCleaner.cleanUrl(queryAndHash))
    }

    @Test
    fun youtubeTrackingSiOdstraniAleCasAZadneVideoIdZachova() {
        // YouTube Share ID (si)
        val ytVideo = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abcdef123456"
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", UrlCleaner.cleanUrl(ytVideo))

        // Zachování časového razítka (t)
        val ytWithTime = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123456&t=42s"
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42s", UrlCleaner.cleanUrl(ytWithTime))

        // Krátký odkaz youtu.be jen se si
        val ytShort = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123456"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", UrlCleaner.cleanUrl(ytShort))
    }

    @Test
    fun spotifyTrackingSiOdstrani() {
        val spotify = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=1234567890abcdef&context=spotify"
        assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", UrlCleaner.cleanUrl(spotify))
    }

    @Test
    fun twitterXTrackingOdstrani() {
        val tweet = "https://x.com/user/status/1234567890?s=20&t=abcdef"
        assertEquals("https://x.com/user/status/1234567890", UrlCleaner.cleanUrl(tweet))
    }

    @Test
    fun cisteniOdkazuVTextuZpravy() {
        val text = "Koukni na tohle: https://example.com/news?id=1&utm_source=twitter, fakt pecka!"
        val cleaned = UrlCleaner.cleanTextUrls(text)
        assertEquals("Koukni na tohle: https://example.com/news?id=1, fakt pecka!", cleaned)
    }

    @Test
    fun textSViceOdkazyAInterpunkci() {
        val text = "Tady je video (https://youtu.be/xyz?si=track&t=10s) a článek https://blog.cz/post?fbclid=999."
        val cleaned = UrlCleaner.cleanTextUrls(text)
        assertEquals("Tady je video (https://youtu.be/xyz?t=10s) a článek https://blog.cz/post.", cleaned)
    }

    @Test
    fun cisteniComposedTextVCetneExplicitnichOdkazuAFormatu() {
        val original = eu.tintera.plume.core.telegram.ComposedText(
            text = "Koukni https://example.com?utm_source=test a klikni sem",
            formats = listOf(
                eu.tintera.plume.core.telegram.FormatSpan(
                    format = eu.tintera.plume.core.telegram.TextFormat.Link,
                    start = 43,
                    end = 53,
                    url = "https://hidden.com?fbclid=secret",
                )
            )
        )
        val cleaned = UrlCleaner.cleanComposedText(original)
        assertEquals("Koukni https://example.com a klikni sem", cleaned.text)
        assertEquals(1, cleaned.formats.size)
        val format = cleaned.formats.first()
        assertEquals("https://hidden.com", format.url)
        // Offset se posunul doleva o délku umazaného "?utm_source=test" (16 znaků)
        assertEquals(43 - 16, format.start)
        assertEquals(53 - 16, format.end)
    }
}
