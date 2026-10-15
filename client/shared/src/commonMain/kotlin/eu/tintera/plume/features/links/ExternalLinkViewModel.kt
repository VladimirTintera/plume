package eu.tintera.plume.features.links

import eu.tintera.plume.core.navigation.ContactOffers
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.navigation.LinkOpenRequest
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.ShareTarget
import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ContactLinkExpiredException
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.InviteJoinResult
import eu.tintera.plume.core.telegram.LinkUserNotFoundException
import eu.tintera.plume.core.telegram.LoginLinkKind
import eu.tintera.plume.core.telegram.ProxyRepository
import eu.tintera.plume.core.telegram.UnsupportedProxyException
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.features.conversation.ChatTarget
import eu.tintera.plume.features.conversation.FolderInviteSheetState
import eu.tintera.plume.features.conversation.InviteSheetState
import eu.tintera.plume.features.conversation.WebAppRequest
import eu.tintera.plume.features.proxy.ProxyLinkFlow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.contact_link_expired
import eu.tintera.plume.resources.conversation_join_failed
import eu.tintera.plume.resources.conversation_link_failed
import eu.tintera.plume.resources.conversation_link_user_not_found
import eu.tintera.plume.resources.folder_invite_added
import eu.tintera.plume.resources.folder_invite_failed
import eu.tintera.plume.resources.folder_invite_joined
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.proxy_unsupported
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Odkaz na Telegram zvenčí ([LinkOpenRequests]): z jiné aplikace přes systém, nebo z čtečky
 * QR kódů. Totéž, co konverzace dělá s odkazem ve zprávě (`ConversationViewModel.openLink`),
 * jen bez konverzace kolem — co ukazuje konverzace nad sebou (náhled pozvánky, sada samolepek,
 * proxy, varování před přihlašovacím odkazem), ukazuje tady shell.
 *
 * Rozdíly proti odkazu ve zprávě:
 * - **Chat se otevře nad srovnaným zásobníkem**, jako z notifikace — odkaz zvenčí s tím, co
 *   bylo v Plume zrovna otevřené, nesouvisí.
 * - **Bot se z odkazu nikdy nespouští sám** (`autostart`), chat jen nabídne Start. Odkaz zvenčí
 *   mohl poslat kdokoli a uživatel ho v Plume ještě neviděl.
 * - **Neznámý webový odkaz jde VÝSLOVNĚ do prohlížeče** ([UrlOpener.openInBrowser]). Systém
 *   by ho jinak mohl vrátit zase Plume, protože Plume adresy `t.me` přijímá.
 */
class ExternalLinkViewModel(
    private val requests: LinkOpenRequests,
    private val chats: ChatRepository,
    private val shareRequests: ShareRequests,
    private val botStartRequests: BotStartRequests,
    private val urlOpener: UrlOpener,
    proxies: ProxyRepository,
    /** Kontaktní odkaz (QR kód): profil s „Přidat do kontaktů" jako hlavní akcí. */
    private val contactOffers: ContactOffers,
) : ViewModel() {

    private val logger = Logger.withTag("ExternalLink")

    val proxyLinkFlow = ProxyLinkFlow(viewModelScope, proxies)

    private val _state = MutableStateFlow(ExternalLinkState())
    val state: StateFlow<ExternalLinkState> = _state.asStateFlow()

    /**
     * Vyzvedne odkaz z [LinkOpenRequests] a otevře ho. Volá ho [ExternalLinkHost], ne `init`:
     * ViewModel žije v úložišti Activity i po odhlášení a odkaz musí počkat na přihlášený shell.
     */
    fun open(request: LinkOpenRequest) {
        requests.consume(request)
        viewModelScope.launch { resolve(request.url) }
    }

    private suspend fun resolve(url: String) {
        chats.resolveLink(url)
            .onSuccess { link ->
                when (link) {
                    // Druh, který Plume neumí (platby, dárky…): webový do prohlížeče, `tg://` nikam.
                    null -> if (!url.isWebUrl() || !urlOpener.openInBrowser(url)) {
                        _state.update { it.copy(message = UiText(Res.string.conversation_link_failed)) }
                    }
                    is InternalLink.Chat -> {
                        link.draftText?.let { text ->
                            shareRequests.start(text, hasFiles = false, target = ShareTarget(link.chatId, confirm = false))
                        }
                        if (link.offerContact) contactOffers.offer(link.chatId)
                        navigate(ExternalDestination.Chat(chatTarget(link.chatId), profile = link.openProfile))
                    }
                    is InternalLink.Message ->
                        navigate(ExternalDestination.Chat(ChatTarget(link.chatId, link.messageId, topicId = link.topicId)))
                    // Sdílení bez vybraného chatu: obsah nese čip v shellu (viz ShareRequests).
                    is InternalLink.ShareText -> shareRequests.start(link.text, hasFiles = false)
                    is InternalLink.Proxy -> proxyLinkFlow.open(link.config)
                    is InternalLink.Invite -> _state.update { it.copy(invite = InviteSheetState(link.link, link.preview)) }
                    is InternalLink.FolderInvite -> _state.update {
                        it.copy(folderInvite = FolderInviteSheetState(link.link, link.preview))
                    }
                    is InternalLink.StickerSet -> _state.update { it.copy(stickerSet = link.setId) }
                    is InternalLink.BotStart -> {
                        botStartRequests.request(link.chatId, link.parameter)
                        navigate(ExternalDestination.Chat(ChatTarget(link.chatId)))
                    }
                    is InternalLink.BotStartInGroup -> navigate(ExternalDestination.AddBotToGroup(link))
                    is InternalLink.MainWebApp -> navigate(
                        ExternalDestination.WebApp(
                            WebAppRequest(link.botUserId, url = "", launch = false, main = true, startParameter = link.startParameter),
                        ),
                    )
                    is InternalLink.WebApp -> navigate(
                        ExternalDestination.WebApp(
                            WebAppRequest(
                                link.botUserId,
                                url = "",
                                launch = false,
                                startParameter = link.startParameter,
                                shortName = link.shortName,
                            ),
                        ),
                    )
                    is InternalLink.Screen -> navigate(ExternalDestination.Screen(link.screen))
                    is InternalLink.LoginWarning -> _state.update { it.copy(loginWarning = link.kind) }
                    is InternalLink.Story -> navigate(ExternalDestination.Story(link.posterChatId, link.storyId))
                }
            }
            .onFailure { failure ->
                val message = when (failure) {
                    is LinkUserNotFoundException -> UiText(Res.string.conversation_link_user_not_found)
                    is ContactLinkExpiredException -> UiText(Res.string.contact_link_expired)
                    is UnsupportedProxyException -> UiText(Res.string.proxy_unsupported)
                    else -> failure.reported(Res.string.conversation_link_failed)
                }
                _state.update { it.copy(message = message) }
            }
    }

    /** Skupina s tématy bez určeného tématu se otevře seznamem témat, jako ze seznamu chatů. */
    private suspend fun chatTarget(chatId: Long) = ChatTarget(chatId, forum = chats.opensAsTopics(chatId))

    private fun navigate(destination: ExternalDestination) {
        _state.update { it.copy(destination = destination) }
    }

    /** Shell cíl otevřel. */
    fun consumeDestination() = _state.update { it.copy(destination = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun joinInvite() {
        val invite = _state.value.invite?.takeUnless { it.joining } ?: return
        _state.update { it.copy(invite = invite.copy(joining = true)) }
        viewModelScope.launch {
            chats.joinByInvite(invite.link)
                .onSuccess { result ->
                    when (result) {
                        is InviteJoinResult.Joined -> {
                            _state.update { it.copy(invite = null) }
                            navigate(ExternalDestination.Chat(chatTarget(result.chatId)))
                        }
                        InviteJoinResult.Requested ->
                            _state.update { it.copy(invite = null, message = UiText(Res.string.invite_request_sent)) }
                    }
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_join_failed)
                    _state.update { it.copy(invite = it.invite?.copy(joining = false), message = message) }
                }
        }
    }

    fun dismissInvite() = _state.update { it.copy(invite = null) }

    fun toggleFolderInviteChat(chatId: Long) {
        _state.update { state ->
            val invite = state.folderInvite?.takeUnless { it.adding } ?: return@update state
            val selected = if (chatId in invite.selected) invite.selected - chatId else invite.selected + chatId
            state.copy(folderInvite = invite.copy(selected = selected))
        }
    }

    /** Jako v konverzaci: složka se pak objeví nad seznamem chatů sama, stačí hláška. */
    fun addFolderInvite() {
        val invite = _state.value.folderInvite?.takeUnless { it.adding || !it.canAdd } ?: return
        _state.update { it.copy(folderInvite = invite.copy(adding = true)) }
        viewModelScope.launch {
            chats.addFolderByInvite(invite.link, invite.preview.missingChats.map { it.id }.filter { it in invite.selected })
                .onSuccess {
                    val done = if (invite.preview.folderId == 0) Res.string.folder_invite_added else Res.string.folder_invite_joined
                    _state.update { it.copy(folderInvite = null, message = UiText(done, invite.preview.title)) }
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.folder_invite_failed)
                    _state.update { it.copy(folderInvite = it.folderInvite?.copy(adding = false), message = message) }
                }
        }
    }

    fun dismissFolderInvite() = _state.update { it.copy(folderInvite = null) }

    fun dismissStickerSet() = _state.update { it.copy(stickerSet = null) }

    fun dismissLoginWarning() = _state.update { it.copy(loginWarning = null) }

    private fun Throwable.reported(fallback: StringResource): UiText {
        logger.w(this) { "Odkaz zvenčí se nepodařilo otevřít." }
        return userMessage(fallback)
    }
}

private fun String.isWebUrl(): Boolean {
    val scheme = substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme == "http" || scheme == "https"
}

data class ExternalLinkState(
    /** Kam shell přejde; nuluje ho ([ExternalLinkViewModel.consumeDestination]). */
    val destination: ExternalDestination? = null,
    val invite: InviteSheetState? = null,
    val folderInvite: FolderInviteSheetState? = null,
    val stickerSet: Long? = null,
    val loginWarning: LoginLinkKind? = null,
    /** Jednorázová hláška pro snackbar shellu. */
    val message: UiText? = null,
)

/** Obrazovka, na kterou odkaz zvenčí vede. Navigaci dělá shell, ViewModel jen říká kam. */
sealed interface ExternalDestination {
    /** [profile] = odkaz chce profil (`?profile`), otevře se nad konverzací. */
    data class Chat(val target: ChatTarget, val profile: Boolean = false) : ExternalDestination

    data class Story(val posterChatId: Long, val storyId: Int) : ExternalDestination

    /** Mini aplikace bota; pod ní chat s botem, aby zpět vedlo k němu. */
    data class WebApp(val request: WebAppRequest) : ExternalDestination

    data class AddBotToGroup(val link: InternalLink.BotStartInGroup) : ExternalDestination

    data class Screen(val screen: AppScreen) : ExternalDestination
}
