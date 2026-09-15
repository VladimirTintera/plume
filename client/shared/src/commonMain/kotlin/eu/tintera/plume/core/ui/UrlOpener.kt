package eu.tintera.plume.core.ui

/** Otevře odkaz v prohlížeči (nebo v aplikaci, která ho umí zpracovat). */
expect class UrlOpener {
    fun open(url: String)
}
