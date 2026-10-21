package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.telegram.ChatExportKind
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_import_done
import eu.tintera.plume.resources.chat_import_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * Import chatu z jiné aplikace (export z WhatsAppu sdílený do Plume): sdílený `.txt`, který
 * Telegram pozná jako export (`getMessageFileType`), se místo přílohy nabídne k importu do
 * otevřeného chatu. Ostatní sdílené soubory jsou jeho přílohy — páruje je server podle jména.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationImport(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val shareRequests: ShareRequests,
    private val logger: Logger,
    /** Sdílené soubory nad vstupní pole — kam jdou, když import do chatu nejde. */
    private val attachAsFiles: (List<OutgoingAttachment>) -> Unit = {},
) {
    /**
     * Je sdílený obsah export chatu? Když ano, vyzvedne si ho (`consume`) a nabídne import;
     * jinak vrátí `false` a obsah jde obvyklou cestou nad vstupní pole.
     */
    suspend fun offer(content: SharedContent): Boolean {
        val files = content.attachments?.map { it.path }.orEmpty()
        val export = files.firstOrNull { it.endsWith(".txt", ignoreCase = true) } ?: return false
        val head = withContext(Dispatchers.Default) { runCatching { readHead(export) }.getOrNull() } ?: return false
        val kind = repository.chatExportKind(head).getOrNull() ?: return false
        if (!shareRequests.consume(content.id)) return true
        // Do chatu, kam import nejde (cizí skupina, kanál, nevzájemný kontakt, skupinový export
        // do soukromého chatu a naopak), věta k potvrzení není.
        val confirmation = repository.importConfirmation(chatId, kind).getOrNull()
        state.update {
            it.copy(chatImport = ChatImportOffer(export, files - export, kind, confirmation, content.attachments.orEmpty()))
        }
        return true
    }

    /** Import potvrzený v dialogu. */
    fun confirm() {
        val offer = state.value.chatImport?.takeIf { it.confirmation != null && !it.importing } ?: return
        state.update { it.copy(chatImport = offer.copy(importing = true)) }
        scope.launch {
            repository.importMessages(chatId, offer.messageFile, offer.attachedFiles)
                .onSuccess { state.update { it.copy(chatImport = null, notice = UiText(Res.string.chat_import_done)) } }
                .onFailure { failure ->
                    state.update { it.copy(chatImport = null, error = failure.reported(logger, Res.string.chat_import_failed)) }
                }
        }
    }

    fun dismiss() = state.update { current -> current.copy(chatImport = current.chatImport?.takeIf { it.importing }) }

    /** Do chatu, kam import nejde: export i přílohy se vloží nad pole jako obyčejné soubory. */
    fun attachInstead() {
        val offer = state.value.chatImport?.takeIf { !it.importing } ?: return
        state.update { it.copy(chatImport = null) }
        attachAsFiles(offer.files)
    }

    /** Prvních sto řádků souboru — víc `getMessageFileType` nechce. */
    private fun readHead(path: String): String =
        FileSystem.SYSTEM.source(path.toPath()).buffer().use { source ->
            buildString {
                var lines = 0
                while (lines < HEAD_LINES) {
                    val line = source.readUtf8Line() ?: break
                    append(line).append('\n')
                    lines++
                }
            }
        }
}

/**
 * Nabídka importu: soubor zpráv, přílohy, co v exportu je, a věta od serveru k potvrzení —
 * `null` = do tohohle chatu import nejde (dialog to řekne a nic nenabídne).
 */
data class ChatImportOffer(
    val messageFile: String,
    val attachedFiles: List<String>,
    val kind: ChatExportKind,
    val confirmation: String?,
    /** Sdílené soubory, jak přišly — pro vložení jako přílohy místo importu. */
    val files: List<OutgoingAttachment> = emptyList(),
    val importing: Boolean = false,
)

private const val HEAD_LINES = 100
