package eu.tintera.plume.core.telegram

/** Zpráva pro testy; vyplněná jen pole, na kterých testy rozhodují (konverzace, kalendář médií…). */
fun testMessage(
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
