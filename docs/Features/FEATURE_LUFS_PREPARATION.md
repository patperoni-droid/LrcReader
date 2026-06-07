# FEATURE — LUFS PREPARATION

## Objectif produit

La préparation LUFS permet de préparer les niveaux des morceaux avant répétition ou concert.

Le but est de comparer et ajuster rapidement le volume perçu des titres depuis la Bibliothèque, sans devoir corriger pendant le live.

Cette fonction est un outil de préparation. Elle ne modifie pas le moteur audio live profond.

Retour terrain concert :

Le besoin SMP n'est pas la conformité broadcast.

Le critère de réussite est :

"Éviter les gros écarts de volume perçu entre deux morceaux en concert."

L'objectif est d'obtenir des enchaînements cohérents à l'oreille, notamment sur :
- refrains
- passages denses
- morceaux dansants
- backing tracks avec grosses basses
- morceaux masterisés de façon inégale

---

## Clarification LUFS SMP V1

Le système actuellement nommé LUFS dans l'interface n'est pas un calcul LUFS conforme ITU-R BS.1770 / EBU R128.

Il s'agit d'un système SMP V1 d'estimation et de normalisation de niveau basé sur l'analyse waveform.

Conséquences :
- il reste utile en pratique pour rapprocher les niveaux des morceaux
- il ne doit pas être documenté comme un vrai LUFS broadcast
- le terme LUFS reste conservé dans l'interface pour compatibilité et simplicité utilisateur
- les écrans, actions et strings utilisateur ne doivent pas être renommés dans cette V1

👉 SMP vise la cohérence live à l'oreille, pas une conformité académique de diffusion.

---

## Emplacement

La fonction LUFS est réservée à :

Bibliothèque -> onglet LUFS

Elle ne doit pas apparaître dans la bibliothèque normale, les playlists, le Player live, Define Next ou la timeline.

---

## Valeur LUFS affichée

La valeur affichée doit rester simple :

- titre non corrigé : afficher la valeur source mesurée
- titre corrigé : afficher la valeur effective de sortie

Exemples :

- non corrigé : `-18`
- corrigé vers la cible : `-14`
- corrigé puis ajusté de `+2 dB` : `-12`

En état non sélectionné, la ligne reste compacte et n'affiche pas le mot `LUFS`.

En état sélectionné, la valeur peut afficher `LUFS`.

---

## Marqueur corrigé

Un marqueur discret `✓` indique qu'un titre a déjà une correction LUFS sauvegardée.

Ce marqueur est basé sur la configuration du morceau :

- `playback.volumeSource = "lufs"` -> titre corrigé LUFS
- autre source ou absence de correction -> pas de marqueur LUFS

Le marqueur doit rester compact et ne doit pas ajouter de label long.

---

## Sélection et ajustement

Un titre peut être sélectionné dans l'onglet LUFS.

Quand le titre est sélectionné :

- les contrôles `-1` / `+1` sont visibles
- un warning visuel compact peut apparaître si le gain devient élevé
- le gain manuel est appliqué en temps réel pendant la préécoute ou la lecture du morceau courant

Règles :

- titre sélectionné sans réglage manuel : application de la cible par défaut `-14 LUFS`
- `+1` / `-1` agit relativement à la valeur effective actuelle affichée
- ne jamais repartir automatiquement de `-14` après un ajustement manuel
- l'utilisateur doit entendre immédiatement la correction manuelle
- aucun arrêt/reprise du morceau ne doit être nécessaire
- Track Console doit refléter le niveau réellement appliqué quand le morceau est le morceau courant

Exemples :

- titre affiché `-14`, `+1` -> `-13`
- titre affiché `-18`, `+1` -> `-17`
- titre affiché `-10`, `-1` -> `-11`

---

## Modèle de niveau recommandé

Le modèle produit recommandé est :

Niveau automatique SMP
+
Correction manuelle utilisateur

Rôles :
- `Appliquer LUFS` rapproche automatiquement le morceau d'un niveau de référence SMP
- le gain manuel corrige ensuite à l'oreille
- le gain manuel reste mémorisé par morceau
- le gain manuel est audible immédiatement pendant la lecture

Le gain final stocké dans `volumeDb` correspond au niveau réellement appliqué.

---

## Préécoute LUFS

La préécoute LUFS sert aux répétitions et à la préparation des niveaux.

Elle permet de comparer rapidement les volumes réels des morceaux, même quand les intros sont longues.

Règles UX :

- tap simple sur Play : préécoute depuis le début du morceau (`0s`)
- appui long sur Play : menu léger de départ rapide
- choix disponibles : `20s`, `40s`, `60s`, `90s`
- après choix, la preview démarre directement à cette position

Contraintes :

- réutiliser le preview player de l'onglet LUFS
- ne pas créer de nouveau player pour cette fonction
- effectuer le seek de preview avant lecture
- ne pas lancer le Player live
- ne pas afficher paroles, timeline ou Define Next

---

## Atelier d'uniformisation des playlists

La page LUFS sert aussi d'atelier rapide de préparation des playlists avant concert.

Fonctions utilisées :
- écoute du morceau
- sauts rapides à `+20 s`, `+40 s`, `+60 s`, `+90 s`
- accès rapide aux passages représentatifs
- correction `+1` / `-1` en temps réel
- écoute immédiate du résultat
- sauvegarde automatique du gain manuel

Retour terrain validé :

L'utilisateur ne cherche généralement pas à écouter l'introduction complète.

L'objectif est d'atteindre rapidement :
- le refrain
- le passage dense
- le passage dansant
- la zone où les différences de niveau deviennent réellement perceptibles

👉 Cette page agit comme un atelier de calibration des playlists.

---

## Stockage SongUnit

Les réglages LUFS font partie du `SongUnit` via `config.json`.

Champs attendus dans `playback` quand LUFS est préparé :

- `volumeDb` : gain final appliqué
- `volumeSource` : `"lufs"` si la correction LUFS est active
- `lufsMeasured` : valeur source mesurée si stockée
- `lufsTarget` : cible LUFS
- `lufsAutoDb` : correction automatique calculée
- `lufsManualDb` : ajustement utilisateur `-1` / `+1`

Ces champs doivent rester dans le runtime normalisé du morceau.

---

## Sync manuelle

Les réglages LUFS doivent suivre un morceau synchronisé.

Quand téléphone A envoie un morceau vers téléphone B :

- le `config.json` du `SongUnit` doit être inclus dans le package
- les champs LUFS doivent être conservés à l'import
- le hash sync du morceau doit changer si les champs LUFS changent
- B doit afficher la même valeur effective
- B doit afficher le même marqueur `✓` si LUFS est actif
- B doit appliquer le même gain en lecture

La sync LUFS ne doit pas dépendre du titre, du nom de fichier ou du chemin externe.

La source de vérité reste `songId` + `SongUnit/config.json`.

---

## Limites V1

- pas de limiteur
- warning visuel seulement si gain élevé
- liberté utilisateur conservée même si le gain devient élevé
- pas de popup bloquante
- pas de désactivation forcée de LUFS

---

## Isolation live

La préparation LUFS n'a aucun impact sur :

- Player live
- timeline
- Define Next
- autoplay
- crossfade
- anti-blanc / anti-silence
- playlists
- moteur audio live profond

Tout changement touchant ces systèmes doit être traité comme une autre feature, avec validation live séparée.

Aucune analyse lourde ne doit être réalisée pendant le live.

Les analyses de niveau doivent rester :
- à l'import
- lors d'une action volontaire utilisateur
- lors d'une préparation avant concert

Jamais pendant la lecture live.

Priorité :

Stabilité > fonctionnalité.

---

## Roadmap — Niveau Live

Étudier un futur système "Niveau Live" davantage orienté scène.

Principe envisagé :
- analyse offline
- détection des passages forts
- analyse d'environ 10 secondes représentatives
- mesure plus proche du ressenti réel en concert
- conservation du gain manuel utilisateur

Objectif :

Réduire encore les écarts de volume perçu lors des enchaînements de morceaux.

---

## Règle projet

Toute modification future de la fonction LUFS doit mettre à jour ce fichier.

La documentation doit rester cohérente avec l'implémentation réelle.
