package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

/** Statistika přenesených dat: slučování položek TDLib do sítí a druhů. */
class NetworkUsageTest {

    @Test
    fun polozkyTehozDruhuSeSectouAradiOdNejvetsiho() {
        val usage = networkUsageOf(
            sinceDate = 100,
            records = listOf(
                // TDLib vrací fotku, fotku z příběhu i mizející fotku zvlášť — u nás jsou to Fotky.
                TrafficRecord(UsageNetwork.WiFi, TrafficKind.Photos, sentBytes = 10, receivedBytes = 100),
                TrafficRecord(UsageNetwork.WiFi, TrafficKind.Photos, sentBytes = 0, receivedBytes = 50),
                TrafficRecord(UsageNetwork.WiFi, TrafficKind.Messages, sentBytes = 300, receivedBytes = 400),
                TrafficRecord(UsageNetwork.Mobile, TrafficKind.Videos, sentBytes = 0, receivedBytes = 5),
            ),
        )

        assertEquals(listOf(UsageNetwork.Mobile, UsageNetwork.WiFi), usage.networks.map { it.network })
        val wifi = usage.networks.single { it.network == UsageNetwork.WiFi }
        assertEquals(
            listOf(KindTraffic(TrafficKind.Messages, 300, 400), KindTraffic(TrafficKind.Photos, 10, 150)),
            wifi.kinds,
        )
        assertEquals(310, usage.sentBytes)
        assertEquals(555, usage.receivedBytes)
    }

    @Test
    fun prazdneSiteADruhySeVynechajiHovorSDelkouNe() {
        val usage = networkUsageOf(
            sinceDate = 0,
            records = listOf(
                TrafficRecord(UsageNetwork.Roaming, TrafficKind.Music, sentBytes = 0, receivedBytes = 0),
                TrafficRecord(UsageNetwork.Mobile, TrafficKind.Calls, sentBytes = 0, receivedBytes = 0, callSeconds = 30),
            ),
        )

        assertEquals(listOf(UsageNetwork.Mobile), usage.networks.map { it.network })
        assertEquals(30, usage.networks.single().callSeconds)
        assertEquals(emptyList(), usage.networks.single().kinds)
    }
}
