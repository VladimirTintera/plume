package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.InlineQueryResultLocation
import dev.g000sha256.tdl.dto.InlineQueryResultVenue
import dev.g000sha256.tdl.dto.Location
import eu.tintera.plume.core.location.GeoPoint
import dev.g000sha256.tdl.toResult

/**
 * Hledání míst — přes inline bota, kterého jmenuje server (`venue_search_bot_username`), stejně
 * jako GIFy hledá GIF bot. Vlastní mapy ani poskytovatele míst aplikace nemá.
 *
 * **Bez polohy uživatele bot nevrací nic použitelného**: ověřeno na emulátoru, na „Staroměstské
 * náměstí Praha" i na samotné „Praha" přišel supermarket z amerického předměstí. Hledá se proto
 * vždycky kolem [near]; prázdný dotaz vrací místa v okolí.
 */
interface PlaceRepository {
    /** Místa k dotazu kolem [near]. [chatId] chce inline bot vědět, kam se hledá. */
    suspend fun search(chatId: Long, query: String, near: GeoPoint): Result<List<Place>>
}

/**
 * Místo, jak ho vrátí hledání a jak odchází do ankety (`Venue`). [provider], [id] a [type] jsou
 * údaje poskytovatele (Foursquare), server je u místa drží.
 */
data class Place(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val provider: String = "",
    val id: String = "",
    val type: String = "",
)

class TdlPlaceRepository(private val holder: TelegramClientHolder) : PlaceRepository {

    private val logger = Logger.withTag("Places")
    private val bot = InlineBot(holder, option = "venue_search_bot_username", fallback = DEFAULT_VENUE_BOT)

    override suspend fun search(chatId: Long, query: String, near: GeoPoint): Result<List<Place>> {
        val botUserId = bot.userId().getOrElse { return Result.failure(it) }
        return holder.client.getInlineQueryResults(
            botUserId = botUserId,
            chatId = chatId,
            userLocation = Location(latitude = near.latitude, longitude = near.longitude, horizontalAccuracy = 0.0),
            query = query,
            offset = "",
        )
            .toResult()
            .map { results ->
                results.results.filterNotNull().mapNotNull { result ->
                    when (result) {
                        is InlineQueryResultVenue -> result.venue.let { venue ->
                            Place(
                                title = venue.title,
                                address = venue.address,
                                latitude = venue.location.latitude,
                                longitude = venue.location.longitude,
                                provider = venue.provider,
                                id = venue.id,
                                type = venue.type,
                            )
                        }
                        is InlineQueryResultLocation -> Place(
                            title = result.title,
                            address = "",
                            latitude = result.location.latitude,
                            longitude = result.location.longitude,
                        )
                        else -> null
                    }
                }.distinctBy { it.id.ifEmpty { "${it.latitude},${it.longitude}" } }
            }
            .onFailure { logger.w(it) { "Hledání míst selhalo." } }
    }

    private companion object {
        /** Kdyby server volbu neposlal. */
        const val DEFAULT_VENUE_BOT = "foursquare"
    }
}
