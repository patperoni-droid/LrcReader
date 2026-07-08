# COMPONENT — Screen Shell

## Shell permanent / Contenu variable

## Principe

Les écrans SMP sont composés de deux parties distinctes :

1. Un Shell permanent.
2. Un Contenu variable.

Cette séparation permet de conserver les commandes et les repères essentiels pendant que les données de l'écran changent.

---

## Shell permanent

Le Shell regroupe les éléments qui doivent rester présents pendant toute la vie de l'écran.

Exemples :

- barre supérieure SMP ;
- barre d'onglets locale ;
- `PlaybackControl` ;
- futurs composants permanents, comme `Sequential Navigation` ou `Mini Player`.

Le Shell ne dépend jamais du contenu chargé.

Il ne doit pas disparaître lorsqu'une zone de contenu est vide, en cours de chargement ou temporairement indisponible.

---

## Contenu variable

Le contenu représente les informations propres à l'écran.

Exemples :

- Songs ;
- Lists ;
- Lyrics ;
- résultats de recherche ;
- playlists ;
- états vides ;
- scans ;
- indexations ;
- chargements.

Cette zone peut être remplacée temporairement par un écran de chargement ou un indicateur de progression.

Le remplacement du contenu ne doit pas affecter les composants du Shell.

---

## Règle fondamentale

Les opérations suivantes ne doivent jamais provoquer la disparition des composants du Shell :

- chargement ;
- scan ;
- indexation ;
- recherche ;
- filtrage ;
- rafraîchissement des données.

Seule la zone de contenu est remplacée.

---

## Ergonomie

Le maintien du Shell permet :

- de conserver les repères visuels ;
- de préserver la mémoire musculaire ;
- de garder les commandes essentielles accessibles ;
- d'éviter la sensation que l'application est bloquée.

Cette règle est particulièrement importante en situation de répétition ou de concert, où le musicien doit pouvoir continuer à agir même lorsqu'une zone de l'écran se met à jour.

---

## PlaybackControl

`PlaybackControl` est un composant du Shell.

Il ne doit jamais être placé dans une branche conditionnelle de contenu.

Il doit rester visible pendant les opérations de chargement lorsque ses actions restent valides.

Les écrans qui utilisent `PlaybackControl` doivent adapter leur contenu autour de lui, et non déplacer ou supprimer `PlaybackControl` en fonction de l'état local du contenu.

---

## Philosophie SMP

Un écran peut changer de contenu.

Le Shell, lui, reste stable.

Cette séparation constitue une règle d'architecture officielle de SMP.
