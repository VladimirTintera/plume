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
    /** Profil jiného chatu nad konverzací — odesílatel ze skupiny, na jehož tvář se ťuklo. */
    val onOpenUserProfile: (chatId: Long) -> Unit,
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
    /** Odkaz na obrazovku aplikace (`tg://settings/…`, `tg://newgroup`) — navigaci řeší shell. */
    val onOpenScreen: (AppScreen) -> Unit = {},
    /** Tlačítko bota s inline dotazem do jiného chatu: výběr chatu. */
    val onSwitchInline: (SwitchInlinePick) -> Unit = {},
    /** Pruh „Pozvat členy" nad čerstvou skupinou: výběr lidí. */
    val onAddMembers: () -> Unit = {},
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
 * Jednorázové události ze stavu, které vedou mimo obrazovku: ViewModel řekne „otevři chat",
 * navigace je věc shellu. Událost se nuluje hned, jinak by se po otočení otevřela znovu.
 *
 * Navigace se čte přes [rememberUpdatedState]: efekt se restartuje jen novou událostí, ne
 * novou instancí [ConversationNavigation], a přitom volá vždycky tu aktuální.
 */
@Composable
internal fun ConversationEvents(
    state: ConversationState,
    viewModel: ConversationViewModel,
    navigation: ConversationNavigation,
) {
    val nav by rememberUpdatedState(navigation)

    // Výběr cílového chatu je vlastní obrazovka, takže požadavek z nabídky se předá navigaci.
    val forwardRequest = state.forwardRequest
    LaunchedEffect(forwardRequest) {
        if (forwardRequest != null) {
            nav.onForward(forwardRequest)
            viewModel.consumeForwardRequest()
        }
    }

    // Spravované přímé zprávy kanálu se čtou po vláknech; celý chat by psaní stejně odmítl.
    val directAdmin = state.info?.isDirectMessagesAdmin == true && state.directTopicId == null
    LaunchedEffect(directAdmin) { if (directAdmin) nav.onViewAsDirectTopics?.invoke() }

    // Chat se smazal (⋮ v hlavičce, pruh proti spamu) — konverzace nemá co ukazovat.
    val chatGone by viewModel.chatGone.collectAsStateWithLifecycle()
    LaunchedEffect(chatGone) { if (chatGone) nav.onChatGone() }
    val unarchived by viewModel.unarchived.collectAsStateWithLifecycle()
    LaunchedEffect(unarchived) { if (unarchived) nav.onUnarchived() }

    // Chat ze zmínky nebo odkazu: ViewModel ho najde, navigace je věc obrazovky.
    LaunchedEffect(state.openMediaAt) {
        val target = state.openMediaAt ?: return@LaunchedEffect
        viewModel.consumeOpenMediaAt()
        nav.onOpenMediaAt(target.messageId, target.seconds)
    }
    LaunchedEffect(state.openChat) {
        val target = state.openChat ?: return@LaunchedEffect
        viewModel.consumeOpenChat()
        nav.onOpenChat(target)
    }
    LaunchedEffect(state.openScreen) {
        val screen = state.openScreen ?: return@LaunchedEffect
        viewModel.consumeOpenScreen()
        nav.onOpenScreen(screen)
    }
    LaunchedEffect(state.openStory) {
        val story = state.openStory ?: return@LaunchedEffect
        viewModel.consumeOpenStory()
        nav.onOpenStory(story)
    }
    LaunchedEffect(state.addBotToGroup) {
        val link = state.addBotToGroup ?: return@LaunchedEffect
        viewModel.consumeAddBotToGroup()
        nav.onAddBotToGroup(link)
    }
    LaunchedEffect(state.switchInline) {
        val pick = state.switchInline ?: return@LaunchedEffect
        viewModel.consumeSwitchInline()
        nav.onSwitchInline(pick)
    }
    LaunchedEffect(state.openProfile) {
        val target = state.openProfile ?: return@LaunchedEffect
        viewModel.consumeOpenProfile()
        nav.onOpenUserProfile(target)
    }

    // Jednorázová událost od tlačítka bota. Navigace je synchronní, takže vynulování potom
    // efekt nezruší uprostřed (past „Vrátit" v docs/poznatky/konverzace-pohyb.md se týká práce, která teprve běží).
    state.openPollComposer?.let { mode ->
        LaunchedEffect(mode) {
            nav.onCreatePoll(PollComposerRequest(mode, state.replyingTo?.id, state.replyQuote))
            viewModel.consumeOpenPollComposer()
        }
    }
    // „Upravit" nad seznamem úkolů: týž formulář jako nový seznam, předvyplněný.
    state.openChecklistEditor?.let { messageId ->
        LaunchedEffect(messageId) {
            nav.onCreateChecklist(ChecklistComposerRequest(replyToMessageId = null, quote = null, editMessageId = messageId))
            viewModel.consumeOpenChecklistEditor()
        }
    }
    state.openBotShare?.let { request ->
        LaunchedEffect(request) {
            viewModel.consumeOpenBotShare()
            nav.onOpenBotShare(request)
        }
    }
    state.openWebApp?.let { request ->
        LaunchedEffect(request) {
            viewModel.consumeOpenWebApp()
            nav.onOpenWebApp(request)
        }
    }
}
