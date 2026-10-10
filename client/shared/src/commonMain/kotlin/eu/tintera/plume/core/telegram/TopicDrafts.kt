package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.MessageTopicForum
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Rozepsaná zpráva v tématu skupiny — položka přehledu konceptů vedle konceptů celých chatů. */
data class TopicDraft(
    val chat: ChatSummary,
    val topicId: Int,
    val topicName: String,
    val draft: ChatDraft,
)

/**
 * Koncepty v tématech fóra napříč skupinami. `Chat.draftMessage` nese jen koncept celého chatu;
 * koncept tématu je v `ForumTopic.draftMessage` a TDLib ho hlásí `UpdateForumTopic` — jenže jen
 * u témat, která zná. Proto se napřed projdou témata skupin.
 */
interface TopicDrafts {

    /** Nejčerstvější první. Skupiny se procházejí, až když se seznam čte (přehled, počet v menu). */
    val drafts: Flow<List<TopicDraft>>

    /** Smaže koncept tématu; vrátí, čím ho vrátit celý (jako [ChatRepository.deleteDraft]). */
    suspend fun delete(chatId: Long, topicId: Int): Result<DraftRestore?>
}

/**
 * [TopicDrafts] nad TDLib.
 *
 * **Musí vznikat eagerně**: `UpdateForumTopic` chodí i bez otevřeného přehledu (koncept psaný
 * na jiném zařízení) a při líné konstrukci by se ztratil.
 *
 * - **Skupina se prochází jednou za přihlášení** (`getForumTopics`, první stránka po 100
 *   tématech, pořadí podle poslední aktivity) — pak už stav drží updaty. Koncept v tématu
 *   hlouběji než stovka chybí, dokud se nezmění.
 * - **Název tématu** nese `UpdateForumTopicInfo`; pro téma, které přišlo jen updatem konceptu,
 *   se dočte `getForumTopic`. To po odpovědi pošle `UpdateForumTopic` i beze změny (viz
 *   `TdlForumRepository.topicChanges`) — dočítá se proto jen téma BEZ známého názvu, jinak by se
 *   čtení a update honily dokola.
 */
class TdlTopicDrafts(
    private val holder: TelegramClientHolder,
    chats: ChatRepository,
    private val scope: CoroutineScope,
) : TopicDrafts {

    private val logger = Logger.withTag("Chats")

    private val client: TdlClient get() = holder.client

    private data class Key(val chatId: Long, val topicId: Int)

    private data class Entry(val name: String, val draft: ChatDraft)

    private val entries = MutableStateFlow<Map<Key, Entry>>(emptyMap())

    /** Skupiny, jejichž témata už se prošla. Jen pod [mutex]. */
    private val scanned = mutableSetOf<Long>()
    private val mutex = Mutex()

    init {
        scope.launch {
            holder.updates { forumTopicUpdates }.collect { update ->
                val key = Key(update.chatId, update.forumTopicId)
                val draft = update.draftMessage?.toChatDraft()
                if (draft == null) {
                    entries.update { it - key }
                    return@collect
                }
                val name = entries.value[key]?.name
                    ?: client.getForumTopic(chatId = update.chatId, forumTopicId = update.forumTopicId).toResult()
                        .getOrNull()?.info?.name
                    ?: return@collect
                entries.update { it + (key to Entry(name, draft)) }
            }
        }
        scope.launch {
            holder.updates { forumTopicInfoUpdates }.collect { update ->
                val key = Key(update.info.chatId, update.info.forumTopicId)
                entries.update { current ->
                    val entry = current[key] ?: return@update current
                    current + (key to entry.copy(name = update.info.name))
                }
            }
        }
        // Nový účet ohlásí svoje; koncepty odhlášeného nesmí zůstat ani v přehledu, ani v počtu.
        scope.launch {
            holder.replaced.collect {
                mutex.withLock { scanned.clear() }
                entries.value = emptyMap()
            }
        }
    }

    override val drafts: Flow<List<TopicDraft>> = combine(
        entries,
        chats.forumChats.onStart { emit(emptyList()) },
    ) { entries, forums ->
        // Skupiny, které přibyly, se projdou na pozadí; výsledek přijde dalším [entries].
        scope.launch { forums.forEach { scan(it.id) } }
        val byId = forums.associateBy { it.id }
        entries.mapNotNull { (key, entry) ->
            byId[key.chatId]?.let { TopicDraft(it, key.topicId, entry.name, entry.draft) }
        }.sortedByDescending { it.draft.timestamp }
    }.distinctUntilChanged()

    private suspend fun scan(chatId: Long) {
        if (!mutex.withLock { scanned.add(chatId) }) return
        client.getForumTopics(
            chatId = chatId,
            query = "",
            offsetDate = 0,
            offsetMessageId = 0L,
            offsetForumTopicId = 0,
            limit = SCAN_LIMIT,
        ).toResult()
            .onSuccess { page ->
                val found = page.topics.filterNotNull().mapNotNull { topic ->
                    topic.draftMessage?.toChatDraft()?.let { Key(chatId, topic.info.forumTopicId) to Entry(topic.info.name, it) }
                }
                if (found.isNotEmpty()) entries.update { it + found }
            }
            .onFailure {
                logger.w(it) { "Témata skupiny $chatId kvůli konceptům se nenačetla." }
                mutex.withLock { scanned.remove(chatId) }
            }
    }

    override suspend fun delete(chatId: Long, topicId: Int): Result<DraftRestore?> {
        val topic = MessageTopicForum(forumTopicId = topicId)
        val draft = client.getForumTopic(chatId = chatId, forumTopicId = topicId).toResult()
            .getOrElse { return Result.failure(it) }
            .draftMessage ?: return Result.success(null)
        return client.setChatDraftMessage(chatId = chatId, topicId = topic, draftMessage = null)
            .toResult()
            .map {
                DraftRestore {
                    client.setChatDraftMessage(chatId = chatId, topicId = topic, draftMessage = draft).toResult().map { }
                }
            }
    }
}

/** Témat na skupinu, ve kterých se při prvním průchodu hledají koncepty. Víc TDLib naráz nevrátí. */
private const val SCAN_LIMIT = 100
