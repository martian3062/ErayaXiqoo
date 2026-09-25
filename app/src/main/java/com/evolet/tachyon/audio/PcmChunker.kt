package com.evolet.tachyon.audio

/** Whisper takes at most 30 s per window: buffer PCM and emit fixed 30 s chunks. */
class PcmChunker(
    private val sampleRate: Int = 16_000,
    chunkSeconds: Int = 30,
    private val onChunk: (ShortArray) -> Unit,
) {
    private val chunk = ShortArray(sampleRate * chunkSeconds)
    private var filled = 0

    fun push(data: ShortArray, count: Int = data.size) {
        var off = 0
        while (off < count) {
            val n = minOf(count - off, chunk.size - filled)
            System.arraycopy(data, off, chunk, filled, n)
            filled += n
            off += n
            if (filled == chunk.size) {
                onChunk(chunk.copyOf())
                filled = 0
            }
        }
    }

    /** Emits the tail, unless it's shorter than [minSeconds] (whisper hallucinates on near-silence). */
    fun flush(minSeconds: Double = 1.0) {
        if (filled >= sampleRate * minSeconds) onChunk(chunk.copyOf(filled))
        filled = 0
    }
}
