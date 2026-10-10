package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscribeOfferTest {
    private fun trial(max: Int = 300, weekly: Int = 2, left: Int = 2, reset: Long = 0) =
        SpeechTrial(maxMediaDurationSeconds = max, weeklyCount = weekly, leftCount = left, nextResetDate = reset)

    @Test
    fun sPremiumVzdyckyBezOhleduNaZkusebniPrepisy() {
        assertEquals(TranscribeOffer.Available, transcribeOffer(true, trial(weekly = 0, left = 0), 9_999))
    }

    @Test
    fun neznamyStavRozhodneServer() {
        assertEquals(TranscribeOffer.Available, transcribeOffer(false, null, 9_999))
    }

    @Test
    fun bezZkusebnichPrepisuSeNenabizi() {
        assertEquals(TranscribeOffer.Hidden, transcribeOffer(false, trial(weekly = 0, left = 0), 10))
    }

    @Test
    fun dlouhyZaznamSeVysvetliDrivNezDoslePrepisy() {
        assertEquals(TranscribeOffer.TooLong(300), transcribeOffer(false, trial(left = 0), 301))
        assertEquals(TranscribeOffer.Available, transcribeOffer(false, trial(), 300))
    }

    @Test
    fun doslePrepisyNesouCasObnoveni() {
        assertEquals(TranscribeOffer.UsedUp(1_800_000_000), transcribeOffer(false, trial(left = 0, reset = 1_800_000_000), 10))
    }

    @Test
    fun nulovyStropDelkyNeomezuje() {
        assertEquals(TranscribeOffer.Available, transcribeOffer(false, trial(max = 0), 10_000))
    }

    /** Telefon jen tam, kde server odmítne; server má přednost. */
    @Test
    fun telefonKdyzServerNe() {
        assertEquals(TranscribeOffer.OnDevice, transcribeOffer(false, trial(weekly = 0, left = 0), 10, onDevice = true))
        assertEquals(TranscribeOffer.OnDevice, transcribeOffer(false, trial(left = 0), 10, onDevice = true))
        assertEquals(TranscribeOffer.Available, transcribeOffer(true, null, 10, onDevice = true))
        assertEquals(TranscribeOffer.Available, transcribeOffer(false, trial(), 10, onDevice = true))
    }

    /** Delší záznam než pět minut se v telefonu nepřepisuje — zůstane, co řekne server. */
    @Test
    fun dlouhyZaznamTelefonNebere() {
        assertEquals(TranscribeOffer.Hidden, transcribeOffer(false, trial(weekly = 0, left = 0), MAX_ON_DEVICE_SECONDS + 1, onDevice = true))
    }
}
