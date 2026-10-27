package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.MessageTopicThread
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.ui.UiText

/**
 * Rozepsaný komentář ve vlákně komentářů [threadId] diskusní skupiny [chatId]. [post] je
 * jednořádkový náhled příspěvku, ke kterému vlákno patří (kořen vlákna); `null`, když ho TDLib
 * nevrátil.
 */
data class ThreadDraft(
    val chatId: Long,
    val threadId: Long,
    val post: UiText?,
    val draft: ChatDraft,
)

/**
 * Koncepty ve vláknech komentářů. Na rozdíl od chatů a témat je TDLib nehlásí ŽÁDNÝM updatem
 * (`UpdateChatDraftMessage` nese jen koncept celého chatu, `UpdateForumTopic` jen témata) —
 * koncept vlákna je jen v odpovědi `getMessageThread`. Které vlákno koncept má, proto musí vědět
 * volající (aplikace si pamatuje vlákna, kde koncept psala); tady se jen přečte a smaže.
 */
interface ThreadDrafts {

    /** Koncept vlákna, nebo `null`, když žádný není (odeslaný, smazaný na jiném zařízení). */
    suspend fun read(chatId: Long, threadId: Long): Result<ThreadDraft?>

    /** Smaže koncept vlákna; vrátí, čím ho vrátit celý (jako [ChatRepository.deleteDraft]). */
    suspend fun delete(chatId: Long, threadId: Long): Result<DraftRestore?>
}

/** [ThreadDrafts] nad TDLib. Bezstavová, updaty neodebírá — žádné nejsou. */
class TdlThreadDrafts(private val holder: TelegramClientHolder) : ThreadDrafts {

    private val client: TdlClient get() = holder.client

    override suspend fun read(chatId: Long, threadId: Long): Result<ThreadDraft?> =
        client.getMessageThread(chatId = chatId, messageId = threadId).toResult().map { thread ->
            val draft = thread.draftMessage?.toChatDraft() ?: return@map null
            // Zprávy, kterými vlákno začíná, jdou od nejnovější; kořen je ta nejstarší.
            val root = thread.messages.filterNotNull().lastOrNull()
            ThreadDraft(chatId = thread.chatId, threadId = thread.messageThreadId, post = root?.previewText(), draft = draft)
        }

    override suspend fun delete(chatId: Long, threadId: Long): Result<DraftRestore?> {
        val topic = MessageTopicThread(messageThreadId = threadId)
        val draft = client.getMessageThread(chatId = chatId, messageId = threadId).toResult()
            .getOrElse { return Result.failure(it) }
            .draftMessage ?: return Result.success(null)
        return client.setChatDraftMessage(chatId = chatId, topicId = topic, draftMessage = null)
            .toResult()
            .map {
                DraftRestore {
                    client.setChatDraftMessage(chatId = chatId, topicId = topic, draftMessage = draft).toResult().map { }
                }
            }
    }
}
