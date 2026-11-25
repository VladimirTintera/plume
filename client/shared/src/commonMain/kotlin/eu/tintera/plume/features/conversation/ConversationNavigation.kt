package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.AppScreen

/**
 * Kam konverzace odchází: všechno, co řeší navigace v shellu, ne obrazovka sama.
 *
 * Jeden objekt místo dvaceti parametrů [ConversationScreen]: ta je v dexu na hraně registrů
 * a každý parametr composable funkce stojí registr navíc (a s ním příznaky `$changed`).
 *
 * `data class` kvůli přeskakování: volající ji skládá při každé rekompozici znovu, takže
 * by se jinak lišila referencí a konverzace by se skládala s ním. Lambdy volajícího si
 * Compose pamatuje (strong skipping), takže dvě po sobě složené instance jsou si rovné.
 */
data class ConversationNavigation(
    val onBack: () -> Unit,
    val onOpenMedia: (messageId: Long) -> Unit,
    /** Video od daného místa — čas v médiu („1:23" v textu). */
    val onOpenMediaAt: (messageId: Long, seconds: Int) -> Unit = { messageId, _ -> onOpenMedia(messageId) },
    val onForward: (messageIds: List<Long>) -> Unit,
    /** „Odpovědět v jiném chatu": výběr chatu, kde se na zprávu odpoví. */
    val onReplyElsewhere: (messageId: Long) -> Unit = {},
    /** Příloha ankety v prohlížeči; `index` viz [MessageAction.OpenPollMedia]. */
    val onOpenPollMedia: (messageId: Long, index: Int) -> Unit = { _, _ -> },
    /** Médium článku v prohlížeči; `index` viz [MessageAction.OpenArticleMedia]. */
    val onOpenArticleMedia: (messageId: Long, index: Int) -> Unit = { _, _ -> },
    /** Video reklamy na celé obrazovce (prohlížeč s tlačítkem reklamy). */
    val onOpenSponsoredMedia: (messageId: Long) -> Unit = {},
    /** Videochat chatu na poslech (ťuknutí na pruh nad konverzací). */
    val onOpenVideoChat: (groupCallId: Int) -> Unit = {},
    /** Příběh v prohlížeči — citace odpovědi na příběh, odkaz `t.me/jméno/s/N`. */
    val onOpenStory: (StoryTarget) -> Unit = {},
    /**
     * Otevřít chat nad konverzací (zmínka, odkaz, komentáře); kam přesně — zpráva, vlákno,
     * téma, seznam témat —, říká [ChatTarget].
     */
    val onOpenChat: (ChatTarget) -> Unit,
    /** Hashtag v textu: hledání zpráv s tagem v seznamu chatů. */
    val onSearch: (query: String) -> Unit,
    /** Ťuknutí na hlavičku: profil chatu. */
    val onOpenProfile: () -> Unit,
    /**
     * Kalendář zpráv (třetí panel, na telefonu přes celou obrazovku); `null` = konverzace bez
     * kalendáře. [anchor] = zpráva, na které konverzace stojí — vstup do ostrůvku historie.
     */
    val onOpenCalendar: ((anchor: Long?) -> Unit)? = null,
    /** Profil jiného chatu nad konverzací — odesílatel ze skupiny, na jehož tvář se ťuklo. */
    val onOpenUserProfile: (chatId: Long) -> Unit,
    /** Statistika příspěvku v kanálu z „Informace o zprávě" (jen pro správce, `canGetStatistics`). */
    val onOpenPostStatistics: (messageId: Long) -> Unit = {},
    /**
     * Chat se z pruhu vrátil z archivu mezi chaty. Jen konverzace otevřená Z ARCHIVU s tím
     * něco dělá (přestěhuje se do hlavního seznamu); jinde se nic měnit nemusí.
     */
    val onUnarchived: () -> Unit = {},
    /** Chat je smazaný nebo opuštěný (⋮ v hlavičce, pruh proti spamu) — zavřít všechno, co ho ukazuje. */
    val onChatGone: () -> Unit = onBack,
    /** Sponka → Anketa (a tlačítko bota „vytvořit anketu"): formulář nové ankety nad konverzací. */
    val onCreatePoll: (PollComposerRequest) -> Unit = {},
    /** Sponka → Seznam úkolů: formulář nového seznamu nad konverzací. */
    val onCreateChecklist: (ChecklistComposerRequest) -> Unit = {},
    /** Mini aplikace bota — obrazovka přes celou plochu nad konverzací. */
    val onOpenWebApp: (WebAppRequest) -> Unit = {},
    /** Tlačítko bota „vyber lidi / chat": výběr nad konverzací. */
    val onOpenBotShare: (BotShareRequest) -> Unit = {},
    /** Odkaz `t.me/bot?startgroup=…`: výběr skupiny, do které bota přidat. */
    val onAddBotToGroup: (InternalLink.BotStartInGroup) -> Unit = {},
    /** Odkaz na obrazovku aplikace (`tg://settings/…`, `tg://new/group`) — navigaci řeší shell. */
    val onOpenScreen: (AppScreen) -> Unit = {},
    /** Tlačítko bota s inline dotazem do jiného chatu: výběr chatu. */
    val onSwitchInline: (SwitchInlinePick) -> Unit = {},
    /** Pruh „Pozvat členy" nad čerstvou skupinou a ⋮ → Přidat členy: výběr lidí. */
    val onAddMembers: () -> Unit = {},
    /** ⋮ → Sdílet kontakt: výběr chatů, do kterých kontakt člověka [userId] poslat. */
    val onShareContact: (userId: Long) -> Unit = {},
    /** Pruh s čekajícími žádostmi o vstup: otevře jejich seznam. */
    val onOpenJoinRequests: () -> Unit = {},
    /**
     * Uložené zprávy jako seznam témat podle zdrojového chatu. `null` = nenabízet (každá jiná
     * konverzace i téma samo).
     */
    val onViewAsChats: (() -> Unit)? = null,
    /**
     * Skupina s tématy jako seznam témat (volba se uloží na serveru, viz
     * [ConversationViewModel.viewAsTopics]). `null` = nenabízet.
     */
    val onViewAsTopics: (() -> Unit)? = null,
    /**
     * Přímé zprávy kanálu, které uživatel spravuje: celý chat se nečte jako konverzace, ale jako
     * vlákna po odběratelích (psát se dá jen do vlákna). Konverzace se tím sama nahradí, ať do ní
     * vede kterákoli cesta (seznam, hledání, odkaz). `null` = konverzace zůstane, jak je.
     */
    val onViewAsDirectTopics: (() -> Unit)? = null,
    /**
     * Rychlý přepínač (podržení šipky zpět): vybraný chat místo téhle konverzace. `null` =
     * nenabízet (bublina konverzace).
     */
    val onSwitchChat: ((ChatSummary) -> Unit)? = null,
)

/**
 * Jednorázové události, které vedou mimo obrazovku: ViewModel řekne „otevři chat"
 * ([ConversationViewModel.navigation], [ConversationNavEvent]), navigace je věc shellu.
 *
 * Navigace se čte přes [rememberUpdatedState]: odběr běží po celou dobu obrazovky a volá
 * vždycky aktuální [ConversationNavigation] (a u ankety aktuální odpověď v poli), ne tu
 * z okamžiku spuštění.
 */
@Composable
internal fun ConversationEvents(
    state: ConversationState,
    viewModel: ConversationViewModel,
    navigation: ConversationNavigation,
) {
    val nav by rememberUpdatedState(navigation)
    val current by rememberUpdatedState(state)

    // Spravované přímé zprávy kanálu se čtou po vláknech; celý chat by psaní stejně odmítl.
    val directAdmin = state.info?.isDirectMessagesAdmin == true && state.directTopicId == null
    LaunchedEffect(directAdmin) { if (directAdmin) nav.onViewAsDirectTopics?.invoke() }

    // Chat se smazal (⋮ v hlavičce, pruh proti spamu) — konverzace nemá co ukazovat.
    val chatGone by viewModel.chatGone.collectAsStateWithLifecycle()
    LaunchedEffect(chatGone) { if (chatGone) nav.onChatGone() }
    val unarchived by viewModel.unarchived.collectAsStateWithLifecycle()
    LaunchedEffect(unarchived) { if (unarchived) nav.onUnarchived() }

    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                // Výběr cílového chatu je vlastní obrazovka.
                is ConversationNavEvent.Forward -> nav.onForward(event.messageIds)
                is ConversationNavEvent.ReplyElsewhere -> nav.onReplyElsewhere(event.messageId)
                is ConversationNavEvent.OpenMediaAt -> nav.onOpenMediaAt(event.target.messageId, event.target.seconds)
                is ConversationNavEvent.OpenChat -> nav.onOpenChat(event.target)
                is ConversationNavEvent.OpenScreen -> nav.onOpenScreen(event.screen)
                is ConversationNavEvent.OpenStory -> nav.onOpenStory(event.target)
                is ConversationNavEvent.AddBotToGroup -> nav.onAddBotToGroup(event.link)
                is ConversationNavEvent.SwitchInline -> nav.onSwitchInline(event.pick)
                is ConversationNavEvent.OpenProfile -> nav.onOpenUserProfile(event.chatId)
                is ConversationNavEvent.OpenPostStatistics -> nav.onOpenPostStatistics(event.messageId)
                // Anketa od tlačítka bota jde jako odpověď na to, na co se v poli právě odpovídá.
                is ConversationNavEvent.CreatePoll ->
                    nav.onCreatePoll(PollComposerRequest(event.mode, current.replyingTo?.id, current.replyQuote))
                // „Upravit" nad seznamem úkolů: týž formulář jako nový seznam, předvyplněný.
                is ConversationNavEvent.EditChecklist ->
                    nav.onCreateChecklist(ChecklistComposerRequest(replyToMessageId = null, quote = null, editMessageId = event.messageId))
                is ConversationNavEvent.OpenBotShare -> nav.onOpenBotShare(event.request)
                is ConversationNavEvent.OpenWebApp -> nav.onOpenWebApp(event.request)
            }
        }
    }
}
