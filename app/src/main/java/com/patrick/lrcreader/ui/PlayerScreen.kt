package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.AutoReturnPrefs
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.PlayerBusController
import com.patrick.lrcreader.core.pauseWithFade
import com.patrick.lrcreader.core.MidiCueDispatcher   // ⬅️ IMPORT MIDI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    mediaPlayer: MediaPlayer,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit,
    parsedLines: List<LrcLine>,
    onParsedLinesChange: (List<LrcLine>) -> Unit,
    highlightColor: Color = Color(0xFFE040FB),
    currentTrackUri: String?,
    currentTrackGainDb: Int,
    onTrackGainChange: (Int) -> Unit,
    tempo: Float,
    onTempoChange: (Float) -> Unit,
    // Tonalité en demi-tons (mémorisée par titre)
    pitchSemi: Int,
    onPitchSemiChange: (Int) -> Unit,
    onRequestShowPlaylist: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    // 🔊 On branche ce MediaPlayer sur le bus LECTEUR
    LaunchedEffect(Unit) {
        PlayerBusController.attachPlayer(context, mediaPlayer)
        PlayerBusController.applyCurrentVolume(context)
    }

    // Décalage global des paroles (latence de base)
    val lyricsDelayMs = 0L

    // 🔧 Offset ajustable par l'utilisateur, par morceau
    //    0 = comportement actuel
    //    +1000 = les paroles sortent plus tard d'environ 1 s
    //    -1000 = les paroles sortent plus tôt d'environ 1 s
    var userOffsetMs by remember(currentTrackUri) { mutableStateOf(-100L) }

    var isConcertMode by remember {
        mutableStateOf(DisplayPrefs.isConcertMode(context))
    }

    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember { mutableStateOf(0) }

    // Dernière ligne pour laquelle on a envoyé un CUE MIDI
    var lastMidiIndex by remember(currentTrackUri) { mutableStateOf(-1) }
    var showLyrics by remember { mutableStateOf(true) }
    var userScrolling by remember { mutableStateOf(false) }

    val lineHeightDp = 80.dp
    val lineHeightPx = with(density) { lineHeightDp.toPx() }
    val baseTopSpacerPx by remember(lyricsBoxHeightPx) { mutableStateOf(lyricsBoxHeightPx) }

    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableStateOf(0) }

    var hasRequestedPlaylist by remember(currentTrackUri) { mutableStateOf(false) }

    // 🔘 retour auto vers la playlist (-10 s)
    var isAutoReturnEnabled by remember {
        mutableStateOf(AutoReturnPrefs.isEnabled(context))
    }

    var isEditingLyrics by remember { mutableStateOf(false) }
    var showMixScreen by remember { mutableStateOf(false) }

    var rawLyricsText by remember(currentTrackUri) { mutableStateOf("") }
    var editingLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }

    var currentEditTab by remember { mutableStateOf(0) }

    // 🔁 À CHAQUE CHANGEMENT DE MORCEAU :
    // PlayerScreen recharge les paroles depuis LrcStorage
    LaunchedEffect(currentTrackUri) {
        if (currentTrackUri == null) {
            // Pas de titre actif → on vide tout
            onParsedLinesChange(emptyList())
            rawLyricsText = ""
            editingLines = emptyList()
            return@LaunchedEffect
        }

        // On lit le .lrc SAUVEGARDÉ pour ce morceau
        val stored = LrcStorage.loadForTrack(context, currentTrackUri)

        if (!stored.isNullOrBlank()) {
            val parsed = parseLrc(stored)

            // ✅ Ces lignes deviennent LA vérité pour ce track
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            editingLines = parsed
        } else {
            // Aucun fichier LRC → pas de paroles
            onParsedLinesChange(emptyList())
            rawLyricsText = ""
            editingLines = emptyList()
        }
    }

    // ---------- Suivi lecture + index ligne courante + MIDI + affichage ----------
    LaunchedEffect(isPlaying, parsedLines, userOffsetMs) {
        while (true) {
            val d = runCatching { mediaPlayer.duration }.getOrNull() ?: -1
            if (d > 0) durationMs = d

            val p = runCatching { mediaPlayer.currentPosition }.getOrNull() ?: 0
            if (!isDragging) positionMs = p

            if (parsedLines.isNotEmpty()) {
                // Décalage effectif = latence de base + offset utilisateur
                val totalOffsetMs = lyricsDelayMs + userOffsetMs

                // position "paroles" = position audio - décalage total
                val posMs = (p.toLong() - totalOffsetMs).coerceAtLeast(0L)

                // On repère toutes les lignes taguées
                val taggedIndices = parsedLines.withIndex()
                    .filter { it.value.timeMs > 0L }
                    .map { it.index }

                if (taggedIndices.isEmpty()) {
                    // Aucune ligne taguée → on laisse les paroles visibles dès le début
                    showLyrics = true
                } else {
                    // Il y a des tags → rien à l'écran avant la première ligne taguée
                    val firstTaggedIndex = taggedIndices.first()
                    val firstTaggedTime = parsedLines[firstTaggedIndex].timeMs
                    // posMs tient déjà compte du décalage global totalOffsetMs
                    showLyrics = posMs >= firstTaggedTime
                }

                // On cherche la DERNIÈRE ligne taguée dont le temps est <= posMs
                val newIndex = taggedIndices.lastOrNull { idx ->
                    parsedLines[idx].timeMs <= posMs
                } ?: -1

                if (newIndex != -1 && newIndex != currentLrcIndex) {
                    currentLrcIndex = newIndex
                }

                // 🔥 Déclenche le CUE MIDI quand la ligne change
                if (currentTrackUri != null && newIndex != -1 && newIndex != lastMidiIndex) {
                    lastMidiIndex = newIndex
                    MidiCueDispatcher.onActiveLineChanged(
                        trackUri = currentTrackUri,
                        lineIndex = newIndex
                    )
                }
            }

            delay(200)
            if (!isPlaying && !mediaPlayer.isPlaying) delay(200)
        }
    }

    // ---------- Autoswitch playlist (optionnel) ----------
    LaunchedEffect(durationMs, positionMs, hasRequestedPlaylist, currentTrackUri, isAutoReturnEnabled) {
        val enabled = isAutoReturnEnabled
        if (
            enabled &&
            !hasRequestedPlaylist &&
            durationMs > 0 &&
            positionMs >= durationMs - 10_000
        ) {
            hasRequestedPlaylist = true
            onRequestShowPlaylist()
        }
    }

    // ---------- Suivi scroll manuel ----------
    LaunchedEffect(scrollState) {
        while (true) {
            userScrolling = scrollState.isScrollInProgress
            delay(80)
        }
    }

    fun centerCurrentLineImmediate() {
        if (lyricsBoxHeightPx == 0) return
        val centerPx = lyricsBoxHeightPx / 2f
        val lineAbsY = baseTopSpacerPx + currentLrcIndex * lineHeightPx
        val wantedScroll = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
        scope.launch { scrollState.scrollTo(wantedScroll) }
    }

    fun seekAndCenter(targetMs: Int, targetIndex: Int) {
        PlaybackCoordinator.onPlayerStart()

        // On applique le même décalage que pour la lecture :
        // si on retarde les paroles, on seek un peu plus loin dans l'audio.
        val totalOffsetMs = lyricsDelayMs + userOffsetMs
        val seekPos = (targetMs.toLong() + totalOffsetMs)
            .coerceAtLeast(0L)
            .coerceAtMost(durationMs.toLong())
            .toInt()

        runCatching { mediaPlayer.seekTo(seekPos) }
        currentLrcIndex = targetIndex
        positionMs = seekPos

        if (!mediaPlayer.isPlaying) {
            // 🔊 Volume appliqué via le bus lecteur
            PlayerBusController.applyCurrentVolume(context)

            mediaPlayer.start()
            onIsPlayingChange(true)
        }
        if (lyricsBoxHeightPx > 0) {
            val centerPx = lyricsBoxHeightPx / 2f
            val lineAbsY = baseTopSpacerPx + targetIndex * lineHeightPx
            val wanted = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
            scope.launch { scrollState.scrollTo(wanted) }
        }
    }

    // ---------- Auto-centering pendant lecture ----------
    LaunchedEffect(isPlaying, parsedLines, lyricsBoxHeightPx) {
        if (parsedLines.isEmpty() || lyricsBoxHeightPx == 0) return@LaunchedEffect

        while (true) {
            if (isPlaying && !userScrolling && !isDragging) {
                centerCurrentLineImmediate()
            }
            delay(40)
        }
    }

    // ─────────────────────────────
    //  LAYOUT GLOBAL ANALOGIQUE
    // ─────────────────────────────

    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (isEditingLyrics) {
            // ========= MODE ÉDITION =========
            LyricsEditorSection(
                highlightColor = highlightColor,
                currentTrackUri = currentTrackUri,
                isEditingLyrics = isEditingLyrics,
                onCloseEditor = { isEditingLyrics = false },
                rawLyricsText = rawLyricsText,
                onRawLyricsTextChange = { rawLyricsText = it },
                editingLines = editingLines,
                onEditingLinesChange = { editingLines = it },
                currentEditTab = currentEditTab,
                onCurrentEditTabChange = { currentEditTab = it },
                mediaPlayer = mediaPlayer,
                positionMs = positionMs,
                durationMs = durationMs,
                onIsPlayingChange = onIsPlayingChange,
                onSaveSortedLines = { sorted ->
                    // Met à jour les états locaux pour CE morceau
                    rawLyricsText = sorted.joinToString("\n") { it.text }
                    editingLines = sorted

                    // Informe le parent : c'est cette liste qui sera utilisée en lecture
                    onParsedLinesChange(sorted)

                    // On ferme l’éditeur
                    isEditingLyrics = false
                }
            )

        } else {

            // ========= MODE LECTURE =========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B1B1B)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {

                        ReaderHeader(
                            isConcertMode = isConcertMode,
                            onToggleConcertMode = {
                                isConcertMode = !isConcertMode
                                DisplayPrefs.setConcertMode(context, isConcertMode)
                            },
                            autoReturnEnabled = isAutoReturnEnabled,
                            onToggleAutoReturn = {
                                val newValue = !isAutoReturnEnabled
                                isAutoReturnEnabled = newValue
                                AutoReturnPrefs.setEnabled(context, newValue)
                                // pour pouvoir redéclencher sur ce morceau
                                hasRequestedPlaylist = false
                            },
                            highlightColor = highlightColor,
                            onOpenMix = { showMixScreen = true },
                            onOpenEditor = {
                                if (parsedLines.isNotEmpty()) {
                                    rawLyricsText = parsedLines.joinToString("\n") { it.text }
                                    editingLines = parsedLines
                                } else {
                                    rawLyricsText = ""
                                    editingLines = emptyList()
                                }
                                currentEditTab = 0
                                isEditingLyrics = true
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Zone centrale : toujours mode MANU avec ligne centrée
                        Box(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            LyricsArea(
                                modifier = Modifier.fillMaxSize(),
                                scrollState = scrollState,
                                parsedLines = parsedLines,              // ⬅️ on repasse les vraies lignes
                                isContinuousScroll = false,
                                isConcertMode = isConcertMode,
                                currentLrcIndex = currentLrcIndex,
                                baseTopSpacerPx = baseTopSpacerPx,
                                lyricsBoxHeightPx = lyricsBoxHeightPx,
                                onLyricsBoxHeightChange = { lyricsBoxHeightPx = it },
                                highlightColor = highlightColor,
                                onLineClick = { index, timeMs ->
                                    // Seek + centrage
                                    seekAndCenter(timeMs.toInt(), index)

                                    // 🔥 Et on déclenche le CUE MIDI immédiatement au clic
                                    if (currentTrackUri != null) {
                                        lastMidiIndex = index
                                        MidiCueDispatcher.onActiveLineChanged(
                                            trackUri = currentTrackUri,
                                            lineIndex = index
                                        )
                                    }
                                }
                            )

                            // Masque opaque par-dessus tant qu'on n'a pas atteint la 1ère ligne taguée
                            if (!showLyrics) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color(0xFF1B1B1B))  // même couleur que la carte
                                )
                            }
                        }

                        TimeBar(
                            positionMs = if (isDragging) dragPosMs else positionMs,
                            durationMs = durationMs,
                            onSeekLivePreview = { newPos ->
                                isDragging = true
                                dragPosMs = newPos
                            },
                            onSeekCommit = { newPos ->
                                isDragging = false
                                val safe = min(max(newPos, 0), durationMs)
                                runCatching { mediaPlayer.seekTo(safe) }
                                positionMs = safe
                            },
                            highlightColor = highlightColor
                        )


                        PlayerControls(
                            isPlaying = isPlaying,
                            onPlayPause = {
                                if (mediaPlayer.isPlaying) {
                                    // Pause + bascule éventuelle vers le fond sonore
                                    pauseWithFade(scope, mediaPlayer, 400L) {
                                        onIsPlayingChange(false)
                                        PlaybackCoordinator.onFillerStart()
                                        runCatching { FillerSoundManager.startFromPlayerPause(context) }
                                    }
                                } else {
                                    if (durationMs > 0) {
                                        PlaybackCoordinator.onPlayerStart()

                                        // 🔊 Le bus applique le volume courant (fader LECTEUR)
                                        PlayerBusController.applyCurrentVolume(context)

                                        mediaPlayer.start()
                                        onIsPlayingChange(true)
                                        centerCurrentLineImmediate()
                                    }
                                }
                            },
                            onPrev = {
                                mediaPlayer.seekTo(0)
                                if (!mediaPlayer.isPlaying) {
                                    PlaybackCoordinator.onPlayerStart()

                                    // 🔊 Même chose quand on relance depuis le début
                                    PlayerBusController.applyCurrentVolume(context)

                                    mediaPlayer.start()
                                    onIsPlayingChange(true)
                                }
                                centerCurrentLineImmediate()
                            },
                            onNext = {
                                mediaPlayer.seekTo(max(durationMs - 1, 0))
                                mediaPlayer.pause()
                                onIsPlayingChange(false)
                                PlaybackCoordinator.onFillerStart()
                                runCatching { FillerSoundManager.startIfConfigured(context) }
                            }
                        )
                    }
                }
            }

            if (showMixScreen) {
                TrackMixScreen(
                    modifier = Modifier.fillMaxSize(),
                    highlightColor = highlightColor,
                    currentTrackGainDb = currentTrackGainDb,
                    onTrackGainChange = onTrackGainChange,
                    tempo = tempo,
                    onTempoChange = onTempoChange,
                    pitchSemi = pitchSemi,
                    onPitchSemiChange = onPitchSemiChange,
                    currentTrackUri = currentTrackUri,
                    onClose = { showMixScreen = false }
                )
            }
        }
    }
}

/* ─────────────────────────────
   HEADER LECTURE – STYLE CONSOLE
   ───────────────────────────── */

@Composable
private fun ReaderHeader(
    isConcertMode: Boolean,
    onToggleConcertMode: () -> Unit,
    autoReturnEnabled: Boolean,
    onToggleAutoReturn: () -> Unit,
    highlightColor: Color,
    onOpenMix: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF3A2C24),
                        Color(0xFF4B372A),
                        Color(0xFF3A2C24)
                    )
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                Color(0x55FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            IconButton(onClick = onToggleConcertMode) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Changer de style",
                    tint = if (isConcertMode) highlightColor else Color(0xFFCFD8DC)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bouton -10 (retour auto playlist)
            // Bouton -10 (retour auto playlist)
            IconButton(onClick = onToggleAutoReturn) {
                Text(
                    text = "-10",
                    color = if (autoReturnEnabled) Color.White else Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onOpenMix) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Mixage du titre",
                    tint = Color(0xFFFFC107)
                )
            }

            IconButton(onClick = onOpenEditor) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Éditer les paroles",
                    tint = Color(0xFFFFF3E0)
                )
            }
        }
    }
}