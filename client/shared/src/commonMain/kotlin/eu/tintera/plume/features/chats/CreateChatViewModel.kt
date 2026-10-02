package eu.tintera.plume.features.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatCreation
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.CreatedChat
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.create_channel_failed
import eu.tintera.plume.resources.create_group_failed
import eu.tintera.plume.resources.edit_chat_photo_failed
import eu.tintera.plume.resources.edit_profile_not_photo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Co se po založení ukáže, než se chat otevře — jen když je co hlásit. */
data class CreatedReport(
    val chatId: Long,
    val notAdded: List<String>,
    val photoFailed: Boolean,
)

/**
 * Založení skupiny ([channel] = `false`) nebo kanálu: název, fotka, u kanálu popis.
 *
 * Text polí je **Compose stav přímo ve ViewModelu**, jako v úpravě chatu — hodnota přes tok
 * o dispatch později poskakuje kurzorem.
 *
 * **Fotka se jen připraví a nahraje až s chatem** (`setChatPhoto` potřebuje id). Ořezává se hned
 * po výběru, aby náhled ukazoval přesně to, co odejde. Když nahrání selže, chat už stojí —
 * zakládat ho kvůli fotce znovu by vyrobilo druhý; řekne se to a fotka jde přidat v úpravě.
 */
class CreateChatViewModel(
    val channel: Boolean,
    private val memberChatIds: List<Long>,
    private val creation: ChatCreation,
    private val profiles: ProfileRepository,
    private val squarePhoto: SquarePhoto,
    conversations: ConversationRepository,
) : ViewModel() {

    private val logger = Logger.withTag("CreateChat")

    var title by mutableStateOf("")
    var description by mutableStateOf("")

    /** Oříznutá fotka na disku; `null` = bez fotky. */
    var photoPath by mutableStateOf<String?>(null)
        private set
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
        if (memberChatIds.isNotEmpty()) {
            viewModelScope.launch {
                memberNames = memberChatIds
                    .map { chatId -> async { conversations.chatInfo(chatId).getOrNull()?.title } }
                    .awaitAll()
                    .filterNotNull()
            }
        }
    }

    /** Název je povinný — chat bez názvu server odmítne. */
    val canCreate: Boolean get() = title.isNotBlank() && !creating && !photoBusy

    /** Z výběru příloh; bere se první fotka, video fotkou chatu být nemůže. */
    fun setPhoto(attachments: List<OutgoingAttachment>) {
        val photo = attachments.firstOrNull() as? OutgoingAttachment.Photo
        if (photo == null) {
            notice = UiText(Res.string.edit_profile_not_photo)
            return
        }
        photoBusy = true
        viewModelScope.launch {
            runCatching { squarePhoto.crop(photo.path) }
                .onSuccess { photoPath = it }
                .onFailure { failure ->
                    logger.w(failure) { "Fotku pro nový chat se nepodařilo připravit." }
                    notice = UiText(Res.string.edit_chat_photo_failed)
                }
            photoBusy = false
        }
    }

    fun removePhoto() {
        photoPath = null
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
            ).onSuccess { chat -> finish(chat) }
                .onFailure { failure ->
                    logger.w(failure) { "Založení ${if (channel) "kanálu" else "skupiny"} selhalo." }
                    notice = failure.userMessage(if (channel) Res.string.create_channel_failed else Res.string.create_group_failed)
                    creating = false
                }
        }
    }

    private suspend fun finish(chat: CreatedChat) {
        val photoFailed = photoPath?.let { path ->
            profiles.setPhoto(chat.chatId, path)
                .onFailure { logger.w(it) { "Fotku nového chatu ${chat.chatId} se nepodařilo nastavit." } }
                .isFailure
        } ?: false
        // `creating` zůstává: chat stojí a obrazovka odchází, druhé ťuknutí by založilo další.
        if (chat.notAdded.isEmpty() && !photoFailed) {
            created = chat.chatId
        } else {
            report = CreatedReport(chat.chatId, chat.notAdded, photoFailed)
        }
    }
}
