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

    /**
     * Zkopíruje soubor do schránky, odkud ho jde vložit do jiné aplikace nebo do složky.
     *
     * Kopíruje se taky **kopie pod čitelným jménem** — vložená do Průzkumníka nebo Finderu by
     * jinak nesla jméno z TDLib. Leží v dočasném adresáři, dokud ji nenahradí další kopírování:
     * schránka odkazuje na soubor, smazat ji dřív nejde. [isImage] říká, že soubor je obrázek
     * a má jít i jako obrázek (vložení do editoru, do zprávy); jinde než na desktopu se jinak
     * nekopíruje nic ([copiesAnyFile]).
     *
     * `false`, když se zkopírovat nepodařilo; příčina jde do logu.
     */
    suspend fun copy(path: String, fileName: String, isImage: Boolean): Boolean
}

/**
 * Jestli má platforma systémový dialog sdílení souboru. Desktop ho z JVM nedostane — místo
 * „Sdílet" tam je kopírování do schránky ([MediaSharer.copy]) a „Uložit jako…".
 */
expect val fileShareSupported: Boolean

/**
 * Jestli jde do schránky jakýkoli soubor, nebo jen obrázek. Na desktopu se soubor vkládá do
 * složky i do zprávy v jiné aplikaci; na telefonu schránka soubory nenese (Android ano jako
 * URI, ale vložit ho skoro nic neumí) a obrázek je jediné, co dává smysl.
 */
expect val copiesAnyFile: Boolean

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
