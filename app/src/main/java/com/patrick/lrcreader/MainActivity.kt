package com.patrick.lrcreader.exo

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.ui.*
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restauration auto de la sauvegarde globale JSON (si besoin)
        AutoRestore.restoreIfNeeded(this)

        // Restauration de l’état de session (onglet, playlists)
        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        // Init DJ
        DjEngine.init(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                val ctx = this@MainActivity

                // MediaPlayer unique de l’appli
                val mediaPlayer = remember { MediaPlayer() }

                // LoudnessEnhancer pour le gain positif
                val loudnessEnhancer = remember {
                    LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                        enabled = true
                    }
                }

                // Onglet sélectionné
                var selectedTab by remember {
                    mutableStateOf(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Home)
                }

                // Playlist rapide sélectionnée
                var selectedQuickPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialQuickPlaylist)
                }

                // Playlist "complète" actuellement ouverte
                var openedPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialOpenedPlaylist)
                }

                // Lecture en cours
                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }

                // Paroles LRC déjà parsées pour le titre en cours
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }

                // Token pour invalider les lectures concurrentes (crossfadePlay)
                var currentPlayToken by remember { mutableStateOf(0L) }

                // Réglage de volume par titre (en dB)
                var currentTrackGainDb by remember { mutableStateOf(0) }

                // Couleur des paroles du titre actuel
                var currentLyricsColor by remember { mutableStateOf(Color(0xFFE040FB)) }

                // Pour forcer un rafraîchissement de certaines listes
                var refreshKey by remember { mutableStateOf(0) }

                // Bloc-notes plein écran
                var isNotesOpen by remember { mutableStateOf(false) }

                // Tempo par morceau (1f = normal)
                var currentTrackTempo by remember { mutableStateOf(1f) }

                // ----------------------------------------------------------
                //  BRANCHAGE PlaybackCoordinator
                //  (comment le lecteur, le DJ et le filler se coupent entre eux)
                // ----------------------------------------------------------

                // 👉 Comment on ARRÊTE le lecteur quand le DJ/filler le demande
                PlaybackCoordinator.stopPlayer = {
                    try {
                        if (mediaPlayer.isPlaying) {
                            // Pause simple et fiable
                            mediaPlayer.pause()
                        }
                        isPlaying = false
                    } catch (_: Exception) {
                    }
                }

                // 👉 Comment on ARRÊTE le DJ quand le lecteur/filler le demande
                PlaybackCoordinator.stopDj = {
                    try {
                        DjEngine.stopDj()
                    } catch (_: Exception) {
                    }
                }

                // 👉 Comment on ARRÊTE le fond sonore
                PlaybackCoordinator.stopFiller = {
                    runCatching { FillerSoundManager.fadeOutAndStop(200) }
                }

                // ----------------------------------------------------------
                //  GAIN PAR MORCEAU
                // ----------------------------------------------------------
                fun applyGainToPlayer(db: Int) {
                    try {
                        val clamped = db.coerceIn(-12, 12)
                        if (clamped <= 0) {
                            // Gain négatif : on baisse le volume du player
                            val linear = 10f.pow(clamped / 20f)
                            mediaPlayer.setVolume(linear, linear)
                            loudnessEnhancer.setTargetGain(0)
                        } else {
                            // Gain positif : volume 1.0 + LoudnessEnhancer
                            mediaPlayer.setVolume(1f, 1f)
                            loudnessEnhancer.setTargetGain(clamped * 100)
                        }
                    } catch (_: Exception) {
                    }
                }

                // ----------------------------------------------------------
                //  TEMPO (SPEED) PAR MORCEAU
                // ----------------------------------------------------------
                fun applyTempoToPlayer(speed: Float) {
                    try {
                        // On évite les valeurs totalement absurdes
                        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                        val params = mediaPlayer.playbackParams
                            .setSpeed(safeSpeed)
                            .setPitch(1f) // on garde le pitch constant
                        mediaPlayer.playbackParams = params
                    } catch (_: Exception) {
                        // IllegalStateException possible si le player n'est pas prêt → on ignore
                    }
                }

                // ----------------------------------------------------------
                //  PLAY + CROSSFADE  (LECTEUR PRINCIPAL)
                // ----------------------------------------------------------
                val playWithCrossfade: (String, String?) -> Unit = { uriString, playlistName ->

                    // Le lecteur démarre → on coupe DJ + fond sonore
                    PlaybackCoordinator.onPlayerStart()

                    currentPlayingUri = uriString
                    runCatching { FillerSoundManager.fadeOutAndStop(400) }

                    // Nouveau token pour invalider une lecture précédente
                    val myToken = currentPlayToken + 1
                    currentPlayToken = myToken

                    // Restauration du gain stocké pour ce titre
                    val savedDb = TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                    currentTrackGainDb = savedDb

                    // Restauration du tempo stocké pour ce titre
                    val savedTempo = TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f
                    currentTrackTempo = savedTempo

                    val result = runCatching {
                        crossfadePlay(
                            context = ctx,
                            mediaPlayer = mediaPlayer,
                            uriString = uriString,
                            playlistName = playlistName,
                            playToken = myToken,
                            getCurrentToken = { currentPlayToken },
                            onLyricsLoaded = { original ->
                                val override = LrcStorage.loadForTrack(ctx, uriString)
                                parsedLines = parseLrc(override ?: original ?: "")
                            },
                            onStart = {
                                isPlaying = true
                                applyGainToPlayer(currentTrackGainDb)
                                applyTempoToPlayer(currentTrackTempo)
                            },
                            onError = {
                                isPlaying = false
                                mediaPlayer.reset()
                                PlaybackCoordinator.onPlayerStop()
                            },
                            onNaturalEnd = {
                                isPlaying = false
                                PlaybackCoordinator.onPlayerStop()
                                // Si la lecture se termine normalement → on relance un fond sonore
                                runCatching { FillerSoundManager.startIfConfigured(ctx) }
                            }
                        )
                    }

                    if (result.isFailure) {
                        mediaPlayer.reset()
                        isPlaying = false
                        PlaybackCoordinator.onPlayerStop()
                    }

                    // On passe automatiquement sur l’onglet Lecteur
                    selectedTab = BottomTab.Player
                    SessionPrefs.saveTab(ctx, TAB_PLAYER)
                }

                // ----------------------------------------------------------
                //  RELEASE RESSOURCES
                // ----------------------------------------------------------
                DisposableEffect(Unit) {
                    onDispose {
                        loudnessEnhancer.release()
                        mediaPlayer.release()
                    }
                }

                // ----------------------------------------------------------
                // UI PRINCIPALE
                // ----------------------------------------------------------
                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = {
                                selectedTab = it
                                SessionPrefs.saveTab(ctx, tabKeyOf(it))
                            }
                        )
                    }
                ) { innerPadding ->

                    // Bloc-notes plein écran (prioritaire sur le reste)
                    if (isNotesOpen) {
                        NotesScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onClose = { isNotesOpen = false }
                        )
                        return@Scaffold
                    }

                    when (selectedTab) {

                        // 🏠 ACCUEIL
                        is BottomTab.Home -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onOpenPlayer = {
                                selectedTab = BottomTab.Player
                                SessionPrefs.saveTab(ctx, TAB_PLAYER)
                            },
                            onOpenConcertMode = {
                                DisplayPrefs.setConcertMode(ctx, true)
                                selectedTab = BottomTab.Player
                                SessionPrefs.saveTab(ctx, TAB_PLAYER)
                            },
                            onOpenDjMode = {
                                selectedTab = BottomTab.Dj
                                SessionPrefs.saveTab(ctx, TAB_DJ)
                            },
                            onOpenTuner = {
                                selectedTab = BottomTab.Tuner
                                SessionPrefs.saveTab(ctx, TAB_TUNER)
                            },
                            onOpenProfile = {},
                            onOpenTutorial = {
                                selectedTab = BottomTab.More
                                SessionPrefs.saveTab(ctx, TAB_MORE)
                            },
                            onOpenSettings = {
                                selectedTab = BottomTab.More
                                SessionPrefs.saveTab(ctx, TAB_MORE)
                            },
                            onOpenNotes = { isNotesOpen = true }
                        )

                        // 🎵 LECTEUR PRINCIPAL
                        is BottomTab.Player -> PlayerScreen(
                            modifier = Modifier.padding(innerPadding),
                            mediaPlayer = mediaPlayer,
                            isPlaying = isPlaying,
                            onIsPlayingChange = { isPlaying = it },
                            parsedLines = parsedLines,
                            onParsedLinesChange = { parsedLines = it },
                            highlightColor = currentLyricsColor,
                            currentTrackUri = currentPlayingUri,
                            currentTrackGainDb = currentTrackGainDb,
                            onTrackGainChange = { db ->
                                currentPlayingUri?.let { uri ->
                                    TrackVolumePrefs.saveDb(ctx, uri, db)
                                }
                                currentTrackGainDb = db
                                applyGainToPlayer(db)
                            },
                            tempo = currentTrackTempo,
                            onTempoChange = { newTempo ->
                                currentTrackTempo = newTempo
                                applyTempoToPlayer(newTempo)
                                currentPlayingUri?.let { uri ->
                                    TrackTempoPrefs.saveTempo(ctx, uri, newTempo)
                                }
                            },
                            onRequestShowPlaylist = {
                                selectedTab = BottomTab.QuickPlaylists
                            }
                        )

                        // ⭐ PLAYLISTS RAPIDES
                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onPlaySong = { uri, playlistName, color ->
                                playWithCrossfade(uri, playlistName)
                                currentPlayingUri = uri
                                selectedQuickPlaylist = playlistName
                                SessionPrefs.saveQuickPlaylist(ctx, playlistName)
                                currentLyricsColor = color
                            },
                            refreshKey = refreshKey,
                            currentPlayingUri = currentPlayingUri,
                            selectedPlaylist = selectedQuickPlaylist,
                            onSelectedPlaylistChange = { name ->
                                selectedQuickPlaylist = name
                                SessionPrefs.saveQuickPlaylist(ctx, name)
                            }
                        )

                        // 📚 BIBLIOTHÈQUE SIMPLE
                        is BottomTab.Library ->
                            LibraryScreen(modifier = Modifier.padding(innerPadding))

                        // 📂 TOUTES LES PLAYLISTS
                        is BottomTab.AllPlaylists -> {
                            val m = Modifier.padding(innerPadding)
                            if (openedPlaylist == null) {
                                AllPlaylistsScreen(
                                    modifier = m,
                                    onPlaylistClick = { name ->
                                        openedPlaylist = name
                                        SessionPrefs.saveOpenedPlaylist(ctx, name)
                                    }
                                )
                            } else {
                                PlaylistDetailScreen(
                                    modifier = m,
                                    playlistName = openedPlaylist!!,
                                    onBack = {
                                        openedPlaylist = null
                                        SessionPrefs.saveOpenedPlaylist(ctx, null)
                                    },
                                    onPlaySong = { uri ->
                                        playWithCrossfade(uri, openedPlaylist)
                                    }
                                )
                            }
                        }

                        // ⚙️ ÉCRAN "PLUS"
                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onAfterImport = { refreshKey++ }
                        )

                        // 🎚️ MODE DJ
                        is BottomTab.Dj -> DjScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx
                        )

                        // 🎸 ACCORDEUR
                        is BottomTab.Tuner -> TunerScreen(
                            modifier = Modifier.padding(innerPadding),
                            onClose = {
                                selectedTab = BottomTab.Home
                                SessionPrefs.saveTab(ctx, TAB_HOME)
                            }
                        )
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------- */
/*  Conversion BottomTab <-> String                                */
/* --------------------------------------------------------------- */

private const val TAB_HOME = "home"
private const val TAB_PLAYER = "player"
private const val TAB_QUICK = "quick"
private const val TAB_LIBRARY = "library"
private const val TAB_ALL = "all"
private const val TAB_MORE = "more"
private const val TAB_DJ = "dj"
private const val TAB_TUNER = "tuner"

private fun tabKeyOf(tab: BottomTab): String = when (tab) {
    is BottomTab.Home -> TAB_HOME
    is BottomTab.Player -> TAB_PLAYER
    is BottomTab.QuickPlaylists -> TAB_QUICK
    is BottomTab.Library -> TAB_LIBRARY
    is BottomTab.AllPlaylists -> TAB_ALL
    is BottomTab.More -> TAB_MORE
    is BottomTab.Dj -> TAB_DJ
    is BottomTab.Tuner -> TAB_TUNER
}

private fun tabFromKey(key: String): BottomTab = when (key) {
    TAB_HOME -> BottomTab.Home
    TAB_PLAYER -> BottomTab.Player
    TAB_QUICK -> BottomTab.QuickPlaylists
    TAB_LIBRARY -> BottomTab.Library
    TAB_ALL -> BottomTab.AllPlaylists
    TAB_MORE -> BottomTab.More
    TAB_DJ -> BottomTab.Dj
    TAB_TUNER -> BottomTab.Home   // on évite le lancement auto du Tuner
    else -> BottomTab.Home
}