package eu.tintera.plume.app.shell

import eu.tintera.plume.core.accounts.AccountSlot
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val slot = AccountSlot.Primary

/**
 * Routy archivu musí být registrované v [NavSavedStateConfiguration] — `NavKey` je otevřená
 * polymorfní hierarchie, takže chybějící registrace projde překladem a spadne až za běhu
 * při obnově zásobníku po zabití procesu.
 */
class ArchiveNavigationTest {

    private val json = Json { serializersModule = NavSavedStateConfiguration.serializersModule }

    @Test
    fun archivniKonverzacePrezijeObnovuZasobniku() {
        val route: NavKey = ArchiveConversationRoute(slot, chatId = 42, messageId = 100)

        assertEquals(route, json.roundTrip(route))
    }

    @Test
    fun profilZArchivniKonverzacePrezijeObnovuZasobniku() {
        val route: NavKey = ArchiveChatProfileRoute(slot, chatId = 42)

        assertEquals(route, json.roundTrip(route))
    }

    private fun Json.roundTrip(route: NavKey): NavKey =
        decodeFromString(
            PolymorphicSerializer(NavKey::class),
            encodeToString(PolymorphicSerializer(NavKey::class), route),
        )
}
