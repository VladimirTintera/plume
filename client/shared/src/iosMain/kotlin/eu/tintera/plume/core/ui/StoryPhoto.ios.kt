package eu.tintera.plume.core.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill

class IosStoryPhoto : StoryPhoto {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun render(path: String, fill: Boolean): String = withContext(Dispatchers.Default) {
        val image = UIImage.imageWithContentsOfFile(path) ?: error("Obrázek nejde dekódovat.")
        val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
        val place = storyPhotoPlacement(width.toInt(), height.toInt(), fill)
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = true
        }
        val size = CGSizeMake(STORY_PHOTO_WIDTH.toDouble(), STORY_PHOTO_HEIGHT.toDouble())
        val renderer = UIGraphicsImageRenderer(size = size, format = format)
        val story = renderer.imageWithActions { _ ->
            UIColor.blackColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, STORY_PHOTO_WIDTH.toDouble(), STORY_PHOTO_HEIGHT.toDouble()))
            image.drawInRect(
                CGRectMake(place.left.toDouble(), place.top.toDouble(), place.width.toDouble(), place.height.toDouble()),
            )
        }
        val jpeg = UIImageJPEGRepresentation(story, PHOTO_JPEG_QUALITY / 100.0) ?: error("JPEG nejde vytvořit.")
        val output = outgoingPath("story.jpg")
        check(jpeg.writeToFile(output, atomically = true)) { "Fotku nejde uložit." }
        output
    }
}
