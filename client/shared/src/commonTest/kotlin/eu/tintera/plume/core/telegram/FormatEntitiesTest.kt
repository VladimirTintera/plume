package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.TextEntityTypeExpandableBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypePre
import dev.g000sha256.tdl.dto.TextEntityTypePreCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Formátování z pole jako entity TDLib — to, co opravdu odejde se zprávou. */
class FormatEntitiesTest {

    @Test
    fun blokKoduSJazykemOdejdeJakoPreCode() {
        val type = listOf(FormatSpan(TextFormat.CodeBlock, 0, 5, language = "kotlin")).toTdl("val x").single().type
        assertIs<TextEntityTypePreCode>(type)
        assertEquals("kotlin", type.language)
    }

    @Test
    fun blokKoduBezJazykaZustavaPre() {
        assertIs<TextEntityTypePre>(listOf(FormatSpan(TextFormat.CodeBlock, 0, 5)).toTdl("val x").single().type)
        // Prázdný jazyk je jako žádný — `preCode` s prázdným jazykem by nic neobarvil.
        assertIs<TextEntityTypePre>(listOf(FormatSpan(TextFormat.CodeBlock, 0, 5, language = "")).toTdl("val x").single().type)
    }

    @Test
    fun sbalitelnaCitaceOdejdeJakoSbalitelna() {
        assertIs<TextEntityTypeExpandableBlockQuote>(
            listOf(FormatSpan(TextFormat.ExpandableQuote, 0, 5)).toTdl("ahoj!").single().type,
        )
    }
}
