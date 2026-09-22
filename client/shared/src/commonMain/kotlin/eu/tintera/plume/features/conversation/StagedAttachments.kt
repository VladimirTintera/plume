package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.ContentAccess
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
 * Pravidla alba: **fotky s videi a soubory se nemíchají** (Telegram skládá do alba dokumenty jen s dokumenty).
 * Výběr jiného druhu proto připravené přílohy **nahradí** — uživatel si právě vybral něco jiného.
 * Stejný druh se přidá na konec, nejvýš do [MAX_ATTACHMENTS]; co se nevejde, zahodí se a ohlásí.
 */
internal fun stageAttachments(
    current: List<OutgoingAttachment>,
    picked: List<OutgoingAttachment>,
): StagedAttachments {
    if (picked.isEmpty()) return StagedAttachments(current, overflow = false)

    val sameKind = current.isNotEmpty() && current.first().isVisual == picked.first().isVisual
    // Výběr sám může obsahovat obojí jen teoreticky (výběry jsou oddělené); bere se druh první.
    val kind = picked.first().isVisual
    val combined = (if (sameKind) current else emptyList()) + picked.filter { it.isVisual == kind }

    return StagedAttachments(
        attachments = combined.take(MAX_ATTACHMENTS),
        overflow = combined.size > MAX_ATTACHMENTS,
    )
}

/** Fotky a videa jdou do jednoho alba spolu; soubory jen se soubory. */
private val OutgoingAttachment.isVisual: Boolean get() = this !is OutgoingAttachment.Document

/**
 * Smí se u přílohy nabídnout jednorázové zhlédnutí nebo časovač.
 *
 * **Jen v soukromém chatu** — TDLib jinde odpoví „Messages can self-destruct only in private
 * chats". Uložené zprávy jsou technicky soukromý chat, ale není tam komu obsah ukázat, takže
 * se nenabízí ani tam. **A jen u jedné přílohy**: mizející médium se otevírá samo, bez
 * listování (viz prohlížeč médií), takže do alba nepatří.
 */
internal fun selfDestructAllowed(isPrivateChat: Boolean, isSavedMessages: Boolean, attachmentCount: Int): Boolean =
    isPrivateChat && !isSavedMessages && attachmentCount == 1

/**
 * Přílohy, u kterých mizení není dovolené, přepne na běžné odeslání.
 *
 * Volá se po každé změně připravených příloh: kdo k mizející fotce přidá druhou, dostane album
 * — a mizení z něj musí zmizet VIDITELNĚ nad polem, ne tiše až při odeslání. Vrací `null`,
 * když není co přepínat; jinak nový seznam, a změna se má ohlásit.
 */
internal fun dropForbiddenSelfDestruct(
    attachments: List<OutgoingAttachment>,
    allowed: Boolean,
): List<OutgoingAttachment>? {
    if (allowed || attachments.none { it.selfDestructType != null }) return null
    return attachments.map { attachment ->
        if (attachment.selfDestructType == null) return@map attachment
        when (attachment) {
            is OutgoingAttachment.Photo -> attachment.copy(mode = AttachmentMode.Standard)
            is OutgoingAttachment.Video -> attachment.copy(mode = AttachmentMode.Standard)
            is OutgoingAttachment.Document -> attachment
        }
    }
}

/**
 * Jako co příloha doopravdy odejde.
 *
 * Režim „Odeslat jako soubor" posílá fotku i video přes `InputMessageDocument`
 * (`toInputContent`), takže se řídí právem na SOUBORY, ne na fotky. Kdo to plete, dostane
 * chybu od serveru u přílohy, kterou aplikace nabídla.
 */
private fun OutgoingAttachment.goesAsDocument(): Boolean =
    mode == AttachmentMode.AsFile || this is OutgoingAttachment.Document

/**
 * Vyřadí přílohy, které chat nepřijme.
 *
 * Systémový výběr fotek vydá fotky i videa jedním tahem, takže ve skupině, která povoluje jen
 * fotky, se mezi vybranými objeví i video — a to by skončilo chybou AŽ PO odeslání. Volající
 * porovná velikost seznamu a rozdíl ohlásí.
 */
internal fun allowedByContent(
    picked: List<OutgoingAttachment>,
    content: ContentAccess,
): List<OutgoingAttachment> = picked.filter { attachment ->
    when {
        attachment.goesAsDocument() -> content.documents
        attachment is OutgoingAttachment.Photo -> content.photos
        attachment is OutgoingAttachment.Video -> content.videos
        else -> true
    }
}
