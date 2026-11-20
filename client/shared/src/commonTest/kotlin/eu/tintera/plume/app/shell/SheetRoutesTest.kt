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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

        assertSame(stack, stack.withoutSheets(), "identita kvůli withProfileFollowing")
        assertEquals(emptyList(), stack.trailingSheets())
    }

    @Test
    fun sheetNavrchuSePravidlumNadPanelyNeukaze() {
        val stack = listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 7), BookmarksRoute(slot, 7), MyQrCodeRoute)

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 7)), stack.withoutSheets())
        assertEquals(listOf<NavKey>(BookmarksRoute(slot, 7), MyQrCodeRoute), stack.trailingSheets())
    }

    @Test
    fun sheetUprostredZasobnikuSeNekresli() {
        // Odkaz tg://new/group nad otevřeným přehledem konceptů: sheet pod obrazovkou se nesmí
        // kreslit ani jako předchozí scéna v náhledu gesta zpět (jeho okno by gesto sebralo).
        val stack = listOf<NavKey>(ChatsRoute, DraftsRoute(slot), ConversationRoute(slot, 7))

        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 7)), stack.withoutSheets())
        assertEquals(emptyList(), stack.trailingSheets())
        assertTrue(stack.hasBuriedSheet())
    }

    @Test
    fun pohrbenySheetZmiziNavrchuZustane() {
        val stack = mutableListOf<NavKey>(ChatsRoute, DraftsRoute(slot), ConversationRoute(slot, 7), MyQrCodeRoute)

        assertTrue(stack.dropBuriedSheets())
        assertEquals(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 7), MyQrCodeRoute), stack)
        assertFalse(stack.hasBuriedSheet())
        assertFalse(stack.dropBuriedSheets(), "druhé volání nemá co odebrat")
    }

    @Test
    fun sheetyNavrchuNejsouPohrbene() {
        val stack = mutableListOf<NavKey>(ChatsRoute, BookmarksRoute(slot, 7), MyQrCodeRoute)

        assertFalse(stack.hasBuriedSheet())
        assertFalse(stack.dropBuriedSheets())
        assertEquals(listOf<NavKey>(ChatsRoute, BookmarksRoute(slot, 7), MyQrCodeRoute), stack)
    }

    private fun Json.roundTrip(route: NavKey): NavKey =
        decodeFromString(
            PolymorphicSerializer(NavKey::class),
            encodeToString(PolymorphicSerializer(NavKey::class), route),
        )
}
