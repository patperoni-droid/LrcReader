# 💾 FEATURE — EXPORT & BACKUP

---

## 🎯 OBJECTIF

Permettre à l’utilisateur de :

- sauvegarder l’intégralité de sa bibliothèque live
- transférer facilement ses données vers un autre appareil
- sécuriser son travail (paroles, accords, timeline, etc.)

👉 priorité :

sécurité des données > simplicité > portabilité

---

## 🧠 PRINCIPES FONDAMENTAUX

### 📦 SMP = format de transport

- `.smp` est une archive complète
- contient toutes les données nécessaires au morceau
- indépendant du téléphone d’origine

---

### ⚙️ Runtime = version de travail

- situé dans :
  `/data/data/.../files/tracks/<songId>/`
- utilisé pour la lecture live
- modifié en temps réel (paroles, timeline, etc.)

---

### 🔁 Cycle de vie

IMPORT → runtime → MODIFICATION → EXPORT

👉 le runtime est la source de vérité du travail utilisateur

---

## 📤 EXPORT INDIVIDUEL

### Flux

Arrangement → WAV → SMP → runtime

---

### Étapes

1. rendu WAV final (`ArrangementWavRenderer`)
2. conversion en `.smp`
3. import automatique dans l’application

---

### Résultat

- morceau disponible immédiatement dans la bibliothèque
- version propre prête pour le live

---

## 💾 SAUVEGARDE COMPLÈTE

### Accès

- via écran **Plus**
- action : `Sauvegarder ma bibliothèque`

---

### Fonctionnement

- scan des morceaux runtime (`SmpLibraryScanner`)
- export de chaque morceau en `.smp` à jour
- scan du stockage pour récupérer les `.smp` non importés
- déduplication basée sur `songId`
- ajout d’un fichier `state.json`

---

### Contenu sauvegardé

- tous les morceaux importés (version runtime)
- tous les `.smp` présents dans le stockage
- playlists
- état utilisateur (played, lastPlayed, etc.)

---

### Destination

- dossier choisi par l’utilisateur via SAF
- sous-dossier automatique :

Export_YYYY-MM-DD_HH-mm

---

### Objectif

- sauvegarder tout le travail utilisateur
- permettre un transfert complet téléphone → tablette

---

### Principe

- sauvegarde = création de fichiers `.smp`
- restauration = réimport des `.smp`
- aucune dépendance au runtime Android

---

## 🔄 RESTAURATION

### Restaurer ma bibliothèque

---

### Fonctionnement

- sélection d’un dossier via SAF
- scan des `.smp`
- lecture du `state.json`
- import des morceaux
- gestion des conflits

---

### Modes

#### Conserver les morceaux existants

- garde les morceaux déjà présents
- ajoute uniquement les nouveaux

---

#### Remplacer les morceaux existants

- remplace uniquement les morceaux présents dans la sauvegarde
- ne supprime jamais les autres

---

### Règles

✔ aucun doublon (`songId`)  
✔ aucune suppression automatique  
✔ playlists restaurées après import  
✔ fonctionnement non destructif

---

## 📁 CHOIX DU DOSSIER (SAF)

### Fonctionnement

- ouverture du sélecteur Android :
  `ACTION_OPEN_DOCUMENT_TREE`
- utilisateur choisit le dossier
- permissions persistantes enregistrées

---

### Comportement

- création automatique du dossier export
- écriture via `DocumentFile`
- compatible Android 11+

---

### Avantages

✔ sécurisé  
✔ flexible  
✔ compatible tous appareils

---

## 🔄 IMPORT SIMPLE

### Cas d’usage

- ouverture d’un fichier `.smp`
- import automatique dans l’application

---

### Processus

.smp → extraction → runtime

---

### Résultat

- création d’un `SongUnit`
- ajout à la bibliothèque
- prêt à être utilisé immédiatement

---

## ⚠️ LIMITATIONS ACTUELLES

### V1

- restauration non interactive (pas de “me demander” fichier par fichier)
- dépendance à la présence correcte des `songId` dans les `.smp`

---

### Cas limites

- `.smp` sans `songId` lisible → duplication possible
- sauvegardes multiples → gestion utilisateur nécessaire

---

## 🔮 ROADMAP

### V2

- mode avancé “me demander”
- sélection fine des conflits

---

### V3

- nettoyage automatique des `.smp`
- meilleure gestion du stockage

---

### V4

- sauvegarde complète en un fichier unique
- restauration en un clic

---

## ⚠️ RÈGLES IMPORTANTES

❌ pas de zip utilisé en live  
❌ pas de traitement lourd pendant lecture  
✔ export uniquement hors lecture  
✔ runtime = source de vérité

---

## 🎯 PHILOSOPHIE

L’utilisateur doit pouvoir perdre son téléphone  
sans perdre son travail.

---

## 💥 RÉSUMÉ

Cette feature permet :

- de sauvegarder toute la bibliothèque
- de transférer facilement vers un autre appareil
- de sécuriser totalement le travail utilisateur

👉 C’est une feature critique du produit 💥