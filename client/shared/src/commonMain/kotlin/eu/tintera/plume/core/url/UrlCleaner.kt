package eu.tintera.plume.core.url

import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.afterEdit

/**
 * Odstraňuje známé sledovací a analytické parametry (UTM značky, click ID reklamních
 * a sociálních sítí, trackery sdílení) z webových odkazů.
 *
 * Zachovává funkční a navigační parametry (např. dotaz `q`, stránku `p`, ID videa `v`,
 * časovou značku videa `t`, kotvu `#` apod.).
 */
object UrlCleaner {

    /**
     * Univerzální sledovací parametry (malými písmeny).
     */
    private val UNIVERSAL_TRACKING_PARAMS = setOf(
        // Google Analytics / Urchin Tracking Module
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "utm_source_platform",
        "utm_creative_format",
        "utm_marketing_tactic",

        // Reklamní click ID
        "fbclid",       // Facebook / Meta
        "gclid",        // Google Click Identifier
        "gclsrc",       // Google Click Source
        "dclid",        // DoubleClick
        "msclkid",      // Microsoft / Bing
        "twclid",       // Twitter
        "ttclid",       // TikTok
        "wbraid",       // Google Ads web-to-app
        "gbraid",       // Google Ads app-to-web
        "yclid",        // Yandex
        "mc_cid",       // Mailchimp Campaign ID
        "mc_eid",       // Mailchimp Email ID

        // Sociální sítě
        "igshid",       // Instagram Share ID
        "share_id",     // Obecný share tracking
    )

    /**
     * Sledovací parametry specifické pro konkrétní domény (normalizované na malé písmo).
     */
    private fun isDomainSpecificTracker(host: String, paramNameLower: String): Boolean {
        val domain = host.removePrefix("www.")
        return when {
            // YouTube: `si` (Share Identifier) sleduje sdílejícího uživatele.
            // DŮLEŽITÉ: `v` (video ID) a `t` (časový posun) se nesmí smazat!
            domain == "youtube.com" || domain.endsWith(".youtube.com") || domain == "youtu.be" -> {
                paramNameLower == "si" || paramNameLower == "feature"
            }

            // Spotify: `si` (Share Identifier), `context`
            domain == "spotify.com" || domain.endsWith(".spotify.com") || domain == "open.spotify.com" -> {
                paramNameLower == "si" || paramNameLower == "context"
            }

            // TikTok: `_r`, `_t`, `checksum`
            domain == "tiktok.com" || domain.endsWith(".tiktok.com") -> {
                paramNameLower == "_r" || paramNameLower == "_t" || paramNameLower == "checksum"
            }

            // Twitter / X: `s`, `t`
            domain == "twitter.com" || domain.endsWith(".twitter.com") || domain == "x.com" || domain.endsWith(".x.com") -> {
                paramNameLower == "s" || paramNameLower == "t"
            }

            // Amazon: `ref_`, `pf_rd_*`
            domain == "amazon.com" || domain.contains(".amazon.") -> {
                paramNameLower.startsWith("ref_") || paramNameLower.startsWith("pf_rd_")
            }

            else -> false
        }
    }

    /**
     * Očistí zadanou [url] od sledovacích parametrů.
     *
     * Pokud URL neobsahuje žádné query parametry nebo není platný HTTP(S) odkaz,
     * vrátí ji beze změny.
     */
    fun cleanUrl(url: String): String {
        val trimmed = url.trim()
        val queryStart = trimmed.indexOf('?')
        if (queryStart == -1) return trimmed

        // Schéma musí být http nebo https (nebo relativní odkaz bez schématu)
        val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme.isNotEmpty() && scheme != "http" && scheme != "https") {
            return trimmed
        }

        val fragmentStart = trimmed.indexOf('#', startIndex = queryStart)
        val queryEnd = if (fragmentStart != -1) fragmentStart else trimmed.length

        val basePath = trimmed.substring(0, queryStart)
        val queryString = trimmed.substring(queryStart + 1, queryEnd)
        val fragment = if (fragmentStart != -1) trimmed.substring(fragmentStart) else ""

        if (queryString.isEmpty()) {
            return basePath + fragment
        }

        // Extrakce domény pro doménově specifická pravidla
        val host = extractHost(basePath)

        val pairs = queryString.split('&')
        val retained = mutableListOf<String>()

        for (pair in pairs) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=')
            val nameLower = name.lowercase()

            val isTracker = nameLower in UNIVERSAL_TRACKING_PARAMS ||
                nameLower.startsWith("utm_") ||
                isDomainSpecificTracker(host, nameLower)

            if (!isTracker) {
                retained.add(pair)
            }
        }

        val newQuery = if (retained.isNotEmpty()) "?" + retained.joinToString("&") else ""
        return basePath + newQuery + fragment
    }

    /**
     * Najde v [text] všechny webové odkazy začínající `http://` nebo `https://`
     * a nahradí je jejich vyčištěnou verzí.
     */
    fun cleanTextUrls(text: String): String {
        if (!text.contains("http://", ignoreCase = true) && !text.contains("https://", ignoreCase = true)) {
            return text
        }

        val urlRegex = Regex("""https?://[^\s<>"'{}|\\^`]+""", RegexOption.IGNORE_CASE)
        return urlRegex.replace(text) { matchResult ->
            val rawUrl = matchResult.value
            // Pokud na konci URL uvízla interpunkce (tečka, čárka, závorka na konci věty),
            // oddělíme ji před čištěním.
            val (urlOnly, trailing) = trimTrailingPunctuation(rawUrl)
            cleanUrl(urlOnly) + trailing
        }
    }

    /**
     * Očistí webové odkazy v celém textu a zároveň aktualizuje formátovací rozsahy
     * (i explicitní odkazy v [eu.tintera.plume.core.telegram.FormatSpan.url]).
     */
    fun cleanComposedText(composed: ComposedText): ComposedText {
        val originalText = composed.text
        val cleanedText = cleanTextUrls(originalText)

        // Aktualizujeme formátování podle změny textu (posun offsetů)
        val shiftedFormats = if (originalText != cleanedText) {
            composed.formats.afterEdit(old = originalText, new = cleanedText)
        } else {
            composed.formats
        }

        // Vyčistíme i cílové URL u explicitních hypertextových odkazů ([FormatSpan.url])
        val cleanedFormats = shiftedFormats.map { span ->
            if (span.url != null) {
                span.copy(url = cleanUrl(span.url))
            } else {
                span
            }
        }

        return eu.tintera.plume.core.telegram.ComposedText(cleanedText, cleanedFormats)
    }

    private fun extractHost(basePath: String): String {
        val withoutScheme = if (basePath.contains("://")) basePath.substringAfter("://") else basePath
        val hostWithPort = withoutScheme.substringBefore('/')
        return hostWithPort.substringBefore(':').lowercase()
    }

    private fun trimTrailingPunctuation(url: String): Pair<String, String> {
        var end = url.length
        while (end > 0) {
            val c = url[end - 1]
            if (c == '.' || c == ',' || c == ';' || c == ')' || c == ']' || c == '!') {
                end--
            } else {
                break
            }
        }
        return url.substring(0, end) to url.substring(end)
    }
}
