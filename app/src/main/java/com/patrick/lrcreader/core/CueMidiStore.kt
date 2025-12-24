package com.patrick.lrcreader.core

import android.net.Uri
/**
 * Stockage simple des Cues MIDI en mémoire.
 * (pas encore de persistance disque, on verra plus tard)
 */
object CueMidiStore {

    // trackUri (String) -> liste mutable de Cues
    private val cuesByTrack: MutableMap<String, MutableList<CueMidi>> = mutableMapOf()

    private fun keyFor(trackUri: String?): String? {
        val raw = trackUri?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { Uri.parse(raw).normalizeScheme().toString() }
            .getOrElse { raw }
    }

    fun getCuesForTrack(trackUri: String?): List<CueMidi> {
        val key = keyFor(trackUri) ?: return emptyList()
        return cuesByTrack[key]?.toList() ?: emptyList()
    }
    fun shiftAfterDelete(trackUri: String?, deletedLineIndex: Int) {
        val key = keyFor(trackUri) ?: return
        val list = cuesByTrack[key] ?: return

        val shifted = list.mapNotNull { cue ->
            when {
                cue.lineIndex == deletedLineIndex -> null // supprimé
                cue.lineIndex > deletedLineIndex -> cue.copy(lineIndex = cue.lineIndex - 1)
                else -> cue
            }
        }.toMutableList()

        if (shifted.isEmpty()) cuesByTrack.remove(key) else cuesByTrack[key] = shifted
    }

    /**
     * Ajoute ou remplace un Cue pour une ligne donnée d’un morceau.
     * (clé = lineIndex)
     */
    fun upsertCue(trackUri: String?, cue: CueMidi) {
        val key = keyFor(trackUri) ?: return
        val list = cuesByTrack.getOrPut(key) { mutableListOf() }

        val idx = list.indexOfFirst { it.lineIndex == cue.lineIndex }
        if (idx >= 0) {
            list[idx] = cue
        } else {
            list += cue
        }
    }

    /**
     * Supprime le Cue associé à une ligne de paroles.
     */
    fun deleteCue(trackUri: String?, lineIndex: Int) {
        val key = keyFor(trackUri) ?: return
        val list = cuesByTrack[key] ?: return
        list.removeAll { it.lineIndex == lineIndex }
        if (list.isEmpty()) cuesByTrack.remove(key)
    }
}