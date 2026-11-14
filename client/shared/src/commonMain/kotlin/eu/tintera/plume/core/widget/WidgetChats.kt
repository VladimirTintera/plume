package eu.tintera.plume.core.widget

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.telegram.ChatSummary
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
    /** Účet chatu, id slotu; chybí u podkladů zapsaných před víc účty. */
    val slot: String = AccountSlot.Primary.id,
    /**
     * Odznak účtu na avataru, když widget ukazuje chaty víc účtů (režim Všechny účty): iniciála
     * účtu, `null` = odznak se nekreslí. Bez něj nejdou rozlišit řádky téhož chatu ve dvou
     * účtech (Telegram, společná skupina).
     */
    val accountInitial: String? = null,
    /** Kulatá miniatura fotky účtu k odznaku; `null` = iniciála [accountInitial]. */
    val accountAvatar: String? = null,
) {
    /** Klíč řádku v seznamu widgetu: totéž id má chat ve dvou účtech. */
    val itemId: Long get() = if (slot == AccountSlot.Primary.id) id else "$slot:$id".hashCode().toLong()
}

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
    val (important, rest) = visibleInWidget(chats, savedMessagesChatId).partition { it.isPinned || (it.hasUnread && !it.isMuted) }
    return (important + rest).take(max)
}

private fun visibleInWidget(chats: List<ChatSummary>, savedMessagesChatId: Long?): List<ChatSummary> =
    chats.filter { it.sponsor == null && (it.id != savedMessagesChatId || it.isPinned) }

/** Chaty jednoho účtu pro widget víc účtů ([mergedWidgetChats]). */
data class WidgetAccountChats(val slot: AccountSlot, val chats: List<ChatSummary>, val savedMessagesChatId: Long?)

/**
 * Chaty do widgetu z víc účtů (režim Všechny účty): totéž pravidlo jako [widgetChats], jen přes
 * všechny účty — napřed připnuté (po účtech, uvnitř účtu v pořadí od TDLib), pak nepřečtené všech
 * účtů a nakonec zbytek, obojí podle času poslední zprávy. `order` z TDLib se mezi účty porovnat
 * nedá, stejně jako ve společném seznamu chatů. S jedním účtem přesně [widgetChats].
 */
fun mergedWidgetChats(accounts: List<WidgetAccountChats>, max: Int = WIDGET_MAX_CHATS): List<Pair<AccountSlot, ChatSummary>> {
    accounts.singleOrNull()?.let { one -> return widgetChats(one.chats, one.savedMessagesChatId, max).map { one.slot to it } }
    val pinned = mutableListOf<Pair<AccountSlot, ChatSummary>>()
    val unread = mutableListOf<Pair<AccountSlot, ChatSummary>>()
    val rest = mutableListOf<Pair<AccountSlot, ChatSummary>>()
    accounts.forEach { account ->
        visibleInWidget(account.chats, account.savedMessagesChatId).forEach { chat ->
            when {
                chat.isPinned -> pinned += account.slot to chat
                chat.hasUnread && !chat.isMuted -> unread += account.slot to chat
                else -> rest += account.slot to chat
            }
        }
    }
    val newestFirst = compareByDescending<Pair<AccountSlot, ChatSummary>> { it.second.lastMessageTimestamp ?: Int.MIN_VALUE }
    return (pinned + unread.sortedWith(newestFirst) + rest.sortedWith(newestFirst)).take(max)
}

/** Víc chatů se do widgetu rozumné velikosti nevejde; seznam ve widgetu jde posouvat. */
const val WIDGET_MAX_CHATS = 12
