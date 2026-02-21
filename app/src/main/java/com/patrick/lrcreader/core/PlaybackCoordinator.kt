package com.patrick.lrcreader.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordonne les 3 sources audio :
 * - Lecteur principal
 * - DJ
 * - Fond sonore (filler)
 *
 * Règles :
 * - Player, DJ et Filler ne jouent jamais ensemble.
 * - Quand l’un démarre, il coupe les autres via les callbacks stopX.
 */

object PlaybackCoordinator {
    private const val NEXT_TAG = "NEXT"

    data class NextTrack(
        val uri: String,
        val title: String,
        val playlist: String?
    )

    enum class Source {
        None,
        Player,
        Dj,
        Filler
    }

    // Source actuellement "maître"
    private var currentSource: Source = Source.None

    // callbacks fournis par MainActivity / DjEngine / FillerSoundManager
    var stopPlayer: (() -> Unit)? = null
    var stopDj: (() -> Unit)? = null
    var stopFiller: (() -> Unit)? = null
    private val _nextTrack = MutableStateFlow<NextTrack?>(null)
    val nextTrack: StateFlow<NextTrack?> = _nextTrack.asStateFlow()

    /**
     * Utilisé par le fond sonore pour savoir si un "titre principal"
     * (Player ou DJ) est en train de jouer.
     */
    val isMainPlaying: Boolean
        get() = currentSource == Source.Player || currentSource == Source.Dj

    @Synchronized
    fun setNextTrack(uri: String, title: String, playlist: String?) {
        val cleanUri = uri.trim()
        if (cleanUri.isEmpty()) return
        val cleanTitle = title.ifBlank { cleanUri }
        _nextTrack.value = NextTrack(
            uri = cleanUri,
            title = cleanTitle,
            playlist = playlist
        )
        Log.i(NEXT_TAG, "set uri=$cleanUri title=$cleanTitle playlist=$playlist")
    }

    @Synchronized
    fun clearNextTrack(reason: String = "") {
        val previous = _nextTrack.value ?: return
        _nextTrack.value = null
        Log.i(NEXT_TAG, "clear uri=${previous.uri} reason=$reason")
    }

    @Synchronized
    fun peekNextTrack(): NextTrack? = _nextTrack.value

    /* ---------------------------------------------------------- */
    /*  PLAYER                                                     */
    /* ---------------------------------------------------------- */

    @Synchronized
    fun onPlayerStart() {
        // le lecteur devient maître → coupe DJ + filler
        if (currentSource == Source.Dj) {
            stopDj?.invoke()
        }
        if (currentSource == Source.Filler) {
            stopFiller?.invoke()
        }
        currentSource = Source.Player
    }

    @Synchronized
    fun onPlayerStop() {
        if (currentSource == Source.Player) {
            currentSource = Source.None
        }
    }

    /* ---------------------------------------------------------- */
    /*  DJ                                                         */
    /* ---------------------------------------------------------- */

    @Synchronized
    fun onDjStart() {
        // le DJ devient maître → coupe lecteur + filler
        if (currentSource == Source.Player) {
            stopPlayer?.invoke()
        }
        if (currentSource == Source.Filler) {
            stopFiller?.invoke()
        }
        currentSource = Source.Dj
    }

    @Synchronized
    fun onDjStop() {
        if (currentSource == Source.Dj) {
            currentSource = Source.None
        }
    }

    /* ---------------------------------------------------------- */
    /*  FILLER (fond sonore)                                      */
    /* ---------------------------------------------------------- */

    @Synchronized
    fun onFillerStart() {
        // le filler ne doit JAMAIS se lancer par-dessus un titre principal
        when (currentSource) {
            Source.Player -> stopPlayer?.invoke()
            Source.Dj     -> stopDj?.invoke()
            else          -> {}
        }
        currentSource = Source.Filler
    }
    // ----------------------------------------------------------
    //  "PORTES D'ENTRÉE" À UTILISER PARTOUT
    //  (verrouille la règle : un seul son à la fois)
    // ----------------------------------------------------------

    @Synchronized
    fun requestStartPlayer() {
        onPlayerStart()
    }

    @Synchronized
    fun requestStartDj() {
        onDjStart()
    }

    @Synchronized
    fun requestStartFiller() {
        onFillerStart()
    }
    @Synchronized
    fun onFillerStop() {
        if (currentSource == Source.Filler) {
            currentSource = Source.None
        }
    }
}
