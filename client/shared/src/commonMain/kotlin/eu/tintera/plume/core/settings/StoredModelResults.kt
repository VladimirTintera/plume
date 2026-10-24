package eu.tintera.plume.core.settings

import kotlinx.serialization.Serializable

// Výsledky modelu v telefonu, které AppSettings ukládá, aby se po restartu nepočítaly znovu.
// Ukládají se jako holý JSON seznam bez jména třídy, takže balíček jde měnit bez migrace.

/** Hotový přepis z modelu v telefonu, uložený v [AppSettings]. [accountId] drží účty od sebe. */
@Serializable
data class StoredTranscript(
    val accountId: Long,
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val savedAt: Long,
)

/** Hotový popis fotky z modelu v telefonu, uložený v [AppSettings]. [accountId] drží účty od sebe. */
@Serializable
data class StoredPhotoDescription(
    val accountId: Long,
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val savedAt: Long,
)

/**
 * Uložený posudek triáže, aby se po restartu nepřečtené chaty neposuzovaly znovu.
 * [messageId] je zpráva, ke které posudek patří: u soukromého chatu poslední, u skupiny
 * nejnovější nepřečtená zmínka. [accountId] drží účty od sebe (id Uložených zpráv).
 */
@Serializable
data class StoredTriageVerdict(
    val accountId: Long,
    val chatId: Long,
    val messageId: Long,
    val waits: Boolean,
    val savedAt: Long,
)
