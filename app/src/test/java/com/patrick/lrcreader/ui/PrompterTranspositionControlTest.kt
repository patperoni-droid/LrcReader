package com.patrick.lrcreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PrompterTranspositionControlTest {

    @Test
    fun sharedControlFormatsZeroPositiveAndNegativeValues() {
        assertEquals("0", formatPrompterTransposition(0))
        assertEquals("+2", formatPrompterTransposition(2))
        assertEquals("-3", formatPrompterTransposition(-3))
    }

    @Test
    fun sharedControlStopsAtUpperBound() {
        assertEquals(11, stepPrompterTransposition(10, 1))
        assertEquals(11, stepPrompterTransposition(11, 1))
        assertEquals(11, stepPrompterTransposition(Int.MAX_VALUE, 1))
    }

    @Test
    fun sharedControlStopsAtLowerBound() {
        assertEquals(-11, stepPrompterTransposition(-10, -1))
        assertEquals(-11, stepPrompterTransposition(-11, -1))
        assertEquals(-11, stepPrompterTransposition(Int.MIN_VALUE, -1))
    }
}
