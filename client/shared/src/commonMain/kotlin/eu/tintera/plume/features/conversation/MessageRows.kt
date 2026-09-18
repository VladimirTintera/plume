package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState

/**
 * Jedna položka seznamu konverzace: samostatná zpráva, nebo celé album.
 *
 * Album jsou v Telegramu samostatné zprávy spojené `mediaAlbumId`; konverzace je kreslí jako jednu
 * bublinu. [messages] jde stejně jako seznam zpráv **od nejnovější**.
 */
internal class MessageRow(val messages: List<ChatMessage>) {

    val newest: ChatMessage get() = messages.first()
    val oldest: ChatMessage get() = messages.last()
    val isAlbum: Boolean get() = messages.size > 1

    /** Zprávy alba v pořadí, ve kterém se čtou a kreslí v mřížce: od nejstarší. */
    val members: List<ChatMessage> get() = messages.asReversed()

    /**
     * Zpráva, ke které se v bublině vztahuje popisek, reakce, odpověď a nabídka.
     *
     * U alba ta s popiskem — tu uživatel vidí jako „text alba" a reaguje na ni. Bez popisku první
     * (nejstarší), jak to dělají ostatní klienti.
     */
    val primary: ChatMessage = members.firstOrNull { it.text.isNotBlank() } ?: oldest

    /**
     * Co bublina kreslí. U samostatné zprávy ona sama; u alba [primary] bez přílohy (tu nese
     * mřížka), s časem nejnovější zprávy a se „nejhorším" stavem doručení — album je odeslané,
     * až když jsou odeslané všechny jeho zprávy.
     */
    val display: ChatMessage = if (!isAlbum) {
        newest
    } else {
        primary.copy(
            media = null,
            timestamp = newest.timestamp,
            isEdited = messages.any { it.isEdited },
            sendState = when {
                messages.any { it.sendState == SendState.Failed } -> SendState.Failed
                messages.any { it.sendState == SendState.Sending } -> SendState.Sending
                else -> primary.sendState
            },
        )
    }

    fun contains(messageId: Long): Boolean = messages.any { it.id == messageId }
}

/**
 * Položky, u kterých se hlásí „přečteno".
 *
 * Automaticky stačí **nejnovější zpráva chatu**, a jen když je moje: přečtením té poslední jsou
 * přečtené i všechny před ní, takže dvojitá fajfka u celé historie byla řada stejných odznaků
 * bez informace. A **když po mojí zprávě napsal protějšek, je přečtení dokázané samo** —
 * odznak už nemá co dodat. Proto se hledá nejnovější zpráva, ne nejnovější odchozí: vyjde-li
 * příchozí, nehlásí přečteno nikdo.
 *
 * [revealedMessageId] je zpráva, u které si odznak **vyžádal uživatel** ťuknutím. Je to výjimka
 * z pravidla výš, ne jeho přepsání: kdo chce ubezpečení u jedné konkrétní zprávy, dostane ho,
 * a automatický odznak u nejnovější zprávy zůstává. Rozhoduje se to tady, na jednom místě —
 * v bublině by to byla druhá podmínka o témže a ty dvě by se časem rozešly.
 *
 * Servisní oznámení se přeskakují. „X se přidal do skupiny" nedokazuje, že si někdo něco přečetl,
 * takže by odznak schovalo neprávem.
 *
 * [rows] jde jako seznam konverzace, tedy **od nejnovější**.
 */
internal fun readBadgeRows(rows: List<MessageRow>, revealedMessageId: Long? = null): List<MessageRow> {
    val automatic = rows.firstOrNull { !it.display.isService }?.takeIf { it.display.isOutgoing }

    val revealed = revealedMessageId
        ?.let { id -> rows.firstOrNull { it.contains(id) } }
        ?.takeIf { it.display.isOutgoing && !it.display.isService }

    return if (revealed == null || revealed === automatic) {
        listOfNotNull(automatic)
    } else {
        listOfNotNull(automatic, revealed)
    }
}

/**
 * Spojí sousední zprávy téhož alba do jedné položky.
 *
 * Jen **sousední**: album se odesílá naráz a jeho zprávy mají po sobě jdoucí id, takže mezi ně nic
 * nepatří. Když by se tam přesto něco dostalo, rozdělí se album na dvě bubliny — lepší než
 * přeskládat pořadí zpráv.
 *
 * Na hraně načtené historie může být album načtené jen z části; dotažením starší stránky se k němu
 * zbytek připojí.
 */
internal fun groupAlbums(messages: List<ChatMessage>): List<MessageRow> {
    val rows = ArrayList<MessageRow>(messages.size)
    var current = ArrayList<ChatMessage>()

    fun flush() {
        if (current.isNotEmpty()) rows += MessageRow(current)
        current = ArrayList()
    }

    for (message in messages) {
        val album = message.mediaAlbumId
        val continuesAlbum = album != 0L && current.isNotEmpty() && current.last().mediaAlbumId == album
        if (!continuesAlbum) flush()
        current += message
    }
    flush()
    return rows
}

/**
 * Kreslí se příloha zprávy přímo na plochu konverzace, bez bubliny?
 *
 * Fotka a video jsou obrázek samy o sobě — bublina kolem nich nemá co ohraničovat a jen přidává
 * rám, ze kterého konverzace ztěžkne. Stejně to dělá Google Messages. Popisek pod snímkem bublinu
 * naopak dostane vlastní: text na holé ploše by se ztratil.
 *
 * Podmínkou je, že na zprávě nevisí nic, co pozadí potřebuje:
 * - **citace** se bez pozadí ztratí (tentýž důvod, proč si bublinu drží i zpráva z pár emoji),
 * - **náhled odkazu** stojí na proužku akcentu uvnitř bubliny.
 *
 * Ostatní přílohy bublinu potřebují dál: soubor, hlasovka i samolepkový popis jsou řádky s textem
 * a ikonou, ne obrázek. [album] nese zprávy alba, u samostatné zprávy `null`.
 */
internal fun showsBareMedia(message: ChatMessage, album: List<ChatMessage>?): Boolean {
    if (message.replyToMessageId != null || message.linkPreview != null) return false
    val media = album?.map { it.media } ?: listOf(message.media)
    return media.isNotEmpty() && media.all { it is MessageMedia.Photo || it is MessageMedia.Video }
}

/**
 * Kreslí se náhled odkazu jako skupina na ploše — text zprávy, obrázek a popis pod ním — místo
 * karty s proužkem uvnitř bubliny?
 *
 * Obrázek náhledu je obrázek jako každý jiný, takže patří na plochu ze stejného důvodu jako fotka
 * (viz [showsBareMedia]). Doplňkový text (web, titulek, popis) na plochu nepatří a dostane vlastní
 * kontejner pod snímkem.
 *
 * Platí to jen pro **velký obrázek**: o tom, jestli je velký, rozhoduje odesílatel
 * (`LinkPreview.largePhoto`) a malý čtvereček vedle textu je pořád řádek v kartě, ne snímek.
 * Náhled u zprávy s přílohou nebo citací zůstává v bublině, jako celá ta zpráva.
 */
internal fun showsBareLinkPreview(message: ChatMessage): Boolean {
    val preview = message.linkPreview ?: return false
    return preview.largePhoto &&
        preview.photo != null &&
        message.media == null &&
        message.replyToMessageId == null
}

