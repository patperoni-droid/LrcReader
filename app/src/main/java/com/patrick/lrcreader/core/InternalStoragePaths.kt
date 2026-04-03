package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Gestion et sécurisation de l'arborescence SPL_Music
 *
 * IMPORTANT (compatibilité) :
 * - On crée les dossiers en minuscules (audio/lyrics/accords)
 * - MAIS on crée aussi les anciens dossiers en Majuscules (Audio/Lyrics/Accords)
 *   et on copie les fichiers dans les deux sens (sans jamais supprimer).
 *
 * But : éviter que certaines parties de l'app (ou anciennes versions) cherchent
 * encore "Lyrics" alors que toi tu as mis les .lrc dans "lyrics" (ou inversement).
 */
object InternalStoragePaths {

    fun ensureSplRoot(
        context: Context,
        createLegacyAudioTextDirs: Boolean = true
    ): File {
        // /Android/data/<package>/files/SPL_Music
        val root = File(context.getExternalFilesDir(null), "SPL_Music")
        if (!root.exists()) root.mkdirs()

        // Dossiers racine (on garde tes noms actuels)
        listOf(
            "DJ",
            "Backups"
        ).forEach { name ->
            File(root, name).mkdirs()
        }

        // BackingTracks
        val backingTracks = File(root, "BackingTracks")
        backingTracks.mkdirs()

        // --- Dossiers attendus (minuscules)
        val audioLower = File(backingTracks, "audio")
        val lyricsLower = File(backingTracks, "lyrics")
        val accordsLower = File(backingTracks, "accords")
        val audioUpper = File(backingTracks, "Audio")
        val lyricsUpper = File(backingTracks, "Lyrics")
        val accordsUpper = File(backingTracks, "Accords")

        // Sur nouveau workspace, on ne recrée plus automatiquement
        // Audio/Lyrics/Accords/Midi/Videos.
        val baseDirs = mutableListOf<File>()
        if (createLegacyAudioTextDirs) {
            baseDirs += listOf(
                audioLower, lyricsLower, accordsLower,
                audioUpper, lyricsUpper, accordsUpper
            )
        }
        baseDirs.forEach { it.mkdirs() }

        if (createLegacyAudioTextDirs) {
            // Migration SAFE : copie dans les deux sens, sans suppression
            // (pour que TOUT marche quel que soit le dossier cherché par le code)
            mirrorCopyOnly(audioUpper, audioLower, tag = "MIGRATION_AUDIO")
            mirrorCopyOnly(audioLower, audioUpper, tag = "MIGRATION_AUDIO")

            mirrorCopyOnly(lyricsUpper, lyricsLower, tag = "MIGRATION_LYRICS")
            mirrorCopyOnly(lyricsLower, lyricsUpper, tag = "MIGRATION_LYRICS")

            mirrorCopyOnly(accordsUpper, accordsLower, tag = "MIGRATION_CHORDS")
            mirrorCopyOnly(accordsLower, accordsUpper, tag = "MIGRATION_CHORDS")
        }

        // Bonus compat : si tu avais un dossier "lyrique" ou "lyriques" (français)
        val lyrique1 = File(backingTracks, "lyrique")
        val lyrique2 = File(backingTracks, "lyriques")
        if (createLegacyAudioTextDirs) {
            if (lyrique1.exists() && lyrique1.isDirectory) {
                mirrorCopyOnly(lyrique1, lyricsLower, tag = "MIGRATION_LYRIQUE_FR")
                mirrorCopyOnly(lyrique1, lyricsUpper, tag = "MIGRATION_LYRIQUE_FR")
            }
            if (lyrique2.exists() && lyrique2.isDirectory) {
                mirrorCopyOnly(lyrique2, lyricsLower, tag = "MIGRATION_LYRIQUE_FR")
                mirrorCopyOnly(lyrique2, lyricsUpper, tag = "MIGRATION_LYRIQUE_FR")
            }
        }

        return root
    }

    /**
     * Copie tous les fichiers d'un dossier source vers un dossier destination,
     * uniquement si le fichier n'existe pas déjà en destination.
     * Aucune suppression automatique.
     */
    private fun mirrorCopyOnly(fromDir: File, toDir: File, tag: String) {
        if (!fromDir.exists() || !fromDir.isDirectory) return
        if (!toDir.exists()) toDir.mkdirs()

        val files = fromDir.listFiles() ?: return
        if (files.isEmpty()) return

        Log.d(tag, "Copy ${fromDir.name} → ${toDir.name} (${files.size} items)")

        files.forEach { f ->
            if (!f.isFile) return@forEach
            val dest = File(toDir, f.name)
            if (dest.exists()) return@forEach
            runCatching {
                f.copyTo(dest, overwrite = false)
            }
        }
    }
}
