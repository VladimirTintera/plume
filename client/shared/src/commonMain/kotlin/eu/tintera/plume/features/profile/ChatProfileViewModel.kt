package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.profile_action_failed
import eu.tintera.plume.resources.profile_link_copied
import eu.tintera.plume.core.ui.TextClipboard
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AdminRights
import eu.tintera.plume.core.telegram.ChatProfile
import eu.tintera.plume.core.telegram.MemberDetail
import eu.tintera.plume.core.telegram.OwnershipTransferCheck
import eu.tintera.plume.resources.error_transfer_needs_password
import eu.tintera.plume.resources.error_transfer_too_fresh
import eu.tintera.plume.resources.member_banned
import eu.tintera.plume.resources.member_dismissed
import eu.tintera.plume.resources.member_load_failed
import eu.tintera.plume.resources.member_rights_saved
import eu.tintera.plume.resources.member_unrestricted
import eu.tintera.plume.resources.member_transferred
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.ProfileKind
import eu.tintera.plume.core.telegram.ProfileMember
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.profile_failed
import eu.tintera.plume.resources.profile_mute_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SharedMediaCounts
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.ChatNotificationOption
import eu.tintera.plume.core.telegram.ChatNotificationPrefs
import eu.tintera.plume.core.telegram.NotificationSettingsRepository
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.resources.settings_notif_error
import kotlin.time.Duration
import eu.tintera.plume.resources.document_download_failed
import eu.tintera.plume.resources.document_open_failed
import eu.tintera.plume.resources.profile_shared_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource

/**
 * Profil chatu: údaje se načtou jednou při otevření, živé jsou jen ztlumení a dostupnost.
 * Členové skupiny se dotahují až po profilu — jen když je server vydá.
 */
class ChatProfileViewModel(
    private val chatId: Long,
    private val profiles: ProfileRepository,
    private val chats: ChatRepository,
    private val conversations: ConversationRepository,
    private val files: FileRepository,
    private val fileOpener: FileOpener,
    private val urlOpener: UrlOpener,
    private val notificationSettings: NotificationSettingsRepository,
    private val notificationPermission: NotificationPermission,
    private val clipboard: TextClipboard,
) : ViewModel() {

    private val logger = Logger.withTag("Profile")

    /** Běžící stahování podle `fileId`; druhé ťuknutí ho zruší. */
    private val documentJobs = mutableMapOf<Int, Job>()

    private val _state = MutableStateFlow(ChatProfileState())
    val state: StateFlow<ChatProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.profile(chatId)
                .onSuccess { profile ->
                    _state.update { it.copy(profile = profile, loading = false) }
                    if (profile.canListMembers) loadMembers()
                    if (profile.kind == ProfileKind.User) observePresence()
                }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.reported(Res.string.profile_failed)) }
                }
        }
        viewModelScope.launch {
            chats.muted(chatId).collect { muted -> _state.update { it.copy(muted = muted) } }
        }
        viewModelScope.launch {
            /*
             * Změna postavení člena (i naše vlastní, i z jiného klienta) znamená přečíst seznam
             * znovu. `conflate`, protože „odebrat všem práva" pošle updatů dávku a čtení jich
             * snese jedno.
             */
            profiles.memberUpdates(chatId).conflate().collect { reloadMembers() }
        }
        viewModelScope.launch {
            notificationSettings.chatSettings(chatId).collect { prefs -> _state.update { it.copy(notifications = prefs) } }
        }
        viewModelScope.launch {
            // Počty rozhodují, které záložky vůbec budou — proto hned při otevření, ne až
            // s první stránkou. Selhání se nehlásí: profil je k užitku i bez sdíleného obsahu.
            conversations.sharedMediaCounts(chatId)
                .onSuccess { counts -> _state.update { it.copy(counts = counts) } }
                .onFailure { failure ->
                    logger.w(failure) { "Počty sdíleného obsahu se nepodařilo načíst." }
                    _state.update { it.copy(counts = SharedMediaCounts.Empty) }
                }
        }
    }

    /**
     * První stránka záložky. Volá se, až když se stránka poprvé ukáže — záložek je několik
     * a dotazovat se dopředu na všechny by znamenalo tři dotazy za každé otevření profilu.
     */
    fun loadShared(kind: SharedMediaKind) {
        val tab = _state.value.shared[kind]
        if (tab != null) return
        _state.update { it.copy(shared = it.shared + (kind to SharedMediaTab(loading = true))) }
        fetchShared(kind, fromMessageId = 0)
    }

    /** Další stránka; spouští ji seznam, když se dorolovalo ke konci. */
    fun loadMoreShared(kind: SharedMediaKind) {
        val tab = _state.value.shared[kind] ?: return
        if (tab.loading || tab.reachedEnd || tab.messages.isEmpty()) return
        _state.update { it.copy(shared = it.shared + (kind to tab.copy(loading = true))) }
        fetchShared(kind, fromMessageId = tab.messages.last().id)
    }

    private fun fetchShared(kind: SharedMediaKind, fromMessageId: Long) {
        viewModelScope.launch {
            conversations.sharedMedia(chatId, kind, fromMessageId)
                .onSuccess { page ->
                    _state.update { state ->
                        val current = state.shared[kind] ?: SharedMediaTab()
                        // Stránka může po odfiltrování mizejících médií dorazit prázdná, aniž by
                        // to byl konec — proto se na konec ptá `reachedEnd` od repository.
                        state.copy(
                            shared = state.shared + (
                                kind to current.copy(
                                    messages = current.messages + page.messages,
                                    loading = false,
                                    reachedEnd = page.reachedEnd,
                                )
                                ),
                        )
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Sdílený obsah se nepodařilo načíst." }
                    _state.update { state ->
                        val current = state.shared[kind] ?: SharedMediaTab()
                        state.copy(
                            shared = state.shared + (kind to current.copy(loading = false, reachedEnd = true)),
                            error = failure.reported(Res.string.profile_shared_failed),
                        )
                    }
                }
        }
    }

    /** Fotka nebo video: otevře se prohlížeč médií, stejně jako z konverzace. */
    fun openMedia(messageId: Long) {
        _state.update { it.copy(openMedia = messageId) }
    }

    fun consumeOpenMedia() {
        _state.update { it.copy(openMedia = null) }
    }

    /**
     * Soubor se stáhne a předá systému — táž cesta jako v konverzaci, včetně průběhu v řádku
     * a druhého ťuknutí jako zrušení.
     */
    fun openDocument(messageId: Long) {
        val document = _state.value.sharedMessage(messageId)?.media as? MessageMedia.Document ?: return

        documentJobs.remove(document.fileId)?.let { running ->
            running.cancel()
            _state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            return
        }

        _state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to 0f)) }
        documentJobs[document.fileId] = viewModelScope.launch {
            try {
                // Ťuknutí je výslovný pokyn: stahování se spustí znovu, i kdyby předchozí viselo.
                files.retryDownload(document.fileId)
                val states = files.file(document.fileId).onEach { fileState ->
                    if (fileState is FileState.Downloading) {
                        _state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to fileState.progress)) }
                    }
                }
                // Na rozběh se čeká jen chvíli, na dokončení pak jak dlouho je potřeba.
                val started = withTimeoutOrNull(DOWNLOAD_START_TIMEOUT) { states.first { it !is FileState.Pending } }
                val file = started?.let { first ->
                    if (first is FileState.Ready || first is FileState.Failed) {
                        first
                    } else {
                        states.first { it is FileState.Ready || it is FileState.Failed }
                    }
                }
                when (file) {
                    is FileState.Ready -> if (!fileOpener.open(file.path, document.mimeType)) {
                        _state.update { it.copy(error = UiText(Res.string.document_open_failed)) }
                    }
                    else -> _state.update { it.copy(error = UiText(Res.string.document_download_failed)) }
                }
            } finally {
                // I při zrušení musí průběh ze stavu zmizet.
                documentJobs.remove(document.fileId)
                _state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            }
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            profiles.members(chatId)
                .onSuccess { members -> _state.update { it.copy(members = members) } }
                // Bez členů je profil pořád k užitku; hlásit to snackbarem by jen rušilo.
                .onFailure { failure ->
                    logger.w(failure) { "Členy chatu se nepodařilo načíst." }
                    _state.update { it.copy(members = emptyList()) }
                }
        }
    }

    private fun observePresence() {
        viewModelScope.launch {
            conversations.presenceUpdates(chatId).collect { presence -> _state.update { it.copy(presence = presence) } }
        }
    }

    /** Ztlumit / zapnout upozornění. Stav se nepřepisuje lokálně — potvrzení přijde updatem. */
    fun toggleMute() {
        val muted = _state.value.muted ?: return
        viewModelScope.launch {
            chats.setMuted(chatId, !muted).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.profile_mute_failed)) }
            }
        }
    }

    fun openNotificationSheet() = _state.update { it.copy(notificationSheet = true) }

    fun closeNotificationSheet() = _state.update { it.copy(notificationSheet = false) }

    /** Ztlumení na dobu; `null` = navždy. Sheet se zavře — rozhodnuto, víc tam není co dělat. */
    fun mute(duration: Duration?) {
        _state.update { it.copy(notificationSheet = false) }
        viewModelScope.launch {
            val result = if (duration == null) chats.setMuted(chatId, true) else chats.muteFor(chatId, duration)
            result.onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.profile_mute_failed)) } }
        }
    }

    fun unmute() {
        _state.update { it.copy(notificationSheet = false) }
        viewModelScope.launch {
            chats.setMuted(chatId, false).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.profile_mute_failed)) }
            }
        }
    }

    /** Přepínač v sheetu; stav se nepřepisuje lokálně, potvrzení přijde updatem chatu. */
    fun setNotificationOption(option: ChatNotificationOption, value: Boolean) {
        viewModelScope.launch {
            notificationSettings.setChatOption(chatId, option, value).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.settings_notif_error)) }
            }
        }
    }

    fun resetNotifications() {
        viewModelScope.launch {
            notificationSettings.resetToDefault(chatId).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.settings_notif_error)) }
            }
        }
    }

    val supportsConversationSettings: Boolean = notificationPermission.supportsChannelSettings

    fun openConversationSettings() {
        val prefs = _state.value.notifications ?: return
        val title = _state.value.profile?.title ?: return
        notificationPermission.openConversationSettings(chatId, title, prefs.scope.channel)
    }

    /** Odkaz na chat (veřejné jméno, jinak pozvánka) do schránky. */
    fun copyLink() {
        val link = _state.value.profile?.link ?: return
        viewModelScope.launch {
            clipboard.copy(link)
            _state.update { it.copy(error = UiText(Res.string.profile_link_copied)) }
        }
    }

    /**
     * Stav se po úspěchu přepíše lokálně — `UpdateUserFullInfo` profil neodebírá.
     *
     * Blokování vede přes [ChatRepository], ne přes profil: je to tatáž akce jako v nabídce nad
     * chatem v seznamu a dvě cesty k `setMessageSenderBlockList` by se dřív nebo později rozešly.
     */
    fun setBlocked(blocked: Boolean) {
        val profile = _state.value.profile ?: return
        // Koho blokovat, si repository zjistí z chatu; tady jen hlídáme, že tenhle chat někoho má.
        profile.userId ?: return
        viewModelScope.launch {
            chats.setBlocked(chatId, blocked)
                .onSuccess { _state.update { it.copy(profile = it.profile?.copy(isBlocked = blocked)) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.profile_action_failed)) } }
        }
    }

    /** Po úspěchu obrazovka zavře profil i konverzaci — chat už v seznamu není. */
    fun deleteOrLeave() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            profiles.deleteOrLeave(profile)
                .onSuccess { _state.update { it.copy(chatGone = true) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.profile_action_failed)) } }
        }
    }

    /** Člen skupiny: otevře se soukromý chat s ním (TDLib ho založí lokálně, nic se neposílá). */
    fun openMember(userId: Long) {
        viewModelScope.launch {
            chats.privateChat(userId)
                .onSuccess { target -> _state.update { it.copy(openChat = target) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.profile_failed)) } }
        }
    }

    // --- Správa členů ---

    /**
     * Dlouhý stisk na členovi. Člen se načítá až teď: `getChatMember` je dotaz na server
     * a ptát se na každého ze seznamu dopředu by znamenalo dvě stě dotazů za otevření profilu.
     *
     * Nabídka se otevře, jen když má co ukázat ([MemberActions.any]) — běžnému členovi by se
     * jinak otevřel prázdný sheet a vypadalo by to jako chyba.
     */
    fun openMemberActions(userId: Long) {
        viewModelScope.launch {
            profiles.member(chatId, userId)
                .onSuccess { member -> if (member.actions.any) _state.update { it.copy(memberSheet = member) } }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.member_load_failed)) }
                }
        }
    }

    fun closeMemberActions() = _state.update { it.copy(memberSheet = null) }

    /** Nabídka se zavře a obrazovka otevře práva správce, nebo omezení — podle [admin]. */
    fun openMemberRights(admin: Boolean) {
        val member = _state.value.memberSheet ?: return
        _state.update {
            it.copy(memberSheet = null, openMemberRights = MemberRightsTarget(member.userId, admin))
        }
    }

    fun consumeOpenMemberRights() = _state.update { it.copy(openMemberRights = null) }

    /** Sesazení je uložení prázdných práv — vlastní volání na to TDLib nemá. */
    fun dismissAdmin() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null) }
        runMemberAction(UiText(Res.string.member_dismissed, member.name)) {
            profiles.setAdminRights(chatId, member.userId, AdminRights())
        }
    }

    fun unrestrictMember() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null) }
        runMemberAction(UiText(Res.string.member_unrestricted)) {
            profiles.unrestrictMember(chatId, member.userId)
        }
    }

    fun confirmBan() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null, memberBan = member) }
    }

    fun cancelBan() = _state.update { it.copy(memberBan = null) }

    /** Vyhození; [duration] `null` = navždy. */
    fun banMember(duration: Duration?) {
        val member = _state.value.memberBan ?: return
        _state.update { it.copy(memberBan = null) }
        runMemberAction(UiText(Res.string.member_banned, member.name)) {
            profiles.banMember(chatId, member.userId, duration)
        }
    }

    fun editMemberTag() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null, memberTag = member) }
    }

    fun cancelMemberTag() = _state.update { it.copy(memberTag = null) }

    fun setMemberTag(tag: String) {
        val member = _state.value.memberTag ?: return
        _state.update { it.copy(memberTag = null) }
        runMemberAction(UiText(Res.string.member_rights_saved)) {
            profiles.setMemberTag(chatId, member.userId, tag.trim())
        }
    }

    /**
     * Jestli převod vůbec jde, se ptáme PŘED otevřením dialogu: bez dvoufázového ověření
     * (nebo s čerstvě nastaveným heslem) to Telegram nedovolí a uživatel by psal heslo zbytečně.
     */
    fun startOwnershipTransfer() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null) }
        viewModelScope.launch {
            profiles.canTransferOwnership()
                .onSuccess { check ->
                    when (check) {
                        OwnershipTransferCheck.Ready -> _state.update { it.copy(memberTransfer = member) }
                        OwnershipTransferCheck.PasswordNeeded ->
                            _state.update { it.copy(error = UiText(Res.string.error_transfer_needs_password)) }
                        is OwnershipTransferCheck.TooFresh ->
                            _state.update { it.copy(error = UiText(Res.string.error_transfer_too_fresh)) }
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.profile_action_failed)) }
                }
        }
    }

    fun cancelOwnershipTransfer() = _state.update { it.copy(memberTransfer = null) }

    /** Heslo jde rovnou do repository a nikde se nedrží — ani ve stavu, ani v logu. */
    fun transferOwnership(password: String) {
        val member = _state.value.memberTransfer ?: return
        _state.update { it.copy(transferBusy = true) }
        viewModelScope.launch {
            profiles.transferOwnership(chatId, member.userId, password)
                .onSuccess {
                    _state.update {
                        it.copy(
                            memberTransfer = null,
                            transferBusy = false,
                            error = UiText(Res.string.member_transferred, member.name),
                        )
                    }
                    // Vlastníkem je teď někdo jiný, takže se změnila i naše vlastní práva.
                    reloadProfile()
                }
                .onFailure { failure ->
                    // Dialog zůstane otevřený: chybné heslo se opravuje na místě.
                    _state.update {
                        it.copy(transferBusy = false, error = failure.reported(Res.string.profile_action_failed))
                    }
                }
        }
    }

    /**
     * Akce nad členem: po úspěchu se seznam členů ČTE ZNOVU, nepřepisuje se lokálně. Co změna
     * postavení udělala (a jestli server vůbec poslechl), ví jen server — stejně jako u relací
     * v Nastavení → Zařízení.
     */
    private fun runMemberAction(success: UiText, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action()
                .onSuccess {
                    _state.update { it.copy(error = success) }
                    loadMembers()
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.profile_action_failed)) }
                }
        }
    }

    /** Po návratu z úpravy práv: role v seznamu se mohla změnit, a s ní i to, co sami smíme. */
    fun reloadMembers() {
        if (_state.value.profile?.canListMembers == true) loadMembers()
    }

    private fun reloadProfile() {
        viewModelScope.launch {
            profiles.profile(chatId).onSuccess { profile -> _state.update { it.copy(profile = profile) } }
            loadMembers()
        }
    }

    fun openLink(url: String) {
        urlOpener.open(url)
    }

    fun consumeOpenChat() {
        _state.update { it.copy(openChat = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    private fun Throwable.reported(message: StringResource): UiText {
        logger.w(this) { message.key }
        return userMessage(message)
    }
}

data class ChatProfileState(
    val profile: ChatProfile? = null,
    val loading: Boolean = true,
    /** `null` = načítají se, prázdné = nejsou nebo je server nevydal. */
    val members: List<ProfileMember>? = null,
    val presence: Presence? = null,
    /** `null`, dokud chat repository nezná — tlačítko se pak nekreslí. */
    val muted: Boolean? = null,
    /** Upozornění chatu i s tím, co přebírá z výchozího nastavení; `null` = řádek se nekreslí. */
    val notifications: ChatNotificationPrefs? = null,
    val notificationSheet: Boolean = false,
    /** `null`, dokud počty nedorazí; teprve podle nich se vykreslí záložky. */
    val counts: SharedMediaCounts? = null,
    /** Načtený obsah záložek; chybějící klíč = záložka se ještě neukázala. */
    val shared: Map<SharedMediaKind, SharedMediaTab> = emptyMap(),
    /** Průběh stahování souboru podle `fileId`, dokud se neotevře. */
    val documentDownloads: Map<Int, Float> = emptyMap(),
    val error: UiText? = null,
    /** Jednorázová událost: chat je smazaný / opuštěný, profil i konverzace se zavřou. */
    val chatGone: Boolean = false,
    /** Jednorázová událost: otevřít chat (člen skupiny). */
    val openChat: Long? = null,
    /** Jednorázová událost: otevřít prohlížeč médií na této zprávě. */
    val openMedia: Long? = null,
    /** Člen s otevřenou nabídkou akcí; `null` = nabídka není. */
    val memberSheet: MemberDetail? = null,
    /** Člen, u kterého se potvrzuje vyhození. */
    val memberBan: MemberDetail? = null,
    /** Člen, kterému se mění štítek. */
    val memberTag: MemberDetail? = null,
    /** Člen, kterému se předává vlastnictví; dialog se ptá na heslo. */
    val memberTransfer: MemberDetail? = null,
    /** Převod běží — tlačítko v dialogu se nesmí dát zmáčknout podruhé. */
    val transferBusy: Boolean = false,
    /** Jednorázová událost: otevřít obrazovku práv správce nebo omezení. */
    val openMemberRights: MemberRightsTarget? = null,
) {
    /**
     * Které záložky mají být. Členové jsou první, pak jen ty druhy obsahu, které v chatu jsou —
     * prázdná záložka by byla slib, který chat nesplní. Dokud počty nedorazí, nejsou žádné:
     * záložka, která by po chvíli zmizela, vypadá jako chyba.
     */
    val tabs: List<ProfileTab>
        get() {
            val counts = counts ?: return emptyList()
            return buildList {
                if (profile?.canListMembers == true) add(ProfileTab.Members)
                SharedMediaKind.entries.forEach { kind ->
                    if (counts.of(kind) > 0) add(ProfileTab.Shared(kind))
                }
            }
        }

    /** Zpráva z načtených záložek — pro otevření souboru nemusíme vědět, ze které je. */
    fun sharedMessage(messageId: Long): ChatMessage? =
        shared.values.firstNotNullOfOrNull { tab -> tab.messages.firstOrNull { it.id == messageId } }
}

/** Kam vede „Upravit práva": [admin] `true` = práva správce, `false` = omezení člena. */
data class MemberRightsTarget(val userId: Long, val admin: Boolean)

/** Záložka profilu. Členové jsou vlastní druh, ne sdílený obsah. */
sealed interface ProfileTab {
    data object Members : ProfileTab
    data class Shared(val kind: SharedMediaKind) : ProfileTab
}

/** Stav jedné záložky sdíleného obsahu. */
data class SharedMediaTab(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val reachedEnd: Boolean = false,
)

/** Na rozběh stahování se čeká jen chvíli; bez toho by mrtvé spojení nechalo kolečko točit se napořád. */
private const val DOWNLOAD_START_TIMEOUT = 10_000L
