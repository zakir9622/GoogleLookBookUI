package com.zakir.vestra.shared.time

/**
 * Epoch-millis clock abstraction for commonMain (generation-stability finding M).
 * Default uses wall clock; tests may replace [System].
 */
fun interface EpochClock {
    fun nowMs(): Long

    companion object {
        var System: EpochClock = EpochClock { java.lang.System.currentTimeMillis() }
    }
}
