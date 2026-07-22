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

- tous les morceaux importés (version runtime), incluant leurs réglages LUFS dans `config.json`
- le projet `arrangement.json` courant de chaque morceau lorsqu'il existe
- les variantes Arrangement virtuelles dans `arrangement_variants.json` à l'intérieur du `.smp` de leur morceau parent
- tous les `.smp` présents dans le stockage
- playlists
- état utilisateur (played, lastPlayed, etc.)

### Variantes Arrangement virtuelles

Une variante virtuelle dépend de l'audio de son morceau parent. Elle ne doit jamais être exportée comme un `.smp` autonome sans audio.

La sauvegarde complète applique donc les règles suivantes :

- un seul `.smp` est créé pour le morceau parent ;
- l'audio source n'est présent qu'une fois ;
- les identifiants, titres et Structures de ses variantes sont embarqués dans ce même conteneur ;
- une variante orpheline dont le parent est absent provoque un échec signalé au lieu d'être ignorée silencieusement.

Lors de la restauration, le parent est importé en premier puis ses variantes sont recréées comme données runtime normalisées. Les anciens `.smp` sans variantes restent compatibles.

Diagnostic du premier essai du 22 juillet 2026 : la sauvegarde concernée avait été créée avec une application installée avant cette évolution et son `.smp` ne contenait ni `arrangement.json` ni `arrangement_variants.json`. Les variantes encore visibles avaient été conservées par la restauration non destructive. Une ancienne archive reste compatible, mais elle ne peut pas recréer des données Arrangement absentes de son contenu. La validation fonctionnelle doit donc utiliser une nouvelle sauvegarde produite après installation du build corrigé.

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
- les réglages LUFS suivent le morceau via `SongUnit/config.json`
- référence LUFS : `FEATURE_LUFS_PREPARATION.md`
- aucune dépendance au runtime Android

---

## 🔄 RESTAURATION

### Restaurer ma bibliothèque

---

### Fonctionnement

- sélection d’un dossier via SAF
- scan des `.smp`
- lecture du `state.json`
- import prioritaire des morceaux / `SongUnit`
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
✔ les morceaux restaurés sont disponibles comme runtime normalisé  
✔ les variantes virtuelles sont restaurées avec leur parent sans duplication audio
✔ les playlists peuvent être réactivées par import JSON dédié  
✔ fonctionnement non destructif

### Playlists et restauration bêta

En bêta publique, la restauration bibliothèque privilégie la reconstruction fiable des morceaux.

Règles :

- les `SongUnit` restaurés redeviennent immédiatement exploitables
- les playlists peuvent ensuite être importées individuellement depuis leur JSON
- l'utilisateur choisit ainsi quelles playlists réactiver
- l'import JSON playlist préserve groupes, couleurs, occurrences fantômes et ordre brut
- voir `FEATURE_PLAYLISTS.md`

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

### Installation initiale

Lors du premier choix du workspace :

- l'utilisateur valide le dossier via SAF Android
- SMP affiche un état de préparation du workspace
- l'initialisation SAF est effectuée hors thread UI
- l'écran ne doit pas rester noir pendant la création / validation du workspace
- la préparation peut durer quelques secondes selon l'appareil et le fournisseur DocumentsUI

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

## 🔁 SMP SYNC — TRANSFERT LOCAL MANUEL

SMP Sync complète l’export/import classique avec un transfert local entre deux appareils :

- téléphone principal : appareil de travail
- téléphone secours : appareil de backup prêt pour le live

Principes :

- pas de cloud
- pas d’Internet obligatoire
- transfert via LocalLink / Wi-Fi local / hotspot
- `.smp` reste un format de transport
- le téléphone secours importe toujours vers son runtime local normalisé
- aucun fichier `.smp` n’est utilisé pendant le live

### V1 actuelle : mode manuel

La V1 privilégie la fiabilité :

1. le téléphone principal crée une connexion
2. le téléphone secours rejoint la connexion
3. l’utilisateur choisit explicitement ce qu’il envoie
4. le package est envoyé au téléphone secours
5. l’utilisateur confirme manuellement l’import

Catégories :

- morceaux
- playlists
- bloc-notes : prévu plus tard
- prompteurs : prévu plus tard

Règles :

- aucune suppression automatique
- aucun merge automatique
- `songId` conservé
- remplacement d’un morceau existant autorisé seulement après confirmation utilisateur
- playlists transférées comme état de structure, sans duplication audio
- familles et groupes de playlist doivent rester compatibles avec le transfert

Le moteur d’analyse différentielle existe comme aide/diagnostic, mais le mode manuel reste la référence UX V1.

Voir aussi : `FEATURE_SMP_SYNC.md`

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
