package eu.tintera.plume.core.calls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Vlastní zprávy Plume ↔ Plume za hovoru (ukazovátko na sdílené obrazovce), poslané
 * signalizací hovoru vedle zpráv ntgcalls (`sendCallSignalingData`).
 *
 * - **Telegram ani knihovna hovorů jiný kanál nemají.** TDLib signalizaci jen přenáší
 *   (server ji předá protějšku), knihovna si v ní posílá svoje zprávy zašifrované klíčem
 *   hovoru. Data, která nedešifruje, zahodí — oficiální klient naše zprávy tiše ignoruje.
 * - **Poznají se podle [MAGIC] na začátku** a na přijímající straně se vyloví dřív, než by
 *   šly do knihovny. Signalizace knihovny je šifrovaná, takže začíná náhodnými bajty;
 *   shoda s osmibajtovou značkou je zanedbatelná.
 * - **Šifrované AES-GCM klíčem odvozeným z klíče hovoru** ([sideChannelKey]): server
 *   signalizaci vidí, obsah hovoru vidět nemá.
 * - Jen JSON s typem v poli `t`; neznámý typ (novější verze) se zahodí.
 */
internal object CallSideChannel {

    private val MAGIC = "PLUMESC1".encodeToByteArray()

    private val json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
    }

    fun isOurs(data: ByteArray): Boolean =
        data.size > MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun seal(key: ByteArray, message: CallSideMessage): ByteArray =
        MAGIC + aesGcmSeal(key, json.encodeToString(CallSideMessage.serializer(), message).encodeToByteArray())

    /** `null`, když zpráva nejde rozšifrovat (jiný klíč) nebo přečíst (neznámý typ). */
    fun open(key: ByteArray, data: ByteArray): CallSideMessage? = runCatching {
        val plain = aesGcmOpen(key, data.copyOfRange(MAGIC.size, data.size))
        json.decodeFromString(CallSideMessage.serializer(), plain.decodeToString())
    }.getOrNull()
}

@Serializable
sealed interface CallSideMessage {
    /** Co protějšek umí; posílá se se začátkem sdílení obrazovky. */
    @Serializable
    @SerialName("hello")
    data class Hello(val pointer: Boolean) : CallSideMessage

    /**
     * Ukazovátko na sdílené obrazovce: místo v poměru k obrazu (0–1 zleva / shora),
     * [down] = prst je na obrazovce (tah), `false` = zvednutý (značka vybledne).
     */
    @Serializable
    @SerialName("pointer")
    data class Pointer(val x: Float, val y: Float, val down: Boolean) : CallSideMessage
}

/** Klíč postranního kanálu: SHA-256 z klíče hovoru se solí — ne tentýž klíč jako knihovna. */
internal expect fun sideChannelKey(callKey: ByteArray): ByteArray

/** AES-256-GCM; výsledek je nonce (12 B) + šifrový text se značkou. */
internal expect fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray

internal expect fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray
