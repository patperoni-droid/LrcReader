package com.patrick.lrcreader.core

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

    /**
     * Utilisé par le fond sonore pour savoir si un "titre principal"
     * (Player ou DJ) est en train de jouer.
     */
    val isMainPlaying: Boolean
        get() = currentSource == Source.Player || currentSource == Source.Dj

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

    @Synchronized
    fun onFillerStop() {
        if (currentSource == Source.Filler) {
            currentSource = Source.None
        }
    }
}