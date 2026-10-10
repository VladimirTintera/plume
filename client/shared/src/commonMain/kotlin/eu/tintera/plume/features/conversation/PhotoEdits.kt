package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.PhotoMarkup
import eu.tintera.plume.core.ui.PhotoMarkupRenderer

/**
 * Úprava připravené fotky — společná pro přílohy nad vstupním polem i sdílení „Komu: …".
 *
 * Upravená fotka je NOVÝ soubor a originál zůstává: další úprava začíná z něj i s dosavadní
 * úpravou, takže jde vzít zpátky cokoli. Záznamy se vedou podle SOUČASNÉ cesty přílohy.
 */
data class PhotoEdit(
    val originalPath: String,
    val markup: PhotoMarkup,
    /** Rozměry originálu — upravená příloha je má po ořezu a otočení jiné. */
    val originalWidth: Int,
    val originalHeight: Int,
)

/** Čím fotku nahradit: soubor, jeho rozměry a záznam úpravy (`null` = zase originál). */
data class EditedPhoto(val path: String, val width: Int, val height: Int, val edit: PhotoEdit?)

/** Úprava, kterou fotka má — bez záznamu žádná. */
val PhotoEdit?.markupOrEmpty: PhotoMarkup get() = this?.markup ?: PhotoMarkup()

/** Prázdná úprava: zpátky originál, bez vykreslování. */
fun revertedPhoto(photo: OutgoingAttachment.Photo, edit: PhotoEdit?): EditedPhoto = EditedPhoto(
    path = edit?.originalPath ?: photo.path,
    width = edit?.originalWidth ?: photo.width,
    height = edit?.originalHeight ?: photo.height,
    edit = null,
)

/** Vykreslí [markup] z ORIGINÁLU fotky [photo] (s dřívější úpravou [edit]). Může vyhodit. */
suspend fun PhotoMarkupRenderer.renderEdit(photo: OutgoingAttachment.Photo, edit: PhotoEdit?, markup: PhotoMarkup): EditedPhoto {
    val original = revertedPhoto(photo, edit)
    val marked = render(original.path, markup)
    return EditedPhoto(marked.path, marked.width, marked.height, PhotoEdit(original.path, markup, original.width, original.height))
}

/** Přílohy s fotkou [path] vyměněnou za upravenou; režim odeslání zůstává. */
fun List<OutgoingAttachment>.withEditedPhoto(path: String, edited: EditedPhoto): List<OutgoingAttachment> = map { attachment ->
    if (attachment is OutgoingAttachment.Photo && attachment.path == path) {
        attachment.copy(path = edited.path, width = edited.width, height = edited.height)
    } else {
        attachment
    }
}

/** Záznamy úprav po výměně fotky [path]: starý pod starou cestou pryč, nový pod novou. */
fun Map<String, PhotoEdit>.withEditedPhoto(path: String, edited: EditedPhoto): Map<String, PhotoEdit> =
    (this - path).let { rest -> edited.edit?.let { rest + (edited.path to it) } ?: rest }

/**
 * Cesta, pod kterou přílohu zná vestavěná galerie: u upravené fotky ta původní. Galerie si
 * pamatuje, co z ní vzešlo, a bez toho by upravenou fotku při dalším otevření nenašla mezi
 * vybranými a její odznačení by nic neodebralo.
 */
fun Map<String, PhotoEdit>.galleryPath(path: String): String = this[path]?.originalPath ?: path
