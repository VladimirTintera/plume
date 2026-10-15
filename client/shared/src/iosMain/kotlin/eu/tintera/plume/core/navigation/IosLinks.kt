package eu.tintera.plume.core.navigation

import co.touchlab.kermit.Logger
import org.koin.core.Koin

/**
 * Odkazy zvenčí na iOS: `tg://…` z jiné aplikace (Safari, stránka `t.me` „Otevřít v Telegramu",
 * fotoaparát). Systém ho doručí SwiftUI do `onOpenURL` (`ContentView.swift`), který ho předá sem;
 * otevře ho shell přes [LinkOpenRequests] stejně jako na Androidu (`MainActivity.receiveLink`).
 *
 * **Jen `tg://`, ne `https://t.me`.** Webový odkaz by iOS předal aplikaci jen jako Universal
 * Link, a ten se ověřuje souborem `apple-app-site-association` na doméně — `t.me` ho vydává
 * Telegram a Plume v něm nebude. Asociovaná doména by tedy nikdy neprošla; `t.me` otevře Safari
 * a její tlačítko „Otevřít v Telegramu" pošle `tg://`, které už Plume dostane.
 *
 * Schéma vlastní aplikace (`eu.tintera.plume://share`, jím rozšíření pro sdílení aplikaci jen
 * probouzí) sem chodí taky a projde bez akce — sdílení si vyzvedne `IosShareInbox` při návratu
 * do popředí.
 */
object IosLinks {

    private val logger = Logger.withTag("Links")

    private var requests: LinkOpenRequests? = null

    internal fun start(koin: Koin) {
        requests = koin.get()
    }

    /**
     * Z `onOpenURL`. `true`, když to byl odkaz na Telegram a čeká na otevření; co doopravdy
     * znamená, rozhodne až TDLib v shellu.
     */
    fun open(url: String): Boolean {
        if (!isTelegramLink(url)) return false
        val target = requests ?: run {
            // initKoin běží v `init` aplikace, dřív než první scéna — sem by se nemělo dojít.
            logger.w { "Odkaz zvenčí přišel před startem Koinu, zahazuje se." }
            return false
        }
        target.request(url)
        // Bez adresy: nese jméno, token kontaktu nebo pozvánku.
        logger.i { "Odkaz zvenčí předán k otevření." }
        return true
    }
}
