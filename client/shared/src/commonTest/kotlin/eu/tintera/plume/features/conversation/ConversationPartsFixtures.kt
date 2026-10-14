package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageReaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** Chat, ve kterém se testy částí [ConversationViewModel] odehrávají. */
internal const val TEST_CHAT = 42L

internal val testLogger = Logger.withTag("Test")

/** Zpráva do stavu konverzace; jen pole, na kterých části rozhodují. */
internal fun testMessage(
    id: Long,
    text: String = "zpráva $id",
    outgoing: Boolean = false,
    album: Long = 0L,
    reactions: List<MessageReaction> = emptyList(),
) = ChatMessage(
    id = id,
    isOutgoing = outgoing,
    senderName = null,
    senderAvatarFileId = null,
    senderId = 7L,
    text = text,
    entities = emptyList(),
    media = null,
    linkPreview = null,
    reactions = reactions,
    sendState = null,
    replyToMessageId = null,
    isService = false,
    timestamp = id.toInt(),
    isEdited = false,
    mediaAlbumId = album,
)

/** Stav konverzace se zprávami seřazenými od nejnovější, jak je drží ViewModel. */
internal fun testState(vararg messages: ChatMessage) =
    MutableStateFlow(ConversationState(messages = messages.sortedByDescending { it.id }))

/** Hlavička chatu; výchozí je soukromý chat, kam se smí psát. */
internal fun testChatInfo(
    isGroup: Boolean = false,
    isChannel: Boolean = false,
    isSavedMessages: Boolean = false,
    canJoin: Boolean = false,
) = ChatInfo(
    id = TEST_CHAT,
    title = "Test",
    lastReadOutboxMessageId = 0,
    lastReadInboxMessageId = 0,
    unreadCount = 0,
    avatarFileId = null,
    isGroup = isGroup,
    isChannel = isChannel,
    isSavedMessages = isSavedMessages,
    draftText = null,
    draftReplyToMessageId = null,
    canJoin = canJoin,
)


/**
 * Rozsah pro část, která v něm drží nekonečný odběr (`stateIn(…, Eagerly)`): na plánovači
 * testu, ale NE jako jeho dítě. Dítě by `runTest` čekal donekonečna a `backgroundScope` zase
 * nečeká `advanceUntilIdle`. Zruší se spolu s koncem testu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.detachedScope(): CoroutineScope =
    CoroutineScope(StandardTestDispatcher(testScheduler) + Job()).also { detached ->
        backgroundScope.coroutineContext.job.invokeOnCompletion { detached.cancel() }
    }
