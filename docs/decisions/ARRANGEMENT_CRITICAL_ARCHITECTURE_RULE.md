RÔLE

Ce document décrit une décision technique critique pour la future feature "Arrangement".

Il doit être lu AVANT toute implémentation liée à :
- segments
- structure de morceaux
- réorganisation musicale
- enchaînement de parties

⸻

CONTEXTE

Dans la page "Régler la grille", une tentative de boucle audio a été faite avec la logique suivante :

lecture → OUT atteint → seekTo(IN) → reprise

Résultat :
- accroc audible au retour
- comportement non fiable
- dépendant du device

Conclusion :
Cette approche est INVALIDE pour une feature Arrangement.

⸻

DÉCOUVERTE CLÉ

Un test a été réalisé avec une approche différente :

- création d’un segment audio clipé (IN → OUT)
- lecture via Media3 / ExoPlayer
- utilisation d’une boucle native (repeat)

Résultat :
- boucle fluide
- pas d’accroc
- comportement stable

Conclusion validée :

❗ Un enchaînement audio propre ne peut PAS être basé sur des seek en live.
❗ Il doit être basé sur des segments préparés AVANT lecture.

⸻

PRINCIPE ARCHITECTUREL À RESPECTER

Pour toute implémentation de la feature Arrangement :

NE JAMAIS FAIRE :
- seekTo pour simuler une boucle ou un enchaînement
- repositionnement audio en temps réel pour changer de structure

TOUJOURS FAIRE :
- préparer une liste de segments AVANT la lecture
- utiliser Media3 / ExoPlayer avec :
    - MediaItem
    - clipping (startMs / endMs)
    - playlist de segments

Exemple :

Segment A : 10s → 20s  
Segment B : 50s → 60s

Structure :

A → B → B → C

Implémentation :

List<MediaItem> préparée AVANT play

⸻

OBJECTIF DE LA FEATURE ARRANGEMENT

Permettre à l’utilisateur de :
- découper un morceau en segments musicaux
- réorganiser ces segments
- jouer un nouveau “morceau” sans modifier l’audio source

⸻

MODÈLE MINIMAL À PRÉVOIR

ArrangementSegment :
- startMs
- endMs
- name

Structure :
- liste ordonnée de segments

Évolution UX validée :
- cette évolution d'interface est réservée à la tablette et ne modifie pas l'interface Arrangement du téléphone
- l'interface tablette cible présente directement cette Structure sous la forme d'une piste horizontale unique de conteneurs audio ordonnés
- chaque bloc est une occurrence indépendante avec une identité stable, un nom, une couleur, ses propres `startMs` / `endMs`, un `repeatCount` et un état `muted`
- la waveform supérieure reste exprimée dans le temps du morceau source, tandis que la piste horizontale est exprimée dans le temps cumulé de l'Arrangement préparé
- la tête de lecture dessinée au-dessus des blocs suit la position réelle de la preview Structure et ne constitue jamais une horloge autonome
- la playlist peut être ouverte ou fermée dans un panneau latéral gauche interne à l'écran Arrangement ; ce redimensionnement ne modifie ni la Structure, ni le zoom, ni le titre actif
- sélectionner un titre B dans cette playlist conserve l'Arrangement A tant que le Play jaune n'a pas lancé B via le pipeline Playback officiel
- `repeatCount` est développé en segments de lecture avant le démarrage
- les occurrences muettes sont exclues de la liste Media3 préparée sans être supprimées du projet
- cette évolution supprime les deux colonnes visibles, mais ne change pas l'obligation de préparer toute la playlist Media3 avant lecture
- toute évolution du modèle partagé doit rester rétrocompatible et ne provoquer aucun changement fonctionnel sur téléphone
- le stockage V2 porte les occurrences indépendantes dans `entries` et maintient une projection `segments` / `structureSegmentIds` pour les lecteurs V1
- la lecture d'un stockage V1 dérive des `entryId` déterministes en mémoire sans migration destructive ni réécriture automatique
- si l'ancien écran téléphone sauvegarde un Arrangement déjà en V2, les métadonnées `repeatCount`, `muted` et `color` existantes sont préservées par identité d'occurrence

⸻

CONTRAINTES SMP_RULES

Respecter absolument :

- aucun traitement lourd en live
- aucune lecture depuis zip
- audio déjà disponible localement
- préparation avant lecture
- stabilité live prioritaire

⸻

LIMITES CONNUES

- certains formats (MP3) peuvent produire un léger délai si segment non aligné sur keyframe
- une V2 pourra améliorer cela (PCM, pré-render, cache)

Mais pour la V1 :
- cette approche est validée et acceptable

⸻

CONCLUSION

La feature Arrangement DOIT être basée sur :

👉 segments préparés
👉 playlist Media3
👉 aucun seek live

Toute implémentation qui ne respecte pas ce principe est à rejeter.

⸻

RAPPEL IMPORTANT

Ce point a été validé expérimentalement.

Ne pas revenir en arrière.
Ne pas réintroduire de logique basée sur seekTo.
