package eu.tintera.plume.core.chatcolor

import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable

/**
 * Barva, kterou si uživatel dal ke konverzaci. Jen v tomhle zařízení a jen pro něj — Telegram
 * o ní neví (téma chatu `setChatTheme` by se vnutilo i druhé straně). [accountId] drží barvy
 * od sebe mezi účty.
 *
 * Barva se ukládá NÁZVEM ([ThemeColor.name]), jako barva aplikace: nová barva uprostřed výčtu
 * by jinak tiše změnila význam uložených hodnot. Neznámý název (novější verze) se při čtení
 * zahodí a chat má barvy aplikace.
 */
@Serializable
data class StoredChatColor(val accountId: Long, val chatId: Long, val color: String)

/** Barvy chatů účtu [account]; `null` účet (nepřihlášeno) žádné nemá. */
fun chatColorMap(stored: List<StoredChatColor>, account: Long?): Map<Long, ThemeColor> {
    if (account == null) return emptyMap()
    return stored
        .filter { it.accountId == account }
        .mapNotNull { entry -> ThemeColor.entries.firstOrNull { it.name == entry.color }?.let { entry.chatId to it } }
        .toMap()
}

/**
 * Barvy konverzací přihlášeného účtu (uložené v [AppSettings]).
 *
 * Účet je id Uložených zpráv ([ChatRepository.savedMessagesChatId]), stejně jako u odložených
 * chatů a záložek.
 */
class ChatColors(
    private val settings: AppSettings,
    private val chats: ChatRepository,
) {

    /** Chat → barva; chat bez záznamu má barvy aplikace. */
    val colors: Flow<Map<Long, ThemeColor>> =
        combine(settings.chatColors, chats.savedMessagesChatId, ::chatColorMap).distinctUntilChanged()

    /**
     * Barvy právě teď, bez čekání na flow. Konverzace se musí složit rovnou ve své barvě —
     * první snímek v barvách aplikace by se pak k barvě chatu prolnul a vypadal jako bliknutí.
     */
    fun current(): Map<Long, ThemeColor> = chatColorMap(settings.chatColors.value, chats.savedMessagesChatId.value)

    /** Nastaví barvu chatu; `null` vrátí barvy aplikace (záznam zmizí, nic se neukládá). */
    fun set(chatId: Long, color: ThemeColor?) {
        val account = chats.savedMessagesChatId.value ?: return
        val others = settings.chatColors.value.filterNot { it.accountId == account && it.chatId == chatId }
        settings.setChatColors(if (color == null) others else others + StoredChatColor(account, chatId, color.name))
    }
}
