package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageReaction
import kotlinx.coroutines.flow.MutableStateFlow

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
