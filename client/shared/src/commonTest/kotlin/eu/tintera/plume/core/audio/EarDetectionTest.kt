package eu.tintera.plume.core.audio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarDetectionTest {

    @Test
    fun telefonUUchaPrepne() {
        // Nastojato u hlavy: tíže míří skoro celá po displeji, ne skrz něj.
        assertTrue(raisedToEar(EarSensors(near = true, gravityZ = 0.15f), headphonesConnected = false))
    }

    /**
     * Tohle je ten případ, kvůli kterému pravidlo není jen „přiblížení".
     *
     * Telefon položený displejem dolů senzor hlásí jako zakrytý úplně stejně jako telefon u ucha;
     * bez kontroly sklonu by hlasovka uprostřed přehrávání ztichla do sluchátka.
     */
    @Test
    fun telefonDisplejemDoluNaStoleNeprepne() {
        assertFalse(raisedToEar(EarSensors(near = true, gravityZ = -0.98f), headphonesConnected = false))
    }

    @Test
    fun telefonDisplejemNahoruNeprepne() {
        // Senzor nic nezakrývá, takže by to neprošlo ani prvním krokem — ale ať to drží obojí.
        assertFalse(raisedToEar(EarSensors(near = false, gravityZ = 0.98f), headphonesConnected = false))
    }

    @Test
    fun oddalenyTelefonNeprepne() {
        assertFalse(raisedToEar(EarSensors(near = false, gravityZ = 0.1f), headphonesConnected = false))
    }

    @Test
    fun seSluchatkySeNeprepinaAniUUcha() {
        assertFalse(raisedToEar(EarSensors(near = true, gravityZ = 0.1f), headphonesConnected = true))
    }

    /**
     * Zařízení bez akcelerometru (nebo než první hodnota dorazí) má `gravityZ` nula, tedy
     * „nastojato". Funkce tím spadne na samotné přiblížení, místo aby přestala fungovat.
     */
    @Test
    fun bezUdajeOSklonuRozhodujePriblizeni() {
        assertTrue(raisedToEar(EarSensors(near = true), headphonesConnected = false))
        assertFalse(raisedToEar(EarSensors(near = false), headphonesConnected = false))
    }

    @Test
    fun prahJeUprostred() {
        assertTrue(raisedToEar(EarSensors(near = true, gravityZ = 0.69f), headphonesConnected = false))
        assertFalse(raisedToEar(EarSensors(near = true, gravityZ = 0.71f), headphonesConnected = false))
    }
}
