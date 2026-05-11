package com.patrick.lrcreader.core.audio

data class SampleSegment(
    val id: String,
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val sampleRateHz: Int,
    val pcm16Stereo: ByteArray
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    val estimatedRamBytes: Int
        get() = pcm16Stereo.size

    init {
        require(id.isNotBlank()) { "SampleSegment id must not be blank" }
        require(name.isNotBlank()) { "SampleSegment name must not be blank" }
        require(startMs >= 0L) { "SampleSegment startMs must be >= 0" }
        require(endMs > startMs) { "SampleSegment endMs must be > startMs" }
        require(sampleRateHz > 0) { "SampleSegment sampleRateHz must be > 0" }
        require(pcm16Stereo.isNotEmpty()) { "SampleSegment PCM buffer must not be empty" }
        require(pcm16Stereo.size % BYTES_PER_STEREO_FRAME == 0) {
            "SampleSegment PCM buffer must contain complete 16-bit stereo frames"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SampleSegment

        if (id != other.id) return false
        if (name != other.name) return false
        if (startMs != other.startMs) return false
        if (endMs != other.endMs) return false
        if (sampleRateHz != other.sampleRateHz) return false
        if (!pcm16Stereo.contentEquals(other.pcm16Stereo)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + startMs.hashCode()
        result = 31 * result + endMs.hashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + pcm16Stereo.contentHashCode()
        return result
    }

    companion object {
        const val CHANNEL_COUNT: Int = 2
        const val BYTES_PER_SAMPLE: Int = 2
        const val BYTES_PER_STEREO_FRAME: Int = CHANNEL_COUNT * BYTES_PER_SAMPLE
    }
}
