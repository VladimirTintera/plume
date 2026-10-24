package eu.tintera.plume.core.calls

// iOS hovory zatím nemá (bez mediální vrstvy se postranní kanál nikdy nepoužije).

internal actual fun sideChannelKey(callKey: ByteArray): ByteArray =
    throw UnsupportedOperationException("Hovory na iOS zatím nejsou.")

internal actual fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray =
    throw UnsupportedOperationException("Hovory na iOS zatím nejsou.")

internal actual fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray =
    throw UnsupportedOperationException("Hovory na iOS zatím nejsou.")
