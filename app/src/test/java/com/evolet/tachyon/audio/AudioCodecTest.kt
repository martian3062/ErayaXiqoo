package com.evolet.tachyon.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCodecTest {

    @Test fun `wav round trip at 16 kHz`() {
        val pcm = ShortArray(1600) { (it * 7 % 2000 - 1000).toShort() }
        assertArrayEquals(pcm, WavReader.toPcm16kMono(WavWriter.encode(pcm)))
    }

    @Test fun `chunker emits 30 s chunks and a tail`() {
        val chunks = mutableListOf<Int>()
        val c = PcmChunker(sampleRate = 100, chunkSeconds = 30) { chunks += it.size }
        c.push(ShortArray(7_000))    // 70 s
        c.flush()
        assertEquals(listOf(3_000, 3_000, 1_000), chunks)
    }

    @Test fun `chunker drops a sub-second tail`() {
        val chunks = mutableListOf<Int>()
        val c = PcmChunker(sampleRate = 100, chunkSeconds = 30) { chunks += it.size }
        c.push(ShortArray(3_050))
        c.flush()
        assertEquals(listOf(3_000), chunks)
    }

    @Test fun `default chunker emits five second windows for live transcription`() {
        val chunks = mutableListOf<Int>()
        val c = PcmChunker(sampleRate = 100) { chunks += it.size }
        c.push(ShortArray(1_250))
        c.flush()
        assertEquals(listOf(500, 500, 250), chunks)
    }
}
