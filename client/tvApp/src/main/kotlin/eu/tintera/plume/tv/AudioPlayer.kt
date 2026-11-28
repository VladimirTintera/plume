package eu.tintera.plume.tv

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.video.PartialFileDataSource
import eu.tintera.plume.core.video.StreamingSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = Logger.withTag("TvAudio")

/** Skladba nebo hlasovka k přehrání: zpráva, soubor a co ukázat v docku. */
@Immutable
data class AudioItem(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val voice: Boolean,
    val title: String,
    val performer: String?,
    val durationSeconds: Int,
    val coverFileId: Int?,
    /** Příchozí hlasovka, kterou ještě nikdo nepustil — po spuštění se nahlásí jako poslechnutá. */
    val unlistened: Boolean,
)

/**
 * Hlasovky, nebo skladby (podle druhu [first]) ze zpráv od nejstarší, v pořadí přehrávání.
 * Hlasovky a hudba se nemíchají: po hlasovce hraje další hlasovka, po skladbě další skladba.
 */
fun audioItems(chatId: Long, messages: List<ChatMessage>, voice: Boolean): List<AudioItem> =
    messages.mapNotNull { message ->
        when (val media = message.media) {
            is MessageMedia.VoiceNote -> if (!voice) null else AudioItem(
                chatId = chatId,
                messageId = message.id,
                fileId = media.fileId,
                voice = true,
                // Prázdný = hlasovka bez jména odesílatele; dock pak ukáže „Hlasová zpráva".
                title = message.senderName.orEmpty(),
                performer = null,
                durationSeconds = media.durationSeconds,
                coverFileId = null,
                unlistened = !message.isOutgoing && !media.isListened,
            )
            is MessageMedia.Audio -> if (voice) null else AudioItem(
                chatId = chatId,
                messageId = message.id,
                fileId = media.fileId,
                voice = false,
                title = media.title.ifBlank { media.fileName },
                performer = media.performer,
                durationSeconds = media.durationSeconds,
                coverFileId = media.coverFileId,
                unlistened = false,
            )
            else -> null
        }
    }

/**
 * Přehrávač hlasovek a hudby pro celou aplikaci: jeden, aby hrál dál při přechodu mezi chaty
 * a aby se dva zvuky nepraly. Drží ExoPlayer a `MediaSession` — tlačítka médií na ovladači
 * (přehrát/pozastavit, další, předchozí) jdou do něj i bez fokusu v aplikaci a Google TV ukáže
 * kartu „Právě hraje".
 *
 * **Skladba hraje za běhu stahování** (`StreamingSource` z `core`, jako video): hodinový mix
 * nebo audiokniha mají desítky MB a dřív se čekalo na celý soubor. Přetočení za stažený úsek
 * přesune stahování tam ([StreamingSource.requestFrom]). **Hlasovka se stáhne celá**, pak
 * hraje: má pár desítek kB a stáhne se dřív, než by se stream rozběhl. Dokud se čeká, dock
 * ukazuje čekání.
 *
 * Fokus zvuku řeší ExoPlayer (`handleAudioFocus`): spuštěné video hudbu pozastaví samo.
 */
@Stable
class TvAudioPlayer(
    context: Context,
    private val files: FileRepository,
    private val conversations: ConversationRepository,
    private val scope: CoroutineScope,
) {
    // Přetáčení z ovladače (MEDIA_FAST_FORWARD / REWIND přes `MediaSession`) o tentýž krok
    // jako šipky na bublině skladby ([seekBy]).
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(SEEK_AUDIO_MS)
        .setSeekForwardIncrementMs(SEEK_AUDIO_MS)
        .build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        setHandleAudioBecomingNoisy(true)
    }

    private val sessionPlayer = QueueSessionPlayer()
    private val session = MediaSession.Builder(context, sessionPlayer).setId("plume-tv-audio").build()

    /**
     * Přehrávač, jak ho vidí `MediaSession`. Fronta je v aplikaci ([queue]), ExoPlayer má vždy
     * jen jednu položku — „další" a „předchozí" by session nenabídla a tlačítka na ovladači by
     * nic neudělala (ověřeno na emulátoru: pauza šla, další ne). Obal je nabízí a posílá do fronty.
     *
     * Třída, ne anonymní objekt: `@OptIn` u vlastnosti tělo objektu nepokryje a lint dál hlásil
     * `UnsafeOptInUsageError` (`ForwardingPlayer` je v media3 „UnstableApi“, jako na telefonu
     * `MusicSessionPlayer`).
     */
    @OptIn(UnstableApi::class)
    private inner class QueueSessionPlayer : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*QUEUE_COMMANDS).build()

        override fun isCommandAvailable(command: Int): Boolean = command in QUEUE_COMMANDS || super.isCommandAvailable(command)

        override fun seekToNext() = next()

        override fun seekToNextMediaItem() = next()

        override fun seekToPrevious() = previous()

        override fun seekToPreviousMediaItem() = previous()
    }

    var queue by mutableStateOf<List<AudioItem>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    val current: AudioItem? get() = queue.getOrNull(index)
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    private var loadJob: Job? = null

    /** Soubor, který se pro přehrávání stahuje; při přepnutí jinam se stahování zruší. */
    private var downloadingFileId: Int? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { durationMs = it }
                // Dohraná položka pustí další z fronty (hlasovky po sobě, jako na telefonu).
                if (state == Player.STATE_ENDED) {
                    if (index < queue.lastIndex) select(index + 1) else stop()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                logger.w(error) { "Zvuk ${current?.messageId} na televizi nejde přehrát." }
                skipUnplayable()
            }
        })
        scope.launch {
            while (true) {
                positionMs = player.currentPosition.coerceAtLeast(0)
                delay(250)
            }
        }
    }

    /** Pustí [items] od [startIndex]; když už hraje právě tahle zpráva, jen přepne pauzu. */
    fun play(items: List<AudioItem>, startIndex: Int) {
        val target = items.getOrNull(startIndex) ?: return
        if (current?.messageId == target.messageId && current?.chatId == target.chatId) {
            toggle()
            return
        }
        queue = items
        select(startIndex)
    }

    fun toggle() {
        if (current == null) return
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (index < queue.lastIndex) select(index + 1)
    }

    /**
     * Přetočí právě hrající položku o [deltaMs] (záporné = zpět). Dopředu nejvýš vteřinu před
     * konec: skok na samý konec by položku dohrál a pustil další z fronty, takže by opakované
     * šipky vpravo nečekaně přeskakovaly na další hlasovky. Dokud se soubor stahuje, nic.
     */
    fun seekBy(deltaMs: Long) {
        if (current == null || loading) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: durationMs
        val target = (player.currentPosition + deltaMs).coerceIn(0, (duration - SEEK_END_MARGIN_MS).coerceAtLeast(0))
        player.seekTo(target)
        positionMs = target
    }

    fun previous() {
        // Jako všude: po pár vteřinách přehrávání vrací na začátek, jinak na předchozí položku.
        if (player.currentPosition > RESTART_THRESHOLD_MS || index == 0) player.seekTo(0) else select(index - 1)
    }

    fun stop() {
        loadJob?.cancel()
        abandonDownload()
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        index = 0
        loading = false
    }

    @OptIn(UnstableApi::class)
    private fun select(newIndex: Int) {
        val item = queue.getOrNull(newIndex) ?: return
        index = newIndex
        durationMs = item.durationSeconds * 1000L
        positionMs = 0
        loading = true
        player.stop()
        loadJob?.cancel()
        if (downloadingFileId != item.fileId) abandonDownload()
        downloadingFileId = item.fileId
        loadJob = scope.launch {
            // Stav souboru čte vlákno přehrávače (zdroj streamu), proto `MutableStateFlow`;
            // odběr běží, dokud položka hraje (zruší ho další `select`).
            val fileState = MutableStateFlow<FileState>(FileState.Pending)
            launch { files.file(item.fileId).collect { fileState.value = it } }
            val first = fileState.first { state ->
                state is FileState.Ready || state is FileState.Failed ||
                    (!item.voice && state is FileState.Downloading && state.path != null && state.totalSize > 0)
            }
            loading = false
            val metadata = MediaMetadata.Builder().setTitle(item.title).setArtist(item.performer).build()
            when (first) {
                is FileState.Ready ->
                    player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(File(first.path))).setMediaMetadata(metadata).build())
                is FileState.Downloading -> {
                    val source = StreamingSource(
                        // Hotový soubor TDLib přesune z `temp/`; zdroj si cestu čte při každém otevření.
                        path = { fileState.value.currentPath() ?: first.path.orEmpty() },
                        totalSize = first.totalSize,
                        downloaded = {
                            when (val state = fileState.value) {
                                is FileState.Downloading -> state.downloaded
                                is FileState.Ready -> 0L until Long.MAX_VALUE
                                else -> LongRange.EMPTY
                            }
                        },
                        requestFrom = { offset -> scope.launch { files.downloadFrom(item.fileId, offset) } },
                    )
                    val factory = DataSource.Factory { PartialFileDataSource(source) }
                    val mediaItem = MediaItem.Builder().setUri(Uri.parse("tdlib://${first.path}")).setMediaMetadata(metadata).build()
                    player.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem))
                }
                else -> {
                    logger.w { "Zvuk ${item.messageId} se na televizi nestáhl." }
                    skipUnplayable()
                    return@launch
                }
            }
            player.prepare()
            player.play()
            if (item.unlistened) {
                conversations.openContent(item.chatId, item.messageId)
                    .onFailure { logger.w(it) { "Poslechnutí hlasovky ${item.messageId} se nenahlásilo." } }
            }
        }
    }

    /**
     * Zruší stahování souboru, od kterého se odešlo (jiná skladba, konec fronty), je-li ještě
     * rozstahovaný: odběr `files.file` stahování spustil a sám ho nezruší. Se streamováním se
     * hodinová skladba, kterou uživatel po minutě přeskočil, jinak stahovala dál celá — a televize
     * mívají kolem 8 GB.
     */
    private fun abandonDownload() {
        val fileId = downloadingFileId ?: return
        downloadingFileId = null
        scope.launch {
            if (files.observe(fileId).first() is FileState.Downloading) files.cancelDownload(fileId)
        }
    }

    /**
     * Položku, která nejde stáhnout ani přehrát, přeskočí na další, a na konci fronty přehrávání
     * ukončí. Jinak by zůstala „hrající" v docku s tlačítkem, které nic nedělá.
     */
    private fun skipUnplayable() {
        loading = false
        if (index < queue.lastIndex) select(index + 1) else stop()
    }

    fun release() {
        session.release()
        player.release()
    }
}

/** Krok přetáčení: skladba o 10 s, hlasovka (desítky vteřin) o 5 s. */
const val SEEK_AUDIO_MS = 10_000L
const val SEEK_VOICE_MS = 5_000L
private const val SEEK_END_MARGIN_MS = 1_000L

/** Přeskakování ve frontě, které session nabízí navíc ([TvAudioPlayer.sessionPlayer]). */
private val QUEUE_COMMANDS = intArrayOf(
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
)

/** Po kolika ms přehrávání vrací „předchozí" na začátek položky místo na předchozí. */
private const val RESTART_THRESHOLD_MS = 3_000L

/** Přehrávač zvuku aplikace; `null` mimo [ProvideAudioPlayer] (přihlášení). */
val LocalAudioPlayer = staticCompositionLocalOf<TvAudioPlayer?> { null }

/** Založí přehrávač zvuku na dobu přihlášení a dá ho obsahu přes [LocalAudioPlayer]. */
@Composable
fun rememberTvAudioPlayer(): TvAudioPlayer {
    val context = LocalContext.current
    val files = koinInject<FileRepository>()
    val conversations = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    val player = remember { TvAudioPlayer(context.applicationContext, files, conversations, scope) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
