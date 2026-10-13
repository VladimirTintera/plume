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
