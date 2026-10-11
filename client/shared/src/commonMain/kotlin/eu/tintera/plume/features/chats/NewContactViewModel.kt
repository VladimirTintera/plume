package eu.tintera.plume.features.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatCreation
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.PhoneEntry
import eu.tintera.plume.core.ui.PhoneInput
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.new_contact_failed
import eu.tintera.plume.resources.new_contact_not_found
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Nový kontakt podle telefonního čísla: jméno, příjmení a číslo s výběrem země jako
 * u přihlášení ([PhoneInput]). Po přidání se otevře konverzace s ním.
 *
 * Číslo, na kterém nikoho najít nejde, je **věta pod polem, ne chyba**: buď tam účet není,
 * nebo jeho soukromí hledání podle čísla nedovoluje, a server to schválně nerozliší.
 */
class NewContactViewModel(
    private val creation: ChatCreation,
    session: TelegramSession,
) : ViewModel() {

    private val logger = Logger.withTag("NewContact")

    private val phoneInput = PhoneInput(session, viewModelScope)

    /** Text polí je Compose stav, ne `StateFlow` — viz [PhoneInput]. */
    var firstName by mutableStateOf("")
        private set
    var lastName by mutableStateOf("")
        private set

    val phone: PhoneEntry get() = phoneInput.phone
    val countries: StateFlow<List<Country>> = phoneInput.countries

    var adding by mutableStateOf(false)
        private set

    /** Proč přidání neprošlo; pod polem čísla, mizí s další úpravou. */
    var error by mutableStateOf<UiText?>(null)
        private set

    /** Přidáno — obrazovka otevře konverzaci s tímhle chatem. */
    var added by mutableStateOf<Long?>(null)
        private set

    /** Křestní jméno TDLib chce neprázdné; příjmení je nepovinné. */
    val canAdd: Boolean get() = firstName.isNotBlank() && phone.isComplete && !adding

    init {
        viewModelScope.launch { phoneInput.loadCountries() }
    }

    fun onFirstNameChange(value: String) {
        firstName = value.take(NAME_MAX_LENGTH)
        error = null
    }

    fun onLastNameChange(value: String) {
        lastName = value.take(NAME_MAX_LENGTH)
        error = null
    }

    fun onPhoneChange(value: String) {
        phoneInput.onPhoneChange(value)
        error = null
    }

    fun selectCountry(country: Country) {
        phoneInput.selectCountry(country)
        error = null
    }

    fun add() {
        if (!canAdd) return
        adding = true
        error = null
        viewModelScope.launch {
            creation.addContact(
                phoneNumber = phone.international,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
            )
                .onSuccess { chatId ->
                    if (chatId == null) error = UiText(Res.string.new_contact_not_found) else added = chatId
                }
                .onFailure { failure ->
                    logger.w(failure) { "Kontakt se nepodařilo přidat." }
                    error = failure.userMessage(Res.string.new_contact_failed)
                }
            adding = false
        }
    }

    private companion object {
        /** Strop Telegramu pro jméno i příjmení. */
        const val NAME_MAX_LENGTH = 64
    }
}
