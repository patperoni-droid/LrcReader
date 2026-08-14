# FEATURE — LUFS PREPARATION — OBSOLÈTE

## Statut

**Document historique et obsolète. Ne pas utiliser comme spécification actuelle.**

Le 14 août 2026, le Créateur de Stage Music Player a confirmé l'abandon complet du système LUFS.

LUFS n'est plus :

- une fonctionnalité actuelle ;
- une option proposée au musicien ;
- une roadmap ;
- une cible future de Stage Music Player.

La fonctionnalité qui remplace définitivement cet ancien système est :

```text
docs/Features/FEATURE_LEVELS.md
```

---

## Raison De Conservation

Ce fichier est conservé uniquement pour :

- expliquer l'origine de certains noms techniques encore présents dans le code et les anciennes données ;
- faciliter la lecture de l'historique Git ;
- éviter qu'un ancien document ou identifiant soit interprété comme une fonction à restaurer.

Il ne doit plus être référencé par une documentation fonctionnelle actuelle.

---

## Héritage Technique

Le code peut encore contenir des identifiants `lufs`, des champs de configuration historiques et des chemins de compatibilité pour les morceaux existants.

Ces éléments ne représentent pas le produit. Ne pas les supprimer ou les migrer sans un chantier de code explicite garantissant la conservation du `LEVEL` mémorisé et la compatibilité des SongUnit existantes.

---

## Règle Définitive

Ne développer aucune nouvelle fonction LUFS.

Ne présenter aucun calcul, cible, analyse ou normalisation LUFS comme une capacité actuelle ou future de Stage Music Player.
