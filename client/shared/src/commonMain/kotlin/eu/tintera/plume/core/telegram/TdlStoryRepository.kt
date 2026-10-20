package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlThrowable
import dev.g000sha256.tdl.dto.ChatActiveStories
import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.StoryList
import dev.g000sha256.tdl.dto.StoryListArchive
import dev.g000sha256.tdl.dto.StoryListMain
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * [StoryRepository] nad TDLib. **Musí vznikat eagerně** (`createdAtStart`): TDLib posílá
 * `updateChatActiveStories` spolu s načítáním chatů hned po přihlášení, a co odběr nestihne,
 * nezopakuje — kroužek by se objevil až s další změnou autorových příběhů.
 */
class TdlStoryRepository(
    private val holder: TelegramClientHolder,
    session: TelegramSession,
    private val scope: CoroutineScope,
) : StoryRepository {

    private val logger = Logger.withTag("Stories")

    private val client get() = holder.client

    private val _active = MutableStateFlow<Map<Long, ActiveStories>>(emptyMap())
    override val active: StateFlow<Map<Long, ActiveStories>> = _active.asStateFlow()

    init {
        scope.launch {
            holder.updates { chatActiveStoriesUpdates }.collect { update -> apply(update.activeStories) }
        }
        // Jméno a fotka autora se mění vlastními updaty, ne novým `ChatActiveStories`.
        scope.launch {
            holder.updates { chatTitleUpdates }.collect { update ->
                _active.update { all ->
                    all[update.chatId]?.let { all + (update.chatId to it.copy(title = update.title)) } ?: all
                }
            }
        }
        scope.launch {
            holder.updates { chatPhotoUpdates }.collect { update ->
                _active.update { all ->
                    all[update.chatId]?.let { all + (update.chatId to it.copy(avatarFileId = update.photo?.small?.id)) } ?: all
                }
            }
        }
        scope.launch {
            session.authStage.collect { stage ->
                when (stage) {
                    AuthStage.Ready -> {
                        loadAll(StoryListMain())
                        loadAll(StoryListArchive())
                    }

                    AuthStage.LoggingOut -> _active.value = emptyMap()
                    else -> Unit
                }
            }
        }
    }

    /**
     * Načte celý seznam. Příběhy samy chodí updaty, `loadActiveStories` je jen spouští;
     * 404 znamená „všechno načtené", ne chybu. Strop počtu kol je pojistka proti smyčce.
     */
    private suspend fun loadAll(list: StoryList) {
        repeat(MAX_LOAD_ROUNDS) {
            val result = client.loadActiveStories(storyList = list).toResult()
            val failure = result.exceptionOrNull() ?: return@repeat
            if (failure !is TdlThrowable || failure.code != NOT_FOUND) {
                logger.w(failure) { "Příběhy se nepodařilo načíst (${list::class.simpleName})" }
            }
            return
        }
    }

    private suspend fun apply(stories: ChatActiveStories) {
        if (stories.stories.isEmpty()) {
            _active.update { it - stories.chatId }
            return
        }
        // Autora známe z dřívějška, nebo si ho dočteme. Chat musí TDLib znát (update o něm
        // chodí dřív než o jeho příbězích), takže `getChat` odpoví z lokální databáze.
        val known = _active.value[stories.chatId]
        val chat = if (known == null) client.getChat(chatId = stories.chatId).toResult().getOrNull() else null
        if (known == null && chat == null) {
            logger.w { "Autor příběhů ${stories.chatId} není známý" }
            return
        }
        val entry = ActiveStories(
            chatId = stories.chatId,
            title = chat?.title ?: known!!.title,
            avatarFileId = if (chat != null) chat.photo?.small?.id else known!!.avatarFileId,
            kind = chat?.type?.chatKind() ?: known!!.kind,
            list = when (stories.list) {
                is StoryListMain -> StoryListKind.Main
                is StoryListArchive -> StoryListKind.Archive
                null -> null
            },
            order = stories.order,
            maxReadStoryId = stories.maxReadStoryId,
            stories = stories.stories.map {
                StoryRef(id = it.storyId, date = it.date, isLive = it.isLive, isForCloseFriends = it.isForCloseFriends)
            },
        )
        _active.update { it + (stories.chatId to entry) }
    }

    override suspend fun story(posterChatId: Long, storyId: Int): MessageMedia.Story {
        val local = client.getStory(storyPosterChatId = posterChatId, storyId = storyId, onlyLocal = true).toResult().getOrNull()
        val story = local ?: withTimeoutOrNull(STORY_FETCH_TIMEOUT) {
            client.getStory(storyPosterChatId = posterChatId, storyId = storyId, onlyLocal = false).toResult()
                .onFailure { logger.w(it) { "Příběh $posterChatId/$storyId se nepodařilo načíst" } }
                .getOrNull()
        }
        return MessageStory(storyPosterChatId = posterChatId, storyId = storyId, viaMention = false)
            .toMedia(story) { id -> _active.value[id]?.title ?: client.getChat(chatId = id).toResult().getOrNull()?.title }
    }

    override suspend fun open(posterChatId: Long, storyId: Int) {
        client.openStory(storyPosterChatId = posterChatId, storyId = storyId).toResult()
            .onFailure { logger.w(it) { "openStory $posterChatId/$storyId" } }
    }

    override suspend fun close(posterChatId: Long, storyId: Int) {
        client.closeStory(storyPosterChatId = posterChatId, storyId = storyId).toResult()
            .onFailure { logger.w(it) { "closeStory $posterChatId/$storyId" } }
    }
}

private const val MAX_LOAD_ROUNDS = 50
private const val NOT_FOUND = 404

/** Prohlížeč na příběh čeká, ale ne donekonečna: bez spojení TDLib na síť čeká mlčky. */
private val STORY_FETCH_TIMEOUT = 10.seconds
