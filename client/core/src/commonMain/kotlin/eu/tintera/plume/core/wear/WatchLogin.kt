package eu.tintera.plume.core.wear

/**
 * Přihlášení hodinek potvrzené z telefonu přes Wearable Data Layer: hodinky pošlou přihlašovací
 * odkaz ze svého QR kódu (`tg://login?token=…`) aplikaci na telefonu, ta se zeptá notifikací
 * a po potvrzení ho přihlásí (`confirmQrCodeAuthentication`) — jako by ho naskenovala.
 *
 * Smlouva mezi `:client:wearApp` a `:client:androidApp` na jednom místě: oba moduly ji berou
 * odsud, takže se cesta ani kód výsledku nemůžou rozejít. Zprávy si Data Layer doručí jen mezi
 * aplikacemi se stejným `applicationId` a podpisem.
 */
object WatchLogin {

    /** Schopnost, kterou telefon s Plume ohlašuje (`android_wear_capabilities` v `androidApp`). */
    const val PHONE_CAPABILITY = "plume_phone"

    /** Hodinky → telefon: data zprávy jsou přihlašovací odkaz v UTF-8. */
    const val PATH_REQUEST = "/plume/watch-login"

    /** Telefon → hodinky: data zprávy jsou jeden z kódů [Result]. */
    const val PATH_RESULT = "/plume/watch-login/result"

    /** Jak dopadla žádost hodinek na telefonu. */
    enum class Result(val wire: String) {
        /** Telefon přihlášení potvrdil; hodinky dojdou do `Ready` samy. */
        Confirmed("confirmed"),

        /** Uživatel v notifikaci zamítl. */
        Declined("declined"),

        /** Plume na telefonu přihlášené není — nemá čím potvrdit. */
        NotSignedIn("not_signed_in"),

        /** Potvrzení selhalo (vypršelý kód, bez spojení…). */
        Failed("failed"),
        ;

        companion object {
            fun fromWire(value: String): Result? = entries.firstOrNull { it.wire == value }
        }
    }

    /** Je to přihlašovací odkaz Telegramu? Nic jiného telefon k potvrzení nepustí. */
    fun isLoginLink(link: String): Boolean = link.trim().startsWith("tg://login?token=", ignoreCase = true)
}
