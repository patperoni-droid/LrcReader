package com.patrick.lrcreader.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.audio.ArrangementSourceWavCache
import com.patrick.lrcreader.core.audio.SampleSegment
import com.patrick.lrcreader.core.audio.SamplerEngine
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.ArrangementSegmentData
import com.patrick.lrcreader.smp.ArrangementStore
import com.patrick.lrcreader.smp.SmpLibraryScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

@Composable
fun ArrangementSamplerTestScreen(
    songId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val samplerEngine = remember { SamplerEngine() }
    val crossfadeOptionsMs = remember { listOf(0, 8, 20, 50, 100) }
    val antiClickFadeOptionsMs = remember { listOf(0, 1, 2, 3, 5) }
    var selectedCrossfadeMs by remember { mutableStateOf(0) }
    var selectedAntiClickFadeMs by remember { mutableStateOf(0) }
    var structureSegments by remember(songId) { mutableStateOf<List<ArrangementSegmentData>>(emptyList()) }
    var isLoadingSamples by remember(songId) { mutableStateOf(false) }
    var loadedSamplesCount by remember(songId) { mutableStateOf(0) }
    var statusMessage by remember(songId) { mutableStateOf<String?>(null) }
    val noSongMessage = stringResource(R.string.arrangement_sampler_test_no_song)
    val noStructureMessage = stringResource(R.string.arrangement_sampler_test_empty_structure)
    val loadOkFormat = stringResource(R.string.arrangement_sampler_test_loaded_status)
    val loadErrorMessage = stringResource(R.string.arrangement_sampler_test_error_status)

    DisposableEffect(samplerEngine) {
        onDispose { samplerEngine.release() }
    }

    LaunchedEffect(selectedCrossfadeMs) {
        samplerEngine.setCrossfadeDurationMs(selectedCrossfadeMs)
    }

    LaunchedEffect(selectedAntiClickFadeMs) {
        samplerEngine.setAntiClickFadeDurationMs(selectedAntiClickFadeMs)
    }

    LaunchedEffect(songId) {
        samplerEngine.stop()
        loadedSamplesCount = 0
        val cleanSongId = songId?.trim().orEmpty()
        if (cleanSongId.isEmpty()) {
            structureSegments = emptyList()
            statusMessage = noSongMessage
            return@LaunchedEffect
        }
        val arrangementData = withContext(Dispatchers.IO) {
            ArrangementStore.load(context.applicationContext, cleanSongId)
        }
        val arrangementSegments = arrangementData?.segments.orEmpty()
        val byId = arrangementSegments.associateBy { it.id }
        structureSegments = arrangementData
            ?.structureSegmentIds
            .orEmpty()
            .mapNotNull { segmentId -> byId[segmentId] }
        statusMessage = if (structureSegments.isEmpty()) noStructureMessage else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090909))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back_arrow),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.arrangement_sampler_test_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !isLoadingSamples && !songId.isNullOrBlank(),
                onClick = {
                    val cleanSongId = songId?.trim().orEmpty()
                    if (cleanSongId.isEmpty()) {
                        statusMessage = noSongMessage
                        return@Button
                    }
                    isLoadingSamples = true
                    statusMessage = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val song = SmpLibraryScanner(context.applicationContext)
                                    .findSongById(cleanSongId)
                                    ?: error("song_not_found")
                                val audioPath = song.audioPath?.takeIf { it.isNotBlank() }
                                    ?: error("audio_missing")
                                val wavFile = ArrangementSourceWavCache.ensureSourceWav(
                                    context = context.applicationContext,
                                    songId = cleanSongId,
                                    sourceUri = Uri.fromFile(File(audioPath))
                                )
                                buildSampleSegmentsFromWav(
                                    wavFile = wavFile,
                                    structureSegments = structureSegments
                                )
                            }
                        }
                        result
                            .onSuccess { sampleSegments ->
                                samplerEngine.loadSegments(sampleSegments)
                                loadedSamplesCount = sampleSegments.size
                                statusMessage = loadOkFormat.format(sampleSegments.size)
                            }
                            .onFailure { error ->
                                Log.w(SAMPLER_TEST_TAG, "LOAD_SAMPLES_FAIL message=${error.message}", error)
                                loadedSamplesCount = 0
                                statusMessage = loadErrorMessage
                            }
                        isLoadingSamples = false
                    }
                }
            ) {
                Text(
                    text = if (isLoadingSamples) {
                        stringResource(R.string.arrangement_sampler_test_loading)
                    } else {
                        stringResource(R.string.arrangement_sampler_test_load_samples)
                    }
                )
            }
            OutlinedButton(
                onClick = {
                    samplerEngine.stop()
                    statusMessage = null
                }
            ) {
                Text(stringResource(R.string.arrangement_sampler_test_stop))
            }
            OutlinedButton(
                onClick = {
                    samplerEngine.release()
                    loadedSamplesCount = 0
                    statusMessage = null
                }
            ) {
                Text(stringResource(R.string.arrangement_sampler_test_release))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            crossfadeOptionsMs.forEach { optionMs ->
                val selected = optionMs == selectedCrossfadeMs
                Text(
                    text = "${optionMs}ms",
                    color = if (selected) Color(0xFF80CBC4) else Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable {
                        selectedCrossfadeMs = optionMs
                        samplerEngine.setCrossfadeDurationMs(optionMs)
                    }
                )
                if (optionMs != crossfadeOptionsMs.last()) {
                    Text(
                        text = "·",
                        color = Color(0xFF607D8B),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Anti-clic:",
                color = Color(0xFF90A4AE),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            antiClickFadeOptionsMs.forEach { optionMs ->
                val selected = optionMs == selectedAntiClickFadeMs
                Text(
                    text = "${optionMs}ms",
                    color = if (selected) Color(0xFF80CBC4) else Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable {
                        selectedAntiClickFadeMs = optionMs
                        samplerEngine.setAntiClickFadeDurationMs(optionMs)
                    }
                )
                if (optionMs != antiClickFadeOptionsMs.last()) {
                    Text(
                        text = "·",
                        color = Color(0xFF607D8B),
                        fontSize = 13.sp
                    )
                }
            }
        }

        statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
        }

        structureSegments.forEachIndexed { index, segment ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${index + 1}. ${segment.name}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.arrangement_sampler_test_segment_range,
                        segment.startMs,
                        segment.endMs
                    ),
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = loadedSamplesCount == structureSegments.size,
                        onClick = { samplerEngine.play(index) }
                    ) {
                        Text(stringResource(R.string.arrangement_sampler_test_play))
                    }
                    OutlinedButton(
                        enabled = loadedSamplesCount == structureSegments.size,
                        onClick = { samplerEngine.queueNext(index) }
                    ) {
                        Text(stringResource(R.string.arrangement_sampler_test_queue_next))
                    }
                }
            }
        }
    }
}

private fun buildSampleSegmentsFromWav(
    wavFile: File,
    structureSegments: List<ArrangementSegmentData>
): List<SampleSegment> {
    require(structureSegments.isNotEmpty()) { "empty_structure" }
    val wavInfo = readSamplerTestWavInfo(wavFile)
    require(wavInfo.bitsPerSample == 16) { "unsupported_bits_${wavInfo.bitsPerSample}" }
    require(wavInfo.channelCount == SampleSegment.CHANNEL_COUNT) {
        "unsupported_channels_${wavInfo.channelCount}"
    }

    val bytesPerFrame = wavInfo.channelCount * (wavInfo.bitsPerSample / 8)
    RandomAccessFile(wavFile, "r").use { input ->
        return structureSegments.mapIndexed { index, segment ->
            val startMs = minOf(segment.startMs, segment.endMs).coerceAtLeast(0L)
            val endMs = maxOf(segment.startMs, segment.endMs).coerceAtLeast(startMs + 1L)
            val startFrame = (startMs * wavInfo.sampleRate.toLong()) / 1_000L
            val endFrame = (endMs * wavInfo.sampleRate.toLong()) / 1_000L
            val startByteOffset = wavInfo.dataOffset + startFrame * bytesPerFrame.toLong()
            val endByteOffset = wavInfo.dataOffset + endFrame * bytesPerFrame.toLong()
            val clampedStart = startByteOffset.coerceIn(wavInfo.dataOffset, wavInfo.dataEndOffset)
            val clampedEnd = endByteOffset.coerceIn(clampedStart, wavInfo.dataEndOffset)
            val byteCount = (clampedEnd - clampedStart).coerceAtLeast(0L)
            require(byteCount > 0L && byteCount <= Int.MAX_VALUE) { "invalid_segment_$index" }

            val pcm = ByteArray(byteCount.toInt())
            input.seek(clampedStart)
            input.readFully(pcm)
            SampleSegment(
                id = segment.id,
                name = segment.name,
                startMs = startMs,
                endMs = endMs,
                sampleRateHz = wavInfo.sampleRate,
                pcm16Stereo = pcm
            )
        }
    }
}

private fun readSamplerTestWavInfo(file: File): SamplerTestWavInfo {
    RandomAccessFile(file, "r").use { input ->
        require(readSamplerAscii(input, 4) == "RIFF") { "invalid_wav" }
        readSamplerIntLe(input)
        require(readSamplerAscii(input, 4) == "WAVE") { "invalid_wav" }

        var sampleRate: Int? = null
        var channelCount: Int? = null
        var bitsPerSample: Int? = null
        var dataOffset: Long? = null
        var dataSizeBytes: Long? = null

        while (input.filePointer < input.length()) {
            val chunkId = readSamplerAscii(input, 4)
            val chunkSize = readSamplerIntLe(input).toLong() and 0xFFFFFFFFL
            val chunkStart = input.filePointer

            when (chunkId) {
                "fmt " -> {
                    readSamplerShortLe(input)
                    channelCount = readSamplerShortLe(input)
                    sampleRate = readSamplerIntLe(input)
                    readSamplerIntLe(input)
                    readSamplerShortLe(input)
                    bitsPerSample = readSamplerShortLe(input)
                }
                "data" -> {
                    dataOffset = chunkStart
                    dataSizeBytes = chunkSize
                    break
                }
            }

            input.seek(chunkStart + chunkSize + (chunkSize and 1L))
        }

        return SamplerTestWavInfo(
            sampleRate = sampleRate ?: error("sample_rate_missing"),
            channelCount = channelCount ?: error("channel_count_missing"),
            bitsPerSample = bitsPerSample ?: error("bits_missing"),
            dataOffset = dataOffset ?: error("data_missing"),
            dataSizeBytes = dataSizeBytes ?: error("data_missing")
        )
    }
}

private fun readSamplerAscii(input: RandomAccessFile, byteCount: Int): String {
    val buffer = ByteArray(byteCount)
    input.readFully(buffer)
    return String(buffer, Charsets.US_ASCII)
}

private fun readSamplerIntLe(input: RandomAccessFile): Int {
    val b0 = input.read()
    val b1 = input.read()
    val b2 = input.read()
    val b3 = input.read()
    require(b3 >= 0) { "unexpected_eof" }
    return (b0 and 0xFF) or
        ((b1 and 0xFF) shl 8) or
        ((b2 and 0xFF) shl 16) or
        ((b3 and 0xFF) shl 24)
}

private fun readSamplerShortLe(input: RandomAccessFile): Int {
    val b0 = input.read()
    val b1 = input.read()
    require(b1 >= 0) { "unexpected_eof" }
    return (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
}

private data class SamplerTestWavInfo(
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataSizeBytes: Long
) {
    val dataEndOffset: Long
        get() = dataOffset + dataSizeBytes
}

private const val SAMPLER_TEST_TAG = "ArrangementSamplerTest"
