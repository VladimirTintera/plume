package eu.tintera.plume.core.audio

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

/**
 * Most mezi [MusicQueue] a Media3: `Player`, který nic nepřehrává, jen ukazuje stav fronty
 * a předává povely z notifikace, zamykací obrazovky, sluchátek a hodinek.
 *
 * **Zvuk hraje dál [VoicePlayer], ne ExoPlayer.** Přehrávač je v aplikaci jeden pro hlasovky
 * i hudbu (hraje nejvýš jedno), a druhý přehrávač jen pro hudbu na pozadí by se s ním musel
 * domlouvat, kdo zrovna mluví. `SimpleBasePlayer` je přesně na tohle: stav se popíše
 * a Media3 si z něj složí notifikaci i systémovou relaci.
 *
 * **Playlist má nejvýš tři položky** — předchozí skladbu, hrající a další. Media3 podle nich
 * sám rozhodne, jestli „předchozí" skočí na začátek skladby (hraje-li přes tři vteřiny), nebo na
 * skladbu před ní; celý chat jako playlist by znamenal načíst všechny skladby dopředu.
 */
@OptIn(UnstableApi::class)
internal class MusicSessionPlayer(private val queue: MusicQueue) : SimpleBasePlayer(Looper.getMainLooper()) {

    private var playing: NowPlaying? = null

    fun show(nowPlaying: NowPlaying?) {
        playing = nowPlaying
        invalidateState()
    }

    override fun getState(): State {
        val now = playing ?: return State.Builder()
            .setAvailableCommands(Player.Commands.EMPTY)
            .setPlaybackState(Player.STATE_IDLE)
            .build()

        val items = buildList {
            now.previous?.let { add(neighborItem(it)) }
            add(currentItem(now))
            now.next?.let { add(neighborItem(it)) }
        }
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_RELEASE,
            )
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, now.previous != null)
            .addIf(Player.COMMAND_SEEK_TO_NEXT, now.next != null)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, now.next != null)
            .build()

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(if (now.previous != null) 1 else 0)
            // Načítání skladby je „přehrává se, jen ještě čeká na data" — tlačítko ukazuje pauzu.
            .setPlayWhenReady(now.playing || now.loading, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (now.loading) Player.STATE_BUFFERING else Player.STATE_READY)
            // Pozice se čte živě, až ji Media3 chce; ve stavu by znamenala překreslování
            // dvacetkrát za vteřinu.
            .setContentPositionMs { queue.positionMs }
            .setPlaybackParameters(PlaybackParameters(now.speed))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) queue.resume() else queue.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val now = playing ?: return Futures.immediateVoidFuture()
        val currentIndex = if (now.previous != null) 1 else 0
        when {
            mediaItemIndex > currentIndex -> queue.skipToNext()
            mediaItemIndex < currentIndex -> queue.skipToPrevious()
            else -> queue.seekTo(if (positionMs == C.TIME_UNSET) 0L else positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        queue.stop()
        return Futures.immediateVoidFuture()
    }

    /** Relace končí se službou; hudba samotná patří [MusicQueue] a ta žije dál. */
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private fun currentItem(now: NowPlaying): MediaItemData {
        val metadata = MediaMetadata.Builder()
            .setTitle(now.track.title)
            .setArtist(now.track.performer)
            .setArtworkUri(now.track.artworkPath?.let { Uri.fromFile(File(it)) })
            .build()
        return MediaItemData.Builder(now.key.uid)
            .setMediaItem(MediaItem.Builder().setMediaId(now.key.uid).setMediaMetadata(metadata).build())
            .setDurationUs(if (now.durationMs > 0) now.durationMs * 1000 else C.TIME_UNSET)
            .setIsSeekable(!now.loading)
            .build()
    }

    /** Soused je jen místo v playlistu — co je zač, se ukáže, až začne hrát. */
    private fun neighborItem(key: VoiceKey): MediaItemData = MediaItemData.Builder(key.uid)
        .setMediaItem(MediaItem.Builder().setMediaId(key.uid).build())
        .build()
}

private val VoiceKey.uid: String get() = "$chatId:$messageId"
