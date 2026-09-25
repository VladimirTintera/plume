package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatPoll
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.PollVotersPage
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.poll_percent
import eu.tintera.plume.resources.poll_subtitle_joined
import eu.tintera.plume.resources.poll_stop_confirm
import eu.tintera.plume.resources.poll_stop_text
import eu.tintera.plume.resources.poll_stop_title
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.poll_voters_failed
import eu.tintera.plume.resources.poll_voters_more
import eu.tintera.plume.resources.poll_voters_title
import eu.tintera.plume.resources.poll_votes
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Hlasující u jedné odpovědi, jak se dotahují po stránkách. */
data class AnswerVoters(
    val voters: List<SenderInfo> = emptyList(),
    /** Odhad serveru; skutečný konec pozná až prázdná stránka. */
    val total: Int = 0,
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val hasMore: Boolean get() = !loading && !failed && voters.size < total

    /**
     * Další stránka. Tentýž hlasující na dvou stránkách zůstane jednou — dva stejné klíče by
     * `LazyColumn` shodily. **Prázdná stránka je konec**, i když server odhadoval víc: bez toho by
     * „Zobrazit další" viselo u odpovědi, ke které už nic nepřijde.
     */
    fun withPage(page: PollVotersPage): AnswerVoters {
        val merged = (voters + page.voters).distinctBy { it.id }
        return copy(
            voters = merged,
            total = if (merged.size == voters.size) merged.size else maxOf(page.total, merged.size),
            loading = false,
            failed = false,
        )
    }
}

/**
 * Kdo v anketě jak hlasoval. Klíčem mapy je index odpovědi v PŮVODNÍM pořadí
 * ([eu.tintera.plume.core.telegram.PollAnswer.index]) — tím se ptá server.
 *
 * Načítají se jen odpovědi, pro které někdo hlasoval, všechny naráz: sheet ukazuje všechny
 * odpovědi pod sebou a čekat na ně po jedné by znamenalo seznam, který pod prstem roste.
 */
class PollVotersViewModel(
    private val chatId: Long,
    private val messageId: Long,
    answerIndexes: List<Int>,
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(answerIndexes.associateWith { AnswerVoters() })
    val state: StateFlow<Map<Int, AnswerVoters>> = _state.asStateFlow()

    init {
        answerIndexes.forEach(::load)
    }

    fun loadMore(answerIndex: Int) {
        val current = _state.value[answerIndex] ?: return
        if (!current.hasMore) return
        _state.update { it + (answerIndex to current.copy(loading = true)) }
        load(answerIndex)
    }

    private fun load(answerIndex: Int) {
        viewModelScope.launch {
            val offset = _state.value[answerIndex]?.voters?.size ?: 0
            repository.pollVoters(chatId, messageId, answerIndex, offset)
                .onSuccess { page ->
                    _state.update { it + (answerIndex to (it[answerIndex] ?: AnswerVoters()).withPage(page)) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Hlasující u odpovědi $answerIndex zprávy $messageId se nepodařilo načíst." }
                    _state.update {
                        it + (answerIndex to (it[answerIndex] ?: AnswerVoters()).copy(loading = false, failed = true))
                    }
                }
        }
    }

    private companion object {
        val logger = Logger.withTag("PollVoters")
    }
}

/**
 * Sheet „Kdo hlasoval": odpovědi pod sebou, u každé blok zaoblených řádků s hlasujícími —
 * stejný jazyk jako sheet připnutých zpráv a seznam chatů.
 *
 * [poll] je ŽIVÁ anketa ze stavu konverzace (hlavičky s počty se mění s ní); hlasující se čtou
 * jednou při otevření.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PollVotersSheet(
    chatId: Long,
    messageId: Long,
    poll: ChatPoll,
    onOpenVoter: (senderId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val answers = poll.options.filter { it.voters > 0 }
    val viewModel: PollVotersViewModel = koinViewModel(
        key = "poll-voters-$chatId-$messageId",
        parameters = { parametersOf(chatId, messageId, answers.map { it.index }) },
    )
    val voters by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.poll_voters_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp),
        )
        Text(
            text = poll.question,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp),
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            modifier = Modifier.heightIn(max = VOTERS_MAX_HEIGHT),
        ) {
            answers.forEach { answer ->
                val loaded = voters[answer.index] ?: AnswerVoters()
                item(key = "answer-${answer.index}") {
                    AnswerHeader(
                        text = answer.text,
                        votes = answer.voters,
                        percentage = answer.percentage,
                        modifier = Modifier.padding(top = GROUP_SPACING),
                    )
                }
                itemsIndexed(loaded.voters, key = { _, voter -> "voter-${answer.index}-${voter.id}" }) { index, voter ->
                    VoterRow(
                        voter = voter,
                        shape = groupItemShape(index, loaded.voters.size),
                        onClick = { onOpenVoter(voter.id) },
                    )
                }
                item(key = "more-${answer.index}") {
                    when {
                        loaded.loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                        loaded.failed -> Text(
                            text = stringResource(Res.string.poll_voters_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                        loaded.hasMore -> TextButton(onClick = { viewModel.loadMore(answer.index) }) {
                            Text(stringResource(Res.string.poll_voters_more))
                        }
                    }
                }
            }
        }
    }
}

/** Odpověď nad svým blokem: text, a vpravo počet hlasů a procenta. */
@Composable
private fun AnswerHeader(text: String, votes: Int, percentage: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                Res.string.poll_subtitle_joined,
                pluralStringResource(Res.plurals.poll_votes, votes, votes.grouped()),
                stringResource(Res.string.poll_percent, percentage),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun VoterRow(voter: SenderInfo, shape: Shape, onClick: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Avatar(chatId = voter.id, title = voter.title, avatarFileId = voter.avatarFileId, size = 40.dp)
            Text(
                text = voter.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Strop výšky: u ankety s desítkami hlasů by sheet jinak zabral celou obrazovku. */
private val VOTERS_MAX_HEIGHT = 560.dp

/**
 * Potvrzení „Ukončit anketu". Nevratné — hlasovat už nepůjde nikomu —, proto se ptá, a text
 * říká následek, ne „opravdu?". Tlačítko je chybovou barvou jako u jiných nevratných akcí.
 */
@Composable
internal fun StopPollDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.poll_stop_title)) },
        text = { Text(stringResource(Res.string.poll_stop_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.poll_stop_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
