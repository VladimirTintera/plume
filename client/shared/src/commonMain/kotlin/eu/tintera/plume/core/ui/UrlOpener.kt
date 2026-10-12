package eu.tintera.plume.core.ui

/** Otevře odkaz v prohlížeči (nebo v aplikaci, která ho umí zpracovat). */
expect class UrlOpener {
    fun open(url: String)

    /**
     * Webový odkaz výslovně v prohlížeči, i když ho jinak bere aplikace — včetně Plume samotné.
     * Pro odkaz `t.me/…`, který přišel zvenčí a Plume ho otevřít neumí: [open] by ho systém
     * mohl vrátit zase Plume. Vrací `false`, když se nepovedl.
     */
    fun openInBrowser(url: String): Boolean
}
