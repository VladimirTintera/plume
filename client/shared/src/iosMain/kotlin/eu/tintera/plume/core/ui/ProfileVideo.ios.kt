package eu.tintera.plume.core.ui

import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetExportPresetMediumQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeMPEG4
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
import platform.AVFoundation.setPreferredTransform
import platform.AVFoundation.setFileLengthLimit
import platform.AVFoundation.setVideoComposition
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMinimum
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.CMTimeSubtract
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/**
 * AVFoundation: kompozice jen se stopou obrazu (tím zmizí zvuk) zkrácená na 10 s od začátku úseku,
 * nad ní `AVMutableVideoComposition` s výřezem a export do MP4.
 *
 * **Datový tok export nastavit neumí**, jen předvolbou. Proto `MediumQuality` a navíc
 * `fileLengthLimit` na strop Telegramu — kdyby ho soubor přesto přerostl, export ho zkrátí,
 * místo aby server profilovku odmítl.
 */
@OptIn(ExperimentalForeignApi::class)
class IosProfileVideo : ProfileVideo {

    override suspend fun durationMs(path: String): Long = withContext(Dispatchers.Default) {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
        (CMTimeGetSeconds(asset.duration) * 1000).toLong().coerceAtLeast(0)
    }

    override suspend fun frame(path: String, atMs: Long, edge: Int): String = withContext(Dispatchers.Default) {
        val output = path.substringBeforeLast('/') + "/" + profileFrameName(atMs, edge)
        if (NSFileManager.defaultManager.fileExistsAtPath(output)) return@withContext output
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
        val generator = AVAssetImageGenerator(asset = asset).apply {
            appliesPreferredTrackTransform = true
            maximumSize = CGSizeMake(edge.toDouble(), edge.toDouble())
            // Snímek pro statickou fotku PŘESNĚ v čase (server ho vezme odtud); dlaždicím pásu
            // stačí nejbližší klíčový, ten je rychlý.
            if (edge > PROFILE_VIDEO_STRIP_EDGE) {
                requestedTimeToleranceBefore = CMTimeMake(value = 0, timescale = 1)
                requestedTimeToleranceAfter = CMTimeMake(value = 0, timescale = 1)
            }
        }
        val cgImage = generator.copyCGImageAtTime(CMTimeMake(value = atMs, timescale = 1000), actualTime = null, error = null)
            ?: error("Video nemá snímek.")
        try {
            val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(cgImage), PHOTO_JPEG_QUALITY / 100.0)
                ?: error("JPEG nejde vytvořit.")
            // `atomically`: souběžné čtení (pás i ořez) nesmí dostat napůl zapsaný JPEG.
            check(jpeg.writeToFile(output, atomically = true)) { "Snímek nejde uložit." }
            output
        } finally {
            CGImageRelease(cgImage)
        }
    }

    override suspend fun crop(path: String, region: CropRegion?, startMs: Long): String {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
        val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
            ?: error("Video nemá obraz.")

        val composition = AVMutableComposition()
        val video = composition.addMutableTrackWithMediaType(AVMediaTypeVideo, preferredTrackID = 0)
            ?: error("Kompozice nejde založit.")
        val start = CMTimeMake(value = 0, timescale = 1)
        val from = CMTimeMake(value = startMs, timescale = 1000)
        val length = CMTimeMinimum(
            CMTimeSubtract(asset.duration, from),
            CMTimeMake(value = PROFILE_VIDEO_MAX_SECONDS.toLong(), timescale = 1),
        )
        check(video.insertTimeRange(CMTimeRangeMake(from, length), ofTrack = track, atTime = start, error = null)) {
            "Video nejde zkrátit."
        }
        video.setPreferredTransform(track.preferredTransform)

        // Otočený obraz: `preferredTransform` ho otočí, ale může ho posunout mimo počátek.
        val (originX, originY, width, height) = track.naturalSize.useContents {
            CGRectApplyAffineTransform(CGRectMake(0.0, 0.0, width, height), track.preferredTransform).useContents {
                listOf(origin.x, origin.y, abs(size.width), abs(size.height))
            }
        }
        val (left, top, sidePixels) = squarePixels(region, width.toInt(), height.toInt())
        val side = sidePixels.toDouble()
        val edge = ((minOf(sidePixels, PROFILE_VIDEO_EDGE).coerceAtLeast(PROFILE_VIDEO_MIN_EDGE) / 2) * 2).toDouble()

        var transform = CGAffineTransformConcat(
            track.preferredTransform,
            CGAffineTransformMakeTranslation(-originX - left, -originY - top),
        )
        transform = CGAffineTransformConcat(transform, CGAffineTransformMakeScale(edge / side, edge / side))
        val layer = AVMutableVideoCompositionLayerInstruction.videoCompositionLayerInstructionWithAssetTrack(video)
        layer.setTransform(transform, atTime = start)
        val instruction = AVMutableVideoCompositionInstruction()
        instruction.setTimeRange(CMTimeRangeMake(start, composition.duration))
        instruction.setLayerInstructions(listOf(layer))
        val videoComposition = AVMutableVideoComposition()
        videoComposition.setRenderSize(CGSizeMake(edge, edge))
        videoComposition.setFrameDuration(CMTimeMake(value = 1, timescale = FRAME_RATE))
        videoComposition.setInstructions(listOf(instruction))

        val target = path.substringBeforeLast('/') + "/profile.mp4"
        val session = AVAssetExportSession(asset = composition, presetName = AVAssetExportPresetMediumQuality)
            ?: error("Export nejde založit.")
        session.outputURL = NSURL.fileURLWithPath(target)
        session.outputFileType = AVFileTypeMPEG4
        session.setVideoComposition(videoComposition)
        session.setFileLengthLimit(PROFILE_VIDEO_MAX_BYTES)
        session.shouldOptimizeForNetworkUse = true
        val completed = suspendCancellableCoroutine { continuation ->
            session.exportAsynchronouslyWithCompletionHandler {
                continuation.resume(session.status == AVAssetExportSessionStatusCompleted)
            }
            continuation.invokeOnCancellation { session.cancelExport() }
        }
        check(completed) { "Video se nepodařilo převést." }
        return target
    }

    private companion object {
        const val FRAME_RATE = 30
    }
}
