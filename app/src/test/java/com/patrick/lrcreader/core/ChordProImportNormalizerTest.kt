package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProImportNormalizerTest {
    private fun convert(source: String): String = applyChordProImport(analyzeChordProImport(source))

    @Test fun explicitSymbolsReuseTheExistingGrammar() {
        listOf("Em", "Am/F#", "Cmaj7", "F#sus4", "Gadd9", "C", "Cm", "C7",
            "C#m7", "Bb", "Cdim", "G/B", "D/F#", "Emmaj7", "C7(b9)").forEach {
            assertEquals(it, "[$it]", convert("**$it**"))
        }
    }

    @Test fun analysisDescribesExactRangesWithoutChangingSource() {
        val source = "🎸 **Em** puis **Am/F#**!"
        val analysis = analyzeChordProImport(source)
        assertEquals(source, analysis.source)
        assertEquals(2, analysis.candidateCount)
        assertEquals(listOf("**Em**", "**Am/F#**"), analysis.replacements.map {
            source.substring(it.sourceRange)
        })
        assertEquals(3, analysis.replacements.first().sourceRange.first)
        assertEquals(listOf("[Em]", "[Am/F#]"), analysis.replacements.map { it.replacement })
        assertEquals("🎸 [Em] puis [Am/F#]!", applyChordProImport(analysis))
        assertEquals(source, analysis.source)
    }

    @Test fun completeUserExample() {
        val source = "[Verse 1] **Em**         **Emmaj7**              **Em7**          **A**   Dis-lui,   fais ça pour moi,   dis-lui                    **Am7**  **D7**                  **G**          **Am/F#**   **B7** que les jours sans elle   me semblent moins longs."
        val expected = "[Verse 1] [Em]         [Emmaj7]              [Em7]          [A]   Dis-lui,   fais ça pour moi,   dis-lui                    [Am7]  [D7]                  [G]          [Am/F#]   [B7] que les jours sans elle   me semblent moins longs."
        assertEquals(9, analyzeChordProImport(source).candidateCount)
        assertEquals(expected, convert(source))
    }

    @Test fun ordinaryTextRawChordsAndSectionsAreUntouched() {
        listOf("", "[Em]", "[Verse 1]", "[Chorus]", "[Refrain]", "**Bonjour**",
            "**La**", "**Do**", "**Si**", "Em", "Em   C   G   D", "A D La Do Si",
            "Em      C\nBonjour le monde", "** Em**", "**Em **", "**Em C**",
            "**C/**", "**H7**", "**Coucou**").forEach {
            assertEquals(it, it, convert(it))
            assertEquals(0, analyzeChordProImport(it).candidateCount)
        }
    }

    @Test fun incompleteNestedAndAmbiguousMarkupIsUntouched() {
        listOf("**Em", "Em**", "*Em*", "***Em***", "****Em****", "**Em***",
            "**outer **Em** outer**", "*outer **Em** outer*", "**Em *texte***",
            "**Em** **C", "**Em\nC**", "**Em\rC**", "**Em\r\nC**",
            "\\**Em**", "[**Em**]", "[Verse **Em**]", "[**Em**",
            "**[Em]**", "**Em**[**C**]**G**").forEach {
            // The last case contains two safe candidates outside the protected brackets.
            if (it == "**Em**[**C**]**G**") {
                assertEquals("[Em][**C**][G]", convert(it))
            } else assertEquals(it, it, convert(it))
        }
    }

    @Test fun preservesAllCharactersOutsideReplacementRanges() {
        val source = "  Éléonore, l’été 🎸\t**Am/F#**!  \r\n\t**Cmaj7** d'accord\r**Gadd9**…\n\n"
        val expected = "  Éléonore, l’été 🎸\t[Am/F#]!  \r\n\t[Cmaj7] d'accord\r[Gadd9]…\n\n"
        assertEquals(expected, convert(source))
    }

    @Test fun doesNotPadOrGuessAlignment() {
        assertEquals("Je [Am]voulais", convert("Je **Am**voulais"))
        assertEquals("[Em]      [C]\nBonjour le monde", convert("**Em**      **C**\nBonjour le monde"))
    }

    @Test fun invalidCandidatesDoNotHideIndependentValidCandidates() {
        assertEquals("**Bonjour** [Em] **Si** [G]", convert("**Bonjour** **Em** **Si** **G**"))
        assertEquals("***C***\r\n[Em]", convert("***C***\r\n**Em**"))
    }

    @Test fun conversionIsIdempotent() {
        val converted = convert("[Verse 1] **Em** paroles **Am/F#**\n[Chorus] **Bonjour**")
        assertEquals(converted, convert(converted))
        assertTrue(analyzeChordProImport(converted).replacements.isEmpty())
    }
}
