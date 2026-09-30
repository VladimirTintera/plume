package eu.tintera.plume.core.files

import androidx.compose.runtime.Composable

/**
 * Otevře systémový dialog sdílení se souborem.
 *
 * Sdílí se **kopie pod čitelným jménem** ([fileName], viz [savedFileName]), ne soubor TDLib —
 * příjemce jméno ukazuje a `5868681087951770588_121.jpg` by nikomu nic neřeklo. Kopie leží
 * v cache a přepíše ji až další sdílení; příjemce si soubor čte až po odeslání, takže ji nejde
 * smazat hned.
 *
 * Na iOS dialog nabízí i „Uložit obrázek" a „Uložit do Souborů", takže tam sdílení pokrývá
 * i ukládání jinam než do galerie.
 */
expect class MediaSharer {
    /** Vrací `false`, když se dialog otevřít nepodařilo; příčina jde do logu. */
    suspend fun share(path: String, fileName: String): Boolean

    /**
     * Sdílí text (odkaz pozvánky). Odkaz jde jako text, ne jako adresa souboru — příjemci
     * (Zprávy, e-mail) ho vloží do zprávy a sami z něj udělají klikatelný odkaz.
     */
    fun shareText(text: String): Boolean
}

/**
 * „Uložit jako…": systémový výběr umístění (Stažené, Disk, …) a kopie do něj.
 *
 * Jen Android (`ACTION_CREATE_DOCUMENT`, bez oprávnění). Na iOS je `null` — tam „Uložit do
 * Souborů" nabízí dialog sdílení a druhá cesta k témuž by jen mátla.
 */
interface SaveAsLauncher {
    /** [onResult] dostane `true` po uložení, `false` při chybě a `null`, když uživatel výběr zavřel. */
    fun launch(path: String, fileName: String, onResult: (saved: Boolean?) -> Unit)
}

@Composable
expect fun rememberSaveAs(): SaveAsLauncher?

/** Jestli má platforma „Uložit jako…" — podle toho se položka vůbec nabídne (viz [rememberSaveAs]). */
expect val saveAsSupported: Boolean
