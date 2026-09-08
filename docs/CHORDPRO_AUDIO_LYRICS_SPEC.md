# CHORDPRO_AUDIO_LYRICS_SPEC.md

## Objectif

Ajouter la prise en charge de ChordPro dans le Lecteur audio > Paroles de MusiMio, en réutilisant autant que possible le moteur et les composants déjà validés dans le Prompteur.

L’objectif est d’obtenir des paroles synchronisées avec la musique pouvant contenir des accords ChordPro, sans casser le fonctionnement actuel du Lecteur audio, de la synchronisation des paroles ni de l’onglet Accords existant.

## Structure actuelle à conserver

Dans l’écran d’édition des paroles du Lecteur audio, trois onglets existent actuellement :

* Paroles
* Accords
* Synchro

Ces trois onglets doivent être conservés.

### Onglet Accords

L’onglet Accords conserve son rôle actuel.

Il sert à saisir et afficher une grille d’accords seule, sans paroles.

Cette grille peut être synchronisée avec la musique.

Il ne faut pas transformer cet onglet en éditeur ChordPro.

Exemple :

```text
Am    F
C     G
Am    F
```

Cette fonction reste utile pour les musiciens qui souhaitent uniquement suivre des accords pendant la lecture audio.

## ChordPro appartient à l’onglet Paroles

La prise en charge de ChordPro doit être ajoutée dans l’onglet Paroles.

Exemple :

```text
Je [Am]pars ce soir
Sans [F]savoir où je vais
```

Dans ce mode :

* le texte reste une parole ;
* les accords ChordPro sont intégrés dans le texte ;
* les accords sont rendus visuellement avec les paroles ;
* la synchronisation reste celle des paroles existantes.

L’onglet Accords indépendant et le ChordPro dans Paroles doivent être considérés comme deux fonctionnalités différentes et complémentaires.

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

## Interface téléphone

Le téléphone reste prioritaire dans MusiMio.

La zone disponible pour éditer les paroles est réduite.

Il ne faut donc pas ajouter une nouvelle barre complète qui réduirait fortement la hauteur du champ texte.

### Palette d’accords

Sur téléphone :

**ne pas afficher la palette complète d’accords.**

L’utilisateur peut :

* saisir manuellement les balises ChordPro ;
* coller du texte ChordPro ;
* utiliser les outils d’édition disponibles dans la barre compacte.

Exemples :

```text
[Am]
[F]
[G7]
```

La palette d’accords complète est réservée à la tablette dans cette première version.

## Réutilisation de la ligne « Afficher le timing »

L’écran Paroles possède actuellement une ligne contenant la commande :

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

## Interface tablette

La tablette dispose de davantage de largeur.

Elle peut donc proposer une interface plus complète.

Sur tablette, il est prévu de pouvoir afficher :

* les commandes de formatage ;
* la transposition ;
* la palette d’accords.

La palette peut reprendre celle déjà utilisée dans le Prompteur.

Ne pas créer une nouvelle palette différente sans nécessité.

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

## Cohabitation avec l’onglet Accords

Un morceau peut potentiellement contenir :

* des paroles ChordPro dans l’onglet Paroles ;
* une grille indépendante dans l’onglet Accords.

Ces deux données doivent pouvoir coexister.

Ne pas supprimer automatiquement l’une lorsque l’autre existe.

Dans cette première version, il n’est pas nécessaire de fusionner les deux systèmes.

Ils restent indépendants.

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

Connecter le moteur ChordPro déjà existant au contenu de l’onglet Paroles.

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

Téléphone :

* pas de palette complète d’accords.

Tablette :

* possibilité d’afficher la palette.

### Étape 5 — Transposition et persistance

Réutiliser le moteur de transposition existant.

Ajouter la mémorisation de la transposition par morceau.

### Étape 6 — Validation générale

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

* palette d’accords ;
* layout grand écran.

#### Régression

Vérifier :

* onglet Accords ;
* onglet Synchro ;
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
* suppression ou remplacement de l’onglet Accords ;
* refonte générale du Lecteur audio ;
* nouvelles fonctions sans rapport direct avec ChordPro.

## Principe final

MusiMio doit proposer deux usages distincts :

### Accords

Grille indépendante synchronisée avec la musique.

Destinée notamment aux musiciens qui souhaitent uniquement suivre leurs accords.

### Paroles ChordPro

Paroles synchronisées contenant directement les accords.

Destinées notamment aux chanteurs, guitaristes et musiciens qui souhaitent suivre texte et harmonie ensemble.

Les deux systèmes doivent coexister sans se remplacer.

## Règle d’architecture principale

**Ne pas dupliquer le système ChordPro du Prompteur.**

Le Lecteur audio doit réutiliser les briques existantes dès que possible.

L’objectif final est d’avoir :

**un moteur ChordPro commun, deux contextes d’utilisation : Prompteur et Lecteur audio paroles.**
