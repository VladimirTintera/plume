package eu.tintera.plume.core.notifications

/**
 * Smlouva mezi notifikací na iOS a aplikací: kategorie (které akce notifikace nabídne) a klíče
 * v `userInfo`.
 *
 * **Na iOS nese akce KATEGORIE, ne notifikace.** Na Androidu se akce skládají ke každé notifikaci
 * zvlášť; na iOS se sada kategorií zaregistruje jednou při startu a notifikace se na jednu z nich
 * jen odkáže (`categoryIdentifier`). Kombinace „smí odpovědět / smí ztlumit" proto musí být
 * předem vyjmenované.
 *
 * Notifikaci skládá (až bude) notification service extension z rozšifrovaného pushe; ta musí
 * vyplnit právě tyhle klíče. Proto je smlouva ve společném kódu, ne schovaná v obsluze akcí.
 */
object NotificationCategories {

    /** Id chatu v `userInfo`. Číslo (ne řetězec) — `longValue` na něj sedí bez parsování. */
    const val KEY_CHAT_ID = "plume_chat_id"

    /** Nejnovější zpráva notifikace: po ni se čte, ve skupině ji cituje odpověď. */
    const val KEY_MESSAGE_ID = "plume_message_id"

    /** `true` ve skupině — jen tam odpověď cituje (viz Android, `NotificationPresenter`). */
    const val KEY_IS_GROUP = "plume_is_group"

    const val ACTION_REPLY = "plume.reply"
    const val ACTION_MARK_READ = "plume.markRead"
    const val ACTION_MUTE = "plume.mute"

    /**
     * Kategorie notifikace chatu. Přečíst jde vždycky; odpověď jen tam, kam se smí psát
     * (`ChatNotification.canReply`), ztlumení jen u běžných zpráv (`ChatNotification.canMute`).
     */
    fun chatCategory(canReply: Boolean, canMute: Boolean): String =
        "plume.chat" + (if (canReply) ".reply" else "") + (if (canMute) ".mute" else "")

    /** Všechny kategorie s akcemi v pořadí, v jakém je notifikace ukáže. */
    val all: List<Pair<String, List<String>>> =
        listOf(true, false).flatMap { canReply ->
            listOf(true, false).map { canMute ->
                chatCategory(canReply, canMute) to listOfNotNull(
                    ACTION_REPLY.takeIf { canReply },
                    ACTION_MARK_READ,
                    ACTION_MUTE.takeIf { canMute },
                )
            }
        }
}
