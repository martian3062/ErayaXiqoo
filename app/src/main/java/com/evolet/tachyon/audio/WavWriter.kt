package com.evolet.tachyon.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM 16-bit mono → in-memory WAV (for whisper-server uploads). */
object WavWriter {
    fun encode(pcm: ShortArray, sampleRate: Int = 16_000): ByteArray {
        val dataLen = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataLen)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        buf.putShort(1)                 // PCM
        buf.putShort(1)                 // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)      // byte rate
        buf.putShort(2)                 // block align
        buf.putShort(16)                // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataLen)
        for (s in pcm) buf.putShort(s)
        return buf.array()
    }
}
