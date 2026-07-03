# FEATURE — LEVELS V2 Roadmap

## Objectif

Ce document décrit la feuille de route d'implémentation de `LEVELS V2`.

La mission de la page `LEVELS` est de préparer le niveau réel de chaque morceau avant le concert.

`docs/Features/FEATURE_LEVELS.md` reste le document de vision produit. Il définit la philosophie, le workflow musicien et les principes UX.

Ce document ne redéfinit pas cette vision. Il décrit uniquement les étapes de mise en œuvre.

---

## Règle De Progression

Chaque étape est indépendante.

On ne développe jamais deux étapes en même temps.

Une seule étape doit être ouverte, implémentée et validée avant de passer à la suivante.

Si une étape introduit une régression live, une confusion UX ou un comportement instable, elle doit être corrigée avant de continuer la roadmap.

Ordre obligatoire :

```text
V2.1 Renommer l'onglet
↓
V2.2 Supprimer les références LUFS visibles
↓
V2.3 Nouvelle présentation LEVELS
↓
V2.4 Évolution du panneau LEVELS
↓
V2.5 Préparation rapide du mix
```

---

## Principes Communs

- La vérité produit est le `LEVEL` mémorisé du morceau.
- Le moteur LUFS existant ne doit pas être modifié dans cette roadmap.
- Le terme LUFS devient un détail technique interne.
- La page doit rester orientée préparation du concert.
- Le Player reste la référence pour le comportement réel du niveau.
- Aucun traitement lourd ne doit être ajouté pendant la lecture.
- Les composants existants doivent être réutilisés lorsque cela est possible.
- Chaque patch doit rester minimal, testable et réversible.

---

## V2.1 — Renommer Complètement L'Onglet

### Objectif

Remplacer l'ancien nom `LUFS` par un nom représentatif de la mission réelle de cette page :

```text
LEVELS
```

L'utilisateur doit comprendre que cette page sert à préparer les niveaux des morceaux, pas à analyser des valeurs audio techniques.

### Périmètre

- Renommer l'entrée visible dans la navigation.
- Renommer le titre de l'écran si nécessaire.
- Mettre à jour les chaînes utilisateur liées au nom de l'onglet.
- Conserver le comportement existant.

### Exclusions

- Aucun changement du moteur LUFS.
- Aucun changement du stockage des niveaux.
- Aucune réorganisation profonde de l'écran.
- Aucun changement du Player.

### Critères De Validation

- L'ancien nom `LUFS` n'apparaît plus comme nom d'onglet.
- Le nouvel onglet s'appelle `LEVELS`.
- La navigation existante continue de fonctionner.
- Aucun réglage de niveau existant n'est perdu.
- Téléphone et tablette restent cohérents.

---

## V2.2 — Supprimer Les Références LUFS Visibles

### Objectif

Supprimer de l'interface utilisateur les références visibles à LUFS.

Le moteur LUFS reste inchangé.

Le terme LUFS devient un détail technique interne, non exposé au musicien.

### Périmètre

- Retirer les libellés visibles qui présentent la page comme un outil LUFS.
- Supprimer ou masquer les actions utilisateur `Appliquer LUFS` et `Retirer LUFS`.
- Mettre à jour l'aide de première ouverture pour expliquer la philosophie `LEVELS`.
- Conserver les mécanismes internes existants tant qu'ils ne perturbent pas l'UX.

### Exclusions

- Aucun refactor du moteur LUFS.
- Aucune suppression de données existantes.
- Aucune migration de stockage.
- Aucun nouvel algorithme audio.

### Critères De Validation

- L'utilisateur ne voit plus LUFS dans le parcours principal.
- Les actions `Appliquer LUFS` et `Retirer LUFS` ne structurent plus l'interface.
- L'aide de première ouverture parle de préparation du niveau de concert.
- Les réglages `LEVEL` déjà mémorisés restent disponibles.
- Le Player continue d'utiliser le niveau réel attendu.

---

## V2.3 — Nouvelle Présentation LEVELS

### Objectif

Mettre davantage en valeur les informations réellement utiles au musicien.

La page doit aider à répondre rapidement à la question :

```text
Ce morceau est-il au bon niveau par rapport aux autres ?
```

### Périmètre

- Remplacer la colonne visible `LUFS` par une colonne `LEVEL`.
- Afficher la valeur réellement utilisée par le Player.
- Mettre en avant les morceaux et leur niveau actuel.
- Conserver les commandes de lecture utiles à la préparation.
- Préserver l'écoute rapide directement accessible par appui simple sur `Lecture`.

### Exclusions

- Aucun changement de calcul audio.
- Aucun nouveau système de normalisation.
- Aucun changement du comportement du fader `LEVEL`.
- Aucun changement de stockage.

### Critères De Validation

- La liste affiche les morceaux avec leur `LEVEL` actuel.
- La valeur affichée correspond au niveau réellement utilisé par le Player.
- L'écoute rapide ouvre le choix `Début`, `20 s`, `40 s`, `60 s` et `90 s`.
- L'écran reste lisible et simple.
- Aucun vocabulaire LUFS n'est nécessaire pour utiliser la page.

---

## V2.4 — Évolution Du Panneau LEVELS

### Objectif

Réorganiser les outils existants dans une logique de préparation du concert.

Le panneau doit devenir un espace de travail clair pour écouter, comparer et ajuster les morceaux.

### Périmètre

- Regrouper les outils existants autour du workflow de préparation.
- Réutiliser les composants existants lorsque cela est possible.
- Préparer l'intégration du tiroir `LEVEL`.
- Conserver les raccourcis utiles au mix du concert.
- Garder une interface cohérente avec le Player.

### Exclusions

- Aucun refactor global de l'application.
- Aucun nouveau moteur audio.
- Aucun changement de protocole ou de format de fichier.
- Aucun traitement lourd pendant la lecture.

### Critères De Validation

- Les commandes principales servent clairement la préparation du mix.
- Le panneau ne met plus en avant une logique d'analyse automatique.
- Les fonctions existantes utiles restent accessibles.
- L'ergonomie reste stable sur téléphone et tablette.
- Les composants réutilisés gardent le même comportement que dans le Player.

---

## V2.5 — Préparation Rapide Du Mix

### Objectif

Faire de `LEVELS` un véritable atelier de préparation audio.

La page doit permettre de parcourir rapidement plusieurs morceaux, écouter des passages représentatifs, régler les niveaux et construire un mix cohérent pour le concert.

### Périmètre

- Intégrer le tiroir `LEVEL` dans le workflow principal.
- Permettre un réglage rapide du niveau du morceau sélectionné.
- Conserver l'écoute rapide comme fonction centrale.
- Améliorer la comparaison entre plusieurs titres.
- Documenter les évolutions futures sans remettre en cause la philosophie actuelle.

### Exclusions

- Aucun retour à une philosophie LUFS visible.
- Aucune uniformisation automatique comme parcours principal.
- Aucun remplacement du Player comme référence du niveau réel.
- Aucun changement destructif des réglages existants.

### Critères De Validation

- Le musicien peut choisir un morceau, lancer un passage utile et régler son `LEVEL` sans quitter le workflow.
- Le niveau mémorisé est celui utilisé par le Player.
- Les réglages restent stables entre les morceaux.
- L'écoute rapide facilite la comparaison entre titres.
- La page sert clairement la préparation du concert.

---

## Règle Finale

Il est interdit de développer plusieurs étapes simultanément.

Chaque étape doit être :

- diagnostiquée ;
- implémentée avec un patch minimal ;
- validée ;
- documentée si nécessaire ;
- commitée séparément.

La roadmap `LEVELS V2` doit rester progressive afin de préserver la stabilité live de SMP.
