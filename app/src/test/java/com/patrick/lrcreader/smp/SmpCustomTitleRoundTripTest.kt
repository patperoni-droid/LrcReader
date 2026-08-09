package com.patrick.lrcreader.smp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmpCustomTitleRoundTripTest {

    @Test
    fun parentAlias_roundTripsSeparatelyFromStructuralTitle() {
        val decoded = roundTripParent("Titre parent", contract("Alias parent"))

        assertEquals("Titre parent", decoded.title)
        assertEquals("Alias parent", decoded.customTitle?.value)
    }

    @Test
    fun parentExplicitNoAlias_roundTripsAsJsonNull() {
        val encoded = parentConfig("Titre parent", contract(null)).toJson()
        val decoded = requireNotNull(SmpConfig.fromJsonOrNull(encoded.toString()))

        assertTrue(encoded.has(CUSTOM_TITLE_KEY))
        assertTrue(encoded.isNull(CUSTOM_TITLE_KEY))
        assertEquals(contract(null), decoded.customTitle)
    }

    @Test
    fun legacyParentArchive_withoutContractRemainsDistinguishable() {
        val decoded = requireNotNull(
            SmpConfig.fromJsonOrNull(
                JSONObject().put("id", PARENT_ID).put("title", "Titre parent").toString()
            )
        )

        assertNull(decoded.customTitle)
    }

    @Test
    fun blankParentAlias_usesExistingStoreClearSemantics() {
        val decoded = requireNotNull(
            SmpConfig.fromJsonOrNull(
                JSONObject()
                    .put("id", PARENT_ID)
                    .put("title", "Titre parent")
                    .put(CUSTOM_TITLE_KEY, "   \t ")
                    .toString()
            )
        )

        assertEquals(contract(null), decoded.customTitle)
    }

    @Test
    fun parentStructuralTitle_isNotReplacedByAliasInArchive() {
        val encoded = parentConfig("Titre parent", contract("Alias parent")).toJson()

        assertEquals("Titre parent", encoded.getString("title"))
        assertEquals("Alias parent", encoded.getString(CUSTOM_TITLE_KEY))
    }

    @Test
    fun twoVariants_roundTripWithIndependentAliases() {
        val decoded = roundTripVariants(
            variant(VARIANT_A_ID, "Version acoustique", contract("Alias acoustique")),
            variant(VARIANT_B_ID, "Version concert", contract("Alias concert"))
        )

        assertEquals("Alias acoustique", decoded.variants.single { it.id == VARIANT_A_ID }.customTitle?.value)
        assertEquals("Alias concert", decoded.variants.single { it.id == VARIANT_B_ID }.customTitle?.value)
    }

    @Test
    fun variantExplicitNoAlias_roundTripsAsJsonNull() {
        val encoded = ArrangementVariantsArchiveCodec.encode(
            archive(variant(VARIANT_A_ID, "Version acoustique", contract(null)))
        )
        val item = encoded.getJSONArray("variants").getJSONObject(0)
        val decoded = ArrangementVariantsArchiveCodec.decode(encoded).variants.single()

        assertTrue(item.has(CUSTOM_TITLE_KEY))
        assertTrue(item.isNull(CUSTOM_TITLE_KEY))
        assertEquals(contract(null), decoded.customTitle)
    }

    @Test
    fun legacyVariantArchive_withoutContractRemainsDistinguishable() {
        val json = legacyVariantArchiveJson()
        val decoded = ArrangementVariantsArchiveCodec.decode(json).variants.single()

        assertNull(decoded.customTitle)
    }

    @Test
    fun fullFamilyRoundTrip_onEmptyRuntimeRestoresThreeExactAliases() {
        val parentTransport = parentConfig("Titre parent", contract("Alias parent")).toJsonString()
        val variantsTransport = ArrangementVariantsArchiveCodec.encode(
            archive(
                variant(VARIANT_A_ID, "Version acoustique", contract("Alias acoustique")),
                variant(VARIANT_B_ID, "Version concert", contract("Alias concert"))
            )
        ).toString()
        val restoredAliases = linkedMapOf<String, String>()

        val parent = requireNotNull(SmpConfig.fromJsonOrNull(parentTransport))
        applyToMap(PARENT_ID, parent.customTitle, restoredAliases)
        ArrangementVariantsArchiveCodec.decode(JSONObject(variantsTransport)).variants.forEach { item ->
            applyToMap(item.id, item.customTitle, restoredAliases)
        }

        assertEquals(
            mapOf(
                PARENT_ID to "Alias parent",
                VARIANT_A_ID to "Alias acoustique",
                VARIANT_B_ID to "Alias concert"
            ),
            restoredAliases
        )
    }

    @Test
    fun legacyArchive_preservesExistingLocalAlias() {
        val aliases = linkedMapOf(PARENT_ID to "Alias local")
        var replaced = false
        var cleared = false

        val applied = applyCustomTitleContract(
            contract = null,
            replace = { replaced = true; aliases[PARENT_ID] = it; true },
            clear = { cleared = true; aliases.remove(PARENT_ID); true }
        )

        assertTrue(applied)
        assertFalse(replaced)
        assertFalse(cleared)
        assertEquals("Alias local", aliases[PARENT_ID])
    }

    @Test
    fun recentArchiveAlias_replacesOnlySameSongUnit() {
        val aliases = linkedMapOf(PARENT_ID to "Ancien parent", FOREIGN_ID to "Alias étranger")

        applyToMap(PARENT_ID, contract("Alias parent"), aliases)

        assertEquals("Alias parent", aliases[PARENT_ID])
        assertEquals("Alias étranger", aliases[FOREIGN_ID])
    }

    @Test
    fun recentArchiveExplicitNoAlias_clearsOnlySameSongUnit() {
        val aliases = linkedMapOf(PARENT_ID to "Ancien parent", FOREIGN_ID to "Alias étranger")

        applyToMap(PARENT_ID, contract(null), aliases)

        assertFalse(aliases.containsKey(PARENT_ID))
        assertEquals("Alias étranger", aliases[FOREIGN_ID])
    }

    @Test
    fun familyWithoutAliases_usesUnchangedStructuralTitles() {
        val parent = roundTripParent("Titre parent", contract(null))
        val variants = roundTripVariants(
            variant(VARIANT_A_ID, "Version acoustique", contract(null)),
            variant(VARIANT_B_ID, "Version concert", contract(null))
        )

        assertEquals("Titre parent", parent.title)
        assertEquals("Version acoustique", variants.variants.single { it.id == VARIANT_A_ID }.title)
        assertEquals("Version concert", variants.variants.single { it.id == VARIANT_B_ID }.title)
    }

    @Test
    fun targetedVariantRestore_doesNotModifyParentOrSiblingAlias() {
        val aliases = linkedMapOf(
            PARENT_ID to "Parent local",
            VARIANT_A_ID to "A locale",
            VARIANT_B_ID to "B locale"
        )
        val targeted = roundTripVariants(
            variant(VARIANT_A_ID, "Version acoustique", contract("A transportée")),
            selectedVariantId = VARIANT_A_ID
        )

        targeted.variants.forEach { applyToMap(it.id, it.customTitle, aliases) }

        assertEquals("Parent local", aliases[PARENT_ID])
        assertEquals("A transportée", aliases[VARIANT_A_ID])
        assertEquals("B locale", aliases[VARIANT_B_ID])
    }

    @Test
    fun utf8AccentsApostrophesAndPunctuation_arePreserved() {
        val exact = "Été d’Anaïs — scène n°2 : 100 % !?"

        assertEquals(exact, roundTripParent("Titre", contract(exact)).customTitle?.value)
        assertEquals(
            exact,
            roundTripVariants(variant(VARIANT_A_ID, "Version", contract(exact)))
                .variants.single().customTitle?.value
        )
    }

    @Test
    fun archiveContainsNoAliasesForeignToFamily() {
        val encoded = parentConfig("Titre parent", contract("Alias parent")).toJsonString() +
            ArrangementVariantsArchiveCodec.encode(
                archive(variant(VARIANT_A_ID, "Version acoustique", contract("Alias acoustique")))
            ).toString()

        assertFalse(encoded.contains(FOREIGN_ID))
        assertFalse(encoded.contains("Alias étranger"))
    }

    @Test
    fun archiveNeverTransportsGlobalTitleAliasesFile() {
        val parentJson = parentConfig("Titre parent", contract("Alias parent")).toJson()
        val variantsJson = ArrangementVariantsArchiveCodec.encode(
            archive(variant(VARIANT_A_ID, "Version acoustique", contract("Alias acoustique")))
        )

        assertFalse(parentJson.has("title_aliases.json"))
        assertFalse(variantsJson.toString().contains("title_aliases.json"))
        assertFalse(variantsJson.toString().contains("songId::"))
    }

    @Test
    fun parentAndVariants_areRestoredOnlyUnderExactSongIds() {
        val aliases = linkedMapOf<String, String>()
        applyToMap(PARENT_ID, contract("Alias parent"), aliases)
        applyToMap(VARIANT_A_ID, contract("Alias acoustique"), aliases)
        applyToMap(VARIANT_B_ID, contract("Alias concert"), aliases)

        assertEquals(setOf(PARENT_ID, VARIANT_A_ID, VARIANT_B_ID), aliases.keys)
        assertFalse(aliases.keys.any { it.contains("Titre") || it.contains("Version") })
    }

    @Test
    fun historicalFallbackCanSupplyExportValue_withoutBecomingIdentity() {
        val historicalAlias = "Alias historique URI"
        val capturedForSongId = contract(historicalAlias)
        val decoded = roundTripParent("Titre structurel", capturedForSongId)

        assertEquals(PARENT_ID, decoded.id)
        assertEquals(historicalAlias, decoded.customTitle?.value)
        assertFalse(decoded.toJsonString().contains("content://"))
    }

    @Test
    fun explicitAliasDeletion_isUnambiguousFromLegacyAbsence() {
        val legacy = requireNotNull(SmpConfig.fromJsonOrNull("{\"id\":\"$PARENT_ID\"}"))
        val explicitNone = roundTripParent("Titre parent", contract(null))

        assertNull(legacy.customTitle)
        assertEquals(contract(null), explicitNone.customTitle)
    }

    private fun roundTripParent(
        structuralTitle: String,
        customTitle: SmpConfig.CustomTitleContract
    ): SmpConfig = requireNotNull(
        SmpConfig.fromJsonOrNull(parentConfig(structuralTitle, customTitle).toJsonString())
    )

    private fun parentConfig(
        structuralTitle: String,
        customTitle: SmpConfig.CustomTitleContract
    ) = SmpConfig(
        id = PARENT_ID,
        title = structuralTitle,
        customTitle = customTitle
    )

    private fun roundTripVariants(
        vararg variants: ArrangementVariantArchiveEntry,
        selectedVariantId: String? = null
    ): ArrangementVariantsArchive = ArrangementVariantsArchiveCodec.decode(
        ArrangementVariantsArchiveCodec.encode(
            archive(*variants, selectedVariantId = selectedVariantId)
        )
    )

    private fun archive(
        vararg variants: ArrangementVariantArchiveEntry,
        selectedVariantId: String? = null
    ) = ArrangementVariantsArchive(
        sourceSongId = PARENT_ID,
        variants = variants.toList(),
        selectedVariantId = selectedVariantId
    )

    private fun variant(
        id: String,
        structuralTitle: String,
        customTitle: SmpConfig.CustomTitleContract
    ) = ArrangementVariantArchiveEntry(
        id = id,
        title = structuralTitle,
        arrangement = arrangement(id),
        customTitle = customTitle
    )

    private fun arrangement(variantId: String) = ArrangementData(
        version = 2,
        name = variantId,
        sourceSongId = PARENT_ID,
        updatedAt = 1234L,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData(
                entryId = "segment_$variantId",
                name = "Segment",
                startMs = 0L,
                endMs = 10_000L
            )
        )
    )

    private fun legacyVariantArchiveJson(): JSONObject {
        return JSONObject()
            .put("format", "smp_arrangement_variants")
            .put("version", 1)
            .put("sourceSongId", PARENT_ID)
            .put(
                "variants",
                JSONArray().put(
                    JSONObject()
                        .put("id", VARIANT_A_ID)
                        .put("title", "Version acoustique")
                        .put("arrangement", ArrangementJsonCodec.encode(arrangement(VARIANT_A_ID)))
                )
            )
    }

    private fun applyToMap(
        songId: String,
        contract: SmpConfig.CustomTitleContract?,
        aliases: MutableMap<String, String>
    ) {
        assertTrue(
            applyCustomTitleContract(
                contract = contract,
                replace = { title -> aliases[songId] = title; true },
                clear = { aliases.remove(songId); true }
            )
        )
    }

    private fun contract(value: String?) = SmpConfig.CustomTitleContract(value)

    private companion object {
        const val CUSTOM_TITLE_KEY = "customTitle"
        const val PARENT_ID = "parent_song"
        const val VARIANT_A_ID = "variant_a"
        const val VARIANT_B_ID = "variant_b"
        const val FOREIGN_ID = "foreign_song"
    }
}
