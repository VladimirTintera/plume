package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.ChatExportKind
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationImportTest {

    private class FakeRepository(
        var kind: ChatExportKind? = ChatExportKind.Private("Jana"),
        var confirmation: Result<String> = Result.success("Zprávy se naimportují."),
    ) : UnimplementedConversationRepository() {
        val heads = mutableListOf<String>()
        val imported = mutableListOf<Pair<String, List<String>>>()

        override suspend fun chatExportKind(head: String): Result<ChatExportKind?> {
            heads += head
            return Result.success(kind)
        }

        override suspend fun importConfirmation(chatId: Long, kind: ChatExportKind): Result<String> = confirmation

        override suspend fun importMessages(chatId: Long, messageFile: String, attachedFiles: List<String>): Result<Unit> {
            imported += messageFile to attachedFiles
            return Result.success(Unit)
        }
    }

    private val temp = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-import-test-${kotlin.random.Random.nextLong()}"
    private val repository = FakeRepository()
    private val shares = ShareRequests()

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(temp)

    private fun file(name: String, text: String = ""): Path {
        FileSystem.SYSTEM.createDirectories(temp)
        val path = temp / name
        FileSystem.SYSTEM.write(path) { writeUtf8(text) }
        return path
    }

    /** Sdílení se soubory [paths] vybrané do testovacího chatu. */
    private fun share(vararg paths: Path) = shares.run {
        val id = start(text = null, hasFiles = true)
        prepared(id, attachments = paths.map { OutgoingAttachment.Document(it.toString()) }, failed = 0)
        choose(TEST_CHAT)
        incoming.value!!
    }

    private val attached = mutableListOf<String>()

    private fun TestScope.import(state: kotlinx.coroutines.flow.MutableStateFlow<ConversationState>) =
        ConversationImport(TEST_CHAT, state, this, repository, shares, testLogger) { files -> attached += files.map { it.path } }

    @Test
    fun exportSeNabidneKImportuSPrilohami() = runTest {
        val txt = file("WhatsApp Chat with Jana.txt", "12/31/24, 9:41 PM - Jana: Ahoj\n")
        val photo = file("IMG-20241231-WA0001.jpg")
        val state = testState()

        assertTrue(import(state).offer(share(txt, photo)))

        val offer = assertNotNull(state.value.chatImport)
        assertEquals(txt.toString(), offer.messageFile)
        assertEquals(listOf(photo.toString()), offer.attachedFiles)
        assertEquals("12/31/24, 9:41 PM - Jana: Ahoj\n", repository.heads.single())
        // Sdílení si import vyzvedl — nad pole nepůjde.
        assertNull(shares.incoming.value)
    }

    @Test
    fun obycejnySouborJdeJakoPriloha() = runTest {
        repository.kind = null
        val state = testState()

        assertFalse(import(state).offer(share(file("poznamky.txt", "nakup"))))
        assertNull(state.value.chatImport)
        assertNotNull(shares.incoming.value)
    }

    @Test
    fun bezTxtSeNeptaNikdo() = runTest {
        val state = testState()
        assertFalse(import(state).offer(share(file("foto.jpg"))))
        assertEquals(emptyList(), repository.heads)
    }

    @Test
    fun potvrzeniNaimportuje() = runTest {
        val txt = file("chat.txt", "x\n")
        val state = testState()
        val import = import(state)
        import.offer(share(txt))

        import.confirm()
        advanceUntilIdle()

        assertEquals(listOf(txt.toString() to emptyList<String>()), repository.imported)
        assertNull(state.value.chatImport)
        assertNotNull(state.value.notice)
    }

    @Test
    fun doChatuBezImportuNejdePotvrdit() = runTest {
        repository.confirmation = Result.failure(IllegalStateException("CHAT_ADMIN_REQUIRED"))
        val state = testState()
        val import = import(state)
        import.offer(share(file("chat.txt", "x\n")))
        assertNull(state.value.chatImport?.confirmation)

        import.confirm()
        advanceUntilIdle()

        assertEquals(emptyList(), repository.imported)
    }

    @Test
    fun kdyzImportNejdeExportSeVloziJakoSoubor() = runTest {
        repository.confirmation = Result.failure(IllegalStateException("CHAT_ADMIN_REQUIRED"))
        val txt = file("chat.txt", "x\n")
        val state = testState()
        val import = import(state)
        import.offer(share(txt))

        import.attachInstead()

        assertEquals(listOf(txt.toString()), attached)
        assertNull(state.value.chatImport)
    }
}
