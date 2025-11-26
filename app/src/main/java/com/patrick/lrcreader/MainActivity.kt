package com.patrick.lrcreader.exo

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.ui.*
import com.patrick.lrcreader.ui.TextPrompterScreen
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

                // Bloc-notes plein écran (overlay)
                var isNotesOpen by remember { mutableStateOf(false) }

                // Écran du fond sonore
                var isFillerSettingsOpen by remember { mutableStateOf(false) }

                // Tempo par morceau (1f = normal)
                var currentTrackTempo by remember { mutableStateOf(1f) }

                // 🔊 Mixage global : niveaux maîtres
                var isGlobalMixOpen by remember { mutableStateOf(false) }
                var playerMasterLevel by remember { mutableStateOf(1f) }   // 100%
                var djMasterLevel by remember { mutableStateOf(1f) }        // 100%
                var fillerMasterLevel by remember { mutableStateOf(0.6f) }  // 60%

                // Id du titre texte pour le prompteur (normalisé : "note:123" ou "text:abc")
                var textPrompterId by remember { mutableStateOf<String?>(null) }

                // ----------------------------------------------------------
                //  BRANCHAGE PlaybackCoordinator
                // ----------------------------------------------------------

                PlaybackCoordinator.stopPlayer = {
                    try {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                        }
                        isPlaying = false
                    } catch (_: Exception) {}
                }

                PlaybackCoordinator.stopDj = {
                    runCatching { DjEngine.stopDj() }
                }

                PlaybackCoordinator.stopFiller = {
                    runCatching { FillerSoundManager.fadeOutAndStop(200) }
                }

                // ----------------------------------------------------------
                //  GAIN PAR MORCEAU (avec master Lecteur)
                // ----------------------------------------------------------
                fun applyGainToPlayer(db: Int) {
                    try {
                        val clamped = db.coerceIn(-12, 12)
                        val master = playerMasterLevel.coerceIn(0f, 1f)

                        if (clamped <= 0) {
                            // Gain négatif : on baisse le volume du player + master
                            val linear = 10f.pow(clamped / 20f)
                            val v = linear * master
                            mediaPlayer.setVolume(v, v)
                            loudnessEnhancer.setTargetGain(0)
                        } else {
                            // Gain positif : volume 1.0 * master + LoudnessEnhancer
                            val v = 1f * master
                            mediaPlayer.setVolume(v, v)
                            loudnessEnhancer.setTargetGain(clamped * 100)
                        }
                    } catch (_: Exception) {}
                }

                // ----------------------------------------------------------
                //  TEMPO PAR MORCEAU
                // ----------------------------------------------------------
                fun applyTempoToPlayer(speed: Float) {
                    try {
                        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                        val params = mediaPlayer.playbackParams
                            .setSpeed(safeSpeed)
                            .setPitch(1f)
                        mediaPlayer.playbackParams = params
                    } catch (_: Exception) {}
                }

                // ----------------------------------------------------------
                //  PLAY + CROSSFADE
                // ----------------------------------------------------------
                val playWithCrossfade: (String, String?) -> Unit = { uriString, playlistName ->

                    PlaybackCoordinator.onPlayerStart()
                    currentPlayingUri = uriString
                    runCatching { FillerSoundManager.fadeOutAndStop(400) }

                    val myToken = currentPlayToken + 1
                    currentPlayToken = myToken

                    val savedDb = TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                    currentTrackGainDb = savedDb

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
                                runCatching { FillerSoundManager.startIfConfigured(ctx) }
                            }
                        )
                    }

                    if (result.isFailure) {
                        mediaPlayer.reset()
                        isPlaying = false
                        PlaybackCoordinator.onPlayerStop()
                    }

                    selectedTab = BottomTab.Player
                    SessionPrefs.saveTab(ctx, TAB_PLAYER)
                }

                // ----------------------------------------------------------
                //  RELEASE
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
                            onSelected = { tab ->
                                // On change l’onglet
                                selectedTab = tab
                                SessionPrefs.saveTab(ctx, tabKeyOf(tab))

                                // On ferme les écrans plein écran / overlays
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isNotesOpen = false        // 👈 important : ferme Mes notes
                            }
                        )
                    }
                ) { innerPadding ->

                    // Écran réglages du fond sonore
                    if (isFillerSettingsOpen) {
                        FillerSoundScreen(
                            context = ctx,
                            onBack = { isFillerSettingsOpen = false }
                        )
                        return@Scaffold
                    }

                    // 🎚️ Mixage global plein écran
                    if (isGlobalMixOpen) {
                        GlobalMixScreen(
                            modifier = Modifier.padding(innerPadding),
                            playerLevel = playerMasterLevel,
                            onPlayerLevelChange = { lvl ->
                                playerMasterLevel = lvl
                                applyGainToPlayer(currentTrackGainDb)
                            },
                            djLevel = djMasterLevel,
                            onDjLevelChange = { lvl ->
                                djMasterLevel = lvl
                                DjEngine.setMasterVolume(lvl)
                            },
                            fillerLevel = fillerMasterLevel,
                            onFillerLevelChange = { lvl ->
                                fillerMasterLevel = lvl
                            },
                            onBack = { isGlobalMixOpen = false }
                        )
                        return@Scaffold
                    }

                    // 🔹 Prompteur texte plein écran
                    textPrompterId?.let { tid ->
                        TextPrompterScreen(
                            modifier = Modifier.padding(innerPadding),
                            songId = tid,
                            onClose = { textPrompterId = null }
                        )
                        return@Scaffold
                    }

                    // ------------------------------------------------------
                    //  NAVIGATION PAR ONGLET
                    // ------------------------------------------------------
                    when (selectedTab) {

                        is BottomTab.Home -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onOpenPlayer = {
                                // Le bouton "Mode Playlist" ouvre les playlists rapides
                                selectedTab = BottomTab.QuickPlaylists
                                SessionPrefs.saveTab(ctx, TAB_QUICK)
                            },
                            onOpenFondSonore = { isFillerSettingsOpen = true },
                            onOpenDjMode = {
                                selectedTab = BottomTab.Dj
                                SessionPrefs.saveTab(ctx, TAB_DJ)
                            },
                            onOpenGlobalMix = { isGlobalMixOpen = true },
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

                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onPlaySong = { uri, playlistName, color ->
                                if (uri.startsWith("prompter://")) {
                                    // Titre texte : ouverture prompteur, pas lecteur audio
                                    val rawId = uri.removePrefix("prompter://")
                                    val numeric = rawId.toLongOrNull()
                                    textPrompterId =
                                        if (numeric != null) "note:$numeric"
                                        else "text:$rawId"

                                    selectedQuickPlaylist = playlistName
                                    SessionPrefs.saveQuickPlaylist(ctx, playlistName)
                                    currentLyricsColor = color
                                } else {
                                    // Audio normal
                                    playWithCrossfade(uri, playlistName)
                                    currentPlayingUri = uri
                                    selectedQuickPlaylist = playlistName
                                    SessionPrefs.saveQuickPlaylist(ctx, playlistName)
                                    currentLyricsColor = color
                                }
                            },
                            refreshKey = refreshKey,
                            currentPlayingUri = currentPlayingUri,
                            selectedPlaylist = selectedQuickPlaylist,
                            onSelectedPlaylistChange = { name ->
                                selectedQuickPlaylist = name
                                SessionPrefs.saveQuickPlaylist(ctx, name)
                            }
                        )

                        is BottomTab.Library ->
                            LibraryScreen(modifier = Modifier.padding(innerPadding))

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
                            }
                        }

                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onAfterImport = { refreshKey++ }
                        )

                        is BottomTab.Dj -> DjScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx
                        )

                        is BottomTab.Tuner -> TunerScreen(
                            modifier = Modifier.padding(innerPadding),
                            onClose = {
                                selectedTab = BottomTab.Home
                                SessionPrefs.saveTab(ctx, TAB_HOME)
                            }
                        )
                    }

                    // 🔹 OVERLAY "Mes notes" par-dessus le contenu, barre du bas accessible
                    if (isNotesOpen) {
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .background(Color(0xAA000000)) // léger voile
                        ) {
                            NotesScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 70.dp), // pour laisser voir la barre
                                context = ctx,
                                onClose = { isNotesOpen = false }
                            )
                        }
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
    TAB_TUNER -> BottomTab.Home
    else -> BottomTab.Home
}