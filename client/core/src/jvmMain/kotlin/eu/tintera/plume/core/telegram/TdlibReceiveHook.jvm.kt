package eu.tintera.plume.core.telegram

/**
 * Sem přesměruje přepis jaru wrapperu (`client/desktopApp/build.gradle.kts`, `TdlibJarTransform`)
 * každé `org.drinkless.tdlib.JsonClient.receive` uvnitř `tdl-coroutines-jvm`: odpověď TDLib
 * projde [TdlibJsonRepair] dřív, než ji wrapper rozebere. Totéž dělá na Androidu instrumentace
 * bajtkódu; jméno třídy a metody je smlouva s přepisem stejně jako tam.
 */
object TdlibReceiveHook {
    @JvmStatic
    fun afterReceive(json: String?): String? = json?.let(TdlibJsonRepair::repair)
}

internal actual val tdlibJsonRepairWired: Boolean = true
