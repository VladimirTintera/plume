package eu.tintera.plume.core.telegram

/**
 * Když je [text] právě jedno z [emojis] (seznam od serveru, [ChatRepository.diceEmojis]),
 * vrátí ho v podobě od serveru — zpráva pak odejde jako hod, ne jako text. Jinak `null`.
 *
 * Porovnává se bez výběru varianty (U+FE0F): klávesnice posílá „⚽️" i „⚽" podle toho,
 * odkud emoji vzala, a server by jinou podobu, než jakou má v seznamu, jako hod nepoznal.
 */
fun diceEmojiFor(text: String, emojis: List<String>): String? {
    val wanted = text.trim().withoutVariationSelector()
    if (wanted.isEmpty()) return null
    return emojis.firstOrNull { it.withoutVariationSelector() == wanted }
}

private fun String.withoutVariationSelector(): String = replace("️", "")
