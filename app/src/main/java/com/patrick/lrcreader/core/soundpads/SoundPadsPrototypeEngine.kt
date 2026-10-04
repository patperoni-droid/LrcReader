@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.patrick.lrcreader.core.soundpads

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.PadsBusController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One dedicated Media3 player. No sound processing, focus request or main-source commands. */
class SoundPadsPrototypeEngine(context: Context) {
    enum class Phase { IDLE, PREPARING, PLAYING, ERROR }
    data class Sample(val padId: String, val source: String, val readyMs: Long?, val sinkCallbackMs: Long)
    data class State(
        val phase: Phase = Phase.IDLE,
        val padId: String? = null,
        val readyMs: Long? = null,
        val samples: List<Sample> = emptyList(),
        val errorCode: Int? = null,
        val underruns: Int = 0
    )
    private data class Request(
        val mediaId: String, val pad: SoundPad, val startMs: Long, val source: String,
        var readyMs: Long? = null, var sampled: Boolean = false
    )
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var sequence = 0L
    private var request: Request? = null
    private val appContext = context.applicationContext
    private var released = false
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        // This overlay must not take audio focus away from Player, DJ or Filler.
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
        setHandleAudioBecomingNoisy(true)
        repeatMode = Player.REPEAT_MODE_OFF
    }

    init {
        PadsBusController.attachEngine(this)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val current = currentRequest() ?: return
                if (playbackState == Player.STATE_READY && current.readyMs == null) {
                    current.readyMs = SystemClock.elapsedRealtime() - current.startMs
                    mutableState.value = mutableState.value.copy(readyMs = current.readyMs)
                    log("READY", current, "readyMs=${current.readyMs}")
                }
                if (playbackState == Player.STATE_ENDED) stopAll()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val current = currentRequest()
                if (isPlaying && current != null) {
                    PlaybackCoordinator.onPadsStart(current.pad.padId)
                    mutableState.value = mutableState.value.copy(phase = Phase.PLAYING)
                } else {
                    PlaybackCoordinator.onPadsStop()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                log("ERROR", request, "code=${error.errorCode}")
                stopAll()
                mutableState.value = mutableState.value.copy(phase = Phase.ERROR, errorCode = error.errorCode)
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioPositionAdvancing(
                eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long
            ) {
                val current = currentRequest() ?: return
                if (current.sampled || eventTime.timeline.isEmpty) return
                val window = androidx.media3.common.Timeline.Window()
                val eventMediaId = eventTime.timeline.getWindow(eventTime.windowIndex, window).mediaItem.mediaId
                if (eventMediaId != current.mediaId) return
                current.sampled = true
                // Callback reception is an observable software delay, NOT acoustic latency.
                val elapsed = SystemClock.elapsedRealtime() - current.startMs
                val sample = Sample(current.pad.padId, current.source, current.readyMs, elapsed)
                mutableState.value = mutableState.value.copy(samples = (mutableState.value.samples + sample).takeLast(60))
                log("SINK_CALLBACK", current, "callbackMs=$elapsed readyMs=${current.readyMs} playoutWallMs=$playoutStartSystemTimeMs")
            }
            override fun onAudioUnderrun(
                eventTime: AnalyticsListener.EventTime, bufferSize: Int,
                bufferSizeMs: Long, elapsedSinceLastFeedMs: Long
            ) {
                mutableState.value = mutableState.value.copy(underruns = mutableState.value.underruns + 1)
                log("UNDERRUN", request, "bufferMs=$bufferSizeMs elapsedSinceFeedMs=$elapsedSinceLastFeedMs")
            }
        })
    }

    fun trigger(pad: SoundPad) {
        checkMainThread()
        if (released) return
        player.playWhenReady = false
        PlaybackCoordinator.onPadsStop()
        val current = Request("${pad.padId}:${++sequence}", pad, SystemClock.elapsedRealtime(),
            PlaybackCoordinator.activeSource.value.name)
        request = current
        mutableState.value = mutableState.value.copy(phase = Phase.PREPARING,
            padId = pad.padId, readyMs = null, errorCode = null)
        val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(pad.inMs)
        pad.outMs?.let { clip.setEndPositionMs(it) }
        val media = MediaItem.Builder().setMediaId(current.mediaId)
            .setUri(Uri.fromFile(File(pad.audioPath)))
            .setClippingConfiguration(clip.build()).build()
        log("TRIGGER", current, "inMs=${pad.inMs} outMs=${pad.outMs} gain=${padsEffectiveGain(pad.volume, PadsBusController.uiLevel.value)}")
        player.volume = padsEffectiveGain(pad.volume, PadsBusController.uiLevel.value)
        // Always reset the media, including rapid retriggers. No asynchronous callback calls play().
        player.setMediaItem(media, true)
        player.prepare()
        player.play()
    }

    /** Compatibility entry point; this is the same persistent bus, never a parallel level. */
    fun setGlobalUiLevel(level: Float) = PadsBusController.setUiLevel(appContext, level)

    internal fun applyBusVolume() {
        checkMainThread()
        if (!released) request?.let {
            player.volume = padsEffectiveGain(it.pad.volume, PadsBusController.uiLevel.value)
        }
    }

    internal val outputVolume: Float
        get() { checkMainThread(); return player.volume }

    fun stopAll() {
        checkMainThread()
        if (released) return
        log("STOP", request, "requestInvalidated=true")
        ++sequence
        request = null // invalidates late diagnostics as well as playback intent
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        PlaybackCoordinator.onPadsStop()
        mutableState.value = mutableState.value.copy(phase = Phase.IDLE, padId = null, readyMs = null)
    }

    fun release() {
        checkMainThread()
        if (released) return
        stopAll()
        player.release()
        released = true
        PadsBusController.detachEngine(this)
    }

    private fun currentRequest(): Request? = request?.takeIf { player.currentMediaItem?.mediaId == it.mediaId }
    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())
    private fun log(event: String, current: Request?, detail: String) {
        Log.i("SOUND_PADS", "$event request=${current?.mediaId} source=${current?.source} " +
            "mainNow=${PlaybackCoordinator.activeSource.value} $detail")
    }
}
