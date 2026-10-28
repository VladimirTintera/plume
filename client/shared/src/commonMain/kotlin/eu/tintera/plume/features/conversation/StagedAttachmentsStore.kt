package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Přílohy nad vstupním polem, které čekají, až se uživatel do chatu vrátí.
 *
 * Text pole přežije odchod z chatu jako koncept v TDLib (`setChatDraftMessage`), přílohy ale
 * žily jen ve stavu ViewModelu a s ním zmizely: kdo si k fotce psal popisek a mezitím odskočil
 * do jiného chatu, našel po návratu popisek bez fotky. Koncept s přílohou Telegram nemá, takže
 * je drží tohle úložiště — jedno na proces (Koin `single`), po kusech pro každé místo psaní
 * ([StagedAttachmentsKey]).
 *
 * - **Jen v paměti procesu.** Zabití procesu přílohy nepřežijí; kopie souborů v `cache/outgoing`
 *   po nich zůstanou, stejně jako dřív, a uklidí je systém s cache.
 * - **Vyzvednutím záznam zmizí** ([take]): přílohy od té chvíle drží konverzace, a při dalším
 *   odchodu je vrátí ([put]) v podobě, v jaké je nechala — i prázdné, to záznam smaže.
 * - **Záznamy úprav fotek jdou s nimi** ([StagedAttachmentsEntry.photoEdits]): bez nich by
 *   vestavěná galerie upravenou fotku nepoznala jako vybranou a šlo by ji vybrat podruhé.
 */
class StagedAttachmentsStore {

    private val entries = MutableStateFlow<Map<StagedAttachmentsKey, StagedAttachmentsEntry>>(emptyMap())

    /** Vyzvedne přílohy čekající u [key]; `null`, když tam nic nečeká. */
    fun take(key: StagedAttachmentsKey): StagedAttachmentsEntry? =
        entries.getAndUpdate { it - key }[key]

    /** Uloží přílohy pro [key]; prázdné záznam smažou. */
    fun put(key: StagedAttachmentsKey, entry: StagedAttachmentsEntry) {
        entries.update { if (entry.attachments.isEmpty()) it - key else it + (key to entry) }
    }
}

/**
 * Místo psaní: chat, a v něm případně vlákno komentářů nebo téma. Přílohy připravené v tématu
 * nesmí vyskočit v celé skupině ani v jiném tématu — odešly by jinam, než je uživatel chystal.
 */
data class StagedAttachmentsKey(
    val chatId: Long,
    val threadRootId: Long? = null,
    val forumTopicId: Int? = null,
    val savedTopicId: Long? = null,
    val directTopicId: Long? = null,
)

/**
 * Co se s přílohami ukládá: ony samy (i s režimem spoileru a mizení), záznamy úprav fotek
 * a volba „Poslat jednotlivě".
 */
data class StagedAttachmentsEntry(
    val attachments: List<OutgoingAttachment>,
    val photoEdits: Map<String, PhotoEdit> = emptyMap(),
    val separately: Boolean = false,
)
