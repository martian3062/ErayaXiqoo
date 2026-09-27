package com.evolet.tachyon.audio

/** Receives microphone PCM from [RecorderService]. */
interface RecordingSink {
    fun onRecordingStarted(fromSample: Boolean = false)
    fun onChunk(pcm: ShortArray)
    fun onRecordingStopped()
    fun onRecorderError(message: String)
    fun onLevel(value: Float)
}

enum class RecordingTarget(val wireName: String) {
    COMMITMENTS("commitments"),
    CONVERSATION("conversation");

    companion object {
        fun fromWireName(value: String?): RecordingTarget =
            entries.firstOrNull { it.wireName == value } ?: COMMITMENTS
    }
}
