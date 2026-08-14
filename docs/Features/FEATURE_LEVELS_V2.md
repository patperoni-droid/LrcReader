# FEATURE — LEVELS V2 — HISTORIQUE

## Statut

**Document historique — roadmap clôturée.**

Ce fichier conserve la trace de la migration de l'ancienne interface de préparation des niveaux vers `LEVELS`.

Il ne constitue plus une roadmap active et ne doit pas être utilisé pour définir une fonctionnalité future.

La référence fonctionnelle actuelle est :

```text
docs/Features/FEATURE_LEVELS.md
```

---

## Décision Produit Définitive

Le 14 août 2026, le Créateur a confirmé que :

- l'ancien système LUFS est complètement abandonné ;
- `LEVELS` le remplace comme unique fonctionnalité de préparation des niveaux ;
- `LEVELS` repose sur l'écoute et le réglage manuel du niveau de chaque morceau ;
- aucune évolution future ne doit réintroduire LUFS comme fonction ou promesse produit.

---

## Résultat De La Migration

La migration a notamment conduit à :

- renommer l'onglet en `LEVELS` ;
- supprimer du parcours principal les actions collectives d'application et de retrait automatiques ;
- afficher le niveau mémorisé en dB ;
- conserver le démarrage rapide à `Début`, `20 s`, `40 s`, `60 s` et `90 s` ;
- intégrer le Playback Control officiel ;
- réutiliser le tiroir de gain partagé avec le Player ;
- recentrer la préparation sur une comparaison à l'oreille.

---

## Héritage Technique

Des noms internes et des données issus de l'ancien système subsistent dans le code pour des raisons historiques et de compatibilité.

Leur présence ne rouvre pas cette roadmap. Toute migration ou suppression de ces éléments doit faire l'objet d'un chantier de code séparé, sans perte des niveaux déjà mémorisés.

---

## Règle

Ne pas ajouter de nouvelle étape à ce document.

Documenter toute évolution fonctionnelle de `LEVELS` dans `FEATURE_LEVELS.md` et prioriser tout chantier futur éventuel dans `docs/BACKLOG.md`.
