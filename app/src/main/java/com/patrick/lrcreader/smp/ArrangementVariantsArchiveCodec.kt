package com.patrick.lrcreader.smp

import org.json.JSONArray
import org.json.JSONObject

internal data class ArrangementVariantArchiveEntry(
    val id: String,
    val title: String,
    val arrangement: ArrangementData,
    val lyrics: String? = null,
    val chords: String? = null,
    val lyricsLineColors: Map<String, Int>? = null,
    val timeline: String? = null,
    val annotations: String? = null,
    val midiCues: String? = null,
    val dmxCues: String? = null,
    val grid: String? = null,
    val prompter: ArrangementVariantPrompterArchiveAsset? = null,
    val lyricsEditorRaw: String? = null,
    val customTitle: SmpConfig.CustomTitleContract? = null
)

internal data class ArrangementVariantPrompterArchiveAsset(
    val format: String,
    val content: String
)

internal data class ArrangementVariantsArchive(
    val sourceSongId: String,
    val variants: List<ArrangementVariantArchiveEntry>,
    val selectedVariantId: String? = null
)

internal object ArrangementVariantsArchiveCodec {
    const val FILE_NAME = "arrangement_variants.json"
    const val MAX_ARCHIVE_BYTES = 4 * 1024 * 1024

    private const val FORMAT = "smp_arrangement_variants"
    private const val VERSION = 1
    private const val MAX_VARIANTS = 250
    private const val MAX_LYRICS_BYTES = 1024 * 1024
    private const val MAX_CHORDS_BYTES = 1024 * 1024
    private const val MAX_TIMELINE_BYTES = 1024 * 1024
    private const val MAX_ANNOTATIONS_BYTES = 1024 * 1024
    private const val MAX_MIDI_CUES_BYTES = 1024 * 1024
    private const val MAX_DMX_CUES_BYTES = 1024 * 1024
    private const val MAX_GRID_BYTES = 64 * 1024
    internal const val MAX_PROMPTER_BYTES = 1024 * 1024
    internal const val MAX_LYRICS_EDITOR_RAW_BYTES = 1024 * 1024
    private const val MAX_LYRICS_LINE_COLORS = 10_000
    private val SAFE_ID = Regex("[A-Za-z0-9._-]+")

    fun encode(archive: ArrangementVariantsArchive): JSONObject {
        val sourceSongId = validateId(archive.sourceSongId, "sourceSongId")
        require(archive.variants.size <= MAX_VARIANTS) { "Too many Arrangement variants" }

        val seenIds = linkedSetOf<String>()
        val variantsJson = JSONArray()
        archive.variants
            .sortedBy(ArrangementVariantArchiveEntry::id)
            .forEach { variant ->
                val variantId = validateId(variant.id, "variant id")
                require(variantId != sourceSongId) { "Arrangement variant id matches its parent" }
                require(seenIds.add(variantId)) { "Duplicate Arrangement variant id" }
                val title = variant.title.trim().takeIf(String::isNotEmpty)
                    ?: throw IllegalArgumentException("Arrangement variant title is empty")
                validateArrangement(variant.arrangement, sourceSongId)
                val variantJson = JSONObject()
                        .put("id", variantId)
                        .put("title", title)
                        .put("arrangement", ArrangementJsonCodec.encode(variant.arrangement))
                variant.customTitle?.encodeInto(variantJson, "customTitle")
                encodeAssets(variant)?.let { assets ->
                    variantJson.put("assets", assets)
                }
                variantsJson.put(variantJson)
            }

        val selectedVariantId = archive.selectedVariantId?.let { rawId ->
            validateId(rawId, "selected variant id").also { selectedId ->
                require(selectedId in seenIds) {
                    "Selected Arrangement variant is missing from archive"
                }
            }
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("sourceSongId", sourceSongId)
            .put("variants", variantsJson)
            .apply {
                selectedVariantId?.let { put("selectedVariantId", it) }
            }
            .also { encoded ->
                require(encoded.toString().toByteArray(Charsets.UTF_8).size <= MAX_ARCHIVE_BYTES) {
                    "Arrangement variants archive is too large"
                }
            }
    }

    fun decode(json: JSONObject): ArrangementVariantsArchive {
        require(json.optString("format") == FORMAT) { "Unsupported Arrangement variants format" }
        require(json.optInt("version", -1) == VERSION) { "Unsupported Arrangement variants version" }

        val sourceSongId = validateId(json.optString("sourceSongId"), "sourceSongId")
        val variantsJson = json.optJSONArray("variants")
            ?: throw IllegalArgumentException("Missing Arrangement variants list")
        require(variantsJson.length() <= MAX_VARIANTS) { "Too many Arrangement variants" }

        val seenIds = linkedSetOf<String>()
        val variants = buildList {
            for (index in 0 until variantsJson.length()) {
                val item = variantsJson.optJSONObject(index)
                    ?: throw IllegalArgumentException("Invalid Arrangement variant")
                val variantId = validateId(item.optString("id"), "variant id")
                require(variantId != sourceSongId) { "Arrangement variant id matches its parent" }
                require(seenIds.add(variantId)) { "Duplicate Arrangement variant id" }
                val title = item.optString("title").trim().takeIf(String::isNotEmpty)
                    ?: throw IllegalArgumentException("Arrangement variant title is empty")
                val arrangementJson = item.optJSONObject("arrangement")
                    ?: throw IllegalArgumentException("Missing Arrangement variant structure")
                val arrangement = ArrangementJsonCodec.decode(arrangementJson)
                validateArrangement(arrangement, sourceSongId)
                val assets = decodeAssets(item.optJSONObject("assets"))
                add(
                    ArrangementVariantArchiveEntry(
                        id = variantId,
                        title = title,
                        arrangement = arrangement,
                        lyrics = assets?.lyrics,
                        chords = assets?.chords,
                        lyricsLineColors = assets?.lyricsLineColors,
                        timeline = assets?.timeline,
                        annotations = assets?.annotations,
                        midiCues = assets?.midiCues,
                        dmxCues = assets?.dmxCues,
                        grid = assets?.grid,
                        prompter = assets?.prompter,
                        lyricsEditorRaw = assets?.lyricsEditorRaw,
                        customTitle = SmpConfig.CustomTitleContract.decodeFrom(
                            item,
                            "customTitle"
                        )
                    )
                )
            }
        }
        val selectedVariantId = json.optString("selectedVariantId")
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { rawId ->
                validateId(rawId, "selected variant id").also { selectedId ->
                    require(variants.any { it.id == selectedId }) {
                        "Selected Arrangement variant is missing from archive"
                    }
                }
            }

        return ArrangementVariantsArchive(
            sourceSongId = sourceSongId,
            variants = variants,
            selectedVariantId = selectedVariantId
        )
    }

    private fun validateId(rawId: String, label: String): String {
        val id = rawId.trim()
        require(id.isNotEmpty() && id.length <= 200 && SAFE_ID.matches(id)) { "Invalid $label" }
        return id
    }

    private fun validateArrangement(arrangement: ArrangementData, sourceSongId: String) {
        require(arrangement.sourceSongId.trim() == sourceSongId) {
            "Arrangement variant source does not match its parent"
        }
        require(arrangement.entries.isNotEmpty() || arrangement.structureSegmentIds.isNotEmpty()) {
            "Arrangement variant structure is empty"
        }
    }

    private data class VariantAssets(
        val lyrics: String?,
        val chords: String?,
        val lyricsLineColors: Map<String, Int>?,
        val timeline: String?,
        val annotations: String?,
        val midiCues: String?,
        val dmxCues: String?,
        val grid: String?,
        val prompter: ArrangementVariantPrompterArchiveAsset?,
        val lyricsEditorRaw: String?
    )

    private fun encodeAssets(variant: ArrangementVariantArchiveEntry): JSONObject? {
        val lyrics = variant.lyrics?.also(::validateLyrics)
        val chords = variant.chords?.also(::validateChords)
        val lyricsLineColors = variant.lyricsLineColors?.also(::validateLyricsLineColors)
        val timeline = variant.timeline?.also(::validateTimeline)
        val annotations = variant.annotations?.also(::validateAnnotations)
        val midiCues = variant.midiCues?.also(::validateMidiCues)
        val dmxCues = variant.dmxCues?.also(::validateDmxCues)
        val grid = variant.grid?.also(::validateGrid)
        val prompter = variant.prompter?.also(::validatePrompter)
        val lyricsEditorRaw = variant.lyricsEditorRaw?.also(::validateLyricsEditorRaw)
        if (
            lyrics == null &&
            chords == null &&
            lyricsLineColors == null &&
            timeline == null &&
            annotations == null &&
            midiCues == null &&
            dmxCues == null &&
            grid == null &&
            prompter == null &&
            lyricsEditorRaw == null
        ) {
            return null
        }
        return JSONObject().apply {
            lyrics?.let { put("lyrics", it) }
            chords?.let { put("chords", it) }
            timeline?.let { put("timeline", it) }
            annotations?.let { put("annotations", it) }
            midiCues?.let { put("midiCues", it) }
            dmxCues?.let { put("dmxCues", it) }
            grid?.let { put("grid", it) }
            prompter?.let { asset ->
                put(
                    "prompter",
                    JSONObject()
                        .put("format", asset.format)
                        .put("content", asset.content)
                )
            }
            lyricsEditorRaw?.let { put("lyricsEditorRaw", it) }
            lyricsLineColors?.let { colors ->
                put(
                    "lyricsLineColors",
                    JSONObject().apply {
                        colors.keys.sorted().forEach { key ->
                            put(key, colors.getValue(key))
                        }
                    }
                )
            }
        }
    }

    private fun decodeAssets(json: JSONObject?): VariantAssets? {
        json ?: return null
        val lyrics = if (json.has("lyrics") && !json.isNull("lyrics")) {
            json.getString("lyrics").also(::validateLyrics)
        } else {
            null
        }
        val chords = if (json.has("chords") && !json.isNull("chords")) {
            json.getString("chords").also(::validateChords)
        } else {
            null
        }
        val lyricsLineColorsJson = json.optJSONObject("lyricsLineColors")
        val lyricsLineColors = lyricsLineColorsJson?.let { colorsJson ->
            buildMap {
                colorsJson.keys().forEach { key ->
                    put(key, colorsJson.getInt(key))
                }
            }.also(::validateLyricsLineColors)
        }
        val timeline = if (json.has("timeline") && !json.isNull("timeline")) {
            json.getString("timeline").also(::validateTimeline)
        } else {
            null
        }
        val annotations = if (json.has("annotations") && !json.isNull("annotations")) {
            json.getString("annotations").also(::validateAnnotations)
        } else {
            null
        }
        val midiCues = if (json.has("midiCues") && !json.isNull("midiCues")) {
            json.getString("midiCues").also(::validateMidiCues)
        } else {
            null
        }
        val dmxCues = if (json.has("dmxCues") && !json.isNull("dmxCues")) {
            json.getString("dmxCues").also(::validateDmxCues)
        } else {
            null
        }
        val grid = if (json.has("grid") && !json.isNull("grid")) {
            json.getString("grid").also(::validateGrid)
        } else {
            null
        }
        val prompter = if (json.has("prompter") && !json.isNull("prompter")) {
            val prompterJson = json.getJSONObject("prompter")
            ArrangementVariantPrompterArchiveAsset(
                format = prompterJson.getString("format"),
                content = prompterJson.getString("content")
            ).also(::validatePrompter)
        } else {
            null
        }
        val lyricsEditorRaw = if (json.has("lyricsEditorRaw") && !json.isNull("lyricsEditorRaw")) {
            json.getString("lyricsEditorRaw").also(::validateLyricsEditorRaw)
        } else {
            null
        }
        return VariantAssets(
            lyrics = lyrics,
            chords = chords,
            lyricsLineColors = lyricsLineColors,
            timeline = timeline,
            annotations = annotations,
            midiCues = midiCues,
            dmxCues = dmxCues,
            grid = grid,
            prompter = prompter,
            lyricsEditorRaw = lyricsEditorRaw
        )
    }

    private fun validateLyrics(lyrics: String) {
        require(lyrics.toByteArray(Charsets.UTF_8).size <= MAX_LYRICS_BYTES) {
            "Arrangement variant lyrics are too large"
        }
    }

    private fun validateChords(chords: String) {
        require(chords.toByteArray(Charsets.UTF_8).size <= MAX_CHORDS_BYTES) {
            "Arrangement variant chords are too large"
        }
    }

    private fun validateTimeline(timeline: String) {
        require(timeline.toByteArray(Charsets.UTF_8).size <= MAX_TIMELINE_BYTES) {
            "Arrangement variant timeline is too large"
        }
    }

    private fun validateAnnotations(annotations: String) {
        require(annotations.toByteArray(Charsets.UTF_8).size <= MAX_ANNOTATIONS_BYTES) {
            "Arrangement variant annotations are too large"
        }
    }

    private fun validateMidiCues(midiCues: String) {
        require(midiCues.toByteArray(Charsets.UTF_8).size <= MAX_MIDI_CUES_BYTES) {
            "Arrangement variant MIDI cues are too large"
        }
    }

    private fun validateDmxCues(dmxCues: String) {
        require(dmxCues.toByteArray(Charsets.UTF_8).size <= MAX_DMX_CUES_BYTES) {
            "Arrangement variant DMX cues are too large"
        }
    }

    private fun validateGrid(grid: String) {
        require(grid.toByteArray(Charsets.UTF_8).size <= MAX_GRID_BYTES) {
            "Arrangement variant grid is too large"
        }
    }

    private fun validatePrompter(prompter: ArrangementVariantPrompterArchiveAsset) {
        require(prompter.format == "txt" || prompter.format == "json") {
            "Unsupported Arrangement variant prompter format"
        }
        require(prompter.content.toByteArray(Charsets.UTF_8).size <= MAX_PROMPTER_BYTES) {
            "Arrangement variant prompter is too large"
        }
    }

    private fun validateLyricsEditorRaw(rawText: String) {
        require(rawText.toByteArray(Charsets.UTF_8).size <= MAX_LYRICS_EDITOR_RAW_BYTES) {
            "Arrangement variant raw lyrics draft is too large"
        }
    }

    private fun validateLyricsLineColors(colors: Map<String, Int>) {
        require(colors.size <= MAX_LYRICS_LINE_COLORS) {
            "Too many Arrangement variant lyrics line colors"
        }
        require(colors.keys.none(String::isBlank)) {
            "Invalid Arrangement variant lyrics line color key"
        }
    }
}
