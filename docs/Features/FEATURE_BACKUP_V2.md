# 💾 FEATURE — BACKUP V2

## Évolution du système de sauvegarde de la bibliothèque

---

## Statut au 11 août 2026

Ce document conserve la cible historique d'une sauvegarde de travail complète. Le comportement
actuellement implémenté et validé reste défini dans
[FEATURE_EXPORT_BACKUP.md](FEATURE_EXPORT_BACKUP.md), et le cycle produit dans
[FEATURE_LIBRARY_BACKUP.md](FEATURE_LIBRARY_BACKUP.md).

Déjà livré, sous une forme plus simple que la première hypothèse de ce document :

- le nom du dossier de sauvegarde est proposé automatiquement et reste modifiable ;
- `state.json` conserve l'état applicatif et les playlists ;
- les modes Morceaux et Playlists `Conserver / Remplacer` sont indépendants.
- une sauvegarde complète réussie devient une référence de travail ;
- l'action **Mettre à jour la bibliothèque** republie de façon sûre les Familles SongUnit
  courantes dans le même dossier.

Toujours non implémenté dans la mise à jour minimale :

- republier l'État global (`state.json`, playlists, groupes et textes défilants) ;
- retirer les Familles supprimées de la sauvegarde ;
- détecter et afficher l'état « à jour » ou « modifié » ;
- appliquer la structure cible `SMP/` ou `DATA/`.

Les hypothèses de nommage du JSON et de sous-dossiers ci-dessous doivent donc être
rediagnostiquées avant développement. Elles ne décrivent pas le format courant.

# Objectif

Transformer le système actuel de sauvegarde en un véritable outil de travail.

La philosophie est simple :

> La bibliothèque interne est la source de vérité.

Les sauvegardes sont des copies fidèles de cette bibliothèque.

Une sauvegarde ne doit jamais devenir plus importante que la bibliothèque elle-même.

---

# Principes

Deux usages doivent être clairement séparés.

## 1. Nouvelle sauvegarde

Créer une nouvelle sauvegarde indépendante.

Utilisée pour :

- archiver une version ;
- préparer un concert ;
- transférer la bibliothèque ;
- conserver un historique.

Chaque sauvegarde crée un nouveau dossier Export daté.

Exemple :

Export_2026-07-18_21-35

Ce fonctionnement est conservé.

---

## 2. Mettre à jour une sauvegarde

Cible complète, partiellement livrée.

Objectif :

Mettre à jour une sauvegarde existante.

Aucun nouveau dossier n'est créé.

La version minimale actuelle republie les Familles SongUnit. La cible reste de reconstruire la
sauvegarde existante afin qu'elle reflète exactement l'état actuel de la bibliothèque.

Cette fonction joue le rôle d'un "Ctrl+S".

---

# Création d'une sauvegarde

Workflow futur :

1.
Choisir le dossier de destination.

2.
Donner un nom à la sauvegarde.

Exemple :

Concert été 2026

Ce nom devient le nom du fichier JSON principal.

Exemple :

Concert été 2026.json

Le dossier Export continue d'être nommé automatiquement avec la date.

Exemple :

Export_2026-07-18_21-35

---

# Organisation des fichiers

Version cible.

Export_2026-07-18_21-35/

    SMP/
        morceau1.smp
        morceau2.smp
        morceau3.smp
        ...

    Concert été 2026.json

Objectifs :

- regrouper toutes les SongUnit (.smp) dans un dossier dédié ;
- rendre immédiatement visible le fichier JSON principal ;
- préparer une structure évolutive.

---

# Évolution future

Lorsque les playlists deviendront des fichiers séparés :

Export_2026-07-18_21-35/

    SMP/
        morceau1.smp
        morceau2.smp
        ...

    DATA/
        Concert été 2026.json
        playlists.json
        settings.json
        ...

Le dossier DATA n'est pas obligatoire pour la première version.

Il est simplement prévu afin de faciliter les évolutions futures.

---

# Synchronisation

La synchronisation ne réalise jamais une mise à jour partielle.

Elle reconstruit entièrement la sauvegarde.

Concrètement :

- reconstruction complète du JSON ;
- reconstruction complète de tous les fichiers .smp ;
- ajout des nouveaux morceaux ;
- suppression des morceaux supprimés ;
- prise en compte des modifications des paroles ;
- prise en compte des accords ;
- prise en compte des contenus de prompteur liés aux morceaux et des textes défilants autonomes ;
- prise en compte des futures métadonnées.

Le dossier Export devient ainsi une image fidèle de la bibliothèque.

---

# Ce qui est volontairement évité

Aucun système de delta.

Aucune synchronisation incrémentale.

Aucun patch.

La priorité reste :

Fiabilité avant optimisation.

---

# Philosophie utilisateur

Le musicien travaille.

Il modifie.

Il ajoute des morceaux.

Il modifie des paroles.

Puis il appuie simplement sur :

Mettre à jour la bibliothèque

Sans avoir à recréer une nouvelle sauvegarde.

---

# Source de vérité

Toujours :

Bibliothèque interne SMP

Jamais :

Une sauvegarde.

Les sauvegardes sont uniquement des copies de sécurité.

---

# Priorité

La version minimale Familles SongUnit est livrée. L'extension à l'État global, aux suppressions et
à l'indication fiable de l'état de sauvegarde reste un chantier priorisé dans `docs/BACKLOG.md`.

---

# Philosophie finale

Une sauvegarde doit être aussi simple à utiliser qu'un "Ctrl+S", tout en restant aussi fiable qu'un export complet.

---

# Diagnostic d'architecture et décisions retenues

## Architecture générale

BACKUP V2 est compatible avec l'architecture SMP.

La bibliothèque interne reste toujours la seule source de vérité.

Une sauvegarde n'est jamais utilisée comme état de travail.

Elle reste uniquement une copie reconstruite depuis la bibliothèque interne.

## Rétrocompatibilité

La rétrocompatibilité est obligatoire.

Les sauvegardes existantes doivent continuer à fonctionner.

La restauration devra donc reconnaître plusieurs structures de sauvegarde.

Les anciens exports ne devront jamais être cassés.

## Organisation future

Organisation actuellement retenue :

Export_yyyy-MM-dd_HH-mm/

    SMP/
        *.smp

    Nom choisi.json

Évolution future prévue :

Export_yyyy-MM-dd_HH-mm/

    SMP/
        *.smp

    DATA/
        Nom choisi.json
        playlists.json
        settings.json
        ...

Le dossier DATA est une évolution prévue mais n'est pas obligatoire pour la première version.

## Nom du JSON

Le dossier Export conserve son nom automatique avec la date.

En revanche le fichier JSON principal devient personnalisable.

Exemple :

Concert été 2026.json

Le but est de rendre immédiatement identifiable le contenu de la sauvegarde.

## Mise à jour complète d'une sauvegarde — cible

La synchronisation ne réalise jamais une mise à jour incrémentale.

Elle reconstruit entièrement la sauvegarde existante.

Elle :

- recrée le JSON ;
- régénère tous les .smp ;
- ajoute les nouveaux morceaux ;
- retire ceux qui n'existent plus dans la bibliothèque ;
- met à jour toutes les données (paroles, accords, contenus de prompteur liés aux morceaux,
  textes défilants autonomes, etc.).

La sauvegarde devient ainsi une image fidèle de la bibliothèque.

## Variantes Arrangement virtuelles

Une variante Arrangement virtuelle n'est pas un morceau audio autonome. Elle dépend du `songId` et du fichier audio de son titre parent.

La représentation de sauvegarde retenue est donc :

- le titre parent produit un seul fichier `.smp` ;
- ce fichier transporte l'audio une seule fois ;
- `arrangement.json` conserve le projet Arrangement courant du parent ;
- `arrangement_variants.json` regroupe les variantes virtuelles du parent avec leur identifiant,
  leur titre, leur Structure, leurs paroles et brouillons bruts, accords, couleurs, Timeline,
  annotations, MIDI/DMX, grille, contenu de prompteur lié au morceau, titre personnalisé et
  réglages de lecture pris en charge ;
- aucun `.smp` incomplet n'est produit pour une variante seule.

La restauration normalise d'abord le parent, puis recrée ses variantes comme entrées distinctes de la Bibliothèque. Un ancien `.smp` sans ces fichiers continue à être accepté.

## Restauration

Ne pas confondre :

Synchroniser une sauvegarde

et

Restaurer une bibliothèque.

La synchronisation concerne uniquement le dossier Export.

La restauration dans SMP ne devient pas destructive.

Elle continue à appliquer les règles de restauration existantes.

## Synchronisation multi-appareils

BACKUP V2 ne remplace pas SMP Sync.

Les deux mécanismes restent totalement indépendants.

BACKUP V2 concerne une copie externe.

SMP Sync reste le mécanisme de transfert entre appareils.

Aucune fusion automatique n'est prévue.

## Point de vigilance

Le point technique le plus sensible concerne la synchronisation d'un dossier SAF existant.

Toute future implémentation devra garantir qu'aucune suppression ne puisse sortir du périmètre du dossier Export concerné.

La sécurité des données est prioritaire sur les performances.

## Plan historique à réévaluer

Découper BACKUP V2 en plusieurs étapes indépendantes.

### V2.1

- nom personnalisable : livré pour le dossier de sauvegarde, pas pour le JSON ;
- organisation des .smp dans un dossier dédié ;
- restauration compatible ancien et nouveau format.

### V2.2

- mémorisation de la sauvegarde de travail : livrée sous forme minimale ;
- republication sûre des Familles SongUnit : livrée sous forme minimale.

### V2.3

- mise à jour complète d'une sauvegarde existante, État global et suppressions compris.
