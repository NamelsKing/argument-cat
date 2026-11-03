package dev.namelsking.argumentcat

import org.junit.Test
import kotlin.test.*

class SpeechPcmTest {
    @Test fun `quiet speech becomes audible without losing its shape`() {
        val pcm = SpeechPcm.normalized(listOf(0.0, 10.0, -20.0, 5.0))
        assertEquals(0, pcm[0].toInt())
        assertEquals(27852, -pcm[2].toInt())
        assertEquals(pcm[1].toInt(), -pcm[2].toInt() / 2)
    }
    @Test fun `large peaks retain headroom instead of clipping`() {
        val pcm = SpeechPcm.normalized(listOf(-32768.0, 32767.0))
        assertTrue(pcm.all { it.toInt() in -27853..27853 })
        assertTrue(pcm.first() < 0 && pcm.last() > 0)
    }
    @Test fun `silence and invalid audio are rejected`() {
        assertFails { SpeechPcm.normalized(emptyList()) }
        assertFails { SpeechPcm.normalized(listOf(0.0, 0.0)) }
        assertFails { SpeechPcm.normalized(listOf(Double.NaN)) }
    }
}
