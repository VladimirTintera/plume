package eu.tintera.plume.core.ui

import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAssetExportPresetHEVCHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableVideoComposition
import platform.AVFoundation.AVMutableVideoCompositionInstruction
import platform.AVFoundation.AVMutableVideoCompositionLayerInstruction
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.duration
import platform.AVFoundation.insertTimeRange
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.setVideoComposition
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMinimum
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.CMTimeSubtract
import platform.Foundation.NSURL

/**
 * AVFoundation: kompozice obrazu a zvuku zkrácená na 60 s, nad ní `AVMutableVideoComposition`
 * s plátnem 720 × 1280 a export předvolbou H.265.
 *
 * **Klíčový snímek každou sekundu export nastavit neumí** (jen `AVAssetWriter`); předvolba ho
 * dává po pár sekundách. Jestli to serveru vadí, ověřené NENÍ — iOS je jen přeložené.
 */
@OptIn(ExperimentalForeignApi::class)
class IosStoryVideo(private val profileVideo: ProfileVideo) : StoryVideo {

    override val supported: Boolean = true

    override suspend fun durationMs(path: String): Long = profileVideo.durationMs(path)

    override suspend fun frame(path: String, atMs: Long, edge: Int): String = profileVideo.frame(path, atMs, edge)

    override suspend fun render(path: String, fill: Boolean, startMs: Long): RenderedStoryVideo {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
        val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
            ?: error("Video nemá obraz.")
        val audioTrack = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack

        val composition = AVMutableComposition()
        val start = CMTimeMake(value = 0, timescale = 1)
        val from = CMTimeMake(value = startMs, timescale = 1000)
        val length = CMTimeMinimum(
            CMTimeSubtract(asset.duration, from),
            CMTimeMake(value = STORY_VIDEO_MAX_SECONDS.toLong(), timescale = 1),
        )
        // Úsek ze zdroje od [startMs], v kompozici od nuly.
        val range = CMTimeRangeMake(from, length)
        val video = composition.addMutableTrackWithMediaType(AVMediaTypeVideo, preferredTrackID = 0)
            ?: error("Kompozice nejde založit.")
        check(video.insertTimeRange(range, ofTrack = track, atTime = start, error = null)) { "Video nejde zkrátit." }
        if (audioTrack != null) {
            composition.addMutableTrackWithMediaType(AVMediaTypeAudio, preferredTrackID = 0)
                ?.insertTimeRange(range, ofTrack = audioTrack, atTime = start, error = null)
        }

        // Otočený obraz: `preferredTransform` ho otočí, ale může ho posunout mimo počátek.
        val (originX, originY, width, height) = track.naturalSize.useContents {
            CGRectApplyAffineTransform(CGRectMake(0.0, 0.0, width, height), track.preferredTransform).useContents {
                listOf(origin.x, origin.y, abs(size.width), abs(size.height))
            }
        }
        val canvasWidth = STORY_VIDEO_WIDTH.toDouble()
        val canvasHeight = STORY_VIDEO_HEIGHT.toDouble()
        val scale = if (fill) max(canvasWidth / width, canvasHeight / height) else min(canvasWidth / width, canvasHeight / height)
        var transform = CGAffineTransformConcat(track.preferredTransform, CGAffineTransformMakeTranslation(-originX, -originY))
        transform = CGAffineTransformConcat(transform, CGAffineTransformMakeScale(scale, scale))
        transform = CGAffineTransformConcat(
            transform,
            CGAffineTransformMakeTranslation((canvasWidth - width * scale) / 2, (canvasHeight - height * scale) / 2),
        )
        val layer = AVMutableVideoCompositionLayerInstruction.videoCompositionLayerInstructionWithAssetTrack(video)
        layer.setTransform(transform, atTime = start)
        val instruction = AVMutableVideoCompositionInstruction()
        instruction.setTimeRange(CMTimeRangeMake(start, composition.duration))
        instruction.setLayerInstructions(listOf(layer))
        val videoComposition = AVMutableVideoComposition()
        videoComposition.setRenderSize(CGSizeMake(canvasWidth, canvasHeight))
        videoComposition.setFrameDuration(CMTimeMake(value = 1, timescale = FRAME_RATE))
        videoComposition.setInstructions(listOf(instruction))

        val target = outgoingPath("story.mp4")
        val session = AVAssetExportSession(asset = composition, presetName = AVAssetExportPresetHEVCHighestQuality)
            ?: error("Export nejde založit.")
        session.outputURL = NSURL.fileURLWithPath(target)
        session.outputFileType = AVFileTypeMPEG4
        session.setVideoComposition(videoComposition)
        session.shouldOptimizeForNetworkUse = true
        val completed = suspendCancellableCoroutine { continuation ->
            session.exportAsynchronouslyWithCompletionHandler {
                continuation.resume(session.status == AVAssetExportSessionStatusCompleted)
            }
            continuation.invokeOnCancellation { session.cancelExport() }
        }
        check(completed) { "Video se nepodařilo převést." }
        return RenderedStoryVideo(
            path = target,
            durationSeconds = CMTimeGetSeconds(composition.duration),
            hasAudio = audioTrack != null,
        )
    }

    private companion object {
        const val FRAME_RATE = 30
    }
}
