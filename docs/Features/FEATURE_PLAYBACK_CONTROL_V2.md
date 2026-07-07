# FEATURE — Playback Control V2

## Objectif

Cette feuille de route décrit les étapes d'introduction du composant officiel `Playback Control`.

Le document de référence du composant est :

```text
docs/Components/COMPONENT_PLAYBACK_CONTROL.md
```

Le Player reste l'écran complet et l'autorité live.

Le `Playback Control` devient progressivement le centre de contrôle compact et réutilisable de la lecture SMP.

---

## Règles De Roadmap

- une seule étape est développée à la fois ;
- chaque étape doit être validée avant de commencer la suivante ;
- aucune ergonomie ne doit être généralisée avant validation terrain ;
- aucun développement n'est prévu pour DJ dans cette roadmap ;
- DJ conserve pour l'instant sa propre ergonomie ;
- rien n'est décidé concernant Arrangement ;
- la question d'Arrangement sera évaluée plus tard.

---

## V2.1 — Prototype Visuel Dans Le Player

### Objectif

Valider l'ergonomie du futur `Playback Control` dans le Lecteur Audio / Paroles avant toute extraction en composant partagé.

### Périmètre

- prototyper le layout dans le contexte Player ;
- conserver le Player comme écran complet ;
- respecter la composition V1 du composant ;
- conserver les comportements existants ;
- conserver la stabilité live ;
- conserver le comportement musical attendu.

### Exclusions

- aucune création de composant partagé ;
- aucun déploiement dans les autres écrans ;
- aucun changement dans DJ ;
- aucune décision concernant Arrangement ;
- aucune généralisation avant validation.

### Critères De Validation

- le musicien comprend immédiatement le rôle du composant ;
- les commandes principales restent accessibles ;
- le Player conserve ses fonctions avancées ;
- la lecture reste stable ;
- l'ergonomie téléphone est prioritaire et validée.

### Etat

Prototype visuel engagé dans le Player.

---

## V2.2 — Validation Terrain

### Objectif

Valider le `Playback Control` en utilisation réelle avant toute extension.

### Périmètre

- tester en répétition ;
- tester en conditions proches concert ;
- vérifier la lisibilité ;
- vérifier la taille des zones d'action ;
- vérifier la cohérence avec le Player existant.

### Exclusions

- aucune nouvelle fonctionnalité ;
- aucun déploiement dans d'autres écrans ;
- aucun changement d'architecture.

### Critères De Validation

- le composant est compris sans explication ;
- le bouton principal Lecture / Pause ne crée pas de confusion ;
- le bouton Retour Début est compris comme une action de position ;
- le réglage rapide du gain est utile sans remplacer le `GainDrawer` ;
- aucun geste fréquent du Player n'est dégradé.

---

## V2.3 — Réglage Rapide Du Gain

### Objectif

Valider et affiner le réglage rapide du gain dans le `Playback Control`.

### Périmètre

- permettre une correction rapide du gain ;
- afficher le gain courant ;
- conserver le `GainDrawer` comme réglage précis ;
- vérifier que les deux composants se complètent.

### Exclusions

- aucune modification du `GainDrawer` officiel ;
- aucun changement de logique audio ;
- aucun changement dans DJ ;
- aucune duplication de réglage.

### Critères De Validation

- le réglage rapide est assez évident pour être utilisé en répétition ;
- le musicien comprend que le `GainDrawer` reste disponible pour l'ajustement précis ;
- le gain affiché reste cohérent avec le niveau réel du morceau ;
- aucune confusion n'apparaît entre correction rapide et réglage précis.

---

## V2.4 — Déploiement Progressif Dans Les Autres Écrans

### Objectif

Étudier l'intégration progressive du `Playback Control` dans d'autres écrans après validation utilisateur.

### Périmètre

- évaluer écran par écran ;
- conserver une ergonomie identique ;
- ne déployer que là où le composant apporte un vrai gain d'usage ;
- préserver la lisibilité de chaque écran.

### Exclusions

- aucun développement pour DJ ;
- aucune décision concernant Arrangement ;
- aucune intégration automatique dans tous les écrans ;
- aucune variante locale du composant.

### Critères De Validation

- l'écran reste plus simple avec le composant qu'avant ;
- le composant ne masque pas d'information importante ;
- la navigation reste claire ;
- la lecture reste contrôlable sans revenir systématiquement au Player ;
- l'ergonomie reste identique à celle validée dans le Player.

---

## Principe Final

Le `Playback Control` doit progresser par validation réelle.

Il ne doit jamais devenir un élément décoratif ou envahissant.

Sa valeur est de donner un contrôle immédiat de la lecture, partout où cela aide réellement le musicien.
