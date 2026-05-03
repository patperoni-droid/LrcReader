# 🎵 FEATURE — TEMPO ARRANGEMENT

---

## 🎯 OBJECTIF

Permettre à l’utilisateur de :

- découper un morceau en segments
- supprimer rapidement une partie (pont, intro, etc.)
- réorganiser le morceau pour le live
- prévisualiser le résultat sans coupure
- vérifier précisément les transitions
- exporter une version finale propre
- sauvegarder l’ensemble de sa bibliothèque live

👉 Cas d’usage principal :
- adapter un morceau pour la scène (durée, structure, énergie)

---

## 🧩 CONCEPTS CLÉS

### IN / OUT

- IN = point d’entrée
- OUT = point de sortie

👉 définissent une zone de travail dans le morceau

---

### MODE + / -

#### Mode +

IN → OUT  
→ sélection classique  
→ crée 1 segment

---

#### Mode -

extérieur de IN → OUT  
→ permet de supprimer une partie  
→ crée 2 segments :

0 → IN  
OUT → fin

---

### SEGMENTS

Un segment = portion du morceau définie par :

startMs / endMs

---

### STRUCTURE

Liste ordonnée de segments :

Segment A → Segment B → Segment C

👉 représente le morceau final en live

---

## 🎛️ COMPORTEMENTS UX

### Recalage précis

- long press sur waveform
  → recale IN ou OUT (le plus proche)

---

### Grille rythmique

- Grille ON :
  → quantification rythmique

- Grille OFF :
  → position libre

#### Sync grille

- bouton Sync
- recale la grille sur la position actuelle de lecture
- conserve le tempo

👉 permet d’ajuster la grille à volonté

---

### Zoom / Pan

- zoom :
  → agrandit la zone de travail

- pan :
  → libère le focus (IN/OUT)

- comportement :
  → priorité au contrôle utilisateur  
  → pas de verrouillage forcé du centre

---

### Ajout de segments

Bouton Ajouter :

- mode + → 1 segment (IN → OUT)
- mode - → 2 segments (extérieur)

---

### Suppression rapide

- bouton suppression directement dans la liste Segments
- suppression instantanée
- nettoyage automatique dans Structure

👉 remplace l’ancien appui long

---

## 🔁 LECTURE STRUCTURE

### Principe

- player secondaire local
- playlist de segments clipés (MediaItems)
- lecture fluide basée sur ExoPlayer

---

### Tête de lecture structure

- une tête de lecture est affichée
- suit la lecture réelle
- identifie le segment courant

---

### Seek structure

- déplacement libre dans la structure
- accès direct aux transitions
- pas de blocage par la lecture

👉 permet de travailler rapidement les enchaînements

---

### Loop IN / OUT

- boucle basée sur clip ExoPlayer
- comportement corrigé :

✔ seek libre dans la boucle  
✔ reprise correcte après pause  
✔ loop toujours active  
✔ retour IN uniquement en progression naturelle

---

### Règles

✔ ne pas toucher player principal  
✔ ne pas casser timeMs  
✔ isoler la preview  
✔ aucun seek forcé non maîtrisé

---

## 🎧 PREVIEW WAV (ÉCOUTER)

### Principe

Le bouton “Écouter” génère une preview audio du montage final.

---

### Fichier

- un seul fichier temporaire :
  preview_arrangement.wav
- stocké dans le cache

---

### Optimisations

- si la structure n’a pas changé :
  → réutilisation du fichier existant
- sinon :
  → régénération

---

### Lifecycle

- fichier conservé pendant la session
- supprimé automatiquement à la sortie de la page

👉 évite toute accumulation

---

### Seek dans le WAV

- barre dédiée “Écoute du montage”
- affichage temps courant / durée
- seek libre dans le WAV

👉 permet :

- accès direct aux transitions (ex : 3:30)
- validation rapide sans écouter depuis le début

---

### Objectif

text Validation rapide + fidèle du rendu final

---

## 🔊 CORRECTION AUDIO

- micro fade (~12 ms)
  → atténue les clics aux transitions

⚠️ peut introduire une légère variation de volume

---

## 📦 EXPORT

### Export individuel

Flux :

Arrangement → WAV → SMP → runtime

- rendu WAV final via ArrangementWavRenderer
- conversion en SMP
- import automatique dans la bibliothèque

👉 morceau immédiatement exploitable

---

### Export global (sauvegarde)

Accessible depuis Paramètres

Fonction :

- export de tous les morceaux live (runtime)
- création d’un .smp à jour pour chaque morceau
- choix du dossier via SAF
- dossier automatique :
  Export_YYYY-MM-DD_HH-mm

---

### Contenu

- audio
- paroles
- accords
- timeline
- MIDI / DMX
- config

---

### Objectif export global

text Sauvegarder tout le travail utilisateur et permettre le transfert vers un autre appareil

---

## 🧠 PRINCIPES CLÉS

- ExoPlayer = source de vérité (timeMs)
- aucun traitement lourd en live
- aucun seek utilisé pour simuler une structure
- structure basée sur segments préparés
- séparation claire :
  - preview structure (rapide)
  - preview WAV (fidèle)
  - export (final)

---

## 🛑 INTERDIT

- seekTo pour simuler une structure
- logique cachée ou automatique imprévisible
- accumulation de fichiers temporaires
- dépendance UI pour logique audio

---

## 🏁 OBJECTIF FINAL

Créer un système :

- rapide à éditer
- précis
- fiable en live
- sans friction utilisateur

👉 un véritable outil de travail musica