package eu.tintera.plume.core.telegram

/**
 * Co v chatu čeká na přečtení kromě zpráv samotných: zmínka (i odpověď na vlastní zprávu)
 * a reakce na vlastní zprávu. Obojí TDLib počítá zvlášť od nepřečtených zpráv — zmínka
 * uprostřed stovky zpráv ve skupině by se jinak ztratila.
 */
enum class UnreadMarkKind { Mention, Reaction }

/** Počty nepřečtených zmínek a reakcí v chatu. */
data class UnreadMarks(val mentions: Int = 0, val reactions: Int = 0) {
    fun count(kind: UnreadMarkKind): Int = when (kind) {
        UnreadMarkKind.Mention -> mentions
        UnreadMarkKind.Reaction -> reactions
    }
}

/** Změna nepřečtených reakcí u jedné zprávy — přišla nová, nebo je přečetla obrazovka či jiné zařízení. */
data class UnreadReactionChange(val messageId: Long, val hasUnread: Boolean)

/**
 * Nejstarší zpráva z výsledku hledání — skáče se od ní, jako u nepřečtených zpráv.
 *
 * TDLib vrací výsledky od nejnovější, takže nejstarší je nejmenší id; prázdný výsledek znamená,
 * že počet na chatu byl zastaralý a už není kam skočit.
 */
internal fun oldestOf(messageIds: List<Long>): Long? = messageIds.minOrNull()
