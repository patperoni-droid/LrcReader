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

### 🚀 Onboarding bêta

Sur une installation vierge de la bêta publique :

- le Bus principal est visible par défaut
- le Mode DJ est visible par défaut
- ces valeurs par défaut ne s'appliquent que si aucune préférence utilisateur n'existe encore
- une tablette détectée active automatiquement le mode split au premier lancement
- une préférence tablette déjà enregistrée n'est jamais écrasée

Principe :

✔ téléphone = interface téléphone  
✔ tablette = expérience split par défaut  
✔ les choix existants de l'utilisateur restent prioritaires

### 🧭 Cockpit tablette

En mode tablette, SMP peut utiliser un Split Layout :

- panneau gauche : Playlist
- panneau droit : destination active

Destination principale :

- Paroles défilantes

Destinations compatibles :

- Bus principal
- Lecteur / Paroles
- Bibliothèque
- Paramètres
- Fond sonore
- DJ
- Accordeur
- Éditeur de paroles
- autres panneaux compatibles plus tard

Le menu cockpit est le mécanisme privilégié pour naviguer entre ces destinations.

Barre SMP tablette :

- la barre supérieure commune est affichée dans le panneau droit
- elle change uniquement la destination active du panneau droit
- elle ne doit pas modifier la logique métier téléphone
- elle donne accès à :
  - Bus principal
  - Lecteur / Paroles
  - Fond sonore
  - DJ
  - Bibliothèque
  - Accordeur
  - Recherche
  - menu trois points SMP
  - roue cockpit
- la Playlist n'a pas d'icône dans cette barre car elle reste visible en permanence à gauche

Règles :

✔ `Paroles` signifie toujours `Playlist | Paroles défilantes`  
✔ `Paroles` ne signifie pas l'éditeur de paroles  
✔ aucun panneau droit ne doit bloquer le retour vers les paroles  
✔ les écrans nécessitant toute la largeur, comme Arrangement ou Timeline, restent en Fullscreen  
✔ le téléphone reste la base stable ; la tablette est une extension conditionnée au mode tablette/split
✔ le split tablette est une variante de présentation, pas une logique métier séparée

Bibliothèque tablette :

- l'en-tête compact regroupe `Songs / Lists / Lyrics / LUFS / dossier / menu / cockpit`
- le titre `Bibliothèque` et le sous-titre sont masqués en mode tablette compact pour gagner de la hauteur
- les lignes de morceaux ne sont pas compactées pour l'instant à cause d'un risque runtime déjà rencontré
- depuis la playlist gauche, l'icône dossier doit ouvrir la navigation dossiers / Bibliothèque dans le panneau droit
- cette action ne doit pas sortir du split tablette
- la Playlist doit rester visible à gauche pendant la navigation dossiers

Recherche tablette :

- Recherche est un mode de filtre de l'écran actif
- Recherche n'est pas une destination autonome
- Bibliothèque + Recherche filtre la Bibliothèque
- Playlist + Recherche filtre la Playlist

Édition paroles tablette :

- l'éditeur de paroles peut activer un mode focus uniquement en split tablette
- quand le clavier Android est ouvert, les éléments secondaires peuvent être masqués pour récupérer de la hauteur
- la barre SMP, les onglets `Paroles / Synchro` et `Afficher les timings` sont restaurés quand le clavier est fermé
- l'utilisateur reste dans l'onglet Édition
- le texte en cours ne doit pas être perdu
- le téléphone reste inchangé

Installation initiale :

- après validation SAF Android, SMP affiche un état de préparation du workspace
- l'initialisation SAF du workspace s'exécute hors thread UI
- l'utilisateur ne doit pas voir d'écran noir pendant cette préparation
- la préparation initiale doit rester claire même si Android met plusieurs secondes à finaliser l'accès dossier

Mode DJ bêta :

- le premier lancement DJ est simplifié
- la carte automatique `Activer le mode DJ` n'est plus imposée
- aucun scan DJ long n'est déclenché automatiquement au premier affichage
- si la permission audio Android manque, elle est demandée automatiquement à l'ouverture du DJ
- après acceptation, l'arborescence MediaStore est chargée et affichée directement
- le bouton `Folder` reste disponible pour lire ou ajouter rapidement un dossier complet à la file DJ
- le dossier DJ dédié, le scan manuel, le cache et l'index restent disponibles comme fonctions avancées
- la séparation DJ / Backing Track reste stricte : le DJ garde son moteur et son bus séparés

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

Contrôles de gain live dans le lecteur paroles :

- même logique métier téléphone/tablette
- même gain manuel mémorisé par morceau
- même workflow existant `currentTrackGainDb` / `onLiveGainDelta(...)`
- aucun modèle de gain séparé selon l'appareil

Tablette split :

- fader permanent visible dans le panneau droit
- accès immédiat pendant la lecture et la consultation des paroles

Téléphone :

- fader dans un tiroir latéral droit
- tiroir fermé par défaut
- poignée/flèche discrète sur le bord droit du Player
- fermeture/ouverture par la poignée
- le tiroir peut rester ouvert lors des changements de morceaux
- changement de morceau : le fader doit refléter le gain sauvegardé du nouveau morceau

Plage actuelle :

- plage active : `-24 dB` à `+6 dB`
- réserve visuelle future possible : `+6 dB` à `+12 dB`, inactive pour l'instant

Limite connue :

- le passage autour de `0 dB` pendant une lecture active peut encore provoquer une micro-coupure
- cette limite est documentée tant qu'une solution sans craquement ni reconfiguration risquée n'est pas validée
- priorité actuelle : démarrage fiable des morceaux, stabilité des gains négatifs et absence de craquements

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

- amélioration continue du mode DJ simplifié
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
