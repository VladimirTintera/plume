package eu.tintera.plume.core.calls

/**
 * Prořeže opakující se řádky výpisu: každý DRUH zprávy (tag a text bez čísel, adres a obsahu
 * hranatých závorek) projde poprvé a pak nejvýš jednou za [windowMs], s počtem vynechaných.
 *
 * Proč druh, ne tag: dvouapůlhodinový hovor vyrobil přes 180 tisíc řádků WebRTC (19 MB) a skoro
 * všechno byla opakování — odeslaný a nevyřízený STUN ping, ping na reflector, znovusbírání
 * kandidátů na rozhraní bez internetu. Výpis podle tagů by buď spadl na strop, nebo by musel
 * zahodit celé soubory (`connection.cc`), ve kterých jsou i ty vzácné užitečné řádky
 * (`Connection pruned`, `Timed out`). Takhle zůstane vidět, CO se děje a jak často, a změna
 * (nový druh zprávy) projde hned.
 */
internal class RepeatFilter(
    private val windowMs: Long = 60_000,
    private val maxKinds: Int = 2_000,
) {

    private class Kind(var emittedAtMs: Long, var suppressed: Int)

    // Nejdéle nepoužitý druh vypadne první, ať paměť neroste s každou novou adresou.
    private val kinds = LinkedHashMap<String, Kind>()

    /** Řádek k zápisu (s počtem vynechaných od minula), nebo `null`, když se má vynechat. */
    fun accept(tag: String, message: String, line: String, atMs: Long): String? {
        val key = "$tag ${normalize(message)}"
        val kind = kinds.remove(key)
        if (kind == null) {
            if (kinds.size >= maxKinds) kinds.remove(kinds.keys.first())
            kinds[key] = Kind(atMs, 0)
            return line
        }
        kinds[key] = kind
        if (atMs - kind.emittedAtMs < windowMs) {
            kind.suppressed++
            return null
        }
        val suppressed = kind.suppressed
        kind.emittedAtMs = atMs
        kind.suppressed = 0
        return if (suppressed > 0) "$line  (+$suppressed similar)" else line
    }

    internal companion object {
        private val lineNumber = Regex("""\(line \d+\)""")
        private val brackets = Regex("""\[[^\[\]]*]""")
        private val hex = Regex("""\b[0-9a-fA-F]{6,}\b""")
        private val digits = Regex("""\d+""")

        fun normalize(message: String): String {
            var text = lineNumber.replace(message, "")
            // Vnořené závorky (`Conn[…:Net[wlan0:…]:…]`) se loupou zevnitř.
            repeat(3) { text = brackets.replace(text, "{}") }
            text = hex.replace(text, "H")
            return digits.replace(text, "N")
        }
    }
}
