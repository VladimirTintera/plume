package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.broadcast_group_failed
import eu.tintera.plume.resources.broadcast_group_done
import eu.tintera.plume.core.telegram.offersBroadcastGroup
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.resources.welcome_messages_delete_failed
import eu.tintera.plume.resources.welcome_messages_add_failed
import eu.tintera.plume.core.telegram.WelcomeMessage
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import eu.tintera.plume.resources.edit_chat_forum_failed
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.telegram.ForumRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import eu.tintera.plume.core.telegram.InviteLinkRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.EditableChat
import eu.tintera.plume.core.telegram.LinkedChat
import eu.tintera.plume.resources.edit_chat_discussion_failed
import eu.tintera.plume.resources.edit_chat_sticker_set_done
import eu.tintera.plume.resources.edit_chat_sticker_set_failed
import eu.tintera.plume.resources.edit_chat_sticker_set_removed
import eu.tintera.plume.resources.edit_chat_discussion_linked
import eu.tintera.plume.resources.edit_chat_discussion_unlinked
import eu.tintera.plume.core.telegram.GroupPermissions
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.resources.edit_chat_delete_failed
import eu.tintera.plume.resources.edit_chat_setting_failed
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.core.telegram.ChatReactions
import eu.tintera.plume.core.telegram.GroupToggle
import eu.tintera.plume.core.telegram.GroupSettings
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.core.ui.VideoCropControl
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.invite_links_share_failed
import eu.tintera.plume.resources.invite_links_copied
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.telegram.BoostStatus
import eu.tintera.plume.core.telegram.ChatBoostEntry
import eu.tintera.plume.resources.edit_chat_load_failed
import eu.tintera.plume.resources.edit_chat_permissions_failed
import eu.tintera.plume.resources.edit_chat_photo_failed
import eu.tintera.plume.resources.edit_chat_save_failed
import eu.tintera.plume.resources.edit_chat_upgrade_failed
import eu.tintera.plume.resources.edit_profile_not_photo
import androidx.compose.runtime.snapshotFlow
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.telegram.isPublicChatsLimit
import eu.tintera.plume.features.channellimit.PublicLinkLimitCheck
import eu.tintera.plume.core.telegram.usernameFormatError
import eu.tintera.plume.core.ui.USERNAME_CHECK_DELAY_MS
import eu.tintera.plume.core.ui.UsernameStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Úprava skupiny nebo kanálu: název, popis, veřejný odkaz, fotka a výchozí práva členů.
 *
 * Stavěno na [EditProfileViewModel][eu.tintera.plume.features.settings.EditProfileViewModel] —
 * text polí je **Compose stav přímo ve ViewModelu**, ne `StateFlow`: hodnota vrácená přes tok
 * o dispatch později poskakuje kurzorem.
 *
 * **Fotka a práva se ukládají hned, texty až tlačítkem Uložit.** Fotka má vlastní nahrávání
 * a přepínač práv se musí pohnout na ťuknutí; texty naopak nemá smysl posílat po písmenech.
 */
class EditChatViewModel(
    chatId: Long,
    private val repository: ProfileRepository,
    private val photoCrop: ProfilePhotoCrop,
    inviteLinks: InviteLinkRepository,
    private val forums: ForumRepository,
    private val chats: ChatRepository,
    private val stickers: StickerRepository,
    quickReaction: QuickReaction,
    /** Odkaz na boost do schránky a do systémového sdílení. */
    private val clipboard: TextClipboard? = null,
    private val sharer: MediaSharer? = null,
    /** Doporučení převést skupinu na gigaskupinu; bez něj se převod nenabízí. */
    notices: AccountNotices? = null,
) : ViewModel() {

    /**
     * Telegram doporučuje převést tyhle supergroupy na gigaskupinu (viz [offersBroadcastGroup]).
     * Compose stav, ne holý `StateFlow.value`: doporučení může dorazit až po otevření úpravy
     * a řádek se pak musí ukázat sám.
     */
    private var broadcastSuggestions by mutableStateOf<Set<Long>>(emptySet())

    init {
        notices?.let { source ->
            viewModelScope.launch { source.broadcastGroupSuggestions.collect { broadcastSuggestions = it } }
        }
    }

    /**
     * Upravovaný chat. Po převodu základní skupiny na supergroupu se změní na id nové skupiny —
     * obrazovka zůstává otevřená a všechno další (uložení, fotka, práva) jde už do ní.
     */
    private var chatId: Long = chatId

    /** Čeká na potvrzení převodu základní skupiny: kvůli čemu se převádí. `null` = nic. */
    var upgradeAsked by mutableStateOf<GroupUpgradeReason?>(null)
        private set

    /** Převod právě běží. */
    var upgrading by mutableStateOf(false)
        private set

    /**
     * Jednorázová událost: skupina se převedla, (staré id, nové id). Navigace přepíše otevřené
     * obrazovky staré skupiny na novou; obrazovka událost po předání vynuluje.
     */
    var upgraded by mutableStateOf<Pair<Long, Long>?>(null)
        private set

    /** Diskusní skupina kanálu, jak ji ukazuje řádek „Diskuse"; mění se po potvrzení serverem. */
    var linkedChat by mutableStateOf<LinkedChat?>(null)
        private set

    /** Skupiny k výběru v sheetu; `null`, dokud se nenačtou. */
    var discussionCandidates by mutableStateOf<List<ChatSummary>?>(null)
        private set

    var discussionBusy by mutableStateOf(false)
        private set

    /** Sada samolepek skupiny, jak ji ukazuje řádek; mění se po potvrzení serverem. */
    var stickerSet by mutableStateOf<StickerSetSummary?>(null)
        private set

    /** Sady k výběru (nainstalované sady uživatele); `null`, dokud se nenačtou. */
    var stickerSetCandidates by mutableStateOf<List<StickerSetSummary>?>(null)
        private set

    var stickerSetBusy by mutableStateOf(false)
        private set

    /**
     * Témata zapnutá, jak je ukazuje přepínač. Pohne se hned a při selhání se vrátí, jako práva
     * členů; skutečný stav pak přijde s `UpdateSupergroup` do seznamu chatů.
     */
    var isForum by mutableStateOf(false)
        private set

    /** Čekající žádosti o vstup — podtitul řádku, který na ně vede. Živě, vyřizuje je i jiný správce. */
    val pendingJoinRequests: StateFlow<Int> = inviteLinks.pendingJoinRequests(chatId).map { it.count }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val logger = Logger.withTag("EditChat")

    var original by mutableStateOf<EditableChat?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")

    /** Veřejný chat s odkazem [username]; ukládá se tlačítkem Uložit spolu s texty. */
    var isPublic by mutableStateOf(false)
        private set
    var username by mutableStateOf("")
    var usernameStatus by mutableStateOf<UsernameStatus>(UsernameStatus.Idle)
        private set

    /** Limit vlastních veřejných chatů; při něm obrazovka ukáže `PublicLinkLimitSheet`. */
    val publicLimit = PublicLinkLimitCheck(chats, viewModelScope)

    /**
     * Soukromý, nebo veřejný. Na veřejný se hned zjistí, jestli ho limit pustí — jen u chatu,
     * který veřejný ještě není: jeho vlastní odkaz už se do limitu počítá.
     */
    fun choosePublic(public: Boolean) {
        isPublic = public
        when {
            !public -> publicLimit.privateChosen()
            original?.username.isNullOrEmpty() -> publicLimit.publicChosen { isPublic }
        }
    }

    /**
     * Sheet s limitem se zavřel. [released] = nějakému chatu se odebral odkaz a veřejný chat
     * už jde; jinak zpátky na soukromý — odkaz by stejně nešel nastavit.
     */
    fun publicLimitClosed(released: Boolean) {
        publicLimit.closed()
        if (!released) isPublic = false
    }

    /** Veřejné jméno, jak by se uložilo: u soukromého chatu prázdné. */
    private val effectiveUsername: String get() = if (isPublic) username.trim() else ""

    /**
     * Práva členů, jak je ukazují přepínače. TDLib na jejich změnu žádný update neposílá, takže
     * je tohle jediný zdroj pravdy: přepínač se pohne hned a při selhání se vrátí — stejně jako
     * v nastavení archivu.
     */
    var permissions by mutableStateOf<GroupPermissions?>(null)
        private set

    /**
     * Pomalý režim, reakce a přepínače, jak je ukazuje obrazovka. Pohnou se hned a při selhání
     * se vrátí, stejně jako [permissions].
     */
    var settings by mutableStateOf(GroupSettings())
        private set

    /** Emoji, která jdou jako reakce povolit (`updateActiveEmojiReactions`), v pořadí od serveru. */
    val reactionChoices: StateFlow<List<String>> = quickReaction.choices

    var saving by mutableStateOf(false)
        private set
    var photoBusy by mutableStateOf(false)
        private set

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    /** Otevřený sheet boostů: `null` = zavřený, jinak stav načítání ([BoostSheet]). */
    var boosts by mutableStateOf<BoostSheet?>(null)
        private set

    /**
     * Boosty se čtou při každém otevření — mezitím mohly přibýt. Stav a první stránka seznamu
     * souběžně; selhání seznamu stav nezahodí (seznam se pak prostě neukáže).
     */
    fun openBoosts() {
        boosts = BoostSheet()
        viewModelScope.launch {
            repository.boostStatus(chatId)
                .onSuccess { status -> boosts = boosts?.copy(status = status) }
                .onFailure { failure ->
                    logger.w(failure) { "Stav boostů chatu $chatId se nepodařilo načíst." }
                    boosts = boosts?.copy(failed = true)
                }
        }
        loadBoosts(offset = "")
    }

    /** Další stránka seznamu boostů („Načíst další"). */
    fun loadMoreBoosts() {
        val sheet = boosts ?: return
        val offset = sheet.nextOffset ?: return
        if (sheet.loadingMore) return
        boosts = sheet.copy(loadingMore = true)
        loadBoosts(offset)
    }

    private fun loadBoosts(offset: String) {
        viewModelScope.launch {
            repository.boosts(chatId, offset)
                .onSuccess { page ->
                    boosts = boosts?.let { it.copy(entries = it.entries.orEmpty() + page.boosts, nextOffset = page.nextOffset, loadingMore = false) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Seznam boostů chatu $chatId se nepodařilo načíst." }
                    boosts = boosts?.copy(entries = boosts?.entries.orEmpty(), nextOffset = null, loadingMore = false)
                }
        }
    }

    fun closeBoosts() {
        boosts = null
    }

    fun copyBoostLink() {
        val url = boosts?.status?.boostUrl ?: return
        val clipboard = clipboard ?: return
        viewModelScope.launch {
            clipboard.copy(url)
            if (!clipboard.confirmsCopy) notice = UiText(Res.string.invite_links_copied)
        }
    }

    /** Systémové sdílení odkazu na boost — poprosit členy, ať chat boostnou. */
    fun shareBoostLink() {
        val url = boosts?.status?.boostUrl ?: return
        if (sharer?.shareText(url) != true) notice = UiText(Res.string.invite_links_share_failed)
    }

    /** Otevřený sheet uvítacích zpráv: `null` = zavřený ([WelcomeSheet]). */
    var welcome by mutableStateOf<WelcomeSheet?>(null)
        private set

    private var welcomeJob: Job? = null

    /**
     * Uvítací zprávy se sledují, dokud je sheet otevřený — seznam přichází updatem po načtení
     * i po každé změně (přidání, smazání, i z jiného zařízení), takže se po akci nemusí číst znovu.
     */
    fun openWelcomeMessages() {
        welcome = WelcomeSheet()
        welcomeJob?.cancel()
        welcomeJob = viewModelScope.launch {
            repository.welcomeMessages(chatId)
                .catch { failure ->
                    logger.w(failure) { "Uvítací zprávy chatu $chatId se nepodařilo načíst." }
                    welcome = welcome?.copy(failed = true)
                }
                .collect { messages -> welcome = welcome?.copy(messages = messages) }
        }
    }

    fun closeWelcomeMessages() {
        welcomeJob?.cancel()
        welcomeJob = null
        welcome = null
    }

    fun addWelcomeMessage(text: String) {
        val sheet = welcome ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sheet.busy) return
        welcome = sheet.copy(busy = true)
        viewModelScope.launch {
            repository.addWelcomeMessage(chatId, trimmed)
                .onSuccess { welcome = welcome?.copy(busy = false, added = (welcome?.added ?: 0) + 1) }
                .onFailure { failure ->
                    welcome = welcome?.copy(busy = false)
                    notice = failure.reported(Res.string.welcome_messages_add_failed)
                }
        }
    }

    fun deleteWelcomeMessage(id: Int) {
        viewModelScope.launch {
            repository.deleteWelcomeMessage(chatId, id)
                .onFailure { failure -> notice = failure.reported(Res.string.welcome_messages_delete_failed) }
        }
    }

    /** Smazání pro všechny právě běží. */
    var deleting by mutableStateOf(false)
        private set

    /** Jednorázová událost: chat je smazaný pro všechny, navigace zavře všechno, co ho ukazuje. */
    var deleted by mutableStateOf(false)
        private set

    /** Jednorázová událost: uloženo, obrazovka se zavře. */
    var saved by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repository.editableChat(chatId)
                .onSuccess { chat ->
                    original = chat
                    title = chat.title
                    description = chat.description
                    permissions = chat.permissions
                    settings = chat.settings
                    isForum = chat.isForum
                    linkedChat = chat.linkedChat
                    stickerSet = chat.stickerSet
                    username = chat.username
                    isPublic = chat.username.isNotEmpty()
                }
                .onFailure { failure ->
                    loadFailed = true
                    notice = failure.reported(Res.string.edit_chat_load_failed)
                }
        }
    }

    /*
     * Počty omezených a vyhozených v podtitulech se drží živě, když je mění jiný správce.
     * Přepíše se jen `moderation` — rozepsané texty zůstanou.
     */
    init {
        viewModelScope.launch {
            repository.memberUpdates(chatId).conflate().collect { refreshModeration() }
        }
    }

    private var composedOnce = false

    /**
     * Návrat ze seznamu omezených nebo vyhozených: počty se čtou znovu. Update o změně člena
     * na to nestačí — přijde dřív, než server nový stav vydá, a čtení v tu chvíli vrátilo
     * ještě starý počet (ověřeno: po zrušení omezení zůstalo „1 člověk"). Poprvé nic, první
     * načtení dělá `init`.
     */
    fun refreshOnReturn() {
        if (!composedOnce) {
            composedOnce = true
            return
        }
        viewModelScope.launch { refreshModeration() }
    }

    private suspend fun refreshModeration() {
        repository.editableChat(chatId).onSuccess { fresh -> original = original?.copy(moderation = fresh.moderation) }
    }

    init {
        // Dostupnost se ptá serveru po pauze v psaní a jen nad jménem, které projde místními
        // pravidly a není to současné jméno chatu — stejně jako u vlastního uživatelského jména.
        viewModelScope.launch {
            snapshotFlow { effectiveUsername }
                .distinctUntilChanged()
                .collectLatest { name ->
                    val unchanged = name.equals(original?.username.orEmpty(), ignoreCase = true)
                    if (name.isEmpty() || unchanged || usernameFormatError(name) != null) {
                        usernameStatus = UsernameStatus.Idle
                        return@collectLatest
                    }
                    usernameStatus = UsernameStatus.Checking
                    delay(USERNAME_CHECK_DELAY_MS)
                    usernameStatus = repository.checkUsername(chatId, name).fold(
                        onSuccess = { if (it == UsernameCheck.Available) UsernameStatus.Available else UsernameStatus.Unavailable(it) },
                        // Nezjištěná dostupnost ukládání neblokuje — rozhodne server.
                        onFailure = { UsernameStatus.Idle },
                    )
                }
        }
    }

    val changed: Boolean
        get() {
            val chat = original ?: return false
            return title.trim() != chat.title || description.trim() != chat.description ||
                effectiveUsername != chat.username
        }

    val canSave: Boolean
        get() {
            val chat = original ?: return false
            val name = effectiveUsername
            // Veřejný chat chce jméno; soukromý (prázdné) projde vždycky.
            val usernameOk = name == chat.username || (!isPublic) || (
                name.isNotEmpty() && usernameFormatError(name) == null &&
                    usernameStatus !is UsernameStatus.Unavailable && usernameStatus != UsernameStatus.Checking
                )
            return changed && !saving && !upgrading && title.isNotBlank() && usernameOk
        }

    /**
     * Uloží, co se změnilo. Název, popis a veřejný odkaz jsou tři různá volání: když jedno selže,
     * další se neposílají a obrazovka zůstane otevřená s hláškou — co prošlo, se zapíše do [original],
     * takže další pokus to neposílá znovu. Stejně to dělá úprava vlastního profilu.
     */
    fun save() {
        val chat = original ?: return
        if (!canSave) return
        // Veřejný odkaz má jen supergroupa: základní skupina se napřed převede, a to se potvrzuje.
        if (chat.isBasicGroup && effectiveUsername.isNotEmpty()) {
            upgradeAsked = GroupUpgradeReason.PublicLink
            return
        }
        saveChanges(chat)
    }

    private fun saveChanges(chat: EditableChat) {
        saving = true
        viewModelScope.launch {
            var current = chat
            val name = title.trim()
            val about = description.trim()
            val result = runCatching {
                if (name != current.title) {
                    repository.setTitle(chatId, name).getOrThrow()
                    current = current.copy(title = name)
                }
                if (about != current.description) {
                    repository.setDescription(chatId, about).getOrThrow()
                    current = current.copy(description = about)
                }
                val link = effectiveUsername
                if (link != current.username) {
                    repository.setUsername(chatId, link).getOrThrow()
                    current = current.copy(username = link)
                }
            }
            original = current
            saving = false
            result
                .onSuccess { saved = true }
                .onFailure { failure ->
                    // Limit veřejných chatů se při volbě Veřejný zjistit nemusel (spojení);
                    // server ho ohlásí až tady a místo hlášky se nabídne odebrat odkaz jinému.
                    if (failure.isPublicChatsLimit()) {
                        logger.w(failure) { "Odkaz chatu $chatId narazil na limit veřejných chatů." }
                        publicLimit.hit()
                    } else {
                        notice = failure.reported(Res.string.edit_chat_save_failed)
                    }
                }
        }
    }

    /** Obrázek čekající na ořez ([PhotoCropOverlay]); `null` = ořez zavřený. */
    val cropping: String? get() = photoCrop.image

    /** V ořezu je snímek videa — ořez řekne, že se zkrátí a přijde o zvuk. */
    val croppingVideo: VideoCropControl? get() = photoCrop.video

    /** Z výběru příloh; bere se první fotka nebo video (animovaná fotka chatu). */
    fun setPhoto(attachments: List<OutgoingAttachment>) {
        photoBusy = true
        viewModelScope.launch {
            runCatching { photoCrop.open(attachments) }
                .onSuccess { opened -> if (!opened) notice = UiText(Res.string.edit_profile_not_photo) }
                .onFailure { notice = it.reported(Res.string.edit_chat_photo_failed) }
            photoBusy = false
        }
    }

    fun cancelCrop() {
        photoCrop.cancel()
    }

    fun cropPhoto(region: CropRegion) {
        photoBusy = true
        viewModelScope.launch {
            // Ořez na čtverec: fotka se kreslí v kruhu a neoříznutou by každý klient ořízl
            // jinak — stejně jako u vlastní profilovky. Výřez vybral uživatel.
            runCatching { photoCrop.crop(region) }
                .mapCatching { prepared -> prepared?.let { repository.setPhoto(chatId, it.file).getOrThrow() } }
                .onSuccess { reloadPhoto() }
                .onFailure { notice = it.reported(Res.string.edit_chat_photo_failed) }
            photoBusy = false
        }
    }

    fun deletePhoto() {
        photoBusy = true
        viewModelScope.launch {
            repository.deletePhoto(chatId)
                .onSuccess { reloadPhoto() }
                .onFailure { notice = it.reported(Res.string.edit_chat_photo_failed) }
            photoBusy = false
        }
    }

    /** Přepínač se pohne hned; při selhání se vrátí a ohlásí se to. */
    fun setPermission(change: (GroupPermissions) -> GroupPermissions) {
        val previous = permissions ?: return
        val updated = change(previous)
        if (updated == previous) return
        permissions = updated

        viewModelScope.launch {
            repository.setPermissions(chatId, updated).onFailure { failure ->
                logger.w(failure) { "Práva skupiny $chatId se nepodařilo uložit." }
                permissions = previous
                notice = failure.reported(Res.string.edit_chat_permissions_failed)
            }
        }
    }

    /** Nabídnout převod na gigaskupinu: vlastníkovi, kterému ho Telegram doporučil. */
    val offersBroadcastGroup: Boolean
        get() = original?.let { offersBroadcastGroup(it, broadcastSuggestions) } == true

    /** Převod na gigaskupinu právě běží. */
    var convertingToBroadcast by mutableStateOf(false)
        private set

    /** Převede skupinu na gigaskupinu; potvrzení drží obrazovka (převod je nevratný). */
    fun convertToBroadcastGroup() {
        val chat = original ?: return
        val supergroupId = chat.supergroupId ?: return
        if (convertingToBroadcast || !offersBroadcastGroup) return
        convertingToBroadcast = true
        viewModelScope.launch {
            repository.convertToBroadcastGroup(supergroupId)
                .onSuccess {
                    original = original?.copy(isBroadcastGroup = true)
                    notice = UiText(Res.string.broadcast_group_done)
                }
                .onFailure { notice = it.reported(Res.string.broadcast_group_failed) }
            convertingToBroadcast = false
        }
    }

    fun deleteChatForAll() {
        if (deleting || original?.canDeleteForAll != true) return
        deleting = true
        viewModelScope.launch {
            repository.deleteChatForAll(chatId)
                .onSuccess { deleted = true }
                .onFailure { notice = it.reported(Res.string.edit_chat_delete_failed) }
            deleting = false
        }
    }

    fun setSlowMode(seconds: Int) {
        val previous = settings
        if (previous.slowModeDelay == null || previous.slowModeDelay == seconds) return
        settings = previous.copy(slowModeDelay = seconds)
        viewModelScope.launch {
            repository.setSlowMode(chatId, seconds).onFailure { failure ->
                settings = settings.copy(slowModeDelay = previous.slowModeDelay)
                notice = failure.reported(Res.string.edit_chat_setting_failed)
            }
        }
    }

    fun setToggle(toggle: GroupToggle, value: Boolean) {
        val previous = settings.toggles[toggle] ?: return
        if (previous == value) return
        val before = settings.toggles
        // Vypnuté podpisy vypnou i profily autorů — tak to uloží i repository.
        settings = settings.copy(
            toggles = before + (toggle to value) +
                if (toggle == GroupToggle.SignMessages && !value && GroupToggle.ShowMessageSender in before) {
                    mapOf(GroupToggle.ShowMessageSender to false)
                } else {
                    emptyMap()
                },
        )
        viewModelScope.launch {
            repository.setGroupToggle(chatId, toggle, value).onFailure { failure ->
                settings = settings.copy(toggles = before)
                notice = failure.reported(Res.string.edit_chat_setting_failed)
            }
        }
    }

    fun setReactions(reactions: ChatReactions) {
        val previous = settings.reactions ?: return
        if (previous == reactions) return
        settings = settings.copy(reactions = reactions)
        viewModelScope.launch {
            repository.setReactions(chatId, reactions).onFailure { failure ->
                settings = settings.copy(reactions = previous)
                notice = failure.reported(Res.string.edit_chat_setting_failed)
            }
        }
    }

    fun toggleForum(forum: Boolean) {
        if (forum == isForum) return
        if (forum && original?.isBasicGroup == true) {
            upgradeAsked = GroupUpgradeReason.Topics
            return
        }
        isForum = forum
        viewModelScope.launch {
            forums.setForum(chatId, forum).onFailure { failure ->
                isForum = !forum
                notice = failure.reported(Res.string.edit_chat_forum_failed)
            }
        }
    }

    fun cancelUpgrade() {
        upgradeAsked = null
    }

    /**
     * Potvrzený převod: skupina se převede, ViewModel se přepne na nové id a pak udělá, kvůli
     * čemu se převádělo — uloží (i s odkazem), nebo zapne témata. Rozepsané texty zůstávají.
     * Když převod projde a to další ne, skupina už převedená JE; hláška to řekne a obrazovka
     * ukazuje novou skupinu, takže další pokus jde rovnou do ní.
     */
    fun confirmUpgrade() {
        val reason = upgradeAsked ?: return
        upgradeAsked = null
        if (upgrading) return
        upgrading = true
        viewModelScope.launch {
            val old = chatId
            val fresh = repository.upgradeToSupergroup(old)
                .mapCatching { new ->
                    chatId = new
                    upgraded = old to new
                    repository.editableChat(new).getOrThrow()
                }
            upgrading = false
            fresh
                .onSuccess { chat ->
                    original = chat
                    when (reason) {
                        GroupUpgradeReason.PublicLink -> saveChanges(chat)
                        GroupUpgradeReason.Topics -> toggleForum(true)
                    }
                }
                .onFailure { notice = it.reported(Res.string.edit_chat_upgrade_failed) }
        }
    }

    fun upgradeHandled() {
        upgraded = null
    }

    /** Načte skupiny, které jde připojit; volá se při otevření sheetu, ne dopředu. */
    fun loadDiscussionCandidates() {
        discussionCandidates = null
        viewModelScope.launch { discussionCandidates = chats.suitableDiscussionChats() }
    }

    /**
     * Připojí diskusní skupinu (`null` odpojí). Řádek se změní až po potvrzení serverem —
     * propojení má podmínky (práva ve skupině), které zná jen on.
     */
    fun setDiscussion(group: ChatSummary?) {
        if (discussionBusy) return
        discussionBusy = true
        viewModelScope.launch {
            repository.setDiscussionGroup(chatId, group?.id)
                .onSuccess {
                    linkedChat = group?.let { LinkedChat(it.id, it.title, it.avatarFileId) }
                    notice = UiText(if (group != null) Res.string.edit_chat_discussion_linked else Res.string.edit_chat_discussion_unlinked)
                }
                .onFailure { notice = it.reported(Res.string.edit_chat_discussion_failed) }
            discussionBusy = false
        }
    }

    /**
     * Načte sady k výběru — nainstalované sady uživatele, jako v oficiálních klientech; cizí sadu
     * si napřed přidá. Volá se při otevření sheetu, ne dopředu.
     */
    fun loadStickerSetCandidates() {
        stickerSetCandidates = null
        viewModelScope.launch { stickerSetCandidates = stickers.installedSets().first() }
    }

    /** Nastaví sadu skupiny (`null` zruší). Řádek se změní až po potvrzení serverem. */
    fun chooseStickerSet(set: StickerSetSummary?) {
        if (stickerSetBusy) return
        stickerSetBusy = true
        viewModelScope.launch {
            repository.setStickerSet(chatId, set?.id)
                .onSuccess {
                    stickerSet = set
                    notice = UiText(if (set != null) Res.string.edit_chat_sticker_set_done else Res.string.edit_chat_sticker_set_removed)
                }
                .onFailure { notice = it.reported(Res.string.edit_chat_sticker_set_failed) }
            stickerSetBusy = false
        }
    }

    fun reportError(message: UiText) {
        notice = message
    }

    fun noticeShown() {
        notice = null
    }

    /** Po změně fotky jen fotka — rozepsané texty se přepsat nesmí. */
    private suspend fun reloadPhoto() {
        repository.editableChat(chatId).onSuccess { fresh ->
            original = original?.copy(
                photoFileId = fresh.photoFileId,
                photoThumbnailFileId = fresh.photoThumbnailFileId,
            )
        }
    }

    /** Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`. */
    private fun Throwable.reported(fallback: org.jetbrains.compose.resources.StringResource): UiText {
        logger.w(this) { "Úprava chatu $chatId selhala." }
        return userMessage(fallback)
    }
}

/** Proč se základní skupina převádí na supergroupu — podle toho mluví potvrzení. */
enum class GroupUpgradeReason { PublicLink, Topics }

/**
 * Sheet uvítacích zpráv: [messages] po načtení (`null` = načítá se), [failed] když načtení
 * selhalo, [busy] během přidávání. [added] se zvedá po každém přidání — sheet podle něj
 * vyprázdní pole.
 */
data class WelcomeSheet(
    val messages: List<WelcomeMessage>? = null,
    val failed: Boolean = false,
    val busy: Boolean = false,
    val added: Int = 0,
)

/** Sheet boostů: [status] po načtení, [failed] když načtení selhalo, jinak se načítá. */
data class BoostSheet(
    val status: BoostStatus? = null,
    val failed: Boolean = false,
    /** Jednotlivé boosty; `null` = první stránka se ještě čte. */
    val entries: List<ChatBoostEntry>? = null,
    /** Odkud číst další stránku; `null` = dál nic není. */
    val nextOffset: String? = null,
    val loadingMore: Boolean = false,
)
