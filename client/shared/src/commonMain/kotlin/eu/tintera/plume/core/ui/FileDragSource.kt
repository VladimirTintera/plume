package eu.tintera.plume.core.ui

import androidx.compose.ui.Modifier

/** Soubor, který jde vytáhnout z okna ven: cesta na disku a jméno, pod kterým v cíli přistane. */
data class DraggedFile(val path: String, val fileName: String)

/** Jestli platforma umí táhnout soubor z okna do jiné aplikace (desktop). */
expect val fileDragSupported: Boolean

/**
 * Tah myší vytáhne soubor z okna ven — do Průzkumníka, Finderu, e-mailu. [file] se ptá až při
 * začátku tahu; `null` = teď není co táhnout (soubor ještě není stažený) a tah propadne dál
 * (výběr textu v bublině). Na telefonu nic nedělá — tah prstem tam patří posouvání a gestům.
 */
expect fun Modifier.fileDragSource(file: () -> DraggedFile?): Modifier
