# CHORDPRO_AUDIO_LYRICS_SPEC.md

## Objectif

Ajouter la prise en charge de ChordPro dans le Lecteur audio > Lyrics de MusiMio, en réutilisant autant que possible le moteur et les composants déjà validés dans le Prompteur.

L’objectif est d’obtenir un contenu musical textuel synchronisé pouvant réunir paroles, accords ChordPro et formatage riche, sans casser le fonctionnement actuel du Lecteur audio ni la synchronisation.

L’architecture cible repose sur :

* une seule source éditable de contenu : `Lyrics` ;
* une seule source de timing : `Sync` ;
* une vue de lecture dérivée : `Grille`.

## Architecture cible des vues

Cette architecture remplace les règles antérieures qui conservaient un éditeur de grille d’accords indépendant.

### Éditeur : `Lyrics | Sync`

L’éditeur doit proposer deux onglets :

* `Lyrics` pour le contenu musical textuel ;
* `Sync` pour les timestamps des lignes de ce contenu.

L’ancien onglet d’édition `Grille` / `Accords` est obsolète dans l’architecture cible et doit être supprimé à terme.

`Lyrics` est l’unique source éditable. Il peut contenir :

* des paroles seules ;
* des paroles accompagnées d’accords ChordPro ;
* des accords seuls sous forme ChordPro ;
* du formatage riche.

### Synchronisation : `Sync`

`Sync` reste dédié aux timings. Il doit pouvoir synchroniser chaque ligne de `Lyrics`, quel que soit son contenu.

Une ligne contenant uniquement des accords peut donc recevoir un timestamp comme une ligne de paroles normale :

```text
[Am] [F] [G]
```

### Lecteur : `Lyrics | Grille`

Le Lecteur conserve deux vues :

* `Lyrics`, qui affiche le contenu complet avec paroles, accords ChordPro et formatage riche ;
* `Grille`, qui affiche une projection en lecture seule des accords ChordPro extraits de `Lyrics`.

La vue `Grille` ne constitue pas une seconde source d’accords et ne possède pas de contenu éditable indépendant.

## ChordPro appartient à `Lyrics`

La prise en charge de ChordPro doit être intégrée à `Lyrics`.

Exemple :

```text
Je [Am]pars ce soir
Sans [F]savoir où je vais
```

Dans ce contenu :

* le texte source reste dans `Lyrics` ;
* les accords ChordPro sont intégrés dans le texte ;
* les accords sont rendus visuellement avec les paroles ;
* la synchronisation reste celle des lignes `Lyrics` existantes.

## Ne pas modifier la logique de synchronisation audio

La prise en charge ChordPro ne doit pas modifier le fonctionnement actuel de la synchronisation.

La timeline audio existante reste la source de vérité.

ChordPro intervient uniquement dans :

* l’édition du texte ;
* l’analyse du contenu ;
* le rendu visuel ;
* la transposition des accords.

Il ne doit pas modifier :

* les timings LRC ;
* le seek ;
* pause / reprise ;
* changement de piste ;
* navigation ;
* Define Next ;
* fonctionnement ExoPlayer ;
* variantes ;
* logique de playlist.

## Réutilisation du moteur ChordPro du Prompteur

Ne pas créer un second moteur ChordPro spécifique au Lecteur audio.

Réutiliser autant que possible :

* le parser ChordPro existant ;
* le rendu des accords ;
* la logique de mise en forme ;
* la transposition ;
* `ChordTransposition.kt` ;
* la logique de stockage de la transposition ;
* les composants UI déjà utilisés dans le Prompteur lorsque leur factorisation est raisonnable.

Objectif :

**un seul comportement ChordPro dans toute l’application.**

Une correction future du moteur doit pouvoir bénéficier au Prompteur et au Lecteur audio.

## Palette d’accords automatique dans `Lyrics`

La palette d’accords doit être disponible dans `Lyrics` sur téléphone et sur tablette.

Elle est construite automatiquement à partir des accords ChordPro reconnus dans le contenu du morceau. Elle doit :

* contenir une seule occurrence de chaque accord unique ;
* conserver de préférence l’ordre de première apparition ;
* se mettre à jour lorsqu’un nouvel accord reconnu apparaît dans `Lyrics` ;
* ne jamais devenir une source de données indépendante.

### Apparition immédiate dans la palette

Dès qu’un nouvel accord ChordPro reconnu est saisi ou inséré dans `Lyrics`, il doit apparaître automatiquement dans la palette.

Aucune validation supplémentaire ni action manuelle ne doit être nécessaire.

Exemple :

Palette actuelle :

`Am   G   F`

L’utilisateur saisit pour la première fois :

`[Dm]`

La palette devient immédiatement :

`Am   G   F   Dm`

Si le morceau contient ensuite plusieurs occurrences de `Dm`, l’accord ne doit apparaître qu’une seule fois dans la palette.

L’objectif est que l’utilisateur comprenne naturellement le fonctionnement de la palette en voyant les nouveaux accords y apparaître au fur et à mesure de la saisie.

Exemple : si `Am`, `G`, `F` et `C` apparaissent plusieurs fois, la palette affiche uniquement :

```text
Am   G   F   C
```

Un appui sur un accord doit permettre de l’insérer au curseur ou de remplacer l’accord existant lorsque le curseur se trouve déjà dans une balise d’accord, en réutilisant le comportement validé dans le Prompteur.

### Insertion intelligente depuis la palette

Lorsqu’un utilisateur touche un accord dans la palette, MusiMio doit adapter le comportement à la position du curseur.

#### Cas 1 — Curseur dans du texte normal

Si le curseur est placé dans du texte normal, toucher `Am` dans la palette doit insérer :

`[Am]`

à la position du curseur.

#### Cas 2 — Curseur entre des crochets vides

Si l’utilisateur a déjà saisi :

`[]`

et que le curseur se trouve entre les deux crochets, toucher `Am` doit produire :

`[Am]`

Il ne faut pas produire :

`[[Am]]`

#### Cas 3 — Curseur dans un accord existant

Si le curseur se trouve dans une balise d’accord existante, par exemple :

`[G]`

toucher `Am` dans la palette doit remplacer l’accord existant et produire :

`[Am]`

#### Objectif UX

L’utilisateur ne doit pas avoir à se demander s’il doit saisir lui-même les crochets.

La palette insère automatiquement la syntaxe ChordPro correcte.

Si des crochets vides sont déjà présents, elle doit les réutiliser au lieu d’en ajouter une seconde paire.

Cette logique doit reprendre autant que possible le comportement d’insertion/remplacement déjà validé dans le Prompteur.

Sur téléphone, la palette peut occuper une ligne supplémentaire compacte et horizontalement défilable. Sur tablette, elle suit la même logique et profite simplement de la largeur disponible.

Il ne faut pas maintenir deux moteurs de palette différents selon le type d’appareil.

## Réutilisation de la ligne « Afficher le timing »

L’écran `Lyrics` possède actuellement une ligne contenant la commande :

**Afficher le timing**

Cette ligne doit être optimisée.

Le texte « Afficher le timing » doit être remplacé par une icône suffisamment compréhensible.

Le comportement de la commande Timing ne doit pas changer.

La largeur libérée doit permettre d’installer les commandes ChordPro.

## Barre d’édition ChordPro

La barre doit reprendre autant que possible la logique déjà validée dans le Prompteur.

Objectif visuel approximatif :

```text
[Timing] [Formatage...]        [-] 0 [+]
```

La barre doit rester :

* compacte ;
* utilisable au doigt ;
* cohérente avec le Prompteur ;
* lisible sur smartphone ;
* sans réduction importante de la zone de texte.

Les zones tactiles doivent rester suffisamment grandes.

## Commandes de formatage

Réutiliser autant que possible les commandes déjà validées dans le Prompteur.

Selon la largeur réellement disponible, peuvent notamment être disponibles :

* titre ;
* section ;
* gras ;
* italique ;
* séparateur ;
* commentaire ;
* couleur ;
* défaut / suppression du formatage.

Ne pas ajouter automatiquement toutes les commandes si cela rend la barre illisible.

La priorité est :

1. lisibilité ;
2. espace texte ;
3. ergonomie tactile ;
4. cohérence avec le Prompteur.

Si nécessaire, certaines commandes secondaires pourront être regroupées dans un menu.

## Transposition

Ajouter dans la barre compacte le contrôle déjà utilisé dans le Prompteur :

```text
−   0   +
```

Plage de transposition :

```text
-11 à +11
```

La transposition doit affecter uniquement les accords ChordPro.

Elle ne doit jamais modifier :

* les paroles ;
* la synchronisation ;
* le fichier audio ;
* les timings.

## Persistance de la transposition

La valeur de transposition doit être conservée pour le morceau.

Lorsqu’un utilisateur :

1. ouvre un morceau ;
2. transpose les accords ;
3. quitte le morceau ;
4. revient plus tard ;

la valeur précédente doit être restaurée.

Réutiliser autant que possible le système de persistance déjà validé pour le Prompteur.

La clé doit être liée au morceau concerné et ne doit pas contaminer les autres morceaux.

## Adaptation téléphone et tablette

Le téléphone reste prioritaire dans MusiMio et la zone de texte doit conserver suffisamment de hauteur.

La barre d’édition, les commandes de formatage, la transposition et la palette automatique doivent rester compactes. La tablette peut exploiter sa largeur supplémentaire, sans introduire une logique fonctionnelle distincte.

## Rendu pendant la lecture audio

Pendant la lecture, une ligne contenant :

```text
Je [Am]pars ce soir
```

doit être rendue comme une véritable ligne ChordPro.

L’accord doit apparaître clairement associé au mot ou à la position correspondante.

Le rendu doit rester lisible lorsque :

* la ligne devient active ;
* le texte défile ;
* l’utilisateur fait un seek ;
* la piste est mise en pause ;
* la lecture reprend ;
* la taille du texte change.

## Paroles sans ChordPro

La modification doit rester totalement compatible avec les morceaux existants.

Un morceau contenant uniquement :

```text
Je pars ce soir
Sans savoir où je vais
```

doit continuer à fonctionner exactement comme aujourd’hui.

L’absence de balise ChordPro ne doit provoquer :

* aucune modification visuelle inutile ;
* aucun ralentissement perceptible ;
* aucune erreur ;
* aucune modification des timings.

## Dérivation de la vue `Grille`

La vue `Grille` extrait automatiquement, ligne par ligne, les accords ChordPro présents dans `Lyrics`.

Exemple :

```text
[00:12.50]Je [Am]pars ce [F]soir
```

produit dans `Grille` :

```text
12.50 → Am   F
```

Une ligne ne contenant que des accords reste valide :

```text
[Am] [F]
```

et produit une ligne de grille contenant :

```text
Am   F
```

La ligne de `Grille` réutilise le timestamp de la ligne `Lyrics` correspondante.

Règle :

**1 ligne Lyrics synchronisée → 1 ligne Grille avec le même timing.**

Les accords d’une même ligne ne reçoivent pas de timestamps individuels. La vue dérivée ne cherche pas à déterminer le moment précis où chaque accord doit être joué à l’intérieur de la ligne.

## Collage de contenu

L’éditeur Paroles doit accepter le collage de ChordPro existant.

Exemple :

```text
[Am]Hello darkness my old [G]friend
```

Le texte doit être conservé correctement dans l’éditeur et interprété lors du rendu.

La conversion automatique d’une présentation traditionnelle :

```text
Am       G
Hello darkness my old friend
```

vers ChordPro n’appartient pas à cette première intégration.

Cette fonction pourra être étudiée plus tard.

## Priorité à la stabilité

Le Lecteur audio est une fonction critique utilisée pendant les prestations live.

La priorité de ce chantier est donc :

**stabilité > fonctionnalité**

Aucune modification ne doit dégrader :

* lecture audio ;
* synchro paroles ;
* changement de chanson ;
* défilement ;
* seek ;
* pause/reprise ;
* Define Next ;
* playlists ;
* variantes ;
* performances.

## Méthode d’implémentation

Le travail devra être effectué par petites étapes.

### Étape 1 — Audit

Cartographier le Lecteur audio et identifier :

* stockage des paroles ;
* parsing actuel ;
* modèle des lignes synchronisées ;
* rendu ;
* éditeur ;
* timing ;
* composants UI ;
* points permettant de réutiliser ChordPro.

**Aucune modification de code à cette étape.**

### Étape 2 — Parsing ChordPro

Connecter le moteur ChordPro déjà existant au contenu de l’onglet `Lyrics`.

Le texte brut doit continuer à fonctionner.

### Étape 3 — Rendu

Afficher correctement les accords ChordPro pendant la lecture synchronisée.

Valider notamment :

* ligne active ;
* défilement ;
* changement de ligne ;
* seek.

### Étape 4 — Édition

Adapter la ligne actuellement utilisée par « Afficher le timing ».

Ajouter :

* icône Timing ;
* commandes de formatage ;
* contrôle de transposition.

Téléphone et tablette :

* afficher la palette automatique des accords réellement utilisés ;
* conserver une présentation compacte ;
* permettre un défilement horizontal de la palette si nécessaire.

### Étape 5 — Transposition et persistance

Réutiliser le moteur de transposition existant.

Ajouter la mémorisation de la transposition par morceau.

### Étape 6 — Source unique et vue `Grille` dérivée

Faire de `Lyrics` l’unique source éditable du contenu musical textuel.

Supprimer à terme l’ancien onglet d’édition `Grille` / `Accords`, puis construire la vue `Grille` du Lecteur à partir des accords ChordPro de `Lyrics`, sans créer de second stockage ni de timings par accord.

### Étape 7 — Validation générale

Tester au minimum :

#### Téléphone

* morceau sans accords ;
* morceau ChordPro ;
* changement de tonalité ;
* fermeture/réouverture du morceau ;
* seek ;
* pause/reprise ;
* changement de chanson ;
* orientation si concernée ;
* longues paroles ;
* lignes courtes ;
* lignes longues.

#### Tablette

Même série de tests avec en plus :

* palette automatique d’accords ;
* layout grand écran.

#### Régression

Vérifier :

* éditeur limité à `Lyrics | Sync` ;
* vue `Grille` dérivée ;
* onglet `Sync` ;
* anciennes paroles ;
* playlists ;
* Define Next ;
* variantes ;
* lecteur audio.

## Ce qui n’est pas demandé dans cette première version

Ne pas ajouter pendant ce chantier :

* conversion automatique texte + accords vers ChordPro ;
* reconnaissance intelligente d’accords copiés depuis Internet ;
* synchronisation individuelle de chaque accord ;
* modification automatique du fichier audio selon la transposition ;
* refonte générale du Lecteur audio ;
* nouvelles fonctions sans rapport direct avec ChordPro.

## Principe final

MusiMio doit appliquer le principe suivant :

* `Lyrics` est l’unique source de vérité pour le contenu musical textuel ;
* `Sync` est l’unique source de timing pour les lignes ;
* `Grille` est une vue de lecture seule dérivée automatiquement des accords ChordPro de `Lyrics`.

Cette architecture permet les paroles seules, les paroles avec accords, les accords seuls et le formatage riche sans double saisie, divergence de contenu ni duplication des timings.

## Règle d’architecture principale

**Ne pas dupliquer le système ChordPro du Prompteur.**

Le Lecteur audio doit réutiliser les briques existantes dès que possible.

L’objectif final est d’avoir :

**un moteur ChordPro commun, deux contextes d’utilisation : Prompteur et Lecteur audio paroles.**
