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

### 📱 Téléphone / tablette

Retour terrain concert réel :

Le téléphone et la tablette n'ont pas le même rôle.

Téléphone :

- préparation
- édition
- répétition
- secours
- mobilité

Tablette :

- utilisation scène principale
- vision globale
- playlist visible en permanence
- paroles simultanées
- confort de lecture

👉 Le téléphone reste l'outil flexible et mobile.
👉 La tablette devient le cockpit live principal.

---

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

## 🔄 SMP SYNC — USAGE LIVE RECOMMANDÉ

Retour terrain concert réel :

Le flux recommandé est :

1. préparer sur téléphone
2. synchroniser périodiquement vers tablette
3. utiliser la tablette en live

Objectif :

- conserver le confort d'édition du téléphone
- préparer la tablette avant la scène
- disposer d'un environnement live stable et lisible
- garder le téléphone comme secours possible

Règles :

✔ la tablette doit travailler sur des données locales déjà importées
✔ aucun `.smp` ne doit être lu directement pendant le live
✔ aucune synchronisation lourde ne doit interférer avec la lecture

---

## 🎚️ UNIFORMISATION DES NIVEAUX EN CONCERT

Retour terrain concert réel :

Le critère SMP n'est pas la conformité broadcast.

Le critère SMP est :

"Éviter les gros écarts de volume perçu entre deux morceaux en concert."

Workflow recommandé :

1. utiliser `Appliquer LUFS` SMP pendant la préparation pour rapprocher automatiquement le morceau d'un niveau de référence
2. corriger ensuite à l'oreille avec le gain manuel
3. écouter immédiatement le résultat pendant la préécoute ou la lecture
4. conserver le gain manuel mémorisé par morceau

La page d'ajustement volume sert aussi d'atelier d'uniformisation des playlists :

- sauts rapides vers les passages représentatifs
- accès au refrain, passage dense ou passage dansant
- correction `+1` / `-1` audible immédiatement
- sauvegarde automatique du gain manuel

Règles live :

✔ aucune analyse lourde pendant le live
✔ analyse uniquement à l'import, sur action volontaire ou en préparation avant concert
✔ Track Console doit rester cohérente avec le niveau réellement entendu

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
- contrôle au pied Bluetooth :
  - navigation playlist
  - lancement morceau
  - réduction des manipulations écran en situation de stress ou fatigue

---

### V3

- intégration complète mode DJ simplifié
- enchaînements intelligents
- étude d'un système "Niveau Live" offline :
  - détection des passages forts
  - analyse d'environ 10 secondes représentatives
  - mesure plus proche du ressenti réel en concert
  - conservation du gain manuel utilisateur
- mode concert simplifié :
  - interface réduite
  - accès ultra rapide aux morceaux
  - moins de surcharge cognitive

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
