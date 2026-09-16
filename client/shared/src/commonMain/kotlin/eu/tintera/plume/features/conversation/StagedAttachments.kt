package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.OutgoingAttachment

/** Kolik příloh jde poslat najednou — strop alba v Telegramu. */
internal const val MAX_ATTACHMENTS = 10

/** Výsledek přidání příloh k připraveným: nový seznam a jestli se něco nevešlo pod strop. */
internal data class StagedAttachments(
    val attachments: List<OutgoingAttachment>,
    val overflow: Boolean,
)

/**
 * Přidá nově vybrané přílohy k těm, které čekají nad vstupním polem.
 *
 * Pravidla alba: **fotky a soubory se nemíchají** (Telegram skládá do alba dokumenty jen s dokumenty).
 * Výběr jiného druhu proto připravené přílohy **nahradí** — uživatel si právě vybral něco jiného.
 * Stejný druh se přidá na konec, nejvýš do [MAX_ATTACHMENTS]; co se nevejde, zahodí se a ohlásí.
 */
internal fun stageAttachments(
    current: List<OutgoingAttachment>,
    picked: List<OutgoingAttachment>,
): StagedAttachments {
    if (picked.isEmpty()) return StagedAttachments(current, overflow = false)

    val sameKind = current.isNotEmpty() && current.first().isPhoto == picked.first().isPhoto
    // Výběr sám může obsahovat obojí jen teoreticky (výběry jsou oddělené); bere se druh první.
    val kind = picked.first().isPhoto
    val combined = (if (sameKind) current else emptyList()) + picked.filter { it.isPhoto == kind }

    return StagedAttachments(
        attachments = combined.take(MAX_ATTACHMENTS),
        overflow = combined.size > MAX_ATTACHMENTS,
    )
}

private val OutgoingAttachment.isPhoto: Boolean get() = this is OutgoingAttachment.Photo
