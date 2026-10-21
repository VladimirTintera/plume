package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ProxyConfig
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.navigation.ContactOffers
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.ShareTarget
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ContactLinkExpiredException
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ForwardOrigin
import eu.tintera.plume.core.telegram.GuardDecision
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.LoginUrlPrompt
import eu.tintera.plume.core.telegram.JoinResult
import eu.tintera.plume.core.telegram.isChannelLimit
import eu.tintera.plume.core.telegram.LinkUserNotFoundException
import eu.tintera.plume.core.telegram.UnsupportedProxyException
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.features.proxy.ProxyLinkFlow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_login_failed
import eu.tintera.plume.resources.external_login_done
import eu.tintera.plume.resources.article_load_failed
import eu.tintera.plume.resources.comments_unavailable
import eu.tintera.plume.resources.contact_link_expired
import eu.tintera.plume.resources.conversation_bot_start_failed
import eu.tintera.plume.resources.conversation_join_failed
import eu.tintera.plume.resources.conversation_link_failed
import eu.tintera.plume.resources.conversation_link_user_not_found
import eu.tintera.plume.resources.conversation_mention_not_found
import eu.tintera.plume.resources.folder_invite_added
import eu.tintera.plume.resources.folder_invite_failed
import eu.tintera.plume.resources.folder_invite_joined
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.join_guard_declined
import eu.tintera.plume.resources.proxy_unsupported
import eu.tintera.plume.resources.story_live_unsupported
import eu.tintera.plume.resources.story_unavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Kam vede odkaz nebo ťuknutí ve zprávě: odkaz (i `t.me` a `tg://` uvnitř aplikace, s potvrzením
 * cizího webu), zmínka, chat, zpráva, komentáře, původ přeposlání, profil odesílatele, příběh,
 * pozvánka do chatu a do složky, start bota z odkazu a mini aplikace. Navigaci
 * dělá obrazovka — sem patří rozhodnutí a jednorázová událost ve stavu, kterou si vyzvedne.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationLinks(
    private val chatId: Long,
    /** Téma skupiny: odkaz na zprávu mimo něj otevře chat, ne skok. */
    private val forumTopicId: Int?,
    /** Téma Uložených zpráv: odkaz na zprávu otevře chat, ne skok. */
    private val savedTopicId: Long?,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    /** Odkaz ven (prohlížeč); `UrlOpener` je `expect class`, v testu ho nahradit nejde. */
    private val openUrl: (url: String) -> Unit,
    private val shareRequests: ShareRequests,
    private val botStartRequests: BotStartRequests,
    /** Kontaktní odkaz: profil, ve kterém je „Přidat do kontaktů" hlavní akcí. */
    private val contactOffers: ContactOffers,
    /** Odkaz `t.me/proxy`: dialog přidání proxy (`ProxyLinkFlow.open`). */
    private val openProxy: (ProxyConfig) -> Unit,
    private val jumper: MessageJumper,
    private val logger: Logger,
    /** Limit skupin a kanálů: místo hlášky výběr neaktivních k opuštění (`ChannelLimitRequests`). */
    private val onChannelLimit: () -> Unit = {},
    /** Živý příběh jde sledovat v prohlížeči (Android); jinak jen věta, že to neumíme. */
    private val liveStoriesSupported: Boolean = false,
) {
    /**
     * Otevře odkaz ze zprávy. Odkaz na Telegram otevře aplikace sama ([InternalLink]), ostatní
     * jde do prohlížeče.
     *
     * - chat (`t.me/jméno`) se otevře nad touhle konverzací, jako zmínka;
     * - odkaz na zprávu v TOMHLE chatu na ni skočí na místě (dojetím nebo přejezdem, se
     *   zvýrazněním), v jiném chatu ho otevře se skokem na zprávu;
     * - pozvánka ukáže náhled chatu ([ConversationState.invite]) — přidává se až na potvrzení;
     * - sada samolepek otevře svůj sheet.
     *
     * Rozpoznání je lokální volání TDLib, takže běžný webový odkaz jde do prohlížeče bez prodlevy.
     */
    fun openLink(url: String, confirmExternal: Boolean = false) {
        scope.launch {
            chatRepository.resolveLink(url)
                .onSuccess { link ->
                    when (link) {
                        null -> openExternal(url, confirmExternal)
                        // Žádost o přihlášení se zpracovává jen z odkazu zvenčí (předpis TDLib);
                        // z chatu je to obyčejný odkaz.
                        is InternalLink.Oauth -> openExternal(url, confirmExternal)
                        is InternalLink.Chat -> openChatLink(link)
                        // Sdílení bez vybraného chatu: obsah nese čip v shellu (viz ShareRequests).
                        is InternalLink.ShareText -> shareRequests.start(link.text, hasFiles = false)
                        is InternalLink.Message -> when {
                            // Tentýž chat, a v tématu jen totéž téma — zpráva z jiného tématu
                            // v téhle historii není, skok by skončil „už není k dispozici".
                            // V tématu Uložených zpráv odkaz téma nenese, takže se otevře celý chat.
                            link.chatId == chatId && savedTopicId == null &&
                                (forumTopicId == null || link.topicId == forumTopicId) ->
                                link.messageId?.let { jumper.jumpToMessage(it, fromSearch = true) }
                            else -> openTarget(ChatTarget(link.chatId, link.messageId, topicId = link.topicId))
                        }
                        is InternalLink.Invite -> state.update { it.copy(invite = InviteSheetState(link.link, link.preview)) }
                        is InternalLink.FolderInvite -> state.update {
                            it.copy(folderInvite = FolderInviteSheetState(link.link, link.preview))
                        }
                        is InternalLink.StickerSet -> state.update { it.copy(openStickerSet = link.setId) }
                        is InternalLink.Proxy -> openProxy(link.config)
                        is InternalLink.BotStart -> openBotStart(link)
                        // Skupinu vybírá obrazovka výběru; přidání se potvrzuje až tam.
                        is InternalLink.BotStartInGroup -> state.update { it.copy(addBotToGroup = link) }
                        is InternalLink.MainWebApp -> requestWebApp(
                            WebAppRequest(link.botUserId, url = "", launch = false, main = true, startParameter = link.startParameter),
                        )
                        is InternalLink.Screen -> state.update { it.copy(openScreen = link.screen) }
                        is InternalLink.LoginWarning -> state.update { it.copy(loginWarning = link.kind) }
                        is InternalLink.Story -> openStory(StoryTarget(link.posterChatId, link.storyId))
                        is InternalLink.WebApp -> requestWebApp(
                            WebAppRequest(
                                link.botUserId,
                                url = "",
                                launch = false,
                                startParameter = link.startParameter,
                                shortName = link.shortName,
                            ),
                        )
                    }
                }
                .onFailure { failure ->
                    val message = when (failure) {
                        is LinkUserNotFoundException -> UiText(Res.string.conversation_link_user_not_found)
                        is ContactLinkExpiredException -> UiText(Res.string.contact_link_expired)
                        is UnsupportedProxyException -> UiText(Res.string.proxy_unsupported)
                        else -> failure.reported(logger, Res.string.conversation_link_failed)
                    }
                    state.update { it.copy(error = message) }
                }
        }
    }

    /**
     * Odkaz na chat. Profil (`?profile`) se otevře nad touhle konverzací; jinak chat, a text
     * z odkazu (`?text=…`) čeká v jeho poli jako sdílený text — připojí se za rozepsaný koncept,
     * nic se neodesílá. Jde přes [ShareRequests] s vybraným chatem: konverzace si ho vyzvedne,
     * až obnoví vlastní koncept, a v tomhle chatu ho vyzvedne rovnou.
     */
    private fun openChatLink(link: InternalLink.Chat) {
        if (link.offerContact) contactOffers.offer(link.chatId)
        if (link.openProfile) {
            state.update { it.copy(openProfile = link.chatId) }
            return
        }
        link.draftText?.let { text ->
            shareRequests.start(text, hasFiles = false, target = ShareTarget(link.chatId, confirm = false))
        }
        if (link.chatId != chatId) openTarget(ChatTarget(link.chatId))
    }

    /**
     * Startovací odkaz bota. S `autostart` se bot spustí hned, jinak se parametr odloží
     * ([BotStartRequests]) a chat bota nabídne Start — parametr píše odkaz, ne uživatel, a co
     * bot s ním udělá, se uživatel dozví až po ťuknutí.
     */
    fun openBotStart(link: InternalLink.BotStart) {
        if (link.autostart) {
            scope.launch {
                repository.startBot(link.chatId, link.parameter).onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_bot_start_failed)) }
                }
            }
        } else {
            botStartRequests.request(link.chatId, link.parameter)
        }
        if (link.chatId != chatId) openTarget(ChatTarget(link.chatId))
    }

    /** Odkaz z tlačítka bota potvrzený v dialogu. */
    fun openConfirmedLink() {
        val url = state.value.confirmLink ?: return
        state.update { it.copy(confirmLink = null) }
        openUrl(url)
    }

    fun dismissLink() = state.update { it.copy(confirmLink = null) }

    /** Obrazovka formulář ankety otevřela. */
    /**
     * Mini aplikace bota. Souhlas před prvním otevřením u bota se ptá až obrazovka mini aplikace
     * (`WebAppViewModel`) — vstupů je víc než konverzace.
     */
    fun requestWebApp(request: WebAppRequest) {
        state.update { it.copy(openWebApp = request) }
    }

    fun consumeOpenWebApp() = state.update { it.copy(openWebApp = null) }

    /** Přidat se přes pozvánku z náhledu. Po přidání se chat otevře. */
    fun joinInvite() {
        val invite = state.value.invite?.takeUnless { it.joining } ?: return
        state.update { it.copy(invite = invite.copy(joining = true)) }
        scope.launch {
            chatRepository.joinByInvite(invite.link)
                .onSuccess { result ->
                    when (result) {
                        is JoinResult.Joined -> {
                            state.update { it.copy(invite = null) }
                            openTarget(ChatTarget(result.chatId))
                        }
                        JoinResult.Requested ->
                            state.update { it.copy(invite = null, notice = UiText(Res.string.invite_request_sent)) }
                        JoinResult.Declined ->
                            state.update { it.copy(invite = null, error = UiText(Res.string.join_guard_declined)) }
                        is JoinResult.GuardBot -> {
                            state.update { it.copy(invite = null) }
                            awaitGuardBot(result)
                        }
                    }
                }
                .onFailure { failure ->
                    if (failure.isChannelLimit()) {
                        state.update { it.copy(invite = null) }
                        onChannelLimit()
                        return@onFailure
                    }
                    val message = failure.reported(logger, Res.string.conversation_join_failed)
                    state.update { it.copy(invite = it.invite?.copy(joining = false), error = message) }
                }
        }
    }

    fun dismissInvite() {
        state.update { it.copy(invite = null) }
    }

    /**
     * Vstup schvaluje strážní bot: otevře se jeho mini aplikace a čeká se, jak rozhodne. Mini
     * aplikace se po rozhodnutí zavře sama; schválený chat se otevře nad konverzací, jako po
     * přidání bez bota. Zavře-li ji uživatel bez rozhodnutí, čekání skončí s konverzací.
     * Volá ho i tlačítko „Přidat se" ([ConversationChatActions.join]).
     */
    fun awaitGuardBot(guard: JoinResult.GuardBot, onApproved: (chatId: Long) -> Unit = { openTarget(ChatTarget(it)) }) {
        requestWebApp(WebAppRequest(guard.botUserId, url = "", launch = false, guardQueryId = guard.queryId))
        scope.launch {
            when (val decision = chatRepository.awaitGuardDecision(guard.queryId)) {
                is GuardDecision.Approved -> onApproved(decision.chatId)
                GuardDecision.Declined -> state.update { it.copy(error = UiText(Res.string.join_guard_declined)) }
                GuardDecision.Queued -> state.update { it.copy(notice = UiText(Res.string.invite_request_sent)) }
            }
        }
    }

    fun toggleFolderInviteChat(chatId: Long) {
        state.update { current ->
            val invite = current.folderInvite?.takeUnless { it.adding } ?: return@update current
            val selected = if (chatId in invite.selected) invite.selected - chatId else invite.selected + chatId
            current.copy(folderInvite = invite.copy(selected = selected))
        }
    }

    /**
     * Přidá sdílenou složku (nebo jen nové chaty do složky, kterou uživatel už má). Do vybraných
     * chatů se tím uživatel přidá; složka se pak objeví v řádku nad seznamem chatů sama
     * (`UpdateChatFolders`), takže se nikam nenaviguje — stačí hláška.
     */
    fun addFolderInvite() {
        val invite = state.value.folderInvite?.takeUnless { it.adding || !it.canAdd } ?: return
        state.update { it.copy(folderInvite = invite.copy(adding = true)) }
        scope.launch {
            chatRepository.addFolderByInvite(invite.link, invite.preview.missingChats.map { it.id }.filter { it in invite.selected })
                .onSuccess {
                    val done = if (invite.preview.folderId == 0) Res.string.folder_invite_added else Res.string.folder_invite_joined
                    state.update { it.copy(folderInvite = null, notice = UiText(done, invite.preview.title)) }
                }
                .onFailure { failure ->
                    if (failure.isChannelLimit()) {
                        state.update { it.copy(folderInvite = null) }
                        onChannelLimit()
                        return@onFailure
                    }
                    val message = failure.reported(logger, Res.string.folder_invite_failed)
                    state.update { it.copy(folderInvite = it.folderInvite?.copy(adding = false), error = message) }
                }
        }
    }

    fun dismissFolderInvite() {
        state.update { it.copy(folderInvite = null) }
    }

    /** Obrazovka sheet sady otevřela. */
    fun consumeOpenStickerSet() {
        state.update { it.copy(openStickerSet = null) }
    }

    /**
     * Zmínka v textu: najde chat a obrazovka ho otevře nad touhle konverzací ([ConversationState.openChat]).
     * Zmínka téhož chatu (sebe sama v Uložených zprávách, kanál ve vlastním příspěvku) nikam nevede.
     */
    fun openMentioned(resolve: suspend () -> Result<Long>) {
        scope.launch {
            resolve()
                .onSuccess { target -> if (target != chatId) openTarget(ChatTarget(target)) }
                .onFailure { failure ->
                    val message = failure.reported(logger, Res.string.conversation_mention_not_found)
                    state.update { it.copy(error = message) }
                }
        }
    }

    /**
     * Chat k otevření nad konverzací. Skupina s tématy bez určeného tématu a zprávy se otevře
     * seznamem témat, jako ze seznamu chatů — zmínka `@skupina` i odkaz `t.me/skupina` by jinak
     * vedly do promíchané konverzace celé skupiny. Zjištění je lokální dotaz.
     */
    fun openTarget(target: ChatTarget) {
        scope.launch {
            val forum = target.messageId == null && target.threadId == null && target.topicId == null &&
                chatRepository.opensAsTopics(target.chatId)
            state.update { it.copy(openChat = target.copy(forum = forum)) }
        }
    }

    /**
     * Zdroj přeposlané zprávy. Původní příspěvek se otevře jako odkaz na zprávu: v tomhle chatu
     * skokem na místě, jinde konverzací se skokem na ni (i v kanálu, kde uživatel není — ten
     * dostane „Přidat se").
     */
    /**
     * Komentáře k příspěvku kanálu: vlákno v připojené diskusní skupině. Kde to je, ví jen
     * server (`getMessageThread`), navigace je věc obrazovky — jako u zmínky.
     */
    /**
     * Dočte useknutý článek („Číst dál"). Celý přijde jako nová podoba téže zprávy a nahradí ji
     * v seznamu; repository si ho pamatuje, takže další převody zprávy (reakce, přečtení)
     * nevrátí zase začátek.
     */
    fun readFullArticle(messageId: Long) {
        if (messageId in state.value.loadingArticles) return
        state.update { it.copy(loadingArticles = it.loadingArticles + messageId) }
        scope.launch {
            repository.fullArticle(chatId, messageId)
                .onSuccess { message ->
                    state.update { it.copy(messages = merge(it.messages, listOf(message))) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Článek $messageId se nepodařilo dočíst." }
                    state.update { it.copy(notice = failure.userMessage(Res.string.article_load_failed)) }
                }
            state.update { it.copy(loadingArticles = it.loadingArticles - messageId) }
        }
    }

    fun openComments(messageId: Long) {
        scope.launch {
            repository.commentThread(chatId, messageId)
                .onSuccess { thread ->
                    state.update { it.copy(openChat = ChatTarget(thread.chatId, threadId = thread.rootMessageId)) }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.comments_unavailable)) }
                }
        }
    }

    fun openForwardOrigin(origin: ForwardOrigin) {
        when (origin) {
            is ForwardOrigin.User -> openMentioned { chatRepository.privateChat(origin.userId) }
            is ForwardOrigin.Chat -> when {
                origin.chatId != chatId -> openTarget(ChatTarget(origin.chatId, origin.messageId))
                else -> origin.messageId?.let { jumper.jumpToMessage(it) }
            }
            // Skrytý účet server neprozradí, značka se u něj nedá ťuknout.
            is ForwardOrigin.Hidden -> Unit
        }
    }

    /**
     * Webový odkaz. Napřed se zeptá serveru (`getExternalLinkInfo`), jestli web nechce uživatele
     * rovnou přihlásit účtem Telegramu — pak dialog jako u tlačítka bota. Jinak, nebo když dotaz
     * selže, obyčejné otevření (s potvrzením adresy, kde se potvrzuje). V tajném chatu se
     * serveru na odkaz neptá: TDLib to tam předepisuje jen se zapnutými náhledy odkazů.
     */
    private suspend fun openExternal(url: String, confirmExternal: Boolean) {
        val plain: (String) -> Unit = { target ->
            if (confirmExternal) state.update { it.copy(confirmLink = target) } else openUrl(target)
        }
        if (state.value.info?.isSecret == true) return plain(url)
        chatRepository.externalLinkInfo(url)
            .onSuccess { prompt ->
                when (prompt) {
                    is LoginUrlPrompt.Open -> if (prompt.skipConfirmation) openUrl(prompt.url) else plain(prompt.url)
                    is LoginUrlPrompt.Confirm -> state.update { it.copy(externalLogin = ExternalLogin(url, prompt)) }
                }
            }
            .onFailure { plain(url) }
    }

    /** Přihlášení z odkazu potvrzené v dialogu; [allowWriteAccess] = bot webu smí psát. */
    fun confirmExternalLogin(allowWriteAccess: Boolean) {
        val login = state.value.externalLogin ?: return
        state.update { it.copy(externalLogin = null) }
        scope.launch {
            chatRepository.externalLoginUrl(login.link, allowWriteAccess)
                .onSuccess { target ->
                    // Prázdná adresa = přihlášeno, stačí to říct.
                    if (target.isBlank()) {
                        state.update { it.copy(notice = UiText(Res.string.external_login_done, login.prompt.domain)) }
                    } else {
                        openUrl(target)
                    }
                }
                // Přihlásit se nepovedlo, ale web uživatel chtěl — otevře se bez přihlášení.
                .onFailure { failure ->
                    logger.w(failure) { "Přihlášení z odkazu (${login.prompt.domain}) selhalo." }
                    state.update { it.copy(notice = UiText(Res.string.bot_login_failed)) }
                    openUrl(login.link)
                }
        }
    }

    /** Dialog zavřený bez přihlášení: odkaz se otevře obyčejně, chtěl ho uživatel. */
    fun dismissExternalLogin() {
        val login = state.value.externalLogin ?: return
        state.update { it.copy(externalLogin = null) }
        openUrl(login.link)
    }

    /**
     * Profil odesílatele ze skupiny. Profil stojí na CHATU, takže se napřed najde (a případně
     * lokálně založí) soukromý chat s ním — stejně jako při ťuknutí na člena v profilu skupiny.
     */
    fun openSenderProfile(userId: Long) {
        scope.launch {
            chatRepository.privateChat(userId)
                .onSuccess { target -> state.update { it.copy(openProfile = target) } }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_mention_not_found)) }
                }
        }
    }

    /** Obrazovka profil otevřela; jednorázová událost jako [consumeOpenChat]. */
    fun consumeOpenProfile() {
        state.update { it.copy(openProfile = null) }
    }

    fun consumeOpenMediaAt() {
        state.update { it.copy(openMediaAt = null) }
    }

    fun consumeOpenChat() {
        state.update { it.copy(openChat = null) }
    }

    fun consumeOpenScreen() {
        state.update { it.copy(openScreen = null) }
    }

    fun consumeOpenStory() {
        state.update { it.copy(openStory = null) }
    }

    /**
     * Příběh z citace odpovědi, z odkazu nebo ze zprávy: s fotkou či videem (a živý, kde ho
     * umíme přehrát) do prohlížeče, jinak jen věta.
     * Ptá se až teď — příběh mezitím mohl vypršet, a prohlížeč, který se otevře a nemá co
     * ukázat, je horší než hláška nad polem.
     */
    fun openStory(target: StoryTarget) {
        scope.launch {
            val story = repository.story(target.posterChatId, target.storyId)
            when {
                story.content != null || (story.live != null && liveStoriesSupported) -> state.update { it.copy(openStory = target) }
                story.isLive -> state.update { it.copy(notice = UiText(Res.string.story_live_unsupported)) }
                else -> state.update { it.copy(notice = UiText(Res.string.story_unavailable)) }
            }
        }
    }

    fun dismissLoginWarning() = state.update { it.copy(loginWarning = null) }

    fun consumeAddBotToGroup() {
        state.update { it.copy(addBotToGroup = null) }
    }

    fun consumeSwitchInline() {
        state.update { it.copy(switchInline = null) }
    }
}
