package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Vlastní účet: kdo je přihlášený (živě) a úprava profilu.
 *
 * Hranice kolem TDLib jako ostatní repository. [account] se mění s každou změnou vlastního
 * uživatele (`UpdateUser`) — i z jiného zařízení —, takže karta účtu v Nastavení a fotka ve
 * vyhledávací pilulce po úpravě nezůstanou staré.
 */
interface AccountRepository {

    /** Přihlášený účet; `null`, dokud se nenačte. */
    val account: Flow<AccountInfo?>

    /** Profil k úpravě — navíc bio a strop jeho délky, které [AccountInfo] nenese. */
    suspend fun editableProfile(): Result<EditableProfile>

    suspend fun setName(firstName: String, lastName: String): Result<Unit>

    /** Prázdné = uživatelské jméno odebrat. */
    suspend fun setUsername(username: String): Result<Unit>

    /** Dá se jméno použít? Dotaz na server — volá se až nad jménem, které projde [usernameFormatError]. */
    suspend fun checkUsername(username: String): Result<UsernameCheck>

    suspend fun setBio(bio: String): Result<Unit>

    /** Nová profilová fotka z připraveného souboru (čtvercový JPEG, nebo krátké čtvercové video). */
    suspend fun setPhoto(photo: ProfilePhotoFile): Result<Unit>

    /** Odebere současnou fotku; ukáže se ta předchozí, pokud nějaká je. */
    suspend fun deletePhoto(): Result<Unit>

    /**
     * Odkaz, přes který si mě druhý najde a napíše mi (QR kód „Můj kontakt"). Server ho při
     * každém volání může vydat nový; viz [ContactLink].
     */
    /**
     * Pošle kód na NOVÉ číslo (`sendPhoneNumberCode` s `PhoneNumberCodeTypeChange`). Číslo se
     * změní až po [checkPhoneChangeCode]; účet i všechna data v cloudu zůstávají, mění se jen
     * číslo, na které se přihlašuje.
     */
    suspend fun sendPhoneChangeCode(phoneNumber: String): Result<PhoneChangeCode> =
        Result.failure(UnsupportedOperationException())

    /** Kód jinou cestou (SMS, hovor) — jen s [PhoneChangeCode.next] a po uplynutí lhůty. */
    suspend fun resendPhoneChangeCode(): Result<PhoneChangeCode> = Result.failure(UnsupportedOperationException())

    /** Kód z nového čísla — tím se číslo účtu změní. */
    suspend fun checkPhoneChangeCode(code: String): Result<Unit> = Result.failure(UnsupportedOperationException())

    suspend fun contactLink(): Result<ContactLink>
}

/**
 * Odkaz na vlastní účet (`getUserLink`).
 *
 * - S veřejným uživatelským jménem je to `t.me/jméno` a platí napořád ([expiresInSeconds] `0`).
 * - Bez něj dočasný kontaktní odkaz `t.me/contact/…` s tokenem. Kdo ho otevře, najde účet
 *   (`searchUserByToken`), i když číslo skrývá — proto platí jen chvíli a pak se vyrábí nový.
 */
data class ContactLink(val url: String, val expiresInSeconds: Int)

data class EditableProfile(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String,
    val bio: String,
    /** Strop délky bia od serveru (`bio_length_max`); Premium ho zvedá. */
    val bioMaxLength: Int,
    /** `fileId` velké varianty fotky, nebo `null` bez fotky. */
    val photoFileId: Int?,
    val photoThumbnailFileId: Int?,
)

enum class UsernameCheck { Available, Invalid, Occupied, Purchasable, TooManyPublic }

/**
 * Proč jméno nejde, podle pravidel Telegramu — nebo `null`, když projde a dá se ověřit na
 * serveru. Prázdné jméno je v pořádku: znamená odebrat ho.
 *
 * Kontroluje se lokálně napřed, ať se na server neposílá dotaz za každý znak, který stejně
 * neprojde: 5–32 znaků, jen písmena bez diakritiky, číslice a podtržítko, na začátku písmeno,
 * na konci ne podtržítko.
 */
fun usernameFormatError(username: String): UsernameFormatError? = when {
    username.isEmpty() -> null
    username.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_') } -> UsernameFormatError.Characters
    username.first() !in 'a'..'z' && username.first() !in 'A'..'Z' -> UsernameFormatError.Start
    username.length < USERNAME_MIN_LENGTH -> UsernameFormatError.TooShort
    username.length > USERNAME_MAX_LENGTH -> UsernameFormatError.TooLong
    username.last() == '_' -> UsernameFormatError.End
    else -> null
}

enum class UsernameFormatError { Characters, Start, TooShort, TooLong, End }

const val USERNAME_MIN_LENGTH = 5
const val USERNAME_MAX_LENGTH = 32

/** Strop délky jména i příjmení, jak ho vynucuje server. */
const val NAME_MAX_LENGTH = 64

/**
 * Kód odeslaný na telefonní číslo: jakou má podobu ([code]), kudy půjde, když si ho uživatel
 * nechá poslat znovu ([next], `null` = jinou cestou nejde), a za kolik sekund to smí
 * ([resendTimeout], počítáno od příchodu).
 */
data class PhoneChangeCode(
    val code: AuthCode,
    val next: CodeDelivery?,
    val resendTimeout: Int,
)

