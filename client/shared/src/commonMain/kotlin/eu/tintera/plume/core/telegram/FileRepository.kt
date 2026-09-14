package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Stahování souborů (fotky, videa, avatary) z TDLib.
 *
 * TDLib nedává URL — drží soubory ve **vlastním adresáři na disku** a identifikuje je celočíselným
 * `fileId`. Cesta k souboru je známá až po stažení, průběh chodí updaty. Tohle rozhraní ten model
 * překládá do jednoho reaktivního stavu, aby UI nemuselo řešit ani stahování, ani cesty.
 */
interface FileRepository {

    /**
     * Živý stav souboru. Odběr **sám spustí stažení**, pokud soubor ještě není na disku —
     * UI tedy jen vykreslí to, co dostane, a nic neinicializuje.
     */
    fun file(fileId: Int): Flow<FileState>
}

sealed interface FileState {

    /** Ještě se nezačalo stahovat, nebo čekáme na první update. */
    data object Pending : FileState

    /** [progress] je 0f–1f; u souborů s neznámou velikostí zůstane na 0f. */
    data class Downloading(val progress: Float) : FileState

    /** [path] je absolutní cesta do adresáře TDLib. */
    data class Ready(val path: String) : FileState

    data class Failed(val message: String) : FileState
}
