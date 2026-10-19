package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.AccountPrivacyRoute
import eu.tintera.plume.core.navigation.AppLockRoute
import eu.tintera.plume.core.navigation.PrivacySettingRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.navigation.TwoStepRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.telegram.PrivacySetting
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Podstránka otevřená ze sekce Nastavení ([openSubpage]). Od šířky Medium je sekce vidět vedle
 * podstránky a ťuknutí na další řádek dřív vršilo Dvoufázové ověření, Zámek aplikace a další
 * přes sebe — zpět pak procházelo všechny, na které se kdy ťuklo.
 */
class SettingsSubpageTest {

    private val privacy = SettingsSectionRoute(SettingsSection.Privacy)

    @Test
    fun dalsiPodstrankaZeSekceNahradiOtevrenou() {
        val stack = mutableListOf<NavKey>(SettingsRoute, privacy)

        stack.openSubpage(privacy, TwoStepRoute)
        stack.openSubpage(privacy, AppLockRoute)
        stack.openSubpage(privacy, TwoStepRoute)

        assertEquals(listOf<NavKey>(SettingsRoute, privacy, TwoStepRoute), stack)
    }

    /** I s tím, co se otevřelo z podstránky (jedno nastavení soukromí nad Soukromím účtu). */
    @Test
    fun nahradiIVnorenePodstranky() {
        val setting = PrivacySettingRoute(PrivacySetting.PhoneNumber)
        val stack = mutableListOf<NavKey>(SettingsRoute, privacy, AccountPrivacyRoute, setting)

        stack.openSubpage(privacy, AppLockRoute)

        assertEquals(listOf<NavKey>(SettingsRoute, privacy, AppLockRoute), stack)
    }

    /** Řádek otevřené podstránky ji nechá a zavře jen, co leží nad ní. */
    @Test
    fun tataPodstrankaZustaneAZavreCoJeNadNi() {
        val setting = PrivacySettingRoute(PrivacySetting.PhoneNumber)
        val stack = mutableListOf<NavKey>(SettingsRoute, privacy, AccountPrivacyRoute, setting)

        stack.openSubpage(privacy, AccountPrivacyRoute)

        assertEquals(listOf<NavKey>(SettingsRoute, privacy, AccountPrivacyRoute), stack)
    }

    /** Sekce na odchodu (už není v zásobníku) nesmí nic otevřít — ani nad jinou sekcí. */
    @Test
    fun odchazejiciSekceNicNeotevre() {
        val chats = SettingsSectionRoute(SettingsSection.Chats)
        val stack = mutableListOf<NavKey>(SettingsRoute, chats)

        stack.openSubpage(privacy, TwoStepRoute)

        assertEquals(listOf<NavKey>(SettingsRoute, chats), stack)
    }
}
