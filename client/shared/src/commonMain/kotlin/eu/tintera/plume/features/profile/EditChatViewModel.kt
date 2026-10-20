package eu.tintera.plume.features.profile

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
) : ViewModel() {

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
