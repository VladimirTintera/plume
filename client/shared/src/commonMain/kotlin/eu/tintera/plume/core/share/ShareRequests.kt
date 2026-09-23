package eu.tintera.plume.core.share

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update

/**
 * Obsah sdílený do aplikace ze systému (nabídka „Sdílet" v jiné aplikaci) a kam s ním.
 *
 * **Stav, ne událost**, ze stejného důvodu jako `ChatOpenRequests`: sdílet jde i do aplikace,
 * která neběží nebo visí na přihlášení, a výběr chatu tedy nemusí mít kdo ukázat. Požadavek si
 * počká, než vznikne shell.
 *
 * Průběh: platforma ohlásí příchod ([start]) hned, soubory připravuje dál ([prepared]) — kopie
 * videa může trvat a výběr chatu se má ukázat okamžitě. Uživatel vybere chat ([choose])
 * a konverzace si obsah vyzvedne ([deliveries]), až je připravený.
 */
class ShareRequests {

    private val current = MutableStateFlow<SharedContent?>(null)

    /** Sdílený obsah, se kterým se ještě nic nestalo; `null`, když žádný není. */
    val incoming: StateFlow<SharedContent?> = current.asStateFlow()

    private var nextId = 1L

    /**
     * Přišlo sdílení. [hasFiles] = soubory se teprve připravují a přijdou v [prepared]; bez nich
     * je obsah hned hotový. Nové sdílení nahradí to předchozí, nevyřízené.
     */
    fun start(text: String?, hasFiles: Boolean): Long {
        val id = nextId++
        current.value = SharedContent(
            id = id,
            text = text?.takeIf { it.isNotBlank() },
            attachments = if (hasFiles) null else emptyList(),
        )
        return id
    }

    /** Soubory jsou v cache; [failed] se nepodařilo připravit a konverzace to ohlásí. */
    fun prepared(id: Long, attachments: List<OutgoingAttachment>, failed: Int) {
        current.update { content ->
            content?.takeIf { it.id == id }?.copy(attachments = attachments, failed = failed) ?: content
        }
    }

    /** Uživatel vybral chat. Obsah si vyzvedne jeho konverzace, až bude připravený. */
    fun choose(chatId: Long) {
        current.update { it?.copy(targetChatId = chatId, awaitingConfirmation = false) }
    }

    /**
     * Chat je vybraný zvenčí (Direct Share), ale obsah ještě nikam nejde — čeká na obrazovce
     * „Komu: …", kde uživatel vidí, co posílá, a teprve odtud ho odešle.
     *
     * Od [choose] se liší právě tím: vybraný chat ho z konverzace nesmí vyzvednout
     * ([deliveries]), protože odesílá ta obrazovka sama.
     */
    fun confirm(chatId: Long) {
        current.update { it?.copy(targetChatId = chatId, awaitingConfirmation = true) }
    }

    /**
     * Obrazovka sdílení odešla, aniž by obsah někam poslala — sdílení se zahodí. Zůstává jen to,
     * co už je na cestě do konverzace: tam výběr taky odchází (nahradí ho chat) a obsah na ni čeká.
     *
     * Obsah čekající na potvrzení ([confirm]) se zahazuje, i když chat vybraný má — odejít z té
     * obrazovky znamená „nechci to poslat", a bez zahození by se sdílení vložilo do konverzace,
     * kterou uživatel neotevřel.
     */
    fun abandon() {
        current.update { it?.takeIf { content -> content.targetChatId != null && !content.awaitingConfirmation } }
    }

    /**
     * Obsah pro chat [chatId], jakmile je vybraný a připravený. Kdo ho dostane, musí zavolat
     * [consume] — jinak by ho konverzace po otočení displeje vložila podruhé.
     */
    fun deliveries(chatId: Long): Flow<SharedContent> = current
        .filterNotNull()
        .filter { it.targetChatId == chatId && it.attachments != null && !it.awaitingConfirmation }

    /**
     * Vyřízeno. Porovnává se id: mezi vyzvednutím a vyřízením mohlo přijít nové sdílení
     * a to by se zahodilo nepřečtené.
     */
    fun consume(id: Long) {
        current.update { it?.takeIf { content -> content.id != id } }
    }
}

data class SharedContent(
    val id: Long,
    /** Sdílený text nebo odkaz; u souborů je to popisek. */
    val text: String?,
    /** `null`, dokud se soubory připravují. */
    val attachments: List<OutgoingAttachment>?,
    /** Kolik souborů se nepodařilo připravit. */
    val failed: Int = 0,
    /** Vybraný chat; `null`, dokud se vybírá. */
    val targetChatId: Long? = null,
    /**
     * Chat je vybraný, ale obsah čeká na obrazovce „Komu: …" ([ShareRequests.confirm]), ne
     * v konverzaci. Odesílá ta obrazovka, takže konverzace si ho vyzvednout nesmí.
     */
    val awaitingConfirmation: Boolean = false,
)
