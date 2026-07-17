# 💾 FEATURE — BACKUP V2

## Évolution du système de sauvegarde de la bibliothèque

---

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

## 2. Synchroniser une sauvegarde

Nouvelle fonctionnalité.

Objectif :

Mettre à jour une sauvegarde existante.

Aucun nouveau dossier n'est créé.

La sauvegarde existante est reconstruite afin de refléter exactement l'état actuel de la bibliothèque.

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
- prise en compte du prompteur ;
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

Synchroniser la sauvegarde

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

Fonctionnalité prévue pour une future version.

Aucune implémentation dans la version actuellement en cours de stabilisation pour la bêta Play Store.

---

# Philosophie finale

Une sauvegarde doit être aussi simple à utiliser qu'un "Ctrl+S", tout en restant aussi fiable qu'un export complet.
