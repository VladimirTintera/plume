package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.RecordMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordGestureTest {

    private val cancel = 120f
    private val lock = 80f

    @Test
    fun ulozenaVolbaPlatiKdyzJiChatDovoli() {
        assertEquals(RecordMode.VideoNote, recordModeFor(RecordMode.VideoNote, voiceNotes = true, videoNotes = true))
        assertEquals(RecordMode.Voice, recordModeFor(RecordMode.Voice, voiceNotes = true, videoNotes = true))
    }

    @Test
    fun zakazanyDruhSpadneNaDruhy() {
        // Kolečko uložené z jiného chatu, tady jen hlasovky (nebo zařízení bez kamery).
        assertEquals(RecordMode.Voice, recordModeFor(RecordMode.VideoNote, voiceNotes = true, videoNotes = false))
        assertEquals(RecordMode.VideoNote, recordModeFor(RecordMode.Voice, voiceNotes = false, videoNotes = true))
    }

    @Test
    fun bezObouDruhuNenahravaNic() {
        assertNull(recordModeFor(RecordMode.Voice, voiceNotes = false, videoNotes = false))
    }

    @Test
    fun prepinatJdeJenSObemaDruhy() {
        assertTrue(canSwitchRecordMode(voiceNotes = true, videoNotes = true))
        assertFalse(canSwitchRecordMode(voiceNotes = true, videoNotes = false))
        assertFalse(canSwitchRecordMode(voiceNotes = false, videoNotes = true))
    }

    @Test
    fun kratkyTahNedelaNic() {
        assertEquals(RecordDrag.None, recordDragOutcome(dx = -60f, dy = -40f, cancelDistance = cancel, lockDistance = lock))
    }

    @Test
    fun tahDolevaZahodiNahoruZamkne() {
        assertEquals(RecordDrag.Cancel, recordDragOutcome(dx = -130f, dy = -10f, cancelDistance = cancel, lockDistance = lock))
        assertEquals(RecordDrag.Lock, recordDragOutcome(dx = -10f, dy = -90f, cancelDistance = cancel, lockDistance = lock))
    }

    @Test
    fun opacneSmeryNedelajiNic() {
        // Prst po tlačítku přirozeně ujíždí; doprava a dolů nic neznamená.
        assertEquals(RecordDrag.None, recordDragOutcome(dx = 300f, dy = 300f, cancelDistance = cancel, lockDistance = lock))
    }

    @Test
    fun sikmyTahRozhodnePomerneBlizsiPrah() {
        // Přes oba prahy: zámek je poměrně dál (2×), takže zamkne, nezahodí.
        assertEquals(RecordDrag.Lock, recordDragOutcome(dx = -130f, dy = -160f, cancelDistance = cancel, lockDistance = lock))
        assertEquals(RecordDrag.Cancel, recordDragOutcome(dx = -300f, dy = -90f, cancelDistance = cancel, lockDistance = lock))
    }

    @Test
    fun prubehNapovedyNejdeDoZaporu() {
        assertEquals(0f, cancelProgress(dx = 50f, cancelDistance = cancel))
        assertEquals(0.5f, cancelProgress(dx = -60f, cancelDistance = cancel))
        assertEquals(0f, lockProgress(dy = 20f, lockDistance = lock))
    }
}
