# PlayerScreen.md

> **Statut :** Brouillon (à compléter)
>
> **Objectif :** Décrire l'architecture et les responsabilités de `PlayerScreen` afin de faciliter sa maintenance et son futur découpage en plusieurs composants, sans modifier son comportement.

---

# 1. Présentation

`PlayerScreen` est le cœur de l'application SMP.

Il rassemble actuellement une grande partie de la logique liée au lecteur, à l'affichage des informations du morceau, aux commandes de lecture et à plusieurs fonctionnalités annexes.

Au fil des évolutions du projet, ce fichier est devenu volumineux. L'objectif n'est pas de le réécrire, mais de mieux comprendre sa structure afin de pouvoir, plus tard, extraire certaines responsabilités dans des composants spécialisés.

---

# 2. Objectifs du futur refactoring

- Réduire la taille de `PlayerScreen`.
- Clarifier les responsabilités de chaque partie.
- Faciliter les évolutions futures.
- Conserver exactement le même comportement pour l'utilisateur.
- Ne pas introduire de régression.

---

# 3. Responsabilités actuelles

> À compléter au fur et à mesure.

Exemples :

- Gestion de l'écran principal du lecteur.
- Affichage des informations du morceau.
- Commandes de lecture.
- Gestion des paroles.
- Gestion de la Waveform.
- Gestion des différents panneaux.
- Dialogues.
- Menus.
- États Compose.

---

# 4. Découpage envisagé

> Liste d'idées uniquement. Aucun engagement sur l'architecture finale.

Exemples :

- `PlayerTopBar`
- `PlaybackControls`
- `WaveformPanel`
- `LyricsPanel`
- `TransportBar`
- `PlayerDialogs`
- `PlayerMenus`

Cette liste évoluera au fil des besoins.

---

# 5. Dépendances importantes

À documenter.

Exemples :

- ViewModels utilisés.
- Services.
- Gestionnaire audio.
- États partagés.
- Navigation.
- Configuration tablette / téléphone.

---

# 6. Points sensibles

Cette section servira à noter tout ce qui ne doit pas être modifié sans précaution.

Exemples :

- synchronisation du Playback ;
- interactions avec la Waveform ;
- gestion des changements de morceau ;
- cas particuliers découverts au fil des tests.

---

# 7. Historique des décisions

Nous noterons ici les choix importants afin de comprendre pourquoi certaines solutions ont été retenues.

Exemple :

- Date
- Décision
- Motivation
- Alternatives envisagées

---

# 8. Idées futures

Toutes les idées d'amélioration qui ne sont pas prioritaires seront notées ici afin de ne pas les oublier pendant le développement courant.

---

# 9. Checklist avant un futur refactoring

Avant de commencer le découpage de `PlayerScreen`, vérifier que :

- les fonctionnalités sont stables ;
- les principaux bugs sont corrigés ;
- la documentation est à jour ;
- une version de référence est disponible ;
- les scénarios de test sont définis.

---

**Remarque**

Ce document est volontairement orienté "architecture" et non "code". Il doit permettre à une personne — ou à une IA — de comprendre rapidement le rôle de `PlayerScreen` avant de modifier son implémentation.