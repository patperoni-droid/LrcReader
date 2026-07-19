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

## V2.5 — Enchaînement Automatique Live

### Objectif

Permettre au musicien d'armer, sur tablette uniquement, l'enchaînement du prochain titre depuis le `Playback Control`, sans intervention à la fin du morceau en cours.

### Périmètre

- ajouter sur tablette un bouton AUTO distinct de Play et Pause ;
- conserver le bouton Play jaune comme indicateur du morceau préparé ;
- utiliser le morceau préparé en priorité, sinon le prochain morceau jouable de la playlist ;
- respecter une cible `Define Next` explicitement définie ;
- lancer la cible dès la fin effective du morceau actif ou à son point OUT ;
- utiliser exclusivement le pipeline de transition live officiel ;
- rendre l'état AUTO visible et stable pendant toute la session.

### Exclusion Téléphone

- aucun bouton AUTO n'est ajouté au Playback Control téléphone ;
- aucune place supplémentaire n'est consommée dans l'interface téléphone ;
- le comportement historique téléphone reste inchangé ;
- toute implémentation doit vérifier explicitement l'absence de régression sur téléphone.

### Règle Musicale

AUTO ne doit ajouter aucun temps entre les titres.

Aucun délai, silence, compte à rebours ou système de détection vocale n'est introduit.

Les respirations entre les titres proviennent uniquement du contenu musical existant : blanc enregistré, fin longue ou introduction du morceau suivant.

### Sécurité Live

- AUTO est désactivé par défaut après un redémarrage complet de l'application ;
- désarmer AUTO n'interrompt jamais le morceau actif ;
- une sélection préparée ne démarre jamais avant la fin du morceau, sauf appui manuel sur Play ;
- aucune cible invalide ou ambiguë ne doit être lancée ;
- l'automatisme ne dépend jamais de l'état visuel de la playlist ;
- les règles de gain, pitch, speed, timeline et transition restent intégralement appliquées.

### Validation Requise Avant Implémentation

- enchaînement normal de plusieurs titres ;
- choix d'un autre prochain titre pendant la lecture ;
- priorité de `Define Next` ;
- point OUT et fin naturelle ;
- groupes et éléments non jouables ;
- changement d'écran et recomposition ;
- désactivation de AUTO pendant la lecture ;
- absence de cible valide ;
- transitions avec gain, pitch ou speed non neutres ;
- validation fonctionnelle sur tablette réelle ;
- validation de non-régression sur téléphone réel, sans bouton AUTO visible.

### État

Architecture fonctionnelle documentée. Implémentation non commencée.

---

## Principe Final

Le `Playback Control` doit progresser par validation réelle.

Il ne doit jamais devenir un élément décoratif ou envahissant.

Sa valeur est de donner un contrôle immédiat de la lecture, partout où cela aide réellement le musicien.
