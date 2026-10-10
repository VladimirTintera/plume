package eu.tintera.plume.core.ui

/**
 * Nástroj kreslení do fotky před odesláním.
 *
 * Šířka tahu je podíl KRATŠÍ strany fotky, ne body obrazovky: tentýž tah musí v náhledu i ve
 * vykreslené fotce zakrýt totéž, ať je fotka na displeji jakkoli zmenšená.
 */
enum class MarkupTool(val widthFraction: Float) {
    /** Tenká čára barvou z [MARKUP_PEN_COLORS]. */
    Pen(0.008f),

    /** Široký průsvitný žlutý tah — zvýraznit, ne zakrýt. */
    Highlighter(0.045f),

    /**
     * Začernění: široký NEPRŮHLEDNÝ černý tah. Ne rozmazání — rozmazaný text nebo číslo jde
     * při troše snahy přečíst, černá plocha ne.
     */
    Redact(0.06f),
}

/**
 * Jeden tah prstem. Body jsou podíly šířky a výšky fotky (0..1), střídavě x a y; barva je ARGB.
 *
 * Barva je barva OBSAHU (co odejde v obrázku), ne vzhledu aplikace — proto absolutní, ne role
 * z `colorScheme`: zvýraznění nesmí mít v tmavém motivu jinou barvu než ve světlém.
 */
data class MarkupStroke(
    val tool: MarkupTool,
    val color: Long,
    val points: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        other is MarkupStroke && tool == other.tool && color == other.color && points.contentEquals(other.points)

    override fun hashCode(): Int = (tool.hashCode() * 31 + color.hashCode()) * 31 + points.contentHashCode()
}

/** Barvy pera: červená, žlutá, zelená, modrá, bílá, černá. */
val MARKUP_PEN_COLORS: List<Long> = listOf(
    0xFFE53935,
    0xFFFDD835,
    0xFF43A047,
    0xFF1E88E5,
    0xFFFFFFFF,
    0xFF000000,
)

/** Zvýrazňovač: žlutá na 40 %, aby text pod ní zůstal čitelný. */
const val MARKUP_HIGHLIGHTER_COLOR: Long = 0x66FFEB3B

/** Začernění je vždycky plně černé. */
const val MARKUP_REDACT_COLOR: Long = 0xFF000000

/** Barva tahu daného nástroje; pero bere zvolenou [penColor]. */
fun MarkupTool.strokeColor(penColor: Long): Long = when (this) {
    MarkupTool.Pen -> penColor
    MarkupTool.Highlighter -> MARKUP_HIGHLIGHTER_COLOR
    MarkupTool.Redact -> MARKUP_REDACT_COLOR
}

/** Šířka tahu v pixelech fotky [width] × [height]. */
fun MarkupTool.strokeWidth(width: Int, height: Int): Float = widthFraction * minOf(width, height)

/** Vykreslená fotka: nový soubor a jeho rozměry. */
data class MarkedPhoto(val path: String, val width: Int, val height: Int)

/**
 * Vykreslí tahy do fotky v plném rozlišení a uloží NOVÝ soubor v cache vedle ostatních příloh.
 * Původní soubor zůstává: další úprava začíná z něj i s dosavadními tahy, takže jde vzít zpátky
 * i tah z minulé úpravy — z vykreslené kopie by už nešel.
 *
 * Vstupem je fotka připravená výběrem příloh (JPEG, otočená podle EXIF, nejvýš 2560 px),
 * výstupem JPEG téže velikosti.
 */
interface PhotoMarkupRenderer {
    suspend fun render(path: String, strokes: List<MarkupStroke>): MarkedPhoto
}
