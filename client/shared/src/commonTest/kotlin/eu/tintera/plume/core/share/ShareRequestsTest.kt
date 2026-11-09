package eu.tintera.plume.core.share

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareRequestsTest {

    private val photo = OutgoingAttachment.Photo(path = "/cache/outgoing/a/foto.jpg", width = 10, height = 10)

    @Test
    fun obsahSeDoruciAzVybranemuAPripravenemuChatu() = runBlocking {
        val requests = ShareRequests()
        val id = requests.start(text = "popisek", hasFiles = true)

        // Nevybraný chat: nic.
        assertNull(withTimeoutOrNull(50) { requests.deliveries(AccountSlot.Primary, 7).first() })

        // Vybraný, ale soubory se ještě připravují: pořád nic.
        requests.choose(AccountSlot.Primary, 7)
        assertNull(withTimeoutOrNull(50) { requests.deliveries(AccountSlot.Primary, 7).first() })

        requests.prepared(id, listOf(photo), failed = 1)
        val delivered = withTimeout(1_000) { requests.deliveries(AccountSlot.Primary, 7).first() }
        assertEquals(listOf(photo), delivered.attachments)
        assertEquals("popisek", delivered.text)
        assertEquals(1, delivered.failed)

        // Jiný chat ho nedostane.
        assertNull(withTimeoutOrNull(50) { requests.deliveries(AccountSlot.Primary, 8).first() })

        requests.consume(id)
        assertNull(requests.incoming.value)
    }

    @Test
    fun textBezSouboruJeHotovyHned() = runBlocking {
        val requests = ShareRequests()
        requests.start(text = "https://example.com", hasFiles = false)
        requests.choose(AccountSlot.Primary, 7)
        assertEquals(emptyList(), withTimeout(1_000) { requests.deliveries(AccountSlot.Primary, 7).first() }.attachments)
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
    fun potvrzovanySnimekSiKonverzaceNevyzvedne() = runBlocking {
        // Direct Share: chat je vybraný, ale odesílá potvrzovací obrazovka. Kdyby si obsah vzala
        // konverzace, naskočil by jí nad vstupní pole a odešel by dvakrát.
        val requests = ShareRequests()
        val id = requests.start(text = null, hasFiles = true)
        requests.confirm(AccountSlot.Primary, 7)
        requests.prepared(id, listOf(photo), failed = 0)

        assertNull(withTimeoutOrNull(50) { requests.deliveries(AccountSlot.Primary, 7).first() })
        assertEquals(listOf(photo), requests.incoming.value?.attachments)
    }

    /** Soukromý chat má v obou účtech totéž id: obsah si smí vyzvednout jen konverzace jeho účtu. */
    @Test
    fun sdileniJdeJenDoUctuVybranehoChatu() = runBlocking {
        val requests = ShareRequests()
        requests.start(text = "x", hasFiles = false, target = ShareTarget(AccountSlot("a1b2"), 7, confirm = false))

        assertNull(withTimeoutOrNull(50) { requests.deliveries(AccountSlot.Primary, 7).first() })
        assertEquals("x", requests.deliveries(AccountSlot("a1b2"), 7).first().text)
    }

    @Test
    fun odchodZPotvrzeniSdileniZahodi() {
        // Odejít z obrazovky „Komu: …" znamená „nechci to poslat" — a bez zahození by se obsah
        // vložil do konverzace, kterou uživatel neotevřel.
        val requests = ShareRequests()
        val id = requests.start(text = "x", hasFiles = false, target = ShareTarget(AccountSlot.Primary, 7, confirm = true))
        requests.abandon(id)
        assertNull(requests.incoming.value)
    }

    @Test
    fun sdileniSChatemNemaMezistavBezChatu() = runBlocking {
        // Direct Share: obsah vzniká rovnou s chatem. Dřív `start` a pak `confirm` — a otevřená
        // obrazovka „Komu: …" mezitím viděla obsah bez chatu a odešla jako „obsah zmizel".
        val requests = ShareRequests()
        val seen = mutableListOf<SharedContent?>()
        val watching = launch(Dispatchers.Unconfined) { requests.incoming.collect { seen += it } }
        requests.start(text = null, hasFiles = true, target = ShareTarget(AccountSlot.Primary, 7, confirm = true))
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
        val old = requests.start(text = null, hasFiles = true, target = ShareTarget(AccountSlot.Primary, 7, confirm = true))
        val new = requests.start(text = null, hasFiles = true, target = ShareTarget(AccountSlot.Primary, 8, confirm = true))

        requests.abandon(old)
        assertEquals(new, requests.incoming.value?.id)

        // Nové sdílení na čip: odchod „Komu: …", kterou kvůli němu shell zavírá, ho nezahodí.
        val chip = requests.start(text = "x", hasFiles = false)
        requests.abandon(new)
        assertEquals(chip, requests.incoming.value?.id)
    }

    // --- Sdílení na čipu: perzistence, vypršení, ✕ a opakované sdílení -------------------------

    private val fileSystem = FileSystem.SYSTEM
    private val folder: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "share-${Random.nextLong()}"
    private var clock = 1_000_000L

    @AfterTest
    fun cleanUp() {
        fileSystem.deleteRecursively(folder, mustExist = false)
    }

    private fun store() = FileShareStore(folder, fileSystem)

    /** Nová instance nad týmž úložištěm = nový proces po zabití toho starého. */
    private fun process() = ShareRequests(store = store(), now = { clock })

    /** Kopie sdíleného souboru, jak ji dělá `ShareIntake`: `outgoing/<uuid>/jméno`. */
    private fun copy(name: String): String {
        val dir = folder / "copy-${Random.nextLong()}"
        fileSystem.createDirectories(dir)
        val file = dir / name
        fileSystem.write(file) { writeUtf8("x") }
        return file.toString()
    }

    private fun exists(path: String) = fileSystem.exists(path.toPath())

    private fun copiedPhoto(name: String = "fotka.jpg") =
        OutgoingAttachment.Photo(path = copy(name), width = 4, height = 3)

    @Test
    fun sdileniBezChatuNeseCipASdileniSChatemNe() {
        val requests = ShareRequests()
        requests.start(text = "x", hasFiles = false)
        assertTrue(requests.incoming.value!!.carried)
        requests.start(text = "y", hasFiles = false, target = ShareTarget(AccountSlot.Primary, 7, confirm = false))
        assertFalse(requests.incoming.value!!.carried)
    }

    @Test
    fun prichodCipuSeOhlasiJednou() {
        val requests = ShareRequests()
        val id = requests.start(text = "x", hasFiles = false)
        assertEquals(id, requests.arrival.value)
        requests.arrivalShown(id)
        assertNull(requests.arrival.value)

        // Direct Share seznam chatů neukazuje — má vlastní obrazovku.
        requests.start(text = null, hasFiles = true, target = ShareTarget(AccountSlot.Primary, 7, confirm = true))
        assertNull(requests.arrival.value)
    }

    @Test
    fun odchodVyberuPoVolbeObsahNezahodi() {
        // Po „Vložit sem" je obsah na cestě do konverzace — nic ho zahodit nesmí.
        val requests = ShareRequests()
        val id = requests.start(text = "x", hasFiles = false)
        requests.choose(AccountSlot.Primary, 7)
        requests.abandon(id)
        assertEquals(id, requests.incoming.value?.id)
        assertTrue(requests.incoming.value!!.carried, "čip nezmizí, dokud si ho konverzace nevyzvedne")
    }

    @Test
    fun cipPrezijeZabitiProcesu() {
        val photo = copiedPhoto()
        val first = process()
        val id = first.start(text = "popisek", hasFiles = true)
        first.prepared(id, listOf(photo), failed = 1, sources = mapOf(photo.path to "content://x"))

        val restored = process().incoming.value
        assertNotNull(restored)
        assertTrue(restored.carried)
        assertNull(restored.targetChatId)
        assertEquals("popisek", restored.text)
        assertEquals(listOf<OutgoingAttachment>(photo), restored.attachments)
        assertEquals(1, restored.failed)
        assertEquals(mapOf(photo.path to "content://x"), restored.sources)
        assertEquals(clock, restored.createdAt)
    }

    @Test
    fun obnovenyCipSeznamChatuZnovuNeotevira() {
        process().start(text = "https://example.com", hasFiles = false)
        val restored = process()
        assertNotNull(restored.incoming.value)
        // Zásobník se po zabití procesu obnovil s ním — nic se nemá zavírat.
        assertNull(restored.arrival.value)
    }

    @Test
    fun rozpracovanaPripravaSeNeuklada() {
        // Oprávnění ke zdrojovým URI zabití procesu nepřežije — přípravu by nešlo dokončit.
        process().start(text = null, hasFiles = true)
        assertNull(process().incoming.value)
    }

    @Test
    fun vlozeneSdileniSeObnovouNevrati() = runBlocking {
        val photo = copiedPhoto()
        val requests = process()
        val id = requests.start(text = null, hasFiles = true)
        requests.prepared(id, listOf(photo), failed = 0)

        // „Vložit sem": konverzace si obsah vyzvedne a vyřídí.
        requests.choose(AccountSlot.Primary, 7)
        val delivered = withTimeout(1_000) { requests.deliveries(AccountSlot.Primary, 7).first() }
        assertTrue(requests.consume(delivered.id))

        assertNull(process().incoming.value)
        // Soubory si převzala konverzace — smazat se nesmí.
        assertTrue(exists(photo.path))
    }

    @Test
    fun vlozeniJenJednou() {
        val requests = ShareRequests()
        val id = requests.start(text = "x", hasFiles = false)
        requests.choose(AccountSlot.Primary, 7)
        assertTrue(requests.consume(id))
        assertFalse(requests.consume(id), "druhá konverzace téhož chatu už nic nevloží")
    }

    @Test
    fun vybranyAleNevyzvednutyCipSeObnoviZnovuNaCipu() {
        val requests = process()
        requests.start(text = "x", hasFiles = false)
        requests.choose(AccountSlot.Primary, 7)

        val restored = process().incoming.value
        assertNotNull(restored)
        assertNull(restored.targetChatId)
    }

    @Test
    fun krizekZahodiObsahIKopie() {
        val photo = copiedPhoto()
        val requests = process()
        val id = requests.start(text = null, hasFiles = true)
        requests.prepared(id, listOf(photo), failed = 0)

        requests.discard(id)

        assertNull(requests.incoming.value)
        assertFalse(exists(photo.path))
        assertNull(process().incoming.value)
    }

    @Test
    fun krizekStarehoCipuNoveSdileniNezahodi() {
        val requests = ShareRequests()
        val old = requests.start(text = "staré", hasFiles = false)
        val new = requests.start(text = "nové", hasFiles = false)
        requests.discard(old)
        assertEquals(new, requests.incoming.value?.id)
    }

    @Test
    fun opakovaneSdileniNahradiCipISmazeStareKopie() {
        val old = copiedPhoto("stara.jpg")
        val requests = process()
        val first = requests.start(text = null, hasFiles = true)
        requests.prepared(first, listOf(old), failed = 0)

        val second = requests.start(text = "https://example.com", hasFiles = false)

        assertEquals(second, requests.incoming.value?.id)
        assertEquals(second, requests.arrival.value)
        assertFalse(exists(old.path))
        assertEquals("https://example.com", process().incoming.value?.text)
    }

    @Test
    fun opozdenaPripravaZahozenehoSdileniSmazeSveKopie() {
        val requests = process()
        val id = requests.start(text = null, hasFiles = true)
        requests.discard(id)

        val late = copiedPhoto("pozde.jpg")
        requests.prepared(id, listOf(late), failed = 0)

        assertNull(requests.incoming.value)
        assertFalse(exists(late.path))
    }

    @Test
    fun poDniSdileniVyprsi() {
        val photo = copiedPhoto()
        val requests = process()
        val id = requests.start(text = null, hasFiles = true)
        requests.prepared(id, listOf(photo), failed = 0)

        clock += SHARE_LIFETIME_MS - 1
        assertNotNull(process().incoming.value, "den ještě neuplynul")
        requests.dropExpired()
        assertNotNull(requests.incoming.value)

        clock += 1
        requests.dropExpired()
        assertNull(requests.incoming.value)
        assertFalse(exists(photo.path))
    }

    @Test
    fun poDniSeSdileniPriStartuNeobnovi() {
        val photo = copiedPhoto()
        val requests = process()
        val id = requests.start(text = null, hasFiles = true)
        requests.prepared(id, listOf(photo), failed = 0)

        clock += SHARE_LIFETIME_MS
        assertNull(process().incoming.value)
        assertFalse(exists(photo.path))
        assertFalse(fileSystem.exists(folder / "shared-content.json"), "záznam je smazaný, ne jen přeskočený")
    }

    @Test
    fun ztracenaKopieSePriObnovePocitaJakoNepripravena() {
        // Cache smí systém vyčistit; co z ní zmizelo, se ohlásí jako soubor, který nevyšel.
        val kept = copiedPhoto("zustala.jpg")
        val lost = copiedPhoto("zmizela.jpg")
        val requests = process()
        val id = requests.start(text = null, hasFiles = true)
        requests.prepared(id, listOf(kept, lost), failed = 0)
        fileSystem.delete(lost.path.toPath())

        val restored = process().incoming.value
        assertEquals(listOf<OutgoingAttachment>(kept), restored?.attachments)
        assertEquals(1, restored?.failed)
    }

    @Test
    fun directShareSeNeukladaAChovaSeJakDriv() = runBlocking {
        val photo = copiedPhoto()
        val requests = process()
        val id = requests.start(text = null, hasFiles = true, target = ShareTarget(AccountSlot.Primary, 7, confirm = true))
        requests.prepared(id, listOf(photo), failed = 0)

        // Neuloží se: obrazovka „Komu: …" se po zabití procesu stejně zavře.
        assertNull(process().incoming.value)
        assertFalse(requests.incoming.value!!.carried)
        assertNull(requests.arrival.value)
        // Konverzace si potvrzovaný obsah nevyzvedne, odchod z obrazovky ho zahodí.
        assertNull(withTimeoutOrNull(50) { requests.deliveries(AccountSlot.Primary, 7).first() })
        requests.abandon(id)
        assertNull(requests.incoming.value)
    }

    @Test
    fun directSharePresCipCipNahradi() {
        val photo = copiedPhoto()
        val requests = process()
        val chip = requests.start(text = null, hasFiles = true)
        requests.prepared(chip, listOf(photo), failed = 0)

        val direct = requests.start(text = null, hasFiles = true, target = ShareTarget(AccountSlot.Primary, 7, confirm = true))

        assertEquals(direct, requests.incoming.value?.id)
        assertFalse(exists(photo.path), "kopie z čipu už nikdo nepošle")
        assertNull(process().incoming.value)
    }

    @Test
    fun poskozenyZaznamSeZahodi() {
        fileSystem.createDirectories(folder)
        fileSystem.write(folder / "shared-content.json") { writeUtf8("{nesmysl") }
        assertNull(process().incoming.value)
        assertFalse(fileSystem.exists(folder / "shared-content.json"))
    }

    @Test
    fun mazeJenUvnitrSveSlozky() {
        // Cesta mimo úložiště (chyba v záznamu) se nesmaže — nesmí zaplatit cizí soubor.
        val outside = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "share-outside-${Random.nextLong()}"
        fileSystem.createDirectories(outside)
        val file = outside / "cizi.jpg"
        fileSystem.write(file) { writeUtf8("x") }
        try {
            store().deleteFiles(listOf(file.toString()))
            assertTrue(fileSystem.exists(file))
        } finally {
            fileSystem.deleteRecursively(outside, mustExist = false)
        }
    }

    @Test
    fun videoSNahledemProjdeZaznamem() {
        val video = OutgoingAttachment.Video(
            path = copy("video.mp4"),
            width = 1920,
            height = 1080,
            durationSeconds = 12,
            thumbnail = eu.tintera.plume.core.telegram.OutgoingThumbnail(copy("thumb.jpg"), 320, 180),
            streamable = false,
        )
        val file = OutgoingAttachment.Document(copy("smlouva.pdf"))
        val requests = process()
        val id = requests.start(text = null, hasFiles = true)
        requests.prepared(id, listOf(video, file), failed = 0)

        assertEquals(listOf(video, file), process().incoming.value?.attachments)
    }
}
