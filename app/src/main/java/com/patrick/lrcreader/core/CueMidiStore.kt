package com.patrick.lrcreader.core

/**
 * Stockage simple des Cues MIDI en mémoire.
 * (pas encore de persistance disque, on verra plus tard)
 */
object CueMidiStore {

    // trackUri (String) -> liste mutable de Cues
    private val cuesByTrack: MutableMap<String, MutableList<CueMidi>> = mutableMapOf()

    private fun keyFor(trackUri: String?): String? =
        trackUri?.takeIf { it.isNotBlank() }

    fun getCuesForTrack(trackUri: String?): List<CueMidi> {
        val key = keyFor(trackUri) ?: return emptyList()
        return cuesByTrack[key]?.toList() ?: emptyList()
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