package eu.tintera.plume.core.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.NotificationIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.component.inject

/**
 * Služba, která drží hudbu naživu mimo aplikaci: MediaSession, notifikace s ovládáním
 * a běh v popředí (`foregroundServiceType="mediaPlayback"`).
 *
 * **Bez služby by hudba na pozadí přestala hrát.** Aplikace odsunutá do pozadí je v keši
 * a systém ji do pár vteřin zmrazí (viz „Notifikace" v docs/poznatky/notifikace.md) — i s přehrávačem. Služba
 * v popředí ji drží v provozu, dokud hudba hraje; Media3 ji do popředí přepíná a zase z něj
 * pouští sám, podle stavu přehrávače.
 *
 * Deklarovaná je v manifestu vstupního bodu (`:client:androidApp`), jako ostatní komponenty;
 * spouští ji [MusicControls], když začne hrát hudba.
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService(), KoinComponent {

    override fun getKoin() = GlobalContext.get().primaryAccount()

    private val queue: MusicQueue by inject()
    private val scope = MainScope()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        val player = MusicSessionPlayer(queue)
        val created = MediaSession.Builder(this, player).build()
        session = created
        // Relace vytvořená ve službě se do ní sama nepřidá — bez toho by nevznikla notifikace,
        // dokud se nepřipojí cizí ovladač.
        addSession(created)

        scope.launch {
            queue.nowPlaying.collect { playing ->
                player.show(playing)
                // Hudba skončila: notifikace zmizela s prázdným playlistem, služba nemá co držet.
                if (playing == null) stopSelf()
            }
        }
        // Ťuknutí na notifikaci otevře chat, ze kterého skladba hraje.
        scope.launch {
            queue.nowPlaying.map { it?.key?.chatId }.distinctUntilChanged().collect { chatId ->
                chatId?.let { openChat(it) }?.let(created::setSessionActivity)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun openChat(chatId: Long): PendingIntent? {
        val intent = NotificationIntents.chatIntent(this, chatId) ?: return null
        return PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Spustí [MusicPlaybackService], když začne hrát hudba. Běží od startu procesu (`createdAtStart`),
 * jako ostatní, co musí reagovat mimo obrazovky.
 *
 * Spouští se obyčejným `startService`, ne `startForegroundService`: hudba začíná ťuknutím,
 * tedy s aplikací v popředí, a do popředí službu přepne Media3, až skladba hraje. Kdyby se
 * přesto startovalo z pozadí, systém to odmítne výjimkou — ta se jen zaloguje.
 */
class MusicControls(private val context: Context, private val queue: MusicQueue, private val scope: CoroutineScope) {

    private val logger = Logger.withTag("MusicControls")

    fun start() {
        scope.launch {
            queue.nowPlaying.map { it != null }.distinctUntilChanged().collect { active ->
                if (!active) return@collect
                runCatching { context.startService(Intent(context, MusicPlaybackService::class.java)) }
                    .onFailure { failure -> logger.w(failure) { "Službu pro hudbu nejde spustit." } }
            }
        }
    }
}
