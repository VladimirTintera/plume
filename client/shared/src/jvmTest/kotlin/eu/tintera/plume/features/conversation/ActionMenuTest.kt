package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.features.conversation.items.MenuSlot
import eu.tintera.plume.resources.menu_more
import kotlinx.coroutines.runBlocking
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.select_check_box_24dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Nabídka nad zprávou, jak se doopravdy složí. Rozmisťuje se z KONSTANT ([menuLevelHeight]), ne
 * z měření — kdyby se výška položky nebo skupiny v material3 změnila, nabídka by visela vedle
 * zprávy a nic jiného by to neohlásilo.
 */
@OptIn(ExperimentalTestApi::class)
class ActionMenuTest {

    private val clicked = mutableListOf<String>()

    private fun item(label: String, slot: MenuSlot, destructive: Boolean = false) = MenuItem(
        icon = when (slot) {
            MenuSlot.Destructive -> Res.drawable.delete_24dp
            MenuSlot.Report -> Res.drawable.flag_24dp
            MenuSlot.Selection -> Res.drawable.select_check_box_24dp
            MenuSlot.Header -> Res.drawable.info_24dp
            else -> if (label == "Kopírovat") Res.drawable.content_copy_24dp else if (label == "Přeposlat") Res.drawable.forward_24dp else Res.drawable.reply_24dp
        },
        label = label,
        destructive = destructive,
        slot = slot,
        onClick = { clicked += label },
    )

    private val levels = arrangeMenu(
        listOf(
            item("Odpovědět", MenuSlot.Primary),
            item("Kopírovat", MenuSlot.Primary),
            item("Přeposlat", MenuSlot.Primary),
            item("Informace", MenuSlot.Header),
            item("Vybrat víc zpráv", MenuSlot.Selection),
            item("Nahlásit", MenuSlot.Report),
            item("Smazat", MenuSlot.Destructive, destructive = true),
        ),
    ) { it.slot }

    @Test
    fun vyskaPrvniUrovneSediSKonstantou() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box {
                    ActionMenu(
                        levels = levels,
                        timestamp = 0,
                        delivery = null,
                        showMore = false,
                        onShowMore = {},
                        cascade = false,
                        open = true,
                        height = 800.dp,
                        alignBottom = false,
                    )
                }
            }
        }
        // Spodek poslední položky + odsazení její skupiny = výška úrovně.
        val bottom = onNodeWithText("Smazat").getUnclippedBoundsInRoot().bottom + MENU_GROUP_PADDING
        assertEquals(menuLevelHeight(levels.topGroupSizes, header = true), bottom)
    }

    @Test
    fun dalsiVymeniObsahAZpetSeVrati() = runComposeUiTest {
        var more by mutableStateOf(false)
        setContent {
            MaterialTheme {
                ActionMenu(
                    levels = levels,
                    timestamp = 0,
                    delivery = null,
                    showMore = more,
                    onShowMore = { more = it },
                    cascade = false,
                    open = true,
                    height = 800.dp,
                    alignBottom = false,
                )
            }
        }
        // Popisek „Další" podle jazyka, ve kterém test běží.
        val moreLabel = runBlocking { getString(Res.string.menu_more) }
        onAllNodesWithText("Vybrat víc zpráv").assertCountEquals(0)
        onNodeWithText(moreLabel).performClick()
        waitForIdle()
        onNodeWithText("Vybrat víc zpráv").performClick()
        assertEquals(listOf("Vybrat víc zpráv"), clicked)
        // Výška „Další" i s řádkem zpět sedí s konstantou stejně jako první úroveň.
        val bottom = onNodeWithText("Nahlásit").getUnclippedBoundsInRoot().bottom + MENU_GROUP_PADDING
        assertEquals(menuLevelHeight(levels.moreGroupSizes, header = false), bottom)
        // Řádek zpět nese tentýž nadpis; je první.
        onAllNodesWithText(moreLabel)[0].performClick()
        waitForIdle()
        onNodeWithText("Odpovědět").performClick()
        assertEquals(listOf("Vybrat víc zpráv", "Odpovědět"), clicked)
    }
}
