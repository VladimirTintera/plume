package eu.tintera.plume.features.channellimit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Limit vlastních veřejných chatů u přepínače Soukromý / Veřejný — sdílí ho založení
 * (`CreateChatViewModel`) i úprava chatu (`EditChatViewModel`). Zjišťuje se HNED po volbě
 * Veřejný (`checkCreatedPublicChatsLimit`), ne až při uložení: uživatel by jinak vymýšlel
 * a kontroloval odkaz, který stejně nastavit nepůjde. Při limitu [reached] otevře
 * [PublicLinkLimitSheet].
 *
 * Nezjištěný limit nic neblokuje — rozhodne server při uložení a jeho chybu ViewModel ohlásí
 * přes [hit].
 */
class PublicLinkLimitCheck(private val chats: ChatRepository, private val scope: CoroutineScope) {

    /** Narazilo se na limit; obrazovka ukazuje sheet s vlastními veřejnými chaty. */
    var reached by mutableStateOf(false)
        private set

    private var running: Job? = null

    /**
     * Zvolen veřejný chat. [stillPublic] se ptá po odpovědi serveru: kdo mezitím přepnul zpátky
     * na soukromý, tomu se sheet neotevře.
     */
    fun publicChosen(stillPublic: () -> Boolean) {
        running?.cancel()
        running = scope.launch {
            val allowed = chats.canCreatePublicChat().getOrDefault(true)
            if (!allowed && stillPublic()) reached = true
        }
    }

    /** Volba Soukromý: rozběhnuté zjišťování už nic neotevře. */
    fun privateChosen() {
        running?.cancel()
        running = null
    }

    /** Limit ohlásil server až při uložení (`isPublicChatsLimit`). */
    fun hit() {
        reached = true
    }

    /** Sheet se zavřel. */
    fun closed() {
        reached = false
    }
}
