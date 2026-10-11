package eu.tintera.plume.core.files

import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentKindTest {

    @Test
    fun extensionDecides() {
        assertEquals(DocumentKind.Image, documentKind("IMG_2024.JPG", "application/octet-stream"))
        assertEquals(DocumentKind.Image, documentKind("fotka.heic", ""))
        assertEquals(DocumentKind.Video, documentKind("klip.MOV", ""))
        assertEquals(DocumentKind.Video, documentKind("nahravka.mkv", "application/x-matroska"))
    }

    @Test
    fun mimeTypeIsFallbackWithoutExtension() {
        assertEquals(DocumentKind.Image, documentKind("screenshot", "image/png"))
        assertEquals(DocumentKind.Video, documentKind("video", "video/mp4; codecs=avc1"))
        assertEquals(DocumentKind.Other, documentKind("soubor", ""))
    }

    @Test
    fun unknownExtensionFallsBackToMime() {
        assertEquals(DocumentKind.Image, documentKind("export.bin", "image/jpeg"))
    }

    @Test
    fun unsupportedImageFormatsStayFiles() {
        // Formáty, které neumíme otevřít, zůstávají souborem, i když jsou to obrázky.
        assertEquals(DocumentKind.Other, documentKind("scan.tiff", "image/tiff"))
        assertEquals(DocumentKind.Other, documentKind("DSC0001.CR2", "image/x-canon-cr2"))
        assertEquals(DocumentKind.Other, documentKind("navrh.psd", "image/vnd.adobe.photoshop"))
    }

    @Test
    fun otherFilesStayFiles() {
        assertEquals(DocumentKind.Other, documentKind("smlouva.pdf", "application/pdf"))
        assertEquals(DocumentKind.Other, documentKind(".gitignore", "text/plain"))
    }

    @Test
    fun formatKeepsVariantsTogether() {
        assertEquals(DocumentFormat.Jpeg, documentFormat("a.jpeg", ""))
        assertEquals(DocumentFormat.Heic, documentFormat("a.HEIF", ""))
        assertEquals(DocumentFormat.QuickTime, documentFormat("klip", "video/quicktime"))
        assertEquals(null, documentFormat("smlouva.pdf", "application/pdf"))
    }

    @Test
    fun onlyImagesAndVideosOpenInViewer() {
        assertEquals(false, opensInViewer("smlouva.pdf", "application/pdf"))
        assertEquals(false, opensInViewer("scan.tiff", "image/tiff"))
        // JPEG otevře prohlížeč na každé platformě. MP4 ne: desktop přehrávač videa zatím nemá
        // a video předává aplikaci systému (viz `viewerUnsupportedFormats` v jvmMain).
        assertEquals(true, opensInViewer("fotka.jpg", ""))
    }
}
