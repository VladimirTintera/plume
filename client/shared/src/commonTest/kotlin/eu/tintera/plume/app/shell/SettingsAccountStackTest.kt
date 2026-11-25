package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.navigation.AccountPrivacyRoute
import eu.tintera.plume.core.navigation.ChatFolderEditRoute
import eu.tintera.plume.core.navigation.ChatFoldersRoute
import eu.tintera.plume.core.navigation.EditProfileRoute
import eu.tintera.plume.core.navigation.MyQrCodeRoute
import eu.tintera.plume.core.navigation.PrivacySettingRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.telegram.PrivacySetting
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Zásobník Nastavení po přepnutí účtu ([keepSettingsDetail]): otevřená sekce zůstane a složí se
 * za nový účet, podstránky nad ní patřily předchozímu účtu.
 */
class SettingsAccountStackTest {

    private val privacy = SettingsSectionRoute(SettingsSection.Privacy)
    private val chats = SettingsSectionRoute(SettingsSection.Chats)
    private val slot = AccountSlot("other")

    @Test
    fun sekceZustanePodstrankyZmizi() {
        val stack = mutableListOf<NavKey>(SettingsRoute, privacy, AccountPrivacyRoute, PrivacySettingRoute(PrivacySetting.PhoneNumber))

        stack.keepSettingsDetail()

        assertEquals(listOf<NavKey>(SettingsRoute, privacy), stack)
    }

    @Test
    fun upravaProfiluZustane() {
        val stack = mutableListOf<NavKey>(SettingsRoute, EditProfileRoute)

        stack.keepSettingsDetail()

        assertEquals(listOf<NavKey>(SettingsRoute, EditProfileRoute), stack)
    }

    @Test
    fun routaSUctemPredchozihoUctuZmizi() {
        val stack = mutableListOf<NavKey>(SettingsRoute, chats, ChatFoldersRoute, ChatFolderEditRoute(slot, 3))

        stack.keepSettingsDetail()

        assertEquals(listOf<NavKey>(SettingsRoute, chats), stack)
    }

    @Test
    fun sheetNadSeznamemZmizi() {
        val stack = mutableListOf<NavKey>(SettingsRoute, MyQrCodeRoute)

        stack.keepSettingsDetail()

        assertEquals(listOf<NavKey>(SettingsRoute), stack)
    }

    @Test
    fun samotnySeznamZustane() {
        val stack = mutableListOf<NavKey>(SettingsRoute)

        stack.keepSettingsDetail()

        assertEquals(listOf<NavKey>(SettingsRoute), stack)
    }
}
