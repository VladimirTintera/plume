package eu.tintera.plume.features.chats

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.ChatSummary

/**
 * Které sheety nad chatem jednoho [ChatsViewModel] jsou otevřené: nabídka po podržení, výběr
 * doby odložení po tahu a volby vymazání historie. Kreslí je [ChatActionsHost], otevírají
 * řádky seznamu — proto společný držák a ne `remember` přímo v hostiteli.
 *
 * **Žije v kompozici (`remember`), ne ve ViewModelu ani v `rememberSaveable`.** ViewModel
 * seznamu i jeho uložený stav přežívají, dokud je seznam v zásobníku, tedy pořád (je to kořen).
 * Sheet otevřený ve chvíli, kdy přes seznam přijela konverzace zvenčí (sdílení, notifikace,
 * odkaz), se pak vrátil s prvním složením seznamu, už v náhledu gesta zpět: jako samostatné
 * okno gesto sebral, náhled se zrušil a z konverzace nešlo odejít. `remember` zanikne s odchodem
 * seznamu z kompozice. Otočení přežije, protože aktivita má `configChanges` a kompozice zůstává;
 * přijde se jen o obnovení po zabití procesu, u kontextové nabídky bez ztráty.
 *
 * Jeden držák na ViewModel: každý účet společného seznamu má vlastní ([ChatSource.sheets]).
 */
@Stable
internal class ChatSheets {
    /** Chat, nad kterým je otevřená nabídka akcí; `null` = zavřená. */
    var actions by mutableStateOf<ChatSummary?>(null)

    /** Chat, kterému se po tahu vybírá doba odložení; `null` = zavřené. */
    var snooze by mutableStateOf<ChatSummary?>(null)

    /** Vymazání historie s volbami (z nabídky nad chatem); `null` = zavřené. */
    var clearHistory by mutableStateOf<ClearHistoryTarget?>(null)
}
