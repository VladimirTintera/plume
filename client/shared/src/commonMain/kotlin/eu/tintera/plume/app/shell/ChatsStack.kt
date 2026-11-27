package eu.tintera.plume.app.shell

import eu.tintera.plume.features.share.InsertChat
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.navigation.AccountRoute
import eu.tintera.plume.core.navigation.ArchiveChatStatisticsRoute
import eu.tintera.plume.core.navigation.ArchiveEventLogRoute
import eu.tintera.plume.core.navigation.ArchivePostStatisticsRoute
import eu.tintera.plume.core.navigation.ChatStatisticsRoute
import eu.tintera.plume.core.navigation.PostStatisticsRoute
import eu.tintera.plume.core.navigation.EventLogRoute
import eu.tintera.plume.core.navigation.JoinRequestsRoute
import eu.tintera.plume.core.navigation.RemovedMembersRoute
import eu.tintera.plume.core.navigation.InviteLinksRoute
import eu.tintera.plume.core.navigation.ArchiveJoinRequestsRoute
import eu.tintera.plume.core.navigation.ArchiveRemovedMembersRoute
import eu.tintera.plume.core.navigation.ArchiveInviteLinksRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.ChatCalendarRoute
import eu.tintera.plume.core.navigation.ChatInsightsRoute
import eu.tintera.plume.core.navigation.ArchiveChatInsightsRoute
import eu.tintera.plume.core.navigation.ArchiveChatCalendarRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.adaptive.coveredExtras
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.MemberRightsRoute
import eu.tintera.plume.core.navigation.EditChatRoute
import eu.tintera.plume.core.navigation.ArchiveMemberRightsRoute
import eu.tintera.plume.core.navigation.ArchiveEditChatRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ArchiveForumTopicsRoute
import eu.tintera.plume.core.navigation.ArchiveDirectTopicsRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.SavedTopicsRoute
import eu.tintera.plume.core.navigation.DirectTopicsRoute
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.features.chats.ArchiveChange
import eu.tintera.plume.features.conversation.ChatTarget
import eu.tintera.plume.core.navigation.ChatSearchRoute

/*
 * Pravidla nad zásobníkem Chatů jako čisté funkce, ne lambdy v `AppShell`.
 *
 * Zásobník nese dvě list-detail větve naráz — hlavní seznam a archiv (viz ARCHIVE_SCENE) —
 * takže skoro každá operace musí vědět, do KTERÉ z nich patří. Uvnitř lambdy v `entryProvider`
 * se to neotestuje a hraniční kombinace (prázdný archiv nad otevřenou konverzací hlavního
 * seznamu, odarchivování právě otevřeného chatu) se na zařízení poznají až špatným rozvržením.
 * Testy jsou v `ChatsStackTest`.
 */

/**
 * Složka, jejíž seznam je vlevo vidět: rozhoduje POSLEDNÍ list panel v zásobníku, protože
 * scéna kreslí vlevo ten.
 */
internal fun List<NavKey>.shownChatFolder(): ChatFolder =
    if (lastOrNull { it == ChatsRoute || it == ArchiveRoute } == ArchiveRoute) {
        ChatFolder.Archive
    } else {
        ChatFolder.Main
    }

/**
 * Je otevřená konverzace v [folder]?
 *
 * Ptát se na obě větve naráz nejde: otevřená konverzace hlavního seznamu o archivu nic neříká.
 * Nad prázdným archivem by kvůli ní zůstal dvoupanel a vedle prázdného stavu by stál
 * placeholder „vyber chat vlevo" — dva prázdné stavy, z nichž pravý radí něco, co udělat nejde
 * (viz `nothingToPick` v `AppShell`).
 */
internal fun List<NavKey>.hasOpenConversation(folder: ChatFolder): Boolean = when (folder) {
    // Seznam témat stojí na místě konverzace (skupina s tématy se otevírá jím). Složka Telegramu
    // je výběr z hlavního seznamu a sdílí jeho větev.
    // Zamčené chaty se otevírají v hlavním zásobníku (`LockedChatsRoute`).
    ChatFolder.Main, is ChatFolder.Custom, ChatFolder.Locked -> any { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute }
    ChatFolder.Archive -> any { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute || it is ArchiveDirectTopicsRoute }
}

/**
 * Zpět z archivu odebere CELOU jeho větev, ne jen seznam: archiv má vlastní detail panel,
 * takže by po něm v zásobníku zůstala konverzace bez seznamu, ke kterému patří. Hlavní seznam
 * se tím vrátí i s tím, co v něm bylo otevřené.
 */
internal fun MutableList<NavKey>.closeArchive() {
    removeAll {
        it is ArchiveRoute || it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute ||
            it is ArchiveDirectTopicsRoute || it is ArchiveChatProfileRoute || it is ArchiveChatCalendarRoute
    }
}

/**
 * Chat změnil složku, takže jeho otevřená konverzace v té OPUŠTĚNÉ se zavře — jinak by
 * v archivu zůstal viset chat, který v něm už není (a naopak). Kam se přesunul, říká
 * [ArchiveChange.archived]; zavírá se vždycky ta druhá větev, takže jedna funkce obslouží
 * hlavní seznam i archiv.
 */
internal fun MutableList<NavKey>.closeConversationOf(slot: AccountSlot, change: ArchiveChange) {
    val ids: Set<Long?> = change.chatIds.toSet()
    if (change.archived) {
        removeAll {
            it.belongsTo(slot) && ((it as? ConversationRoute)?.chatId ?: (it as? ForumTopicsRoute)?.chatId ?: (it as? DirectTopicsRoute)?.chatId) in ids
        }
    } else {
        removeAll {
            it.belongsTo(slot) && ((it as? ArchiveConversationRoute)?.chatId ?: (it as? ArchiveForumTopicsRoute)?.chatId
                ?: (it as? ArchiveDirectTopicsRoute)?.chatId) in ids
        }
    }
}

/**
 * Chat otevřený z archivu se vrátil mezi chaty (pruh nad konverzací u automaticky
 * archivovaného chatu), takže se s ním PŘESTĚHUJE i konverzace: archivní větev se zavře
 * a chat se otevře v hlavním seznamu. Zavřít ji jen jako u [closeConversationOf] by uživatele
 * vyhodilo z chatu, kterému právě dal najevo, že o něj stojí; nechat ji v archivu by znamenalo
 * konverzaci vedle seznamu, ve kterém chat už není.
 *
 * Konverzace otevřená v hlavním seznamu nahradí tu, která tam byla — stejně jako výběr chatu
 * ze seznamu.
 */
internal fun MutableList<NavKey>.moveConversationToMain(slot: AccountSlot, chatId: Long) {
    if (none { route -> route is ArchiveConversationRoute && route.slot == slot && route.chatId == chatId }) return
    removeAll {
        when (it) {
            // Celá archivní větev, i úpravy a práva členů nad profilem.
            is ArchiveRoute, is ArchiveConversationRoute, is ArchiveForumTopicsRoute, is ArchiveDirectTopicsRoute,
            is ArchiveChatProfileRoute, is ArchiveChatCalendarRoute,
            is ArchiveEditChatRoute, is ArchiveMemberRightsRoute, is ArchiveInviteLinksRoute, is ArchiveJoinRequestsRoute,
            is ArchiveRemovedMembersRoute, is ArchiveEventLogRoute, is ArchiveChatStatisticsRoute, is ArchivePostStatisticsRoute, is ArchiveChatInsightsRoute,
            -> true
            // Detail hlavního seznamu patřil jinému chatu; nový chat ho nahradí celý, jinak by
            // zpět z něj vedlo do profilu toho předchozího.
            is ConversationRoute, is ForumTopicsRoute, is SavedTopicsRoute, is DirectTopicsRoute, is ChatProfileRoute, is ChatCalendarRoute,
            is EditChatRoute, is MemberRightsRoute, is InviteLinksRoute, is JoinRequestsRoute, is RemovedMembersRoute, is EventLogRoute,
            is ChatStatisticsRoute, is PostStatisticsRoute, is ChatInsightsRoute,
            -> true
            else -> false
        }
    }
    add(ConversationRoute(slot, chatId))
}

/**
 * Chat je pryč (smazaný, opuštěný): zavře se všechno, co ho ukazuje — konverzace, profil
 * a prohlížeč jeho médií, v hlavním seznamu i v archivu. Zůstat by po něm nemělo nic, co by
 * šlo zpátky otevřít jen proto, aby se ukázalo prázdné.
 */
internal fun MutableList<NavKey>.closeChat(slot: AccountSlot, chatId: Long) {
    removeAll {
        it.belongsTo(slot) && when (it) {
            is ConversationRoute -> it.chatId == chatId
            is ArchiveConversationRoute -> it.chatId == chatId
            is ForumTopicsRoute -> it.chatId == chatId
            is DirectTopicsRoute -> it.chatId == chatId
            is ArchiveForumTopicsRoute -> it.chatId == chatId
            is ArchiveDirectTopicsRoute -> it.chatId == chatId
            is ChatProfileRoute -> it.chatId == chatId
            is ArchiveChatProfileRoute -> it.chatId == chatId
            is ChatCalendarRoute -> it.chatId == chatId
            is ArchiveChatCalendarRoute -> it.chatId == chatId
            is MediaRoute -> it.chatId == chatId
            // Úprava a všechno nad ní — chat smazaný pro všechny se maže právě z úpravy.
            is EditChatRoute -> it.chatId == chatId
            is ArchiveEditChatRoute -> it.chatId == chatId
            is MemberRightsRoute -> it.chatId == chatId
            is ArchiveMemberRightsRoute -> it.chatId == chatId
            is InviteLinksRoute -> it.chatId == chatId
            is ArchiveInviteLinksRoute -> it.chatId == chatId
            is JoinRequestsRoute -> it.chatId == chatId
            is ArchiveJoinRequestsRoute -> it.chatId == chatId
            is RemovedMembersRoute -> it.chatId == chatId
            is ArchiveRemovedMembersRoute -> it.chatId == chatId
            is EventLogRoute -> it.chatId == chatId
            is ArchiveEventLogRoute -> it.chatId == chatId
            is ChatStatisticsRoute -> it.chatId == chatId
            is ArchiveChatStatisticsRoute -> it.chatId == chatId
            is ChatInsightsRoute -> it.chatId == chatId
            is ArchiveChatInsightsRoute -> it.chatId == chatId
            is PostStatisticsRoute -> it.chatId == chatId
            is ArchivePostStatisticsRoute -> it.chatId == chatId
            else -> false
        }
    }
}

/**
 * Základní skupina [old] se převedla na supergroupu [new] — jiné id chatu. Konverzace a profil
 * staré skupiny se přepíšou na novou (bez skoku na zprávu: id zpráv v nové skupině jsou jiná),
 * prohlížeč médií a obrazovky nad úpravou zmizí.
 *
 * **Úprava ([EditChatRoute]) zůstává se starým id**: obrazovka je zrovna otevřená a její
 * ViewModel se na nové id přepnul sám (`EditChatViewModel.confirmUpgrade`) — nahradit ji by
 * zahodilo rozepsané texty i hlášku o výsledku.
 */
internal fun MutableList<NavKey>.replaceUpgradedChat(slot: AccountSlot, old: Long, new: Long) {
    removeAll {
        it.belongsTo(slot) && when (it) {
            is MediaRoute -> it.chatId == old
            // Kalendář počítal historii staré skupiny; nová má jiná id zpráv.
            is ChatCalendarRoute -> it.chatId == old
            is ArchiveChatCalendarRoute -> it.chatId == old
            is MemberRightsRoute -> it.chatId == old
            is ArchiveMemberRightsRoute -> it.chatId == old
            is InviteLinksRoute -> it.chatId == old
            is ArchiveInviteLinksRoute -> it.chatId == old
            is JoinRequestsRoute -> it.chatId == old
            is ArchiveJoinRequestsRoute -> it.chatId == old
            is RemovedMembersRoute -> it.chatId == old
            is ArchiveRemovedMembersRoute -> it.chatId == old
            is EventLogRoute -> it.chatId == old
            is ArchiveEventLogRoute -> it.chatId == old
            is ChatStatisticsRoute -> it.chatId == old
            is ArchiveChatStatisticsRoute -> it.chatId == old
            is ChatInsightsRoute -> it.chatId == old
            is ArchiveChatInsightsRoute -> it.chatId == old
            else -> false
        }
    }
    // Po indexech: `MutableList.replaceAll` je na Kotlin/Native experimentální.
    for (index in indices) {
        val route = this[index]
        if (!route.belongsTo(slot)) continue
        val replaced = when (route) {
            is ConversationRoute -> if (route.chatId == old) ConversationRoute(route.slot, new) else route
            is ArchiveConversationRoute -> if (route.chatId == old) ArchiveConversationRoute(route.slot, new) else route
            is ChatProfileRoute -> if (route.chatId == old) ChatProfileRoute(route.slot, new) else route
            is ArchiveChatProfileRoute -> if (route.chatId == old) ArchiveChatProfileRoute(route.slot, new) else route
            else -> route
        }
        if (replaced != route) this[index] = replaced
    }
}

/**
 * Chat otevřený NAD konverzací (zmínka, odkaz, zdroj přeposlání), takže zpět vede do ní. Kam
 * přesně, říká [ChatTarget]: vlákno komentářů, téma, seznam témat skupiny s tématy, nebo chat —
 * ten jen tehdy, když už na vrcholu zásobníku není (zmínka téhož chatu by ho otevřela podruhé).
 *
 * Téma se přidává rovnou, bez seznamu témat pod sebou: zpět z něj má vést tam, odkud uživatel
 * přišel, ne do seznamu, který nikdy neviděl. [archive] vybírá větev, do které se píše.
 */
internal fun MutableList<NavKey>.openTarget(
    slot: AccountSlot,
    target: ChatTarget,
    archive: Boolean,
    /** Konverzace, ze které se otevírá; s [beside] jde vlákno komentářů do bočního sloupce vedle ní. */
    from: NavKey? = null,
    /** Panely vedle sebe: vlákno komentářů se otevře vedle [from] ([openCommentsBeside]). */
    beside: Boolean = false,
) {
    val chatId = target.chatId
    when {
        target.threadId != null && beside && from != null -> openCommentsBeside(from, slot, chatId, target.threadId, archive)
        target.threadId != null -> add(conversation(slot, archive, chatId, threadId = target.threadId))
        target.topicId != null -> {
            val top = lastOrNull()
            val sameTopic = if (archive) {
                top is ArchiveConversationRoute && top.chatId == chatId && top.topicId == target.topicId
            } else {
                top is ConversationRoute && top.chatId == chatId && top.topicId == target.topicId
            }
            if (!sameTopic) add(conversation(slot, archive, chatId, target.messageId, topicId = target.topicId))
        }
        target.forum -> {
            val topics: NavKey = if (archive) ArchiveForumTopicsRoute(slot, chatId) else ForumTopicsRoute(slot, chatId)
            if (lastOrNull() != topics) add(topics)
        }
        else -> {
            val top = lastOrNull()
            // Otevřený je jen CELÝ chat: z tématu nebo vlákna téže skupiny se do něj jít musí.
            val open = if (archive) {
                top is ArchiveConversationRoute && top.chatId == chatId && top.topicId == null && top.threadId == null
            } else {
                top is ConversationRoute && top.chatId == chatId && top.topicId == null && top.threadId == null
            }
            if (!open) add(conversation(slot, archive, chatId, target.messageId))
        }
    }
}

/**
 * Nalezená zpráva z hledání v seznamu chatů: nahradí otevřenou konverzaci jako výběr chatu.
 * Zpráva v tématu se otevře v TÉMATU a pod ním seznam témat, jako by k ní uživatel došel
 * ze seznamu chatů — zpět tak vede tam, kam by vedl z tématu otevřeného ručně. Bez [messageId]
 * se téma jen otevře (koncept v tématu z přehledu konceptů).
 */
internal fun MutableList<NavKey>.openFoundMessage(slot: AccountSlot, chatId: Long, messageId: Long?, topicId: Int?) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute }
    if (topicId != null) add(ForumTopicsRoute(slot, chatId))
    add(ConversationRoute(slot, chatId, messageId, topicId = topicId))
}

/**
 * Vlákno komentářů z přehledu konceptů: nahradí otevřenou konverzaci jako výběr chatu. Kanál
 * pod ním není — zpět vede do seznamu chatů, odkud uživatel přišel, ne do kanálu, který neotevřel.
 */
internal fun MutableList<NavKey>.openCommentThread(slot: AccountSlot, chatId: Long, threadId: Long) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute }
    add(ConversationRoute(slot, chatId, threadId = threadId))
}

/**
 * Vlákno komentářů v bočním sloupci vedle konverzace [from] (čip pod příspěvkem kanálu na široké
 * ploše). Sloupec je jeden, takže co v něm leželo (profil, kalendář, komentáře jiného příspěvku),
 * se zavře a vlákno ho nahradí. Otevírá-li se z vlákna ve sloupci (odkaz na jiný příspěvek),
 * patří nové vlákno ke kanálu pod ním a staré nahradí. Totéž vlákno podruhé nic nedělá.
 */
internal fun MutableList<NavKey>.openCommentsBeside(from: NavKey, slot: AccountSlot, chatId: Long, threadId: Long, archive: Boolean) {
    val index = lastIndexOf(from)
    if (index < 0) return
    val owner = if (from.isBesideThread()) index - 1 else index
    if (owner < 0) return
    val thread = conversation(slot, archive, chatId, threadId = threadId, beside = true)
    if (lastIndex == owner + 1 && last() == thread) return
    while (lastIndex > owner) removeAt(lastIndex)
    add(thread)
}

/** Vlákno komentářů v bočním sloupci ([ConversationRoute.beside]). */
internal fun NavKey.isBesideThread(): Boolean =
    (this as? ConversationRoute)?.beside == true || (this as? ArchiveConversationRoute)?.beside == true

private fun NavKey.isCommentThread(): Boolean =
    (this as? ConversationRoute)?.threadId != null || (this as? ArchiveConversationRoute)?.threadId != null

private fun conversation(
    slot: AccountSlot,
    archive: Boolean,
    chatId: Long,
    messageId: Long? = null,
    threadId: Long? = null,
    topicId: Int? = null,
    beside: Boolean = false,
): NavKey =
    if (archive) {
        ArchiveConversationRoute(slot, chatId, messageId, threadId = threadId, topicId = topicId, beside = beside)
    } else {
        ConversationRoute(slot, chatId, messageId, threadId = threadId, topicId = topicId, beside = beside)
    }

/** Přidá routu, jen když už nahoře není — dvojí ťuknutí nesmí obrazovku otevřít dvakrát. */
internal fun MutableList<NavKey>.addOnce(route: NavKey) {
    if (lastOrNull() != route) add(route)
}

/**
 * Leží [route] v zásobníku přímo na seznamu chatů (nebo archivu)? Jen pak obrazovka chatu
 * vyrůstá z řádku (`ChatContainer`). Téma nad seznamem témat má tentýž chat, ale otevírá se
 * z jiné obrazovky.
 *
 * Volající si výsledek pamatuje: při návratu je [route] ze zásobníku pryč dřív, než doběhne
 * přechod, a kontejner musí vydržet až do konce.
 */
internal fun List<NavKey>.liesOnChatList(route: NavKey): Boolean {
    val index = lastIndexOf(route)
    return index > 0 && this[index - 1].let { it == ChatsRoute || it == ArchiveRoute }
}

/**
 * Ťuknutí na hlavičku konverzace (nebo seznamu témat) [owner]. Na široké ploše stojí profil
 * vedle konverzace, takže druhé ťuknutí ho zavře i s tím, co se z něj otevřelo — jako panel
 * s informacemi v Telegram Desktop. Na telefonu profil konverzaci nahradí a zpět vede z něj.
 *
 * Co v zásobníku leží nad konverzací, na kterou jde ťuknout, je na široké ploše jen třetí panel:
 * detail by ji nahradil a obrazovka přes celou plochu zakryl.
 */
/**
 * Kalendář jako panel vedle konverzace [owner]. Co nad ní v panelu leželo (profil), se zavře —
 * třetí panel je jeden. Byl-li navrchu kalendář téhož chatu, jen se zavře, jako ⋮ u profilu.
 */
internal fun MutableList<NavKey>.toggleCalendar(owner: NavKey, calendar: NavKey) {
    val index = lastIndexOf(owner)
    if (index < 0) return
    val top = lastOrNull()
    val wasOpen = top != null && top::class == calendar::class && top.calendarChatId() == calendar.calendarChatId() && index < lastIndex
    while (lastIndex > index) removeAt(lastIndex)
    if (!wasOpen) add(calendar)
}

internal fun MutableList<NavKey>.toggleProfile(owner: NavKey, profile: NavKey, sideBySide: Boolean) {
    val index = lastIndexOf(owner)
    if (sideBySide && index in 0 until lastIndex) {
        while (lastIndex > index) removeAt(lastIndex)
    } else {
        add(profile)
    }
}

/**
 * Šipka zpět v konverzaci [route]. Na telefonu vede o krok zpět jako systémové zpět ([pop]).
 * Vedle seznamu [list] (hlavní seznam, nebo archiv) zavře celou větev nad ním — konverzace
 * otevřené zmínkami jedna z druhé i profil — a zůstane seznam s prázdným panelem: konverzace se
 * zavírá, ne prochází zpátky. Systémové zpět a Esc dál jdou po jedné položce.
 *
 * Téma nad seznamem témat se vrací do toho seznamu: je to jeho rodič, ne historie. Stejně
 * vlákno komentářů nad konverzací — otevřelo se čipem pod příspěvkem kanálu, takže zpět vede
 * do kanálu, ne až do seznamu. Vlákno přímo na seznamu (z přehledu konceptů) se zavře jako
 * každá jiná konverzace.
 */
internal fun MutableList<NavKey>.closeConversation(route: NavKey, list: NavKey, sideBySide: Boolean): Boolean {
    val index = lastIndexOf(route)
    if (!sideBySide || index <= 0) return pop(route)
    val parent = this[index - 1]
    val keep = when {
        parent is ForumTopicsRoute || parent is ArchiveForumTopicsRoute || parent is SavedTopicsRoute ||
            parent is DirectTopicsRoute || parent is ArchiveDirectTopicsRoute -> index
        route.isCommentThread() && (parent is ConversationRoute || parent is ArchiveConversationRoute) -> index
        else -> subList(0, index).lastIndexOf(list).takeIf { it >= 0 }?.plus(1) ?: return pop(route)
    }
    while (size > keep) removeAt(lastIndex)
    return true
}

/**
 * Panel s profilem zůstává otevřený, i když se vedle něj přepne konverzace — jako panel
 * s informacemi v Telegram Desktop. Na široké ploše.
 *
 * Profil patří ke konverzaci POD sebou, takže když se nad něj otevře jiná konverzace (jiný chat
 * ze seznamu, zmínka, „Napsat zprávu" členovi), zakrytý profil i co se z něj otevřelo ze
 * zásobníku zmizí ([coveredExtras]) a místo něj se hned za novou konverzaci vloží JEJÍ profil.
 * Panel tak drží, co uživatel otevřel, a ukazuje chat, který je vedle.
 *
 * Konverzace bez profilu (Uložené zprávy po chatech, článek) panel zavře. Zavřený panel (✕,
 * ťuknutí na hlavičku) se s přepnutím neotevírá — žádný profil v zásobníku není co přenést.
 *
 * Co se vloží, říká KOŘEN zakrytého sloupce (položka hned nad konverzací): kalendář přejde na
 * kalendář nové konverzace, profil na její profil a vlákno komentářů se jen zavře — patří
 * ke kanálu, ne k chatu, který ho zakryl.
 *
 * Vrací, jestli se zásobník změnil. Na telefonu se nevolá: tam je profil pod konverzací
 * obyčejná historie a zpět se do něj vrací.
 */
internal fun MutableList<NavKey>.followProfile(paneOf: (NavKey) -> PaneInfo?): Boolean {
    val covered = coveredExtras(paneOf)
    if (covered.isEmpty()) return false
    // Kořen zakrytého sloupce v každé scéně: indexy jdou od konce, kořen má nejmenší.
    val roots = HashMap<Any, NavKey>()
    for (index in covered) {
        val scene = paneOf(this[index])?.scene ?: continue
        roots[scene] = this[index]
    }
    covered.forEach(::removeAt)
    for ((scene, root) in roots) {
        val detail = indexOfLast { paneOf(it)?.let { info -> info.role == PaneRole.Detail && info.scene == scene } == true }
        if (detail < 0) continue
        // Profil téže scény nad konverzací už je (otevřel se v témže kroku) — nic nepřidávat.
        val hasExtra = subList(detail + 1, size).any { paneOf(it)?.let { info -> info.role == PaneRole.Extra && info.scene == scene } == true }
        val follower = when {
            root.isBesideThread() -> null
            root.calendarChatId() != null -> this[detail].calendarRoute()
            else -> this[detail].profileRoute()
        }
        if (!hasExtra && follower != null) add(detail + 1, follower)
    }
    return true
}

/** [followProfile] nad kopií — co se kreslí, než efekt opraví skutečný zásobník. */
internal fun List<NavKey>.withProfileFollowing(paneOf: (NavKey) -> PaneInfo?): List<NavKey> =
    if (coveredExtras(paneOf).isEmpty()) this else toMutableList().apply { followProfile(paneOf) }

/**
 * Kalendář chatu, který stojí v detailu; `null` = obrazovka bez kalendáře. Jen konverzace, kde
 * jde skok na datum (ne vlákno a téma skupiny).
 */
private fun NavKey.calendarRoute(): NavKey? = when (this) {
    is ConversationRoute -> ChatCalendarRoute(slot, chatId, savedTopicId = savedTopicId, directTopicId = directTopicId)
        .takeIf { threadId == null && topicId == null }
    is ArchiveConversationRoute -> ArchiveChatCalendarRoute(slot, chatId, directTopicId = directTopicId)
        .takeIf { threadId == null && topicId == null }
    else -> null
}

/** Chat kalendáře zpráv (hlavní seznam i archiv); `null` = není to kalendář. */
private fun NavKey.calendarChatId(): Long? = when (this) {
    is ChatCalendarRoute -> chatId
    is ArchiveChatCalendarRoute -> chatId
    else -> null
}

/**
 * Stojí [route] přímo nad konverzací nebo seznamem témat, tedy jako kořen bočního panelu? Takový
 * profil nemá šipku zpět ani ✕ — panel zavírá přepínač v hlavičce vedle (`ProfilePaneButton`).
 * Profil nad jiným profilem (člen z profilu skupiny) šipku zpět má.
 */
internal fun List<NavKey>.standsOnDetail(route: NavKey): Boolean {
    val index = lastIndexOf(route)
    return index > 0 && this[index - 1].profileRoute() != null
}

/**
 * Stojí profil [route] přímo nad konverzací s CELÝM týmž chatem (ne vlákno, téma ani seznam
 * témat)? Pak akce nad chatem nabízí ⋮ té konverzace a profil vlastní nemá (`ChatActions`).
 * Profil člena nad konverzací skupiny je jiný chat — ten si nabídku nechává.
 */
internal fun List<NavKey>.standsOnOwnConversation(route: NavKey): Boolean {
    val index = lastIndexOf(route)
    if (index <= 0) return false
    return when (val below = this[index - 1]) {
        is ConversationRoute -> below.threadId == null && below.topicId == null && below.savedTopicId == null &&
            below.directTopicId == null && below.profileRoute() == route
        is ArchiveConversationRoute -> below.threadId == null && below.topicId == null &&
            below.directTopicId == null && below.profileRoute() == route
        else -> false
    }
}

/**
 * Profil chatu, který stojí v detailu; `null` = obrazovka bez profilu. Vlákno komentářů v bočním
 * sloupci detail není: profil nad ním je profil člena, ne kořen sloupce.
 */
private fun NavKey.profileRoute(): NavKey? = when (this) {
    is ConversationRoute -> ChatProfileRoute(slot, chatId).takeIf { !beside }
    is ForumTopicsRoute -> ChatProfileRoute(slot, chatId)
    is DirectTopicsRoute -> ChatProfileRoute(slot, chatId)
    is ArchiveConversationRoute -> ArchiveChatProfileRoute(slot, chatId).takeIf { !beside }
    is ArchiveForumTopicsRoute -> ArchiveChatProfileRoute(slot, chatId)
    is ArchiveDirectTopicsRoute -> ArchiveChatProfileRoute(slot, chatId)
    else -> null
}

/**
 * Patří nad tuhle obrazovku čip sdíleného obsahu? Jen nad položku panelu (seznam, archiv,
 * konverzace, profil), ne nad obrazovku přes celou plochu bez role (prohlížeč médií, „Komu: …").
 */
internal fun List<NavKey>.showsShareChip(paneOf: (NavKey) -> PaneInfo?): Boolean =
    lastOrNull()?.let(paneOf) != null

/**
 * Chat konverzace, která je zrovna vidět — tam jde sdílený obsah z čipu vložit; jinde `null`.
 *
 * Na telefonu musí být konverzace navrchu. Vedle seznamu ([sideBySide]) může nad ní ležet
 * profil (třetí panel, konverzace zůstává vidět); seznam navrchu naopak znamená, že vpravo je
 * placeholder, nebo konverzace jiné větve (hlavní seznam pod archivem), která vidět není.
 */
internal fun List<NavKey>.shareInsertChat(paneOf: (NavKey) -> PaneInfo?, sideBySide: Boolean): InsertChat? {
    val top = lastOrNull() ?: return null
    if (!sideBySide) return top.conversationChat()
    if (paneOf(top) == null) return null
    for (key in asReversed()) {
        when (paneOf(key)?.role) {
            PaneRole.Extra -> continue
            PaneRole.Detail -> return key.conversationChat()
            else -> return null
        }
    }
    return null
}

private fun NavKey.conversationChat(): InsertChat? = when (this) {
    is ConversationRoute -> InsertChat(slot, chatId)
    is ArchiveConversationRoute -> InsertChat(slot, chatId)
    else -> null
}

/**
 * Položku [route] nahradí [replacement] na témže místě zásobníku — co leží pod ní, zůstane
 * (konverzace otevřená odkazem z jiného chatu se vrací do toho chatu). Když už v zásobníku není,
 * nestane se nic.
 */
internal fun MutableList<NavKey>.replaceRoute(route: NavKey, replacement: NavKey) {
    val index = lastIndexOf(route)
    if (index >= 0) set(index, replacement)
}

/**
 * Patří routa účtu [slot]? Id chatu je číslo jednoho účtu: soukromý chat má v obou účtech id
 * protějšku a společná skupina totéž id, takže se zásobník nesmí prohledávat jen podle id —
 * v režimu Vše v něm leží chaty víc účtů (`docs/navrh-vice-uctu.md`, „Společný seznam chatů").
 */
internal fun NavKey.belongsTo(slot: AccountSlot): Boolean = (this as? AccountRoute)?.slot == slot

/**
 * Seznam, ke kterému se vrací konverzace [route] vedle panelů ([closeConversation]): hledání, leží-li
 * pod ní (výsledky vlevo zůstanou), jinak hlavní seznam chatů.
 */
internal fun List<NavKey>.mainListBelow(route: NavKey): NavKey {
    val index = lastIndexOf(route).takeIf { it >= 0 } ?: size
    return subList(0, index).lastOrNull { it is ChatsRoute || it is ChatSearchRoute } ?: ChatsRoute
}
