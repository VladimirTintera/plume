package eu.tintera.plume.core.lock

import android.os.SystemClock

internal actual fun bootElapsedMillis(): Long = SystemClock.elapsedRealtime()
