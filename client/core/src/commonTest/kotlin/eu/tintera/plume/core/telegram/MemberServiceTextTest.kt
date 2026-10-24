package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageChatAddMembers
import dev.g000sha256.tdl.dto.MessageChatDeleteMember
import dev.g000sha256.tdl.dto.MessageChatJoinByLink
import dev.g000sha256.tdl.dto.MessageChatJoinByRequest
import dev.g000sha256.tdl.dto.MessageChatChangeTitle
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_added_you
import eu.tintera.plume.resources.service_member_accepted
import eu.tintera.plume.resources.service_member_added
import eu.tintera.plume.resources.service_member_joined
import eu.tintera.plume.resources.service_member_joined_by_link
import eu.tintera.plume.resources.service_member_left_named
import eu.tintera.plume.resources.service_member_removed
import eu.tintera.plume.resources.service_names_more
import eu.tintera.plume.resources.service_names_two
import eu.tintera.plume.resources.service_removed_you
import eu.tintera.plume.resources.service_you_accepted
import eu.tintera.plume.resources.service_you_left
import eu.tintera.plume.resources.service_you_object
import eu.tintera.plume.resources.service_you_removed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Zprávy o členech se jmény. Aktér je odesílatel zprávy; kdo z lidí je „ty", rozhoduje volající
 * ([ServicePerson.isMe]). Bez některého jména vrací `null` — bezejmenná věta je pak lepší než
 * napůl prázdná.
 */
class MemberServiceTextTest {

    private val me = ServicePerson(name = "", isMe = true)
    private val alice = ServicePerson("Alice")
    private val bob = ServicePerson("Bob")
    private val cyril = ServicePerson("Cyril")

    @Test
    fun odchodASamotnyVstup() {
        assertEquals(
            UiText(Res.string.service_member_left_named, "Alice"),
            MessageChatDeleteMember(userId = 1).memberServiceText(actorId = 1, people = mapOf(1L to alice)),
        )
        assertEquals(
            UiText(Res.string.service_you_left),
            MessageChatDeleteMember(userId = 9).memberServiceText(actorId = 9, people = mapOf(9L to me)),
        )
        // Do veřejné skupiny se člověk „přidá" sám.
        assertEquals(
            UiText(Res.string.service_member_joined, "Alice"),
            MessageChatAddMembers(longArrayOf(1)).memberServiceText(actorId = 1, people = mapOf(1L to alice)),
        )
    }

    @Test
    fun odkazAZadost() {
        assertEquals(
            UiText(Res.string.service_member_joined_by_link, "Alice"),
            MessageChatJoinByLink().memberServiceText(actorId = 1, people = mapOf(1L to alice)),
        )
        assertEquals(
            UiText(Res.string.service_member_accepted, "Alice"),
            MessageChatJoinByRequest().memberServiceText(actorId = 1, people = mapOf(1L to alice)),
        )
        assertEquals(
            UiText(Res.string.service_you_accepted),
            MessageChatJoinByRequest().memberServiceText(actorId = 9, people = mapOf(9L to me)),
        )
    }

    @Test
    fun pridaniJmenujeAzDvaLidi() {
        val people = mapOf(1L to alice, 2L to bob, 3L to cyril)
        assertEquals(
            UiText(Res.string.service_member_added, "Alice", UiText.Raw("Bob")),
            MessageChatAddMembers(longArrayOf(2)).memberServiceText(actorId = 1, people = people),
        )
        assertEquals(
            UiText(Res.string.service_member_added, "Alice", UiText(Res.string.service_names_two, UiText.Raw("Bob"), UiText.Raw("Cyril"))),
            MessageChatAddMembers(longArrayOf(2, 3)).memberServiceText(actorId = 1, people = people),
        )
        // U hromadného přidání stačí první jméno; ostatní se nedohledávají, jen počítají.
        assertEquals(
            UiText(Res.string.service_member_added, "Alice", UiText(Res.plurals.service_names_more, 3, UiText.Raw("Bob"), 3)),
            MessageChatAddMembers(longArrayOf(2, 4, 5, 6)).memberServiceText(actorId = 1, people = mapOf(1L to alice, 2L to bob)),
        )
    }

    @Test
    fun tyJakoPredmet() {
        assertEquals(
            UiText(Res.string.service_added_you, "Alice"),
            MessageChatAddMembers(longArrayOf(9)).memberServiceText(actorId = 1, people = mapOf(1L to alice, 9L to me)),
        )
        assertEquals(
            UiText(Res.string.service_removed_you, "Alice"),
            MessageChatDeleteMember(userId = 9).memberServiceText(actorId = 1, people = mapOf(1L to alice, 9L to me)),
        )
        assertEquals(
            UiText(Res.string.service_you_removed, UiText.Raw("Bob")),
            MessageChatDeleteMember(userId = 2).memberServiceText(actorId = 9, people = mapOf(9L to me, 2L to bob)),
        )
        assertEquals(
            UiText(Res.string.service_member_added, "Alice", UiText(Res.string.service_names_two, UiText(Res.string.service_you_object), UiText.Raw("Bob"))),
            MessageChatAddMembers(longArrayOf(9, 2)).memberServiceText(actorId = 1, people = mapOf(1L to alice, 9L to me, 2L to bob)),
        )
        assertEquals(
            UiText(Res.string.service_member_removed, "Alice", "Bob"),
            MessageChatDeleteMember(userId = 2).memberServiceText(actorId = 1, people = mapOf(1L to alice, 2L to bob)),
        )
    }

    @Test
    fun chybejiciJmenoNechaBezejmennouVetu() {
        assertNull(MessageChatDeleteMember(userId = 2).memberServiceText(actorId = 1, people = mapOf(1L to alice)))
        assertNull(MessageChatJoinByLink().memberServiceText(actorId = 1, people = emptyMap()))
        assertNull(MessageChatChangeTitle(title = "X").memberServiceText(actorId = 1, people = mapOf(1L to alice)))
        assertNull(MessageChatChangeTitle(title = "X").memberServiceUserIds())
    }
}
