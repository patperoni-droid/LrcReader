package com.patrick.lrcreader.smp

import org.json.JSONArray
import org.json.JSONObject

internal data class ArrangementVariantArchiveEntry(
    val id: String,
    val title: String,
    val arrangement: ArrangementData
)

internal data class ArrangementVariantsArchive(
    val sourceSongId: String,
    val variants: List<ArrangementVariantArchiveEntry>
)

internal object ArrangementVariantsArchiveCodec {
    const val FILE_NAME = "arrangement_variants.json"

    private const val FORMAT = "smp_arrangement_variants"
    private const val VERSION = 1
    private const val MAX_VARIANTS = 250
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
                variantsJson.put(
                    JSONObject()
                        .put("id", variantId)
                        .put("title", title)
                        .put("arrangement", ArrangementJsonCodec.encode(variant.arrangement))
                )
            }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("sourceSongId", sourceSongId)
            .put("variants", variantsJson)
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
                add(
                    ArrangementVariantArchiveEntry(
                        id = variantId,
                        title = title,
                        arrangement = arrangement
                    )
                )
            }
        }

        return ArrangementVariantsArchive(
            sourceSongId = sourceSongId,
            variants = variants
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
}
