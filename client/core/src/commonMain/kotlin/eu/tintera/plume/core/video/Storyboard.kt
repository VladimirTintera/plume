package eu.tintera.plume.core.video

/** Dlaždice storyboardu platná od [atMs]: levý horní roh v obrázku ([left], [top], px). */
data class StoryboardFrame(val atMs: Long, val left: Int, val top: Int)

/**
 * Mapa storyboardu (soubor `application/x-tgstoryboardmap`).
 *
 * Formát není nikde popsaný; převzatý z oficiálního klienta pro Android (`VideoSeekPreviewImage.
 * parseStoryBoardMap`): řádky `file=mtproto:<id>`, `frame_width=`, `frame_height=` (rozměry
 * dlaždice — ty dává TDLib i sama) a pak `čas,x,y` — čas v sekundách s desetinnou částí,
 * souřadnice dlaždice v obrázku. Neznámé a vadné řádky se přeskočí, pořadí se srovná.
 */
fun parseStoryboardMap(text: String): List<StoryboardFrame> = text.lineSequence()
    .mapNotNull { line ->
        val parts = line.trim().split(',')
        if (parts.size != 3) return@mapNotNull null
        val seconds = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val left = parts[1].toIntOrNull() ?: return@mapNotNull null
        val top = parts[2].toIntOrNull() ?: return@mapNotNull null
        StoryboardFrame((seconds * 1000).toLong(), left, top)
    }
    .sortedBy { it.atMs }
    .toList()

/** Dlaždice pro čas [ms]: poslední, která začíná nejpozději v něm (před první ta první). */
fun List<StoryboardFrame>.frameAt(ms: Long): StoryboardFrame? {
    if (isEmpty()) return null
    var low = 0
    var high = lastIndex
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (this[mid].atMs <= ms) low = mid else high = mid - 1
    }
    return this[low]
}
