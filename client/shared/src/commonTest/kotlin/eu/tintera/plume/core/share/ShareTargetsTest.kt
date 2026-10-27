package eu.tintera.plume.core.share

import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.FakeChatRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.UnimplementedFileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** „Přidat na plochu": zástupce dostane název a staženou fotku chatu, mimo Android se nenabízí. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareTargetsTest {

    private data class Pinned(val chatId: Long, val title: String, val avatarPath: String?, val isSavedMessages: Boolean)

    private class Publisher(override val canPin: Boolean = true) : ShareTargetPublisher {
        val pinned = mutableListOf<Pinned>()
        override fun publish(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean) = Unit
        override fun pin(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean): Boolean {
            pinned += Pinned(chatId, title, avatarPath, isSavedMessages)
            return true
        }
    }

    // Bez Uložených zpráv: ty si název čtou z prostředků, a o ně tu nejde.
    private val chats = object : FakeChatRepository() {
        override val savedMessagesChatId: StateFlow<Long?> = MutableStateFlow(null)
    }

    private val conversations = object : UnimplementedConversationRepository() {
        override suspend fun chatInfo(chatId: Long): Result<ChatInfo> = Result.success(
            ChatInfo(
                id = chatId,
                title = "Alena",
                lastReadOutboxMessageId = 0,
                lastReadInboxMessageId = 0,
                unreadCount = 0,
                avatarFileId = 7,
                isGroup = false,
                isChannel = false,
                isSavedMessages = false,
                draftText = null,
                draftReplyToMessageId = null,
                canJoin = false,
            ),
        )
    }

    private val files = object : UnimplementedFileRepository() {
        override fun file(fileId: Int): Flow<FileState> = flowOf(FileState.Ready("/avatars/$fileId.jpg"))
    }

    @Test
    fun pripneChatSNazvemAFotkou() = runTest {
        val publisher = Publisher()
        val targets = ShareTargets(conversations, chats, files, publisher, backgroundScope)

        assertTrue(targets.canPinToHomeScreen)
        targets.pinToHomeScreen(42)
        // `advanceUntilIdle` práci v `backgroundScope` nečeká.
        runCurrent()

        assertEquals(listOf(Pinned(42, "Alena", "/avatars/7.jpg", isSavedMessages = false)), publisher.pinned)
    }

    @Test
    fun bezLauncheruSeNenabizi() = runTest {
        val targets = ShareTargets(conversations, chats, files, Publisher(canPin = false), backgroundScope)
        assertFalse(targets.canPinToHomeScreen)
    }
}
