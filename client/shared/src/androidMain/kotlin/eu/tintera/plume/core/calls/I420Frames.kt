package eu.tintera.plume.core.calls

import io.github.pytgcalls.VideoRotation
import io.github.pytgcalls.models.Frame
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Snímky I420 z ntgcalls (`onFrames`, těsně za sebou Y, U, V) do rendereru. Sdílí je hovor ve
 * dvou ([NtgCallMedia]) i živý příběh ([NtgLiveStreamMedia]).
 *
 * Přímé buffery se recyklují po zdroji: snímek 720p má 1,4 MB a nový buffer pro každý
 * (30 za vteřinu) by uvolnil až GC; buffer se vrací, až ho renderer pustí.
 */
internal class I420Frames {
    private val pools = ConcurrentHashMap<CallVideoSource, ConcurrentLinkedQueue<ByteBuffer>>()

    /** Volá se z vlákna knihovny; renderer si snímek kreslí na vlastním vlákně EGL. */
    fun render(source: CallVideoSource, sink: VideoSink, frame: Frame) {
        val info = frame.frame_data ?: return
        val width = info.width
        val height = info.height
        if (width <= 0 || height <= 0) return
        val ySize = width * height
        val chromaWidth = (width + 1) / 2
        val chromaSize = chromaWidth * ((height + 1) / 2)
        val size = ySize + 2 * chromaSize
        if (frame.data.size < size) return
        val pool = pools.getOrPut(source) { ConcurrentLinkedQueue() }
        val buffer = pool.poll()?.takeIf { it.capacity() >= size }?.apply { clear() } ?: ByteBuffer.allocateDirect(size)
        buffer.put(frame.data, 0, size)
        fun plane(offset: Int, length: Int) = buffer.duplicate().apply { position(offset); limit(offset + length) }.slice()
        val i420 = JavaI420Buffer.wrap(
            width, height,
            plane(0, ySize), width,
            plane(ySize, chromaSize), chromaWidth,
            plane(ySize + chromaSize, chromaSize), chromaWidth,
        ) { if (pool.size < POOL_DEPTH) pool.offer(buffer) }
        val video = VideoFrame(i420, info.rotation.degrees, info.absolute_capture_timestamp_ms * 1_000_000L)
        runCatching { sink.onFrame(video) }
        video.release()
    }

    fun forget(source: CallVideoSource) {
        pools.remove(source)
    }

    fun clear() = pools.clear()

    private companion object {
        /** Renderer drží nejvýš snímek, dva; víc bufferů je rezerva. */
        const val POOL_DEPTH = 4

        val VideoRotation.degrees: Int
            get() = when (this) {
                VideoRotation.VIDEO_ROTATION_0 -> 0
                VideoRotation.VIDEO_ROTATION_90 -> 90
                VideoRotation.VIDEO_ROTATION_180 -> 180
                VideoRotation.VIDEO_ROTATION_270 -> 270
            }
    }
}
