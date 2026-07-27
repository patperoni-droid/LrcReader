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

- `.smp` est l'archive de transport d'une SongUnit
- l'archive d'un parent contient son audio, ses données et ses variantes virtuelles
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

## 📤 PARTAGE SMP D'UN TITRE OU D'UNE VARIANTE

Le partage réutilise le même format `.smp` et le même pipeline d'import.

### Partage du parent

- exporte la SongUnit parent
- transporte l'audio une seule fois
- inclut les variantes du parent et leurs données propres

### Partage d'une variante

- conserve le `songId` de la variante
- conserve son `sourceSongId` immuable
- transporte le parent nécessaire à son autonomie
- inclut uniquement la variante ciblée grâce à `selectedVariantId`
- transporte ses paroles, ses accords, ses couleurs de lignes et sa structure
- ne crée pas un second fichier audio

À l'import, l'archive ne doit jamais modifier un parent local qui possède déjà le même
`songId`. Elle ajoute ou met à jour uniquement la variante transportée.

---

## 📤 EXPORT INDIVIDUEL — ASSEMBLAGE ARRANGEMENT

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
- les paroles, accords et couleurs de lignes propres à chaque variante
- tous les `.smp` présents dans le stockage
- playlists, groupes internes, couleurs, ordre, occurrences répétées et titres personnalisés
- état utilisateur (played, lastPlayed, etc.)

### Variantes Arrangement virtuelles

Une variante virtuelle dépend de l'audio de son morceau parent. Elle ne doit jamais être exportée comme un `.smp` autonome sans audio.

La sauvegarde complète applique donc les règles suivantes :

- un seul `.smp` est créé pour le morceau parent ;
- l'audio source n'est présent qu'une fois ;
- les identifiants, titres, structures, paroles, accords et couleurs de lignes de ses variantes sont embarqués dans ce même conteneur ;
- une variante orpheline dont le parent est absent provoque un échec signalé au lieu d'être ignorée silencieusement.

Lors de la restauration, le parent est importé en premier puis ses variantes sont recréées comme données runtime normalisées. Les anciens `.smp` sans variantes restent compatibles.

Une ancienne archive reste compatible, mais elle ne peut pas recréer une donnée qui
n'était pas transportée au moment de sa création.

---

### Destination

- dossier choisi par l’utilisateur via SAF
- un nom par défaut basé sur la date et l'heure est proposé :
  `Export_YYYY-MM-DD_HH-mm`
- ce nom est affiché dans un champ éditable avant la sauvegarde
- si l'utilisateur ne le modifie pas, le comportement historique est conservé
- si l'utilisateur le personnalise, le sous-dossier porte le nom choisi
- un nom vide revient au nom proposé et les séparateurs de chemin invalides sont neutralisés

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
- import des SongUnit parents
- restauration de leurs variantes
- rafraîchissement de l'index de la Bibliothèque
- remappage de l'état par `songId`
- restauration des playlists et de leurs groupes
- gestion des conflits

---

### Modes

#### Conserver les morceaux existants

- garde les morceaux déjà présents
- ajoute uniquement les nouveaux
- restaure les variantes manquantes sans remplacer leurs parents locaux

---

#### Remplacer les morceaux existants

- remplace uniquement les morceaux présents dans la sauvegarde
- ne supprime jamais les autres
- restaure ensuite les variantes transportées par chaque parent

---

### Règles

✔ aucun doublon (`songId`)  
✔ aucune suppression automatique  
✔ les morceaux restaurés sont disponibles comme runtime normalisé  
✔ les variantes virtuelles sont restaurées avec leur parent sans duplication audio
✔ `sourceSongId` reste immuable et un conflit de parent est refusé explicitement
✔ une archive ne modifie que les données qu'elle transporte
✔ les données locales absentes de l'archive sont conservées
✔ les playlists et leurs groupes sont restaurés automatiquement après l'index Bibliothèque
✔ fonctionnement non destructif

### Playlists et restauration complète

Une sauvegarde complète doit reconstruire l'état utilisateur, pas seulement les morceaux.

Règles :

- les `SongUnit` parents sont disponibles avant leurs variantes
- les variantes sont inscrites dans l'index Bibliothèque avant le remappage des playlists
- les playlists sont restaurées automatiquement depuis `state.json`
- groupes internes, couleurs, ordre brut, occurrences répétées et titres personnalisés sont préservés
- le parent et sa variante retrouvent leur groupe et leur position
- un `songId` valide ne doit jamais apparaître comme « Titre manquant »
- la lecture ne doit jamais servir à réparer ou hydrater l'affichage d'une playlist
- l'import JSON dédié reste disponible pour un import volontaire d'une playlist isolée
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

- Bibliothèque → `Importer…` → `Importer un fichier SMP`
- sélection du fichier avec le sélecteur Android
- réutilisation du pipeline normal d'import SMP

L'ouverture directe d'un `.smp` depuis Gmail ou Files via `ACTION_VIEW` ne fait pas
partie du parcours actuellement documenté comme validé.

---

### Processus

.smp → extraction → runtime

---

### Résultat

- création d’un `SongUnit`
- ajout à la bibliothèque
- prêt à être utilisé immédiatement

### Import d'une variante partagée

- si le parent est absent, la SongUnit parent transportée est installée puis la variante est recréée
- si le parent existe avec le même `songId`, il reste la référence locale et n'est jamais modifié
- si la variante existe déjà avec le même parent, elle est mise à jour sans doublon
- si le même `songId` de variante est déjà rattaché à un autre `sourceSongId`, l'import est refusé
- une donnée présente dans l'archive est restaurée
- une donnée absente de l'archive laisse la donnée locale existante inchangée

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
- la sauvegarde complète reste un dossier contenant plusieurs fichiers, pas une archive unique
- le choix Conserver / Remplacer s'applique au flux de restauration, pas fichier par fichier

---

### Cas limites

- ancien `.smp` sans `songId` lisible → traité comme une nouvelle identité, donc duplication possible
- une archive ancienne ne peut pas recréer les données qu'elle ne transporte pas
- une variante orpheline ne peut pas être sauvegardée sans son parent
- sauvegardes multiples → gestion utilisateur nécessaire

---

## 🔮 PISTES FUTURES NON IMPLÉMENTÉES

- mode avancé “me demander” et sélection des conflits fichier par fichier
- synchronisation ou mise à jour d'une sauvegarde existante
- sauvegarde complète dans une archive unique
- ouverture directe d'un fichier `.smp` par `ACTION_VIEW`

---

## ✅ VALIDATION FONCTIONNELLE

Vérifier au minimum :

- sauvegarde avec le nom automatique proposé
- sauvegarde avec un nom personnalisé
- restauration complète d'un parent et de ses variantes
- restauration des paroles et accords propres aux variantes
- restauration d'une playlist contenant un groupe avec parent et variante
- conservation de l'ordre, des couleurs, des occurrences répétées et des titres personnalisés
- absence de « Titre manquant » pour toute référence `songId` valide
- parent déjà présent : parent conservé et variante restaurée
- parent absent : parent recréé puis variante restaurée
- variante déjà présente : mise à jour sans doublon
- conflit de `sourceSongId` : refus explicite
- compatibilité d'une archive ancienne
- validation sur téléphone et tablette

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
