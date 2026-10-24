package eu.tintera.plume.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.resolveNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WearRecentChats")

/** Chat na dlaždici: texty už přeložené, fotka jako hotová miniatura na disku. */
data class RecentChat(
    val id: Long,
    val title: String,
    val unread: Int,
    /** Náhled poslední zprávy; ukazuje se, když nic nečeká. */
    val preview: String?,
    /** Cesta k miniatuře ([RecentChatsPublisher]), nebo `null` — pak iniciála. */
    val avatarPath: String?,
)

/**
 * Co ukazuje dlaždice s chaty. Stejně jako u počtu nepřečtených ([UnreadState]) se systém ptá
 * kdykoli, i bez běžícího procesu, takže podklady leží v souboru.
 */
sealed interface RecentChatsState {
    data object Unknown : RecentChatsState

    data object LoggedOut : RecentChatsState

    data class Chats(val chats: List<RecentChat>) : RecentChatsState

    companion object {
        fun read(context: Context): RecentChatsState {
            val raw = prefs(context).getString(KEY, null) ?: return Unknown
            return runCatching {
                val json = JSONObject(raw)
                if (!json.optBoolean("loggedIn")) return LoggedOut
                val items = json.getJSONArray("chats")
                Chats(
                    List(items.length()) { index ->
                        val item = items.getJSONObject(index)
                        RecentChat(
                            id = item.getLong("id"),
                            title = item.getString("title"),
                            unread = item.getInt("unread"),
                            preview = item.optString("preview").takeIf { item.has("preview") },
                            avatarPath = item.optString("avatar").takeIf { item.has("avatar") },
                        )
                    },
                )
            }.getOrElse { Unknown }
        }

        internal fun write(context: Context, state: RecentChatsState) {
            val json = JSONObject()
            json.put("loggedIn", state is Chats)
            if (state is Chats) {
                json.put(
                    "chats",
                    JSONArray(
                        state.chats.map { chat ->
                            JSONObject()
                                .put("id", chat.id)
                                .put("title", chat.title)
                                .put("unread", chat.unread)
                                .apply { chat.preview?.let { put("preview", it) } }
                                .apply { chat.avatarPath?.let { put("avatar", it) } }
                        },
                    ),
                )
            }
            prefs(context).edit { putString(KEY, json.toString()) }
        }

        private fun prefs(context: Context) = context.getSharedPreferences("wear_recent_chats", Context.MODE_PRIVATE)

        private const val KEY = "state"
    }
}

/**
 * Zapisuje chaty pro dlaždici a žádá systém o její obnovu.
 *
 * - **Chaty jsou začátek hlavního seznamu** — připnuté, pak podle poslední zprávy, tedy totéž,
 *   co uživatel vidí nahoře v aplikaci. Vlastní pořadí by se s aplikací rozcházelo.
 * - **Fotky jsou miniatury připravené při zápisu** (`filesDir/tile/avatars/<fileId>.png`,
 *   [AVATAR_PX]), jako u widgetu telefonu: dlaždice se kreslí bez TDLib a plná fotka by do
 *   popisu dlaždice nepatřila. Na stažení se čeká nejvýš [AVATAR_TIMEOUT] — dlaždice s iniciálou
 *   je lepší než dlaždice, která se neobnoví. Jméno podle `fileId`: nová fotka chatu má nové id.
 * - **Zapisuje se jen v jasných stavech** a po vteřině klidu (viz [UnreadCountPublisher]).
 */
class RecentChatsPublisher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val files: FileRepository,
) {

    @OptIn(FlowPreview::class)
    fun start(session: TelegramSession, chats: ChatRepository) {
        scope.launch {
            combine(session.authStage, chats.chats()) { stage, list ->
                when {
                    stage == AuthStage.Ready && list is ChatListState.Ready -> Input.Top(list.chats.take(MAX_CHATS))
                    stage.isLoggedOut() -> Input.LoggedOut
                    else -> null
                }
            }
                .filterNotNull()
                // Seznam se mění s každou zprávou kdekoli; dlaždici zajímá jen to, co ukazuje.
                .map { input -> input to (input as? Input.Top)?.chats?.map { it.tileKey() } }
                .distinctUntilChanged { old, new -> old.second == new.second && old.first::class == new.first::class }
                .debounce(1.seconds)
                .collect { (input, _) ->
                    val state = when (input) {
                        Input.LoggedOut -> RecentChatsState.LoggedOut
                        is Input.Top -> RecentChatsState.Chats(input.chats.map { it.toRecent() })
                    }
                    RecentChatsState.write(context, state)
                    cleanUpAvatars(state)
                    runCatching { TileService.getUpdater(context).requestUpdate(RecentChatsTileService::class.java) }
                        .onFailure { logger.w(it) { "Obnova dlaždice s chaty se nepodařila." } }
                }
        }
    }

    /** Co z chatu dlaždice ukazuje; změna čehokoli jiného (online stav, koncept…) ji neobnoví. */
    private fun ChatSummary.tileKey() = listOf(id, title, unreadCount, lastMessagePreview, avatarFileId)

    private suspend fun ChatSummary.toRecent() = RecentChat(
        id = id,
        title = title,
        unread = unreadCount,
        // Dlaždice má na náhled jeden řádek; zalomení z víceřádkové zprávy by ho uťalo hned za prvním.
        preview = lastMessagePreview?.resolveNow()?.replace(Regex("\\s+"), " ")?.trim(),
        avatarPath = avatarFileId?.let { avatar(it) },
    )

    private suspend fun avatar(fileId: Int): String? {
        val target = File(avatarDir(), "$fileId.png")
        if (target.exists()) return target.path
        val source = withTimeoutOrNull(AVATAR_TIMEOUT) {
            files.file(fileId).first { it is FileState.Ready } as FileState.Ready
        } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeSquare(source.path, AVATAR_PX) ?: return@runCatching null
                target.parentFile?.mkdirs()
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                target.path
            }.onFailure { logger.w(it) { "Miniatura fotky chatu pro dlaždici se nepodařila." } }.getOrNull()
        }
    }

    private fun cleanUpAvatars(state: RecentChatsState) {
        val used = (state as? RecentChatsState.Chats)?.chats?.mapNotNull { it.avatarPath }?.toSet().orEmpty()
        avatarDir().listFiles()?.filter { it.path !in used }?.forEach { it.delete() }
    }

    private fun avatarDir() = File(context.filesDir, "tile/avatars")

    private sealed interface Input {
        data object LoggedOut : Input

        data class Top(val chats: List<ChatSummary>) : Input
    }

    private companion object {
        const val MAX_CHATS = 3
        const val AVATAR_PX = 96
        val AVATAR_TIMEOUT = 5.seconds
    }
}

/** Čtverec ze středu obrázku, zmenšený na [sizePx]; dekóduje se rovnou zmenšený (mocninou dvou). */
private fun decodeSquare(path: String, sizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val side = minOf(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    return Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
}
