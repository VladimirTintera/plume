package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.DiceStickersRegular
import dev.g000sha256.tdl.dto.DiceStickersSlotMachine
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.MessageDice
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFormat
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.StickerFullTypeRegular
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiceMediaTest {

    private fun sticker(fileId: Int, format: StickerFormat = StickerFormatTgs()) = Sticker(
        id = fileId.toLong(),
        setId = 0,
        width = 512,
        height = 512,
        emoji = "🎲",
        format = format,
        fullType = StickerFullTypeRegular(premiumAnimation = null),
        thumbnail = null,
        sticker = File(
            id = fileId,
            size = 0,
            expectedSize = 0,
            local = LocalFile("", false, false, false, false, 0, 0, 0),
            remote = RemoteFile("", "", false, false, 0),
        ),
    )

    private fun dice(
        value: Int,
        initial: Int? = 1,
        final: Int? = 2,
        emoji: String = "🎲",
    ) = MessageDice(
        initialState = initial?.let { DiceStickersRegular(sticker(it)) },
        finalState = final?.let { DiceStickersRegular(sticker(it)) },
        emoji = emoji,
        value = value,
        successAnimationFrameNumber = 0,
    )

    @Test
    fun sVysledkemJeKoncovaAnimace() {
        val media = dice(value = 6).toDiceMedia()
        assertEquals(MessageMedia.Dice(emoji = "🎲", value = 6, layers = listOf(2)), media)
        assertEquals("🎲 6", media?.label)
    }

    @Test
    fun bezVysledkuSeTociUvodni() {
        val media = dice(value = 0).toDiceMedia()
        assertEquals(listOf(1), media?.layers)
        assertTrue(media!!.rolling)
        assertEquals("🎲", media.label)
    }

    @Test
    fun naDruhyStavSeNepada() {
        // Koncová animace bez výsledku by ukazovala neznámý výsledek; úvodní s výsledkem by se točila.
        assertNull(dice(value = 6, final = null).toDiceMedia())
        assertNull(dice(value = 0, initial = null).toDiceMedia())
    }

    @Test
    fun automatMaPetVrstevVPoradiOdPozadi() {
        val slot = MessageDice(
            initialState = null,
            finalState = DiceStickersSlotMachine(
                background = sticker(10),
                lever = sticker(11),
                leftReel = sticker(12),
                centerReel = sticker(13),
                rightReel = sticker(14),
            ),
            emoji = "🎰",
            value = 24,
            successAnimationFrameNumber = 0,
        )
        assertEquals(listOf(10, 11, 12, 13, 14), slot.toDiceMedia()?.layers)
    }

    @Test
    fun vrstvaMimoTgsZnamenaText() {
        val slot = MessageDice(
            initialState = null,
            finalState = DiceStickersSlotMachine(
                background = sticker(10),
                lever = sticker(11),
                leftReel = sticker(12, StickerFormatWebp()),
                centerReel = sticker(13),
                rightReel = sticker(14),
            ),
            emoji = "🎰",
            value = 24,
            successAnimationFrameNumber = 0,
        )
        // Chybějící válec by ukázal jiný výsledek, než jaký padl.
        assertNull(slot.toDiceMedia())
    }
}
