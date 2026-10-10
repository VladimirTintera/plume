package eu.tintera.plume.core.settings

/**
 * Co udělá tah chatem do strany v seznamu chatů. Každá akce se u chatu, kde už platí, otočí:
 * archivovaný se vrátí mezi chaty, ztlumený odtlumí, připnutý odepne, přečtený označí jako
 * nepřečtený. Tah tak jde vždycky vrátit týmž tahem.
 *
 * Ukládá se názvem, jako ostatní výčty v [AppSettings].
 */
enum class ChatSwipeAction { Archive, Read, Mute, Pin, None }

/**
 * Akce tahu pro obě strany. Směr je FYZICKÝ (doprava / doleva), ne začátek a konec řádku —
 * tak o něm uživatel mluví a tak ho volí v Nastavení.
 *
 * Výchozí je archivace na obou stranách: tak se seznam choval, než šlo vybírat.
 */
data class ChatSwipeActions(
    val right: ChatSwipeAction = ChatSwipeAction.Archive,
    val left: ChatSwipeAction = ChatSwipeAction.Archive,
)
