package eu.tintera.plume.core.chatlabels

import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatLabelsTest {

    private class Settings : UnimplementedAppSettings() {
        override val chatLabels = MutableStateFlow<List<StoredChatLabel>>(emptyList())
        override fun setChatLabels(labels: List<StoredChatLabel>) {
            chatLabels.value = labels
        }
        override val labelAssignments = MutableStateFlow<List<StoredLabelAssignment>>(emptyList())
        override fun setLabelAssignments(assignments: List<StoredLabelAssignment>) {
            labelAssignments.value = assignments
        }
    }

    private class Chats(account: Long?) : UnimplementedChatRepository() {
        override val savedMessagesChatId: StateFlow<Long?> = MutableStateFlow(account)
    }

    private var ids = 0

    private fun labels(settings: Settings, account: Long? = 1) = ChatLabels(settings, Chats(account), newId = { "l${++ids}" })

    @Test
    fun stitkyUctuVPoradiANeznamaBarvaJeModra() {
        val stored = listOf(
            StoredChatLabel(1, "b", "Vyřídit", "RED", order = 1),
            StoredChatLabel(1, "a", "Rodina", "NEW_COLOR", order = 0),
            StoredChatLabel(2, "c", "Cizí účet", "GREEN"),
        )
        assertEquals(
            listOf(ChatLabel("a", "Rodina", ThemeColor.BLUE), ChatLabel("b", "Vyřídit", ThemeColor.RED)),
            labelsOf(stored, account = 1),
        )
        assertEquals(emptyList(), labelsOf(stored, account = null))
    }

    @Test
    fun prirazeniKeSmazanemuStitkuSeVynecha() {
        val defs = listOf(ChatLabel("a", "Rodina", ThemeColor.BLUE), ChatLabel("b", "Práce", ThemeColor.RED))
        val map = labelMap(
            defs,
            listOf(
                StoredLabelAssignment(1, 10, "b"),
                StoredLabelAssignment(1, 10, "a"),
                StoredLabelAssignment(1, 11, "smazany"),
                StoredLabelAssignment(2, 12, "a"),
            ),
            account = 1,
        )
        // Pořadí štítků, ne pořadí přiřazení; chat se smazaným štítkem nemá nic, cizí účet taky.
        assertEquals(mapOf(10L to defs), map)
    }

    @Test
    fun zalozeniPrepnutiASmazani() {
        val settings = Settings()
        val store = labels(settings)
        val family = store.create("  Rodina  ", ThemeColor.GREEN)!!
        val todo = store.create("Vyřídit", ThemeColor.RED)!!
        assertEquals(listOf("Rodina", "Vyřídit"), labelsOf(settings.chatLabels.value, 1).map { it.name })
        assertEquals(listOf(0, 1), settings.chatLabels.value.map { it.order })

        store.toggle(10, family)
        store.toggle(10, todo)
        store.toggle(11, family)
        store.toggle(10, todo)
        assertEquals(setOf(10L to family, 11L to family), settings.labelAssignments.value.map { it.chatId to it.labelId }.toSet())

        store.delete(family)
        assertEquals(listOf("Vyřídit"), settings.chatLabels.value.map { it.name })
        assertEquals(emptyList(), settings.labelAssignments.value)
    }

    @Test
    fun prazdnyNazevABezUctuNicNezalozi() {
        val settings = Settings()
        assertNull(labels(settings).create("   ", ThemeColor.BLUE))
        assertNull(labels(settings, account = null).create("Rodina", ThemeColor.BLUE))
        assertEquals(emptyList(), settings.chatLabels.value)
    }

    @Test
    fun upravaZmeniJenSvujStitek() {
        val settings = Settings()
        val store = labels(settings)
        val id = store.create("Rodina", ThemeColor.GREEN)!!
        store.create("Práce", ThemeColor.BLUE)
        store.update(id, "Rodinka", ThemeColor.PINK)
        assertEquals(
            listOf(ChatLabel(id, "Rodinka", ThemeColor.PINK), ChatLabel("l2", "Práce", ThemeColor.BLUE)),
            labelsOf(settings.chatLabels.value, 1),
        )
    }
}
