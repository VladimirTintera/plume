package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions

/**
 * Náhled odkazu nad vstupním polem, nebo `null`, když se nemá ukázat.
 *
 * Při psaní i při úpravě textu zprávy. Ne při úpravě POPISKU (`editMessageCaption` náhled nenese)
 * a ne s přílohami (text je jejich popisek a náhled se k němu nepřipojí) — hlavička by slibovala
 * něco, co neodejde. Vypnutý náhled se neukazuje — ✕ je slib, že tam nebude.
 */
fun composerLinkPreview(
    preview: LinkPreview?,
    options: LinkPreviewOptions,
    editingCaption: Boolean,
    hasAttachments: Boolean,
): LinkPreview? = preview.takeIf { !options.disabled && !editingCaption && !hasAttachments }

/**
 * Volba po příchodu nového náhledu.
 *
 * **Jiný odkaz = volba znovu od začátku**, kromě vypnutí a výběru odkazu. Velký obrázek
 * vybraný k jednomu webu nic neříká o druhém, a u odkazu bez dvou velikostí
 * ([LinkPreview.canChangeSize]) by volba velikosti visela neviditelně. Vypnutí zůstává: kdo
 * náhled zavřel, nechce, aby se vrátil, jakmile text dopíše — maže se až s prázdným polem nebo
 * odesláním. Vybraný odkaz zůstává, dokud v textu stojí ([links]) — náhled jiného webu je
 * přesně to, co výběr odkazu způsobí.
 *
 * Náhled, který zmizel (text odkaz už nemá), volbu nemění: mezera v psaní odkazu (smazaný
 * a znovu napsaný znak) by jinak volbu zahodila.
 */
fun LinkPreviewOptions.forPreview(
    previous: LinkPreview?,
    next: LinkPreview?,
    links: List<String>,
): LinkPreviewOptions {
    val kept = if (url != null && url !in links) copy(url = null) else this
    return when {
        next == null -> kept
        previous != null && previous.url == next.url -> kept
        previous == null && kept.isDefault -> kept
        // Volba obnovená z konceptu přijde dřív než náhled; ten první k ní patří.
        previous == null -> if (kept.largeMedia != null && !next.canChangeSize) kept.copy(largeMedia = null) else kept
        else -> LinkPreviewOptions(disabled = kept.disabled, url = kept.url)
    }
}

/**
 * Volba připravená k odeslání. Vynucená velikost dostane adresu z náhledu, který uživatel právě
 * vidí (bez ní ji TDLib ignoruje); bez náhledu se velikost nevynucuje vůbec. Vybraný odkaz
 * ([LinkPreviewOptions.url]) odejde i bez náhledu — server si ho k zprávě dohledá sám.
 */
fun LinkPreviewOptions.forSending(shown: LinkPreview?): LinkPreviewOptions? = when {
    disabled -> LinkPreviewOptions(disabled = true)
    isDefault -> null
    // S adresou velikost platí i bez náhledu; bez ní by ji TDLib stejně ignoroval.
    shown == null -> if (url != null) this else copy(largeMedia = null).takeUnless { it.isDefault }
    else -> copy(url = shown.url)
}

/**
 * Volba náhledu upravené zprávy. Vychází z volby, se kterou zpráva odešla, a z toho, co s ní
 * uživatel při úpravě udělal. Odkaz, který z upraveného textu zmizel, se zahodí — náhled by
 * jinak patřil něčemu, co zpráva už neobsahuje. Kontroluje se to i tady, ne jen v
 * [forPreview]: úprava odeslaná dřív, než dorazí náhled, by jinak nesla starou adresu.
 */
fun LinkPreviewOptions.forEdit(text: String, shown: LinkPreview?): LinkPreviewOptions? {
    val url = url
    return (if (url != null && !text.contains(url)) copy(url = null) else this).forSending(shown)
}

/**
 * Odkaz, ke kterému náhled právě patří: vybraný, jinak první v textu. Podle toho se v sheetu
 * označí řádek — adresa v náhledu od serveru ([LinkPreview.url]) bývá upravená (doplněné
 * `https://`, lomítko na konci) a s textem by se neshodovala.
 */
fun LinkPreviewOptions.selectedLink(links: List<String>): String? = url?.takeIf { it in links } ?: links.firstOrNull()

/**
 * Doména odkazu jako název řádku ve výběru: `https://www.example.com/a?b` → `example.com`.
 * Celá adresa stojí pod ní; dva odkazy na tentýž web rozliší až ona.
 */
fun linkHost(link: String): String {
    val withoutScheme = link.substringAfter("://")
    val host = withoutScheme.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
    return host.removePrefix("www.").ifEmpty { link }
}
