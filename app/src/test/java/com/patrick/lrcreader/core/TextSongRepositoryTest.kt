package com.patrick.lrcreader.core

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class TextSongRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = Mockito.mock(Context::class.java)
        TextSongRepository.setInMemoryOnlyForTests(true)
        TextSongRepository.clearAll(context)
    }

    @After
    fun tearDown() {
        TextSongRepository.clearAll(context)
        TextSongRepository.setInMemoryOnlyForTests(false)
    }

    @Test
    fun create_and_listAll_returnsSortedCatalogWithStableUris() {
        val idZulu = TextSongRepository.create(context, "Zulu", "z")
        val idAlpha = TextSongRepository.create(context, "Alpha", "a")

        val all = TextSongRepository.listAll(context)

        assertEquals(2, all.size)
        assertEquals(listOf("Alpha", "Zulu"), all.map { it.title })
        assertEquals(TextSongRepository.resolvePrompterUri(idAlpha), all[0].uri)
        assertEquals(TextSongRepository.resolvePrompterUri(idZulu), all[1].uri)
    }

    @Test
    fun update_isVisibleInListAndGetTitleFromIdOrUri() {
        val id = TextSongRepository.create(context, "Old title", "old")

        TextSongRepository.update(context, id, "New title", "new")

        val updated = TextSongRepository.get(context, id)
        assertNotNull(updated)
        assertEquals("New title", updated?.title)
        assertEquals("new", updated?.content)
        assertEquals("New title", TextSongRepository.getTitle(context, id))
        assertEquals("New title", TextSongRepository.getTitle(context, "prompter://$id"))
    }

    @Test
    fun delete_removesFromCatalogAndLookup() {
        val id = TextSongRepository.create(context, "To delete", "content")
        assertTrue(TextSongRepository.listAll(context).any { it.id == id })

        TextSongRepository.delete(context, id)

        assertNull(TextSongRepository.get(context, id))
        assertTrue(TextSongRepository.listAll(context).none { it.id == id })
        assertNull(TextSongRepository.getTitle(context, "prompter://$id"))
    }
}
