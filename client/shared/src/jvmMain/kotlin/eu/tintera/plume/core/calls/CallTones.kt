package eu.tintera.plume.core.calls

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tóny hovoru na desktopu, spočítané v kódu (16 bitů, mono, [SAMPLE_RATE]). Desktop nemá
 * systémový vyzváněcí tón, který by šel přehrát, a nahrávka by potřebovala licenci; pár
 * sinusovek ji nahradí.
 *
 * Obojí je jedna perioda smyčky — přehrávač ji opakuje, dokud hovor zvoní.
 */
internal object CallTones {

    const val SAMPLE_RATE = 44_100

    /**
     * Vyzvánění příchozího hovoru: dvakrát stoupající trojzvuk (C6, E6, G6) jako úder
     * na zvonkohru, pak ticho do tří vteřin.
     */
    fun ringtone(): ShortArray {
        val out = FloatArray(seconds(RING_PERIOD_S))
        for (start in floatArrayOf(0f, 0.75f)) {
            CHIME_NOTES.forEachIndexed { index, frequency ->
                chime(out, at = start + index * CHIME_STEP_S, frequency = frequency)
            }
        }
        return out.toPcm(RING_LEVEL)
    }

    /**
     * Tón protistrany („tůů") u odchozího hovoru: 425 Hz vteřinu, čtyři vteřiny ticho — evropská
     * norma, kterou zná i Android (`TONE_SUP_RINGTONE`).
     */
    fun ringback(): ShortArray {
        val out = FloatArray(seconds(RINGBACK_PERIOD_S))
        val length = seconds(RINGBACK_ON_S)
        val fade = seconds(FADE_S)
        for (i in 0 until length) {
            val envelope = min(1f, min(i, length - 1 - i).toFloat() / fade)
            out[i] = envelope * sin(2 * PI * RINGBACK_HZ * i / SAMPLE_RATE).toFloat()
        }
        return out.toPcm(RINGBACK_LEVEL)
    }

    /** Úder: rychlý náběh (bez lupnutí) a doznívání, s tichou oktávou navrch pro barvu. */
    private fun chime(out: FloatArray, at: Float, frequency: Float) {
        val first = seconds(at)
        val attack = seconds(FADE_S)
        for (i in 0 until min(seconds(CHIME_LENGTH_S), out.size - first)) {
            val t = i.toFloat() / SAMPLE_RATE
            val envelope = min(1f, i.toFloat() / attack) * exp(-t / CHIME_DECAY_S)
            val tone = sin(2 * PI * frequency * t) + OVERTONE * sin(2 * PI * 2 * frequency * t)
            out[first + i] += envelope * tone.toFloat()
        }
    }

    /** Normalizace na [level] plného rozsahu: součet překrývajících se úderů by jinak přetekl. */
    private fun FloatArray.toPcm(level: Float): ShortArray {
        val peak = maxOf { abs(it) }.takeIf { it > 0f } ?: 1f
        return ShortArray(size) { (this[it] / peak * level * Short.MAX_VALUE).roundToInt().toShort() }
    }

    private fun seconds(value: Float): Int = (value * SAMPLE_RATE).roundToInt()

    private val CHIME_NOTES = floatArrayOf(1046.50f, 1318.51f, 1567.98f)
    private const val CHIME_STEP_S = 0.12f
    private const val CHIME_LENGTH_S = 0.9f
    private const val CHIME_DECAY_S = 0.22f
    private const val OVERTONE = 0.25
    private const val RING_PERIOD_S = 3f
    private const val RING_LEVEL = 0.5f

    private const val RINGBACK_HZ = 425.0
    private const val RINGBACK_ON_S = 1f
    private const val RINGBACK_PERIOD_S = 5f
    private const val RINGBACK_LEVEL = 0.25f

    /** Náběh a doběh tónu; skok z ticha do plné vlny je slyšet jako lupnutí. */
    private const val FADE_S = 0.01f
}
