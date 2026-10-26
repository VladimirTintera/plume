package eu.tintera.plume.features.statistics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatStatistics
import eu.tintera.plume.core.telegram.PostStatistics
import eu.tintera.plume.core.telegram.PublicForward
import eu.tintera.plume.core.telegram.StatGraph
import eu.tintera.plume.core.telegram.StatGraphEntry
import eu.tintera.plume.core.telegram.StatGraphKind
import eu.tintera.plume.core.telegram.StatisticsRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.post_statistics_failed
import eu.tintera.plume.resources.post_statistics_public_shares_failed
import eu.tintera.plume.resources.statistics_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Statistiky kanálu nebo skupiny. Čtou se jednou při otevření (a znovu tlačítkem po chybě);
 * TDLib na ně update nemá a obrazovka je na chvíli.
 */
class ChatStatisticsViewModel(
    val chatId: Long,
    private val repository: StatisticsRepository,
) : ViewModel() {

    private val logger = Logger.withTag("Statistics")

    /** `null` = načítá se, nebo selhalo ([failure]). */
    var statistics by mutableStateOf<ChatStatistics?>(null)
        private set

    /** Grafy statistik s dočtenými příslibenými ([StatGraph.Pending]) — mění se, jak dobíhají. */
    var graphs by mutableStateOf<List<StatGraphEntry>>(emptyList())
        private set

    var failure by mutableStateOf<UiText?>(null)
        private set

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        failure = null
        statistics = null
        loadJob = viewModelScope.launch {
            repository.chatStatistics(chatId)
                .onSuccess { stats ->
                    statistics = stats
                    graphs = stats.graphs
                    resolvePending(stats.graphs, { repository.graph(chatId, it) }) { kind, graph ->
                        graphs = graphs.withGraph(kind, graph)
                    }
                }
                .onFailure { error ->
                    // Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`.
                    logger.w(error) { "Statistiky chatu $chatId se nepodařilo načíst." }
                    failure = error.userMessage(Res.string.statistics_failed)
                }
        }
    }
}

/** Statistika příspěvku v kanálu a jeho veřejná sdílení (stránkovaná). */
class PostStatisticsViewModel(
    val chatId: Long,
    val messageId: Long,
    private val repository: StatisticsRepository,
) : ViewModel() {

    private val logger = Logger.withTag("Statistics")

    var statistics by mutableStateOf<PostStatistics?>(null)
        private set
    var graphs by mutableStateOf<List<StatGraphEntry>>(emptyList())
        private set
    var failure by mutableStateOf<UiText?>(null)
        private set

    /** Veřejná sdílení; `null` = první stránka se načítá (nebo selhala, [forwardsFailure]). */
    var forwards by mutableStateOf<List<PublicForward>?>(null)
        private set
    /** Kolik veřejných sdílení je celkem, i za dalšími stránkami. */
    var forwardsTotal by mutableStateOf(0)
        private set
    var forwardsFailure by mutableStateOf<UiText?>(null)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    private var nextOffset: String? = null

    private var loadJob: Job? = null

    init {
        load()
    }

    val canLoadMore: Boolean get() = nextOffset != null

    fun load() {
        loadJob?.cancel()
        failure = null
        statistics = null
        forwards = null
        forwardsFailure = null
        nextOffset = null
        loadJob = viewModelScope.launch {
            launch { loadForwards(offset = "") }
            repository.postStatistics(chatId, messageId)
                .onSuccess { stats ->
                    statistics = stats
                    graphs = stats.graphs
                    resolvePending(stats.graphs, { repository.graph(chatId, it) }) { kind, graph ->
                        graphs = graphs.withGraph(kind, graph)
                    }
                }
                .onFailure { error ->
                    logger.w(error) { "Statistiku příspěvku $messageId v chatu $chatId se nepodařilo načíst." }
                    failure = error.userMessage(Res.string.post_statistics_failed)
                }
        }
    }

    fun loadMore() {
        val offset = nextOffset ?: return
        if (loadingMore) return
        viewModelScope.launch { loadForwards(offset) }
    }

    private suspend fun loadForwards(offset: String) {
        loadingMore = offset.isNotEmpty()
        repository.publicForwards(chatId, messageId, offset)
            .onSuccess { page ->
                // Přeposlání na hranici stránek by se zdvojilo a dva stejné klíče shodí seznam.
                forwards = (forwards.orEmpty() + page.items).distinctBy { it.chat.id to it.messageId to it.date }
                forwardsTotal = page.total
                nextOffset = page.nextOffset
            }
            .onFailure { error ->
                logger.w(error) { "Veřejná sdílení příspěvku $messageId v chatu $chatId se nepodařilo načíst." }
                nextOffset = null
                if (forwards == null) forwardsFailure = error.userMessage(Res.string.post_statistics_public_shares_failed)
            }
        loadingMore = false
    }
}

/**
 * Dočte souběžně grafy, které server teprve počítal; každý hotový hned ohlásí přes [onLoaded].
 * Selhání jednoho ostatní nezastaví — [load] nevyhazuje, vrací [StatGraph.Failed].
 */
private fun CoroutineScope.resolvePending(
    graphs: List<StatGraphEntry>,
    load: suspend (token: String) -> StatGraph,
    onLoaded: (StatGraphKind, StatGraph) -> Unit,
) {
    graphs.forEach { entry ->
        val pending = entry.graph as? StatGraph.Pending ?: return@forEach
        launch { onLoaded(entry.kind, load(pending.token)) }
    }
}

internal fun List<StatGraphEntry>.withGraph(kind: StatGraphKind, graph: StatGraph): List<StatGraphEntry> =
    map { if (it.kind == kind) it.copy(graph = graph) else it }
