package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.GroupCall
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.GroupCallJoinParameters
import dev.g000sha256.tdl.dto.GroupCallParticipant
import dev.g000sha256.tdl.dto.GroupCallParticipantVideoInfo
import dev.g000sha256.tdl.dto.GroupCallVideoQualityFull
import dev.g000sha256.tdl.dto.GroupCallVideoQualityMedium
import dev.g000sha256.tdl.dto.GroupCallVideoQualityThumbnail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

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
        client.getGroupCallStreams(groupCallId = groupCallId).toResult()
            .onFailure { logger.w(it) { "getGroupCallStreams $groupCallId" } }
            .map { result -> result.streams.maxOfOrNull { it.timeOffset } ?: 0L }

    override suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?): Result<ByteArray> =
        client.getGroupCallStreamSegment(
            groupCallId = groupCallId,
            timeOffset = timeMs,
            // Knihovna chce segmenty po vteřině (tak to dělá i pytgcalls); měřítko 1 = půl vteřiny.
            scale = 0,
            channelId = channelId,
            videoQuality = when (quality) {
                LiveVideoQuality.Thumbnail -> GroupCallVideoQualityThumbnail()
                LiveVideoQuality.Medium -> GroupCallVideoQualityMedium()
                LiveVideoQuality.Full -> GroupCallVideoQualityFull()
                null -> null
            },
        ).toResult().map { it.data }
}

private fun GroupCall.toLiveCall() = LiveCall(
    groupCallId = id,
    isActive = isActive,
    participantCount = participantCount,
    isRtmp = isRtmpStream,
)

private fun GroupCallParticipant.toStreamer() = LiveStreamer(
    participantId = participantId.plainId(),
    camera = videoInfo?.toLiveVideo(),
    screen = screenSharingVideoInfo?.toLiveVideo(),
)

private fun GroupCallParticipantVideoInfo.toLiveVideo() = LiveVideo(
    endpoint = endpointId,
    sourceGroups = sourceGroups.map { LiveVideoSourceGroup(it.semantics, it.sourceIds.toList()) },
)
