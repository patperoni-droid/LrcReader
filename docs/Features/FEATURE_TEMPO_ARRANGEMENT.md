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

- 0 → IN
- OUT → fin

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

## 🧭 UX CIBLE — LISTE UNIQUE D'ARRANGEMENT

**Statut : conception tablette validée, non encore implémentée.**

### Périmètre appareil — tablette uniquement

Cette évolution d'interface concerne exclusivement la tablette.

- le téléphone conserve l'écran Arrangement, sa disposition, sa navigation et ses comportements actuels ;
- la liste unique ne doit jamais être activée sur téléphone ;
- le remplacement de la playlist par le récepteur de segments est strictement conditionné au mode tablette ;
- aucun espace, bouton ou geste supplémentaire ne doit être ajouté à l'interface téléphone ;
- toute évolution du stockage partagé doit rester transparente et rétrocompatible pour le téléphone ;
- la validation finale doit démontrer explicitement l'absence de régression téléphone.

La future interface ne doit plus séparer :

- une colonne contenant les segments disponibles ;
- une seconde colonne contenant leur disposition dans la Structure.

En mode Arrangement, une seule liste ordonnée devient à la fois le récepteur de segments et la Structure finale.

### Disposition tablette

- uniquement pendant l'édition d'un Arrangement, la playlist habituelle située à gauche est remplacée par le récepteur de segments ;
- en dehors du mode Arrangement, la playlist conserve exactement son fonctionnement actuel ;
- le récepteur reprend une présentation proche de la playlist pour conserver les repères visuels et les gestes connus ;
- la partie droite libérée devient la zone principale de réglage du segment sélectionné ;
- le `Playback Control` officiel reste visible et fixé en bas de l'écran ;
- aucun contenu d'édition ne doit repousser le `Playback Control` hors de l'écran.

### Création directe dans la Structure

Flux cible :

1. l'utilisateur place le point IN et le point OUT sur le titre ;
2. il appuie sur `Ajouter` ;
3. le nouveau segment est inséré en haut du récepteur ;
4. il déplace ensuite ce segment à la position désirée dans la Structure.

Il n'existe donc plus d'étape intermédiaire consistant à créer un segment dans une bibliothèque locale puis à l'ajouter séparément à la Structure.

### Édition d'un segment existant

- toucher une ligne sélectionne cette occurrence ;
- ses points IN et OUT sont rechargés dans l'éditeur de droite ;
- l'utilisateur peut corriger IN ou OUT sans recréer le segment ;
- les modifications restent non destructives pour l'audio source ;
- la durée visible de la ligne est recalculée depuis `endMs - startMs`.

### Actions sur une occurrence

Chaque ligne de la liste unique doit pouvoir être :

- déplacée par glisser-déposer ;
- copiée puis collée ;
- dupliquée ;
- supprimée explicitement ;
- mise en mute sans être supprimée ;
- configurée pour être jouée plusieurs fois consécutivement.

La ligne doit pouvoir afficher au minimum :

- son nom ;
- ses points IN et OUT ou sa durée ;
- son nombre de répétitions ;
- son état actif ou muet ;
- une poignée de déplacement ;
- un accès aux actions secondaires.

### Répétition et duplication

Ces deux actions restent distinctes :

- `répétition ×N` rejoue plusieurs fois de suite la même occurrence avec les mêmes points IN et OUT ;
- `dupliquer` crée une nouvelle occurrence indépendante, déplaçable et éditable séparément.

Exemple :

```text
Intro ×1
Couplet ×2
Refrain ×2
Pont muet
Refrain final ×3
```

Si une occurrence réglée sur `×3` est retouchée, ses trois lectures utilisent les mêmes nouveaux points IN et OUT. Pour obtenir une dernière répétition différente, l'utilisateur doit dupliquer l'occurrence puis modifier cette copie.

### Modèle cible d'une occurrence

Une entrée de la Structure doit posséder une identité stable indépendante de sa position dans la liste :

```text
ArrangementEntry
- entryId
- name
- startMs
- endMs
- repeatCount
- muted
```

Règles :

- l'ordre visuel de la liste définit l'ordre de préparation de la Structure ;
- un déplacement ne modifie jamais `entryId` ;
- une duplication crée un nouvel `entryId` ;
- `repeatCount` est toujours supérieur ou égal à 1 ;
- une occurrence muette est conservée dans le projet mais exclue de la lecture et de l'export ;
- copier/coller et duplication ne recopient jamais le fichier audio source.

### Préparation audio

Avant toute lecture de Structure :

1. ignorer les occurrences en mute ;
2. développer chaque `repeatCount` en occurrences de lecture consécutives ;
3. préparer la liste complète de segments clipés Media3 ;
4. seulement ensuite démarrer la preview Arrangement.

Cette UX à liste unique ne change pas la règle d'architecture audio : aucun réordonnancement, mute, collage ou changement de répétition ne doit reconstruire la Structure pendant sa lecture.

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

- édition directe waveform :
  → IN / OUT peuvent être ajustés depuis la waveform

- comportement :  
  → priorité au contrôle utilisateur  
  → pas de verrouillage forcé du centre

Règles :

- pinch zoom, pan et poignées de segments doivent coexister
- éviter les conflits de gestes entre déplacement waveform et édition segment

---

### Ajout de segments

Bouton Ajouter :

- mode + → 1 segment (IN → OUT), inséré en haut de la liste unique
- mode - → 2 segments (extérieur), insérés en haut de la liste unique

---

### Suppression rapide

- bouton suppression directement dans la liste unique
- suppression explicite de l'occurrence concernée
- aucune seconde Structure à nettoyer ou synchroniser

👉 remplace l’ancien appui long

---

## 🔁 LECTURE STRUCTURE

### Principe

- preview de travail rapide
- player secondaire local
- playlist de segments clipés (MediaItems)
- lecture fluide basée sur ExoPlayer
- structure basée sur segments préparés

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
✔ transitions préparées  
✔ stop protection obligatoire

👉 La Structure ne doit jamais piloter le Player principal.

---

## 🧪 SAMPLER EXPÉRIMENTAL

### Principe

- Sampler PCM expérimental
- utilisé pour tester transitions et segments Arrangement
- ne pilote jamais le Player principal
- ne doit pas être couplé à AudioEngine live

### Règles

✔ usage limité aux tests / preview Arrangement  
✔ pas de traitement lourd pendant le live  
✔ reste expérimental tant qu’il n’est pas promu officiellement

---

## 🎧 PREVIEW WAV (ÉCOUTER)

### Principe

Le bouton “Écouter” génère une preview audio du montage final.

👉 Preview WAV = rendu fidèle de validation.

---

### Fichier

- cache contrôlé autour du fichier temporaire :  
  preview_arrangement.wav
- stocké dans le cache
- aucun fichier temporaire accumulé

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
- nettoyage obligatoire

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

Validation rapide + fidèle du rendu final

---

## 🔊 CORRECTION AUDIO

- micro fade court  
  → atténue les clics aux transitions

⚠️ peut introduire une légère variation de volume

---

## 📦 EXPORT

### Export individuel (Assemblage)

Flux :

Arrangement → WAV → SMP → runtime

- rendu WAV final via ArrangementWavRenderer
- conversion en SMP
- import automatique dans la bibliothèque
- le SongUnit runtime doit être exploitable directement après export

👉 morceau immédiatement exploitable

---

### UX Assemblage

Pendant l’assemblage :

- affichage d’un indicateur de progression (roue de chargement)
- message visible
- bouton Assembler désactivé

👉 évite toute impression de blocage

---

## 💾 SAUVEGARDE

### Sauvegarder ma bibliothèque

Accessible depuis l’écran Plus

Fonction :

- sauvegarde complète de la bibliothèque utilisateur
- export des morceaux importés depuis leur état actuel
- création d’un `.smp` à jour pour chaque morceau
- copie des `.smp` non importés présents dans le stockage
- ajout d’un `state.json` (playlists + état)
- choix du dossier via SAF
- dossier automatique horodaté

---

### Objectif sauvegarde

Sauvegarder tout le travail utilisateur et permettre le transfert vers un autre appareil

👉 ex : téléphone → tablette

---

### Principe

- sauvegarde = création de fichiers `.smp`
- restauration = réimport des `.smp`
- indépendance totale du runtime Android

---

## 🔄 RESTAURATION

### Restaurer ma bibliothèque

Fonction :

- sélection d’un dossier de sauvegarde
- détection des fichiers `.smp`
- détection du `state.json`
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
✔ morceaux restaurés comme runtime normalisé  
✔ playlists réactivables par import JSON dédié  
✔ fonctionnement non destructif

---

## 🧠 PRINCIPES CLÉS

- ExoPlayer = source de vérité (timeMs)
- aucun traitement lourd en live
- aucun seek utilisé pour simuler une structure
- structure basée sur segments préparés

### Séparation fondamentale

- preview Structure = travail rapide sur segments
- preview WAV = rendu fidèle de validation
- export = génération finale SMP/runtime

### Règles critiques

- La Structure ne doit jamais piloter le Player principal.
- Aucun traitement lourd pendant le live.
- Les transitions doivent être préparées.
- Le cache WAV doit rester contrôlé et nettoyé.
- Ne pas modifier AudioEngine pour corriger Arrangement preview.

---

## 🛑 INTERDIT

- seekTo pour simuler une structure
- logique cachée ou automatique imprévisible
- accumulation de fichiers temporaires
- dépendance UI pour logique audio
- coupler Sampler expérimental et Player principal
- modifier AudioEngine pour corriger la preview Arrangement

---

## 🏁 OBJECTIF FINAL

Créer un système :

- rapide à éditer
- précis
- fiable en live
- sans friction utilisateur

👉 un véritable outil de travail musical  
