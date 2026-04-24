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