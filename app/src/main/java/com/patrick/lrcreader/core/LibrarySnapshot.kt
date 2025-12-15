package com.patrick.lrcreader.core

import android.net.Uri

/**
 * Photo mémoire unique de la bibliothèque.
 * Elle est remplie UNE fois au lancement,
 * puis l’UI ne fait que la lire.
 */
object LibrarySnapshot {

    /** Dossier racine Music choisi par l’utilisateur */
    var rootFolderUri: Uri? = null

    /** Tous les éléments trouvés lors du scan */
    var entries: List<String> = emptyList() // URIs string, neutre, pas dépendant de l'UI


    /** True quand le scan initial est terminé */
    var isReady: Boolean = false
}
