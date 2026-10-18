package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.DraftChange
import eu.tintera.plume.core.telegram.DraftDiff

/**
 * Co se změnilo mezi [original] a [revised], ve stejné podobě, jakou vrací oprava od serveru
 * (`fixTextWithAi`): text obsahuje i SMAZANÉ kusy a změny jsou vyznačené rozsahy. Pro opravu
 * modelem v telefonu, který rozdíl sám nevrací — sheet ho pak ukáže stejně jako u serveru.
 *
 * - **Po slovech, ne po znacích.** Oprava diakritiky („mas" → „máš") je změna slova; po
 *   znacích by se zvýraznila dvě písmena uprostřed a slovo by se špatně četlo.
 * - **Náhrada je smazání a hned vložení** („~~mas~~máš"), ne jeden rozsah s novým textem jako
 *   u serveru — tak je vidět, co model změnil, a právě to má zvýraznění ukázat: malý model
 *   občas „opraví" i to, co neměl.
 * - Nejdelší společná podposloupnost slov (O(n·m)); koncept má nejvýš `MAX_REWRITE_CHARS`.
 */
internal fun draftDiff(original: String, revised: String): DraftDiff {
    val a = diffTokens(original)
    val b = diffTokens(revised)
    val common = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            common[i][j] = if (a[i] == b[j]) common[i + 1][j + 1] + 1 else maxOf(common[i + 1][j], common[i][j + 1])
        }
    }
    val text = StringBuilder()
    val changes = mutableListOf<DraftChange>()
    val deleted = StringBuilder()
    val inserted = StringBuilder()
    fun flush() {
        if (deleted.isNotEmpty()) {
            changes += DraftChange(text.length, deleted.length, DraftChange.Kind.Delete)
            text.append(deleted)
            deleted.clear()
        }
        if (inserted.isNotEmpty()) {
            changes += DraftChange(text.length, inserted.length, DraftChange.Kind.Insert)
            text.append(inserted)
            inserted.clear()
        }
    }
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
        when {
            i < a.size && j < b.size && a[i] == b[j] -> {
                flush()
                text.append(a[i])
                i++
                j++
            }
            j < b.size && (i == a.size || common[i][j + 1] >= common[i + 1][j]) -> inserted.append(b[j++])
            else -> deleted.append(a[i++])
        }
    }
    flush()
    return DraftDiff(text.toString(), changes)
}

/**
 * Slova (písmena a číslice), úseky mezer a jednotlivé ostatní znaky. Pár UTF-16 (emoji) zůstává
 * pohromadě — rozdělený by se mohl napůl spárovat s jiným emoji a smazaná půlka by se
 * nevykreslila.
 */
internal fun diffTokens(text: String): List<String> {
    val tokens = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        val end = when {
            char.isLetterOrDigit() -> run {
                var end = index + 1
                while (end < text.length && text[end].isLetterOrDigit()) end++
                end
            }
            char.isWhitespace() -> run {
                var end = index + 1
                while (end < text.length && text[end].isWhitespace()) end++
                end
            }
            char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> index + 2
            else -> index + 1
        }
        tokens += text.substring(index, end)
        index = end
    }
    return tokens
}
