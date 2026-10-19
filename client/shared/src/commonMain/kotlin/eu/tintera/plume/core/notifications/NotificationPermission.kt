package eu.tintera.plume.core.notifications

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.DEFAULT_SOUND_ID
import eu.tintera.plume.core.telegram.NotificationChannelKind

/**
 * Stav oprávnění k zobrazování notifikací.
 *
 * Na platformách, kde se o oprávnění nežádá, hlásí [isGranted] `true` — volající se tak nemusí
 * ptát, na čem běží.
 */
expect class NotificationPermission {

    /** Smí aplikace právě teď zobrazovat notifikace? */
    fun isGranted(): Boolean

    /**
     * Otevře systémové nastavení notifikací aplikace.
     *
     * Android po druhém odmítnutí dialog zablokuje natrvalo. Bez téhle cesty by uživatel neměl
     * jak se z toho dostat — v aplikaci by mu notifikace nešly zapnout vůbec.
     */
    fun openSystemSettings()

    /**
     * Má platforma systémové povolení oznámení, které jde ukázat a otevřít ([isGranted],
     * [openSystemSettings])? Linux ne: žádné povolení tam není a nastavení k otevření taky ne,
     * řádek „Povolení v systému" by tvrdil „zapnuto" a na klik nedělal nic.
     */
    val hasSystemPermission: Boolean

    /**
     * Umí platforma nastavit zvuk a vibrace pro jednotlivý kanál? Android ano (od 8.0 je to
     * jediné místo, kde se nastavují), iOS kanály nemá.
     */
    val supportsChannelSettings: Boolean

    /** Umí aplikace tiché hodiny ([eu.tintera.plume.core.settings.QuietHours])? */
    val supportsQuietHours: Boolean

    /**
     * Smí notifikace příchozího hovoru rozsvítit zhasnutý nebo zamčený telefon na obrazovku
     * hovoru (celoobrazovková výzva)? `null` = platforma to neřeší, nebo je to zaručené (iOS,
     * desktop, Android do 13). Od Androidu 14 to Play povolí jen aplikacím deklarovaným jako
     * hovory a uživatel to může vypnout; bez toho zamčený telefon jen zvoní a nic neukáže.
     */
    fun fullScreenCallsAllowed(): Boolean?

    /** Otevře systémové nastavení celoobrazovkových notifikací aplikace ([fullScreenCallsAllowed]). */
    fun openFullScreenCallSettings()

    /** Otevře systémové nastavení kanálu — zvuk, vibrace, důležitost. */
    fun openChannelSettings(channel: NotificationChannelKind, soundId: Long = DEFAULT_SOUND_ID)

    /** Tón účtu smazaný — pryč i jeho kanály a kopie souboru (Android), ať v systému nevisí. */
    fun forgetTone(soundId: Long)

    /**
     * Otevře systémové nastavení jedné konverzace (zvuk, prioritní, tichá). Kde to platforma
     * neumí (Android do 10), otevře kanál [channel], pod který konverzace patří. [soundId] je
     * zvuk z Telegramu, kterým chat zní — s tónem visí konverzace pod kanálem toho tónu.
     */
    fun openConversationSettings(chatId: Long, title: String, channel: NotificationChannelKind, soundId: Long = DEFAULT_SOUND_ID)
}

/**
 * Požádá o oprávnění, pokud je to potřeba.
 *
 * **Nejdřív vlastní vysvětlení, teprve pak systémový dialog.** Android dá na systémový dialog jen
 * dva pokusy a pak ho zablokuje navždy; vyvolat ho naslepo hned po startu znamená velkou šanci,
 * že ho uživatel odklikne a notifikace už nikdy nepůjdou zapnout. Vlastní obrazovka žádný pokus
 * nespotřebuje.
 *
 * Vkládá se až do přihlášené části aplikace — nad přihlašovacím formulářem uživatel ještě netuší,
 * co aplikace dělá, a ptát se ho tam je předčasné.
 */
@Composable
expect fun NotificationPermissionPrompt()
