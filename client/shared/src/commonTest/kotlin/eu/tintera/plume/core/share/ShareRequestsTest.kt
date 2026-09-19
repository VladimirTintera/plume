package eu.tintera.plume.core.share

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShareRequestsTest {

    private val photo = OutgoingAttachment.Photo(path = "/cache/outgoing/a/foto.jpg", width = 10, height = 10)

    @Test
    fun obsahSeDoruciAzVybranemuAPripravenemuChatu() = runBlocking {
        val requests = ShareRequests()
        val id = requests.start(text = "popisek", hasFiles = true)

        // Nevybraný chat: nic.
        assertNull(withTimeoutOrNull(50) { requests.deliveries(7).first() })

        // Vybraný, ale soubory se ještě připravují: pořád nic.
        requests.choose(7)
        assertNull(withTimeoutOrNull(50) { requests.deliveries(7).first() })

        requests.prepared(id, listOf(photo), failed = 1)
        val delivered = withTimeout(1_000) { requests.deliveries(7).first() }
        assertEquals(listOf(photo), delivered.attachments)
        assertEquals("popisek", delivered.text)
        assertEquals(1, delivered.failed)

        // Jiný chat ho nedostane.
        assertNull(withTimeoutOrNull(50) { requests.deliveries(8).first() })

        requests.consume(id)
        assertNull(requests.incoming.value)
    }

    @Test
    fun textBezSouboruJeHotovyHned() = runBlocking {
        val requests = ShareRequests()
        requests.start(text = "https://example.com", hasFiles = false)
        requests.choose(7)
        assertEquals(emptyList(), withTimeout(1_000) { requests.deliveries(7).first() }.attachments)
    }

    @Test
    fun noveSdileniNahradiPredchoziAStaraPripravaHoNepreprise() {
        val requests = ShareRequests()
        val first = requests.start(text = null, hasFiles = true)
        val second = requests.start(text = "druhé", hasFiles = true)

        // Příprava prvního doběhla pozdě — druhé sdílení se jí přepsat nesmí.
        requests.prepared(first, listOf(photo), failed = 0)
        assertEquals(second, requests.incoming.value?.id)
        assertNull(requests.incoming.value?.attachments)

        // Vyřízení prvního druhé nezahodí.
        requests.consume(first)
        assertEquals(second, requests.incoming.value?.id)
    }

    @Test
    fun zavrenyVyberSdileniZahodi() {
        val requests = ShareRequests()
        requests.start(text = "x", hasFiles = false)
        requests.abandon()
        assertNull(requests.incoming.value)
    }

    @Test
    fun odchodVyberuPoVolbeObsahNezahodi() {
        // Po výběru chatu výběr taky odchází (nahradí ho konverzace) — obsah na ni čeká.
        val requests = ShareRequests()
        val id = requests.start(text = "x", hasFiles = false)
        requests.choose(7)
        requests.abandon()
        assertEquals(id, requests.incoming.value?.id)
    }
}
