package eu.tintera.plume.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.Birthdate
import eu.tintera.plume.core.telegram.EditableProfile
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.telegram.usernameFormatError
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.core.ui.VideoCropControl
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.edit_profile_load_failed
import eu.tintera.plume.resources.edit_profile_not_photo
import eu.tintera.plume.resources.edit_profile_photo_failed
import eu.tintera.plume.resources.edit_profile_save_failed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.USERNAME_CHECK_DELAY_MS
import eu.tintera.plume.core.ui.UsernameStatus

/**
 * Úprava vlastního profilu: jméno, uživatelské jméno, bio, datum narození a fotka.
 *
 * **Text polí je Compose stav přímo ve ViewModelu**, ne `StateFlow`: hodnota vrácená přes tok
 * o dispatch později poskakuje kurzorem (viz „Hledání v konverzaci"). Pole tak píšou i čtou
 * tentýž stav synchronně.
 *
 * **Fotka se ukládá hned, texty až tlačítkem Uložit.** Fotka je samostatná akce s nahráváním
 * a jejím vlastním průběhem; kdyby čekala na Uložit, zmizela by s odchodem z obrazovky.
 */
@OptIn(FlowPreview::class)
class EditProfileViewModel(
    private val account: AccountRepository,
    private val photoCrop: ProfilePhotoCrop,
) : ViewModel() {

    private val logger = Logger.withTag("EditProfile")

    var original by mutableStateOf<EditableProfile?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var username by mutableStateOf("")
    var bio by mutableStateOf("")
    /**
     * Datum narození z kalendáře, i s rokem; `null` = neuvedené. Ukládá se s texty tlačítkem
     * Uložit, rok jen s [showBirthYear] — viz [birthdateToSave].
     */
    var birthdate by mutableStateOf<Birthdate?>(null)
    /** „Ukazovat rok". Rok se při vypnutí jen schová, ne zapomene — zapnutí ho vrátí. */
    var showBirthYear by mutableStateOf(true)

    /** Co se uloží: bez roku, když ho uživatel ukazovat nechce (nebo ho datum nemá). */
    val birthdateToSave: Birthdate?
        get() = birthdate?.let { if (showBirthYear) it else it.copy(year = null) }

    var usernameStatus by mutableStateOf<UsernameStatus>(UsernameStatus.Idle)
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
            account.editableProfile()
                .onSuccess { profile ->
                    original = profile
                    firstName = profile.firstName
                    lastName = profile.lastName
                    username = profile.username
                    bio = profile.bio
                    birthdate = profile.birthdate
                    // Datum bez roku: rok ukazovat nechtěl. Bez data: nové datum rok ukazuje.
                    showBirthYear = profile.birthdate?.year != null || profile.birthdate == null
                }
                .onFailure { failure ->
                    loadFailed = true
                    notice = failure.reported(Res.string.edit_profile_load_failed)
                }
        }
        // Dostupnost jména se ptá serveru až po pauze v psaní a jen nad jménem, které projde
        // lokálními pravidly — dotaz za každý znak by byl zbytečný. Rozepsaný dotaz se zruší.
        viewModelScope.launch {
            snapshotFlow { username.trim() }
                .distinctUntilChanged()
                .collectLatest { name ->
                    val unchanged = name.equals(original?.username.orEmpty(), ignoreCase = true)
                    if (name.isEmpty() || unchanged || usernameFormatError(name) != null) {
                        usernameStatus = UsernameStatus.Idle
                        return@collectLatest
                    }
                    usernameStatus = UsernameStatus.Checking
                    delay(USERNAME_CHECK_DELAY_MS)
                    usernameStatus = account.checkUsername(name).fold(
                        onSuccess = { if (it == UsernameCheck.Available) UsernameStatus.Available else UsernameStatus.Unavailable(it) },
                        // Nezjištěná dostupnost ukládání neblokuje — rozhodne server při uložení.
                        onFailure = { UsernameStatus.Idle },
                    )
                }
        }
    }

    val changed: Boolean
        get() {
            val profile = original ?: return false
            return firstName.trim() != profile.firstName || lastName.trim() != profile.lastName ||
                username.trim() != profile.username || bio.trim() != profile.bio ||
                birthdateToSave != profile.birthdate
        }

    val canSave: Boolean
        get() {
            val profile = original ?: return false
            val name = username.trim()
            val usernameOk = name == profile.username ||
                (usernameFormatError(name) == null && usernameStatus !is UsernameStatus.Unavailable && usernameStatus != UsernameStatus.Checking)
            return changed && !saving && firstName.isNotBlank() && usernameOk && bio.trim().length <= profile.bioMaxLength
        }

    /**
     * Uloží, co se změnilo — jméno, uživatelské jméno, bio a datum narození jsou čtyři různá volání. Když jedno
     * selže, další se neposílají a obrazovka zůstane otevřená s hláškou; co už prošlo, se
     * zapíše do [original], takže další pokus to neposílá znovu.
     */
    fun save() {
        val profile = original ?: return
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            var current = profile
            val first = firstName.trim()
            val last = lastName.trim()
            val name = username.trim()
            val about = bio.trim()
            val result = runCatching {
                if (first != current.firstName || last != current.lastName) {
                    account.setName(first, last).getOrThrow()
                    current = current.copy(firstName = first, lastName = last)
                }
                if (name != current.username) {
                    account.setUsername(name).getOrThrow()
                    current = current.copy(username = name)
                }
                if (about != current.bio) {
                    account.setBio(about).getOrThrow()
                    current = current.copy(bio = about)
                }
                val date = birthdateToSave
                if (date != current.birthdate) {
                    account.setBirthdate(date).getOrThrow()
                    current = current.copy(birthdate = date)
                }
            }
            original = current
            saving = false
            result
                .onSuccess { saved = true }
                .onFailure { notice = it.reported(Res.string.edit_profile_save_failed) }
        }
    }

    /** Obrázek čekající na ořez ([PhotoCropOverlay]); `null` = ořez zavřený. */
    val cropping: String? get() = photoCrop.image

    /** V ořezu je snímek videa — ořez řekne, že se zkrátí a přijde o zvuk. */
    val croppingVideo: VideoCropControl? get() = photoCrop.video

    /**
     * Z výběru příloh; bere se první fotka nebo video (animovaná profilovka). Napřed jde do
     * ořezu — nahraje se až s vybraným výřezem ([cropPhoto]).
     */
    fun setPhoto(attachments: List<OutgoingAttachment>) {
        photoBusy = true
        viewModelScope.launch {
            runCatching { photoCrop.open(attachments) }
                .onSuccess { opened -> if (!opened) notice = UiText(Res.string.edit_profile_not_photo) }
                .onFailure { notice = it.reported(Res.string.edit_profile_photo_failed) }
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
                .mapCatching { prepared -> prepared?.let { account.setPhoto(it.file).getOrThrow() } }
                .onSuccess { reloadPhoto() }
                .onFailure { notice = it.reported(Res.string.edit_profile_photo_failed) }
            photoBusy = false
        }
    }

    fun deletePhoto() {
        photoBusy = true
        viewModelScope.launch {
            account.deletePhoto()
                .onSuccess { reloadPhoto() }
                .onFailure { notice = it.reported(Res.string.edit_profile_photo_failed) }
            photoBusy = false
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
        account.editableProfile().onSuccess { fresh ->
            original = original?.copy(photoFileId = fresh.photoFileId, photoThumbnailFileId = fresh.photoThumbnailFileId)
        }
    }

    private fun Throwable.reported(message: org.jetbrains.compose.resources.StringResource): UiText {
        logger.w(this) { message.key }
        return userMessage(message)
    }
}
