package com.patrick.lrcreader.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.json.JSONObject
import java.io.File

class PrompterColorPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun saveReloadFromPortableJsonPreservesEveryColorAndSongIdentity() {
        val context = Mockito.mock(Context::class.java)
        val prefs = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        val rootUri = Mockito.mock(Uri::class.java)
        val root = temporaryFolder.newFolder("library")
        val rootString = "file://${root.absolutePath}"
        Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt())).thenReturn(prefs)
        Mockito.`when`(prefs.getString(Mockito.eq("library_root_uri"), Mockito.isNull())).thenReturn(rootString)
        Mockito.`when`(prefs.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString())).thenReturn(editor)
        Mockito.`when`(rootUri.scheme).thenReturn("file")
        Mockito.`when`(rootUri.path).thenReturn(root.absolutePath)
        val cacheField = TextSongRepository::class.java.getDeclaredField("cache").apply { isAccessible = true }
        val previousCache = cacheField.get(null)
        val previousVersion = TextSongRepository.version.intValue
        TextSongRepository.setInMemoryOnlyForTests(false)
        cacheField.set(null, null)
        try {
            Mockito.mockStatic(Uri::class.java).use { uris ->
                uris.`when`<Uri> { Uri.parse(rootString) }.thenReturn(rootUri)
                val source = PrompterTextColor.entries.joinToString("\n") { color ->
                    applyPrompterColor(TextFieldValue("[Am]Été", TextRange(0, 7)), color, "Texte").text
                }
                val id = TextSongRepository.create(context, "Titre", "Ancien texte")
                TextSongRepository.update(context, id, "Titre", source)
                val json = JSONObject(File(root, "Config/text_songs.json").readText())
                assertEquals(source, json.getJSONObject("items").getJSONObject(id).getString("text"))
                cacheField.set(null, null) // Cold reload; mocked legacy preferences contain no song data.
                val reloaded = requireNotNull(TextSongRepository.get(context, id))
                assertEquals(source, reloaded.content)
                assertEquals("Titre", reloaded.title)
                assertEquals(listOf(id), TextSongRepository.listAll(context).map { it.id })
                val lines = buildRichTextPrompterLines(preparePrompterText(reloaded.content))
                PrompterTextColor.entries.forEachIndexed { index, color ->
                    assertEquals("Été", lines[index].lyricText)
                    val styled = lines[index].lyricText.withPrompterStyles(lines[index].spans, Color.White)
                    assertEquals(resolvePrompterTextColor(color, Color.White), styled.spanStyles.single().item.color)
                    assertEquals(listOf(color), lines[index].words.single().runs.single().chordColors)
                }
            }
        } finally {
            cacheField.set(null, previousCache)
            TextSongRepository.version.intValue = previousVersion
        }
    }
}
