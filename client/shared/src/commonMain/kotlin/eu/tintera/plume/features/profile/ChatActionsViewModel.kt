package eu.tintera.plume.features.profile

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.Birthdate
import eu.tintera.plume.core.telegram.ChatProfile
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.VideoCropControl
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.birthdate_suggest_failed
import eu.tintera.plume.resources.birthdate_suggest_sent
import eu.tintera.plume.resources.contact_add_failed
import eu.tintera.plume.resources.contact_photo_failed
import eu.tintera.plume.resources.contact_photo_removed
import eu.tintera.plume.resources.contact_photo_set
import eu.tintera.plume.resources.contact_remove_failed
import eu.tintera.plume.resources.contact_removed
import eu.tintera.plume.resources.contact_save_failed
import eu.tintera.plume.resources.contact_saved
import eu.tintera.plume.resources.edit_profile_not_photo
import eu.tintera.plume.resources.photo_suggest_failed
import eu.tintera.plume.resources.photo_suggest_sent
import eu.tintera.plume.resources.profile_action_failed
import eu.tintera.plume.resources.profile_link_copied
import eu.tintera.plume.resources.secret_chat_start_failed
import eu.tintera.plume.resources.spam_bar_contact_added
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Akce nad chatem, které nejsou o obsahu konverzace ani o obrazovce profilu: odkaz a QR kód,
 * kontakt (přidat, upravit, odebrat, poslat dál), zástupce na ploše, tajný chat, návrh fotky
 * a data narození, zablokování.
 *
 * Bývaly jen v ⋮ profilu (a stály na celém [ChatProfileViewModel] se členy a médii). Teď je
 * nabízí ⋮ konverzace i profilu ([ChatActions]) — jedna implementace, ať se dvě cesty
 * k blokování nebo kontaktu nerozejdou. Profil čte údaje znovu sám (`profileUpdates`), takže
 * se po akci odsud obnoví i obrazovka profilu.
 *
 * Profil se čte jednou a pak po updatech; TDLib ho má v paměti, takže stojí jedno lokální čtení
 * na otevřenou konverzaci.
 */
class ChatActionsViewModel(
    val chatId: Long,
    private val profiles: ProfileRepository,
    private val chats: ChatRepository,
    private val clipboard: TextClipboard,
    /** Ořez navrhované / vlastní fotky; bez něj se fotka navrhnout ani nastavit nedá. */
    private val photoCrop: ProfilePhotoCrop? = null,
    /** „Přidat na plochu"; bez něj (a mimo Android) se nenabízí. */
    private val shareTargets: ShareTargets? = null,
) : ViewModel() {

    private val logger = Logger.withTag("ChatActions")

    private val _state = MutableStateFlow(ChatActionsState())
    val state: StateFlow<ChatActionsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshProfile()
            // Přejmenování, kontakt, blokování i odjinud: nabídka musí říkat, co platí teď.
            profiles.profileUpdates(chatId).conflate().collect { refreshProfile() }
        }
    }

    private suspend fun refreshProfile() {
        profiles.profile(chatId)
            .onSuccess { profile -> _state.update { it.copy(profile = profile) } }
            .onFailure { failure -> logger.w(failure) { "Profil pro akce nad chatem se nepodařilo načíst." } }
    }

    /** Hláška ukázaná obrazovkou, která akce hostí; obrazovka ji vyzvedne. */
    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Chat k otevření (založený tajný chat); obrazovka ho vyzvedne. */
    fun consumeOpenChat() {
        _state.update { it.copy(openChat = null) }
    }

    fun reportError(text: UiText) {
        _state.update { it.copy(message = text) }
    }

    /** Jde chat připnout na plochu (Android s launcherem, který to dovolí). */
    val canAddToHomeScreen: Boolean get() = shareTargets?.canPinToHomeScreen == true

    /** Požádá launcher o zástupce chatu; potvrzení je dialog launcheru. */
    fun addToHomeScreen() {
        shareTargets?.pinToHomeScreen(chatId)
    }

    /** Odkaz na chat (veřejné jméno, jinak pozvánka) do schránky. */
    fun copyLink() {
        val link = _state.value.profile?.link ?: return
        viewModelScope.launch {
            clipboard.copy(link)
            if (!clipboard.confirmsCopy) _state.update { it.copy(message = UiText(Res.string.profile_link_copied)) }
        }
    }

    /** „Navrhnout profilovou fotku" a vlastní fotka kontaktu jsou k dispozici (je čím ořezat). */
    val canCropPhoto: Boolean get() = photoCrop != null

    /** Obrázek v ořezu; `null` = ořez zavřený. */
    val cropping: String? get() = photoCrop?.image

    val croppingVideo: VideoCropControl? get() = photoCrop?.video

    /** K čemu je otevřený ořez: návrh fotky člověku, nebo vlastní fotka kontaktu. */
    private var cropForPersonalPhoto = false

    /** Z výběru příloh: první fotka nebo video jde do ořezu, jako u vlastní profilovky. */
    fun suggestPhotoFrom(attachments: List<OutgoingAttachment>) {
        cropForPersonalPhoto = false
        openCrop(attachments)
    }

    /** Vlastní fotka kontaktu: stejný výběr a ořez jako návrh, jen se nikomu neposílá. */
    fun personalPhotoFrom(attachments: List<OutgoingAttachment>) {
        cropForPersonalPhoto = true
        openCrop(attachments)
    }

    private fun openCrop(attachments: List<OutgoingAttachment>) {
        val crop = photoCrop ?: return
        viewModelScope.launch {
            runCatching { crop.open(attachments) }
                .onSuccess { opened -> if (!opened) reportError(UiText(Res.string.edit_profile_not_photo)) }
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.photo_suggest_failed)) } }
        }
    }

    fun cancelCrop() {
        photoCrop?.cancel()
    }

    /** Potvrzený ořez: podle toho, k čemu se otevřel, návrh fotky, nebo vlastní fotka kontaktu. */
    fun confirmCrop(region: CropRegion) =
        if (cropForPersonalPhoto) setPersonalPhoto(region) else suggestCroppedPhoto(region)

    private fun setPersonalPhoto(region: CropRegion) {
        val crop = photoCrop ?: return
        val userId = _state.value.profile?.userId ?: return
        viewModelScope.launch {
            runCatching { crop.crop(region) }
                .mapCatching { prepared -> prepared?.let { profiles.setPersonalPhoto(userId, it.file).getOrThrow() } }
                .onSuccess { _state.update { it.copy(message = UiText(Res.string.contact_photo_set)) } }
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.contact_photo_failed)) } }
        }
    }

    /** Ořízne a pošle návrh; přijde mu jako zpráva s fotkou a „Nastavit". */
    private fun suggestCroppedPhoto(region: CropRegion) {
        val crop = photoCrop ?: return
        val userId = _state.value.profile?.userId ?: return
        viewModelScope.launch {
            runCatching { crop.crop(region) }
                .mapCatching { prepared -> prepared?.let { profiles.suggestPhoto(userId, it.file).getOrThrow() } }
                .onSuccess { _state.update { it.copy(message = UiText(Res.string.photo_suggest_sent)) } }
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.photo_suggest_failed)) } }
        }
    }

    /** Odebere vlastní fotku kontaktu; vrátí se ta, kterou si nastavil sám. */
    fun removePersonalPhoto() {
        val userId = _state.value.profile?.userId ?: return
        viewModelScope.launch {
            profiles.setPersonalPhoto(userId, null)
                .onSuccess { _state.update { it.copy(message = UiText(Res.string.contact_photo_removed)) } }
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.contact_photo_failed)) } }
        }
    }

    /**
     * Uloží jméno a poznámku kontaktu. Poznámka se posílá jen změněná: nezměněná si tak nechá
     * formátování, které jí dal jiný klient (pole tady upravuje holý text).
     */
    fun saveContact(firstName: String, lastName: String, note: String) {
        val profile = _state.value.profile ?: return
        val userId = profile.userId ?: return
        val contact = profile.contact ?: return
        viewModelScope.launch {
            profiles.editContact(
                userId = userId,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                note = note.trim().takeIf { it != contact.note },
            )
                .onSuccess { _state.update { it.copy(message = UiText(Res.string.contact_saved)) } }
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.contact_save_failed)) } }
        }
    }

    /** Navrhne člověku datum narození; přijde mu jako zpráva s „Nastavit". */
    fun suggestBirthdate(birthdate: Birthdate) {
        val userId = _state.value.profile?.userId ?: return
        viewModelScope.launch {
            profiles.suggestBirthdate(userId, birthdate)
                .onSuccess { _state.update { it.copy(message = UiText(Res.string.birthdate_suggest_sent)) } }
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.birthdate_suggest_failed)) } }
        }
    }

    /**
     * Stav se po úspěchu přepíše hned lokálně, aby nabídka nečekala; potvrzení dorazí i updatem
     * (`profileUpdates`, plné info uživatele nese blokování).
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
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.profile_action_failed)) } }
        }
    }

    /**
     * Přidá uživatele do kontaktů, nebo ho z nich odebere. Přidání bere jméno z účtu (tatáž cesta
     * jako pruh nad konverzací), odebrání jde přes [ChatRepository.removeContact].
     */
    fun setContact(contact: Boolean) {
        val profile = _state.value.profile ?: return
        val userId = profile.userId ?: return
        viewModelScope.launch {
            val result = if (contact) chats.addToContacts(chatId) else chats.removeContact(userId)
            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            profile = it.profile?.copy(isContact = contact),
                            message = UiText(if (contact) Res.string.spam_bar_contact_added else Res.string.contact_removed),
                        )
                    }
                }
                .onFailure { failure ->
                    val fallback = if (contact) Res.string.contact_add_failed else Res.string.contact_remove_failed
                    _state.update { it.copy(message = failure.reported(fallback)) }
                }
        }
    }

    /**
     * Nový tajný chat s člověkem. Založí se hned a otevře; protějšek ho dostane, až bude online —
     * do té doby konverzace říká, že se čeká.
     */
    fun startSecretChat() {
        val userId = _state.value.profile?.userId ?: return
        viewModelScope.launch {
            chats.createSecretChat(userId)
                .onSuccess { target -> _state.update { it.copy(openChat = target) } }
                .onFailure { failure -> _state.update { it.copy(message = failure.reported(Res.string.secret_chat_start_failed)) } }
        }
    }

    private fun Throwable.reported(message: StringResource): UiText {
        logger.w(this) { message.key }
        return userMessage(message)
    }
}

data class ChatActionsState(
    /** Profil chatu; `null`, dokud se nenačte — nabídka do té doby akce nad chatem nemá. */
    val profile: ChatProfile? = null,
    /** Hláška pro snackbar obrazovky; `null` = nic. */
    val message: UiText? = null,
    /** Chat k otevření (tajný chat založený odsud); `null` = nic. */
    val openChat: Long? = null,
)

/**
 * ViewModel akcí nad chatem [chatId]. Klíč s id: konverzace i profil ho berou ze dvou míst
 * (nabídka v liště a hostitel ořezu přes celou obrazovku) a musí dostat tutéž instanci.
 */
@Composable
internal fun chatActionsViewModel(chatId: Long): ChatActionsViewModel =
    koinViewModel(key = "chat-actions-$chatId", parameters = { parametersOf(chatId) })
