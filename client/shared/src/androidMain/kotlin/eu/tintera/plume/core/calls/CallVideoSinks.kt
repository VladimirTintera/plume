package eu.tintera.plume.core.calls

import org.webrtc.EglBase
import org.webrtc.VideoSink

/**
 * Kam mediální vrstva posílá snímky hovoru: náhled naší kamery a obraz protistrany. Renderer
 * (`CallVideo`) se přihlásí, dokud je vidět; bez přihlášeného se snímky zahazují.
 */
interface CallVideoSinks {
    /** Sdílený kontext EGL knihovny; renderer v něm kreslí. */
    val eglContext: EglBase.Context

    fun attach(source: CallVideoSource, sink: VideoSink)

    fun detach(source: CallVideoSource, sink: VideoSink)
}
