package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.FileTypeLivePhotoVideo
import dev.g000sha256.tdl.dto.FileTypeNone
import dev.g000sha256.tdl.dto.FileTypePhoto
import dev.g000sha256.tdl.dto.FileTypeSelfDestructingLivePhotoVideo
import dev.g000sha256.tdl.dto.FileTypeSelfDestructingPhoto
import dev.g000sha256.tdl.dto.FileTypeVideo
import dev.g000sha256.tdl.dto.NetworkStatisticsEntry
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryCall
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryFile
import dev.g000sha256.tdl.dto.NetworkTypeNone
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import kotlin.test.Test
import kotlin.test.assertEquals

/** Položky TDLib, které sdílejí počítadlo, se nesčítají (čísla z emulátoru). */
class NetworkStatisticsSlotsTest {

    @Test
    fun typySeSpolecnymPocitadlemSeZapocitajiJednou() {
        val wifi = NetworkTypeWiFi()
        val entries = arrayOf<NetworkStatisticsEntry>(
            NetworkStatisticsEntryFile(FileTypePhoto(), wifi, 26_544, 849_729),
            NetworkStatisticsEntryFile(FileTypeSelfDestructingPhoto(), wifi, 26_544, 849_729),
            NetworkStatisticsEntryFile(FileTypeLivePhotoVideo(), wifi, 26_544, 849_729),
            NetworkStatisticsEntryFile(FileTypeSelfDestructingLivePhotoVideo(), wifi, 26_544, 849_729),
            NetworkStatisticsEntryFile(FileTypeVideo(), wifi, 0, 1_000),
            NetworkStatisticsEntryFile(FileTypeNone(), wifi, 6_385_176, 14_971_256),
            NetworkStatisticsEntryFile(FileTypeNone(), NetworkTypeNone(), 5, 5),
            NetworkStatisticsEntryCall(wifi, 10, 20, 61.5),
        )

        val usage = networkUsageOf(0, entries.toRecords())

        val kinds = usage.networks.single().kinds.associateBy { it.kind }
        // Živá fotka je pro TDLib fotka („photos"), ne video.
        assertEquals(KindTraffic(TrafficKind.Photos, 26_544, 849_729), kinds[TrafficKind.Photos])
        assertEquals(KindTraffic(TrafficKind.Videos, 0, 1_000), kinds[TrafficKind.Videos])
        assertEquals(KindTraffic(TrafficKind.Messages, 6_385_176, 14_971_256), kinds[TrafficKind.Messages])
        assertEquals(KindTraffic(TrafficKind.Calls, 10, 20), kinds[TrafficKind.Calls])
        assertEquals(61, usage.networks.single().callSeconds)
    }
}
