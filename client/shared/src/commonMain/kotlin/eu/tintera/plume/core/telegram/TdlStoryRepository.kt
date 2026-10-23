package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlThrowable
import dev.g000sha256.tdl.dto.OptionValueInteger
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
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.ReportStoryResultOk
import dev.g000sha256.tdl.dto.ReportStoryResultOptionRequired
import dev.g000sha256.tdl.dto.ReportStoryResultTextRequired
import dev.g000sha256.tdl.dto.StoryInteraction
import dev.g000sha256.tdl.dto.StoryInteractionTypeForward
import dev.g000sha256.tdl.dto.StoryInteractionTypeRepost
import dev.g000sha256.tdl.dto.StoryInteractionTypeView
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
            canBeArchived = stories.canBeArchived,
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
    override suspend fun myChatId(): Long? = session.me().getOrNull()?.id

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
                    coverFrameTimestamp = media.coverSeconds,
                    isAnimation = !media.hasAudio,
                )
            },
            caption = draft.caption.trim().takeIf { it.isNotEmpty() }?.let { FormattedText(text = it, entities = emptyArray()) },
            privacySettings = draft.audience.toPrivacySettings(),
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

    override suspend fun viewers(storyId: Int, offset: String): Result<StoryViewersPage> =
        client.getStoryInteractions(
            storyId = storyId,
            query = "",
            onlyContacts = false,
            preferForwards = false,
            // Reakce jsou to, co autora zajímá nejvíc — nahoru.
            preferWithReaction = true,
            offset = offset,
            limit = VIEWERS_PAGE,
        ).toResult()
            .onFailure { logger.w(it) { "getStoryInteractions $storyId" } }
            .map { page ->
                StoryViewersPage(
                    viewers = page.interactions.mapNotNull { it.toViewer() },
                    totalCount = page.totalCount,
                    nextOffset = page.nextOffset.takeIf { it.isNotEmpty() },
                )
            }

    /** Divák jako člověk nebo chat se jménem a fotkou; kdo nejde dočíst, vypadne. */
    private suspend fun StoryInteraction.toViewer(): StoryViewer? {
        val (reaction, action) = when (val kind = type) {
            is StoryInteractionTypeView -> kind.chosenReactionType?.toReactionKey() to StoryViewerAction.Viewed
            is StoryInteractionTypeForward -> null to StoryViewerAction.Forwarded
            is StoryInteractionTypeRepost -> null to StoryViewerAction.Reposted
        }
        return when (val actor = actorId) {
            is MessageSenderUser -> {
                val user = client.getUser(userId = actor.userId).toResult().getOrNull() ?: return null
                StoryViewer(
                    chatId = actor.userId,
                    title = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
                    avatarFileId = user.profilePhoto?.small?.id,
                    kind = ChatKind.Private,
                    date = interactionDate,
                    reaction = reaction,
                    action = action,
                )
            }

            is MessageSenderChat -> {
                val chat = client.getChat(chatId = actor.chatId).toResult().getOrNull() ?: return null
                StoryViewer(
                    chatId = actor.chatId,
                    title = chat.title,
                    avatarFileId = chat.photo?.small?.id,
                    kind = chat.type.chatKind(),
                    date = interactionDate,
                    reaction = reaction,
                    action = action,
                )
            }
        }
    }

    override suspend fun closeFriends(): Result<List<Long>> =
        client.getCloseFriends().toResult()
            .onFailure { logger.w(it) { "getCloseFriends" } }
            .map { it.userIds.toList() }

    override suspend fun setCloseFriends(userIds: List<Long>): Result<Unit> =
        client.setCloseFriends(userIds = userIds.toLongArray()).toResult()
            .onFailure { logger.w(it) { "setCloseFriends" } }
            .map { }

    override suspend fun setHidden(chatId: Long, hidden: Boolean): Result<Unit> =
        client.setChatActiveStoriesList(
            chatId = chatId,
            storyList = if (hidden) StoryListArchive() else StoryListMain(),
        ).toResult()
            .onFailure { logger.w(it) { "setChatActiveStoriesList $chatId hidden=$hidden" } }
            .map { }

    override suspend fun profileStories(chatId: Long, fromStoryId: Int): Result<ProfileStoriesPage> =
        client.getChatPostedToChatPageStories(chatId = chatId, fromStoryId = fromStoryId, limit = PROFILE_PAGE).toResult()
            .onFailure { logger.w(it) { "getChatPostedToChatPageStories $chatId" } }
            .map { page ->
                ProfileStoriesPage(
                    stories = page.stories.map { story ->
                        MessageStory(storyPosterChatId = chatId, storyId = story.id, viaMention = false)
                            .toMedia(story) { id -> _active.value[id]?.title ?: client.getChat(chatId = id).toResult().getOrNull()?.title }
                    },
                    totalCount = page.totalCount,
                    pinnedIds = page.pinnedStoryIds.toSet(),
                )
            }

    override suspend fun author(chatId: Long): ActiveStories? {
        _active.value[chatId]?.let { return it }
        val chat = client.getChat(chatId = chatId).toResult().getOrNull() ?: return null
        return ActiveStories(
            chatId = chatId,
            title = chat.title,
            avatarFileId = chat.photo?.small?.id,
            kind = chat.type.chatKind(),
            list = null,
            order = 0,
            maxReadStoryId = 0,
            stories = emptyList(),
        )
    }

    override suspend fun report(posterChatId: Long, storyId: Int, request: ReportRequest): Result<ReportStep> =
        client.reportStory(
            storyPosterChatId = posterChatId,
            storyId = storyId,
            optionId = request.optionId?.bytes ?: ByteArray(0),
            text = request.text,
        ).toResult()
            .onFailure { logger.w(it) { "reportStory $posterChatId/$storyId" } }
            .map { result ->
                // Bez `else`: nový krok v novější TDLib musí rozbít překlad, ne tiše skončit.
                when (result) {
                    is ReportStoryResultOk -> ReportStep.Done
                    is ReportStoryResultOptionRequired -> ReportStep.ChooseOption(
                        title = result.title,
                        options = result.options.map { ReportOption(ReportOptionId(it.id), it.text) },
                    )
                    is ReportStoryResultTextRequired -> ReportStep.EnterText(ReportOptionId(result.optionId), result.isOptional)
                }
            }

    override suspend fun albums(chatId: Long): Result<List<StoryAlbum>> =
        client.getChatStoryAlbums(chatId = chatId).toResult()
            .onFailure { logger.w(it) { "getChatStoryAlbums $chatId" } }
            .map { result -> result.albums.map { StoryAlbum(id = it.id, name = it.name) } }

    override suspend fun albumStories(chatId: Long, albumId: Int, offset: Int): Result<ProfileStoriesPage> =
        client.getStoryAlbumStories(chatId = chatId, storyAlbumId = albumId, offset = offset, limit = PROFILE_PAGE).toResult()
            .onFailure { logger.w(it) { "getStoryAlbumStories $chatId/$albumId" } }
            .map { page ->
                ProfileStoriesPage(
                    stories = page.stories.map { story ->
                        MessageStory(storyPosterChatId = chatId, storyId = story.id, viaMention = false)
                            .toMedia(story) { id -> _active.value[id]?.title ?: client.getChat(chatId = id).toResult().getOrNull()?.title }
                    },
                    totalCount = page.totalCount,
                    // Připnuté má jen profil, ne album.
                    pinnedIds = emptySet(),
                )
            }

    override suspend fun createAlbum(posterChatId: Long, name: String, storyIds: List<Int>): Result<StoryAlbum> =
        client.createStoryAlbum(storyPosterChatId = posterChatId, name = name, storyIds = storyIds.toIntArray()).toResult()
            .onFailure { logger.w(it) { "createStoryAlbum $posterChatId" } }
            .map { StoryAlbum(id = it.id, name = it.name) }

    override suspend fun setInAlbum(chatId: Long, albumId: Int, storyId: Int, inAlbum: Boolean): Result<Unit> =
        if (inAlbum) {
            client.addStoryAlbumStories(chatId = chatId, storyAlbumId = albumId, storyIds = intArrayOf(storyId))
        } else {
            client.removeStoryAlbumStories(chatId = chatId, storyAlbumId = albumId, storyIds = intArrayOf(storyId))
        }.toResult()
            .onFailure { logger.w(it) { "Album $chatId/$albumId, příběh $storyId, v albu=$inAlbum" } }
            .map { }

    override suspend fun renameAlbum(chatId: Long, albumId: Int, name: String): Result<StoryAlbum> =
        client.setStoryAlbumName(chatId = chatId, storyAlbumId = albumId, name = name).toResult()
            .onFailure { logger.w(it) { "setStoryAlbumName $chatId/$albumId" } }
            .map { StoryAlbum(id = it.id, name = it.name) }

    override suspend fun deleteAlbum(chatId: Long, albumId: Int): Result<Unit> =
        client.deleteStoryAlbum(chatId = chatId, storyAlbumId = albumId).toResult()
            .onFailure { logger.w(it) { "deleteStoryAlbum $chatId/$albumId" } }
            .map { }

    override suspend fun reorderAlbums(chatId: Long, albumIds: List<Int>): Result<Unit> =
        client.reorderStoryAlbums(chatId = chatId, storyAlbumIds = albumIds.toIntArray()).toResult()
            .onFailure { logger.w(it) { "reorderStoryAlbums $chatId" } }
            .map { }

    override suspend fun reorderAlbumStories(chatId: Long, albumId: Int, storyIds: List<Int>): Result<Unit> =
        client.reorderStoryAlbumStories(chatId = chatId, storyAlbumId = albumId, storyIds = storyIds.toIntArray()).toResult()
            .onFailure { logger.w(it) { "reorderStoryAlbumStories $chatId/$albumId" } }
            .map { }

    override suspend fun setAudience(storyId: Int, audience: StoryAudience): Result<Unit> =
        client.setStoryPrivacySettings(storyId = storyId, privacySettings = audience.toPrivacySettings()).toResult()
            .onFailure { logger.w(it) { "setStoryPrivacySettings $storyId" } }
            .map { }

    override suspend fun setOnProfile(posterChatId: Long, storyId: Int, onProfile: Boolean): Result<Unit> =
        client.toggleStoryIsPostedToChatPage(storyPosterChatId = posterChatId, storyId = storyId, isPostedToChatPage = onProfile)
            .toResult()
            .onFailure { logger.w(it) { "toggleStoryIsPostedToChatPage $posterChatId/$storyId $onProfile" } }
            .map { }

    // Připnuté vydá jen první stránka příběhů v profilu (`fromStoryId = 0`).
    override suspend fun pinnedStoryIds(chatId: Long): Result<List<Int>> =
        client.getChatPostedToChatPageStories(chatId = chatId, fromStoryId = 0, limit = 1).toResult()
            .onFailure { logger.w(it) { "getChatPostedToChatPageStories $chatId (připnuté)" } }
            .map { it.pinnedStoryIds.toList() }

    override suspend fun setPinned(chatId: Long, storyId: Int, pinned: Boolean): Result<Unit> = runCatching {
        val current = pinnedStoryIds(chatId).getOrThrow()
        val max = client.getOption(name = "pinned_story_count_max").toResult().getOrNull()
            ?.let { (it as? OptionValueInteger)?.value?.toInt() } ?: STORY_PINNED_MAX
        client.setChatPinnedStories(chatId = chatId, storyIds = pinnedAfter(current, storyId, pinned, max).toIntArray())
            .toResult().getOrThrow()
        Unit
    }.onFailure { logger.w(it) { "setChatPinnedStories $chatId $storyId $pinned" } }

    override suspend fun archivedStories(chatId: Long, fromStoryId: Int): Result<ProfileStoriesPage> =
        client.getChatArchivedStories(chatId = chatId, fromStoryId = fromStoryId, limit = PROFILE_PAGE).toResult()
            .onFailure { logger.w(it) { "getChatArchivedStories $chatId" } }
            .map { page ->
                ProfileStoriesPage(
                    stories = page.stories.map { story ->
                        MessageStory(storyPosterChatId = chatId, storyId = story.id, viaMention = false)
                            .toMedia(story) { id -> _active.value[id]?.title ?: client.getChat(chatId = id).toResult().getOrNull()?.title }
                    },
                    totalCount = page.totalCount,
                    pinnedIds = page.pinnedStoryIds.toSet(),
                )
            }

    override suspend fun editCaption(posterChatId: Long, storyId: Int, caption: String): Result<Unit> =
        client.editStory(
            storyPosterChatId = posterChatId,
            storyId = storyId,
            // Obsah a oblasti zůstávají (`null`); prázdný popisek ho odebere.
            caption = FormattedText(text = caption.trim(), entities = emptyArray()),
        ).toResult()
            .onFailure { logger.w(it) { "editStory $posterChatId/$storyId" } }
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

/** Stránka příběhů v profilu; TDLib si počet stejně volí sám. */
private const val PROFILE_PAGE = 30

/** Stránka diváků; víc se do sheetu najednou nevejde a další se dočte při posunu. */
private const val VIEWERS_PAGE = 50
private const val NOT_FOUND = 404

/** Prohlížeč na příběh čeká, ale ne donekonečna: bez spojení TDLib na síť čeká mlčky. */
private val STORY_FETCH_TIMEOUT = 10.seconds
