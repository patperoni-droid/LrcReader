# CHORDPRO_AUDIO_LYRICS_SPEC.md

## Objectif et statut

MusiMio prend en charge ChordPro dans le Lecteur audio > `Lyrics` en réutilisant le moteur et les composants communs au Prompteur.

Le chantier est implémenté et validé fonctionnellement sur appareil. Le contenu musical textuel synchronisé peut réunir paroles, accords ChordPro et formatage riche sans modifier le moteur de synchronisation audio.

L’architecture actuelle repose sur :

* une seule source éditable de contenu : `Lyrics` ;
* une seule source de timing : `Sync` ;
* une vue de lecture dérivée : `Grille`.

## Architecture actuelle des vues

### Éditeur : `Lyrics | Sync`

L’éditeur propose deux onglets :

* `Lyrics` pour le contenu musical textuel ;
* `Sync` pour les timestamps des lignes de ce contenu.

L’ancien onglet d’édition `Grille` / `Accords` a été supprimé. Les anciennes données d’accords séparées ne sont pas supprimées pour autant et restent disponibles pour la compatibilité du Lecteur.

### Lecteur : `Lyrics | Grille`

Le Lecteur conserve deux vues :

* `Lyrics`, qui affiche le contenu complet avec paroles, accords ChordPro et formatage riche ;
* `Grille`, qui affiche les accords ChordPro extraits de `Lyrics`.

La vue `Grille` est une vue de lecture. Elle ne constitue pas une seconde source éditable d’accords.

## `Lyrics`, source éditable unique

`Lyrics` accepte :

* des paroles seules ;
* des paroles accompagnées d’accords ChordPro ;
* des lignes contenant uniquement des accords ChordPro ;
* du formatage riche.

Exemple :

```text
Je [Am]pars ce soir
Sans [F]savoir où je vais
```

Dans ce contenu :

* le texte source reste stocké dans `Lyrics` ;
* les accords sont intégrés au texte avec la syntaxe ChordPro ;
* le rendu de lecture associe visuellement les accords aux paroles ;
* les timestamps restent attachés aux lignes Lyrics existantes.

Une modification de balise ChordPro ou de formatage riche conserve le timestamp existant de la ligne, y compris lorsque plusieurs lignes possèdent le même texte visible.

## `Sync`, source unique des timings

`Sync` reste dédié aux timestamps des lignes de `Lyrics`.

Les lignes synchronisées peuvent contenir :

* des paroles seules ;
* des paroles et des accords ;
* uniquement des accords.

Une ligne contenant uniquement des accords peut donc recevoir un timestamp comme une ligne de paroles normale :

```text
[Am] [F] [G]
```

ChordPro ne modifie pas :

* les timings LRC ;
* le seek ;
* pause / reprise ;
* le changement de piste ;
* la navigation ;
* Define Next ;
* ExoPlayer ;
* les variantes ;
* la logique de playlist.

La timeline audio existante reste la source de vérité.

## Moteur ChordPro commun

Audio Lyrics réutilise les briques ChordPro déjà validées pour le Prompteur, notamment :

* `parseChordPro()` pour reconnaître les accords ;
* la préparation et le rendu partagés d’une ligne ;
* `ChordTransposition.kt` et `transposeChord()` pour la transposition ;
* les commandes communes de formatage et d’édition.

Il n’existe pas de second parser ChordPro propre au Lecteur audio.

Principe :

**un moteur ChordPro commun, deux contextes d’utilisation : Prompteur et Lecteur audio Lyrics.**

## Palette d’accords automatique dans `Lyrics`

La palette automatique est disponible dans `Lyrics` sur téléphone et tablette.

Elle est dérivée directement du contenu Lyrics courant. Elle :

* ne contient que les accords ChordPro reconnus par le parser commun ;
* affiche une seule occurrence de chaque accord ;
* conserve l’ordre de première apparition ;
* se recalcule lorsque le texte Lyrics change ;
* fait apparaître immédiatement un nouvel accord reconnu ;
* n’a aucun stockage ni mécanisme de persistance indépendant.

Exemple :

```text
[Am] texte [G]
[Am] autre ligne
[F] puis [G]
```

Palette obtenue :

```text
Am   G   F
```

Si l’utilisateur saisit ensuite `[Dm]`, la palette devient immédiatement :

```text
Am   G   F   Dm
```

La palette occupe une seule ligne compacte et horizontalement défilable. La même logique fonctionnelle est utilisée sur téléphone et tablette.

### Insertion intelligente depuis la palette

Un appui sur un accord adapte l’insertion à la position actuelle du curseur.

#### Cas 1 — Curseur dans du texte normal

Toucher `Am` insère `[Am]` à la position du curseur.

#### Cas 2 — Curseur entre des crochets vides

Avec le curseur entre les deux crochets de `[]`, toucher `Am` produit `[Am]` et non `[[Am]]`.

#### Cas 3 — Curseur dans un accord existant

Avec le curseur dans `[G]`, toucher `Am` remplace l’accord et produit `[Am]`.

Après insertion ou remplacement, le curseur reste placé de façon cohérente après la balise et le champ d’édition conserve le focus.

## Barre d’édition ChordPro

La ligne auparavant libellée « Afficher le timing » est devenue une barre compacte. La commande Timing utilise une icône et conserve son comportement historique.

La barre regroupe les fonctions d’édition retenues pour Audio Lyrics :

* affichage ou masquage des timings ;
* gras ;
* italique ;
* insertion ou remplacement d’un accord ChordPro ;
* couleur riche ;
* contrôle de transposition `− valeur +`.

Sur les largeurs les plus contraintes, les commandes de formatage peuvent être regroupées dans un menu compact afin de préserver les zones tactiles et la hauteur du champ Lyrics.

### Formatage riche

Le gras, l’italique et les couleurs riches agissent sur le texte ou la sélection courante. La couleur riche utilise les balises textuelles du Prompteur, par exemple :

```text
Je pars <c=red>ce soir</c>
```

Cette couleur riche est distincte de `LrcLine.colorArgb`, qui reste la couleur de ligne historique gérée par `Sync`.

Le Lecteur masque les balises et affiche le rendu riche, avec ou sans accord ChordPro sur la ligne.

### Aperçu riche intermédiaire

L’aperçu riche intermédiaire précédemment affiché sous la barre a été supprimé afin de libérer de la hauteur dans l’éditeur, notamment sur téléphone, et de réserver cet espace à la palette automatique.

Le texte source et ses balises restent visibles dans le champ d’édition. Le rendu riche reste disponible pendant la lecture dans la vue `Lyrics`.

## Transposition et persistance

La barre compacte contient le contrôle `− 0 +`, dans la plage `-11` à `+11` demi-tons.

La transposition :

* affecte uniquement l’affichage des accords ChordPro ;
* utilise le moteur commun `ChordTransposition.kt` ;
* est persistée par morceau avec l’identité stable du morceau ;
* est restaurée lorsque l’utilisateur revient sur le morceau ;
* ne modifie jamais le texte Lyrics source, le fichier audio ou les timestamps.

La palette automatique continue de représenter les accords source du morceau, indépendamment de la valeur transposée affichée.

## Rendu synchronisé dans `Lyrics`

Pendant la lecture, une ligne contenant :

```text
Je [Am]pars ce soir
```

est rendue comme une ligne ChordPro complète. Le texte visible, les accords, leurs positions et le formatage riche utilisent le moteur de rendu partagé.

Une `LrcLine` reste toujours un seul item synchronisé. Son timestamp, son index, son clic, son seek, son état actif et son défilement restent attachés au même item.

Les lignes réellement simples continuent d’utiliser le chemin de rendu historique. La préparation ChordPro et riche dépend du contenu des paroles et de la transposition ; elle n’est pas recalculée dans la boucle de suivi audio.

Les paroles sans ChordPro conservent leur rendu et leur comportement historiques.

## Vue `Grille` dérivée de `Lyrics`

Le chemin prioritaire de données est :

```text
Lyrics → parseChordPro() → lignes Grille dérivées → affichage
```

Pour chaque `LrcLine` Lyrics :

* le parser commun extrait les accords reconnus ;
* le texte des paroles et le formatage riche sont ignorés dans la Grille ;
* toutes les occurrences d’accords sont conservées ;
* les doublons sont conservés ;
* l’ordre réel des accords est conservé ;
* le `timeMs` de la ligne source est réutilisé ;
* aucun timestamp individuel n’est créé pour les accords.

La règle d’unicité de la palette automatique ne s’applique pas à la Grille.

Exemple :

```text
[00:12.50]Je [Am]pars [Am]ce [F]soir
```

produit conceptuellement :

```text
12.50 → Am   Am   F
```

Une ligne ne contenant aucun accord ne produit aucune ligne Grille.

Une ligne contenant uniquement `[Am] [F] [G]` produit `Am   F   G`.

Règle :

**1 ligne Lyrics contenant des accords → 1 ligne Grille avec le même timing.**

### Lignes non minutées

Une ligne Lyrics contenant des accords avec `timeMs = 0` peut rester visible dans la Grille.

Elle ne participe pas au calcul de la ligne active. Si la Grille ne contient aucune ligne réellement minutée, aucune ligne n’est déclarée active par la synchronisation dérivée.

### Transposition de la Grille

La Grille utilise la même valeur de transposition Audio Lyrics que le rendu Lyrics.

Chaque accord est transposé pour l’affichage avec `transposeChord()`. La source ChordPro originale n’est jamais réécrite.

Exemple :

```text
Source Lyrics : [Am] [F]
Transposition : +2
Grille affichée : Bm   G
```

## Compatibilité avec les anciennes grilles

L’ancien stockage séparé des accords est conservé en lecture comme fallback. Aucun modèle historique, fichier ou contenu utilisateur n’a été supprimé et aucune migration destructive n’a été exécutée.

La sélection de la source suit cette règle :

* si `Lyrics` contient au moins un accord ChordPro reconnu, la Grille dérivée est prioritaire ;
* si `Lyrics` ne contient aucun accord reconnu et qu’une ancienne grille existe, cette grille historique reste affichée ;
* si aucune des deux sources ne contient d’accord, la vue Grille reste vide proprement.

Lorsqu’une Grille dérivée existe, son affichage ne crée pas inutilement de nouveau fichier d’accords legacy.

Cette compatibilité permet aux anciens morceaux de conserver leur Grille sans réintroduire un second éditeur d’accords.

## Téléphone et tablette

La logique fonctionnelle est identique sur téléphone et tablette :

* même source Lyrics ;
* même parser ChordPro ;
* même insertion intelligente ;
* même transposition ;
* même dérivation de la Grille ;
* même fallback legacy.

La palette automatique reste sur une seule ligne et peut défiler horizontalement. La tablette profite de la largeur disponible sans utiliser un second moteur de palette.

## Collage de contenu

L’éditeur Lyrics accepte le collage de contenu ChordPro existant.

Exemple :

```text
[Am]Hello darkness my old [G]friend
```

Le texte est conservé dans `Lyrics` et interprété par le rendu et la Grille dérivée.

La conversion automatique d’une présentation traditionnelle :

```text
Am       G
Hello darkness my old friend
```

vers ChordPro n’est pas implémentée.

## Compatibilité et stabilité live

L’intégration conserve :

* les anciennes paroles sans ChordPro ;
* les lignes non minutées ;
* les timestamps existants pendant l’édition ;
* le seek et le défilement synchronisé de Lyrics ;
* pause / reprise et changement de morceau ;
* Define Next ;
* playlists et variantes ;
* les anciennes données de Grille.

La priorité reste :

**stabilité > fonctionnalité**

## Historique du chantier implémenté

Le chantier a été réalisé et validé par étapes :

1. audit du Lecteur audio et du Prompteur ;
2. conservation des timestamps lors des modifications ChordPro ;
3. extraction du rendu partagé d’une ligne ;
4. rendu ChordPro et riche dans les paroles synchronisées ;
5. transposition persistée par morceau ;
6. barre d’édition, couleur riche et palette automatique ;
7. suppression de l’ancien onglet d’édition Grille ;
8. dérivation de la Grille du Lecteur avec fallback legacy ;
9. validation fonctionnelle et visuelle sur appareil.

## Fonctions hors périmètre actuel

Les fonctions suivantes ne sont pas implémentées dans ce chantier :

* conversion automatique texte + accords vers ChordPro ;
* reconnaissance intelligente d’accords copiés depuis Internet ;
* synchronisation individuelle de chaque accord ;
* modification automatique du fichier audio selon la transposition ;
* migration destructive ou suppression automatique des anciennes grilles ;
* refonte générale du Lecteur audio.

## Principe final

MusiMio applique le principe suivant :

* `Lyrics` est l’unique source de vérité éditable pour le contenu musical textuel ;
* `Sync` est l’unique source de timing pour les lignes ;
* `Grille` est une vue de lecture dérivée automatiquement des accords ChordPro de `Lyrics`, avec un fallback temporaire pour les anciennes grilles.

Cette architecture permet les paroles seules, les paroles avec accords, les accords seuls et le formatage riche sans double saisie, divergence de contenu ni duplication des timings.
