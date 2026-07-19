# FEATURE — BACKGROUND SOUND

## Mission

Le **Fond sonore** est un moteur audio indépendant du Playback principal.

Il permet de diffuser une ambiance musicale avant, pendant ou après un concert sans modifier le fonctionnement du Playback principal.

Le Fond sonore n'est jamais un Playback SMP.

Il constitue un système audio complémentaire.

---

# Philosophie

Le Playback principal reste toujours l'élément central du concert.

Le Fond sonore ne remplace jamais le Playback principal.

Il assure uniquement une continuité sonore lorsque le Playback principal n'est plus actif.

---

# Les deux moteurs audio SMP

SMP possède deux moteurs audio indépendants.

## 1. Playback principal

Le Playback principal correspond au morceau actuellement joué.

Il est piloté exclusivement par :

- PlaybackControl.

Il possède :

- Play ;
- Pause ;
- Retour début ;
- Playlist ;
- Paroles ;
- Accords ;
- Timeline.

Le Playback principal est toujours prioritaire.

---

## 2. Fond sonore

Le Fond sonore possède son propre lecteur.

Il est piloté exclusivement par ses commandes locales.

Il possède notamment :

- activation ;
- lecture ;
- arrêt ;
- choix du dossier ;
- volume.

Le Fond sonore ne modifie jamais le Playback principal.

---

# Priorité

Le Playback principal possède toujours la priorité absolue.

Règle officielle :

Tout démarrage du Playback principal provoque immédiatement l'arrêt du Fond sonore.

Cette règle est systématique.

Aucune exception.

---

# États du Fond sonore

Le Fond sonore possède quatre états.

## OFF

Le système est désactivé.

Aucune lecture n'est possible.

---

## ARMÉ

L'utilisateur active le Fond sonore.

Si un Playback principal est actuellement en lecture :

le Fond sonore reste en attente.

Aucune lecture ne démarre.

---

## EN LECTURE

Le Fond sonore diffuse normalement son contenu.

---

## ARRÊT

Le Fond sonore revient à l'état OFF.

---

# Déclenchement automatique

Lorsque le Fond sonore est ARMÉ :

si le Playback principal devient inactif,

le Fond sonore démarre automatiquement.

Exemples :

- Pause ;
- fin du morceau ;
- arrêt du Playback.

---

# Arrêt automatique

Le Fond sonore s'arrête automatiquement lorsque :

- un nouveau Playback principal démarre.

Cette règle est prioritaire.

---

# PlaybackControl

PlaybackControl ne contrôle jamais le Fond sonore.

Il pilote exclusivement :

- le Playback principal.

Cette règle est valable dans toute l'application, y compris dans l'écran Fond sonore.

---

# Commandes du Fond sonore

Le lecteur Fond sonore possède son propre mini contrôleur.

Ce contrôleur est totalement indépendant de PlaybackControl.

Il permet notamment :

- Lecture ;
- Arrêt ;
- Retour début (à confirmer) ;
- navigation éventuelle.

Ces commandes ne pilotent jamais le Playback principal.

---

# Utilisation avant le concert

Le musicien peut utiliser le lecteur Fond sonore indépendamment.

Exemple :

- choisir un dossier ;
- écouter les titres ;
- préparer l'ambiance.

Le lancement manuel est autorisé tant qu'aucun Playback principal n'est actif.

---

# Utilisation pendant le concert

Scénario officiel.

Le morceau A joue.

Le musicien ouvre l'écran Fond sonore.

Il active le Fond sonore.

Le Fond sonore reste ARMÉ.

Le morceau A continue normalement.

Le musicien appuie sur Pause.

Le Playback principal devient inactif.

Le Fond sonore démarre automatiquement.

Le musicien parle au public.

Il retourne dans le Player.

Il lance le morceau B.

Le Fond sonore s'arrête immédiatement.

Le morceau B devient le Playback principal.

---

# Règles d'architecture

Le Playback principal et le Fond sonore sont deux moteurs audio indépendants.

Chaque moteur possède son propre contrôleur.

Aucun contrôleur ne change de responsabilité selon l'écran.

PlaybackControl conserve toujours la même mission :

piloter le Playback principal.

Le lecteur Fond sonore conserve toujours la même mission :

piloter exclusivement le Fond sonore.

---

# Statut

**Statut : ARCHITECTURE EN COURS DE VALIDATION**

Cette architecture constitue la référence pour toutes les évolutions futures du Fond sonore.

Les développements devront respecter :

- la priorité absolue du Playback principal ;
- la séparation stricte des deux moteurs audio ;
- l'unicité des responsabilités de chaque contrôleur.