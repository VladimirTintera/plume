package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.SpeechRecognitionResult
import dev.g000sha256.tdl.dto.SpeechRecognitionResultError
import dev.g000sha256.tdl.dto.SpeechRecognitionResultPending
import dev.g000sha256.tdl.dto.SpeechRecognitionResultText

/**
 * Přepis řeči hlasovky nebo kolečka do textu. Přepisuje server Telegramu (`recognizeSpeech`)
 * a výsledek nese obsah zprávy — přijde úpravou zprávy, jako každá jiná změna obsahu.
 */
sealed interface SpeechTranscript {
    /** Server ještě přepisuje; [partial] je dosavadní text, na začátku prázdný. */
    data class Pending(val partial: String) : SpeechTranscript

    data class Done(val text: String) : SpeechTranscript

    /** Přepis se nepovedl. Text chyby je od TDLib, uživateli se neukazuje — jen [reason]. */
    data class Failed(val reason: Reason) : SpeechTranscript {
        enum class Reason {
            /** Záznam je na přepis moc dlouhý; znovu to nepůjde. */
            TooLong,

            /** Účet bez Premium a bez zkušebních přepisů; znovu to nepůjde. */
            PremiumRequired,

            /** Cokoli jiného — jde to zkusit znovu. */
            Other,
        }

        val canRetry: Boolean get() = reason == Reason.Other
    }
}

private val logger = Logger.withTag("SpeechRecognition")

internal fun SpeechRecognitionResult.toTranscript(): SpeechTranscript = when (this) {
    is SpeechRecognitionResultPending -> SpeechTranscript.Pending(partialText)
    is SpeechRecognitionResultText -> SpeechTranscript.Done(text)
    is SpeechRecognitionResultError -> {
        logger.d { "Přepis se nepovedl: ${error.code} ${error.message}" }
        SpeechTranscript.Failed(
            when (error.message) {
                TOO_LONG -> SpeechTranscript.Failed.Reason.TooLong
                PREMIUM_REQUIRED -> SpeechTranscript.Failed.Reason.PremiumRequired
                else -> SpeechTranscript.Failed.Reason.Other
            },
        )
    }
}

/** Konstanta od serveru pro hlasovku delší, než umí přepsat. */
private const val TOO_LONG = "MSG_VOICE_TOO_LONG"

/** Účet bez Premium, kterému zkušební přepisy nezbyly (nebo je nemá vůbec). */
private const val PREMIUM_REQUIRED = "PREMIUM_ACCOUNT_REQUIRED"

/**
 * Zkušební přepisy pro účet bez Premium (`UpdateSpeechRecognitionTrial`): kolik jich je za
 * týden, kolik zbývá, do jaké délky a kdy se počet obnoví. Server je pošle po startu a po
 * každém využitém přepisu.
 */
data class SpeechTrial(
    val maxMediaDurationSeconds: Int,
    val weeklyCount: Int,
    val leftCount: Int,
    /** Unixový čas obnovení; 0, dokud se žádný zkušební přepis nevyužil. */
    val nextResetDate: Long,
)

/** Co se stane po ťuknutí na „Přepsat". */
sealed interface TranscribeOffer {
    /** Přepis tu nemá kontext (náhled, obrazovka bez konverzace); [transcribeOffer] ho nevrací. */
    data object Hidden : TranscribeOffer

    data object Available : TranscribeOffer

    /**
     * Server přepis nedá (bez Premium a zkušebních přepisů), ale umí ho model v telefonu
     * (`OnDeviceAi.transcribe`). Ťuknutí přepíše v zařízení; nic se neposílá.
     */
    data object OnDevice : TranscribeOffer

    /** Zkušební přepisy na tento týden došly; [resetsAt] je unixový čas obnovení, nebo 0. */
    data class UsedUp(val resetsAt: Long) : TranscribeOffer

    /** Bez Premium jde přepsat jen záznam do [maxSeconds]. */
    data class TooLong(val maxSeconds: Int) : TranscribeOffer

    /** Server přepis nedá a v telefonu jde přepsat jen záznam do [maxSeconds]. */
    data class TooLongOnDevice(val maxSeconds: Int) : TranscribeOffer

    /**
     * Přepis nedá server (bez Premium a zkušebních přepisů) ani telefon. [fix] říká, co v telefonu
     * pomůže; `null`, když nic (zařízení Gemmu neunese) — pak jen Premium.
     */
    data class Unavailable(val fix: OnDeviceFix?) : TranscribeOffer
}

/**
 * Co v telefonu zapnout, aby přepisoval sám: jazyk pro systémový rozpoznávač (desítky MB), nebo
 * Gemmu (gigabajty) — Gemini Nano přes ML Kit zvuk nebere.
 */
enum class OnDeviceFix {
    /** Systémový rozpoznávač jazyk umí, ale stažený není; stáhne ho tlačítko v bublině. */
    DownloadSpeechLanguage,

    /** Systém jazyk pro rozpoznávač právě stahuje. */
    SpeechLanguageDownloading,

    /** AI v zařízení je vypnutá. */
    EnableAi,

    /** Zvolená je Gemini Nano. */
    SwitchToGemma,

    /** Zvolená je Gemma, ale není stažená. */
    DownloadGemma,
}

/**
 * Jestli a jak nabídnout přepis záznamu dlouhého [durationSeconds].
 *
 * S Premium vždycky. Bez něj rozhodují zkušební přepisy; když o nich ještě nic nevíme
 * ([trial] `null`), nabídne se — rozhodne server. Kde přepis nejde, tlačítko zůstane a ťuknutí
 * vysvětlí proč ne: zmizelé tlačítko by neřeklo nic.
 */
fun transcribeOffer(
    isPremium: Boolean,
    trial: SpeechTrial?,
    durationSeconds: Int,
    /** Umí přepis model v telefonu ([TranscribeOffer.OnDevice])? */
    onDevice: Boolean = false,
    /** Co by přepis v telefonu umožnilo, když [onDevice] není. */
    onDeviceFix: OnDeviceFix? = null,
): TranscribeOffer {
    val server = when {
        isPremium || trial == null -> TranscribeOffer.Available
        trial.weeklyCount <= 0 -> null
        trial.maxMediaDurationSeconds in 1..<durationSeconds -> TranscribeOffer.TooLong(trial.maxMediaDurationSeconds)
        trial.leftCount <= 0 -> TranscribeOffer.UsedUp(trial.nextResetDate)
        else -> TranscribeOffer.Available
    }
    if (server == TranscribeOffer.Available) return server
    // Server má přednost (přepisuje líp a rychleji); telefon jen tam, kde server odmítne.
    // Delší záznam by se v telefonu přepisoval minuty — nad [MAX_ON_DEVICE_SECONDS] se nenabízí.
    if (onDevice && durationSeconds <= MAX_ON_DEVICE_SECONDS) return TranscribeOffer.OnDevice
    return when {
        server != null -> server
        onDevice -> TranscribeOffer.TooLongOnDevice(MAX_ON_DEVICE_SECONDS)
        else -> TranscribeOffer.Unavailable(onDeviceFix)
    }
}

/** Nejdelší záznam, který se přepisuje v telefonu (po 30s kusech; na CPU zhruba stejně dlouho, co trvá). */
const val MAX_ON_DEVICE_SECONDS = 300
