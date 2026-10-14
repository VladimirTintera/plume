package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.OutgoingAttachment
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Jak se přetažené nebo zkopírované soubory na desktopu rozdělí ([prepareFiles]): fotky jen
 * tehdy, když jsou fotkami všechny, jinak všechno jako soubory — po jednom by se navzájem
 * nahradily (`stageAttachments` druhy nemíchá).
 */
class DroppedFilesTest {

    private val directory: File = Files.createTempDirectory("plume-drop").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun image(name: String, format: String, width: Int = 40, height: Int = 30): File =
        File(directory, name).also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, it) }

    private fun text(name: String): File = File(directory, name).apply { writeText("plume") }

    @Test
    fun onlyPhotosGoAsPhotos() {
        val result = prepareFiles(
            listOf(image("a.png", "png"), image("b.jpg", "jpg", width = 20, height = 50)),
            acceptAnimations = true,
            acceptImages = true,
        )

        val attachments = assertIs<KeyboardContent.Attachments>(result).attachments
        assertEquals(listOf(40 to 30, 20 to 50), attachments.map { (it as OutgoingAttachment.Photo).let { p -> p.width to p.height } })
    }

    @Test
    fun photoMixedWithFileMakesEverythingFiles() {
        val result = prepareFiles(listOf(image("a.png", "png"), text("notes.txt")), acceptAnimations = true, acceptImages = true)

        val attachments = assertIs<KeyboardContent.Attachments>(result).attachments
        assertEquals(2, attachments.size)
        attachments.forEach { assertIs<OutgoingAttachment.Document>(it) }
    }

    @Test
    fun photosWithoutPhotoRightsGoAsFiles() {
        val result = prepareFiles(listOf(image("a.png", "png")), acceptAnimations = true, acceptImages = false)

        assertIs<OutgoingAttachment.Document>(assertIs<KeyboardContent.Attachments>(result).attachments.single())
    }

    @Test
    fun singleGifIsAnimation() {
        val result = prepareFiles(listOf(image("a.gif", "gif", width = 12, height = 8)), acceptAnimations = true, acceptImages = true)

        val animation = assertIs<KeyboardContent.Animation>(result).animation
        assertEquals(12 to 8, animation.width to animation.height)
    }

    @Test
    fun gifWithoutAnimationRightsOrAmongOthersIsFile() {
        val gif = image("a.gif", "gif")

        val alone = prepareFiles(listOf(gif), acceptAnimations = false, acceptImages = true)
        assertIs<OutgoingAttachment.Document>(assertIs<KeyboardContent.Attachments>(alone).attachments.single())

        val withPhoto = prepareFiles(listOf(gif, image("b.png", "png")), acceptAnimations = true, acceptImages = true)
        assertIs<KeyboardContent.Attachments>(withPhoto).attachments.forEach { assertIs<OutgoingAttachment.Document>(it) }
    }

    @Test
    fun directoriesAreSkipped() {
        val folder = File(directory, "folder").apply { mkdirs() }

        val result = prepareFiles(listOf(folder, image("a.png", "png")), acceptAnimations = true, acceptImages = true)
        assertIs<OutgoingAttachment.Photo>(assertIs<KeyboardContent.Attachments>(result).attachments.single())

        assertFailsWith<IllegalArgumentException> { prepareFiles(listOf(folder), acceptAnimations = true, acceptImages = true) }
    }
}
