package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Statistiky kanálu, skupiny a příspěvku (`getChatStatistics`, `getMessageStatistics`).
 *
 * Server je vydá jen správcům chatů, které na ně mají právo (`SupergroupFullInfo.canGetStatistics`,
 * u kanálů zhruba od 500 odběratelů) — a u příspěvku jen tehdy, když to dovolí
 * `MessageProperties.canGetStatistics`.
 */
interface StatisticsRepository {

    suspend fun chatStatistics(chatId: Long): Result<ChatStatistics>

    /**
     * Graf, který server poslal jen jako příslib ([StatGraph.Pending]). Nikdy nevyhodí —
     * nepovedené načtení je [StatGraph.Failed], ostatní grafy na něm nezávisí.
     */
    suspend fun graph(chatId: Long, token: String): StatGraph

    suspend fun postStatistics(chatId: Long, messageId: Long): Result<PostStatistics>

    /** Veřejná přeposlání příspěvku do jiných kanálů a sdílení do příběhů; [offset] z předchozí stránky. */
    suspend fun publicForwards(chatId: Long, messageId: Long, offset: String = ""): Result<PublicForwardsPage>
}

/** Hodnota za sledované období a srovnání s předchozím obdobím stejné délky. */
data class StatValue(
    val value: Double,
    val previous: Double,
    /** Změna proti [previous] v procentech; 0, když srovnání není (nový kanál). */
    val growthPercent: Double,
)

/** Sledované období v sekundách unixu. */
data class StatPeriod(val start: Int, val end: Int)

/** Co graf ukazuje. Nadpis k druhu dodá UI — v JSONu od serveru žádný není. */
enum class StatGraphKind {
    MemberCount,
    Joins,
    Mutes,
    ViewsByHour,
    ViewsBySource,
    JoinsBySource,
    Languages,
    PostInteractions,
    PostReactions,
    StoryInteractions,
    StoryReactions,
    InstantView,
    MessageContent,
    Actions,
    ActivityByDay,
    ActivityByWeek,
}

data class StatGraphEntry(val kind: StatGraphKind, val graph: StatGraph)

sealed interface StatGraph {
    data class Ready(val chart: StatChart) : StatGraph

    /** Server graf dopočítává; načte se zvlášť přes [StatisticsRepository.graph]. */
    data class Pending(val token: String) : StatGraph

    /**
     * Graf není: chyba od serveru (`statisticalGraphError` — jeho text se uživateli neukazuje),
     * nebo JSON, kterému nerozumíme.
     */
    data object Failed : StatGraph
}

/** Jak se řady kreslí — typ první řady v JSONu (`types`), ostatní bývají stejného typu. */
enum class StatChartKind { Line, Step, Bar, Area }

data class StatSeries(
    /** Název řady od serveru (jazyk u grafu jazyků, „Views" u zhlédnutí). */
    val name: String,
    val values: List<Double>,
)

/**
 * Graf ve formátu, který Telegram posílá všem klientům (`StatisticalGraphData.json_data`):
 * sloupce `x` a `y0…yN`, typy, názvy a příznaky. Barvy a formátovací funkce v JavaScriptu
 * se vědomě nečtou — barvy dává motiv aplikace, formát datum a čísla jazyk.
 */
data class StatChart(
    /** Body na ose x v milisekundách unixu. */
    val x: List<Long>,
    val series: List<StatSeries>,
    val kind: StatChartKind,
    /** Řady se sčítají na sebe (sloupce nebo plochy). */
    val stacked: Boolean,
    /** Plochy se přepočítají na podíl ze sta procent v každém bodě (jazyky, zdroje). */
    val percentage: Boolean,
    /**
     * Každá řada má vlastní měřítko (`y_scaled`: zhlédnutí vedle sdílení). UI je pak kreslí
     * jako samostatné grafy nad sebou — dvě osy y v jednom grafu se číst nedají.
     */
    val separateScales: Boolean,
) {
    /** Body po hodinách (zhlédnutí podle hodiny dne), ne po dnech — jinak se popisuje čas. */
    val hourly: Boolean get() = x.size >= 2 && x[1] - x[0] < DAY_MS

    /** Všechno nula — kanál, kterému se v tom ohledu nic nestalo; graf se nekreslí. */
    val isEmpty: Boolean get() = series.all { s -> s.values.all { it == 0.0 } }

    /**
     * Nejvýš [max] řad: nejmenší (podle součtu) se sečtou do jedné s názvem [otherName].
     * Graf jazyků jich mívá přes deset a víc barev od sebe oko nerozezná.
     */
    fun folded(max: Int, otherName: String): StatChart {
        if (series.size <= max) return this
        val ranked = series.sortedByDescending { it.values.sum() }
        val kept = ranked.take(max - 1).toSet()
        val rest = series.filterNot { it in kept }
        val other = StatSeries(
            name = otherName,
            values = x.indices.map { i -> rest.sumOf { it.values[i] } },
        )
        // Pořadí zůstává jako od serveru, ať barva patří pořád téže řadě.
        return copy(series = series.filter { it in kept } + other)
    }
}

sealed interface ChatStatistics {
    val period: StatPeriod
    val graphs: List<StatGraphEntry>

    data class Channel(
        override val period: StatPeriod,
        val members: StatValue,
        val meanViews: StatValue,
        val meanShares: StatValue,
        val meanReactions: StatValue,
        val meanStoryViews: StatValue,
        val meanStoryShares: StatValue,
        val meanStoryReactions: StatValue,
        /** Kolik procent odběratelů má zapnutá upozornění. */
        val notificationsPercent: Double,
        override val graphs: List<StatGraphEntry>,
        /** Nedávné příspěvky a příběhy s počty, od nejnovějšího. */
        val recent: List<RecentInteraction>,
    ) : ChatStatistics

    data class Group(
        override val period: StatPeriod,
        val members: StatValue,
        val messages: StatValue,
        val viewers: StatValue,
        val senders: StatValue,
        override val graphs: List<StatGraphEntry>,
        val topSenders: List<TopSender>,
        val topAdmins: List<TopAdmin>,
        val topInviters: List<TopInviter>,
    ) : ChatStatistics
}

/** Příspěvek ([messageId]), nebo příběh ([storyId]) z přehledu kanálu. */
data class RecentInteraction(
    val messageId: Long?,
    val storyId: Int?,
    /** Náhled příspěvku; `null` u příběhu nebo zprávy, kterou se nepodařilo načíst. */
    val preview: UiText?,
    /** Kdy příspěvek vyšel; `null`, když se nenačetl. */
    val date: Int?,
    val views: Int,
    val forwards: Int,
    val reactions: Int,
)

data class TopSender(val user: SenderInfo, val messages: Int, val averageCharacters: Int)

data class TopAdmin(val user: SenderInfo, val deletedMessages: Int, val bannedUsers: Int, val restrictedUsers: Int)

data class TopInviter(val user: SenderInfo, val addedMembers: Int)

/** Statistika jednoho příspěvku v kanálu. */
data class PostStatistics(
    val preview: UiText,
    val date: Int,
    val views: Int,
    val forwards: Int,
    val reactions: Int,
    val graphs: List<StatGraphEntry>,
)

/** Kam příspěvek veřejně přeposlali: zpráva v jiném kanálu, nebo příběh. */
data class PublicForward(
    val chat: SenderInfo,
    /** Přeposlaná zpráva v [chat]; `null` u příběhu. */
    val messageId: Long?,
    val date: Int,
    val views: Int,
)

data class PublicForwardsPage(
    /** Kolik přeposlání je celkem (i za dalšími stránkami). */
    val total: Int,
    val items: List<PublicForward>,
    /** `null` = další stránka není. */
    val nextOffset: String?,
)

/**
 * JSON grafu od serveru na [StatChart]; `null`, když mu nerozumíme (žádná osa x, žádná
 * řada, sloupce různé délky). Nikdy nevyhodí — formát patří serveru a může se změnit.
 */
internal fun parseStatChart(json: String): StatChart? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    val columns = root["columns"]?.jsonArray?.map { it.jsonArray } ?: return null
    val types = root.stringMap("types")
    val names = root.stringMap("names")
    val xColumn = columns.firstOrNull { types[it.key()] == "x" } ?: columns.firstOrNull { it.key() == "x" } ?: return null
    val x = xColumn.drop(1).map { it.jsonPrimitive.longOrNull ?: return null }
    if (x.isEmpty()) return null
    val ys = columns.filter { it !== xColumn }
    val series = ys.map { column ->
        StatSeries(
            name = names[column.key()] ?: column.key(),
            values = column.drop(1).map { it.jsonPrimitive.doubleOrNull ?: 0.0 },
        )
    }
    if (series.isEmpty() || series.any { it.values.size != x.size }) return null
    StatChart(
        x = x,
        series = series,
        kind = when (types[ys.first().key()]) {
            "bar" -> StatChartKind.Bar
            "area" -> StatChartKind.Area
            "step" -> StatChartKind.Step
            else -> StatChartKind.Line
        },
        stacked = root.flag("stacked"),
        percentage = root.flag("percentage"),
        separateScales = root.flag("y_scaled") && series.size > 1,
    )
}.getOrNull()

private fun JsonArray.key(): String = first().jsonPrimitive.content

private fun JsonObject.stringMap(name: String): Map<String, String> =
    (this[name] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()

private fun JsonObject.flag(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull == true

private const val DAY_MS = 24L * 60 * 60 * 1000
