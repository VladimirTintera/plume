package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.AppScreen

/**
 * Kam seznam chatů odchází: chaty, menu účtu, „+" a obrazovky aplikace. Navigaci řeší shell,
 * ne seznam sám.
 *
 * `data class` ze stejného důvodu jako [eu.tintera.plume.features.conversation.ConversationNavigation]:
 * shell ji skládá při každé rekompozici a lambdy si Compose pamatuje (strong skipping), takže
 * dvě po sobě složené instance jsou si rovné a seznam se kvůli nim znovu neskládá.
 */
data class ChatListNavigation(
    val onChatClick: (Long) -> Unit,
    /** Skupina s tématy: otevře se seznam témat, ne konverzace. */
    val onForumClick: (Long) -> Unit,
    /** Téma skupiny (koncept v něm z přehledu konceptů); bez něj se otevře seznam témat. */
    val onTopicClick: (chatId: Long, topicId: Int) -> Unit = { chatId, _ -> onForumClick(chatId) },
    /** Vlákno komentářů v diskusní skupině (koncept v něm z přehledu konceptů). */
    val onThreadClick: (chatId: Long, threadId: Long) -> Unit = { chatId, _ -> onChatClick(chatId) },
    /** Nalezená zpráva: otevřít její chat a skočit na ni. */
    val onMessageClick: (chatId: Long, messageId: Long, topicId: Int?) -> Unit,
    val onOpenArchive: () -> Unit,
    /** Menu účtu → Hovory; `null` = položka není (platforma bez hovorů). */
    val onOpenCalls: (() -> Unit)? = null,
    /** Menu účtu → Stahování. */
    val onOpenDownloads: () -> Unit = {},
    val onOpenMyStories: () -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onNewGroup: () -> Unit = {},
    val onNewChannel: () -> Unit = {},
    val onChatArchived: ((ArchiveChange) -> Unit)? = null,
    /** Menu účtu → Nastavení. Na telefonu jediný vstup, spodní lišta není. */
    val onOpenSettings: () -> Unit = {},
    val onOpenProxySettings: () -> Unit = {},
    /** „+" v řádku složek → Nová složka; otevře editor složky v Nastavení. */
    val onCreateFolder: () -> Unit = {},
    /** „+" v řádku složek → Spravovat složky. */
    val onManageFolders: () -> Unit = {},
    /** Editor jedné složky (z nabídky nad záložkou a z prázdné složky). */
    val onEditFolder: (folderId: Int) -> Unit = { onManageFolders() },
    /** Obrazovka aplikace z doporučené akce (dvoufázové ověření, úprava profilu). */
    val onOpenScreen: (AppScreen) -> Unit = {},
    /** Hledání ze sbaleného sloupce; otevře se, až se seznam rozbalí (viz [CollapsedChatList]). */
    val onOpenSearch: () -> Unit = {},
    /** Příběhy autora (kroužek u avataru, hromádka v pilulce). */
    val onOpenStories: (Long) -> Unit = {},
    /** Nový příběh z vybrané fotky nebo videa (cesta, je to video?); `null` = položka v „+" není. */
    val onComposeStory: ((String, Boolean) -> Unit)? = null,
)
