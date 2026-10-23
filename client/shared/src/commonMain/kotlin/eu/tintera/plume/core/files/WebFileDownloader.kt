package eu.tintera.plume.core.files

/**
 * Stažení souboru z webu, o které požádala mini aplikace (`downloadFile`). Soubor nepatří TDLib,
 * je to obyčejná adresa https — stahuje ho systém: Android správcem stahování do Stažených
 * (s notifikací průběhu), iOS a desktop prohlížečem.
 */
expect class WebFileDownloader {
    /** Začne stahovat [url] jako [fileName]. `false`, když se stahování nepodařilo spustit. */
    fun download(url: String, fileName: String): Boolean
}
