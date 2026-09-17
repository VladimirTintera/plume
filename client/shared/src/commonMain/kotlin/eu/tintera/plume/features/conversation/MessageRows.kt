package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
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
 * Položka, u které se hlásí „přečteno" — nebo `null`, když se nehlásí u žádné.
 *
 * Stačí to říct u **nejnovější zprávy chatu**, a jen když je moje: přečtením té poslední jsou
 * přečtené i všechny před ní, takže dvojitá fajfka u celé historie byla řada stejných odznaků
 * bez informace. A **když po mojí zprávě napsal protějšek, je přečtení dokázané samo** —
 * odznak už nemá co dodat. Proto se hledá nejnovější zpráva, ne nejnovější odchozí: vyjde-li
 * příchozí, nehlásí přečteno nikdo.
 *
 * Servisní oznámení se přeskakují. „X se přidal do skupiny" nedokazuje, že si někdo něco přečetl,
 * takže by odznak schovalo neprávem.
 *
 * [rows] jde jako seznam konverzace, tedy **od nejnovější**.
 */
internal fun readBadgeRow(rows: List<MessageRow>): MessageRow? =
    rows.firstOrNull { !it.display.isService }
        ?.takeIf { it.display.isOutgoing }

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
