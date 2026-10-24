package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.GroupCallParticipantVideoInfo
import dev.g000sha256.tdl.dto.GroupCallVideoQualityFull
import dev.g000sha256.tdl.dto.GroupCallVideoQualityMedium
import dev.g000sha256.tdl.dto.GroupCallVideoQualityThumbnail
import dev.g000sha256.tdl.toResult

/*
 * Stream skupinového hovoru po segmentech — společné živým příběhům i videochatům. Knihovna
 * (ntgcalls) si o čas a segmenty říká sama, když server hovor pustí jako stream (velké
 * publikum, RTMP).
 */

/** Kde stream právě končí (unixové milisekundy); 0, dokud žádný není. */
internal suspend fun TdlClient.groupCallStreamTimestamp(groupCallId: Int): Result<Long> =
    getGroupCallStreams(groupCallId = groupCallId).toResult()
        .map { result -> result.streams.maxOfOrNull { it.timeOffset } ?: 0L }

internal suspend fun TdlClient.groupCallSegment(
    groupCallId: Int,
    timeMs: Long,
    channelId: Int,
    quality: LiveVideoQuality?,
): Result<ByteArray> =
    getGroupCallStreamSegment(
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

internal fun GroupCallParticipantVideoInfo.toLiveVideo() = LiveVideo(
    endpoint = endpointId,
    sourceGroups = sourceGroups.map { LiveVideoSourceGroup(it.semantics, it.sourceIds.toList()) },
)
