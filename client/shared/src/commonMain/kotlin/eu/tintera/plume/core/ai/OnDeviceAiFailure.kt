package eu.tintera.plume.core.ai

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.ai_failure_busy
import eu.tintera.plume.resources.ai_failure_empty
import eu.tintera.plume.resources.ai_failure_gemma
import eu.tintera.plume.resources.ai_failure_nano
import eu.tintera.plume.resources.ai_failure_nano_code
import eu.tintera.plume.resources.ai_failure_needs_update
import eu.tintera.plume.resources.ai_failure_no_space
import eu.tintera.plume.resources.ai_failure_too_large
import eu.tintera.plume.resources.ai_failure_unavailable

/**
 * Proč generování selhalo, převedené z chyby runtime na věc, se kterou uživatel umí něco dělat.
 *
 * Dřív se každé selhání hlásilo jednou větou „Shrnutí se nepovedlo" a z obrazovky ani z logu se
 * nedalo poznat, jestli je model vytížený, nebo rozbitý v systému. Na Pixelu 11 Pro Foldu po
 * aktualizaci AICore přestal Gemini Nano nacházet vlastní soubory (`config.binarypb …
 * NOT_FOUND`, ML Kit kód 5) a každé shrnutí den a půl tiše selhávalo.
 */
enum class AiFailureReason {
    /** Model je vytížený nebo došla kvóta baterie — stačí počkat. */
    Busy,

    /** Vstup je na model moc dlouhý. */
    TooLarge,

    /** Na model není v telefonu místo. */
    NotEnoughSpace,

    /** Systém (AICore) je na model moc starý. */
    NeedsUpdate,

    /** Model teď není k dispozici, i když status tvrdil opak. */
    Unavailable,

    /** Model selhal sám od sebe — typicky poškozený model v systému. Tohle patří do Crashlytics. */
    ModelError,
}

/**
 * Selhání generování s důvodem. Zpráva výjimky je anglická a bez textu promptu: jde do logu
 * a tím do Crashlytics, kde podle ní má být poznat, co se stalo („GeminiNano failed: ModelError
 * (code 5)"), a nesmí v ní být obsah konverzace.
 *
 * @property code chybový kód runtime (ML Kit `GenAiException.errorCode`), když nějaký je.
 */
class OnDeviceAiException(
    val reason: AiFailureReason,
    val provider: AiProvider,
    val code: Int? = null,
    cause: Throwable? = null,
) : Exception("${provider.name} failed: $reason" + (code?.let { " (code $it)" } ?: ""), cause)

/**
 * Věta pro uživatele. U poškozeného modelu říká, KDE se to spravuje — tam je chyba mimo
 * aplikaci a „zkusit znovu" samo nepomůže.
 */
fun OnDeviceAiException.userMessage(): UiText = when (reason) {
    AiFailureReason.Busy -> UiText(Res.string.ai_failure_busy)
    AiFailureReason.TooLarge -> UiText(Res.string.ai_failure_too_large)
    AiFailureReason.NotEnoughSpace -> UiText(Res.string.ai_failure_no_space)
    AiFailureReason.NeedsUpdate -> UiText(Res.string.ai_failure_needs_update)
    AiFailureReason.Unavailable -> UiText(Res.string.ai_failure_unavailable)
    AiFailureReason.ModelError -> when (provider) {
        AiProvider.GeminiNano -> code
            ?.let { UiText(Res.string.ai_failure_nano_code, it.toString()) }
            ?: UiText(Res.string.ai_failure_nano)
        AiProvider.Gemma -> UiText(Res.string.ai_failure_gemma)
    }
}

/** Model doběhl, ale nenapsal nic. */
val emptyOutputMessage: UiText get() = UiText(Res.string.ai_failure_empty)

/**
 * Patří selhání do Crashlytics jako samostatný problém? Jen to, co ukazuje na rozbitý model
 * nebo systém. Vytížený model, dlouhý vstup a plný disk jsou očekávané a jako problémy by
 * zaplevelily konzoli (viz pravidlo `w` / `e` v „Hlášení pádů").
 */
val OnDeviceAiException.isReportable: Boolean
    get() = reason == AiFailureReason.ModelError ||
        reason == AiFailureReason.NeedsUpdate ||
        reason == AiFailureReason.Unavailable
