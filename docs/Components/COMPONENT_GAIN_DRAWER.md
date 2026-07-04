# COMPONENT — GainDrawer

## Mission

Le `GainDrawer` est le composant transversal de réglage de gain de SMP.

Sa mission est de permettre de régler rapidement le gain d'un morceau sans quitter le contexte de travail.

Le musicien doit pouvoir ajuster le niveau réel du morceau pendant qu'il travaille, sans changer d'écran ni interrompre son flux.

---

## Philosophie

Le `GainDrawer` doit offrir exactement la même expérience utilisateur dans tous les écrans.

Il doit conserver :

- le même comportement ;
- la même animation ;
- la même ergonomie ;
- la même gestuelle.

L'utilisateur ne doit jamais avoir à réapprendre son fonctionnement selon l'écran dans lequel il se trouve.

Le `GainDrawer` appartient à SMP, pas à un écran particulier.

---

## Source De Vérité

Il n'existe qu'un seul `GainDrawer` officiel.

Toutes les évolutions doivent être effectuées sur ce composant unique.

Aucune duplication n'est autorisée.

Un écran peut décider quand afficher le `GainDrawer`, mais il ne doit pas créer sa propre variante locale du composant.

---

## Utilisations

Usages actuels :

- DJ ;
- Lecteur Audio / Paroles.

Usage prévu :

- LEVELS.

`LEVELS` devra utiliser le même composant que les autres écrans afin de conserver une expérience cohérente pour le réglage du gain.

---

## Règles

- Ne jamais créer une variante spécifique à un écran.
- Ne jamais modifier son ergonomie localement.
- Toujours réutiliser le composant officiel.
- Toute évolution doit rester compatible avec tous les écrans.
- Toute modification doit préserver la stabilité live de SMP.
- Le réglage de gain doit rester compréhensible sans connaissance technique.

---

## Évolutions Futures

Le `GainDrawer` pourra évoluer.

En revanche, son comportement devra rester cohérent dans toute l'application.

Les nouvelles fonctionnalités devront bénéficier automatiquement à tous les écrans qui l'utilisent.

Toute évolution du `GainDrawer` doit donc être pensée comme une évolution de composant SMP, pas comme une correction locale d'écran.
