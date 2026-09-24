package eu.tintera.plume.core.share

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val id = requests.start(text = "x", hasFiles = false)
        requests.abandon(id)
        assertNull(requests.incoming.value)
    }

    @Test
    fun potvrzovanySnimekSiKonverzaceNevyzvedne() = runBlocking {
        // Direct Share: chat je vybraný, ale odesílá potvrzovací obrazovka. Kdyby si obsah vzala
        // konverzace, naskočil by jí nad vstupní pole a odešel by dvakrát.
        val requests = ShareRequests()
        val id = requests.start(text = null, hasFiles = true)
        requests.confirm(7)
        requests.prepared(id, listOf(photo), failed = 0)

        assertNull(withTimeoutOrNull(50) { requests.deliveries(7).first() })
        assertEquals(listOf(photo), requests.incoming.value?.attachments)
    }

    @Test
    fun odchodZPotvrzeniSdileniZahodi() {
        // Odejít z obrazovky „Komu: …" znamená „nechci to poslat" — a bez zahození by se obsah
        // vložil do konverzace, kterou uživatel neotevřel.
        val requests = ShareRequests()
        val id = requests.start(text = "x", hasFiles = false)
        requests.confirm(7)
        requests.abandon(id)
        assertNull(requests.incoming.value)
    }

    @Test
    fun odchodVyberuPoVolbeObsahNezahodi() {
        // Po výběru chatu výběr taky odchází (nahradí ho konverzace) — obsah na ni čeká.
        val requests = ShareRequests()
        val id = requests.start(text = "x", hasFiles = false)
        requests.choose(7)
        requests.abandon(id)
        assertEquals(id, requests.incoming.value?.id)
    }

    @Test
    fun sdileniSChatemNemaMezistavBezChatu() = runBlocking {
        // Direct Share: obsah vzniká rovnou s chatem. Dřív `start` a pak `confirm` — a otevřená
        // obrazovka „Komu: …" mezitím viděla obsah bez chatu a odešla jako „obsah zmizel".
        val requests = ShareRequests()
        val seen = mutableListOf<SharedContent?>()
        val watching = launch(Dispatchers.Unconfined) { requests.incoming.collect { seen += it } }
        requests.start(text = null, hasFiles = true, target = ShareTarget(7, confirm = true))
        watching.cancel()

        val content = seen.filterNotNull().single()
        assertEquals(7, content.targetChatId)
        assertTrue(content.awaitingConfirmation)
    }

    @Test
    fun odchodStareObrazovkyNoveSdileniNezahodi() {
        // Opakované sdílení: kvůli novému obsahu shell starou obrazovku nahrazuje, a její odchod
        // zahazuje JEN to, co ukazovala. Dřív zahodil to nové a nové sdílení se neukázalo vůbec.
        val requests = ShareRequests()
        val old = requests.start(text = null, hasFiles = true, target = ShareTarget(7, confirm = true))
        val new = requests.start(text = null, hasFiles = true, target = ShareTarget(8, confirm = true))

        requests.abandon(old)
        assertEquals(new, requests.incoming.value?.id)

        // Výběr chatu stejně: nový Direct Share jeho odchod nezahodí.
        val chooser = requests.start(text = "x", hasFiles = false)
        val direct = requests.start(text = null, hasFiles = true, target = ShareTarget(7, confirm = true))
        requests.abandon(chooser)
        assertEquals(direct, requests.incoming.value?.id)
    }
}
