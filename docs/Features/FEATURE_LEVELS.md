# FEATURE — LEVELS

## Objectif

`LEVELS` devient l'atelier de préparation du mix du concert.

Son objectif n'est pas de mesurer un niveau théorique. Son objectif est de préparer le niveau réel de chaque morceau avant le live.

Le musicien doit pouvoir écouter les passages utiles, régler le niveau à l'oreille, puis passer rapidement au morceau suivant.

---

## Changement De Philosophie

L'ancien module LUFS reposait sur l'idée suivante :

- analyser les morceaux ;
- uniformiser automatiquement leurs niveaux ;
- affiner ensuite les réglages.

L'expérience terrain montre que ce modèle n'est plus le bon.

En pratique, la préparation fiable se fait à l'oreille, morceau par morceau, avec le fader `LEVEL` mémorisé.

`LEVELS` n'est donc plus un analyseur LUFS.

`LEVELS` devient une page de travail pour préparer le mix réel du concert.

Après validation en utilisation réelle, le workflow manuel est la philosophie définitive de `LEVELS`.

Les traitements automatiques LUFS ne font plus partie du parcours `LEVELS`.

---

## Source De Vérité

La seule valeur de référence est :

```text
LEVEL mémorisé du morceau
```

Ce `LEVEL` est le réglage utilisé par le Player.

Il correspond au niveau choisi par le musicien avec le fader du tiroir `LEVEL`.

La notion LUFS ne doit plus être exposée à l'utilisateur dans ce module.

Les anciennes données ou analyses LUFS peuvent rester internes si elles existent encore, mais elles ne sont plus la vérité produit et ne doivent plus piloter l'UX principale.

Le réglage des niveaux repose exclusivement sur l'écoute du musicien et l'ajustement manuel via le `GainDrawer`.

---

## Workflow Utilisateur

Parcours cible :

```text
Ouvrir LEVELS
↓
Choisir un morceau
↓
Appui simple sur Lecture
↓
Choisir le point d'écoute
↓
Ouvrir le tiroir LEVEL
↓
Écouter
↓
Régler le niveau
↓
Passer au morceau suivant
```

Le travail consiste à préparer le mix du concert, pas à corriger automatiquement des valeurs d'analyse.

Le parcours normal devient :

```text
Choisir un morceau
↓
Écouter
↓
Ajuster
↓
Morceau suivant
```

---

## Vision UX

La page doit rester extrêmement simple.

Elle doit montrer uniquement ce qui aide à préparer le niveau réel des morceaux :

- les morceaux ;
- leur `LEVEL` actuel ;
- les commandes de lecture ;
- le tiroir `LEVEL` ;
- les raccourcis utiles pour préparer le mix.

Tout ce qui concerne les LUFS doit disparaître de l'interface utilisateur principale.

Le musicien ne doit pas avoir à comprendre :

- LUFS ;
- gain calculé ;
- normalisation automatique ;
- analyse audio ;
- correction théorique.

L'interface doit parler le langage du concert :

```text
Est-ce que ce morceau est au bon niveau par rapport aux autres ?
```

---

## Rôle Du Démarrage Rapide

Le bouton `Lecture` ouvre directement le choix du point d'écoute.

Il permet de démarrer au début du morceau ou de rejoindre rapidement un passage utile, notamment un passage fort, afin de régler le niveau sans perdre de temps.

Cette fonction doit être conservée dans `LEVELS`.

Elle fait partie du workflow principal de préparation du mix.

## Écoute Rapide (Quick Preview)

Lorsqu'un morceau est affiché dans la liste des niveaux, un appui simple sur le bouton `Lecture` ouvre directement un petit menu proposant plusieurs points de départ :

- Début ;
- 20 s ;
- 40 s ;
- 60 s ;
- 90 s.

Le morceau démarre directement au temps choisi.

La fonction est donc directement accessible : aucune action cachée ni appui long n'est nécessaire.

Le choix `Début` remplace implicitement `0 seconde`, avec un vocabulaire naturel pour un musicien.

Cette évolution améliore la découvrabilité tout en conservant une interface simple : le bouton reste unique, mais il révèle immédiatement les départs utiles.

Cette fonction permet d'éviter les introductions trop longues et d'écouter rapidement les passages les plus représentatifs d'un morceau.

Elle facilite :

- la comparaison rapide entre plusieurs titres ;
- l'équilibrage des niveaux ;
- la préparation des enchaînements ;
- la création d'un mix cohérent pour le concert.

Cette fonctionnalité fait partie intégrante de la philosophie de préparation audio de SMP et devra être conservée lors des futures évolutions de l'interface.

---

## Tiroir LEVEL

`LEVELS` doit utiliser le même comportement que le Player pour le réglage du niveau.

Objectif produit :

- même fader ;
- même valeur mémorisée ;
- même ergonomie ;
- même effet réel sur le morceau.

Si possible, le même composant doit être réutilisé afin d'éviter deux expériences différentes pour un même réglage.

La documentation produit ne définit pas l'implémentation technique. Elle fixe seulement la règle UX :

```text
Régler le LEVEL dans LEVELS doit revenir au même résultat que régler le LEVEL dans le Player.
```

---

## Roadmap

### Étape 1 — Renommer L'Onglet

Renommer l'onglet :

```text
LUFS
↓
LEVELS
```

Objectif :

- supprimer la promesse d'analyse LUFS ;
- installer le nouveau rôle produit ;
- rendre la page compréhensible pour un musicien.

### Étape 2 — Retirer Les Actions LUFS De L'Interface

Supprimer de l'interface utilisateur principale :

- `Appliquer LUFS` ;
- `Retirer LUFS`.

Ces actions ne correspondent plus au workflow réel.

Elles doivent être retirées du workflow `LEVELS`.

La sélection de morceaux dédiée à ces actions n'est plus nécessaire dans cette page.

### Étape 3 — Remplacer La Colonne LUFS Par LEVEL

La colonne affichée comme `LUFS` devient :

```text
LEVEL
```

Elle affiche la valeur réellement utilisée par le Player :

```text
LEVEL mémorisé du morceau
```

Cette valeur doit aider le musicien à voir rapidement quels morceaux ont déjà été préparés et quels morceaux restent à ajuster.

### Étape 4 — Intégrer Le Tiroir LEVEL

Intégrer dans `LEVELS` le même tiroir `LEVEL` que dans le Player.

Objectif :

- écouter un morceau ;
- ouvrir le tiroir ;
- régler le niveau ;
- mémoriser immédiatement le réglage du morceau.

Le comportement doit rester identique au Player.

### Étape 5 — Conserver Le Démarrage Rapide

Conserver l'écoute rapide directement accessible depuis `Lecture`.

Un appui simple doit ouvrir le choix du point d'écoute :

- `Début` ;
- `20 s` ;
- `40 s` ;
- `60 s` ;
- `90 s`.

Cette fonction permet de sélectionner rapidement le début ou un passage fort et accélère la préparation du mix.

Elle doit rester accessible dans le workflow principal.

### Étape 6 — Mettre À Jour L'Aide De Première Ouverture

La fenêtre d'aide affichée lors de la première ouverture doit présenter la nouvelle philosophie `LEVELS`.

Elle doit expliquer que cette page sert à :

- préparer le niveau sonore de chaque morceau avant le concert ;
- écouter rapidement les passages importants grâce au démarrage rapide ;
- ajuster le `LEVEL` avec le fader ;
- mémoriser automatiquement le niveau de chaque morceau.

Elle ne doit plus mettre en avant les notions de LUFS ou d'uniformisation automatique.

L'objectif est que le musicien comprenne immédiatement le rôle de cette page sans connaissance technique.

---

## Hors Périmètre

Ce document ne propose aucune implémentation technique.

Il ne définit pas :

- une structure de stockage ;
- un algorithme audio ;
- une nouvelle analyse ;
- une migration de données ;
- un refactor de composant.

Les futures modifications devront respecter les règles SMP :

- patch minimal ;
- stabilité avant fonctionnalités ;
- aucune régression du Player ;
- ExoPlayer reste la référence temporelle ;
- aucun traitement lourd pendant le playback.

---

## Principe Final

`LEVELS` sert à préparer un concert à l'oreille.

La vérité n'est plus une mesure LUFS.

La vérité est le niveau réellement choisi par le musicien pour chaque morceau.

Les traitements automatiques LUFS ne sont plus une fonction utilisateur de `LEVELS`.
