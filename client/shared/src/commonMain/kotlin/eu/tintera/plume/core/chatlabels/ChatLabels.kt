package eu.tintera.plume.core.chatlabels

import androidx.compose.runtime.Immutable
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Štítek, který si uživatel založil („Rodina", „Vyřídit"). Jen jeho, v zařízení (mezi jeho
 * zařízeními ho nese synchronizace, `CloudSync`) — Telegram má štítky složek jen s Premium
 * a o těchhle neví. [accountId] drží štítky účtů od sebe: id chatů jsou po účtech.
 *
 * Barva NÁZVEM ([ThemeColor.name]), jako barva chatu: nová barva uprostřed výčtu by jinak tiše
 * změnila uložené. Neznámý název (novější verze) spadne při čtení na modrou.
 */
@Serializable
data class StoredChatLabel(val accountId: Long, val id: String, val name: String, val color: String, val order: Int = 0)

/** Chat [chatId] má štítek [labelId] — jeden záznam na dvojici, ať se synchronizace slévá po kusech. */
@Serializable
data class StoredLabelAssignment(val accountId: Long, val chatId: Long, val labelId: String)

/** Štítek přihlášeného účtu, jak ho kreslí UI. */
@Immutable
data class ChatLabel(val id: String, val name: String, val color: ThemeColor)

/** Štítky účtu [account] v pořadí; `null` účet (nepřihlášeno) žádné nemá. */
fun labelsOf(stored: List<StoredChatLabel>, account: Long?): List<ChatLabel> {
    if (account == null) return emptyList()
    return stored.filter { it.accountId == account }
        .sortedWith(compareBy({ it.order }, { it.name }))
        .map { ChatLabel(it.id, it.name, ThemeColor.entries.firstOrNull { color -> color.name == it.color } ?: ThemeColor.BLUE) }
}

/**
 * Chat → jeho štítky (v pořadí štítků) pro účet [account]. Přiřazení ke smazanému štítku se
 * vynechá — kdyby smazání a přiřazení přišly z různých zařízení, nemá po něm zůstat prázdný čip.
 */
fun labelMap(labels: List<ChatLabel>, assignments: List<StoredLabelAssignment>, account: Long?): Map<Long, List<ChatLabel>> {
    if (account == null) return emptyMap()
    val byId = labels.withIndex().associate { (index, label) -> label.id to (index to label) }
    return assignments.filter { it.accountId == account }
        .mapNotNull { assignment -> byId[assignment.labelId]?.let { assignment.chatId to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> list.distinctBy { it.second.id }.sortedBy { it.first }.map { it.second } }
}

/**
 * Štítky chatů přihlášeného účtu (uložené v [AppSettings]): založení, úprava, smazání
 * a přiřazení. Účet je id Uložených zpráv ([ChatRepository.savedMessagesChatId]), jako u barev
 * chatů a záložek.
 */
class ChatLabels(
    private val settings: AppSettings,
    private val chats: ChatRepository,
    private val newId: () -> String = { Random.nextLong().toULong().toString(16) },
) {
    /** Štítky účtu v pořadí. */
    val labels: Flow<List<ChatLabel>> =
        combine(settings.chatLabels, chats.savedMessagesChatId, ::labelsOf).distinctUntilChanged()

    /** Chat → jeho štítky. */
    val assignments: Flow<Map<Long, List<ChatLabel>>> =
        combine(labels, settings.labelAssignments, chats.savedMessagesChatId, ::labelMap).distinctUntilChanged()

    /** Založí štítek na konci pořadí; vrací jeho id, nebo `null` bez přihlášeného účtu či názvu. */
    fun create(name: String, color: ThemeColor): String? {
        val account = chats.savedMessagesChatId.value ?: return null
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val stored = settings.chatLabels.value
        val order = (stored.filter { it.accountId == account }.maxOfOrNull { it.order } ?: -1) + 1
        val id = newId()
        settings.setChatLabels(stored + StoredChatLabel(account, id, trimmed.take(MAX_LABEL_LENGTH), color.name, order))
        return id
    }

    /** Přejmenuje štítek nebo mu změní barvu. */
    fun update(id: String, name: String, color: ThemeColor) {
        val account = chats.savedMessagesChatId.value ?: return
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        settings.setChatLabels(
            settings.chatLabels.value.map {
                if (it.accountId == account && it.id == id) it.copy(name = trimmed.take(MAX_LABEL_LENGTH), color = color.name) else it
            },
        )
    }

    /** Smaže štítek i s jeho přiřazeními. */
    fun delete(id: String) {
        val account = chats.savedMessagesChatId.value ?: return
        settings.setChatLabels(settings.chatLabels.value.filterNot { it.accountId == account && it.id == id })
        settings.setLabelAssignments(settings.labelAssignments.value.filterNot { it.accountId == account && it.labelId == id })
    }

    /** Přidá chatu štítek, nebo mu ho odebere. */
    fun toggle(chatId: Long, labelId: String) {
        val account = chats.savedMessagesChatId.value ?: return
        val current = settings.labelAssignments.value
        val existing = StoredLabelAssignment(account, chatId, labelId)
        settings.setLabelAssignments(if (existing in current) current - existing else current + existing)
    }
}

/** Štítek je čip v řádku chatu — dlouhý název by vytlačil všechno ostatní. */
const val MAX_LABEL_LENGTH = 24
