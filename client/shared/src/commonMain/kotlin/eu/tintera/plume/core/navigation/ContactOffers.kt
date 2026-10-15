package eu.tintera.plume.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Profily otevřené z kontaktního odkazu (`t.me/contact/…`, QR kód), ve kterých má být
 * „Přidat do kontaktů" hlavní akcí.
 *
 * Je to STAV, ne událost — jako [BotStartRequests]: odkaz se rozpozná v konverzaci nebo v shellu
 * (odkaz zvenčí) a profil, který si nabídku vyzvedne, teprve vznikne. Do routy profilu to nepatří:
 * routa je klíč zásobníku (přepínání profilu, výměna id po převodu skupiny) a tentýž profil
 * otevřený z hlavičky by s jiným příznakem byl jiná položka.
 */
class ContactOffers {

    private val pending = MutableStateFlow<Set<Long>>(emptySet())

    fun offer(chatId: Long) {
        pending.update { it + chatId }
    }

    /** Vyzvedne nabídku pro profil chatu [chatId]; `true` = profil ji má ukázat. */
    fun consume(chatId: Long): Boolean {
        var had = false
        pending.update { current ->
            had = chatId in current
            current - chatId
        }
        return had
    }
}
