package com.patrick.lrcreader.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.TextPrompterPrefs
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import com.patrick.lrcreader.ui.theme.SplColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import java.util.Locale

private data class SongInfo(
    val title: String?,
    val content: String?
)

enum class PrompterAction {
    NEXT,
    PREV,
    TOGGLE,
    HOME,
    END
}

internal fun mapPrompterKey(nativeKeyCode: Int): PrompterAction? = when (nativeKeyCode) {
    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> PrompterAction.NEXT

    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> PrompterAction.PREV

    AndroidKeyEvent.KEYCODE_MOVE_HOME -> PrompterAction.HOME
    AndroidKeyEvent.KEYCODE_MOVE_END -> PrompterAction.END
    else -> null
}

internal fun mapPrompterKey(event: KeyEvent): PrompterAction? =
    mapPrompterKey(event.nativeKeyEvent.keyCode)

internal data class PrompterKeyHandlingDecision(
    val consumed: Boolean,
    val actionToDispatch: PrompterAction?
)

internal fun resolvePrompterKeyHandling(
    eventType: KeyEventType,
    action: PrompterAction?
): PrompterKeyHandlingDecision {
    if (action == null) {
        return PrompterKeyHandlingDecision(
            consumed = false,
            actionToDispatch = null
        )
    }
    return if (eventType == KeyEventType.KeyDown) {
        PrompterKeyHandlingDecision(
            consumed = true,
            actionToDispatch = action
        )
    } else {
        PrompterKeyHandlingDecision(
            consumed = true,
            actionToDispatch = null
        )
    }
}

@Composable
fun TextPrompterScreen(
    modifier: Modifier = Modifier,
    songId: String,
    onClose: () -> Unit,
    hardwareActionToken: Int = 0,
    hardwareAction: PrompterAction = PrompterAction.TOGGLE,
    tabletSplitLayout: Boolean = false
) {
    val context = LocalContext.current

    val songInfo = remember(songId) {
        var result = SongInfo(title = null, content = null)
        try {
            when {
                songId.startsWith("note:") -> {
                    val raw = songId.removePrefix("note:")
                    val idLong = raw.toLongOrNull()
                    if (idLong != null) {
                        val note = NotesRepository.get(context, idLong)
                        if (note != null) {
                            result = SongInfo(
                                title = note.title.ifBlank { "Texte" },
                                content = note.content
                            )
                        }
                    }
                }

                songId.startsWith("text:") -> {
                    val raw = songId.removePrefix("text:")
                    val s = TextSongRepository.get(context, raw)
                    if (s != null) result = SongInfo(title = s.title, content = s.content)
                }

                else -> {
                    val numeric = songId.toLongOrNull()
                    if (numeric != null) {
                        val note = NotesRepository.get(context, numeric)
                        if (note != null) {
                            result = SongInfo(
                                title = note.title.ifBlank { "Texte" },
                                content = note.content
                            )
                        } else {
                            val s = TextSongRepository.get(context, songId)
                            if (s != null) result = SongInfo(title = s.title, content = s.content)
                        }
                    } else {
                        val s = TextSongRepository.get(context, songId)
                        if (s != null) result = SongInfo(title = s.title, content = s.content)
                    }
                }
            }
        } catch (_: Exception) {}
        result
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val hostView = LocalView.current

    var isPlaying by remember { mutableStateOf(false) }
    var prompterRootHasFocus by remember { mutableStateOf(false) }
    var isSpeedSliderOpen by remember { mutableStateOf(false) }
    val minSpeed = 0.10f
    val maxSpeed = 1.40f // + rapide possible → marge en haut

    fun scrollByStep(direction: Int) {
        scope.launch {
            val step = (scrollState.maxValue * 0.08f).toInt().coerceAtLeast(80)
            val target = when {
                direction < 0 -> (scrollState.value - step).coerceAtLeast(0)
                else -> (scrollState.value + step).coerceAtMost(scrollState.maxValue)
            }
            scrollState.animateScrollTo(target)
        }
    }

    val onTogglePlayPause: () -> Unit = { isPlaying = !isPlaying }
    val onPrevStep: () -> Unit = { scrollByStep(direction = -1) }
    val onNextStep: () -> Unit = { scrollByStep(direction = 1) }
    val onJumpToStart: () -> Unit = {
        scope.launch { scrollState.animateScrollTo(0) }
    }
    val onJumpToEnd: () -> Unit = {
        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
    }

    fun dispatchPrompterAction(action: PrompterAction) {
        when (action) {
            PrompterAction.NEXT -> onNextStep()
            PrompterAction.PREV -> onPrevStep()
            PrompterAction.TOGGLE -> onTogglePlayPause()
            PrompterAction.HOME -> onJumpToStart()
            PrompterAction.END -> onJumpToEnd()
        }
    }

    var speedFactor by remember(songId) {
        mutableStateOf(
            (TextPrompterPrefs.getSpeed(context, songId) ?: 1f)
                .coerceIn(minSpeed, maxSpeed)
        )
    }
    // ✅ Slider “linéaire” 0..1 (ce que l’utilisateur bouge)
    var speedSlider by remember(songId) {
        mutableStateOf(
            ((speedFactor - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)
        )
    }
    fun sliderToSpeed(slider: Float, min: Float, max: Float): Float {
        val t = slider.coerceIn(0f, 1f)
        val expo = t * t * t   // ✅ cubic = ÉNORMÉMENT plus de marge en bas
        return min + expo * (max - min)
    }
    // ✅ Auto scroll
    LaunchedEffect(songId, isPlaying, speedSlider) {
        if (!isPlaying) return@LaunchedEffect
        delay(50)

        val max = scrollState.maxValue
        if (max <= 0) return@LaunchedEffect

// vitesse issue du slider (ta fonction existante)
        val clampedSpeed = sliderToSpeed(speedSlider, minSpeed, maxSpeed)

// ✅ VITESSE RÉELLE (indépendante de l’écran)
        val basePxPerSec = 220f   // ← réglage clé (à ajuster UNE fois)
        val pxPerSec = basePxPerSec * clampedSpeed

// durée = distance / vitesse
        val duration = ((max / pxPerSec) * 1000f)
            .toInt()
            .coerceAtLeast(500)

        delay(1)

        scrollState.animateScrollTo(
            value = max,
            animationSpec = tween(
                durationMillis = duration,
                easing = LinearEasing
            )
        )
    }
    LaunchedEffect(Unit) {
        hostView.isFocusableInTouchMode = true
        hostView.requestFocus()
        focusRequester.requestFocus()
    }

    LaunchedEffect(hardwareActionToken) {
        if (hardwareActionToken == 0) return@LaunchedEffect
        if (!prompterRootHasFocus) {
            hostView.isFocusableInTouchMode = true
            hostView.requestFocus()
            focusRequester.requestFocus()
        }
        dispatchPrompterAction(hardwareAction)
    }

    DarkBlueGradientBackground {




        // ✅ Insets (base propre)
        val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

        val transportBottom = safeBottom + 0.dp   // <- seul réglage “look”
        val transportHeight = 72.dp

// IMPORTANT : transportNudgeY = 0 (ou tu le vires)
        val transportNudgeY = if (tabletSplitLayout) 0.dp else 50.dp
        // ✅ Vitre : élargissement gauche/droite
        val glassOverhangLeft = 30.dp
        val glassOverhangRight = 30.dp

        // ✅ Slider tiroir : dimensions
        val sliderHeight = 450.dp
        val sliderWidth = 60.dp
        val overhangRight = 18.dp
        val sliderControlsShiftX = (-60).dp
        // ✅ Réglage fin : déplacement du MÉCANISME (slider + bouton) vers la gauche
        val mechanismNudgeX = (-16).dp

        // ✅ Réglages fins : bloc slider
        val blockOffsetY = (-20).dp
        val blockPaddingEnd = 10.dp

        // ✅ Réglages fins : bouton slider
        val buttonOffsetX = 30.dp
        val buttonOffsetY = -0.dp
        val speedLabel = String.format(Locale.US, "%.1f", speedFactor)

        Box(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { prompterRootHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    val decision = resolvePrompterKeyHandling(
                        eventType = event.type,
                        action = mapPrompterKey(event)
                    )
                    if (!decision.consumed) return@onPreviewKeyEvent false
                    if (!prompterRootHasFocus) {
                        focusRequester.requestFocus()
                    }

                    decision.actionToDispatch?.let(::dispatchPrompterAction)
                    true
                }
        ) {

            // 1) TEXTE plein écran
            PrompterTextViewport(
                content = songInfo.content.orEmpty(),
                scrollState = scrollState,
                startOffsetFraction = 0.55f,
                bottomOffsetFraction = 0.30f,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
            )

            // 2) PROGRESS (calculs)
            val progress =
                if (scrollState.maxValue > 0)
                    scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                else 0f

            val speed = speedFactor.coerceIn(0.3f, 3f)
            val totalMs = (60_000f / speed).toLong().coerceAtLeast(500L)
            val currentMs = (progress * totalMs.toFloat()).toLong().coerceIn(0L, totalMs)

            fun formatMs(ms: Long): String {
                val totalSec = (ms / 1000L).toInt()
                val m = totalSec / 60
                val s = totalSec % 60
                return "%d:%02d".format(m, s)
            }

            // ✅ VITRE (derrière progress + transport)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .wrapContentWidth(unbounded = true)
                    .padding(bottom = transportBottom)
                    .offset(
                        x = (glassOverhangRight - glassOverhangLeft) / 2,
                        y = transportNudgeY
                    )
                    .width(
                        LocalConfiguration.current.screenWidthDp.dp +
                                glassOverhangLeft + glassOverhangRight
                    )
                    .height(transportHeight + 44.dp)
                    .zIndex(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(
                        2.dp,
                        Color.White.copy(alpha = 0.22f),
                        RoundedCornerShape(16.dp)
                    )
            )

// ✅ PROGRESS AU-DESSUS de la vitre
            Row(
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = transportBottom + transportHeight + 2.dp
                    )
                    .offset(y = transportNudgeY),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs(currentMs),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )

                Slider(
                    value = progress,
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        disabledThumbColor = Color.White.copy(alpha = 0.9f),
                        disabledActiveTrackColor = Color.White.copy(alpha = 0.8f),
                        disabledInactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                Text(
                    text = formatMs(totalMs),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }

// ✅ TRANSPORT AU-DESSUS de la vitre
            PrompterTransportBarAudioLike(
                isPlaying = isPlaying,
                onPlayPause = onTogglePlayPause,
                onPrev = onPrevStep,
                onNext = onNextStep,
                onReturnToStart = onJumpToStart,
                showReturnToStart = tabletSplitLayout,
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = transportBottom
                    )
                    .offset(y = transportNudgeY)
            )

            // 4) SLIDER + BOUTON (bloc complet)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = blockPaddingEnd)
                    .offset(
                        x = (0).dp,   // 👈 recule tout le bloc vers la gauche
                        y = blockOffsetY
                    )
                    .zIndex(9999f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ✅ Zone réservée : le bouton ne bouge jamais (place fixe)
                Box(
                    modifier = Modifier
                        .height(sliderHeight)
                        .width(sliderWidth + overhangRight + 9.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // ✅ “Tiroir” : le slider glisse / disparaît mais la place reste
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSpeedSliderOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        VerticalTransparentSpeedSlider(
                            value = speedSlider,
                            onValueChange = { new01 ->
                                speedSlider = new01.coerceIn(0f, 1f)

                                // ✅ vitesse réelle (celle qui sert au défilement)
                                speedFactor = sliderToSpeed(speedSlider, minSpeed, maxSpeed)

                                // ✅ on sauvegarde la vitesse réelle (comme avant, donc pas de casse)
                                TextPrompterPrefs.saveSpeed(context, songId, speedFactor)
                            },
                            valueRange = (0f..1f),
                            height = sliderHeight,
                            width = sliderWidth,
                            trackThickness = 4.dp,
                            trackVerticalPadding = 24.dp,
                            trackColor = SplColors.Outline.copy(alpha = 0.35f),
                            filledTrackColor = SplColors.Accent.copy(alpha = 0.78f),
                            centeredFilledTrack = true,
                            thumbColor = Color.White.copy(alpha = 0.94f),
                            thumbShadowElevation = 4.dp,
                            thumbContent = {
                                Text(
                                    text = speedLabel,
                                    color = Color(0xFF111111),
                                    fontSize = 11.sp
                                )
                            },
                            bottomLabel = stringResource(R.string.track_mix_speed),
                            bottomLabelColor = SplColors.SubText.copy(alpha = 0.90f),
                            overhangRight = overhangRight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ✅ Bouton (position stable)
                FilledTonalIconButton(
                    onClick = { isSpeedSliderOpen = !isSpeedSliderOpen },
                    modifier = Modifier
                        .size(40.dp)
                        .offset(x = buttonOffsetX, y = buttonOffsetY)
                ) {
                    Icon(
                        imageVector = if (isSpeedSliderOpen)
                            Icons.Filled.KeyboardArrowRight
                        else
                            Icons.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.common_cd_toggle_slider)
                    )
                }
            }
        }
    }
}
