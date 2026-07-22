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
- enregistrer plusieurs variantes virtuelles sans dupliquer l'audio source
- sauvegarder l’ensemble de sa bibliothèque live

👉 Cas d’usage principal :
- adapter un morceau pour la scène (durée, structure, énergie)

---

## VARIANTES VIRTUELLES DANS LA BIBLIOTHÈQUE

**Statut : enregistrement Bibliothèque et première lecture audio-only par le lecteur principal implémentés.**

Une variante virtuelle est un projet Arrangement léger, par exemple `Marina_AR01`, qui :

- possède un identifiant stable propre ;
- référence le `songId` du morceau source ;
- conserve sa Structure et ses occurrences dans `arrangement.json` ;
- ne copie et ne transforme jamais le fichier audio source ;
- peut coexister avec plusieurs autres variantes du même morceau ;
- ne produit un WAV que par une action d'assemblage explicite.

Première étape :

- l'action est proposée uniquement dans l'éditeur Arrangement tablette ;
- la variante est créée dans le stockage interne normalisé et apparaît dans la Bibliothèque ;
- elle est clairement identifiée comme variante Arrangement ;
- elle reste non assignable à une playlist pendant cette étape ;
- le titre source et son Arrangement courant restent inchangés.

Deuxième étape — lecture Bibliothèque :

- le Play de la Bibliothèque résout la variante vers son SongUnit source local ;
- les occurrences actives et leurs répétitions sont transformées en une liste Media3 complète avant Play ;
- les occurrences muettes sont exclues de cette préparation ;
- cette liste est chargée dans le lecteur principal officiel, sans second lecteur et sans rendu WAV ;
- Play, Pause, Stop et retour au début utilisent le `Playback Control` officiel ;
- position et durée sont exprimées dans le temps cumulé de l'Arrangement ;
- les paroles, accords, MIDI et DMX ne sont pas encore projetés dans ce nouvel espace temporel et restent hors de cette étape audio-only.

Évolutions ultérieures :

- ajout aux playlists et aux familles de versions ;
- projection temporelle des paroles, accords, MIDI et DMX ;
- transfert conjoint de la variante et de sa source ;
- gestion explicite de la suppression d'une source possédant des variantes.

Une variante virtuelle ne doit jamais être confondue avec un export WAV. L'export crée un audio indépendant ; la variante reste un montage non destructif dépendant de sa source.

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

## 🧭 UX CIBLE — PISTE HORIZONTALE D'ARRANGEMENT

**Statut : conception tablette validée, mise en œuvre progressive en cours.**

### État d'implémentation

Fondation tablette réalisée :

- l'entrée dans l'éditeur Arrangement est désormais signalée explicitement au layout tablette ;
- pendant cette édition, la playlist est repliée et l'éditeur conserve la même instance tout en prenant toute la largeur disponible ;
- les raccourcis supérieurs du panneau droit sont masqués afin de libérer la hauteur utile ;
- le contenu d'Arrangement est contraint et défilable au-dessus du `Playback Control`, qui reste visible en bas ;
- sur tablette, l'entrée `Arrangement` de l'écran `Paramètres / Plus` ouvre désormais l'éditeur canonique intégré à `Timeline / Grille` lorsqu'un morceau SMP est actif ;
- l'ancien éditeur à deux colonnes n'est plus la route principale tablette ; il reste disponible sur téléphone et comme repli lorsqu'aucun morceau SMP actif ne peut alimenter l'éditeur canonique ;
- dans l'éditeur canonique tablette, le bouton `Fermer` et le bouton Retour Android restaurent le cockpit `Playlist | Paroles` sans arrêter ni remplacer le morceau actif ;
- ce comportement est strictement conditionné au layout tablette et ne modifie pas le téléphone.

Reste à réaliser :

- permettre d'ouvrir ou de fermer la playlist dans un panneau latéral gauche sans quitter Arrangement.

Implémenté dans l'éditeur canonique tablette :

- les colonnes `Segments` et `Structure` sont fusionnées visuellement en une seule liste ordonnée ;
- le bouton `Ajouter` insère directement le nouveau segment en tête de cette liste et dans la Structure de lecture ;
- supprimer une ligne retire cette occurrence et supprime aussi son segment interne lorsqu'aucune autre occurrence ne le référence ;
- le téléphone conserve volontairement l'ancien affichage à deux colonnes et son flux d'ajout existant ;
- le modèle persistant V2 d'une occurrence est disponible avec `entryId`, nom, `startMs`, `endMs`, `repeatCount`, `muted` et `color` ;
- la lecture d'un ancien fichier V1 crée en mémoire des occurrences indépendantes et déterministes, sans réécrire automatiquement le fichier ;
- un fichier V2 conserve une projection `segments` / `structureSegmentIds` compatible avec l'ancien écran téléphone ; une sauvegarde issue de cet ancien écran préserve les métadonnées V2 déjà présentes ;
- tant que la piste horizontale n'utilise pas encore ce nouveau modèle, les écrans actuels continuent volontairement d'enregistrer le format V1 et leur comportement reste inchangé ;
- les champs d'occurrence V2 participent au hash de synchronisation afin que répétition, mute et couleur soient transférés comme données de l'Arrangement.
- sur tablette, la Structure est maintenant rendue comme une piste horizontale statique et défilable juste au-dessus du `Playback Control` ;
- chaque bloc affiche son ordre, son nom et sa durée, avec une largeur liée à la durée et une largeur tactile minimale ;
- la sélection active reste verte, la prochaine occurrence préparée reste jaune et la suppression conserve le comportement existant ;
- le défilement horizontal est un état d'interface local : il ne modifie ni l'ordre, ni le stockage, ni la lecture audio ;
- le téléphone continue d'utiliser la carte verticale historique sans changement visuel ou fonctionnel.
- lors du chargement tablette, chaque occurrence de la Structure reçoit son `entryId` V2 indépendant, y compris lorsque plusieurs occurrences provenaient du même segment V1 ;
- toucher un bloc recharge ses propres `startMs` / `endMs` dans les poignées IN / OUT de la waveform source ;
- une correction IN / OUT, une suppression ou un undo conserve cette identité d'occurrence et sauvegarde la Structure tablette en V2 ;
- les segments V1 non placés dans la Structure restent conservés dans le fichier pour éviter toute perte pendant la migration ;
- ouvrir l'Arrangement ou sélectionner un bloc ne réécrit pas le fichier : la migration V2 est enregistrée seulement lors d'une modification explicite ;
- le téléphone continue d'enregistrer sa forme V1 lorsqu'il travaille sur un Arrangement V1.
- chaque bloc tablette propose maintenant renommage, couleur, collage à la tête, mute, répétition et suppression explicite ;
- la poignée de déplacement déplace une occurrence d'un voisin vers la gauche ou la droite sans changer son `entryId` ;
- un collage crée à la frontière choisie une occurrence avec un nouvel `entryId` indépendant et ne duplique jamais le fichier audio ;
- la couleur, le mute et `repeatCount` sont sauvegardés dans l'entrée V2 ;
- avant la preview ou l'export, les occurrences en mute sont exclues et les répétitions sont développées dans la liste audio préparée ;
- pendant une preview Structure ou WAV, une préparation ou un export, l'ajout, le déplacement et les actions de structure sont verrouillés afin de ne jamais reconstruire le montage en pleine lecture ;
- la tête turquoise reste visible sur la piste tablette hors lecture ; après Stop, elle conserve la dernière position exacte, y compris au milieu d'un segment ;
- toucher ou faire glisser la zone supérieure de la piste passe en édition et quantifie la tête sur la frontière de segment la plus proche ;
- après un Stop au milieu d'un segment, `Coller ici` reste indisponible tant qu'un toucher n'a pas choisi une frontière non ambiguë ;
- `Coller ici`, dans le menu du segment sélectionné, duplique ce segment à la frontière indiquée par la tête ;
- sa position utilise le temps cumulé du montage préparé, jamais le temps absolu de la waveform source ;
- pour un bloc répété, la tête parcourt successivement chaque fraction du bloc et affiche la répétition active sous la forme `1/N`, `2/N`, etc. ;
- les occurrences en mute restent visibles dans l'éditeur mais sont sautées par la tête de lecture comme elles le sont par la liste audio préparée ;
- le défilement horizontal conserve l'alignement entre la tête et le bloc actif sans créer d'horloge ou d'animation indépendante du lecteur ;
- la piste tablette compacte n'affiche plus la ligne de titre `Structure` et sa hauteur est réduite afin de limiter le défilement vertical de l'écran ;
- tant qu'aucun bloc n'est sélectionné, le `Playback Control` pilote le titre complet ;
- toucher un bloc arrête d'abord le titre complet puis donne temporairement les commandes du `Playback Control` à cette occurrence ;
- pendant ce ciblage, Pause arrête entièrement la preview secondaire et Play relance le segment sélectionné depuis son début ;
- toucher la waveform source arrête immédiatement la preview secondaire, libère le ciblage et place le titre complet en pause à la position touchée ; le Play suivant démarre donc le titre complet depuis cette position ;
- le bouton retour début arrête la preview secondaire, libère le ciblage du bloc, replace le titre complet à `00:00` et rend les commandes au lecteur principal ;
- le lecteur principal et la preview de segment ne doivent jamais jouer simultanément.

### Périmètre appareil — tablette uniquement

Cette évolution d'interface concerne exclusivement la tablette.

- le téléphone conserve l'écran Arrangement, sa disposition, sa navigation et ses comportements actuels ;
- la piste horizontale ne doit jamais être activée sur téléphone ;
- la playlist latérale escamotable dans Arrangement est strictement conditionnée au mode tablette ;
- aucun espace, bouton ou geste supplémentaire ne doit être ajouté à l'interface téléphone ;
- toute évolution du stockage partagé doit rester transparente et rétrocompatible pour le téléphone ;
- la validation finale doit démontrer explicitement l'absence de régression téléphone.

La future interface ne doit plus séparer :

- une colonne contenant les segments disponibles ;
- une seconde colonne contenant leur disposition dans la Structure.

En mode Arrangement, une seule piste horizontale ordonnée devient à la fois le récepteur de segments et la Structure finale.

### Disposition tablette

- Arrangement reste un outil tablette pleine largeur afin de préserver une waveform et une zone d'édition confortables ;
- la piste Arrangement est placée horizontalement en bas de la zone d'édition, juste au-dessus du `Playback Control` ;
- la playlist est repliée par défaut mais peut être ouverte ou fermée dans un panneau latéral gauche depuis l'écran Arrangement ;
- ouvrir la playlist réduit uniquement la largeur visible de la zone Arrangement ;
- fermer la playlist restitue toute la largeur à l'éditeur ;
- en dehors du mode Arrangement, la playlist conserve exactement son fonctionnement actuel ;
- le `Playback Control` officiel reste visible et fixé en bas de l'écran ;
- aucun contenu d'édition ne doit repousser le `Playback Control` hors de l'écran.

Règles de redimensionnement :

- l'ouverture ou la fermeture de la playlist ne modifie jamais l'ordre, les durées, les couleurs, le zoom ou la sélection des blocs ;
- l'échelle temporelle de la piste est indépendante de la largeur disponible à l'écran ;
- lorsque la playlist est ouverte, la piste présente simplement une fenêtre visible plus étroite et reste défilable horizontalement ;
- l'état de défilement et le niveau de zoom doivent être conservés pendant l'ouverture ou la fermeture du panneau ;
- aucun changement de panneau ne doit arrêter, relancer ou remplacer une lecture.

### Piste horizontale et conteneurs

La Structure est représentée comme une succession de conteneurs audio, dans l'esprit d'une piste de séquenceur :

```text
[ Intro ][ Couplet ][ Refrain ][ Pont ][ Refrain final ]
                              ▲
                       tête de lecture
```

Chaque bloc :

- représente une occurrence indépendante de segment ;
- possède un nom et une couleur modifiables ;
- affiche au minimum son nom, sa durée, son état muet et son nombre de répétitions ;
- peut être déplacé horizontalement pour modifier l'ordre final ;
- conserve une largeur liée à sa durée et au niveau de zoom de la piste ;
- dispose d'une largeur tactile minimale afin qu'un segment très court reste sélectionnable ;
- ne contient ni ne duplique le fichier audio source.

La couleur est une information visuelle portable liée à l'occurrence. Elle n'a aucun effet audio et doit survivre à la sauvegarde, à l'export SMP et au transfert entre appareils.

### Deux espaces temporels distincts

L'interface présente deux temps qui ne doivent jamais être confondus :

- la waveform supérieure utilise le temps absolu du morceau source et sert à régler `startMs` / `endMs` ;
- la piste Arrangement utilise le temps cumulé du montage final préparé.

Exemple : un segment provenant de `02:00 → 02:30` peut commencer à `00:45` dans l'Arrangement si les blocs précédents totalisent 45 secondes.

La tête de lecture de la piste :

- se dessine au-dessus des blocs ;
- représente la position réelle dans l'Arrangement assemblé ;
- est pilotée par la lecture réelle de la preview Structure, jamais par une animation autonome ;
- permet d'identifier l'occurrence et la répétition actuellement jouées ;
- ne transforme jamais un seek du morceau source en mécanisme d'enchaînement.

### Création directe dans la Structure

Flux cible :

1. l'utilisateur place le point IN et le point OUT sur le titre ;
2. il appuie sur `Ajouter` ;
3. le nouveau segment est inséré au début de la piste horizontale ;
4. il déplace ensuite ce segment à la position désirée dans la Structure.

Il n'existe donc plus d'étape intermédiaire consistant à créer un segment dans une bibliothèque locale puis à l'ajouter séparément à la Structure.

### Édition d'un segment existant

- toucher un bloc sélectionne cette occurrence ;
- ses points IN et OUT sont rechargés dans l'éditeur de droite ;
- l'utilisateur peut corriger IN ou OUT sans recréer le segment ;
- les modifications restent non destructives pour l'audio source ;
- la durée visible de la ligne est recalculée depuis `endMs - startMs`.

### Actions sur une occurrence

Chaque bloc de la piste unique doit pouvoir être :

- déplacé par glisser-déposer ;
- copié puis collé ;
- dupliqué ;
- supprimé explicitement ;
- mis en mute sans être supprimé ;
- configuré pour être joué plusieurs fois consécutivement.

Le bloc doit pouvoir afficher au minimum :

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
- color
```

Règles :

- l'ordre visuel de la liste définit l'ordre de préparation de la Structure ;
- un déplacement ne modifie jamais `entryId` ;
- une duplication crée un nouvel `entryId` ;
- `repeatCount` est toujours supérieur ou égal à 1 ;
- une occurrence muette est conservée dans le projet mais exclue de la lecture et de l'export ;
- la couleur reste une métadonnée visuelle portable sans effet sur l'audio ;
- copier/coller et duplication ne recopient jamais le fichier audio source.

### Préparation audio

Avant toute lecture de Structure :

1. ignorer les occurrences en mute ;
2. développer chaque `repeatCount` en occurrences de lecture consécutives ;
3. préparer la liste complète de segments clipés Media3 ;
4. seulement ensuite démarrer la preview Arrangement.

Cette UX à piste horizontale ne change pas la règle d'architecture audio : aucun réordonnancement, mute, collage ou changement de répétition ne doit reconstruire la Structure pendant sa lecture.

### Playlist escamotable et titre actif

L'ouverture de la playlist dans Arrangement réutilise le comportement officiel du `Playback Control` :

```text
A est actif -> Arrangement A reste affiché
B est sélectionné dans la playlist -> Play devient jaune, Arrangement A reste affiché
Play jaune est pressé -> B devient actif, Arrangement B est chargé
```

Règles :

- ouvrir ou fermer la playlist ne change jamais le titre actif ;
- sélectionner un autre titre ne remplace pas l'Arrangement affiché tant que ce titre n'est pas lancé ;
- le passage de A vers B utilise uniquement le pipeline Playback officiel ;
- l'éditeur Arrangement reste ouvert pendant le changement de titre actif ;
- la preview Structure secondaire est arrêtée proprement si le titre actif doit être remplacé ;
- ce comportement ne doit ajouter aucun silence ni délai entre les titres.

### Ordre d'implémentation recommandé

1. ✅ introduire le modèle d'occurrence indépendant et rétrocompatible, incluant la couleur ;
2. ✅ remplacer la liste verticale intermédiaire par une piste horizontale statique et défilable ;
3. ✅ permettre de sélectionner un bloc et de recharger ses points IN / OUT dans la waveform source ;
4. ✅ ajouter renommage, couleur, déplacement, duplication, copie/collage, mute et répétition ;
5. ✅ relier la tête de lecture visuelle à la preview Structure réelle ;
6. ajouter ensuite la playlist latérale escamotable et valider le redimensionnement sans changement d'état.

La playlist escamotable est volontairement placée en dernier : la piste horizontale doit d'abord rester cohérente seule, puis démontrer qu'elle conserve son échelle, son zoom et son défilement lorsque la largeur disponible change.

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

- mode + → 1 segment (IN → OUT), inséré au début de la piste horizontale
- mode - → 2 segments (extérieur), insérés au début de la piste horizontale

---

### Suppression rapide

- bouton suppression directement sur le bloc concerné
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
- sur tablette, le premier Play d'un segment utilise directement le fichier audio source et ne doit jamais attendre un transcodage WAV
- structure basée sur segments préparés

---

### Tête de lecture structure

- une tête de lecture est affichée au-dessus de la piste horizontale
- elle suit la position réelle dans le montage préparé
- elle identifie l'occurrence et la répétition courantes
- son déplacement visuel ne constitue jamais une seconde horloge audio

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
- il n'est pas utilisé par la lecture Structure normale sur tablette
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
