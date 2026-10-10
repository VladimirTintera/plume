package eu.tintera.plume.core.widget

import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.features.chats.hasUnread
import kotlinx.serialization.Serializable

/**
 * Chat ve widgetu na ploše — jen to, co widget kreslí. Ukládá se do souboru, protože widget se
 * kreslí i tehdy, když proces aplikace neběží a TDLib se ptát nedá.
 *
 * [preview] je `null`, když se náhled ukazovat nesmí (zapnutý zámek aplikace) — pak widget nese
 * jen jméno a počet nepřečtených.
 */
@Serializable
data class WidgetChat(
    val id: Long,
    val title: String,
    val preview: String?,
    val unread: Int,
    val muted: Boolean,
    /** Čas poslední zprávy (sekundy od epochy), `null` = chat bez zpráv. */
    val timestamp: Int?,
    /** Kulatá miniatura fotky chatu (soubor widgetu); `null` = iniciála. */
    val avatar: String? = null,
    /** Uložené zprávy — kreslí se se záložkou místo fotky účtu, jako v seznamu chatů. */
    val savedMessages: Boolean = false,
)

/** Co widget ukazuje: chaty a jestli je vůbec někdo přihlášený. */
@Serializable
data class WidgetSnapshot(
    val loggedIn: Boolean = false,
    val chats: List<WidgetChat> = emptyList(),
) {
    /** Nepřečtené ve ztlumených chatech se nepočítají — odznak má říct, co čeká na odpověď. */
    val unread: Int get() = chats.filterNot { it.muted }.sumOf { it.unread }
}

/**
 * Které chaty do widgetu: napřed připnuté a nepřečtené (to, kvůli čemu se na plochu kouká),
 * pak zbytek seznamu, dokud je místo. Pořadí uvnitř zůstává od TDLib, jako v seznamu chatů.
 *
 * Sponzorovaný chat do widgetu nepatří (uživatel si ho nevybral) a Uložené zprávy jen připnuté —
 * stejné pravidlo jako v seznamu chatů (`chatGroups`).
 */
fun widgetChats(chats: List<ChatSummary>, savedMessagesChatId: Long?, max: Int = WIDGET_MAX_CHATS): List<ChatSummary> {
    val visible = chats.filter { it.sponsor == null && (it.id != savedMessagesChatId || it.isPinned) }
    val (important, rest) = visible.partition { it.isPinned || (it.hasUnread && !it.isMuted) }
    return (important + rest).take(max)
}

/** Víc chatů se do widgetu rozumné velikosti nevejde; seznam ve widgetu jde posouvat. */
const val WIDGET_MAX_CHATS = 12
