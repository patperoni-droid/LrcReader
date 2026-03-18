package com.patrick.lrcreader.core

object PlaybackRouter {

    sealed class Target {
        data class Audio(val uri: String, val playlist: String?) : Target()
        data class Smp(val songId: String, val playlist: String?) : Target()
        data class Prompter(val id: String) : Target()
        data class Unknown(val uri: String) : Target()
    }

    /**
     * Route une “URI” venant des playlists / recherches vers la bonne cible.
     * ⚠️ Ne fait AUCUNE action (pas de play, pas de navigation).
     * Juste une décision.
     */
    fun resolve(uri: String, playlist: String?): Target {
        val clean = uri.trim()
        return when {
            isGroupHeader(clean) ->
                Target.Unknown(clean)

            isPrompterItem(clean) ->
                Target.Prompter(clean.removePrefix("prompter://"))

            isSmpItem(clean) ->
                Target.Smp(getSmpSongId(clean) ?: clean, playlist)

            isPlayableAudioItem(clean) ->
                Target.Audio(clean, playlist)

            else ->
                Target.Unknown(clean)
        }
    }
}
