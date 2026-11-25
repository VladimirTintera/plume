package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.sendableTogether
import eu.tintera.plume.core.ui.PICK_LIMIT

/** Kolik příloh jde připravit nad polem najednou; víc než [ALBUM_LIMIT] odejde v několika albech. */
internal const val MAX_ATTACHMENTS = PICK_LIMIT

/** Strop jednoho alba v Telegramu (`sendMessageAlbum` bere 2–10 položek). */
internal const val ALBUM_LIMIT = 10

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

/**
 * Fotky a videa jdou do jednoho alba spolu; soubory jen se soubory. Skladba patří k souborům —
 * vybírá se jako soubor a nad polem stojí v jeho řádcích.
 */
private val OutgoingAttachment.isVisual: Boolean
    get() = this is OutgoingAttachment.Photo || this is OutgoingAttachment.Video

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
            is OutgoingAttachment.Document, is OutgoingAttachment.Audio -> attachment
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
): List<OutgoingAttachment> = picked.mapNotNull { attachment ->
    when {
        // Skladba, kterou chat nepřijme, odejde jako soubor — vybírala se jako soubor.
        attachment is OutgoingAttachment.Audio -> when {
            content.audios -> attachment
            content.documents -> attachment.asDocument()
            else -> null
        }
        attachment.goesAsDocument() -> attachment.takeIf { content.documents }
        attachment is OutgoingAttachment.Photo -> attachment.takeIf { content.photos }
        attachment is OutgoingAttachment.Video -> attachment.takeIf { content.videos }
        else -> attachment
    }
}

/**
 * Jak přílohy odejdou: po albech nejvýš [ALBUM_LIMIT] položek, nebo s [separately] každá
 * zvlášť jako samostatná zpráva („Poslat jednotlivě").
 *
 * Skladby mezi soubory se na soubory převedou za CELÝ výběr ([sendableTogether]), ne po albech:
 * jinak by z jednoho výběru odešlo album souborů a vedle něj album skladeb, podle toho, kam
 * dělení zrovna padlo.
 *
 * **Fotky a videa se soubory do jednoho alba nejdou** (TDLib: „Documents and audio files can be
 * only grouped in an album with messages of the same type"). Výběr se proto dělí na po sobě
 * jdoucí úseky téhož druhu ([AlbumKind]) a album vzniká z každého zvlášť; pořadí zpráv zůstává
 * pořadím výběru. Smíšený výběr vzniká snadno: video ze sponky „Soubor" vedle PDF, fotka z galerie
 * přidaná k souboru, nebo jedna fotka přepnutá na „Jako soubor".
 */
internal fun attachmentBatches(attachments: List<OutgoingAttachment>, separately: Boolean): List<List<OutgoingAttachment>> {
    val sendable = sendableTogether(attachments)
    if (separately) return sendable.map(::listOf)
    val runs = mutableListOf<MutableList<OutgoingAttachment>>()
    sendable.forEach { attachment ->
        val run = runs.lastOrNull()?.takeIf { it.first().albumKind() == attachment.albumKind() }
        if (run != null) run += attachment else runs += mutableListOf(attachment)
    }
    return runs.flatMap { it.chunked(ALBUM_LIMIT) }
}

/** Co smí být spolu v jednom albu: fotky s videi, soubory se soubory, skladby se skladbami. */
private enum class AlbumKind { Media, Documents, Audio }

private fun OutgoingAttachment.albumKind(): AlbumKind = when {
    this is OutgoingAttachment.Audio -> AlbumKind.Audio
    goesAsDocument() -> AlbumKind.Documents
    else -> AlbumKind.Media
}

/** Kolik příloh z výběru odešlo a proč se zbytek nepovedl (`null` = odešlo všechno). */
internal data class BatchSendResult(val sent: Int, val failure: Throwable?)

/**
 * Pošle přílohy po dávkách ([attachmentBatches]), jednu po druhé — pořadí zpráv v chatu je
 * pořadí výběru.
 *
 * **Popisek, odpověď a zmínky patří PRVNÍ dávce**, jako u alba první položce: tak Telegram
 * ukazuje popisek alba a odpověď na zprávu nemá smysl opakovat u každé desítky fotek.
 * Selhání dávky odesílání zastaví; volající ví z [BatchSendResult.sent], které přílohy už
 * odešly, a vrátí nad pole jen ty ostatní — jinak by je druhý pokus poslal podruhé.
 */
internal suspend fun ConversationRepository.sendInBatches(
    chatId: Long,
    attachments: List<OutgoingAttachment>,
    separately: Boolean,
    caption: String,
    replyToMessageId: Long?,
    quote: ReplyQuote?,
    options: SendOptions,
    mentions: List<NameMention> = emptyList(),
    formats: List<FormatSpan> = emptyList(),
    onSent: (List<ChatMessage>) -> Unit = {},
): BatchSendResult {
    var sent = 0
    attachmentBatches(attachments, separately).forEachIndexed { index, batch ->
        val first = index == 0
        sendAttachments(
            chatId = chatId,
            attachments = batch,
            caption = if (first) caption else "",
            replyToMessageId = replyToMessageId.takeIf { first },
            quote = quote.takeIf { first },
            options = options,
            mentions = if (first) mentions else emptyList(),
            formats = if (first) formats else emptyList(),
        )
            .onSuccess(onSent)
            .onFailure { return BatchSendResult(sent, it) }
        sent += batch.size
    }
    return BatchSendResult(sent, failure = null)
}
