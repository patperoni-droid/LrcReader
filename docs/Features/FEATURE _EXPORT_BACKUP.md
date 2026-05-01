# 💾 FEATURE — EXPORT & BACKUP

---

## 🎯 OBJECTIF

Permettre à l’utilisateur de :

- sauvegarder ses morceaux live
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

## 📦 EXPORT GLOBAL (SAUVEGARDE)

### Accès

- via **Paramètres**
- action : `Exporter tous les morceaux live`

---

### Fonctionnement

- scan des morceaux runtime (`SmpLibraryScanner`)
- export de chaque morceau en `.smp` à jour
- un fichier `.smp` par morceau

---

### Destination

- dossier choisi par l’utilisateur via SAF
- sous-dossier auto :

Export_YYYY-MM-DD_HH-mm

---

### Contenu exporté

Chaque `.smp` contient :

- audio
- paroles
- accords
- timeline
- MIDI
- DMX
- annotations
- config

---

### Objectif

sauvegarder tout le travail utilisateur  
et permettre un transfert complet

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

## 🔄 IMPORT

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

### Export global V1

- les `.smp` contiennent :
    - audio + metadata
- MAIS :

❌ pas les arrangements Tempo  
❌ pas les playlists

---

### Architecture

- arrangements stockés dans `ArrangementStore`
- playlists dans `PlaylistRepository`
- non inclus dans `.smp` actuellement

---

## 🔮 ROADMAP

### V2

- intégrer Arrangement dans `SongUnit`
- rendre export `.smp` complet

---

### V3

- export complet :

morceaux + arrangements + playlists

---

### V4

- sauvegarde complète en un fichier unique (backup global)
- restauration complète en un clic

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

- de sauvegarder tous les morceaux live
- de transférer facilement vers un autre appareil
- de sécuriser le travail utilisateur

👉 C’est une feature critique du produit.