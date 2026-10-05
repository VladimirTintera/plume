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
    /** Přepis nabízet nemá smysl — účet bez Premium a bez zkušebních přepisů. */
    data object Hidden : TranscribeOffer

    data object Available : TranscribeOffer

    /** Zkušební přepisy na tento týden došly; [resetsAt] je unixový čas obnovení, nebo 0. */
    data class UsedUp(val resetsAt: Long) : TranscribeOffer

    /** Bez Premium jde přepsat jen záznam do [maxSeconds]. */
    data class TooLong(val maxSeconds: Int) : TranscribeOffer
}

/**
 * Jestli a jak nabídnout přepis záznamu dlouhého [durationSeconds].
 *
 * S Premium vždycky. Bez něj rozhodují zkušební přepisy; když o nich ještě nic nevíme
 * ([trial] `null`), nabídne se — rozhodne server. Došlé přepisy a příliš dlouhý záznam tlačítko
 * nechají, ale ťuknutí vysvětlí proč ne: zmizelé tlačítko by neřeklo nic.
 */
fun transcribeOffer(isPremium: Boolean, trial: SpeechTrial?, durationSeconds: Int): TranscribeOffer = when {
    isPremium || trial == null -> TranscribeOffer.Available
    trial.weeklyCount <= 0 -> TranscribeOffer.Hidden
    trial.maxMediaDurationSeconds in 1..<durationSeconds -> TranscribeOffer.TooLong(trial.maxMediaDurationSeconds)
    trial.leftCount <= 0 -> TranscribeOffer.UsedUp(trial.nextResetDate)
    else -> TranscribeOffer.Available
}
