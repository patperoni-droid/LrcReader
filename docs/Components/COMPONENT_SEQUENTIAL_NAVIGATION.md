# COMPONENT — Sequential Navigation

## Mission

Sequential Navigation est un composant officiel de Stage Music Player (SMP).

Sa mission est de déplacer la **sélection officielle de la playlist** de manière séquentielle, sans interaction directe avec les éléments affichés.

Il est totalement indépendant :

- de l'interface utilisateur ;
- du périphérique ;
- du moteur audio.

Il représente une intention métier de SMP.

---

# Philosophie

Sequential Navigation ne sert pas à lire le morceau suivant.

Il sert à **déplacer le curseur de sélection dans la playlist**.

Cette distinction est fondamentale.

La lecture reste entièrement sous la responsabilité de PlaybackControl.

Le fonctionnement est toujours :

```
Sélection
      ↓
PlaybackControl
      ↓
Lecture
```

La navigation ne déclenche jamais une lecture automatiquement.

Lorsque le mode AUTO du PlaybackControl est armé, Sequential Navigation conserve exactement la même mission : elle prépare uniquement une sélection. Le PlaybackControl peut utiliser cette sélection comme prochaine cible, mais seulement lorsque le morceau actif atteint sa fin effective.

---

# Objectifs

Le composant vise à :

- améliorer la fiabilité de la navigation en concert ;
- réduire les erreurs de sélection ;
- permettre un point d'appui naturel sur le bord de la tablette ;
- limiter les erreurs liées au stress, à la transpiration ou aux tremblements ;
- préparer les futures interfaces de contrôle (Bluetooth, MIDI, pédales, clavier, etc.).

La priorité est la précision du geste, pas la rapidité.

---

# Fonctionnement

Chaque pression sur :

- ▲
- ▼

déplace la sélection d'un élément.

La navigation parcourt la playlist exactement dans l'ordre où elle est affichée.

Elle ne fait aucune différence entre les types d'éléments.

Elle peut donc sélectionner :

- un morceau ;
- un groupe ;
- un prompteur ;
- ou tout autre élément présent dans la playlist.

Sequential Navigation parcourt uniquement la sélection.

Il ne décide jamais de la lecture.

---

# Interaction avec PlaybackControl

PlaybackControl reste entièrement responsable de la lecture.

Lorsque l'utilisateur appuie sur **Play** :

- si la sélection est un morceau, ce morceau est joué ;
- si la sélection est un groupe, le groupe est lancé selon le comportement officiel de SMP ;
- les autres types d'éléments sont traités selon leur logique propre.

Sequential Navigation ne connaît pas ces comportements.

Il se contente de déplacer la sélection.

Même avec AUTO activé, il ne surveille pas la fin du morceau et ne lance jamais lui-même le moteur audio.

---

# Défilement automatique

Lorsque la sélection atteint la limite visible de la playlist :

- la playlist défile automatiquement ;
- la sélection reste toujours visible ;
- la navigation continue tant qu'il reste des éléments à parcourir.

Ainsi, l'intégralité de la playlist peut être parcourue uniquement avec les commandes ▲ ▼.

---

# Interfaces compatibles

Le comportement métier est unique.

Il pourra être utilisé par :

- commandes tactiles ;
- tablette ;
- pédale Bluetooth ;
- pédalier MIDI ;
- clavier ;
- télécommande ;
- Android Auto ;
- toute future interface SMP.

Toutes ces interfaces utilisent exactement le même composant.

Aucune ne possède sa propre logique de navigation.

---

# Présentation selon le périphérique

## Téléphone

Le PlaybackControl reste inchangé.

La navigation continue principalement par sélection tactile.

Aucune commande supplémentaire n'est affichée.

La priorité est la compacité.

---

## Tablette

Le PlaybackControl est présenté dans une barre de contrôle élargie utilisant toute la largeur disponible.

Les commandes ▲ ▼ sont intégrées dans cette barre afin d'offrir une navigation plus confortable pendant les concerts.

Cette présentation améliore le confort d'utilisation sans modifier le comportement du composant.

Le fonctionnement reste strictement identique à celui du téléphone.

---

# Règles d'architecture

- une seule logique officielle de navigation ;
- aucune dépendance à l'interface utilisateur ;
- aucune dépendance au moteur audio ;
- aucune duplication selon le périphérique ;
- séparation stricte entre **Sélection** et **Lecture** ;
- PlaybackControl reste le seul responsable du lancement de la lecture.

---

# Origine du composant

Sequential Navigation est né des essais réalisés en situation réelle de concert.

L'objectif n'était pas de rendre la navigation plus rapide.

L'objectif était de la rendre **plus fiable**.

En pratique, le musicien peut poser sa main sur le bord de la tablette et déplacer la sélection avec les commandes ▲ ▼ sans avoir à viser précisément les lignes de la playlist.

Une fois l'élément souhaité sélectionné, il lui suffit d'appuyer sur **Play**.

Cette approche réduit significativement les risques de mauvaise sélection lorsque les conditions de scène rendent le tactile moins précis.

Sequential Navigation est donc un composant de **navigation de scène**, conçu pour offrir une sélection sûre, prévisible et confortable en utilisation live.

---

# Règle de conception

PlaybackControl conserve toujours la même géométrie.

Les positions des commandes ne doivent jamais varier selon l'écran affiché.

Lorsqu'une fonctionnalité n'est pas disponible (par exemple Sequential Navigation sur un écran ne possédant pas de sélection de playlist), les commandes restent visibles mais apparaissent désactivées.

Les commandes ne sont jamais supprimées si cela modifie l'équilibre visuel du composant.

Cette règle permet de préserver la mémoire musculaire de l'utilisateur et garantit une expérience stable, prévisible et rassurante pendant les concerts.

Principe SMP :

> Les fonctions peuvent varier selon le contexte.
>
> Les commandes, elles, restent toujours à leur place.
>
> Lorsque la lecture est déclenchée via le bouton Play, PlaybackControl lance toujours l'élément actuellement sélectionné dans la playlist officielle de SMP.
