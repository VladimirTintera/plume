package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.edit_chat_forum_failed
import eu.tintera.plume.core.telegram.ForumRepository
import kotlinx.coroutines.flow.distinctUntilChanged
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
import eu.tintera.plume.resources.edit_chat_discussion_linked
import eu.tintera.plume.resources.edit_chat_discussion_unlinked
import eu.tintera.plume.core.telegram.GroupPermissions
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.edit_chat_load_failed
import eu.tintera.plume.resources.edit_chat_permissions_failed
import eu.tintera.plume.resources.edit_chat_photo_failed
import eu.tintera.plume.resources.edit_chat_save_failed
import eu.tintera.plume.resources.edit_profile_not_photo
import androidx.compose.runtime.snapshotFlow
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.telegram.usernameFormatError
import eu.tintera.plume.core.ui.USERNAME_CHECK_DELAY_MS
import eu.tintera.plume.core.ui.UsernameStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    private val chatId: Long,
    private val repository: ProfileRepository,
    private val squarePhoto: SquarePhoto,
    inviteLinks: InviteLinkRepository,
    private val forums: ForumRepository,
    private val chats: ChatRepository,
) : ViewModel() {

    /** Diskusní skupina kanálu, jak ji ukazuje řádek „Diskuse"; mění se po potvrzení serverem. */
    var linkedChat by mutableStateOf<LinkedChat?>(null)
        private set

    /** Skupiny k výběru v sheetu; `null`, dokud se nenačtou. */
    var discussionCandidates by mutableStateOf<List<ChatSummary>?>(null)
        private set

    var discussionBusy by mutableStateOf(false)
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
    var username by mutableStateOf("")
    var usernameStatus by mutableStateOf<UsernameStatus>(UsernameStatus.Idle)
        private set

    /** Veřejné jméno, jak by se uložilo: u soukromého chatu prázdné. */
    private val effectiveUsername: String get() = if (isPublic) username.trim() else ""

    /**
     * Práva členů, jak je ukazují přepínače. TDLib na jejich změnu žádný update neposílá, takže
     * je tohle jediný zdroj pravdy: přepínač se pohne hned a při selhání se vrátí — stejně jako
     * v nastavení archivu.
     */
    var permissions by mutableStateOf<GroupPermissions?>(null)
        private set

    var saving by mutableStateOf(false)
        private set
    var photoBusy by mutableStateOf(false)
        private set

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

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
                    isForum = chat.isForum
                    linkedChat = chat.linkedChat
                    username = chat.username
                    isPublic = chat.username.isNotEmpty()
                }
                .onFailure { failure ->
                    loadFailed = true
                    notice = failure.reported(Res.string.edit_chat_load_failed)
                }
        }
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
            return changed && !saving && title.isNotBlank() && usernameOk
        }

    /**
     * Uloží, co se změnilo. Název, popis a veřejný odkaz jsou tři různá volání: když jedno selže,
     * další se neposílají a obrazovka zůstane otevřená s hláškou — co prošlo, se zapíše do [original],
     * takže další pokus to neposílá znovu. Stejně to dělá úprava vlastního profilu.
     */
    fun save() {
        val chat = original ?: return
        if (!canSave) return
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
                .onFailure { notice = it.reported(Res.string.edit_chat_save_failed) }
        }
    }

    /** Z výběru příloh; bere se první fotka, video fotkou chatu být nemůže. */
    /** Fotka čekající na ořez ([PhotoCropOverlay]); `null` = ořez zavřený. */
    var cropping by mutableStateOf<String?>(null)
        private set

    fun setPhoto(attachments: List<OutgoingAttachment>) {
        val photo = attachments.firstOrNull() as? OutgoingAttachment.Photo
        if (photo == null) {
            notice = UiText(Res.string.edit_profile_not_photo)
            return
        }
        cropping = photo.path
    }

    fun cancelCrop() {
        cropping = null
    }

    fun cropPhoto(region: CropRegion) {
        val path = cropping ?: return
        cropping = null
        photoBusy = true
        viewModelScope.launch {
            // Ořez na čtverec: fotka se kreslí v kruhu a neoříznutou by každý klient ořízl
            // jinak — stejně jako u vlastní profilovky. Výřez vybral uživatel.
            runCatching { squarePhoto.crop(path, region) }
                .mapCatching { repository.setPhoto(chatId, it).getOrThrow() }
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

    fun toggleForum(forum: Boolean) {
        if (forum == isForum) return
        isForum = forum
        viewModelScope.launch {
            forums.setForum(chatId, forum).onFailure { failure ->
                isForum = !forum
                notice = failure.reported(Res.string.edit_chat_forum_failed)
            }
        }
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
