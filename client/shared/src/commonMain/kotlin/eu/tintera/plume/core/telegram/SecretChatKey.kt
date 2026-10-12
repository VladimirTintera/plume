package eu.tintera.plume.core.telegram

/**
 * Otisk klíče tajného chatu k porovnání s protějškem (`SecretChat.keyHash`, 36 bajtů).
 *
 * **Podoba je daná protokolem, ne vkusem**: obrázek 12 × 12 bodů, každý bod dva bity hashe
 * v pořadí little-endian (nejnižší bity prvního bajtu jsou levý horní bod), a čtyři pevné barvy
 * [IDENTICON_PALETTE]. Jen tak vypadá stejně jako v oficiálních aplikacích — a právě s tím ho
 * uživatel porovnává. Jinak řazené bity nebo barvy z motivu by se „neshodovaly" i u správného klíče.
 */
data class SecretChatKey(val bytes: List<Int>) {

    /** Strana obrázku: 12 bodů pro 36bajtový hash, 8 pro starý 16bajtový (vrstva pod 46). */
    val side: Int get() = if (bytes.size >= FULL_SIZE) FULL_SIDE else LEGACY_SIDE

    /** Index barvy (0–3) pro každý bod, po řádcích zleva doprava a shora dolů. */
    val pixels: List<Int>
        get() = List(side * side) { index ->
            val bit = index * 2
            (bytes[bit / 8] shr (bit % 8)) and 0b11
        }

    /**
     * Prvních 32 bajtů šestnáctkově, osm na řádek a uprostřed širší mezera: „26 FA BB DD  03 6D
     * 15 37". **Přesně jako oficiální Android klient** — porovnává se s ním a jinak seskupená
     * nebo malými písmeny psaná čísla se čtou jako jiná, i když jsou stejná.
     */
    val hexLines: List<String>
        get() = bytes.take(HEX_BYTES).chunked(BYTES_PER_LINE).map { line ->
            line.chunked(BYTES_PER_LINE / 2).joinToString("  ") { half ->
                half.joinToString(" ") { it.toString(16).padStart(2, '0').uppercase() }
            }
        }

    companion object {
        /** Bílá, světle modrá, tmavě modrá, modrá — pořadí podle hodnoty dvou bitů (TDLib). */
        val IDENTICON_PALETTE = listOf(0xFFFFFFFF, 0xFFD5E6F3, 0xFF2D5775, 0xFF2F99C9)

        private const val FULL_SIZE = 36
        private const val FULL_SIDE = 12
        private const val LEGACY_SIDE = 8
        private const val HEX_BYTES = 32
        private const val BYTES_PER_LINE = 8

        /** Prázdný hash (chat ještě není dohodnutý) → `null`, ukázat není co. */
        fun of(bytes: ByteArray): SecretChatKey? =
            bytes.takeIf { it.size >= LEGACY_SIDE * LEGACY_SIDE / 4 }?.let { SecretChatKey(it.map { b -> b.toInt() and 0xFF }) }
    }
}
