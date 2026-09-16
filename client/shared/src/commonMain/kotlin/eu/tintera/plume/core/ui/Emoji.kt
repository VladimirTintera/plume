package eu.tintera.plume.core.ui

/**
 * Kolik emoji je ve zprávě, když v ní **není nic jiného** — jinak 0.
 *
 * Slouží k rozhodnutí, jestli se zpráva vykreslí velká a bez bubliny. Nad [max] se vrací 0:
 * dvacet emoji v řadě už je odstavec, ne gesto, a ve velké sazbě by zabral půl obrazovky.
 *
 * **Počítají se shluky, ne kódové body.** Jedna rodina (👨‍👩‍👧) je pět kódových bodů spojených
 * ZWJ a jedna vlajka (🇨🇿) dva regionální indikátory — pro uživatele jsou to jednotlivé emoji
 * a takhle se i počítají.
 *
 * Rozsahy jsou **pragmatický výběr, ne úplná unicode tabulka.** Kotlin v commonMain nemá ani
 * `codePointAt`, ani `\p{...}` v regulárních výrazech, takže nést s sebou celá data emoji by
 * znamenalo vlastní tabulku a její údržbu při každé revizi Unicode. Chyba na kteroukoli stranu
 * je levná: nepoznané emoji se vykreslí normálně velké (nic se nerozbije) a symbol omylem
 * považovaný za emoji se ukáže velký.
 */
fun String.emojiClusterCount(max: Int): Int {
    if (isBlank()) return 0

    var count = 0
    var joined = false
    var previousRegional = false
    var index = 0

    while (index < length) {
        val code = codePoint(index)
        index += if (code > 0xFFFF) 2 else 1

        when {
            // Mezery mezi emoji jsou v pořádku; „👍 👍" je pořád jen emoji.
            code.isSpace() -> previousRegional = false

            // Spojka ZWJ: následující emoji patří do téhož shluku.
            code == ZWJ -> joined = true

            // Modifikátory (odstín pleti, výběr varianty, klávesnicové rámečky) se váží
            // na předchozí emoji a samy shluk nezakládají.
            code.isModifier() -> Unit

            !code.isEmoji() -> return 0

            joined -> joined = false

            // Vlajka = dva regionální indikátory za sebou. Druhý z nich jen dokončuje první.
            code.isRegionalIndicator() && previousRegional -> previousRegional = false

            else -> {
                previousRegional = code.isRegionalIndicator()
                count++
                if (count > max) return 0
            }
        }
    }

    return count
}

/**
 * Začátek posledního znaku před pozicí [end], tak jak ho vidí uživatel — pro mazání pozpátku.
 *
 * Smazat jednu jednotku UTF-16 by u emoji nechalo v textu půlku surrogate páru, u rodiny (👨‍👩‍👧)
 * nebo vlajky (🇨🇿) zbytek složeniny. Proto se odebírá celý shluk: kódový bod i s modifikátory
 * (odstín pleti, výběr varianty, rámeček klávesy), spojené přes ZWJ, a vlajka jako dvojice
 * regionálních indikátorů. Stejně pragmatické jako [emojiClusterCount] — u obyčejného textu
 * jde prostě o jeden znak.
 */
fun String.previousClusterStart(end: Int): Int {
    var index = end.coerceIn(0, length)
    if (index == 0) return 0

    index = previousCodePointStart(index)
    // Modifikátory patří k předchozímu kódovému bodu — smaže se i on.
    while (index > 0 && codePoint(index).isModifier()) {
        index = previousCodePointStart(index)
    }
    // Vlajka: dva regionální indikátory za sebou tvoří jeden znak.
    if (codePoint(index).isRegionalIndicator() && index > 0) {
        val before = previousCodePointStart(index)
        if (codePoint(before).isRegionalIndicator()) index = before
    }
    // Spojka ZWJ: předchozí emoji je součást téhož shluku, pokračuje se před ní.
    while (index > 0 && this[index - 1].code == ZWJ) {
        index = previousCodePointStart(index - 1)
        while (index > 0 && codePoint(index).isModifier()) {
            index = previousCodePointStart(index)
        }
    }
    return index
}

/** Začátek kódového bodu, který končí těsně před [index]. */
private fun String.previousCodePointStart(index: Int): Int =
    if (index >= 2 && this[index - 1].isLowSurrogate() && this[index - 2].isHighSurrogate()) index - 2 else index - 1

/** Kódový bod na pozici — commonMain `codePointAt` nemá, surrogate páry se musí složit ručně. */
private fun String.codePoint(index: Int): Int {
    val high = this[index]
    if (!high.isHighSurrogate() || index + 1 >= length) return high.code

    val low = this[index + 1]
    if (!low.isLowSurrogate()) return high.code

    return 0x10000 + (high.code - 0xD800) * 0x400 + (low.code - 0xDC00)
}

private fun Int.isSpace(): Boolean = this == 0x20 || this == 0x0A || this == 0x09 || this == 0xA0

private fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

private fun Int.isModifier(): Boolean = this in 0x1F3FB..0x1F3FF || // odstíny pleti
    this == 0xFE0F || this == 0xFE0E || // výběr emoji/textové varianty
    this == 0x20E3 || // rámeček klávesy
    this in 0xE0020..0xE007F // tagy (vlajky regionů)

/**
 * Emoji, které může založit shluk.
 *
 * Nezahrnuje ASCII, takže „1", „#" ani „*" se samy za emoji nepovažují — klávesnicové emoji
 * (`1️⃣`) se tím sice minou, ale bez toho by za emoji prošlo každé číslo v textu.
 */
private fun Int.isEmoji(): Boolean = EMOJI_RANGES.any { this in it }

private val EMOJI_RANGES = listOf(
    0x00A9..0x00A9, // ©
    0x00AE..0x00AE, // ®
    0x203C..0x203C,
    0x2049..0x2049,
    0x2122..0x2122, // ™
    0x2139..0x2139,
    0x2194..0x21AA, // šipky s emoji variantou
    0x231A..0x231B, // ⌚⌛
    0x2328..0x2328,
    0x23CF..0x23CF,
    0x23E9..0x23F3, // ⏩ … ⏳
    0x23F8..0x23FA,
    0x24C2..0x24C2,
    0x25AA..0x25FE, // malé čtverce a kruhy
    0x2600..0x27BF, // různé symboly a dingbats
    0x2934..0x2935,
    0x2B00..0x2BFF, // ⬅ ⬛ ⭐ ⭕
    0x3030..0x3030,
    0x303D..0x303D,
    0x3297..0x3297,
    0x3299..0x3299,
    0x1F000..0x1FAFF, // hlavní emoji bloky včetně regionálních indikátorů
)

/** Zero-width joiner — lepí kódové body do jednoho emoji. */
private const val ZWJ = 0x200D
