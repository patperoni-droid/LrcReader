package com.patrick.lrcreader.smp

import org.json.JSONArray
import org.json.JSONObject

internal data class ArrangementVariantArchiveEntry(
    val id: String,
    val title: String,
    val arrangement: ArrangementData,
    val lyrics: String? = null,
    val chords: String? = null,
    val lyricsLineColors: Map<String, Int>? = null
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
                        lyricsLineColors = assets?.lyricsLineColors
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
        val lyricsLineColors: Map<String, Int>?
    )

    private fun encodeAssets(variant: ArrangementVariantArchiveEntry): JSONObject? {
        val lyrics = variant.lyrics?.also(::validateLyrics)
        val chords = variant.chords?.also(::validateChords)
        val lyricsLineColors = variant.lyricsLineColors?.also(::validateLyricsLineColors)
        if (lyrics == null && chords == null && lyricsLineColors == null) {
            return null
        }
        return JSONObject().apply {
            lyrics?.let { put("lyrics", it) }
            chords?.let { put("chords", it) }
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
        return VariantAssets(
            lyrics = lyrics,
            chords = chords,
            lyricsLineColors = lyricsLineColors
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

    private fun validateLyricsLineColors(colors: Map<String, Int>) {
        require(colors.size <= MAX_LYRICS_LINE_COLORS) {
            "Too many Arrangement variant lyrics line colors"
        }
        require(colors.keys.none(String::isBlank)) {
            "Invalid Arrangement variant lyrics line color key"
        }
    }
}
