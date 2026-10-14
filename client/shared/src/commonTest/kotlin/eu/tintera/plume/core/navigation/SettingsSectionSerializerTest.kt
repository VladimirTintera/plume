package eu.tintera.plume.core.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sekce Nastavení v uloženém zásobníku ([SettingsSectionSerializer]): zrušené jméno z dřívější
 * verze (`General` před přeskupením) obnovu neshodí, ale otevře první sekci.
 */
class SettingsSectionSerializerTest {

    @Test
    fun znamaSekceSeVratiBezZmeny() {
        SettingsSection.entries.forEach { section ->
            val json = Json.encodeToString(SettingsSectionRoute.serializer(), SettingsSectionRoute(section))
            assertEquals(SettingsSectionRoute(section), Json.decodeFromString(SettingsSectionRoute.serializer(), json))
        }
    }

    @Test
    fun ukladaSeJmenem() {
        assertEquals("""{"section":"Privacy"}""", Json.encodeToString(SettingsSectionRoute.serializer(), SettingsSectionRoute(SettingsSection.Privacy)))
    }

    @Test
    fun zrusenaSekceOtevrePrvni() {
        val restored = Json.decodeFromString(SettingsSectionRoute.serializer(), """{"section":"General"}""")
        assertEquals(SettingsSectionRoute(SettingsSection.entries.first()), restored)
    }
}
