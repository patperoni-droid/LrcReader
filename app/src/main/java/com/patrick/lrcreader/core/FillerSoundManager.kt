package com.patrick.lrcreader.core

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.exo.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

object FillerSoundManager {
    private const val METER_TAG = "METER"
    private const val VIS_LOG_EVERY_MS = 1000L

    private var player: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var currentVolume: Float = DEFAULT_VOLUME
    private var fillerMeterVisualizer: Visualizer? = null
    private var fillerMeterSessionId: Int = 0
    private var fillerVisCallbacks: Int = 0
    private var fillerVisLastLogTs: Long = 0L

    private var folderPlaylist: List<Uri> = emptyList()
    private var folderIndex: Int = 0
    private var currentFolderUri: Uri? = null
    private var advanceOnNextStart: Boolean = false

    private const val DEFAULT_VOLUME = 0.25f
    private const val CROSSFADE_MS = 1500L
    private const val ASSET_SCHEME = "asset"
    private val DEFAULT_ASSET_TRACKS = listOf(
        "fond_sonore/FS_Lounge_Warm.mp3",
        "fond_sonore/FS_Lounge_Warm_Alt.mp3"
    )

    fun startFromPlayerPause(context: Context) {
        advanceOnNextStart = true
        startIfConfigured(context)
    }

    fun startIfConfigured(context: Context) {
        if (!FillerSoundPrefs.isEnabled(context)) {
            fadeOutAndStop(0)
            return
        }
        if (PlaybackCoordinator.isMainPlaying) {
            fadeOutAndStop(0)
            return
        }
        internalStart(context)
    }

    fun startFromUi(context: Context) {
        if (!FillerSoundPrefs.isEnabled(context)) {
            FillerSoundPrefs.setEnabled(context, true)
        }
        internalStart(context)
    }

    private fun internalStart(context: Context) {
        currentVolume = FillerSoundPrefs.getFillerVolume(context)

        val folderUri = FillerSoundPrefs.getActiveFillerFolder(context)
        if (folderUri != null) {
            if (!isFolderUriReadable(context, folderUri)) {
                folderPlaylist = emptyList()
                currentFolderUri = null
                advanceOnNextStart = false
                FillerSoundPrefs.clearFillerFolder(context)
                Toast.makeText(context, "Dossier fond sonore invalide.", Toast.LENGTH_SHORT).show()
            } else {
                val built: List<Uri> = if (
                    folderPlaylist.isNotEmpty() &&
                    currentFolderUri != null &&
                    currentFolderUri == folderUri
                ) {
                    folderPlaylist
                } else {
                    val fresh = buildPlaylistFromFolderDirect(context, folderUri)
                    if (fresh.isEmpty()) {
                        advanceOnNextStart = false
                        Toast.makeText(context, "Aucun MP3/WAV trouvé dans ce dossier", Toast.LENGTH_SHORT).show()
                        return
                    }
                    folderPlaylist = fresh
                    currentFolderUri = folderUri
                    folderIndex = if (fresh.size == 1) 0 else Random.nextInt(fresh.size)
                    fresh
                }

                if (built.isNotEmpty() && advanceOnNextStart) {
                    if (built.size > 1) folderIndex = (folderIndex + 1) % built.size
                    advanceOnNextStart = false
                }

                val folderStartSucceeded = runCatching {
                    startFromFolderIndex(context, folderIndex)
                }.onFailure { error ->
                    error.printStackTrace()
                    folderPlaylist = emptyList()
                    currentFolderUri = null
                    FillerSoundPrefs.clearFillerFolder(context)
                    Toast.makeText(context, "Impossible de lire le dossier", Toast.LENGTH_SHORT).show()
                }.isSuccess
                if (folderStartSucceeded) {
                    return
                }
            }
        }

        val fileUri = FillerSoundPrefs.getFillerUri(context)
        if (fileUri == null) {
            val assetPlaylist = buildDefaultAssetPlaylist(context)
            if (assetPlaylist.isNotEmpty()) {
                folderPlaylist = assetPlaylist
                currentFolderUri = null
                if (folderIndex !in assetPlaylist.indices) {
                    folderIndex = if (assetPlaylist.size == 1) 0 else Random.nextInt(assetPlaylist.size)
                }
                if (advanceOnNextStart && assetPlaylist.size > 1) {
                    folderIndex = (folderIndex + 1) % assetPlaylist.size
                }
                advanceOnNextStart = false
                runCatching { startFromFolderIndex(context, folderIndex) }
                    .onFailure {
                        Log.e("FillerSoundManager", "startFromAssets failed: ${it.message}", it)
                        fadeOutAndStop(0)
                    }
            }
            return
        }
        try {
            startFromSingleFile(context, fileUri)
        } catch (e: Exception) {
            e.printStackTrace()
            FillerSoundPrefs.clear(context)
            Toast.makeText(context, "Impossible de lire le fond sonore.", Toast.LENGTH_SHORT).show()
        } finally {
            advanceOnNextStart = false
        }
    }

    fun toggle(context: Context) {
        if (isPlaying()) fadeOutAndStop(200)
        else startIfConfigured(context)
    }

    fun next(context: Context) {
        if (folderPlaylist.isEmpty()) {
            startIfConfigured(context)
            return
        }
        if (folderPlaylist.size == 1) {
            try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
            return
        }
        folderIndex = (folderIndex + 1) % folderPlaylist.size
        try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
    }

    fun previous(context: Context) {
        if (folderPlaylist.isEmpty()) {
            startIfConfigured(context)
            return
        }
        if (folderPlaylist.size == 1) {
            try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
            return
        }
        folderIndex = (folderIndex - 1 + folderPlaylist.size) % folderPlaylist.size
        try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
    }

    fun isPlaying(): Boolean {
        val mainPlaying = safeIsPlaying(player, "player")
        val nextPlaying = safeIsPlaying(nextPlayer, "nextPlayer")
        return mainPlaying || nextPlaying
    }

    fun getAudioSessionIds(): List<Int> {
        val playerId = safeSessionId(player, "player")
        val nextId = safeSessionId(nextPlayer, "nextPlayer")
        return listOf(playerId, nextId).filter { it > 0 }.distinct()
    }

    private fun safeIsPlaying(mp: MediaPlayer?, label: String): Boolean {
        return runCatching { mp?.isPlaying == true }
            .getOrElse {
                Log.w(METER_TAG, "FILLER isPlaying($label) failed: ${it.message}")
                false
            }
    }

    private fun safeSessionId(mp: MediaPlayer?, label: String): Int {
        return runCatching { mp?.audioSessionId ?: 0 }
            .getOrElse {
                Log.w(METER_TAG, "FILLER audioSessionId($label) failed: ${it.message}")
                0
            }
    }

    private fun logMeterState(reason: String) {
        val pPlaying = safeIsPlaying(player, "player")
        val nPlaying = safeIsPlaying(nextPlayer, "nextPlayer")
        val pSession = safeSessionId(player, "player")
        val nSession = safeSessionId(nextPlayer, "nextPlayer")
        val anyPlaying = pPlaying || nPlaying
        val session = listOf(pSession, nSession).firstOrNull { it > 0 }
        Log.d(
            METER_TAG,
            "FILLER $reason isPlayingPlayer=$pPlaying isPlayingNext=$nPlaying sessionPlayer=$pSession sessionNext=$nSession"
        )
        if (anyPlaying) {
            Log.d(
                METER_TAG,
                "PLAY_START engine=FillerSoundManager sessionId=$session isPlaying=$anyPlaying reason=$reason"
            )
        }
    }

    private fun startFromSingleFile(context: Context, uri: Uri) {
        stopNow()

        val mp = MediaPlayer()
        try {
            setDataSource(context, mp, uri)
            mp.isLooping = true

            mp.setOnErrorListener { _, what, extra ->
                android.util.Log.e("FillerSoundManager", "MediaPlayer error what=$what extra=$extra uri=$uri")
                Toast.makeText(context, "Fond sonore : erreur de lecture (fichier ou droits).", Toast.LENGTH_SHORT).show()
                true
            }

            // ✅ synchrone
            mp.prepare()
            mp.setVolume(currentVolume, currentVolume)
            mp.start()
            logMeterState("start single file")

            player = mp
            attachFillerMeterTap(mp)
            folderPlaylist = emptyList()
            currentFolderUri = null
        } catch (t: Throwable) {
            android.util.Log.e("FillerSoundManager", "startFromSingleFile failed uri=$uri : ${t.message}", t)
            try { mp.release() } catch (_: Exception) {}
            Toast.makeText(context, "Impossible de lire le fond sonore (autorisation ?).", Toast.LENGTH_SHORT).show()
            fadeOutAndStop(0)
        }
    }

    private fun isAudioName(name: String): Boolean {
        return name.endsWith(".mp3", true) || name.endsWith(".wav", true)
    }

    private fun buildDefaultAssetPlaylist(context: Context): List<Uri> {
        return DEFAULT_ASSET_TRACKS
            .filter { assetPath -> runCatching { context.assets.openFd(assetPath).close() }.isSuccess }
            .map { assetPath -> Uri.parse("$ASSET_SCHEME:///$assetPath") }
    }

    private fun setDataSource(context: Context, mp: MediaPlayer, uri: Uri) {
        if (uri.scheme == ASSET_SCHEME) {
            val assetPath = uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Invalid asset uri: $uri")
            val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
            afd.use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            return
        }
        mp.setDataSource(context, uri)
    }

    private fun isFolderUriReadable(context: Context, folderUri: Uri): Boolean {
        return if (folderUri.scheme == "file") {
            File(folderUri.path ?: return false).isDirectory
        } else {
            openFolderDocument(context, folderUri)?.isDirectory == true
        }
    }

    private fun openFolderDocument(context: Context, folderUri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, folderUri)
            ?: DocumentFile.fromSingleUri(context, folderUri)
    }

    private fun buildPlaylistFromFolderDirect(
        context: Context,
        folderUri: Uri
    ): List<Uri> {
        if (folderUri.scheme == "file") {
            val rootDir = File(folderUri.path ?: return emptyList())
            if (!rootDir.isDirectory) return emptyList()

            val out = ArrayList<Pair<String, Uri>>(256)
            fun walk(dir: File) {
                dir.listFiles()
                    ?.sortedBy { child -> child.name.lowercase(Locale.ROOT) }
                    .orEmpty()
                    .forEach { child ->
                        if (child.isDirectory) {
                            walk(child)
                        } else if (isAudioName(child.name)) {
                            out.add(child.name to Uri.fromFile(child))
                        }
                    }
            }

            walk(rootDir)
            return out.sortedBy { it.first.lowercase(Locale.ROOT) }.map { it.second }
        }

        val rootDoc = openFolderDocument(context, folderUri) ?: return emptyList()
        if (!rootDoc.isDirectory) return emptyList()

        val out = ArrayList<Pair<String, Uri>>(256)
        fun walk(dir: DocumentFile) {
            runCatching { dir.listFiles() }
                .getOrDefault(emptyArray())
                .sortedBy { child -> (child.name ?: "").lowercase(Locale.ROOT) }
                .forEach { child ->
                    if (child.isDirectory) {
                        walk(child)
                    } else {
                        val name = child.name ?: ""
                        if (name.isNotBlank() && isAudioName(name)) {
                            out.add(name to child.uri)
                        }
                    }
                }
        }

        walk(rootDoc)
        return out.sortedBy { it.first.lowercase(Locale.ROOT) }.map { it.second }
    }

    private fun startFromFolderIndex(context: Context, index: Int) {
        if (folderPlaylist.isEmpty()) return
        val uri = folderPlaylist[index]

        stopNext()

        val mp = MediaPlayer()
        try {
            setDataSource(context, mp, uri)
            mp.isLooping = false

            mp.setOnCompletionListener { playNextInFolder(context) }

            mp.setOnErrorListener { _, what, extra ->
                android.util.Log.e("FillerSoundManager", "MediaPlayer error what=$what extra=$extra uri=$uri")
                Toast.makeText(context, "Fond sonore : erreur de lecture (fichier ou droits).", Toast.LENGTH_SHORT).show()
                true
            }

            // ✅ IMPORTANT : synchrone -> démarre immédiatement, isPlaying() devient vrai
            mp.prepare()
            mp.setVolume(currentVolume, currentVolume)
            mp.start()
            logMeterState("start folder index")

            stopCurrentOnly()
            player = mp
            attachFillerMeterTap(mp)
        } catch (t: Throwable) {
            android.util.Log.e("FillerSoundManager", "setDataSource/prepare failed uri=$uri : ${t.message}", t)
            try { mp.release() } catch (_: Exception) {}
            Toast.makeText(context, "Impossible de lire ce fichier (autorisation ?).", Toast.LENGTH_SHORT).show()
            fadeOutAndStop(0)
        }
    }

    // ✅ Fonction manquante (ou sortie de l’objet par une accolade mal placée)
// On la remet ici DANS l’objet, comme ça l’appel compile et l’enchaînement refonctionne.
    private fun playNextInFolder(context: Context) {
        if (folderPlaylist.isEmpty()) { stopNow(); return }

        folderIndex = (folderIndex + 1) % folderPlaylist.size
        val nextUri = folderPlaylist[folderIndex]
        val oldPlayer = player ?: return

        stopNext()

        try {
            val newPlayer = MediaPlayer()
            setDataSource(context, newPlayer, nextUri)
            newPlayer.isLooping = false
            newPlayer.prepare()
            newPlayer.setVolume(0f, 0f)
            newPlayer.start()
            nextPlayer = newPlayer
            logMeterState("crossfade next start")

            fadeJob?.cancel()
            fadeJob = CoroutineScope(Dispatchers.Main).launch {
                val steps = 20
                val stepTime = CROSSFADE_MS / steps
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val volOut = currentVolume * (1f - t)
                    val volIn = currentVolume * t
                    oldPlayer.setVolume(volOut, volOut)
                    newPlayer.setVolume(volIn, volIn)
                    delay(stepTime)
                }

                try { oldPlayer.stop() } catch (_: Exception) {}
                oldPlayer.release()

                player = newPlayer
                nextPlayer = null

                newPlayer.setOnCompletionListener { playNextInFolder(context) }
                attachFillerMeterTap(newPlayer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Fond sonore : impossible d’enchaîner le morceau suivant.", Toast.LENGTH_SHORT).show()
        }
    }
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        currentVolume = v

        fadeJob?.cancel()
        fadeJob = null

        player?.setVolume(v, v)
        nextPlayer?.setVolume(v, v)
    }

    fun setVolumeWithSystem(context: Context, volume: Float, systemFloorPercent: Float = 0.6f) {
        val old = currentVolume
        setVolume(volume)
        if (volume > old) {
            SystemVolumeHelper.setMusicVolumeFloor(context, systemFloorPercent)
        }
    }

    fun fadeOutAndStop(durationMs: Long = 200) {
        val p = player ?: return
        fadeJob?.cancel()
        fadeJob = CoroutineScope(Dispatchers.Main).launch {
            val startVol = currentVolume
            val steps = 16
            val stepTime = durationMs / steps
            for (i in 0..steps) {
                val factor = 1f - i / steps.toFloat()
                val v = (startVol * factor).coerceIn(0f, 1f)
                p.setVolume(v, v)
                delay(stepTime)
            }
            stopNow()
        }
    }

    private fun stopCurrentOnly() {
        val p = player ?: return
        releaseFillerMeterTap()
        try { p.stop() } catch (_: Exception) {}
        p.release()
        player = null
    }

    private fun stopNow() {
        fadeJob?.cancel()
        fadeJob = null
        releaseFillerMeterTap()
        player?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        player = null
        stopNext()
    }

    private fun stopNext() {
        nextPlayer?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        nextPlayer = null
    }

    private fun attachFillerMeterTap(mp: MediaPlayer?) {
        val sessionId = runCatching { mp?.audioSessionId ?: 0 }.getOrElse { 0 }
        if (sessionId <= 0) {
            releaseFillerMeterTap()
            return
        }
        if (fillerMeterVisualizer != null && fillerMeterSessionId == sessionId) return

        releaseFillerMeterTap()
        fillerMeterVisualizer = runCatching {
            Visualizer(sessionId).apply {
                val captureSizeRange = Visualizer.getCaptureSizeRange()
                captureSize = captureSizeRange[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform == null || waveform.isEmpty()) return
                            publishFillerWaveformLevels(waveform)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) = Unit
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false
                )
                enabled = true
            }
        }.onFailure {
            if (BuildConfig.DEBUG) {
                Log.w(METER_TAG, "FILLER_VIS session=$sessionId enabled=false callbacks=0 rms=0.00 peak=0.00 error=${it.message}")
            }
        }.getOrNull()

        fillerMeterSessionId = if (fillerMeterVisualizer != null) sessionId else 0
        fillerVisCallbacks = 0
        fillerVisLastLogTs = 0L
        if (BuildConfig.DEBUG) {
            Log.d(
                METER_TAG,
                "FILLER_VIS session=$sessionId enabled=${fillerMeterVisualizer != null} callbacks=0 rms=0.00 peak=0.00"
            )
        }
    }

    private fun releaseFillerMeterTap() {
        val session = fillerMeterSessionId
        val callbacks = fillerVisCallbacks
        val tap = fillerMeterVisualizer ?: return
        runCatching { tap.enabled = false }
        runCatching { tap.release() }
        fillerMeterVisualizer = null
        fillerMeterSessionId = 0
        fillerVisCallbacks = 0
        fillerVisLastLogTs = 0L
        if (BuildConfig.DEBUG) {
            Log.d(
                METER_TAG,
                "FILLER_VIS session=$session enabled=false callbacks=$callbacks rms=0.00 peak=0.00"
            )
        }
    }

    private fun publishFillerWaveformLevels(waveform: ByteArray) {
        var peak = 0f
        var sumSq = 0.0
        var count = 0

        for (i in waveform.indices) {
            val sample = ((waveform[i].toInt() and 0xFF) - 128) / 128f
            val absSample = abs(sample)
            if (absSample > peak) peak = absSample
            sumSq += sample * sample
            count++
        }

        if (count == 0) return
        val rms = sqrt(sumSq / count).toFloat().coerceIn(0f, 1f)
        val peak01 = peak.coerceIn(0f, 1f)
        MeterManager.onFillerPcm(rms = rms, peak = peak01)

        fillerVisCallbacks++
        if (BuildConfig.DEBUG) {
            val now = SystemClock.elapsedRealtime()
            if (now - fillerVisLastLogTs >= VIS_LOG_EVERY_MS) {
                fillerVisLastLogTs = now
                Log.d(
                    METER_TAG,
                    "FILLER_VIS session=$fillerMeterSessionId enabled=${fillerMeterVisualizer != null} callbacks=$fillerVisCallbacks rms=${fmtLevel(rms)} peak=${fmtLevel(peak01)}"
                )
            }
        }
    }

    private fun fmtLevel(value: Float): String = String.format(Locale.US, "%.2f", value)
}

private object SystemVolumeHelper {
    fun setMusicVolumeFloor(context: Context, floorPercent: Float) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val floor = (max * floorPercent.coerceIn(0f, 1f)).toInt()
            if (cur < floor) {
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, floor, 0)
            }
        } catch (_: Exception) {
        }
    }
}
