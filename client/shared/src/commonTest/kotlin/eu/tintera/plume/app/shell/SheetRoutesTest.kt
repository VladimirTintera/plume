package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.navigation.BookmarksRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.DraftsRoute
import eu.tintera.plume.core.navigation.MyQrCodeRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private val slot = AccountSlot.Primary

/**
 * Sheety jako navigační destinace (`SheetRoute`): pravidla nad zásobníkem je přeskakují
 * a po zabití procesu se obnoví s ním.
 */
class SheetRoutesTest {

    private val json = Json { serializersModule = NavSavedStateConfiguration.serializersModule }

    @Test
    fun sheetyPrezijiObnovuZasobniku() {
        listOf<NavKey>(MyQrCodeRoute, DraftsRoute(slot), BookmarksRoute(slot), BookmarksRoute(slot, chatId = 7, archive = true))
            .forEach { route -> assertEquals(route, json.roundTrip(route)) }
    }

    @Test
    fun zasobnikBezSheetuJeTatazInstance() {
        val stack = listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 7))

        assertSame(stack, stack.withoutTrailingSheets(), "identita kvůli withProfileFollowing")
        assertEquals(emptyList(), stack.trailingSheets())
    }

    @Test
    fun sheetNavrchuSePravidlumNadPanelyNeukaze() {
        val stack = listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 7), BookmarksRoute(slot, 7), MyQrCodeRoute)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 7)), stack.withoutTrailingSheets())
        assertEquals(listOf<NavKey>(BookmarksRoute(slot, 7), MyQrCodeRoute), stack.trailingSheets())
    }

    @Test
    fun sheetUprostredZasobnikuNavrchuNeni() {
        val stack = listOf<NavKey>(ChatsRoute, DraftsRoute(slot), ConversationRoute(slot, 7))

        assertSame(stack, stack.withoutTrailingSheets())
    }

    private fun Json.roundTrip(route: NavKey): NavKey =
        decodeFromString(
            PolymorphicSerializer(NavKey::class),
            encodeToString(PolymorphicSerializer(NavKey::class), route),
        )
}
