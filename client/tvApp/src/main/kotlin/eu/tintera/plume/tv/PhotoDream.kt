package eu.tintera.plume.tv

import android.content.Context
import android.service.dreams.DreamService
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.tv_dream_empty
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Spořič obrazovky televize s fotkami z Telegramu: Uložené zprávy, nebo chat vybraný v nastavení
 * spořiče ([DreamSettingsActivity]). Fotka po fotce s pomalým přiblížením a prolnutím, vpravo
 * dole hodiny.
 *
 * `DreamService` není `LifecycleOwner` ani `SavedStateRegistryOwner`, které `ComposeView` chce
 * najít ve stromu oken — spořič je proto dává sám a vede jejich stav podle snění.
 */
class PhotoDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Neinteraktivní: první stisk na ovladači spořič ukončí, jako u systémového.
        isInteractive = false
        isFullscreen = true
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        setContentView(
            ComposeView(this).apply {
                setContent { PrimaryAccountScope { TvTheme { PhotoDream(DreamSettings(context).chatId) } } }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}

/** Odkud spořič bere fotky; `null` = Uložené zprávy. */
class DreamSettings(context: Context) {
    private val prefs = context.getSharedPreferences("photo_dream", Context.MODE_PRIVATE)

    var chatId: Long?
        get() = prefs.getLong(KEY_CHAT, 0L).takeIf { it != 0L }
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_CHAT) else putLong(KEY_CHAT, value) }.apply()
        }

    private companion object {
        const val KEY_CHAT = "chat_id"
    }
}

/**
 * Obsah spořiče. Bez přihlášení (nebo dokud TDLib nenaběhne) jen hodiny — spořič se spouští
 * i po startu televize, kdy aplikace ještě nemusí být připojená.
 */
@Composable
fun PhotoDream(chosenChatId: Long?) {
    val stage by koinInject<TelegramSession>().authStage.collectAsState()
    val savedMessagesId by koinInject<ChatRepository>().savedMessagesChatId.collectAsState()
    val chatId = chosenChatId ?: savedMessagesId
    val conversations = koinInject<ConversationRepository>()
    val photos by produceState<List<MessageMedia.Photo>?>(null, stage == AuthStage.Ready, chatId) {
        value = null
        if (stage == AuthStage.Ready && chatId != null) value = loadDreamPhotos(conversations, chatId)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val shown = photos
        when {
            shown == null -> Unit
            shown.isEmpty() -> Text(
                stringResource(Res.string.tv_dream_empty),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(48.dp),
            )
            else -> Slideshow(shown)
        }
        DreamClock(Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun Slideshow(photos: List<MessageMedia.Photo>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(photos) {
        while (true) {
            delay(PHOTO_MS)
            index = (index + 1) % photos.size
        }
    }
    val targetPx = with(LocalDensity.current) { PHOTO_HEIGHT.roundToPx() }
    // Další fotka se stáhne a dekóduje předem, ať prolnutí nečeká na síť.
    rememberImage(photos[(index + 1) % photos.size].fullFileId, targetPx)
    Crossfade(targetState = photos[index], animationSpec = tween(FADE_MS), label = "dream-photo") { photo ->
        val image = rememberImage(photo.fullFileId, targetPx) ?: rememberImage(photo.fileId, targetPx)
        // Pomalé přiblížení (efekt Kena Burnse): statický obraz by na OLED televizi vypaloval.
        val zoom = remember(photo) { Animatable(1f) }
        LaunchedEffect(photo) { zoom.animateTo(ZOOM, tween((PHOTO_MS + FADE_MS).toInt(), easing = LinearEasing)) }
        image?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
            )
        }
    }
}

/**
 * Hodiny a datum vpravo dole na tmavém přechodu zespodu. Každou minutu se o kousek posunou — nic na
 * obrazovce spořiče nesmí stát na místě celé hodiny.
 */
@Composable
private fun DreamClock(modifier: Modifier) {
    var now by remember { mutableStateOf(nowSeconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = nowSeconds()
        }
    }
    val minute = now / 60
    val shift = CLOCK_SHIFTS[(minute % CLOCK_SHIFTS.size).toInt()]
    Box(
        // Ztmavení přes celou šířku zespodu, nahoře z průhledné: s přechodem jen do strany
        // měla plocha nad hodinami viditelnou vodorovnou hranu (ověřeno na emulátoru).
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = SAFE_HORIZONTAL, bottom = SAFE_VERTICAL, top = 48.dp).offset(x = -shift, y = -shift),
        ) {
            Text(now.asTimeOfDay(), style = MaterialTheme.typography.displaySmall.tabular(), color = Color.White)
            Text(now.asDate(), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/**
 * Fotky chatu [chatId] od nejnovějších, nejvýš [MAX_PHOTOS], v náhodném pořadí. Bez spoileru,
 * citlivého obsahu a mizejících fotek: spořič běží v obýváku pro každého, kdo se dívá.
 */
private suspend fun loadDreamPhotos(conversations: ConversationRepository, chatId: Long): List<MessageMedia.Photo> {
    val photos = mutableListOf<MessageMedia.Photo>()
    var from = 0L
    for (round in 0 until MAX_PAGES) {
        val page = conversations.sharedMedia(chatId, SharedMediaKind.Media, fromMessageId = from).getOrNull() ?: break
        photos += page.messages.mapNotNull { message ->
            (message.media as? MessageMedia.Photo)?.takeIf { !it.hasSpoiler && !it.isSensitive && !it.isSecret && it.selfDestructType == null }
        }
        if (page.reachedEnd || photos.size >= MAX_PHOTOS) break
        from = page.nextFromMessageId
    }
    return photos.take(MAX_PHOTOS).shuffled()
}

/** Jak dlouho stojí jedna fotka a jak dlouho se prolíná s další. */
private const val PHOTO_MS = 10_000L
private const val FADE_MS = 1_500

/** O kolik se fotka za dobu svého zobrazení přiblíží. */
private const val ZOOM = 1.08f
private val PHOTO_HEIGHT = 1080.dp / 2

private const val MAX_PHOTOS = 200
private const val MAX_PAGES = 10

/** Posuny hodin po minutách (proti vypálení). */
private val CLOCK_SHIFTS = listOf(0.dp, 6.dp, 12.dp, 6.dp)
