package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ProfilePhotoFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatCreation
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.CreatedChat
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.RefusedMember
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.telegram.usernameFormatError
import eu.tintera.plume.core.ui.USERNAME_CHECK_DELAY_MS
import eu.tintera.plume.core.ui.UsernameStatus
import eu.tintera.plume.core.telegram.isChannelLimit
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.navigation.ChannelLimitRequests
import eu.tintera.plume.features.channellimit.PublicLinkLimitCheck
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.PreparedProfilePhoto
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.core.ui.VideoCropControl
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.create_channel_failed
import eu.tintera.plume.resources.create_group_failed
import eu.tintera.plume.resources.add_members_invite_failed
import eu.tintera.plume.resources.edit_chat_photo_failed
import eu.tintera.plume.resources.edit_profile_not_photo
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Co se po založení ukáže, než se chat otevře — jen když je co hlásit. */
data class CreatedReport(
    val chatId: Long,
    /** Koho server nepustil (soukromí); jde jim poslat [inviteLink]. */
    val refused: List<RefusedMember>,
    val photoFailed: Boolean,
    val usernameFailed: Boolean,
    /** Pozvánka nového chatu; `null` = nezjištěná, pak se jen řekne, koho přidat nešlo. */
    val inviteLink: String?,
)

/**
 * Založení skupiny ([channel] = `false`) nebo kanálu: název, fotka, u kanálu popis.
 *
 * Text polí je **Compose stav přímo ve ViewModelu**, jako v úpravě chatu — hodnota přes tok
 * o dispatch později poskakuje kurzorem.
 *
 * **Fotka se jen připraví a nahraje až s chatem** (`setChatPhoto` potřebuje id). Ořezává se hned
 * po výběru výřezu ([PhotoCropOverlay]), aby náhled ukazoval přesně to, co odejde. Když nahrání selže, chat už stojí —
 * zakládat ho kvůli fotce znovu by vyrobilo druhý; řekne se to a fotka jde přidat v úpravě.
 */
class CreateChatViewModel(
    val channel: Boolean,
    private val memberChatIds: List<Long>,
    private val creation: ChatCreation,
    private val profiles: ProfileRepository,
    private val photoCrop: ProfilePhotoCrop,
    conversations: ConversationRepository,
    /** Limit skupin a kanálů: založit další nejde, sheet s neaktivními k opuštění ukazuje shell. */
    private val channelLimit: ChannelLimitRequests? = null,
    /** Limit vlastních veřejných chatů ([PublicLinkLimitCheck]); bez něj se nezjišťuje předem. */
    chats: ChatRepository? = null,
) : ViewModel() {

    private val logger = Logger.withTag("CreateChat")

    var title by mutableStateOf("")
    var description by mutableStateOf("")

    /**
     * Veřejný chat (`t.me/[username]`), nebo soukromý — jen pozvánkou. Výchozí je soukromý:
     * veřejný najde kdokoli a to má být vědomá volba, ne přehlédnutý přepínač.
     */
    var isPublic by mutableStateOf(false)
        private set
    var username by mutableStateOf("")
    var usernameStatus by mutableStateOf<UsernameStatus>(UsernameStatus.Idle)
        private set

    /** Limit vlastních veřejných chatů; při něm obrazovka ukáže `PublicLinkLimitSheet`. */
    val publicLimit: PublicLinkLimitCheck? = chats?.let { PublicLinkLimitCheck(it, viewModelScope) }

    /** Soukromý, nebo veřejný. Na veřejný se hned zjistí, jestli ho limit pustí. */
    fun choosePublic(public: Boolean) {
        isPublic = public
        if (public) publicLimit?.publicChosen { isPublic } else publicLimit?.privateChosen()
    }

    /**
     * Sheet s limitem se zavřel. [released] = nějakému chatu se odebral odkaz a veřejný chat
     * už jde; jinak zpátky na soukromý — odkaz by stejně nešel nastavit.
     */
    fun publicLimitClosed(released: Boolean) {
        publicLimit?.closed()
        if (!released) isPublic = false
    }

    /** Oříznutá fotka (nebo video) na disku; `null` = bez fotky. */
    var photo by mutableStateOf<PreparedProfilePhoto?>(null)
        private set

    /**
     * Fotka z emoji nebo samolepky (`EmojiPhotoSheet`); s [photo] se vylučuje — volba jedné
     * zahodí druhou. Na server jde až po založení, stejně jako fotka ze souboru.
     */
    var emojiPhoto by mutableStateOf<ProfilePhotoFile?>(null)
        private set

    val hasPhoto: Boolean get() = photo != null || emojiPhoto != null

    fun chooseEmojiPhoto(choice: ProfilePhotoFile) {
        emojiPhoto = choice
        photo = null
    }
    var photoBusy by mutableStateOf(false)
        private set

    /** Jména vybraných členů, v pořadí výběru; dočítají se lokálně. */
    var memberNames by mutableStateOf<List<String>>(emptyList())
        private set

    val memberCount: Int get() = memberChatIds.size

    var creating by mutableStateOf(false)
        private set

    /** Hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    /** Založeno a není co hlásit — obrazovka otevře chat. */
    var created by mutableStateOf<Long?>(null)
        private set

    /** Založeno, ale něco se nepovedlo — dialog, pak se chat otevře. */
    var report by mutableStateOf<CreatedReport?>(null)
        private set

    init {
        // Dostupnost se ptá serveru až po pauze v psaní a jen nad jménem, které projde místními
        // pravidly — stejně jako u vlastního uživatelského jména.
        viewModelScope.launch {
            snapshotFlow { if (isPublic) username.trim() else "" }
                .distinctUntilChanged()
                .collectLatest { name ->
                    if (name.isEmpty() || usernameFormatError(name) != null) {
                        usernameStatus = UsernameStatus.Idle
                        return@collectLatest
                    }
                    usernameStatus = UsernameStatus.Checking
                    delay(USERNAME_CHECK_DELAY_MS)
                    usernameStatus = creation.checkUsername(name).fold(
                        onSuccess = { if (it == UsernameCheck.Available) UsernameStatus.Available else UsernameStatus.Unavailable(it) },
                        // Nezjištěná dostupnost založení neblokuje — rozhodne server.
                        onFailure = { UsernameStatus.Idle },
                    )
                }
        }
        if (memberChatIds.isNotEmpty()) {
            viewModelScope.launch {
                memberNames = memberChatIds
                    .map { chatId -> async { conversations.chatInfo(chatId).getOrNull()?.title } }
                    .awaitAll()
                    .filterNotNull()
            }
        }
    }

    /**
     * Název je povinný — chat bez názvu server odmítne. Veřejný chat chce jméno, které projde
     * pravidly a server ho neodmítl; nezjištěná dostupnost neblokuje.
     */
    val canCreate: Boolean
        get() {
            val name = username.trim()
            val usernameOk = !isPublic || (
                name.isNotEmpty() && usernameFormatError(name) == null &&
                    usernameStatus !is UsernameStatus.Unavailable && usernameStatus != UsernameStatus.Checking
                )
            return title.isNotBlank() && !creating && !photoBusy && usernameOk
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
                .onFailure { failure ->
                    logger.w(failure) { "Fotku pro nový chat se nepodařilo připravit." }
                    notice = UiText(Res.string.edit_chat_photo_failed)
                }
            photoBusy = false
        }
    }

    fun cancelCrop() {
        photoCrop.cancel()
    }

    fun cropPhoto(region: CropRegion) {
        photoBusy = true
        viewModelScope.launch {
            runCatching { photoCrop.crop(region) }
                .onSuccess { prepared ->
                    if (prepared != null) {
                        photo = prepared
                        emojiPhoto = null
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Fotku pro nový chat se nepodařilo připravit." }
                    notice = UiText(Res.string.edit_chat_photo_failed)
                }
            photoBusy = false
        }
    }

    fun removePhoto() {
        photo = null
        emojiPhoto = null
    }

    fun reportError(message: UiText) {
        notice = message
    }

    fun noticeShown() {
        notice = null
    }

    fun create() {
        if (!canCreate) return
        creating = true
        viewModelScope.launch {
            creation.create(
                title = title.trim(),
                description = description.trim(),
                channel = channel,
                memberChatIds = memberChatIds,
                username = if (isPublic) username.trim() else "",
            ).onSuccess { chat -> finish(chat) }
                .onFailure { failure ->
                    logger.w(failure) { "Založení ${if (channel) "kanálu" else "skupiny"} selhalo." }
                    if (failure.isChannelLimit() && channelLimit != null) {
                        channelLimit.request()
                    } else {
                        notice = failure.userMessage(if (channel) Res.string.create_channel_failed else Res.string.create_group_failed)
                    }
                    creating = false
                }
        }
    }

    private suspend fun finish(chat: CreatedChat) {
        val photoFailed = (photo?.file ?: emojiPhoto)?.let { file ->
            profiles.setPhoto(chat.chatId, file)
                .onFailure { logger.w(it) { "Fotku nového chatu ${chat.chatId} se nepodařilo nastavit." } }
                .isFailure
        } ?: false
        // `creating` zůstává: chat stojí a obrazovka odchází, druhé ťuknutí by založilo další.
        if (chat.refused.isEmpty() && !photoFailed && !chat.usernameFailed) {
            created = chat.chatId
            return
        }
        // Pozvánku vydá server vlastníkovi hned; bez ní dialog jen řekne, koho přidat nešlo.
        val link = if (chat.refused.isEmpty()) null else profiles.profile(chat.chatId).getOrNull()?.inviteLink
        report = CreatedReport(chat.chatId, chat.refused, photoFailed, chat.usernameFailed, link)
    }

    /**
     * Pošle pozvánku těm, které server nepustil — přidají se sami, když budou chtít. Tatáž cesta
     * jako u přidávání členů (`ProfileRepository.sendInviteLink`). Pak se chat otevře.
     */
    fun sendInvites() {
        val current = report ?: return
        val link = current.inviteLink ?: return
        if (sendingInvites) return
        sendingInvites = true
        viewModelScope.launch {
            profiles.sendInviteLink(current.refused.map { it.userId }, link)
                .onSuccess { created = current.chatId }
                .onFailure { failure ->
                    logger.w(failure) { "Pozvánku do nového chatu se nepodařilo poslat." }
                    notice = UiText(Res.string.add_members_invite_failed)
                    sendingInvites = false
                }
        }
    }

    var sendingInvites by mutableStateOf(false)
        private set
}
