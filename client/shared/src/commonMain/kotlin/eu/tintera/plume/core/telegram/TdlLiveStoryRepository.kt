package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.GroupCall
import dev.g000sha256.tdl.dto.GroupCallMessage
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.GroupCallJoinParameters
import dev.g000sha256.tdl.dto.GroupCallParticipant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [LiveStoryRepository] nad TDLib. */
class TdlLiveStoryRepository(private val holder: TelegramClientHolder) : LiveStoryRepository {

    private val logger = Logger.withTag("LiveStory")

    private val client get() = holder.client

    override suspend fun join(groupCallId: Int, payload: String, audioSourceId: Int): Result<String> =
        client.joinLiveStory(
            groupCallId = groupCallId,
            joinParameters = GroupCallJoinParameters(
                audioSourceId = audioSourceId,
                payload = payload,
                isMuted = true,
                isMyVideoEnabled = false,
            ),
        ).toResult()
            .onFailure { logger.w(it) { "joinLiveStory $groupCallId" } }
            .map { it.text }

    override suspend fun leave(groupCallId: Int) {
        client.leaveGroupCall(groupCallId = groupCallId).toResult()
            .onFailure { logger.w(it) { "leaveGroupCall $groupCallId" } }
    }

    override suspend fun call(groupCallId: Int): Result<LiveCall> =
        client.getGroupCall(groupCallId = groupCallId).toResult()
            .onFailure { logger.w(it) { "getGroupCall $groupCallId" } }
            .map { it.toLiveCall() }

    override val callUpdates: Flow<LiveCall> =
        holder.updates { groupCallUpdates }.map { it.groupCall.toLiveCall() }

    override suspend fun streamer(groupCallId: Int): Result<LiveStreamer?> =
        client.getLiveStoryStreamer(groupCallId = groupCallId).toResult()
            .onFailure { logger.w(it) { "getLiveStoryStreamer $groupCallId" } }
            .map { it.toStreamer() }

    override val participantUpdates: Flow<LiveParticipantUpdate> =
        holder.updates { groupCallParticipantUpdates }.mapNotNull { update ->
            // Sami jsme taky účastník (ztlumený divák bez obrazu); vysílající je ten, kdo obraz posílá.
            if (update.participant.isCurrentUser) return@mapNotNull null
            LiveParticipantUpdate(update.groupCallId, update.participant.toStreamer())
        }

    override suspend fun streamTimestamp(groupCallId: Int): Result<Long> =
        client.groupCallStreamTimestamp(groupCallId)
            .onFailure { logger.w(it) { "getGroupCallStreams $groupCallId" } }

    override suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?): Result<ByteArray> =
        client.groupCallSegment(groupCallId, timeMs, channelId, quality)

    override val messageEvents: Flow<LiveMessageEvent> = merge(
        holder.updates { newGroupCallMessageUpdates }.mapNotNull { update ->
            update.message.toLiveMessage()?.let { LiveMessageEvent.New(update.groupCallId, it) }
        },
        holder.updates { groupCallMessagesDeletedUpdates }.map { update ->
            LiveMessageEvent.Deleted(update.groupCallId, update.messageIds.toSet())
        },
        holder.updates { groupCallMessageSendFailedUpdates }.map { update ->
            logger.w { "sendGroupCallMessage ${update.groupCallId}/${update.messageId}: ${update.error.message}" }
            LiveMessageEvent.SendFailed(update.groupCallId, update.messageId)
        },
        holder.updates { newGroupCallPaidReactionUpdates }.map { update ->
            LiveMessageEvent.PaidReaction(update.groupCallId, sender(update.senderId)?.name, update.starCount)
        },
    )

    override suspend fun sendMessage(groupCallId: Int, text: String): Result<Unit> =
        client.sendGroupCallMessage(
            groupCallId = groupCallId,
            // Do živého příběhu se zalomení řádků poslat nedá.
            text = FormattedText(text = text.replace('\n', ' '), entities = emptyArray()),
            paidMessageStarCount = 0,
        ).toResult()
            .onFailure { logger.w(it) { "sendGroupCallMessage $groupCallId" } }
            .map { }

    override suspend fun deleteMessage(groupCallId: Int, messageId: Int, reportSpam: Boolean): Result<Unit> =
        client.deleteGroupCallMessages(
            groupCallId = groupCallId,
            messageIds = intArrayOf(messageId),
            reportSpam = reportSpam,
        ).toResult()
            .onFailure { logger.w(it) { "deleteGroupCallMessages $groupCallId/$messageId" } }
            .map { }

    private suspend fun GroupCallMessage.toLiveMessage(): LiveMessage? {
        val sender = sender(senderId) ?: return null
        return LiveMessage(
            id = messageId,
            senderId = senderId.plainId(),
            senderName = sender.name,
            avatarFileId = sender.avatarFileId,
            text = text.text,
            starCount = paidMessageStarCount,
            isFromOwner = isFromOwner,
            canBeDeleted = canBeDeleted,
        )
    }

    private class Sender(val name: String, val avatarFileId: Int?)

    private val senders = mutableMapOf<Long, Sender>()
    private val sendersMutex = Mutex()

    /** Jméno a fotka odesílatele; ve vysílání píšou stále titíž, proto mezipaměť. */
    private suspend fun sender(id: MessageSender): Sender? {
        val key = id.plainId()
        sendersMutex.withLock { senders[key] }?.let { return it }
        val resolved = when (id) {
            is MessageSenderUser -> client.getUser(userId = id.userId).toResult().getOrNull()?.let { user ->
                Sender(listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "), user.profilePhoto?.small?.id)
            }
            is MessageSenderChat -> client.getChat(chatId = id.chatId).toResult().getOrNull()?.let { chat ->
                Sender(chat.title, chat.photo?.small?.id)
            }
        } ?: return null
        sendersMutex.withLock { senders[key] = resolved }
        return resolved
    }
}

private fun GroupCall.toLiveCall() = LiveCall(
    groupCallId = id,
    isActive = isActive,
    participantCount = participantCount,
    isRtmp = isRtmpStream,
    canSendMessages = canSendMessages && areMessagesAllowed,
    messageStarCount = paidMessageStarCount,
    canDeleteMessages = canDeleteMessages,
)

private fun GroupCallParticipant.toStreamer() = LiveStreamer(
    participantId = participantId.plainId(),
    camera = videoInfo?.toLiveVideo(),
    screen = screenSharingVideoInfo?.toLiveVideo(),
)
