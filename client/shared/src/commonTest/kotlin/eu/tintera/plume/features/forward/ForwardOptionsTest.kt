package eu.tintera.plume.features.forward

import eu.tintera.plume.core.telegram.ForwardCapabilities
import eu.tintera.plume.core.telegram.ForwardOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class ForwardOptionsTest {

    private val everything = ForwardCapabilities(canHideSender = true, hasCaptions = true)

    @Test
    fun zvoleneVolbyProjdou() {
        val chosen = ForwardOptions(hideSender = true, hideCaptions = true)
        assertEquals(chosen, chosen.allowedBy(everything))
    }

    @Test
    fun bezKopieNejdeSkrytOdesilatelAniPopisky() {
        // Zprávu, kterou server nedovolí zkopírovat, jde jen přeposlat se značkou.
        val chosen = ForwardOptions(hideSender = true, hideCaptions = true)
        assertEquals(ForwardOptions(), chosen.allowedBy(everything.copy(canHideSender = false)))
    }

    @Test
    fun popiskyPlatiJenSeSkrytymOdesilatelem() {
        // TDLib `removeCaption` bez kopie ignoruje — volba se nesmí tvářit, že platí.
        val chosen = ForwardOptions(hideSender = false, hideCaptions = true)
        assertEquals(ForwardOptions(), chosen.allowedBy(everything))
    }

    @Test
    fun bezPopiskuNeniCoSkryt() {
        val chosen = ForwardOptions(hideSender = true, hideCaptions = true)
        assertEquals(ForwardOptions(hideSender = true), chosen.allowedBy(everything.copy(hasCaptions = false)))
    }

    @Test
    fun bezZvukuPlatiVzdycky() {
        // Nezávisí na zprávách: i zprávu, kterou nejde zkopírovat, jde přeposlat potichu.
        val chosen = ForwardOptions(silent = true)
        assertEquals(ForwardOptions(silent = true), chosen.allowedBy(ForwardCapabilities()))
    }
}
