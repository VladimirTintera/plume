package eu.tintera.plume.core.drafts

import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatDraft
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.DraftRestore
import eu.tintera.plume.core.telegram.FakeChatRepository
import eu.tintera.plume.core.telegram.ThreadDraft
import eu.tintera.plume.core.telegram.ThreadDrafts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Evidence konceptů ve vláknech komentářů: TDLib je nehlásí, takže se pamatují vlákna, kde
 * koncept vznikl, a při čtení se ověří.
 */
class CommentDraftsTest {

    private class Settings : UnimplementedAppSettings() {
        val threads = MutableStateFlow<List<CommentDraftThread>>(emptyList())
        override val commentDraftThreads: StateFlow<List<CommentDraftThread>> get() = threads
        override fun setCommentDraftThreads(threads: List<CommentDraftThread>) {
            this.threads.value = threads
        }
    }

    private class Chats : FakeChatRepository() {
        override suspend fun summaries(chatIds: List<Long>): List<ChatSummary> = chatIds.map { chat(it) }
    }

    /** Koncepty podle (chat, vlákno); chybějící klíč = koncept žádný, [failing] = chyba čtení. */
    private class Threads : ThreadDrafts {
        val drafts = mutableMapOf<Pair<Long, Long>, ChatDraft>()
        val failing = mutableSetOf<Pair<Long, Long>>()

        override suspend fun read(chatId: Long, threadId: Long): Result<ThreadDraft?> {
            if (chatId to threadId in failing) return Result.failure(IllegalStateException("offline"))
            return Result.success(drafts[chatId to threadId]?.let { ThreadDraft(chatId, threadId, post = null, draft = it) })
        }

        override suspend fun delete(chatId: Long, threadId: Long): Result<DraftRestore?> {
            val draft = drafts.remove(chatId to threadId) ?: return Result.success(null)
            return Result.success(DraftRestore { drafts[chatId to threadId] = draft; Result.success(Unit) })
        }
    }

    private val settings = Settings()
    private val threads = Threads()
    private val drafts = CommentDrafts(settings, Chats(), threads)

    @Test
    fun zapamatovaneVlaknoSKonceptemJeVPrehledu() = runTest {
        threads.drafts[10L to 5L] = ChatDraft("Rozepsaný komentář", timestamp = 100)
        drafts.note(chatId = 10, threadId = 5, hasDraft = true)

        val found = drafts.drafts.first { it.isNotEmpty() }
        assertEquals(listOf(10L to 5L), found.map { it.chat.id to it.threadId })
        assertEquals("Rozepsaný komentář", found.single().draft.text)
    }

    @Test
    fun vlaknoBezKonceptuSeZEvidenceVyhodi() = runTest {
        // Koncept odešel nebo se smazal na jiném zařízení; evidence o tom neví.
        drafts.note(chatId = 10, threadId = 5, hasDraft = true)

        assertEquals(emptyList(), drafts.drafts.first())
        assertEquals(emptyList(), settings.threads.value)
    }

    @Test
    fun chybaCteniVlaknoNevyhodi() = runTest {
        threads.failing += 10L to 5L
        drafts.note(chatId = 10, threadId = 5, hasDraft = true)

        assertEquals(emptyList(), drafts.drafts.first())
        assertEquals(1, settings.threads.value.size, "Bez sítě se má zkusit příště, ne zapomenout")
    }

    @Test
    fun prazdnyKonceptVlaknoZapomene() {
        drafts.note(chatId = 10, threadId = 5, hasDraft = true)
        drafts.note(chatId = 10, threadId = 5, hasDraft = false)
        assertTrue(settings.threads.value.isEmpty())
    }

    @Test
    fun evidenceCizihoUctuSeNeukazuje() = runTest {
        threads.drafts[10L to 5L] = ChatDraft("Cizí", timestamp = 100)
        settings.threads.value = listOf(CommentDraftThread(accountId = 1, chatId = 10, threadId = 5))

        assertEquals(emptyList(), drafts.drafts.first())
    }

    @Test
    fun smazaniAVraceniVraciIEvidenci() = runTest {
        threads.drafts[10L to 5L] = ChatDraft("Komentář", timestamp = 100)
        drafts.note(chatId = 10, threadId = 5, hasDraft = true)

        val restore = drafts.delete(chatId = 10, threadId = 5).getOrThrow()
        assertTrue(settings.threads.value.isEmpty())

        restore!!.restore()
        assertEquals(listOf(CommentDraftThread(accountId = 999, chatId = 10, threadId = 5)), settings.threads.value)
        assertEquals("Komentář", drafts.drafts.first { it.isNotEmpty() }.single().draft.text)
    }

    private companion object {
        fun chat(id: Long) = ChatSummary(
            id = id,
            title = "Diskuse $id",
            unreadCount = 0,
            lastMessagePreview = null,
            isPinned = false,
            order = id,
            avatarFileId = null,
            isMuted = false,
            isMarkedAsUnread = false,
            kind = ChatKind.Private,
            lastMessageTimestamp = null,
            presence = null,
            activity = null,
            draft = null,
        )
    }
}
