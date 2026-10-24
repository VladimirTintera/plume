package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageChatShared
import dev.g000sha256.tdl.dto.MessageUsersShared
import dev.g000sha256.tdl.dto.SharedChat
import dev.g000sha256.tdl.dto.SharedUser
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_shared_chat
import eu.tintera.plume.resources.content_shared_users
import eu.tintera.plume.resources.service_names_more
import eu.tintera.plume.resources.service_names_two
import eu.tintera.plume.resources.service_shared_chat
import eu.tintera.plume.resources.service_shared_users
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Co účet sdílel s botem: název chatu, jména lidí, a kam vede ťuknutí. */
class SharedWithBotTextTest {

    @Test
    fun sdilenyChatMaNazev() {
        assertEquals(UiText(Res.string.service_shared_chat, "Výlet"), chat(5L, "Výlet").serviceText(isChannelPost = false))
        assertEquals(UiText(Res.string.content_shared_chat), chat(5L, "").serviceText(isChannelPost = false))
    }

    @Test
    fun sdileniLideJmenem() {
        assertEquals(
            UiText(Res.string.service_shared_users, UiText.Raw("Alice Nováková")),
            users(user(1L, "Alice", "Nováková")).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_shared_users, UiText(Res.string.service_names_two, UiText.Raw("Alice"), UiText.Raw("@bob"))),
            users(user(1L, "Alice"), user(2L, "", username = "bob")).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_shared_users, UiText(Res.plurals.service_names_more, 2, UiText.Raw("Alice"), 2)),
            users(user(1L, "Alice"), user(2L, "Bob"), user(3L, "Cyril")).serviceText(false),
        )
        // Bez jména i uživatelského jména zůstane obecná věta, ne prázdné místo.
        assertEquals(UiText(Res.string.content_shared_users), users(user(1L, "")).serviceText(false))
    }

    @Test
    fun jmenaSeDohledajiPodleId() = kotlinx.coroutines.test.runTest {
        // Uživatelský účet dostane jména prázdná („for bots only"), jen id.
        val names = mapOf(1L to "Alice", 2L to "Bob", 5L to "Výlet")
        assertEquals(
            UiText(Res.string.service_shared_users, UiText(Res.string.service_names_two, UiText.Raw("Alice"), UiText.Raw("Bob"))),
            users(user(1L, ""), user(2L, "")).namedSharedWithBotText { names[it] },
        )
        assertEquals(UiText(Res.string.service_shared_chat, "Výlet"), chat(5L, "").namedSharedWithBotText { names[it] })
        assertEquals(UiText(Res.string.content_shared_users), users(user(9L, "")).namedSharedWithBotText { names[it] })
    }

    @Test
    fun tuknutiOtevreChatNeboCloveka() {
        assertEquals(ServiceOpen.Chat(5L), chat(5L, "Výlet").serviceOpenTarget())
        assertEquals(ServiceOpen.User(1L), users(user(1L, "Alice")).serviceOpenTarget())
        assertNull(users(user(1L, "Alice"), user(2L, "Bob")).serviceOpenTarget())
    }

    private fun chat(id: Long, title: String) =
        MessageChatShared(chat = SharedChat(chatId = id, title = title, username = "", photo = null), buttonId = 1)

    private fun users(vararg list: SharedUser) = MessageUsersShared(users = arrayOf(*list), buttonId = 1)

    private fun user(id: Long, first: String, last: String = "", username: String = "") =
        SharedUser(userId = id, firstName = first, lastName = last, username = username, photo = null)
}
