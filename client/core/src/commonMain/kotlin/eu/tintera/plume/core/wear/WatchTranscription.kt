package eu.tintera.plume.core.wear

import eu.tintera.plume.core.telegram.OnDeviceFix

/**
 * Přepis hlasovky pro hodinky v telefonu přes Wearable Data Layer: hodinky Gemmu nemají a server
 * přepis bez Premium nedá, telefon ho umí (Gemma nebo systémový rozpoznávač). Hodinky pošlou,
 * kterou zprávu, telefon ji sám stáhne, přepíše a vrátí text — a přepis si nechá i pro sebe.
 *
 * Smlouva mezi `:client:wearApp` a `:client:androidApp` na jednom místě, jako [WatchLogin].
 * **Id zprávy platí jen v rámci účtu**, proto žádost nese účet hodinek (id Uložených zpráv) a
 * telefon přihlášený k jinému účtu odpoví [Status.WrongAccount], místo aby přepsal cizí zprávu.
 */
object WatchTranscription {

    /** Hodinky → telefon: [Request.encode]. */
    const val PATH_REQUEST = "/plume/watch-transcribe"

    /** Telefon → hodinky: [Response.encode]. */
    const val PATH_RESULT = "/plume/watch-transcribe/result"

    data class Request(val accountId: Long, val chatId: Long, val messageId: Long) {
        fun encode(): ByteArray = "$accountId $chatId $messageId".encodeToByteArray()

        companion object {
            fun decode(data: ByteArray): Request? {
                val parts = data.decodeToString().trim().split(' ')
                if (parts.size != 3) return null
                return Request(parts[0].toLongOrNull() ?: return null, parts[1].toLongOrNull() ?: return null, parts[2].toLongOrNull() ?: return null)
            }
        }
    }

    enum class Status(val wire: String) {
        Done("done"),

        /** Přepis se nepovedl (soubor se nestáhl, model nic nevrátil). */
        Failed("failed"),

        /**
         * Telefon přepisovat neumí (bez Gemmy a bez systémového rozpoznávače pro jazyk). Text
         * odpovědi nese, co v telefonu zapnout ([Response.fix]); starší telefon ho nechá prázdný.
         */
        Unavailable("unavailable"),

        /** Telefon je přihlášený k jinému účtu, nebo vůbec. */
        WrongAccount("wrong_account"),
        ;

        companion object {
            fun fromWire(value: String): Status? = entries.firstOrNull { it.wire == value }
        }
    }

    /**
     * Odpověď: první řádek „chat zpráva stav", zbytek je text přepisu (smí mít víc řádků), u
     * [Status.Unavailable] co v telefonu zapnout ([unavailable], [fix]).
     */
    data class Response(val chatId: Long, val messageId: Long, val status: Status, val text: String = "") {
        fun encode(): ByteArray = "$chatId $messageId ${status.wire}\n$text".encodeToByteArray()

        /** Co v telefonu zapnout, aby přepisoval; `null`, když nic nepomůže, nebo to telefon neřekl. */
        val fix: OnDeviceFix?
            get() = if (status == Status.Unavailable) FIX_WIRE.entries.firstOrNull { it.value == text.trim() }?.key else null

        companion object {
            fun decode(data: ByteArray): Response? {
                val raw = data.decodeToString()
                val head = raw.substringBefore('\n').split(' ')
                if (head.size != 3) return null
                return Response(
                    chatId = head[0].toLongOrNull() ?: return null,
                    messageId = head[1].toLongOrNull() ?: return null,
                    status = Status.fromWire(head[2]) ?: return null,
                    text = raw.substringAfter('\n', ""),
                )
            }

            /** [Status.Unavailable] s tím, co v telefonu zapnout. */
            fun unavailable(chatId: Long, messageId: Long, fix: OnDeviceFix?) =
                Response(chatId, messageId, Status.Unavailable, fix?.let { FIX_WIRE.getValue(it) }.orEmpty())
        }
    }

    /** Názvy na drátě: přejmenování ve výčtu nesmí rozbít spojení se starší verzí na druhé straně. */
    private val FIX_WIRE = mapOf(
        OnDeviceFix.EnableAi to "enable_ai",
        OnDeviceFix.SwitchToGemma to "switch_to_gemma",
        OnDeviceFix.DownloadGemma to "download_gemma",
    )
}
