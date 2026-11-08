package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.GroupCall
import dev.g000sha256.tdl.dto.GroupCallJoinParameters
import dev.g000sha256.tdl.dto.GroupCallParticipant
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** [VideoChatRepository] nad TDLib. */
class TdlVideoChatRepository(private val holder: TelegramClientHolder) : VideoChatRepository {

    private val logger = Logger.withTag("VideoChat")

    private val client get() = holder.client

    override suspend fun join(groupCallId: Int, payload: String, audioSourceId: Int): Result<String> =
        client.joinVideoChat(
            groupCallId = groupCallId,
            // Za sebe, ne za kanál, který uživatel spravuje.
            participantId = null,
            joinParameters = GroupCallJoinParameters(
                audioSourceId = audioSourceId,
                payload = payload,
                isMuted = true,
                isMyVideoEnabled = false,
            ),
            inviteHash = "",
        ).toResult()
            .onFailure { logger.w(it) { "joinVideoChat $groupCallId" } }
            .map { it.text }

    override suspend fun leave(groupCallId: Int) {
        client.leaveGroupCall(groupCallId = groupCallId).toResult()
            .onFailure { logger.w(it) { "leaveGroupCall $groupCallId" } }
    }

    override suspend fun call(groupCallId: Int): Result<VideoChatCall> =
        client.getGroupCall(groupCallId = groupCallId).toResult()
            .onFailure { logger.w(it) { "getGroupCall $groupCallId" } }
            .map { it.toVideoChatCall() }

    override val callUpdates: Flow<VideoChatCall> =
        holder.updates { groupCallUpdates }.map { it.groupCall.toVideoChatCall() }

    override suspend fun loadParticipants(groupCallId: Int) {
        client.loadGroupCallParticipants(groupCallId = groupCallId, limit = PARTICIPANT_PAGE).toResult()
            .onFailure { logger.w(it) { "loadGroupCallParticipants $groupCallId" } }
    }

    override val participantUpdates: Flow<VideoChatParticipantUpdate> =
        holder.updates { groupCallParticipantUpdates }.mapNotNull { update ->
            update.participant.toParticipant()?.let { VideoChatParticipantUpdate(update.groupCallId, it) }
        }

    override suspend fun streamTimestamp(groupCallId: Int): Result<Long> =
        client.groupCallStreamTimestamp(groupCallId)
            .onFailure { logger.w(it) { "getGroupCallStreams $groupCallId" } }

    override suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?): Result<ByteArray> =
        client.groupCallSegment(groupCallId, timeMs, channelId, quality)

    override suspend fun setMuted(groupCallId: Int, participantId: Long, muted: Boolean): Result<Unit> =
        client.toggleGroupCallParticipantIsMuted(
            groupCallId = groupCallId,
            participantId = participantId.toMessageSender(),
            isMuted = muted,
        ).toResult()
            .onFailure { logger.w(it) { "toggleGroupCallParticipantIsMuted $groupCallId/$participantId $muted" } }
            .map { }

    override suspend fun setHandRaised(groupCallId: Int, participantId: Long, raised: Boolean): Result<Unit> =
        client.toggleGroupCallParticipantIsHandRaised(
            groupCallId = groupCallId,
            participantId = participantId.toMessageSender(),
            isHandRaised = raised,
        ).toResult()
            .onFailure { logger.w(it) { "toggleGroupCallParticipantIsHandRaised $groupCallId/$participantId $raised" } }
            .map { }

    override suspend fun chatTitle(chatId: Long): String? =
        client.getChat(chatId = chatId).toResult().getOrNull()?.title

    private suspend fun GroupCallParticipant.toParticipant(): VideoChatParticipant? {
        val sender = sender(participantId) ?: return null
        return VideoChatParticipant(
            id = participantId.plainId(),
            name = sender.name,
            avatarFileId = sender.avatarFileId,
            camera = videoInfo?.toLiveVideo(),
            screen = screenSharingVideoInfo?.toLiveVideo(),
            isMuted = isMutedForAllUsers,
            canUnmuteSelf = canUnmuteSelf,
            isHandRaised = isHandRaised,
            isCurrentUser = isCurrentUser,
            order = order,
        )
    }

    private class Sender(val name: String, val avatarFileId: Int?)

    private val senders = ClientCache<Long, Sender>(holder)

    /** Jméno a fotka účastníka; updaty téhož účastníka chodí pořád dokola, proto mezipaměť. */
    private suspend fun sender(id: MessageSender): Sender? =
        senders.getOrLoad(id.plainId()) { client ->
            when (id) {
                is MessageSenderUser -> client.getUser(userId = id.userId).toResult().getOrNull()?.let { user ->
                    Sender(listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "), user.profilePhoto?.small?.id)
                }
                is MessageSenderChat -> client.getChat(chatId = id.chatId).toResult().getOrNull()?.let { chat ->
                    Sender(chat.title, chat.photo?.small?.id)
                }
            }
        }
}

private fun GroupCall.toVideoChatCall() = VideoChatCall(
    groupCallId = id,
    isActive = isActive,
    participantCount = participantCount,
    title = title,
    isRtmp = isRtmpStream,
)

/** Kolik účastníků dočíst najednou; víc TDLib nedovolí. */
private const val PARTICIPANT_PAGE = 100
