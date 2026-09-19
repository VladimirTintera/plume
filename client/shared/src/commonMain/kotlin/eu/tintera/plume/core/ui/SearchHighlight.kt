package eu.tintera.plume.core.ui

/**
 * Úseky [text], kde stojí [query], bez překryvů. Pro zvýraznění nalezeného slova ve výsledku
 * hledání zpráv napříč chaty i v bublinách při hledání v chatu.
 *
 * **Bez ohledu na velikost písmen i diakritiku** — tak hledá TDLib: „obslehl" najde „Obšlehl"
 * a zvýraznění se s tím musí shodnout, jinak by nalezená zpráva nic zvýrazněného neměla.
 * Porovnává se po znacích ([foldDiacritics] délku nemění), takže pozice sedí na původní text.
 */
internal fun highlightRanges(text: String, query: String): List<IntRange> {
    val needle = foldDiacritics(query.trim())
    if (needle.isEmpty()) return emptyList()
    val haystack = foldDiacritics(text)
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val index = haystack.indexOf(needle, from, ignoreCase = true)
        if (index < 0) break
        ranges += index until index + needle.length
        from = index + needle.length
    }
    return ranges
}

/**
 * Písmena s diakritikou na základní, znak za znak (délka textu se nemění). Pokrývá latinku
 * střední a západní Evropy; Kotlin v commonMain `Normalizer` nemá a pro zvýraznění to stačí —
 * nepokrytý znak se jen nezvýrazní.
 */
internal fun foldDiacritics(text: String): String = buildString(text.length) {
    text.forEach { append(DIACRITICS[it] ?: it) }
}

private val DIACRITICS: Map<Char, Char> = buildMap {
    fun map(from: String, to: Char) = from.forEach { put(it, to) }
    // Znaky jsou zapsané escape sekvencemi: `verifyNoHardcodedText` hlídá diakritiku v literálech
    // (text pro uživatele patří do prostředků) a tahle tabulka text pro uživatele není.
    // áàâäãåą → a, ÁÀÂÄÃÅĄ → A
    map("\u00e1\u00e0\u00e2\u00e4\u00e3\u00e5\u0105", 'a'); map("\u00c1\u00c0\u00c2\u00c4\u00c3\u00c5\u0104", 'A')
    // čćç → c, ČĆÇ → C
    map("\u010d\u0107\u00e7", 'c'); map("\u010c\u0106\u00c7", 'C')
    // ď → d, Ď → D
    map("\u010f", 'd'); map("\u010e", 'D')
    // éěèêëę → e, ÉĚÈÊËĘ → E
    map("\u00e9\u011b\u00e8\u00ea\u00eb\u0119", 'e'); map("\u00c9\u011a\u00c8\u00ca\u00cb\u0118", 'E')
    // íìîï → i, ÍÌÎÏ → I
    map("\u00ed\u00ec\u00ee\u00ef", 'i'); map("\u00cd\u00cc\u00ce\u00cf", 'I')
    // ľĺł → l, ĽĹŁ → L
    map("\u013e\u013a\u0142", 'l'); map("\u013d\u0139\u0141", 'L')
    // ňńñ → n, ŇŃÑ → N
    map("\u0148\u0144\u00f1", 'n'); map("\u0147\u0143\u00d1", 'N')
    // óòôöõő → o, ÓÒÔÖÕŐ → O
    map("\u00f3\u00f2\u00f4\u00f6\u00f5\u0151", 'o'); map("\u00d3\u00d2\u00d4\u00d6\u00d5\u0150", 'O')
    // řŕ → r, ŘŔ → R
    map("\u0159\u0155", 'r'); map("\u0158\u0154", 'R')
    // šś → s, ŠŚ → S
    map("\u0161\u015b", 's'); map("\u0160\u015a", 'S')
    // ť → t, Ť → T
    map("\u0165", 't'); map("\u0164", 'T')
    // úůùûüű → u, ÚŮÙÛÜŰ → U
    map("\u00fa\u016f\u00f9\u00fb\u00fc\u0171", 'u'); map("\u00da\u016e\u00d9\u00db\u00dc\u0170", 'U')
    // ýÿ → y, ÝŸ → Y
    map("\u00fd\u00ff", 'y'); map("\u00dd\u0178", 'Y')
    // žźż → z, ŽŹŻ → Z
    map("\u017e\u017a\u017c", 'z'); map("\u017d\u0179\u017b", 'Z')
}
