package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.dp
import eu.tintera.plume.features.conversation.items.ContextVerb
import eu.tintera.plume.features.conversation.items.MenuSlot
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.features.conversation.items.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rozdělení nabídky nad zprávou do skupin a dvou úrovní a její výška. */
class MessageMenuLevelsTest {

    private fun arrange(vararg items: MessageMenuAction) = arrangeMenu(items.toList()) { it.slot }

    /** Cizí text: nahoře odpověď, kopírování, přeposlání, překlad a nálezy, zbytek pod „Další". */
    @Test
    fun ciziTextMaNahoreHlavniAkceAZbytekVDalsi() {
        val parcel = MessageMenuAction.ContextItem(ContextVerb.TrackParcel, 0)
        val levels = arrange(
            MessageMenuAction.Reply,
            MessageMenuAction.Copy,
            MessageMenuAction.Forward,
            MessageMenuAction.Translate,
            parcel,
            MessageMenuAction.SelectText,
            MessageMenuAction.Explain,
            MessageMenuAction.Info,
            MessageMenuAction.ToggleBookmark,
            MessageMenuAction.Remind,
            MessageMenuAction.Pin,
            MessageMenuAction.Select,
            MessageMenuAction.Report,
            MessageMenuAction.Delete,
        )
        assertEquals(MessageMenuAction.Info, levels.header)
        assertEquals(
            listOf(
                listOf(MessageMenuAction.Reply, MessageMenuAction.Copy, MessageMenuAction.Forward, MessageMenuAction.Translate),
                listOf(parcel),
            ),
            levels.top,
        )
        assertEquals(listOf(MessageMenuAction.Delete), levels.destructive)
        assertEquals(
            listOf(
                listOf(MessageMenuAction.SelectText, MessageMenuAction.Explain),
                listOf(MessageMenuAction.ToggleBookmark, MessageMenuAction.Remind, MessageMenuAction.Pin),
                listOf(MessageMenuAction.Select),
                listOf(MessageMenuAction.Report),
            ),
            levels.more,
        )
        // Hlavička v první skupině, pak nálezy, „Další" a Smazat.
        assertEquals(listOf(4, 1, 1, 1), levels.topGroupSizes)
        // Řádek zpět je v první skupině „Další".
        assertEquals(listOf(3, 3, 1, 1), levels.moreGroupSizes)
    }

    /** Pořadí skupin dává [MenuSlot], ne pořadí kandidátů; uvnitř skupiny pořadí kandidátů zůstává. */
    @Test
    fun skupinyJsouVPoradiSlotu() {
        val levels = arrange(
            MessageMenuAction.Delete,
            MessageMenuAction.StopPoll,
            MessageMenuAction.Forward,
            MessageMenuAction.RetractVote,
            MessageMenuAction.Reply,
            MessageMenuAction.Info,
        )
        assertEquals(
            listOf(
                listOf(MessageMenuAction.Forward, MessageMenuAction.Reply),
                listOf(MessageMenuAction.StopPoll, MessageMenuAction.RetractVote),
            ),
            levels.top,
        )
        assertEquals(listOf(MessageMenuAction.Delete), levels.destructive)
    }

    /** „Další" kvůli jedné položce by byly dva kroky navíc — jediná položka jde nahoru. */
    @Test
    fun jedinaPolozkaVDalsiJdeNahoru() {
        val levels = arrange(MessageMenuAction.Reply, MessageMenuAction.Info, MessageMenuAction.Select, MessageMenuAction.Delete)
        assertTrue(levels.more.isEmpty())
        assertEquals(listOf(listOf(MessageMenuAction.Reply), listOf(MessageMenuAction.Select)), levels.top)
        // Bez „Další" nemá řádek ani skupinu.
        assertEquals(listOf(1, 1, 1), levels.topGroupSizes)
    }

    /** Bez hlavních akcí (server skoro nic nedovolí) zůstane hlavička ve skupině sama. */
    @Test
    fun hlavickaBezHlavnichAkciMaSkupinuSama() {
        val levels = arrange(MessageMenuAction.Info, MessageMenuAction.ToggleBookmark, MessageMenuAction.Select)
        assertTrue(levels.top.isEmpty())
        assertEquals(listOf(0, 1), levels.topGroupSizes)
    }

    /** Nevratné akce mají vlastní skupinu a zůstávají na první úrovni. */
    @Test
    fun nevratneAkceNejsouVDalsi() {
        assertEquals(MenuSlot.Destructive, MessageMenuAction.Delete.slot)
        assertEquals(MenuSlot.Destructive, MessageMenuAction.DeletePollOption.slot)
        assertTrue(MenuSlot.entries.filter { it.inMore }.none { it == MenuSlot.Destructive })
    }

    /** Výška ze skupin: 2 + 44 na řádek + 2 v každé skupině, 2 mezi skupinami, 36 hlavička. */
    @Test
    fun vyskaSePocitaZeSkupin() {
        assertEquals(
            (2 + 4 * 44 + 2).dp + (2 + 44 + 2).dp * 3 + 2.dp * 3 + 36.dp,
            menuLevelHeight(listOf(4, 1, 1, 1), header = true),
        )
        assertEquals((2 + 3 * 44 + 2).dp * 2 + 2.dp, menuLevelHeight(listOf(3, 3), header = false))
        assertEquals(0.dp, menuLevelHeight(emptyList(), header = false))
    }

    /** Typická cizí zpráva má nahoře výrazně méně než dřív (17 řádků po 48 dp, přes 800 dp). */
    @Test
    fun prvniUrovenSeVejdePodZpravu() {
        val height = menuLevelHeight(listOf(4, 2, 1, 1), header = true)
        assertTrue(height < 480.dp, "první úroveň má $height")
    }
}
