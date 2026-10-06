package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.LiveLocation
import dev.g000sha256.tdl.dto.Location
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageLiveLocation
import dev.g000sha256.tdl.toResult
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Vlastní živá poloha, kterou je potřeba posouvat: jedna zpráva v jednom chatu. Sdílet jde do víc
 * chatů naráz a každá zpráva se posouvá zvlášť.
 *
 * [livePeriod] je doba od odeslání, jak ji TDLib nese, a při posunu se musí poslat ZPÁTKY beze
 * změny — jinak by úprava zprávy dobu sdílení prodloužila nebo zkrátila.
 */
data class ActiveLiveLocation(
    val chatId: Long,
    val messageId: Long,
    val livePeriod: Int,
    /** Konec sdílení v sekundách od epochy, [Long.MAX_VALUE] u sdílení „dokud nevypnu". */
    val until: Long,
)

/** Poloha zařízení, jak ji hlásí platforma: s přesností v metrech a směrem pohybu (0 = neznámý). */
data class LiveFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double = 0.0,
    /** Směr pohybu ve stupních 1–360 po směru hodin od severu, 0 = neznámý — viz [headingOf]. */
    val heading: Int = 0,
)

/**
 * Sdílení živé polohy z tohoto účtu.
 *
 * **Které zprávy se mají posouvat, drží TDLib**, ne aplikace: `UpdateActiveLiveLocationMessages`
 * nese celý seznam a s databází zpráv přežije i restart aplikace. Seznam proto žije tady,
 * eagerně od startu procesu — update chodí jen na změnu a po startu přijde jednou.
 *
 * Polohu do nich posílá platforma (Android: služba v popředí `LiveLocationService`), která se
 * podle [active] spouští a zastavuje sama.
 */
interface LiveLocationRepository {
    /** Vlastní živé polohy, které ještě běží. Prošlé se vyřazují i bez updatu od TDLib. */
    val active: StateFlow<List<ActiveLiveLocation>>

    /**
     * Někdo se právě podívá na naši živou polohu — TDLib čeká, že ji aplikace hned posune
     * („the application is expected to update the live location").
     */
    val viewed: Flow<Unit>

    /** Posune všechny [active] zprávy na [fix]. Selhání jedné ostatní nezastaví. */
    suspend fun update(fix: LiveFix)

    /** Zastaví sdílení v jedné zprávě. */
    suspend fun stop(chatId: Long, messageId: Long): Result<Unit>

    /** Zastaví sdílení všude — „Zastavit" v notifikaci. */
    suspend fun stopAll()
}

class TdlLiveLocationRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : LiveLocationRepository {

    private val logger = Logger.withTag("LiveLocation")
    private val list = MutableStateFlow<List<ActiveLiveLocation>>(emptyList())

    override val active: StateFlow<List<ActiveLiveLocation>> = list.asStateFlow()

    override val viewed: Flow<Unit> = holder.updates { messageLiveLocationViewedUpdates }.map { }

    init {
        scope.launch {
            holder.updates { activeLiveLocationMessagesUpdates }.collect { update ->
                list.value = activeLiveLocations(update.messages.toList(), Clock.System.now().epochSeconds)
                logger.i { "Aktivní živé polohy: ${list.value.size}" }
            }
        }
        // Nový účet nemá s předchozím nic společného — služba se musí zastavit hned.
        scope.launch { holder.replaced.collect { list.value = emptyList() } }
    }

    override suspend fun update(fix: LiveFix) {
        val now = Clock.System.now().epochSeconds
        list.value.filter { it.until > now }.forEach { live ->
            holder.client.editMessageLiveLocation(
                chatId = live.chatId,
                messageId = live.messageId,
                location = LiveLocation(
                    location = Location(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        horizontalAccuracy = fix.accuracyMeters.coerceIn(0.0, MAX_ACCURACY_METERS),
                    ),
                    livePeriod = live.livePeriod,
                    heading = fix.heading,
                    proximityAlertRadius = 0,
                ),
            ).toResult().onFailure { failure ->
                logger.w(failure) { "Živou polohu ${live.chatId}/${live.messageId} nejde posunout." }
            }
        }
    }

    override suspend fun stop(chatId: Long, messageId: Long): Result<Unit> =
        // `location = null` sdílení ukončí — TDLib pak zprávu ze seznamu aktivních vyřadí sám.
        holder.client.editMessageLiveLocation(chatId = chatId, messageId = messageId, location = null)
            .toResult()
            .map { }
            .onSuccess { list.value = list.value.filterNot { it.chatId == chatId && it.messageId == messageId } }

    override suspend fun stopAll() {
        list.value.forEach { live ->
            stop(live.chatId, live.messageId).onFailure { failure ->
                logger.w(failure) { "Sdílení ${live.chatId}/${live.messageId} nejde zastavit." }
            }
        }
    }
}

/** Zprávy od TDLib na seznam toho, co se má posouvat — bez prošlých a bez jiného obsahu. */
internal fun activeLiveLocations(messages: List<Message>, now: Long): List<ActiveLiveLocation> =
    messages.mapNotNull { message ->
        val content = message.content as? MessageLiveLocation ?: return@mapNotNull null
        val period = content.location.livePeriod
        ActiveLiveLocation(
            chatId = message.chatId,
            messageId = message.id,
            livePeriod = period,
            until = liveLocationUntil(sentAt = message.date, livePeriod = period),
        )
    }.filter { it.until > now }

/**
 * Doby sdílení, které se nabízejí — jako u oficiálních klientů. TDLib bere 60 s až den, nebo
 * [LIVE_FOREVER] („dokud nevypnu").
 */
val LIVE_LOCATION_PERIODS: List<Int> = listOf(15 * 60, 60 * 60, 8 * 60 * 60, LIVE_FOREVER)

/**
 * Pošle se tahle poloha? Platforma hlásí polohu po pár vteřinách, a každý posun je úprava zprávy
 * na serveru — u všech chatů, kam se sdílí. Posílá se proto jen to, co ostatní uvidí: pohyb
 * o kus, který je na mapě znát, nebo jednou za čas, ať je poloha čerstvá. A hned, když se
 * na ni někdo dívá ([viewed]) — tak to TDLib chce.
 *
 * [last] je naposledy odeslaná poloha s časem odeslání v ms, `null` = ještě nic.
 */
fun shouldSendLiveFix(last: Pair<LiveFix, Long>?, fix: LiveFix, nowMs: Long, viewed: Boolean = false): Boolean {
    if (last == null || viewed) return true
    val (previous, sentAt) = last
    if (nowMs - sentAt >= LIVE_REFRESH_MS) return true
    if (nowMs - sentAt < LIVE_MIN_INTERVAL_MS) return false
    return distanceMeters(previous, fix) >= LIVE_MIN_DISTANCE_METERS || previous.heading != fix.heading && fix.heading != 0
}

/**
 * Směr pohybu pro TDLib: 1–360, `0` = neznámý. Platforma hlásí 0–360 (sever = 0) a „bez směru"
 * zvlášť; sever proto musí být 360, jinak by se ztratil v „neznámém".
 */
fun headingOf(bearing: Float?): Int {
    bearing ?: return 0
    val degrees = bearing.roundToInt().mod(360)
    return if (degrees == 0) 360 else degrees
}

/** Vzdálenost po povrchu Země (haversine) — na pár desítek metrů víc přesnosti netřeba. */
internal fun distanceMeters(a: LiveFix, b: LiveFix): Double {
    val lat1 = a.latitude * PI / 180
    val lat2 = b.latitude * PI / 180
    val dLat = lat2 - lat1
    val dLon = (b.longitude - a.longitude) * PI / 180
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** TDLib bere přesnost 0–1500 m. */
private const val MAX_ACCURACY_METERS = 1500.0

/** Pohyb, který je na mapě ve zprávě znát. */
private const val LIVE_MIN_DISTANCE_METERS = 30.0

/** Častěji se neposílá ani při pohybu — každý posun je úprava zprávy na serveru. */
private const val LIVE_MIN_INTERVAL_MS = 10_000L

/** Bez pohybu se poloha obnoví jednou za čas, ať ostatní vidí, že sdílení žije. */
private const val LIVE_REFRESH_MS = 2 * 60_000L
