package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.LocalPanesSideBySide
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.clear_history_all_text
import eu.tintera.plume.resources.clear_history_all_topic_text
import eu.tintera.plume.resources.conversation_fallback_title
import eu.tintera.plume.resources.summary_me
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Kalendář zpráv (`ChatCalendarRoute`, v archivu `ArchiveChatCalendarRoute`). Vedle konverzace se
 * po ťuknutí na den nezavírá: konverzace vedle se posune a panel zůstane. Když stojí sám (telefon,
 * zúžené okno, složený Fold), skok ho zavře — konverzace pod ním by jinak nebyla vidět.
 *
 * Shrnutí dne a vymazání historie obslouží panel sám: ViewModel shrnutí i sheety by v konverzaci
 * patřily jiné položce navigace.
 */
@Composable
internal fun ChatCalendarPaneHost(
    chatId: Long,
    savedTopicId: Long?,
    directTopicId: Long?,
    anchor: Long?,
    onClose: () -> Unit,
) {
    val viewModel: ChatCalendarViewModel = koinViewModel(
        key = "calendar-$chatId-$savedTopicId-$directTopicId",
        parameters = { parametersOf(chatId, savedTopicId, directTopicId, anchor) },
    )
    val state = viewModel.calendar.state.collectAsStateWithLifecycle().value ?: return
    val info by viewModel.info.collectAsStateWithLifecycle()
    val initialDay by viewModel.initialDay.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val sideBySide = LocalPanesSideBySide.current
    val summary: ChatSummaryViewModel = koinViewModel(key = "calendar-summary-$chatId")
    val summaryAvailable by summary.available.collectAsStateWithLifecycle()
    val summaryMe = stringResource(Res.string.summary_me)
    val summaryTitle = info?.let { chatTitle(it.id, it.title) } ?: stringResource(Res.string.conversation_fallback_title)
    val clearRights = info?.clearHistory?.takeIf { it.byDate }
    val inOwnTopic = savedTopicId != null || directTopicId != null
    var clearDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastSummary by remember { mutableStateOf<(() -> Unit)?>(null) }
    val zone = remember { TimeZone.currentSystemDefault() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        snackbar.showSnackbar(text.resolveNow())
    }

    Box(Modifier.fillMaxSize()) {
        ChatCalendarPane(
            state = state,
            calendar = viewModel.calendar,
            initialDay = initialDay,
            onJumpToDay = { seconds ->
                viewModel.jumpToDay(seconds)
                if (!sideBySide) onClose()
            },
            onJumpToMessage = { messageId ->
                viewModel.jumpToMessage(messageId)
                if (!sideBySide) onClose()
            },
            onSummarizeDay = if (summaryAvailable) {
                { date ->
                    val start = {
                        summary.summarize(summaryTitle, summaryMe) {
                            SummarySource(viewModel.calendar.messagesOfDay(date), unread = false, day = date)
                        }
                    }
                    lastSummary = start
                    start()
                }
            } else {
                null
            },
            onClearDay = clearRights?.let { { date -> clearDay = date.atStartOfDayIn(zone).epochSeconds.toInt() } },
            canFilterBySender = info?.let { it.isGroup && !it.isChannel } == true,
            onClose = onClose,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val summaryState by summary.state.collectAsStateWithLifecycle()
    val summarySource by summary.source.collectAsStateWithLifecycle()
    if (summaryState != ChatSummaryState.Idle) {
        val comparison by summary.comparison.collectAsStateWithLifecycle()
        val canCompare by summary.canCompare.collectAsStateWithLifecycle()
        val mainProvider by summary.mainProvider.collectAsStateWithLifecycle()
        ChatSummarySheet(
            state = summaryState,
            source = summarySource,
            onRetry = { lastSummary?.let(summary::retry) },
            onDismiss = summary::dismiss,
            comparison = comparison,
            canCompare = canCompare,
            onCompare = summary::compare,
            mainProvider = mainProvider,
        )
    }

    val day = clearDay
    val chat = info
    if (day != null && clearRights != null && chat != null) {
        ClearHistorySheet(
            rights = clearRights,
            peerName = chat.title.takeIf { !chat.isGroup && !chat.isChannel },
            allText = if (inOwnTopic) Res.string.clear_history_all_topic_text else Res.string.clear_history_all_text,
            onConfirm = viewModel::clearHistory,
            onDismiss = { clearDay = null },
            initialDay = day,
        )
    }
}
