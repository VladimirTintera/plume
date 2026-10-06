package eu.tintera.plume.features.chats

import androidx.lifecycle.ViewModel
import eu.tintera.plume.core.audio.MusicQueue
import eu.tintera.plume.core.audio.NowPlaying
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayer
import kotlinx.coroutines.flow.StateFlow

/**
 * Přehrávač nad seznamem chatů: hudba, která hraje dál po odchodu z konverzace.
 *
 * Jen hudba — hlasovku odchod z chatu zastaví (`ConversationViewModel.onCleared`), takže se
 * v seznamu chatů nikdy neozve. Fronta i přehrávač jsou společné s konverzací a systémovým
 * ovládáním; tady se jen čtou a ovládají.
 */
class NowPlayingViewModel(
    private val music: MusicQueue,
    private val player: VoicePlayer,
) : ViewModel() {

    val nowPlaying: StateFlow<NowPlaying?> = music.nowPlaying

    /** Průběh pro řádek; mění se dvacetkrát za vteřinu, čte ho až kreslení. */
    val playback: StateFlow<VoicePlayback?> = player.state

    fun togglePlaying() {
        val playing = player.state.value ?: return
        when {
            playing.loading -> Unit
            playing.playing -> music.pause()
            else -> music.resume()
        }
    }

    fun skipToNext() = music.skipToNext()

    fun stop() = music.stop()
}
