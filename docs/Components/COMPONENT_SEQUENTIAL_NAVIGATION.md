# COMPONENT — Sequential Navigation

## Mission

La navigation séquentielle est un comportement officiel de SMP.

Elle permet au musicien de sélectionner le morceau précédent ou suivant sans devoir viser directement une ligne de playlist.

Elle est indépendante de tout périphérique.

Elle décrit une intention musicale et ergonomique, pas une interface particulière.

---

## Objectifs

La navigation séquentielle vise à :

- améliorer la précision en concert ;
- permettre un point d'appui naturel sur le bord de l'écran ;
- limiter les erreurs de sélection ;
- conserver le médiator en main ;
- préparer les futures interfaces de contrôle.

La priorité est la fiabilité du geste en conditions réelles.

---

## Actions Officielles

Les actions conceptuelles officielles sont :

```text
SelectPrevious()
SelectNext()
PlaySelected()
```

Ces actions représentent la source officielle de navigation séquentielle.

Elles ne sont liées à aucune interface particulière.

Elles doivent exprimer une intention unique :

- sélectionner le morceau précédent ;
- sélectionner le morceau suivant ;
- lancer le morceau sélectionné.

---

## Interfaces Possibles

Ces actions pourront être appelées par :

- boutons tactiles ;
- commandes situées sur le bord de l'écran ;
- pédale Bluetooth ;
- pédalier MIDI ;
- clavier ;
- télécommande ;
- toute future interface.

Le comportement métier devra toujours rester unique.

Aucune duplication de logique n'est autorisée.

Une interface déclenche une action officielle.

Elle ne réimplémente jamais la navigation.

---

## Principe D'ergonomie SMP

Les essais terrain montrent qu'en concert :

- le musicien tient souvent un médiator ;
- la main peut trembler légèrement ;
- les doigts peuvent être humides.

Dans ce contexte, viser précisément une ligne de playlist est moins fiable qu'utiliser des commandes situées sur le bord de l'écran.

Ces commandes permettent de prendre appui avec la paume et de déclencher une action plus stable.

La priorité de SMP est donc la fiabilité du geste plutôt que la vitesse absolue.

La navigation séquentielle doit réduire les erreurs de sélection, même si elle demande parfois une action de plus.

---

## Règles D'architecture

- La navigation séquentielle est indépendante du périphérique.
- Le comportement métier est unique.
- Les interfaces ne dupliquent pas la logique.
- Les actions s'appliquent à la sélection officielle de playlist.
- La sélection et la lecture restent deux intentions séparées.
- `PlaySelected()` lit uniquement le morceau actuellement sélectionné.

Cette séparation permet de conserver un contrôle clair en live :

```text
Choisir
↓
Valider
```

---

## Origine Du Concept

Cette architecture est née des essais réalisés en situation réelle de concert.

L'objectif n'est pas d'accélérer la navigation, mais de la rendre plus fiable dans des conditions où la précision tactile est réduite : médiator, transpiration, stress, mouvements.

Toute implémentation future devra préserver cette philosophie.

---

## Statut

Ce document ne valide pas encore une implémentation.

Il décrit une orientation d'architecture afin de préserver cette idée pour les futures évolutions de SMP.
