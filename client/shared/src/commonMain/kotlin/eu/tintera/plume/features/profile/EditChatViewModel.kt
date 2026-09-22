package eu.tintera.plume.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.EditableChat
import eu.tintera.plume.core.telegram.GroupPermissions
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.edit_chat_load_failed
import eu.tintera.plume.resources.edit_chat_permissions_failed
import eu.tintera.plume.resources.edit_chat_photo_failed
import eu.tintera.plume.resources.edit_chat_save_failed
import eu.tintera.plume.resources.edit_profile_not_photo
import kotlinx.coroutines.launch

/**
 * Úprava skupiny nebo kanálu: název, popis, fotka a výchozí práva členů.
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
) : ViewModel() {

    private val logger = Logger.withTag("EditChat")

    var original by mutableStateOf<EditableChat?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")

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
                }
                .onFailure { failure ->
                    loadFailed = true
                    notice = failure.reported(Res.string.edit_chat_load_failed)
                }
        }
    }

    val changed: Boolean
        get() {
            val chat = original ?: return false
            return title.trim() != chat.title || description.trim() != chat.description
        }

    /** Název je povinný — chat bez názvu server odmítne. */
    val canSave: Boolean get() = changed && !saving && title.isNotBlank()

    /**
     * Uloží, co se změnilo. Název a popis jsou dvě různá volání: když první selže, druhé se
     * neposílá a obrazovka zůstane otevřená s hláškou — co prošlo, se zapíše do [original],
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
            }
            original = current
            saving = false
            result
                .onSuccess { saved = true }
                .onFailure { notice = it.reported(Res.string.edit_chat_save_failed) }
        }
    }

    /** Z výběru příloh; bere se první fotka, video fotkou chatu být nemůže. */
    fun setPhoto(attachments: List<OutgoingAttachment>) {
        val photo = attachments.firstOrNull() as? OutgoingAttachment.Photo
        if (photo == null) {
            notice = UiText(Res.string.edit_profile_not_photo)
            return
        }
        photoBusy = true
        viewModelScope.launch {
            // Ořez na čtverec ze středu: fotka se kreslí v kruhu a neoříznutou by každý klient
            // ořízl jinak — stejně jako u vlastní profilovky.
            runCatching { squarePhoto.crop(photo.path) }
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
