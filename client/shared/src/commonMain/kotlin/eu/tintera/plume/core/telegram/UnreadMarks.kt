package eu.tintera.plume.core.telegram

/**
 * Co v chatu čeká na přečtení kromě zpráv samotných: zmínka (i odpověď na vlastní zprávu),
 * reakce na vlastní zprávu a hlas ve vlastní anketě. Všechno TDLib počítá zvlášť od
 * nepřečtených zpráv — zmínka uprostřed stovky zpráv ve skupině by se jinak ztratila.
 */
enum class UnreadMarkKind { Mention, Reaction, PollVote }

/** Počty nepřečtených zmínek, reakcí a hlasů v chatu. */
data class UnreadMarks(val mentions: Int = 0, val reactions: Int = 0, val pollVotes: Int = 0) {
    fun count(kind: UnreadMarkKind): Int = when (kind) {
        UnreadMarkKind.Mention -> mentions
        UnreadMarkKind.Reaction -> reactions
        UnreadMarkKind.PollVote -> pollVotes
    }
}

/**
 * Změna nepřečtených reakcí nebo hlasů u jedné (vlastní) zprávy — přišly nové, nebo je přečetla
 * obrazovka či jiné zařízení. Zmínky tu nejsou: příznak zmínky zpráva nenese, jen chat počet.
 */
data class UnreadMarkChange(val messageId: Long, val kind: UnreadMarkKind, val hasUnread: Boolean)

/**
 * Nejstarší zpráva z výsledku hledání — skáče se od ní, jako u nepřečtených zpráv.
 *
 * TDLib vrací výsledky od nejnovější, takže nejstarší je nejmenší id; prázdný výsledek znamená,
 * že počet na chatu byl zastaralý a už není kam skočit.
 */
internal fun oldestOf(messageIds: List<Long>): Long? = messageIds.minOrNull()
