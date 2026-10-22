package eu.tintera.plume.core.files

import eu.tintera.plume.core.ui.UrlOpener

/** Prohlížeč: soubor si stáhne sám (server ho má posílat s `Content-Disposition: attachment`). */
actual class WebFileDownloader(private val urlOpener: UrlOpener) {
    actual fun download(url: String, fileName: String): Boolean {
        urlOpener.open(url)
        return true
    }
}
