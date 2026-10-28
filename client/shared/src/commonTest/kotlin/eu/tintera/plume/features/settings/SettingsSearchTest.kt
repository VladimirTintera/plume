package eu.tintera.plume.features.settings

import eu.tintera.plume.core.navigation.AccountPrivacyRoute
import eu.tintera.plume.core.navigation.EditProfileRoute
import eu.tintera.plume.core.navigation.PrivacySettingRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.telegram.PrivacySetting
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchTest {

    private fun entry(title: String, keywords: String = "", path: String = "") = ResolvedSettingsEntry(
        entry = SettingsSearchEntry(Res.string.settings_theme, listOf(SettingsSectionRoute(SettingsSection.Appearance))),
        title = title,
        keywords = keywords,
        path = path,
    )

    private val lastSeen = entry("Naposledy viděn a online", keywords = "online, stav", path = "Soukromí › Soukromí účtu")
    private val appLock = entry("Zámek aplikace", keywords = "PIN, otisk prstu", path = "Soukromí")
    private val theme = entry("Motiv", keywords = "tmavý režim, noční režim", path = "Vzhled")
    private val privacy = entry("Soukromí")
    private val accountPrivacy = entry("Soukromí účtu", path = "Soukromí")
    private val all = listOf(privacy, lastSeen, accountPrivacy, appLock, theme)

    private fun titles(query: String) = searchSettings(query, all).map { it.title }

    @Test
    fun zacatekSlovaBezDiakritikyAVelikosti() {
        assertEquals(listOf("Naposledy viděn a online"), titles("viden"))
        assertEquals(listOf("Naposledy viděn a online"), titles("NAPOSLEDY"))
    }

    @Test
    fun vsechnaSlovaDotazuMusiSedet() {
        assertEquals(listOf("Zámek aplikace"), titles("zam apl"))
        assertEquals(emptyList(), titles("zámek motiv"))
    }

    @Test
    fun synonymaNajdouVolbu() {
        assertEquals(listOf("Motiv"), titles("tmavý"))
        assertEquals(listOf("Zámek aplikace"), titles("pin"))
    }

    @Test
    fun nazevZacinajiciDotazemJdePrvni() {
        // „online" je v názvu i v synonymech téže volby; „Soukromí" začíná dotazem a jde před
        // „Soukromí účtu" jen pořadím rejstříku — obojí začíná dotazem.
        assertEquals(listOf("Soukromí", "Soukromí účtu"), titles("soukromi"))
        val synonymOnly = entry("Aktivní zařízení", keywords = "soukromí")
        assertEquals("Soukromí", searchSettings("soukr", all + synonymOnly).first().title)
        assertEquals("Aktivní zařízení", searchSettings("soukr", all + synonymOnly).last().title)
    }

    @Test
    fun prazdnyDotazNicNevraci() {
        assertTrue(searchSettings("   ", all).isEmpty())
    }

    @Test
    fun rejstrikVedeVzdyPresSekciNeboUpravuProfilu() {
        SETTINGS_SEARCH_INDEX.forEach { entry ->
            val first = entry.screens.first()
            assertTrue(first is SettingsSectionRoute || first == EditProfileRoute, "${entry.title} začíná $first")
        }
    }

    @Test
    fun kazdeNastaveniSoukromiJeVRejstriku() {
        PrivacySetting.entries.forEach { setting ->
            assertTrue(
                SETTINGS_SEARCH_INDEX.any { it.screens == listOf(SettingsSectionRoute(SettingsSection.Privacy), AccountPrivacyRoute, PrivacySettingRoute(setting)) },
                "$setting chybí",
            )
        }
    }
}
