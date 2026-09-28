package eu.tintera.plume.core.audio

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MusicNeighbors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Co se o skladbě ukazuje v systémovém ovládání (notifikace, zamykací obrazovka). */
data class TrackInfo(
    val title: String,
    val performer: String?,
    /** Obal alba jako soubor na disku; dorazí až po stažení, do té doby `null`. */
    val artworkPath: String? = null,
)

/**
 * Hrající skladba pro systémové ovládání. Pozice v ní NENÍ — mění se dvacetkrát za vteřinu,
 * a ovládání si ji čte samo ([MusicQueue.positionMs]); tenhle stav se mění jen, když se opravdu
 * něco stane (spuštění, pauza, další skladba), takže podle něj jde překreslovat notifikaci.
 */
data class NowPlaying(
    val key: VoiceKey,
    val track: TrackInfo,
    val loading: Boolean,
    val playing: Boolean,
    val durationMs: Long,
    val speed: Float,
    val previous: VoiceKey?,
    val next: VoiceKey?,
)

/**
 * Hudba v aplikaci: co hraje, co je před tím a za tím, a ovládání ze systému.
 *
 * **Skladba hraje dál i po odchodu z konverzace**, proto fronta nemůže bydlet v jejím
 * ViewModelu (tam byla, dokud se hudba při odchodu zastavovala). Sousední skladby se hledají
 * v chatu ([ConversationRepository.musicNeighbors]), ne v načtené historii obrazovky — ta po
 * odchodu neexistuje.
 *
 * Zvuk dál hraje [VoicePlayer] — v aplikaci je jeden, hlasovka skladbu zastaví a naopak.
 * Tahle třída jen přidává to, co je navíc u hudby: popis skladby, sousedy a navázání další
 * skladby po dohrání.
 */
class MusicQueue(
    private val scope: CoroutineScope,
    private val player: VoicePlayer,
    private val conversations: ConversationRepository,
    private val files: FileRepository,
) {

    /**
     * [advancing]: skladba se právě spouští a přehrávač ji ještě nemá (dohrála předchozí, hledá
     * se další, stahuje se). Po tu dobu je stav přehrávače prázdný, a přesto hudba NESKONČILA —
     * systémové ovládání nesmí zmizet. Na Androidu by s notifikací odešla i služba v popředí
     * a z pozadí ji systém spustit znovu nedovolí.
     */
    private data class Current(
        val key: VoiceKey,
        val track: TrackInfo,
        val neighbors: MusicNeighbors? = null,
        val advancing: Boolean = false,
    )

    private val current = MutableStateFlow<Current?>(null)
    private var detailsJob: Job? = null
    private val logger = Logger.withTag("MusicQueue")

    /** Hrající skladba, `null`, když hudba nehraje (hraje hlasovka, nebo nic). */
    val nowPlaying: StateFlow<NowPlaying?> = combine(player.state, current, player.speed) { playback, current, speed ->
        if (current == null) return@combine null
        val own = playback?.takeIf { it.key == current.key && it.music }
        if (own == null && !current.advancing) return@combine null
        NowPlaying(
            key = current.key,
            track = current.track,
            loading = own?.loading ?: true,
            playing = own?.playing ?: false,
            durationMs = own?.durationMs ?: 0L,
            speed = speed.rate,
            previous = current.neighbors?.previous?.id?.let { VoiceKey(current.key.chatId, it) },
            next = current.neighbors?.next?.id?.let { VoiceKey(current.key.chatId, it) },
        )
    }
        // Pozice se mění dvacetkrát za vteřinu, a ta v tomhle stavu není — beze změny nic dál.
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Pozice v hrající skladbě; systémové ovládání si ji čte, kdy potřebuje. */
    val positionMs: Long get() = player.state.value?.positionMs ?: 0L

    init {
        // Skladba, kterou přehrávač přestal hrát bez navázání (zastavila ji hlasovka, video,
        // nahrávání), z fronty odchází — jinak by ovládání čekalo na něco, co nepřijde.
        scope.launch {
            player.state.collect { playback ->
                val playing = current.value ?: return@collect
                when {
                    playback?.key == playing.key -> if (playing.advancing) current.value = playing.copy(advancing = false)
                    playback == null && playing.advancing -> Unit
                    else -> current.value = null
                }
            }
        }
    }

    /** Skladba z bubliny nebo ze záložky Hudba: pustí ji, nebo u té vybrané přepne pauzu. */
    fun toggle(chatId: Long, messageId: Long, audio: MessageMedia.Audio) {
        val key = VoiceKey(chatId, messageId)
        val playback = player.state.value
        if (playback?.key == key) {
            when {
                playback.loading -> Unit
                playback.playing -> player.pause()
                else -> player.resume()
            }
            return
        }
        play(key, audio)
    }

    /** Pustí skladbu od začátku, nebo od [startMs] (čas v médiu, „1:23"). */
    fun play(chatId: Long, messageId: Long, audio: MessageMedia.Audio, startMs: Long = 0L) =
        play(VoiceKey(chatId, messageId), audio, startMs)

    fun pause() = player.pause()

    fun resume() = player.resume()

    fun seekTo(positionMs: Long) {
        val key = current.value?.key ?: return
        player.seekTo(key, positionMs)
    }

    fun stop() {
        if (player.state.value?.music == true) player.stop()
        current.value = null
    }

    fun skipToNext() = skipToNext(finishedKey = null)

    fun skipToPrevious() {
        val chatId = current.value?.key?.chatId ?: return
        val previous = current.value?.neighbors?.previous ?: return
        val audio = previous.media as? MessageMedia.Audio ?: return
        play(VoiceKey(chatId, previous.id), audio)
    }

    /**
     * Další skladba v chatu. Po dohrání ([finishedKey]) se na sousedy ještě počká — skladba
     * kratší než dotaz na ně by jinak skončila bez navázání.
     */
    private fun skipToNext(finishedKey: VoiceKey?) {
        val playing = current.value ?: return
        if (finishedKey != null) current.value = playing.copy(advancing = true)
        scope.launch {
            val neighbors = playing.neighbors ?: conversations.musicNeighbors(playing.key.chatId, playing.key.messageId)
                .onFailure { logger.w(it) { "Další skladbu nejde najít." } }
                .getOrNull()
            // Mezitím mohl uživatel pustit něco jiného.
            if (current.value?.key != playing.key) return@launch
            if (finishedKey == null && player.state.value?.key != playing.key) return@launch
            val next = neighbors?.next
            val audio = next?.media as? MessageMedia.Audio
            if (next == null || audio == null) {
                // Po dohrání poslední skladby není co navázat; hudba skončila.
                if (finishedKey != null) current.value = null
                return@launch
            }
            play(VoiceKey(playing.key.chatId, next.id), audio)
        }
    }

    private fun play(key: VoiceKey, audio: MessageMedia.Audio, startMs: Long = 0L) {
        val track = TrackInfo(title = audio.title, performer = audio.performer?.ifBlank { null })
        // `advancing` přes `player.play`: to napřed zastaví předchozí zvuk a stav je na chvíli
        // prázdný — bez příznaku by ho sběr stavu vzal jako konec hudby.
        current.value = Current(key, track, advancing = true)
        player.play(key, music = true, startMs = startMs, onFinished = { skipToNext(finishedKey = key) }) {
            path(audio.fileId)
        }
        loadDetails(key, audio)
    }

    /**
     * Sousedi a obal alba — obojí dorazí až po chvíli a systémové ovládání se podle toho
     * překreslí. Do té doby notifikace ukazuje skladbu bez obalu a bez tlačítka „další".
     */
    private fun loadDetails(key: VoiceKey, audio: MessageMedia.Audio) {
        detailsJob?.cancel()
        detailsJob = scope.launch {
            launch {
                conversations.musicNeighbors(key.chatId, key.messageId)
                    .onSuccess { neighbors -> current.update { if (it?.key == key) Current(key, it.track, neighbors) else it } }
                    .onFailure { logger.w(it) { "Sousední skladby nejde najít." } }
            }
            launch {
                val cover = audio.coverFileId ?: return@launch
                val path = withTimeoutOrNull(ARTWORK_TIMEOUT_MS) { runCatching { path(cover) }.getOrNull() } ?: return@launch
                current.update {
                    if (it?.key == key) Current(key, it.track.copy(artworkPath = path), it.neighbors) else it
                }
            }
        }
    }

    /** Počká na stažený soubor; stahování začne až tímhle odběrem. */
    private suspend fun path(fileId: Int): String =
        when (val file = files.file(fileId).first { it is FileState.Ready || it is FileState.Failed }) {
            is FileState.Ready -> file.path
            is FileState.Failed -> error(file.message)
            else -> error("Nedosažitelné")
        }
}

private const val ARTWORK_TIMEOUT_MS = 10_000L
