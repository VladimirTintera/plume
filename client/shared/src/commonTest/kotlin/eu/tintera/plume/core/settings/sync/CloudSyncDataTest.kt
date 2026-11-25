package eu.tintera.plume.core.settings.sync

import eu.tintera.plume.core.video.VideoPosition
import eu.tintera.plume.core.video.VideoSource
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudSyncDataTest {

    private val keepRemote = { _: String, remote: String -> remote }

    @Test
    fun oneSidedChangeWins() {
        val base = mapOf("a" to "1")
        assertEquals(mapOf("a" to "2"), merge3(base, local = mapOf("a" to "2"), remote = base, keepRemote))
        assertEquals(mapOf("a" to "3"), merge3(base, local = base, remote = mapOf("a" to "3"), keepRemote))
    }

    @Test
    fun deletionPropagatesUnlessTheOtherSideChanged() {
        val base = mapOf("a" to "1", "b" to "1")
        val merged = merge3(base, local = mapOf("b" to "1"), remote = mapOf("a" to "1", "b" to "2"), keepRemote)
        // „a" smazané tady, tam beze změny → pryč; „b" tady beze změny, tam změněné → změna.
        assertEquals(mapOf("b" to "2"), merged)
        // Smazané tady, tam mezitím změněné → změna vyhrává.
        assertEquals(mapOf("a" to "5"), merge3(base, local = emptyMap(), remote = mapOf("a" to "5"), keepRemote))
    }

    @Test
    fun withoutBaseBothSidesAreKept() {
        val merged = merge3(emptyMap(), local = mapOf("a" to "1"), remote = mapOf("b" to "2"), keepRemote)
        assertEquals(mapOf("a" to "1", "b" to "2"), merged)
    }

    @Test
    fun conflictGoesToResolver() {
        val merged = merge3(mapOf("a" to "1"), local = mapOf("a" to "2"), remote = mapOf("a" to "3")) { local, _ -> local }
        assertEquals(mapOf("a" to "2"), merged)
    }

    @Test
    fun newerPositionWinsConflict() {
        val older = positionJson(VideoPosition("k", 10_000, HOUR, at = 100))
        val newer = positionJson(VideoPosition("k", 20_000, HOUR, at = 200))
        assertEquals(newer, resolvePosition(older, newer))
        assertEquals(newer, resolvePosition(newer, older))
    }

    @Test
    fun chunksNeverSplitASurrogatePair() {
        val text = "a".repeat(9) + "😀" + "b".repeat(5)
        val parts = chunkForCloud(text, limit = 10)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.none { it.last().isHighSurrogate() })
        assertEquals(listOf(""), chunkForCloud(""))
    }

    @Test
    fun entriesRoundTrip() {
        val entries = mapOf("theme_mode" to "\"Dark\"", "message_text_size" to "18")
        assertEquals(entries, decodeEntries(encodeEntries(entries)))
        assertNull(decodeEntries("nonsense"))
    }

    @Test
    fun preferenceChangesOnlyListDifferences() {
        val names = setOf("theme_mode", "message_text_size", "sticker_size")
        val current = mapOf("theme_mode" to JsonPrimitive("Light"), "sticker_size" to JsonPrimitive(120))
        val entries = mapOf("theme_mode" to "\"Dark\"", "sticker_size" to "120", "message_text_size" to "18")
        assertEquals(
            mapOf("theme_mode" to JsonPrimitive("Dark"), "message_text_size" to JsonPrimitive(18)),
            preferenceChanges(entries, current, names),
        )
        // Klíč, který v položkách chybí, se smaže (výchozí hodnota).
        assertEquals(mapOf("theme_mode" to null), preferenceChanges(emptyMap(), mapOf("theme_mode" to JsonPrimitive("Dark")), setOf("theme_mode")))
    }

    @Test
    fun onlyThisAccountsPositionsAreSynced() {
        val mine = VideoPosition("a", 20_000, HOUR, VideoSource(1, 2, 3), at = 10)
        val other = VideoPosition("b", 20_000, HOUR, VideoSource(9, 2, 3), at = 20)
        val audio = VideoPosition("c", 20_000, HOUR, source = null, at = 30)
        assertEquals(setOf("a"), positionEntries(listOf(mine, other, audio), accountId = 1).keys)
    }

    @Test
    fun positionWrittenDuringSyncIsNotOverwritten() {
        val synced = VideoPosition("a", 20_000, HOUR, VideoSource(1, 2, 3), at = 10)
        val snapshot = positionEntries(listOf(synced), accountId = 1)
        // Mezitím přehrávač zapsal novější pozici; sloučení přineslo starší z úložiště.
        val playing = synced.copy(positionMs = 40_000, at = 50)
        val fromCloud = mapOf("a" to positionJson(synced.copy(positionMs = 30_000, at = 20)))
        val result = positionsWith(fromCloud, listOf(playing), accountId = 1, snapshot = snapshot)
        assertEquals(40_000, result.single().positionMs)
    }

    @Test
    fun otherAccountsPositionsStay() {
        val other = VideoPosition("b", 20_000, HOUR, VideoSource(9, 2, 3), at = 20)
        val fromCloud = mapOf("a" to positionJson(VideoPosition("a", 20_000, HOUR, VideoSource(1, 2, 3), at = 30)))
        val result = positionsWith(fromCloud, listOf(other), accountId = 1)
        assertEquals(listOf("a", "b"), result.map { it.key })
    }

    private fun positionJson(position: VideoPosition) = positionEntries(listOf(position.copy(source = position.source ?: VideoSource(1, 1, 1))), 1).values.single()

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
