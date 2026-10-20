package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlThrowable
import dev.g000sha256.tdl.dto.CanPostStoryResult
import dev.g000sha256.tdl.dto.CanPostStoryResultActiveStoryLimitExceeded
import dev.g000sha256.tdl.dto.CanPostStoryResultBoostNeeded
import dev.g000sha256.tdl.dto.CanPostStoryResultLiveStoryIsActive
import dev.g000sha256.tdl.dto.CanPostStoryResultMonthlyLimitExceeded
import dev.g000sha256.tdl.dto.CanPostStoryResultOk
import dev.g000sha256.tdl.dto.CanPostStoryResultPremiumNeeded
import dev.g000sha256.tdl.dto.CanPostStoryResultWeeklyLimitExceeded
import dev.g000sha256.tdl.dto.ChatActiveStories
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputStoryContentPhoto
import dev.g000sha256.tdl.dto.InputStoryContentVideo
import dev.g000sha256.tdl.dto.StoryPrivacySettingsContacts
import dev.g000sha256.tdl.dto.StoryPrivacySettingsEveryone
import dev.g000sha256.tdl.dto.StoryPrivacySettingsSelectedUsers
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.story_limit_active
import eu.tintera.plume.resources.story_limit_hours
import eu.tintera.plume.resources.story_limit_premium
import eu.tintera.plume.resources.story_post_check_failed
import eu.tintera.plume.resources.story_post_failed
import eu.tintera.plume.resources.story_post_live_active
import eu.tintera.plume.resources.story_post_not_allowed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageReplyToStory
import dev.g000sha256.tdl.dto.InputMessageText
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
    private val session: TelegramSession,
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

    override suspend fun react(posterChatId: Long, storyId: Int, reaction: String?): Result<Unit> =
        client.setStoryReaction(
            storyPosterChatId = posterChatId,
            storyId = storyId,
            reactionType = reaction?.toReactionType(),
            // Reakce z příběhu patří mezi nedávné jako reakce u zprávy.
            updateRecentReactions = true,
        ).toResult()
            .onFailure { logger.w(it) { "setStoryReaction $posterChatId/$storyId" } }
            .map { }

    override suspend fun reply(posterChatId: Long, storyId: Int, text: String): Result<Unit> =
        client.sendMessage(
            // Odpověď jde do soukromého chatu s autorem — jeho id je id toho chatu.
            chatId = posterChatId,
            replyTo = InputMessageReplyToStory(storyPosterChatId = posterChatId, storyId = storyId),
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = false,
            ),
        ).toResult()
            .onFailure { logger.w(it) { "Odpověď na příběh $posterChatId/$storyId" } }
            .map { }

    /** Vlastní příběh se zveřejňuje „za" Uložené zprávy, jejichž id je id účtu. */
    private suspend fun myChatId(): Long? = session.me().getOrNull()?.id

    override suspend fun canPost(): StoryPostCheck {
        val me = myChatId() ?: return StoryPostCheck.Denied(UiText(Res.string.story_post_check_failed))
        return client.canPostStory(chatId = me).toResult().fold(
            onSuccess = { it.toCheck() },
            onFailure = { failure ->
                logger.w(failure) { "canPostStory" }
                StoryPostCheck.Denied(failure.userMessage(Res.string.story_post_check_failed))
            },
        )
    }

    override suspend fun post(draft: StoryDraft): Result<Unit> {
        val me = myChatId() ?: return Result.failure(IllegalStateException("Účet není známý"))
        return client.postStory(
            chatId = me,
            content = when (val media = draft.media) {
                is StoryMedia.Photo -> InputStoryContentPhoto(photo = InputFileLocal(path = media.path), addedStickerFileIds = intArrayOf())
                is StoryMedia.Video -> InputStoryContentVideo(
                    video = InputFileLocal(path = media.path),
                    addedStickerFileIds = intArrayOf(),
                    duration = media.durationSeconds,
                    // Náhled z prvního snímku; vlastní volbu obálky Plume zatím nemá.
                    coverFrameTimestamp = 0.0,
                    isAnimation = !media.hasAudio,
                )
            },
            caption = draft.caption.trim().takeIf { it.isNotEmpty() }?.let { FormattedText(text = it, entities = emptyArray()) },
            privacySettings = when (val audience = draft.audience) {
                StoryAudience.Everyone -> StoryPrivacySettingsEveryone(exceptUserIds = longArrayOf())
                StoryAudience.Contacts -> StoryPrivacySettingsContacts(exceptUserIds = longArrayOf())
                is StoryAudience.Selected -> StoryPrivacySettingsSelectedUsers(userIds = audience.userIds.toLongArray())
            },
            albumIds = intArrayOf(),
            // Delší i kratší doba je jen pro Premium; 24 h je jediná, kterou smí každý.
            activePeriod = STORY_ACTIVE_PERIOD,
            isPostedToChatPage = draft.keepOnProfile,
            protectContent = draft.protectContent,
        ).toResult()
            .onFailure { logger.w(it) { "postStory" } }
            .map { }
    }

    override val postResults: Flow<StoryPostResult> = merge(
        holder.updates { storyPostSucceededUpdates }.map { StoryPostResult.Posted },
        holder.updates { storyPostFailedUpdates }.map { update ->
            logger.w { "Příběh se nezveřejnil: ${update.error.code} ${update.error.message}" }
            val denied = update.errorType?.toCheck() as? StoryPostCheck.Denied
            StoryPostResult.Failed(denied?.reason ?: errorText(update.error.message, Res.string.story_post_failed))
        },
    )

    override suspend fun delete(posterChatId: Long, storyId: Int): Result<Unit> =
        client.deleteStory(storyPosterChatId = posterChatId, storyId = storyId).toResult()
            .onFailure { logger.w(it) { "deleteStory $posterChatId/$storyId" } }
            .map { }

    override suspend fun close(posterChatId: Long, storyId: Int) {
        client.closeStory(storyPosterChatId = posterChatId, storyId = storyId).toResult()
            .onFailure { logger.w(it) { "closeStory $posterChatId/$storyId" } }
    }
}

/**
 * Proč příběh zveřejnit nejde. Nákup Premium se nenabízí, ani když by limit zvedl — Plume
 * platby nedělá (viz roadmapa) a tlačítko, které vede do obchodu, by tu bylo past.
 */
private fun CanPostStoryResult.toCheck(): StoryPostCheck = when (this) {
    is CanPostStoryResultOk -> StoryPostCheck.Allowed
    is CanPostStoryResultActiveStoryLimitExceeded -> StoryPostCheck.Denied(UiText(Res.string.story_limit_active))
    is CanPostStoryResultWeeklyLimitExceeded -> StoryPostCheck.Denied(retryIn(retryAfter))
    is CanPostStoryResultMonthlyLimitExceeded -> StoryPostCheck.Denied(retryIn(retryAfter))
    is CanPostStoryResultPremiumNeeded -> StoryPostCheck.Denied(UiText(Res.string.story_limit_premium))
    is CanPostStoryResultBoostNeeded -> StoryPostCheck.Denied(UiText(Res.string.story_post_not_allowed))
    is CanPostStoryResultLiveStoryIsActive -> StoryPostCheck.Denied(UiText(Res.string.story_post_live_active))
}

/** „Další příběh půjde zveřejnit za N hodin" — zaokrouhleno nahoru, ať to nevyjde dřív. */
private fun retryIn(seconds: Int): UiText {
    val hours = ((seconds + 3599) / 3600).coerceAtLeast(1)
    return UiText(Res.plurals.story_limit_hours, hours, hours)
}

private const val STORY_ACTIVE_PERIOD = 86_400

private const val MAX_LOAD_ROUNDS = 50
private const val NOT_FOUND = 404

/** Prohlížeč na příběh čeká, ale ne donekonečna: bez spojení TDLib na síť čeká mlčky. */
private val STORY_FETCH_TIMEOUT = 10.seconds
