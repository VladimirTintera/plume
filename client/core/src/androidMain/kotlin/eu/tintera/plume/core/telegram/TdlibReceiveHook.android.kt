package eu.tintera.plume.core.telegram

/**
 * Sem přesměruje instrumentace (`client/androidApp/build.gradle.kts`) každé
 * `org.drinkless.tdlib.JsonClient.receive` uvnitř `tdl-coroutines`: odpověď TDLib projde
 * [TdlibJsonRepair] dřív, než ji wrapper rozebere.
 *
 * Jméno třídy i metody jsou smlouva s instrumentací — přejmenování ji tiše rozpojí. Proto
 * [TdlibJsonRepair.isHooked] a kontrola po přihlášení v `TdlTelegramSession`.
 */
object TdlibReceiveHook {
    @JvmStatic
    fun afterReceive(json: String?): String? = json?.let(TdlibJsonRepair::repair)
}

internal actual val tdlibJsonRepairWired: Boolean = true
