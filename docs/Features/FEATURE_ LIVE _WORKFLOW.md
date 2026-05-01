# 🎤 FEATURE — LIVE WORKFLOW

---

## 🎯 OBJECTIF

Garantir une expérience **fluide, sans stress et sans blanc** pendant un live.

Permettre à un musicien solo de :

- enchaîner les morceaux sans interruption
- anticiper le prochain titre
- garder le contrôle total du timing
- éviter toute manipulation complexe en situation réelle

👉 priorité absolue :

Zéro stress > Zéro blanc > Fluidité

---

## 🧠 PRINCIPES FONDAMENTAUX

### 🔥 Lecture fiable

- ExoPlayer = source de vérité du temps (`timeMs`)
- aucun traitement lourd pendant la lecture
- aucune dépendance externe en live

---

### ⚡ Anticipation

- toujours préparer le prochain morceau AVANT la fin
- l’utilisateur doit pouvoir agir sans naviguer

---

### 🛑 Zéro blanc

- aucun silence entre deux morceaux
- transition immédiate ou contrôlée

---

## ⏱️ MÉCANISME -10 SECONDES

### Fonction

À **-10 secondes avant la fin du morceau** :

retour automatique vers l’interface de sélection

---

### Objectif

- permettre de choisir le prochain morceau
- éviter la précipitation de dernière seconde

---

### Comportement attendu

- affichage de la playlist active
- ou bibliothèque si aucune playlist
- jamais bloqué dans un autre mode (ex : recherche)

---

## 🔍 GESTION DE LA RECHERCHE

### Problème résolu

Avant :

recherche active → bloque la playlist

---

### Solution

Quand un morceau est lancé depuis la recherche :

✔ la recherche se ferme immédiatement  
✔ retour à l’état normal

---

### Résultat

- à -10 secondes → playlist visible
- sélection du prochain morceau immédiate

---

## 🎶 SÉLECTION DU PROCHAIN MORCEAU

### Modes possibles

- playlist classique
- liste live (en construction)
- bibliothèque

---

### Règles

✔ action en 1 clic  
✔ aucune navigation complexe  
✔ toujours visible à temps

---

## 🔁 ENCHAÎNEMENT

### Manuel

- utilisateur sélectionne le prochain morceau
- déclenche lecture

---

### Automatique (selon configuration)

- lecture enchaînée
- ou via liste live

---

## 🎚️ CROSSFADE

### Fonction

- transition douce entre deux morceaux

---

### Modes

- manuel
- automatique (mode DJ)

---

### Règles

✔ pas de coupure brutale  
✔ contrôle utilisateur prioritaire

---

## 🔇 NAPPE ANTI-BLANC

### Fonction

- lecture d’un fond sonore entre morceaux

---

### Comportement

fin morceau → nappe démarre  
nouveau morceau → nappe s’arrête

---

### Objectif

- éviter tout silence
- maintenir ambiance

---

## 🔴 INDICATEURS VISUELS

### Morceau en cours

- affichage clair
- position visible

---

### Morceau suivant

- mise en évidence possible
- anticipation visuelle

---

## 🎛️ CONTRÔLE LIVE

### Boutons essentiels

▶︎ play  
⏸ pause  
⏭ suivant  
⏮ précédent  
stop

---

### Règles

✔ gros boutons  
✔ accessibles rapidement  
✔ aucune ambiguïté

---

## ⚠️ ERREURS CRITIQUES À ÉVITER

❌ navigation complexe en live  
❌ écran bloqué (ex : recherche)  
❌ latence au lancement  
❌ silence non contrôlé  
❌ double audio

---

## 🔮 ROADMAP

### V2

- amélioration liste live
- gestion avancée du prochain morceau

---

### V3

- intégration complète mode DJ simplifié
- enchaînements intelligents

---

### V4

- automatisation avancée du live
- scénarios personnalisés

---

## 🎯 PHILOSOPHIE

Le musicien doit rester concentré sur le public,  
pas sur son téléphone.

---

## 💥 RÉSUMÉ

Cette feature garantit :

- un live fluide
- sans interruption
- avec anticipation
- et un contrôle simple

👉 C’est le cœur de l’expérience utilisateur sur scène.