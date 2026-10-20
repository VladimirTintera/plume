package eu.tintera.plume.core.audio

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MusicNeighbors
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration
import kotlin.time.TimeSource
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
    /** Obal jako soubor TDLib — pro přehrávač v aplikaci, který si ho stáhne sám. */
    val coverFileId: Int? = null,
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
 * Skladba, kterou fronta umí pustit: [id] v jejím zdroji (zpráva v chatu, nebo pořadí ve vlastním
 * seznamu, viz [MusicQueue.togglePlaylist]) a co hrát.
 */
data class QueueTrack(val id: Long, val audio: MessageMedia.Audio)

/**
 * Co z fronty hudby potřebuje konverzace ([MusicQueue]); rozhraní kvůli testům, jako
 * [VoicePlayerControl].
 */
interface MusicQueueControl {
    val nowPlaying: StateFlow<NowPlaying?>

    fun toggle(chatId: Long, messageId: Long, audio: MessageMedia.Audio, rememberPosition: Boolean = true)

    fun play(chatId: Long, messageId: Long, audio: MessageMedia.Audio, startMs: Long = 0L, rememberPosition: Boolean = true)

    fun stop()

    fun skipToNext()
}

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
/** Počátek hodin pro [ListeningTracker]; na absolutním čase nezáleží, jen na rozdílech. */
private val monotonicStart = TimeSource.Monotonic.markNow()

class MusicQueue(
    private val scope: CoroutineScope,
    private val player: VoicePlayer,
    private val conversations: ConversationRepository,
    private val files: FileRepository,
    /** Hodiny pro počítání poslechu; v testech virtuální. */
    now: () -> Duration = { monotonicStart.elapsedNow() },
) : MusicQueueControl {

    /**
     * [advancing]: skladba se právě spouští a přehrávač ji ještě nemá (dohrála předchozí, hledá
     * se další, stahuje se). Po tu dobu je stav přehrávače prázdný, a přesto hudba NESKONČILA —
     * systémové ovládání nesmí zmizet. Na Androidu by s notifikací odešla i služba v popředí
     * a z pozadí ji systém spustit znovu nedovolí.
     */
    private data class Current(
        val key: VoiceKey,
        val track: TrackInfo,
        /** Sousedi jsou známí ([previous] a [next] platí, i když jsou `null` = není). */
        val neighborsKnown: Boolean = false,
        val previous: QueueTrack? = null,
        val next: QueueTrack? = null,
        val advancing: Boolean = false,
        /** Pamatovat pozici (ne v tajném chatu); platí i pro skladby, které po této navážou. */
        val rememberPosition: Boolean = true,
        /** Vlastní seznam skladeb (hudba v profilu); `null` = sousedi se hledají v chatu. */
        val playlist: List<QueueTrack>? = null,
        /** Soubor skladby — kvůli hlášení poslechu ([ListeningTracker]). */
        val fileId: Int? = null,
    )

    private val current = MutableStateFlow<Current?>(null)
    private var detailsJob: Job? = null
    private val logger = Logger.withTag("MusicQueue")

    /** Hrající skladba, `null`, když hudba nehraje (hraje hlasovka, nebo nic). */
    override val nowPlaying: StateFlow<NowPlaying?> = combine(player.state, current, player.speed) { playback, current, speed ->
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
            previous = current.previous?.id?.let { VoiceKey(current.key.chatId, it) },
            next = current.next?.id?.let { VoiceKey(current.key.chatId, it) },
        )
    }
        // Pozice se mění dvacetkrát za vteřinu, a ta v tomhle stavu není — beze změny nic dál.
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Pozice v hrající skladbě; systémové ovládání si ji čte, kdy potřebuje. */
    val positionMs: Long get() = player.state.value?.positionMs ?: 0L

    /**
     * Hlášení poslechu (`listenToAudio`): kolik skladba opravdu hrála, nahlášené po jejím konci,
     * přepnutí nebo zastavení. Selhání se jen zaloguje — nic z toho uživatel nevidí.
     */
    private val listening = ListeningTracker(now) { fileId, seconds ->
        logger.d { "Poslech skladby $fileId: $seconds s" }
        scope.launch {
            conversations.listenToAudio(fileId, seconds)
                .onFailure { logger.w(it) { "listenToAudio($fileId, $seconds s) selhalo." } }
        }
    }

    init {
        scope.launch {
            nowPlaying.collect { playing ->
                listening.update(fileId = playing?.let { current.value?.fileId }, playing = playing?.playing == true)
            }
        }
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

    /**
     * Skladba z bubliny nebo ze záložky Hudba: pustí ji, nebo u té vybrané přepne pauzu.
     *
     * Pozice se pamatuje u každé skladby, která má [MessageMedia.Audio.fileKey] — uložit ji ale
     * přehrávač nechá jen u dlouhé (podcast, přednáška), viz [VoicePlayer]. Tajný chat stopu
     * nechávat nemá, proto [rememberPosition].
     */
    override fun toggle(chatId: Long, messageId: Long, audio: MessageMedia.Audio, rememberPosition: Boolean) {
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
        play(key, audio, rememberPosition = rememberPosition)
    }

    /**
     * Skladba z vlastního seznamu [tracks] (hudba v profilu): pustí [index], nebo u hrající přepne
     * pauzu. Sousedé se berou ze seznamu, ne z chatu — skladby v profilu nejsou zprávy. Klíč je
     * `VoiceKey(chatId, track.id)`; [chatId] je chat, který otevře ťuknutí na přehrávač
     * v notifikaci, a id skladeb se nesmí potkat s id zpráv v něm (proto záporná).
     */
    fun togglePlaylist(chatId: Long, tracks: List<QueueTrack>, index: Int) {
        val track = tracks.getOrNull(index) ?: return
        val key = VoiceKey(chatId, track.id)
        val playback = player.state.value
        if (playback?.key == key) {
            when {
                playback.loading -> Unit
                playback.playing -> player.pause()
                else -> player.resume()
            }
            return
        }
        play(key, track.audio, playlist = tracks)
    }

    /** Pustí skladbu od začátku, nebo od [startMs] (čas v médiu, „1:23"). */
    override fun play(chatId: Long, messageId: Long, audio: MessageMedia.Audio, startMs: Long, rememberPosition: Boolean) =
        play(VoiceKey(chatId, messageId), audio, startMs, rememberPosition)

    fun pause() = player.pause()

    fun resume() = player.resume()

    fun seekTo(positionMs: Long) {
        val key = current.value?.key ?: return
        player.seekTo(key, positionMs)
    }

    override fun stop() {
        if (player.state.value?.music == true) player.stop()
        current.value = null
    }

    override fun skipToNext() = skipToNext(finishedKey = null)

    fun skipToPrevious() {
        val playing = current.value ?: return
        val previous = playing.previous ?: return
        play(VoiceKey(playing.key.chatId, previous.id), previous.audio, rememberPosition = playing.rememberPosition, playlist = playing.playlist)
    }

    /**
     * Další skladba v chatu. Po dohrání ([finishedKey]) se na sousedy ještě počká — skladba
     * kratší než dotaz na ně by jinak skončila bez navázání.
     */
    private fun skipToNext(finishedKey: VoiceKey?) {
        val playing = current.value ?: return
        if (finishedKey != null) current.value = playing.copy(advancing = true)
        scope.launch {
            val next = if (playing.neighborsKnown) {
                playing.next
            } else {
                conversations.musicNeighbors(playing.key.chatId, playing.key.messageId)
                    .onFailure { logger.w(it) { "Další skladbu nejde najít." } }
                    .getOrNull()
                    ?.queueTracks()
                    ?.second
            }
            // Mezitím mohl uživatel pustit něco jiného.
            if (current.value?.key != playing.key) return@launch
            if (finishedKey == null && player.state.value?.key != playing.key) return@launch
            if (next == null) {
                // Po dohrání poslední skladby není co navázat; hudba skončila.
                if (finishedKey != null) current.value = null
                return@launch
            }
            play(VoiceKey(playing.key.chatId, next.id), next.audio, rememberPosition = playing.rememberPosition, playlist = playing.playlist)
        }
    }

    private fun play(
        key: VoiceKey,
        audio: MessageMedia.Audio,
        startMs: Long = 0L,
        rememberPosition: Boolean = true,
        playlist: List<QueueTrack>? = null,
    ) {
        val track = TrackInfo(
            title = audio.title.ifBlank { audio.fileName },
            performer = audio.performer?.ifBlank { null },
            coverFileId = audio.coverFileId,
        )
        // `advancing` přes `player.play`: to napřed zastaví předchozí zvuk a stav je na chvíli
        // prázdný — bez příznaku by ho sběr stavu vzal jako konec hudby.
        // Ve vlastním seznamu jsou sousedi známí hned; v chatu je dohledá [loadDetails].
        val index = playlist?.indexOfFirst { it.id == key.messageId } ?: -1
        current.value = Current(
            key,
            track,
            neighborsKnown = playlist != null,
            previous = playlist?.getOrNull(index - 1)?.takeIf { index > 0 },
            next = playlist?.getOrNull(index + 1)?.takeIf { index >= 0 },
            advancing = true,
            rememberPosition = rememberPosition,
            playlist = playlist,
            fileId = audio.fileId,
        )
        player.play(
            key,
            music = true,
            startMs = startMs,
            resumeKey = audio.fileKey?.takeIf { rememberPosition },
            onFinished = { skipToNext(finishedKey = key) },
        ) {
            path(audio.fileId)
        }
        loadDetails(key, audio, findNeighbors = playlist == null)
    }

    /**
     * Sousedi a obal alba — obojí dorazí až po chvíli a systémové ovládání se podle toho
     * překreslí. Do té doby notifikace ukazuje skladbu bez obalu a bez tlačítka „další".
     */
    private fun loadDetails(key: VoiceKey, audio: MessageMedia.Audio, findNeighbors: Boolean) {
        detailsJob?.cancel()
        detailsJob = scope.launch {
            if (findNeighbors) launch {
                conversations.musicNeighbors(key.chatId, key.messageId)
                    .onSuccess { neighbors ->
                        val (previous, next) = neighbors.queueTracks()
                        current.update {
                            if (it?.key == key) {
                                it.copy(neighborsKnown = true, previous = previous, next = next, advancing = false)
                            } else {
                                it
                            }
                        }
                    }
                    .onFailure { logger.w(it) { "Sousední skladby nejde najít." } }
            }
            launch {
                val cover = audio.coverFileId ?: return@launch
                val path = withTimeoutOrNull(ARTWORK_TIMEOUT_MS) { runCatching { path(cover) }.getOrNull() } ?: return@launch
                current.update {
                    if (it?.key == key) it.copy(track = it.track.copy(artworkPath = path), advancing = false) else it
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

/** Sousední zprávy chatu jako skladby fronty (předchozí, další); zpráva bez skladby = žádná. */
private fun MusicNeighbors.queueTracks(): Pair<QueueTrack?, QueueTrack?> =
    previous?.let { message -> (message.media as? MessageMedia.Audio)?.let { QueueTrack(message.id, it) } } to
        next?.let { message -> (message.media as? MessageMedia.Audio)?.let { QueueTrack(message.id, it) } }

private const val ARTWORK_TIMEOUT_MS = 10_000L
