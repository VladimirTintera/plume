package eu.tintera.plume.features.media

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilmstripFitsTest {

    @Test
    fun telefonNe() {
        assertFalse(filmstripFits(412.dp, 915.dp))
        // Na šířku je přes 840 dp, ale nízký: pruh by ukrojil z fotky.
        assertFalse(filmstripFits(915.dp, 412.dp))
    }

    @Test
    fun velkeOknoAno() {
        assertTrue(filmstripFits(1536.dp, 864.dp))
        // Rozložený Fold.
        assertTrue(filmstripFits(841.dp, 701.dp))
    }

    @Test
    fun uzkeOknoNaMonitoruNe() {
        assertFalse(filmstripFits(700.dp, 1000.dp))
    }
}
