package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions

/**
 * Náhled odkazu nad vstupním polem, nebo `null`, když se nemá ukázat.
 *
 * Jen u obyčejné zprávy: při úpravě (`editMessageText` volbu zatím nenese) a s přílohami (text
 * je jejich popisek a náhled se k němu nepřipojí) by hlavička slibovala něco, co neodejde.
 * Vypnutý náhled se neukazuje — ✕ je slib, že tam nebude.
 */
fun composerLinkPreview(
    preview: LinkPreview?,
    options: LinkPreviewOptions,
    editing: Boolean,
    hasAttachments: Boolean,
): LinkPreview? = preview.takeIf { !options.disabled && !editing && !hasAttachments }

/**
 * Volba po příchodu nového náhledu.
 *
 * **Jiný odkaz = volba znovu od začátku**, kromě vypnutí. Velký obrázek vybraný k jednomu webu
 * nic neříká o druhém, a u odkazu bez dvou velikostí ([LinkPreview.canChangeSize]) by volba
 * velikosti visela neviditelně. Vypnutí zůstává: kdo náhled zavřel, nechce, aby se vrátil,
 * jakmile text dopíše — maže se až s prázdným polem nebo odesláním.
 *
 * Náhled, který zmizel (text odkaz už nemá), volbu nemění: mezera v psaní odkazu (smazaný
 * a znovu napsaný znak) by jinak volbu zahodila.
 */
fun LinkPreviewOptions.forPreview(previous: LinkPreview?, next: LinkPreview?): LinkPreviewOptions = when {
    next == null -> this
    previous != null && previous.url == next.url -> this
    previous == null && isDefault -> this
    // Volba obnovená z konceptu přijde dřív než náhled; ten první k ní patří.
    previous == null -> if (largeMedia != null && !next.canChangeSize) copy(largeMedia = null) else this
    else -> LinkPreviewOptions(disabled = disabled)
}

/**
 * Volba připravená k odeslání. Vynucená velikost dostane adresu z náhledu, který uživatel právě
 * vidí (bez ní ji TDLib ignoruje); bez náhledu se velikost nevynucuje vůbec.
 */
fun LinkPreviewOptions.forSending(shown: LinkPreview?): LinkPreviewOptions? = when {
    disabled -> LinkPreviewOptions(disabled = true)
    isDefault -> null
    shown == null -> copy(largeMedia = null, url = null).takeUnless { it.isDefault }
    else -> copy(url = shown.url)
}
