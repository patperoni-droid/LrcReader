# Roadmap

Ce dossier contient uniquement les évolutions futures du projet Stage Music Player.

La liste priorisée et la décision de ce qui constitue réellement un chantier futur sont
centralisées dans :

```text
docs/BACKLOG.md
```

Les fichiers de ce dossier servent uniquement à détailler un chantier important déjà
référencé dans le backlog.

Il ne remplace pas :

- la documentation d'architecture ;
- les fiches de release ;
- l'historique des décisions ;
- les documents de composants existants.

---

# Objectif

Conserver une vision claire des fonctionnalités prévues, sans mélanger :

- ce qui est déjà publié ;
- ce qui est en cours ;
- ce qui est seulement envisagé.

---

# Structure recommandée

Créer un fichier par sujet ou par étape majeure.

Exemples :

```text
BACKUP_V2.md
PLAYBACK_CONTROL_V3.md
GOOGLE_PLAY_BETA.md
TABLET_LIVE_MODE.md
```

Chaque fichier roadmap doit contenir au minimum :

- objectif ;
- contexte ;
- périmètre ;
- étapes prévues ;
- dépendances ;
- risques ;
- statut.

---

# Statuts recommandés

- prévu ;
- en analyse ;
- documenté ;
- en développement ;
- suspendu ;
- livré ;
- abandonné.

---

# Règle

Une roadmap décrit une intention future.

Elle ne doit pas documenter comme existant un comportement qui n'est pas encore implémenté ou publié.

Elle ne doit pas créer une seconde liste de priorités concurrente avec `docs/BACKLOG.md`.
