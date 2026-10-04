# Bibliothèque YouTube de MusiMio — spécification de conception

## Statut

**Conception / réflexion — non implémentée.** Ce document décrit une fonction future ; il ne décrit aucun écran, stockage ou lecteur YouTube actuellement disponible dans MusiMio.

## Objectif et périmètre

Permettre de conserver dans MusiMio des liens vers des vidéos YouTube, principalement des backing tracks pour travailler son instrument, et de les retrouver rapidement, y compris pendant une prestation.

MusiMio enregistrerait uniquement le lien, son classement et, si elles sont utiles, certaines métadonnées. La vidéo ne serait pas téléchargée. Sa lecture nécessiterait une connexion Internet.

Cette bibliothèque de liens serait distincte des morceaux audio locaux et de leurs traitements dans MusiMio.

## Organisation de la bibliothèque

Une section ou un onglet YouTube dédié dans la Bibliothèque permettrait de :

- ajouter un lien YouTube ;
- créer des dossiers et des sous-dossiers ;
- déplacer et classer les liens dans ces dossiers ;
- rechercher les vidéos enregistrées.

Parcours d'ajout envisagé :

1. Ouvrir `Bibliothèque → YouTube`.
2. Choisir `Ajouter un lien` et coller une URL YouTube.
3. Choisir ou créer un dossier, puis enregistrer.
4. Retrouver la vidéo dans la bibliothèque et appuyer dessus pour l'ouvrir dans MusiMio.

Le partage Android depuis l'application YouTube vers MusiMio pourra être étudié ultérieurement ; il ne fait pas partie des exigences retenues à ce stade.

## Lecture intégrée et navigation

En lecture normale, la vidéo resterait dans la zone centrale de MusiMio. Les onglets principaux en haut et le menu principal en bas resteraient visibles et accessibles. L'utilisateur ne devrait pas avoir l'impression d'avoir quitté MusiMio.

Un mode plein écran pourrait être proposé. **Seule son activation volontaire** pourrait masquer les onglets du haut et le menu du bas pour donner tout l'écran à la vidéo. À la sortie du plein écran, l'écran YouTube de MusiMio, ses deux zones de navigation et la vidéo toujours ouverte réapparaîtraient immédiatement.

Le bouton Retour Android et la navigation MusiMio suivraient le chemin `Vidéo → dossier précédent → bibliothèque YouTube`. Le retour ne fermerait pas MusiMio et ne lancerait pas automatiquement l'application YouTube.

> **Principe directeur :** La fonction YouTube doit se comporter comme une partie native de MusiMio. Tant que l’utilisateur n’active pas volontairement le plein écran ou « Ouvrir dans YouTube », l’interface principale de MusiMio reste visible et accessible.

## Compatibilité de lecture YouTube

La lecture intégrée devra utiliser une solution officielle ou compatible avec YouTube. Les éventuelles publicités sont acceptées. MusiMio ne devra ni bloquer les publicités, ni télécharger la vidéo, ni contourner les restrictions de YouTube.

Si une vidéo refuse la lecture intégrée, MusiMio proposera clairement `Ouvrir dans YouTube`. L'ouverture externe restera une action volontaire et explicite. La solution technique et ses conditions d'utilisation devront être vérifiées avant toute implémentation.

## Audio MusiMio

Les traitements audio internes de MusiMio ne sont pas inclus dans cette fonction : transposition, vitesse MusiMio, égaliseur et autres traitements du son. Leur compatibilité avec la lecture YouTube n'est pas présumée.
