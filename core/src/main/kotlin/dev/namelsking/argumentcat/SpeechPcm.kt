package dev.namelsking.argumentcat

import kotlin.math.abs
import kotlin.math.roundToInt

object SpeechPcm {
    // Preserve the waveform while giving quiet synthesis a predictable playback level.
    fun normalized(samples: List<Double>): ShortArray {
        require(samples.isNotEmpty()) { "No speech returned" }
        require(samples.all { it.isFinite() }) { "Invalid speech samples" }
        val peak = samples.maxOf { abs(it) }
        require(peak > 0) { "The voice returned silence. Try Hear reply again." }
        val gain = (Short.MAX_VALUE * 0.85) / peak
        return ShortArray(samples.size) { (samples[it] * gain).roundToInt().coerceIn(-32767, 32767).toShort() }
    }
}
