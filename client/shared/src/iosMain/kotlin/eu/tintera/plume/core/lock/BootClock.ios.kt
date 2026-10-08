package eu.tintera.plume.core.lock

import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

/** Na Darwinu `CLOCK_MONOTONIC` běží i ve spánku (na rozdíl od `CLOCK_UPTIME_RAW`). */
internal actual fun bootElapsedMillis(): Long = (clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()) / 1_000_000uL).toLong()
