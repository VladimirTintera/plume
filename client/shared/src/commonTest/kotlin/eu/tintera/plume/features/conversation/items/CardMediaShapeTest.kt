package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Snímek v kartě s popiskem si bere jen ta nároží karty, do kterých sahá — kvůli letu do prohlížeče. */
class CardMediaShapeTest {
    private val card = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    private val size = Size(100f, 100f)
    private val density = Density(1f)

    private fun corners(hasHeader: Boolean, hasCaption: Boolean): List<Float> {
        val shape = cardMediaShape(card, hasHeader = hasHeader, hasCaption = hasCaption) as CornerBasedShape
        return listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).map { it.toPx(size, density) }
    }

    @Test
    fun sPopiskemMaHorniNarozi() {
        assertEquals(listOf(4f, 18f, 0f, 0f), corners(hasHeader = false, hasCaption = true))
    }

    @Test
    fun sHlavickouMaDolniNarozi() {
        assertEquals(listOf(0f, 0f, 18f, 18f), corners(hasHeader = true, hasCaption = false))
    }

    @Test
    fun sHlavickouIPopiskemJeHranaty() {
        assertEquals(listOf(0f, 0f, 0f, 0f), corners(hasHeader = true, hasCaption = true))
    }
}
