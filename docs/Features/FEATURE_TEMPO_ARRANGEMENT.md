# 🎵 FEATURE — TEMPO ARRANGEMENT

---

## 🎯 OBJECTIF

Permettre à l’utilisateur de :

- découper un morceau en segments
- supprimer rapidement une partie (pont, intro, etc.)
- réorganiser le morceau pour le live
- prévisualiser le résultat sans coupure
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
→ crée **1 segment**

---

#### Mode -

extérieur de IN → OUT

→ permet de supprimer une partie  
→ crée **2 segments** :

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

- **long press sur waveform**
  → recale IN ou OUT (le plus proche)

- Grille ON :
  → quantification rythmique

- Grille OFF :
  → position libre

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

Bouton `Ajouter` :

- mode `+` → 1 segment (IN → OUT)
- mode `-` → 2 segments (extérieur)

---

### Lecture

- preview structure via **player secondaire**
- utilisation de **MediaItems clipés**
- suppression du `seek` manuel

---

### Correction audio

- micro fade (~12 ms)
  → atténue les clics aux transitions

---

## 🔁 LECTURE STRUCTURE

### Principe

- player secondaire local
- playlist de segments clipés
- lecture fluide

---

### Règles

✔ ne pas toucher player principal  
✔ ne pas casser timeMs  
✔ isoler la preview

---

## 📦 EXPORT

### Export individuel

Flux :

Arrangement → WAV → SMP → runtime

- rendu WAV final via `ArrangementWavRenderer`
- conversion en SMP
- import automatique dans la bibliothèque

👉 permet d’obtenir un morceau directement exploitable

---

### Export global (sauvegarde)

Accessible depuis **Paramètres**

Fonction :

- export de tous les morceaux live (runtime)
- création d’un `.smp` à jour pour chaque morceau
- choix du dossier utilisateur via SAF
- création automatique d’un sous-dossier :
  Export_YYYY-MM-DD_HH-mm

Contenu :

- audio
- paroles
- accords
- timeline
- MIDI / DMX
- config

---

### Objectif export global

```text
Sauvegarder tout le travail utilisateur
et permettre le transfert vers un autre appareil