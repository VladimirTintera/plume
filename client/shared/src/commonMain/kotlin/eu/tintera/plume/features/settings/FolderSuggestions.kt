package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ai.FolderCandidate
import eu.tintera.plume.core.ai.FolderCategory
import eu.tintera.plume.core.ai.FolderChatKind
import eu.tintera.plume.core.ai.MAX_FOLDER_CHATS
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.folderPrompt
import eu.tintera.plume.core.ai.parseFolderCategories
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.folder_category_family
import eu.tintera.plume.resources.folder_category_friends
import eu.tintera.plume.resources.folder_category_hobbies
import eu.tintera.plume.resources.folder_category_news
import eu.tintera.plume.resources.folder_category_school
import eu.tintera.plume.resources.folder_category_shopping
import eu.tintera.plume.resources.folder_category_work
import kotlinx.coroutines.flow.lastOrNull
import org.jetbrains.compose.resources.StringResource

/**
 * Složka, kterou navrhl model: kategorie a chaty v ní, v pořadí seznamu chatů. [names] jsou
 * názvy chatů pro řádek pod složkou — uživatel má vidět, CO do složky přidává, dřív než ťukne.
 */
data class SuggestedFolder(val category: FolderCategory, val chatIds: List<Long>, val names: List<String>)

/** Kategorie aspoň s tolika chaty se nabízí; složka s jedním chatem nic netřídí. */
const val MIN_SUGGESTED_FOLDER_CHATS = 2

/**
 * Návrh složek modelem v zařízení (Nastavení → Složky chatů).
 *
 * - **Na ťuknutí** (`variantFor`, ne `proactiveVariant`): uživatel si o návrh řekl, takže jede
 *   i pod úsporným režimem. Nic se nezakládá samo — každou složku přidá až „Přidat".
 * - **Jen nejnovější chaty** ([MAX_FOLDER_CHATS]) z hlavního seznamu, bez Uložených zpráv
 *   a sponzorovaného chatu: ty do složky podle obsahu nepatří.
 * - **Model dostane název, druh a náhled poslední zprávy** — nic se nikam neposílá, model
 *   počítá v telefonu.
 * - **Kategorie, pro kterou už složka STEJNÉHO jména je, se nenabízí**: druhá „Práce" vedle
 *   první by jen mátla. Porovnává se bez ohledu na velikost písmen.
 */
class FolderSuggestions(private val ai: OnDeviceAi, private val settings: AppSettings) {

    /** Stojí za to nabízet? Zapnutá AI a model, který zařízení umí (stažený být nemusí). */
    suspend fun available(): Boolean =
        settings.onDeviceAiEnabled.value &&
            runCatching { ai.status(ai.variantFor(AiTask.Folders, settings)) != OnDeviceAiStatus.Unsupported }.getOrDefault(false)

    /**
     * Návrhy pro [chats] (hlavní seznam, od nejnovějšího). `null` = model není připravený;
     * prázdný seznam = nic k navržení. [titleOf] je název kategorie v jazyce aplikace.
     */
    suspend fun suggest(
        chats: List<ChatSummary>,
        excludedChatIds: Set<Long>,
        existingTitles: Set<String>,
        titleOf: suspend (FolderCategory) -> String,
    ): List<SuggestedFolder>? {
        val variant = ai.variantFor(AiTask.Folders, settings)
        if (ai.status(variant) != OnDeviceAiStatus.Ready) return null
        val picked = chats.filter { it.id !in excludedChatIds && it.sponsor == null }.take(MAX_FOLDER_CHATS)
        if (picked.isEmpty()) return emptyList()
        val candidates = picked.map { chat ->
            FolderCandidate(title = chat.title, kind = chat.kind.forFolder(), preview = chat.lastMessagePreview?.resolveNow())
        }
        val output = ai.generate(variant, folderPrompt(candidates)).lastOrNull()?.text.orEmpty()
        val existing = existingTitles.map { it.trim().lowercase() }.toSet()
        return groupSuggestions(parseFolderCategories(output, picked.size), picked)
            .filter { titleOf(it.category).trim().lowercase() !in existing }
    }
}

/**
 * Přiřazení modelu ([parseFolderCategories]) seskupené do složek: kategorie v pořadí výčtu,
 * jen s aspoň [MIN_SUGGESTED_FOLDER_CHATS] chaty.
 */
fun groupSuggestions(assigned: Map<Int, FolderCategory>, chats: List<ChatSummary>): List<SuggestedFolder> =
    FolderCategory.entries.mapNotNull { category ->
        val inCategory = chats.filterIndexed { index, _ -> assigned[index] == category }
        if (inCategory.size < MIN_SUGGESTED_FOLDER_CHATS) return@mapNotNull null
        SuggestedFolder(category, inCategory.map { it.id }, inCategory.map { it.title })
    }

private fun ChatKind.forFolder(): FolderChatKind = when (this) {
    ChatKind.Private -> FolderChatKind.Private
    ChatKind.Group -> FolderChatKind.Group
    ChatKind.Channel -> FolderChatKind.Channel
}

/** Název složky pro kategorii; každý do 12 znaků (`CHAT_FOLDER_TITLE_MAX`), hlídá test. */
val FolderCategory.title: StringResource?
    get() = when (this) {
        FolderCategory.Work -> Res.string.folder_category_work
        FolderCategory.Family -> Res.string.folder_category_family
        FolderCategory.Friends -> Res.string.folder_category_friends
        FolderCategory.School -> Res.string.folder_category_school
        FolderCategory.Shopping -> Res.string.folder_category_shopping
        FolderCategory.News -> Res.string.folder_category_news
        FolderCategory.Hobbies -> Res.string.folder_category_hobbies
        FolderCategory.Other -> null
    }
