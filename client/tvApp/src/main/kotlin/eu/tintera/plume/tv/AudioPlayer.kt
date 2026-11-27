package eu.tintera.plume.tv

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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * **Soubor se napřed stáhne celý**, pak hraje: hlasovky mají pár desítek kB a skladby jednotky
 * MB, streamování (jako u videa) by tu přidalo složitost bez znatelného užitku. Dokud se
 * stahuje, dock ukazuje čekání.
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
    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        setHandleAudioBecomingNoisy(true)
    }

    /**
     * Přehrávač, jak ho vidí `MediaSession`. Fronta je v aplikaci ([queue]), ExoPlayer má vždy
     * jen jednu položku — „další" a „předchozí" by session nenabídla a tlačítka na ovladači by
     * nic neudělala (ověřeno na emulátoru: pauza šla, další ne). Obal je nabízí a posílá do fronty.
     */
    private val sessionPlayer = object : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*QUEUE_COMMANDS).build()

        override fun isCommandAvailable(command: Int): Boolean = command in QUEUE_COMMANDS || super.isCommandAvailable(command)

        override fun seekToNext() = next()

        override fun seekToNextMediaItem() = next()

        override fun seekToPrevious() = previous()

        override fun seekToPreviousMediaItem() = previous()
    }
    private val session = MediaSession.Builder(context, sessionPlayer).setId("plume-tv-audio").build()

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
                loading = false
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

    fun previous() {
        // Jako všude: po pár vteřinách přehrávání vrací na začátek, jinak na předchozí položku.
        if (player.currentPosition > RESTART_THRESHOLD_MS || index == 0) player.seekTo(0) else select(index - 1)
    }

    fun stop() {
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        index = 0
        loading = false
    }

    private fun select(newIndex: Int) {
        val item = queue.getOrNull(newIndex) ?: return
        index = newIndex
        durationMs = item.durationSeconds * 1000L
        positionMs = 0
        loading = true
        player.stop()
        loadJob?.cancel()
        loadJob = scope.launch {
            val ready = files.file(item.fileId).first { it is FileState.Ready || it is FileState.Failed }
            loading = false
            val path = (ready as? FileState.Ready)?.path ?: return@launch
            val metadata = MediaMetadata.Builder().setTitle(item.title).setArtist(item.performer).build()
            player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(File(path))).setMediaMetadata(metadata).build())
            player.prepare()
            player.play()
            if (item.unlistened) {
                conversations.openContent(item.chatId, item.messageId)
                    .onFailure { logger.w(it) { "Poslechnutí hlasovky ${item.messageId} se nenahlásilo." } }
            }
        }
    }

    fun release() {
        session.release()
        player.release()
    }
}

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
