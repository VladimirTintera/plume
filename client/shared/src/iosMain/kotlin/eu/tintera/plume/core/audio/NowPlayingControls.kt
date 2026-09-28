package eu.tintera.plume.core.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage

/**
 * Hudba na zamykací obrazovce a v Ovládacím centru: `MPNowPlayingInfoCenter` (co hraje)
 * a `MPRemoteCommandCenter` (povely ze systému, sluchátek, hodinek, CarPlay).
 *
 * Hrát na pozadí dovolí `UIBackgroundModes: audio` v Info.plist a kategorie relace Playback,
 * kterou nastavuje [AudioEngine]. Přehrávač sám pozici systému nehlásí — ten si ji dopočítává
 * z posledního hlášení a rychlosti, takže se hlásí při každé změně a za přehrávání průběžně
 * ([REFRESH_MS]); jinak by posun v bublině na zamykací obrazovce nebyl vidět.
 */
@OptIn(ExperimentalForeignApi::class)
class NowPlayingControls(private val queue: MusicQueue, private val scope: CoroutineScope) {

    private var refresh: Job? = null
    private var artworkPath: String? = null
    private var artwork: MPMediaItemArtwork? = null

    fun start() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.handle { queue.resume() }
        center.pauseCommand.handle { queue.pause() }
        center.togglePlayPauseCommand.handle {
            if (queue.nowPlaying.value?.playing == true) queue.pause() else queue.resume()
        }
        center.nextTrackCommand.handle { queue.skipToNext() }
        // Jako na Androidu (Media3): po třech vteřinách skladby „předchozí" skočí na její začátek.
        center.previousTrackCommand.handle {
            if (queue.positionMs > RESTART_THRESHOLD_MS || queue.nowPlaying.value?.previous == null) {
                queue.seekTo(0)
            } else {
                queue.skipToPrevious()
            }
        }
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            queue.seekTo((position * 1000).toLong())
            publish(queue.nowPlaying.value)
            MPRemoteCommandHandlerStatusSuccess
        }

        scope.launch {
            queue.nowPlaying.collect { playing ->
                publish(playing)
                refresh?.cancel()
                if (playing?.playing == true) {
                    refresh = scope.launch {
                        while (true) {
                            delay(REFRESH_MS)
                            publish(queue.nowPlaying.value)
                        }
                    }
                }
            }
        }
    }

    private fun publish(playing: NowPlaying?) {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        listOf(commands.playCommand, commands.pauseCommand, commands.togglePlayPauseCommand, commands.previousTrackCommand, commands.changePlaybackPositionCommand)
            .forEach { it.enabled = playing != null }
        commands.nextTrackCommand.enabled = playing?.next != null

        if (playing == null) {
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
            return
        }
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to playing.track.title,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to queue.positionMs / 1000.0,
            MPNowPlayingInfoPropertyPlaybackRate to if (playing.playing) playing.speed.toDouble() else 0.0,
        )
        playing.track.performer?.let { info[MPMediaItemPropertyArtist] = it }
        if (playing.durationMs > 0) info[MPMediaItemPropertyPlaybackDuration] = playing.durationMs / 1000.0
        artworkFor(playing.track.artworkPath)?.let { info[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    /** Obal se čte z disku jednou na skladbu, ne při každém hlášení pozice. */
    private fun artworkFor(path: String?): MPMediaItemArtwork? {
        if (path != artworkPath) {
            artworkPath = path
            artwork = path?.let { UIImage.imageWithContentsOfFile(it) }?.let { image ->
                MPMediaItemArtwork(boundsSize = image.size, requestHandler = { image })
            }
        }
        return artwork
    }

    private fun MPRemoteCommand.handle(action: () -> Unit) {
        addTargetWithHandler { _ ->
            action()
            MPRemoteCommandHandlerStatusSuccess
        }
    }
}

private const val REFRESH_MS = 5_000L
private const val RESTART_THRESHOLD_MS = 3_000L
