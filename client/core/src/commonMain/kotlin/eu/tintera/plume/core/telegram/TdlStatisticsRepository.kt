package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatStatisticsChannel
import dev.g000sha256.tdl.dto.ChatStatisticsObjectTypeMessage
import dev.g000sha256.tdl.dto.ChatStatisticsObjectTypeStory
import dev.g000sha256.tdl.dto.ChatStatisticsSupergroup
import dev.g000sha256.tdl.dto.DateRange
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.PublicForwardMessage
import dev.g000sha256.tdl.dto.PublicForwardStory
import dev.g000sha256.tdl.dto.StatisticalGraph
import dev.g000sha256.tdl.dto.StatisticalGraphAsync
import dev.g000sha256.tdl.dto.StatisticalGraphData
import dev.g000sha256.tdl.dto.StatisticalGraphError
import dev.g000sha256.tdl.dto.StatisticalValue
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Statistiky nad TDLib.
 *
 * - **Barvy grafů se neberou ze serveru**, takže je jedno, jestli se ptáme na tmavou, nebo
 *   světlou variantu (`isDark` mění jen barvy v JSONu); ptá se na světlou.
 * - **Grafy, které server dopočítává, se tady nenačítají** ([StatGraph.Pending]): čekat na ně
 *   by zdrželo celou obrazovku kvůli jednomu grafu dole. Dočte je ViewModel přes [graph].
 */
class TdlStatisticsRepository(
    private val holder: TelegramClientHolder,
) : StatisticsRepository {

    private val client: TdlClient get() = holder.client
    private val logger = Logger.withTag("Statistics")

    override suspend fun chatStatistics(chatId: Long): Result<ChatStatistics> = runCatching {
        when (val stats = client.getChatStatistics(chatId = chatId, isDark = false).toResult().getOrThrow()) {
            is ChatStatisticsChannel -> stats.toChannel(chatId)
            is ChatStatisticsSupergroup -> stats.toGroup()
        }
    }

    override suspend fun graph(chatId: Long, token: String): StatGraph =
        client.getStatisticalGraph(chatId = chatId, token = token, x = 0L).toResult()
            .map { it.toStatGraph() }
            .getOrElse { failure ->
                logger.w(failure) { "Graf statistiky chatu $chatId se nepodařilo načíst." }
                StatGraph.Failed
            }

    override suspend fun postStatistics(chatId: Long, messageId: Long): Result<PostStatistics> = runCatching {
        coroutineScope {
            val message = async { client.getMessage(chatId = chatId, messageId = messageId).toResult().getOrThrow() }
            val stats = client.getMessageStatistics(chatId = chatId, messageId = messageId, isDark = false)
                .toResult().getOrThrow()
            val post = message.await()
            PostStatistics(
                preview = post.previewText(),
                date = post.date,
                views = post.interactionInfo?.viewCount ?: 0,
                forwards = post.interactionInfo?.forwardCount ?: 0,
                reactions = post.reactionCount(),
                graphs = listOf(
                    StatGraphEntry(StatGraphKind.PostInteractions, stats.messageInteractionGraph.toStatGraph()),
                    StatGraphEntry(StatGraphKind.PostReactions, stats.messageReactionGraph.toStatGraph()),
                ),
            )
        }
    }

    override suspend fun publicForwards(chatId: Long, messageId: Long, offset: String): Result<PublicForwardsPage> = runCatching {
        val page = client.getMessagePublicForwards(chatId = chatId, messageId = messageId, offset = offset, limit = PAGE_SIZE)
            .toResult().getOrThrow()
        PublicForwardsPage(
            total = page.totalCount,
            items = page.forwards.mapNotNull { forward ->
                when (forward) {
                    is PublicForwardMessage -> forward.message.let { message ->
                        val chat = chatInfo(message.chatId) ?: return@mapNotNull null
                        PublicForward(chat, message.id, message.date, message.interactionInfo?.viewCount ?: 0)
                    }
                    is PublicForwardStory -> forward.story.let { story ->
                        val chat = chatInfo(story.posterChatId) ?: return@mapNotNull null
                        PublicForward(chat, messageId = null, date = story.date, views = story.interactionInfo?.viewCount ?: 0)
                    }
                }
            },
            nextOffset = page.nextOffset.ifEmpty { null },
        )
    }

    private suspend fun ChatStatisticsChannel.toChannel(chatId: Long): ChatStatistics.Channel {
        val messageIds = recentInteractions.mapNotNull { (it.objectType as? ChatStatisticsObjectTypeMessage)?.messageId }
        // Náhledy jedním dotazem; nenačtená zpráva (smazaná) zůstane v přehledu jen s počty.
        val messages: Map<Long, Message> = if (messageIds.isEmpty()) {
            emptyMap()
        } else {
            client.getMessages(chatId = chatId, messageIds = messageIds.toLongArray()).toResult()
                .onFailure { logger.w(it) { "Náhledy příspěvků do statistiky chatu $chatId se nepodařilo načíst." } }
                .getOrNull()?.messages?.filterNotNull()?.associateBy { it.id }.orEmpty()
        }
        return ChatStatistics.Channel(
            period = period.toPeriod(),
            members = memberCount.toValue(),
            meanViews = meanMessageViewCount.toValue(),
            meanShares = meanMessageShareCount.toValue(),
            meanReactions = meanMessageReactionCount.toValue(),
            meanStoryViews = meanStoryViewCount.toValue(),
            meanStoryShares = meanStoryShareCount.toValue(),
            meanStoryReactions = meanStoryReactionCount.toValue(),
            notificationsPercent = enabledNotificationsPercentage,
            graphs = listOf(
                StatGraphKind.MemberCount to memberCountGraph,
                StatGraphKind.Joins to joinGraph,
                StatGraphKind.Mutes to muteGraph,
                StatGraphKind.ViewsByHour to viewCountByHourGraph,
                StatGraphKind.ViewsBySource to viewCountBySourceGraph,
                StatGraphKind.JoinsBySource to joinBySourceGraph,
                StatGraphKind.Languages to languageGraph,
                StatGraphKind.PostInteractions to messageInteractionGraph,
                StatGraphKind.PostReactions to messageReactionGraph,
                StatGraphKind.StoryInteractions to storyInteractionGraph,
                StatGraphKind.StoryReactions to storyReactionGraph,
                StatGraphKind.InstantView to instantViewInteractionGraph,
            ).map { (kind, graph) -> StatGraphEntry(kind, graph.toStatGraph()) },
            recent = recentInteractions.map { info ->
                when (val type = info.objectType) {
                    is ChatStatisticsObjectTypeMessage -> {
                        val message = messages[type.messageId]
                        RecentInteraction(
                            messageId = type.messageId,
                            storyId = null,
                            preview = message?.previewText(),
                            date = message?.date,
                            views = info.viewCount,
                            forwards = info.forwardCount,
                            reactions = info.reactionCount,
                        )
                    }
                    is ChatStatisticsObjectTypeStory -> RecentInteraction(
                        messageId = null,
                        storyId = type.storyId,
                        preview = null,
                        date = null,
                        views = info.viewCount,
                        forwards = info.forwardCount,
                        reactions = info.reactionCount,
                    )
                }
            },
        )
    }

    private suspend fun ChatStatisticsSupergroup.toGroup(): ChatStatistics.Group = coroutineScope {
        val senders = async { topSenders.toList().mapUsers({ it.userId }) { user, s -> TopSender(user, s.sentMessageCount, s.averageCharacterCount) } }
        val admins = async {
            topAdministrators.toList().mapUsers({ it.userId }) { user, a ->
                TopAdmin(user, a.deletedMessageCount, a.bannedUserCount, a.restrictedUserCount)
            }
        }
        val inviters = async { topInviters.toList().mapUsers({ it.userId }) { user, i -> TopInviter(user, i.addedMemberCount) } }
        ChatStatistics.Group(
            period = period.toPeriod(),
            members = memberCount.toValue(),
            messages = messageCount.toValue(),
            viewers = viewerCount.toValue(),
            senders = senderCount.toValue(),
            graphs = listOf(
                StatGraphKind.MemberCount to memberCountGraph,
                StatGraphKind.Joins to joinGraph,
                StatGraphKind.JoinsBySource to joinBySourceGraph,
                StatGraphKind.Languages to languageGraph,
                StatGraphKind.MessageContent to messageContentGraph,
                StatGraphKind.Actions to actionGraph,
                StatGraphKind.ActivityByDay to dayGraph,
                StatGraphKind.ActivityByWeek to weekGraph,
            ).map { (kind, graph) -> StatGraphEntry(kind, graph.toStatGraph()) },
            topSenders = senders.await(),
            topAdmins = admins.await(),
            topInviters = inviters.await(),
        )
    }

    /** Uživatele žebříčku načte souběžně; smazaný účet (bez jména) z žebříčku vypadne. */
    private suspend fun <T, R> List<T>.mapUsers(userId: (T) -> Long, build: (SenderInfo, T) -> R): List<R> = coroutineScope {
        map { item -> async { userInfo(userId(item))?.let { build(it, item) } } }.awaitAll().filterNotNull()
    }

    private suspend fun userInfo(userId: Long): SenderInfo? {
        val user = client.getUser(userId = userId).toResult().getOrNull() ?: return null
        val name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
        if (name.isBlank()) return null
        return SenderInfo(
            id = userId,
            title = name,
            avatarFileId = user.profilePhoto?.small?.id,
            verification = user.verificationStatus.toVerification(),
        )
    }

    private suspend fun chatInfo(chatId: Long): SenderInfo? {
        val chat = client.getChat(chatId = chatId).toResult().getOrNull() ?: return null
        return SenderInfo(id = chatId, title = chat.title, avatarFileId = chat.photo?.small?.id)
    }

    private fun StatisticalGraph.toStatGraph(): StatGraph = when (this) {
        is StatisticalGraphData -> parseStatChart(jsonData)?.let { StatGraph.Ready(it) } ?: run {
            logger.w { "Graf statistiky má JSON, kterému nerozumíme: ${jsonData.take(200)}" }
            StatGraph.Failed
        }
        is StatisticalGraphAsync -> StatGraph.Pending(token)
        // Text chyby je od serveru — do logu, ne na obrazovku.
        is StatisticalGraphError -> {
            logger.i { "Graf statistiky server nevydal: $errorMessage" }
            StatGraph.Failed
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

private fun StatisticalValue.toValue() = StatValue(value = value, previous = previousValue, growthPercent = growthRatePercentage)

private fun DateRange.toPeriod() = StatPeriod(start = startDate, end = endDate)

/** Součet všech reakcí na zprávě (každý druh reakce má vlastní počet). */
private fun Message.reactionCount(): Int = interactionInfo?.reactions?.reactions?.sumOf { it.totalCount } ?: 0
