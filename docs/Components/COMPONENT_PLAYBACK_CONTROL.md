# COMPONENT — Playback Control

## Mission

Le `Playback Control` est le centre de contrôle universel de la lecture de SMP.

Il permet au musicien de contrôler immédiatement le morceau en cours sans revenir systématiquement dans le Player.

Le Player reste l'écran complet.

Le `Playback Control` est un composant réutilisable.

---

## Philosophie

Le `Playback Control` ne remplace pas le Player.

Le Player conserve :

- paroles ;
- accords ;
- timeline détaillée ;
- Define Next ;
- fonctions avancées.

Le `Playback Control` fournit uniquement les commandes utilisées très fréquemment pendant une répétition ou un concert.

Il doit permettre de garder le contrôle musical sans changer de contexte.

---

## Principes SMP

- composant officiel ;
- même ergonomie partout ;
- toujours reconnaissable ;
- compact ;
- priorité téléphone ;
- compatible tablette ;
- aucune duplication de logique.

Le `Playback Control` appartient à SMP, pas à un écran particulier.

---

## Composition V1

Le composant comprend :

- barre de progression ;
- temps courant ;
- temps total, ou temps restant selon évolution ;
- bouton Retour Début ;
- bouton principal Lecture / Pause ;
- réglage rapide du gain ;
- affichage du gain courant.

Cette composition représente le premier périmètre fonctionnel du composant.

---

## Règle Du Bouton Principal

Le bouton principal est un carré.

Sa couleur indique l'état global de lecture.

### Etat 1

Carré vert.

Icône :

```text
▶
```

Action :

Lecture ou reprise.

### Etat 2

Carré rouge.

Icône :

```text
⏸
```

Action :

Pause.

Une nouvelle pression reprend exactement au même endroit.

Le bouton principal ne remet jamais le morceau au début.

---

## Règle Du Bouton Retour

Le bouton Retour remet le morceau au début.

Sa mission est uniquement de gérer la position.

Il ne modifie jamais l'état Lecture / Pause.

Comportement attendu :

- si le morceau est en lecture, le bouton revient à `0:00` et la lecture continue ;
- si le morceau est en pause, le bouton revient à `0:00` et le Player reste en pause ;
- si aucun morceau n'est chargé, le bouton ne fait rien.

Le bouton Retour ne déclenche jamais une lecture, une pause ou une reprise.

---

## Réglage Rapide Du Gain

Le `Playback Control` permet un réglage rapide :

```text
[-]  +4 dB  [+]
```

Le `GainDrawer` reste le réglage précis.

Les deux composants se complètent.

Le `Playback Control` sert à corriger vite.

Le `GainDrawer` sert à ajuster finement.

Le réglage rapide du `Playback Control` pilote exactement la même valeur de gain que le `GainDrawer`.

Il n'existe pas de gain propre au `Playback Control`.

Chaque appui sur `[-]` ou `[+]` applique un pas de `1 dB` via la logique officielle de réglage du gain du morceau courant.

---

## Premier Périmètre

Premier écran d'intégration :

- Lecteur Audio / Paroles.

Les autres écrans seront évalués après validation de cette première intégration.

Cette règle ne fige pas de liste d'écrans exclus.

---

## Principes UX

Le `Playback Control` doit :

- être immédiatement identifiable ;
- être utilisable sans apprentissage ;
- privilégier les gestes les plus fréquents ;
- ne jamais masquer les informations importantes ;
- rester compact.

Il doit être assez discret pour exister dans plusieurs contextes, mais assez clair pour être reconnu instantanément pendant un concert.
