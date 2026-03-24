package com.patrick.lrcreader.core.light

object LightCueAutoGenerator {

    enum class Style {
        SOFT,
        STANDARD,
        ENERGETIC
    }

    private const val COLOR_BLUE_ARGB = 0xFF2E5BFFL
    private const val COLOR_AMBER_ARGB = 0xFFFF7043L
    private const val COLOR_WHITE_ARGB = 0xFFF5F7FFL
    private const val COLOR_VIOLET_ARGB = 0xFF7E57C2L
    private const val COLOR_SOFT_GREEN_ARGB = 0xFF66BB6AL

    private val SEGMENT_VARIATION_PATTERN = listOf(1.0, 0.86, 1.14, 0.94, 1.08)

    fun generate(
        durationMs: Long,
        style: Style
    ): List<LightCue> {
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        if (safeDurationMs <= 0L) {
            return emptyList()
        }

        val profile = profileFor(style)
        val blackoutFadeMs = profile.blackoutFadeMs
            .coerceAtLeast(800L)
            .coerceAtMost(safeDurationMs.coerceAtLeast(800L))
        val blackoutTimeMs = (safeDurationMs - blackoutFadeMs).coerceAtLeast(0L)
        val musicalWindowMs = blackoutTimeMs.coerceAtLeast(1L)

        val generated = mutableListOf<LightCue>()
        var currentTimeMs = 0L
        var segmentIndex = 0
        while (currentTimeMs < blackoutTimeMs) {
            val progress = if (blackoutTimeMs <= 0L) 1f else currentTimeMs.toFloat() / blackoutTimeMs.toFloat()
            val phase = when {
                progress < 0.24f -> profile.intro
                progress < 0.78f -> profile.mid
                else -> profile.outro
            }
            generated += LightCue(
                timeMs = currentTimeMs,
                action = LightAction.Color(argb = phase.paletteArgb[segmentIndex % phase.paletteArgb.size]),
                intensity = phase.intensityPattern[segmentIndex % phase.intensityPattern.size],
                fadeMs = phase.fadePatternMs[segmentIndex % phase.fadePatternMs.size]
            )

            val baseStepMs = phase.baseSegmentMs
            val variation = SEGMENT_VARIATION_PATTERN[segmentIndex % SEGMENT_VARIATION_PATTERN.size]
            val stepMs = (baseStepMs * variation).toLong().coerceAtLeast(profile.minStepMs)
            currentTimeMs = (currentTimeMs + stepMs).coerceAtMost(blackoutTimeMs)
            segmentIndex += 1

            if (segmentIndex > 128) {
                break
            }
        }

        generated += LightCue(
            timeMs = blackoutTimeMs,
            action = LightAction.Blackout,
            intensity = 1f,
            fadeMs = blackoutFadeMs
        )

        val cuesByTimeMs = linkedMapOf<Long, LightCue>()
        generated
            .asSequence()
            .map { cue ->
                cue.copy(
                    timeMs = cue.timeMs.coerceIn(0L, musicalWindowMs + blackoutFadeMs),
                    intensity = cue.intensity.coerceIn(0f, 1f),
                    fadeMs = cue.fadeMs.coerceAtLeast(0L)
                )
            }
            .sortedBy { cue -> cue.timeMs }
            .forEach { cue ->
                cuesByTimeMs[cue.timeMs] = cue
            }
        return cuesByTimeMs.values.toList()
    }

    private fun profileFor(style: Style): GenerationProfile {
        return when (style) {
            Style.SOFT -> GenerationProfile(
                minStepMs = 10_000L,
                blackoutFadeMs = 3_800L,
                intro = PhaseProfile(
                    baseSegmentMs = 24_000L,
                    paletteArgb = listOf(COLOR_BLUE_ARGB, COLOR_VIOLET_ARGB, COLOR_WHITE_ARGB),
                    intensityPattern = listOf(0.28f, 0.34f, 0.3f),
                    fadePatternMs = listOf(6_000L, 5_200L, 5_600L)
                ),
                mid = PhaseProfile(
                    baseSegmentMs = 20_000L,
                    paletteArgb = listOf(COLOR_BLUE_ARGB, COLOR_VIOLET_ARGB, COLOR_AMBER_ARGB, COLOR_WHITE_ARGB),
                    intensityPattern = listOf(0.38f, 0.44f, 0.4f, 0.46f),
                    fadePatternMs = listOf(4_800L, 4_200L, 4_600L, 4_000L)
                ),
                outro = PhaseProfile(
                    baseSegmentMs = 18_000L,
                    paletteArgb = listOf(COLOR_VIOLET_ARGB, COLOR_BLUE_ARGB, COLOR_WHITE_ARGB),
                    intensityPattern = listOf(0.42f, 0.36f, 0.32f),
                    fadePatternMs = listOf(4_200L, 4_800L, 5_400L)
                )
            )
            Style.STANDARD -> GenerationProfile(
                minStepMs = 8_000L,
                blackoutFadeMs = 2_800L,
                intro = PhaseProfile(
                    baseSegmentMs = 20_000L,
                    paletteArgb = listOf(COLOR_BLUE_ARGB, COLOR_AMBER_ARGB, COLOR_WHITE_ARGB),
                    intensityPattern = listOf(0.42f, 0.5f, 0.46f),
                    fadePatternMs = listOf(4_200L, 3_400L, 3_800L)
                ),
                mid = PhaseProfile(
                    baseSegmentMs = 16_000L,
                    paletteArgb = listOf(COLOR_BLUE_ARGB, COLOR_AMBER_ARGB, COLOR_VIOLET_ARGB, COLOR_WHITE_ARGB),
                    intensityPattern = listOf(0.58f, 0.66f, 0.62f, 0.7f),
                    fadePatternMs = listOf(2_600L, 2_200L, 2_800L, 2_400L)
                ),
                outro = PhaseProfile(
                    baseSegmentMs = 14_000L,
                    paletteArgb = listOf(COLOR_AMBER_ARGB, COLOR_BLUE_ARGB, COLOR_WHITE_ARGB),
                    intensityPattern = listOf(0.62f, 0.56f, 0.48f),
                    fadePatternMs = listOf(2_600L, 3_000L, 3_400L)
                )
            )
            Style.ENERGETIC -> GenerationProfile(
                minStepMs = 6_000L,
                blackoutFadeMs = 2_000L,
                intro = PhaseProfile(
                    baseSegmentMs = 16_000L,
                    paletteArgb = listOf(COLOR_AMBER_ARGB, COLOR_BLUE_ARGB, COLOR_WHITE_ARGB),
                    intensityPattern = listOf(0.55f, 0.62f, 0.58f),
                    fadePatternMs = listOf(2_400L, 2_000L, 2_200L)
                ),
                mid = PhaseProfile(
                    baseSegmentMs = 12_000L,
                    paletteArgb = listOf(COLOR_AMBER_ARGB, COLOR_BLUE_ARGB, COLOR_WHITE_ARGB, COLOR_VIOLET_ARGB, COLOR_SOFT_GREEN_ARGB),
                    intensityPattern = listOf(0.72f, 0.82f, 0.78f, 0.86f, 0.74f),
                    fadePatternMs = listOf(1_600L, 1_200L, 1_400L, 1_000L, 1_500L)
                ),
                outro = PhaseProfile(
                    baseSegmentMs = 11_000L,
                    paletteArgb = listOf(COLOR_WHITE_ARGB, COLOR_AMBER_ARGB, COLOR_BLUE_ARGB),
                    intensityPattern = listOf(0.8f, 0.68f, 0.54f),
                    fadePatternMs = listOf(1_200L, 1_600L, 2_000L)
                )
            )
        }
    }

    private data class GenerationProfile(
        val minStepMs: Long,
        val blackoutFadeMs: Long,
        val intro: PhaseProfile,
        val mid: PhaseProfile,
        val outro: PhaseProfile
    )

    private data class PhaseProfile(
        val baseSegmentMs: Long,
        val paletteArgb: List<Long>,
        val intensityPattern: List<Float>,
        val fadePatternMs: List<Long>
    )
}
