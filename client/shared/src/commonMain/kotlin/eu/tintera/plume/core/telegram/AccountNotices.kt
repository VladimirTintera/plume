package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Co Telegram účtu sděluje mimo chaty: oznámení od služby, nové podmínky služby a zmrazení
 * účtu. TDLib je posílá jen updatem, ve chvíli, kdy přijdou, a předepisuje je ukázat — oznámení
 * jako popup, podmínky jako blokující popup.
 *
 * **Drží se tu, ne v obrazovce**: update bez odběratele se ztratí (wrapper je drží v `SharedFlow`
 * bez paměti) a zmrazení i podmínky TDLib posílá hned po startu, dřív než se cokoli složí.
 */
interface AccountNotices {
    /** Oznámení čekající na zobrazení, nejstarší první. */
    val serviceNotices: StateFlow<List<ServiceNotice>>

    fun dismissServiceNotice(id: Long)

    /** Nové podmínky služby k přijetí, nebo `null`. */
    val termsOfService: StateFlow<TermsOfServiceUpdate?>

    suspend fun acceptTermsOfService(): Result<Unit>

    /**
     * Podmínky teď schovat. TDLib je pošle znovu po příštím startu — nepřijaté podmínky
     * nezmizí, jen nebudou bránit v práci, dokud se uživatel nerozhodne.
     */
    fun postponeTermsOfService()

    /** Zmrazený účet, nebo `null`. */
    val freeze: StateFlow<AccountFreeze?>

    /**
     * Doporučené akce (`updateSuggestedActions`), v pořadí, v jakém přišly — jen ty, které Plume
     * umí nabídnout ([SuggestedAction]). Nabídky Premium a hvězd se vynechávají.
     */
    val suggestedActions: StateFlow<List<SuggestedAction>>

    /** Schovat akci na serveru — neukáže se už nikde, ani v jiném klientovi. */
    suspend fun hideSuggestedAction(action: SuggestedAction): Result<Unit>

    /**
     * Kontakty, které měly nebo budou mít narozeniny (`updateContactCloseBirthdays`): včera, dnes
     * a zítra. Prázdné po [hideCloseBirthdays] na 24 hodin.
     */
    val closeBirthdays: StateFlow<List<CloseBirthday>> get() = NO_BIRTHDAYS

    /** Schovat narozeniny na 24 hodin, na serveru (`hideContactCloseBirthdays`). */
    suspend fun hideCloseBirthdays(): Result<Unit> = Result.success(Unit)
}

private val NO_BIRTHDAYS: StateFlow<List<CloseBirthday>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

/**
 * Doporučená akce od Telegramu, kterou Plume umí nabídnout. TDLib jich zná víc (Premium, hvězdy,
 * dárky, přístupové klíče, převod skupiny na kanál); ty se vynechávají — buď je Plume
 * neumí splnit, nebo by jen propagovaly placené služby.
 */
sealed interface SuggestedAction {
    /** „Pamatuješ si heslo dvoufázového ověření?" */
    data object CheckPassword : SuggestedAction

    /** „Je tohle pořád tvoje číslo?" Potvrdit, nebo změnit. */
    data object CheckPhoneNumber : SuggestedAction

    /** Nastavit heslo; bez něj se na nové zařízení přihlásí až za [authorizationDelaySeconds] (0 = bez čekání). */
    data class SetPassword(val authorizationDelaySeconds: Int) : SuggestedAction

    /**
     * Přidat přihlašovací e-mail. [canBeHidden] `false` znamená, že ho Telegram vyžaduje
     * (`SETUP_LOGIN_EMAIL_NOSKIP`) a karta nejde schovat.
     */
    data class SetLoginEmail(val canBeHidden: Boolean) : SuggestedAction

    data object SetProfilePhoto : SuggestedAction

    /** Přidat datum narození. */
    data object SetBirthdate : SuggestedAction

    /** Zapnout archivaci a ztlumení nových chatů od neznámých. */
    data object ArchiveUnknownChats : SuggestedAction

    /** Vlastní doporučení od Telegramu s odkazem. [name] je klíč pro schování. */
    data class Custom(
        val name: String,
        val title: String,
        val description: String,
        val url: String,
    ) : SuggestedAction
}

/** Oznámení od Telegramu (`updateServiceNotification`) — ukazuje se jako popup. */
data class ServiceNotice(
    /** Naše pořadové číslo, jen pro zavření; TDLib oznámení žádné id nedává. */
    val id: Long,
    val text: String,
    val entities: List<MessageEntity>,
    /**
     * Klíč relace je neplatný (`AUTH_KEY_DROP_…`): pod oznámením musí být „Zrušit" a „Odhlásit",
     * a odhlášení znamená zahodit lokální data (`destroy`), ne `logOut` — ten by s neplatným
     * klíčem neprošel.
     */
    val offersLogOut: Boolean,
)

/** Nové podmínky služby (`updateTermsOfService`). */
data class TermsOfServiceUpdate(
    val id: String,
    val text: String,
    val entities: List<MessageEntity>,
    /** Kolik let musí mít ten, kdo je přijímá; 0 = bez omezení. */
    val minUserAge: Int,
    /** Telegram chce blokující popup. Bez něj se podmínky jen přijmou, až na ně dojde. */
    val blocking: Boolean,
)

/** Zmrazený účet (`updateFreezeState`). Časy jsou Unix timestamp. */
data class AccountFreeze(
    val frozenAt: Int,
    /** Kdy bude účet smazán a rozmrazit už nepůjde; 0 = neznámé. */
    val deletionAt: Int,
    /** Kam poslat odvolání, nebo `null`. */
    val appealLink: String?,
)
