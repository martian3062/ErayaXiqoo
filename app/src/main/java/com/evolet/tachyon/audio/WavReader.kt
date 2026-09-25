package com.evolet.tachyon.audio

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a PCM 16-bit WAV and returns 16 kHz mono samples. Stereo is averaged and
 * other sample rates are linearly resampled, so a phone recorder's 48 kHz file works as the backup input.
 */
object WavReader {
    fun toPcm16kMono(bytes: ByteArray): ShortArray {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 || tag(b, 0) != "RIFF" || tag(b, 8) != "WAVE") throw IOException("Not a WAV file")

        var channels = 0; var rate = 0; var bits = 0; var format = 0
        var dataOff = -1; var dataLen = 0
        var p = 12
        while (p + 8 <= bytes.size) {
            val id = tag(b, p)
            val len = b.getInt(p + 4)
            val body = p + 8
            when (id) {
                "fmt " -> { format = b.getShort(body).toInt(); channels = b.getShort(body + 2).toInt(); rate = b.getInt(body + 4); bits = b.getShort(body + 14).toInt() }
                "data" -> { dataOff = body; dataLen = minOf(len, bytes.size - body) }
            }
            if (dataOff >= 0 && rate > 0) break
            p = body + len + (len and 1)
        }
        if (dataOff < 0 || rate <= 0) throw IOException("WAV has no fmt/data chunk")
        if (format != 1 || bits != 16) throw IOException("WAV must be PCM 16-bit (got format=$format bits=$bits)")

        val frames = dataLen / (2 * channels)
        val mono = ShortArray(frames) { i ->
            var sum = 0
            for (c in 0 until channels) sum += b.getShort(dataOff + (i * channels + c) * 2)
            (sum / channels).toShort()
        }
        return if (rate == 16_000) mono else resample(mono, rate, 16_000)
    }

    private fun resample(src: ShortArray, from: Int, to: Int): ShortArray {
        val outLen = (src.size.toLong() * to / from).toInt()
        val ratio = from.toDouble() / to
        return ShortArray(outLen) { i ->
            val x = i * ratio
            val i0 = x.toInt().coerceAtMost(src.size - 1)
            val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            val f = x - i0
            (src[i0] * (1 - f) + src[i1] * f).toInt().toShort()
        }
    }

    private fun tag(b: ByteBuffer, at: Int) = String(ByteArray(4) { b.get(at + it) }, Charsets.US_ASCII)
}
