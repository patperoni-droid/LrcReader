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

**Statut : workflow complet implémenté et validé sur téléphone et tablette.**

Une variante virtuelle est un projet Arrangement léger, par exemple `Marina_AR01`, qui :

- possède un identifiant stable propre ;
- référence le `songId` du morceau source ;
- conserve sa Structure et ses occurrences dans `arrangement.json` ;
- ne copie et ne transforme jamais le fichier audio source ;
- peut coexister avec plusieurs autres variantes du même morceau ;
- ne produit un WAV que par une action d'assemblage explicite.

Création depuis l'éditeur :

- l'action `Bibliothèque` est proposée dans l'éditeur Arrangement canonique sur téléphone et tablette ;
- la variante est créée dans le stockage interne normalisé et apparaît dans la Bibliothèque ;
- elle est clairement identifiée comme variante Arrangement ;
- elle peut être assignée à une playlist comme toute autre SongUnit ;
- le titre source et son Arrangement courant restent inchangés.

Lecture depuis la Bibliothèque :

- le Play de la Bibliothèque résout la variante vers son SongUnit source local ;
- les occurrences actives et leurs répétitions sont transformées en une liste Media3 complète avant Play ;
- les occurrences muettes sont exclues de cette préparation ;
- cette liste est chargée dans le lecteur principal officiel, sans second lecteur et sans rendu WAV ;
- Play, Pause, Stop et retour au début utilisent le `Playback Control` officiel ;
- position et durée sont exprimées dans le temps cumulé de l'Arrangement ;
- les paroles et accords propres à la variante utilisent son `songId` et le temps cumulé de sa Structure ;
- Timeline, annotations, MIDI, DMX, grille, contenu de prompteur lié au morceau et réglages de
  lecture propres à la variante sont préservés lors de l'aller-retour SMP ; leur édition et leur
  exécution avancées dans le temps cumulé de la Structure restent des chantiers futurs lorsqu'elles
  ne disposent pas encore d'un parcours utilisateur complet.

La variante peut être ajoutée à une playlist, regroupée avec d'autres SongUnits, modifiée,
partagée, sauvegardée et restaurée sans dupliquer l'audio parent.

Une variante virtuelle ne doit jamais être confondue avec un export WAV. L'export crée un audio indépendant ; la variante reste un montage non destructif dépendant de sa source.

### Sauvegarde et restauration des variantes virtuelles

**Statut : transport, sauvegarde et restauration validés.**

Une variante virtuelle ne possède pas son propre audio et ne constitue donc pas un `.smp` autonome complet.

Règles de transport :

- le `.smp` du morceau parent contient l'audio une seule fois ;
- son éventuel projet de travail `arrangement.json` est exporté avec le parent ;
- toutes ses variantes virtuelles sont regroupées dans `arrangement_variants.json` ;
- chaque variante y conserve son identifiant, son titre, son titre personnalisé, sa Structure
  complète et ses données prises en charge : paroles, brouillon brut d'édition, accords, couleurs,
  Timeline, annotations, MIDI/DMX, grille, contenu de prompteur lié au morceau, trim, vitesse,
  pitch et gain manuel ;
- aucun WAV, MP3 ou autre fichier audio supplémentaire n'est créé ;
- l'export autonome d'une variante virtuelle est refusé afin de ne jamais produire une sauvegarde incomplète ;
- une sauvegarde complète exporte uniquement le morceau parent, qui transporte automatiquement ses variantes.

À l'import :

- le morceau parent est d'abord validé, extrait et normalisé dans le runtime ;
- le manifeste de variantes est ensuite recréé en SongUnits virtuelles distinctes dans la Bibliothèque ;
- chaque Structure restaurée référence le `songId` local du parent importé ;
- le `sourceSongId` d'une variante existante est immuable : un même `songId` déjà rattaché à un autre parent provoque un refus explicite de la restauration ;
- une archive ne remplace que les données qu'elle transporte ; tout asset ou métadonnée locale absent de l'archive est conservé ;
- une restauration échouée remet en place les variantes déjà publiées ; si le parent venait d'être créé par cet import, il est également supprimé lorsque ce retour arrière reste possible ;
- la lecture utilise ensuite uniquement le stockage interne normalisé et ne relit jamais le `.smp` ;
- l'absence du manifeste dans un ancien `.smp` est normale et reste entièrement rétrocompatible.

La variante voyage donc dans la « valise » de son titre parent, tout en restant un élément indépendant dans la Bibliothèque après restauration.

### Historique du premier essai de restauration

Validation terrain du 22 juillet 2026 :

- la sauvegarde inspectée a été créée par une application installée avant l'ajout du nouveau transport Arrangement ;
- son `.smp` parent ne contient effectivement ni `arrangement.json` ni `arrangement_variants.json` ;
- les variantes visibles après restauration étaient les variantes runtime déjà présentes, car la restauration Bibliothèque est non destructive ;
- cet essai ne valide donc pas encore la recréation des variantes par le nouveau manifeste.

Une ancienne sauvegarde ne peut pas reconstruire un projet Arrangement qu'elle n'a jamais transporté.
Les validations suivantes ont ensuite confirmé toute la chaîne :

`arrangement.json runtime parent → export SMP → import SMP → chargement écran Arrangement`.

La sauvegarde/restauration actuelle préserve les occurrences, l'ordre, les points IN / OUT,
les répétitions, les mutes, les noms et les couleurs, et recrée les variantes virtuelles
à partir du manifeste.

Le test de Famille SongUnit complet ajouté le 10 août 2026 certifie également, après export,
suppression du runtime, import et nouveau scan, l'égalité des paroles et brouillons bruts,
accords, couleurs, Timeline, annotations, MIDI/DMX, grille, contenus de prompteur liés aux
morceaux, titres personnalisés et réglages de lecture pris en charge du parent et de ses variantes.

### Réouvrir une variante comme projet éditable

**Statut : mise en œuvre commune au téléphone et à la tablette.**

- si plusieurs variantes d'un même morceau existent, l'utilisateur doit pouvoir choisir celle qu'il préfère dans la Bibliothèque ;
- il doit pouvoir la rouvrir dans Arrangement avec sa Structure exacte ;
- les segments et toutes leurs propriétés doivent redevenir éditables ;
- le titre parent ne doit pas être écrasé implicitement ;
- après modification, l'utilisateur devra choisir explicitement entre mettre à jour cette variante et l'enregistrer comme une nouvelle variante.

Contrat de stockage :

- le `songId` de la variante reste le propriétaire de son `arrangement.json` ;
- le `sourceSongId` désigne uniquement le SongUnit parent qui fournit l'audio et la waveform ;
- l'ouverture d'une variante charge donc les données depuis son dossier normalisé, mais résout l'audio depuis le parent ;
- une variante active conserve les accès `Timeline`, `Arrangement` et `Waveform` du lecteur sur téléphone et tablette ;
- `Arrangement` ouvre le projet appartenant à la variante, tandis que `Waveform` résout le morceau parent qui possède réellement l'audio ;
- une mise à jour conserve le `songId` et le titre de la variante et remplace son projet de façon atomique ;
- « Enregistrer comme nouvelle variante » crée un nouveau `songId` et ne modifie ni la variante ouverte ni le parent ;
- aucun accès au `.smp` n'est autorisé pour cette édition runtime.

Exemple : avec `Marina-AR01`, `Marina-AR02` et `Marina-AR03`, il doit être possible de rouvrir `Marina-AR01`, la tester et continuer à la modifier sans reconstruire ses segments.

### Affecter une variante à une playlist

- une variante virtuelle peut être affectée à une playlist comme un autre titre de la Bibliothèque ;
- la playlist conserve uniquement son identité stable sous la forme `smp://songId` ;
- l'affectation ne crée, ne transcode et ne duplique aucun fichier audio ;
- la lecture depuis la playlist résout la Structure de la variante et l'audio de son titre parent depuis le runtime normalisé ;
- la variante reste l'élément actif et visible dans la playlist et dans le Player ;
- une sauvegarde contenant cette référence exporte le SongUnit parent, car son `.smp` transporte automatiquement toutes ses variantes ;
- si le parent est absent, la lecture et la sauvegarde complète doivent échouer proprement sans modifier la playlist.

### Propriété, dépendance et suppression

Contrat fonctionnel :

- une variante virtuelle ne peut exister que si son titre parent existe dans la Bibliothèque normalisée ;
- le parent reste l'unique propriétaire du fichier audio ;
- la variante possède son propre `songId`, son titre, sa Structure et peut posséder ses propres
  paroles, accords, données Timeline, annotations, MIDI/DMX, grille, contenu de prompteur lié au
  morceau et réglages de lecture ;
- ces données propres à la variante sont stockées dans son dossier runtime, mais voyagent dans la « valise » `.smp` du parent ;
- aucun `.smp` autonome incomplet ne doit être créé pour une variante.

Suppression dans la Bibliothèque :

- supprimer le parent doit supprimer toutes ses variantes et toutes les références de playlist correspondantes ;
- la confirmation doit annoncer clairement le nombre de variantes concernées avant toute suppression ;
- supprimer une variante ne supprime jamais le parent ni les autres variantes ;
- aucune variante orpheline ne doit subsister silencieusement.

Suppression dans une playlist :

- retirer le parent d'une playlist ne retire aucune variante présente dans cette playlist ;
- retirer une variante d'une playlist ne retire ni le parent ni les autres variantes ;
- une playlist peut contenir uniquement la variante, sans occurrence visible du parent, tant que le parent reste présent dans la Bibliothèque.

**État d'implémentation :** la suppression en cascade parent → variantes, la préservation des
assets lors d'une mise à jour Arrangement et le transport de toutes les données de variante
actuellement prises en charge sont implémentés. Les futurs formats devront être ajoutés au même
contrat d'aller-retour avant d'être considérés comme persistants.

### Paroles propres à une variante

**Étape paroles mise en œuvre :**

- une variante peut posséder un fichier `lyrics.lrc` dans son propre dossier runtime ;
- le Player et l'éditeur résolvent ce fichier par le `songId` de la variante, jamais par l'audio du parent ;
- écrire, importer, colorer ou synchroniser les paroles d'une variante ne modifie pas les paroles du parent ;
- l'onglet Synchro utilise le temps cumulé de la Structure jouée, y compris l'ordre et les répétitions ;
- les paroles locales doivent survivre à une mise à jour de la Structure et au redémarrage de l'application ;
- `arrangement_variants.json` transporte le contenu de `lyrics.lrc` et les couleurs de lignes avec l'identité stable de chaque variante ;
- l'import du `.smp` parent recrée ces données dans le dossier runtime de la variante, sans écrire dans les paroles du parent ;
- une ancienne sauvegarde sans assets de variante reste compatible et restaure simplement la Structure sans paroles propres ;
- les accords suivent désormais le même principe d'identité et de transport.

### Accords propres à une variante

- une variante peut posséder son propre fichier `chords.lrc` dans son dossier runtime ;
- le Player et l'éditeur lisent et écrivent ce fichier par le `songId` de la variante, sans modifier les accords du parent ;
- le fichier peut être créé, édité, synchronisé ou supprimé avec le flux Accords existant ;
- `arrangement_variants.json` transporte son contenu dans les assets optionnels de la variante ;
- l'import restaure les accords transportés dans le dossier de la variante et conserve les accords locaux lorsqu’une ancienne archive n'en transporte pas ;
- les anciennes archives sans accords propres restent compatibles ;
- le comportement est commun au téléphone et à la tablette.

### Partager une variante

- l'action `Partager` est disponible directement sur une variante dans la Bibliothèque ;
- la variante ne devient pas un `.smp` autonome sans audio : l'export résout son SongUnit parent et réutilise sa valise ;
- le fichier partagé contient l'audio parent une seule fois et uniquement la variante sélectionnée dans `arrangement_variants.json` ;
- le manifeste marque ce partage avec le `selectedVariantId`, fondé uniquement sur le `songId`, afin de le distinguer sans utiliser le nom du fichier ou le titre ;
- le `songId` de la variante, son titre, sa Structure et ses assets propres transportés sont conservés ;
- partager le parent conserve le comportement existant et transporte toutes ses variantes ;
- l'export est une lecture du runtime normalisé et ne modifie ni le parent ni la variante.
- à l'import, si le `songId` parent existe déjà, le parent local reste intact et seule la variante marquée est ajoutée ou remplacée ;
- si le parent n'existe pas, le pipeline SMP complet normalise d'abord le parent transporté puis recrée la variante ;
- les anciens `.smp` sans `selectedVariantId` conservent exactement leur comportement d'import complet.

---

## 🧩 CONCEPTS CLÉS

### Lecture de la Structure sur téléphone

- la lecture directe depuis l'audio source est le mode recommandé et le comportement par défaut ;
- un mode de compatibilité avancé, visible uniquement sur téléphone dans
  `Plus → Avancé`, permet de réutiliser intégralement la préparation historique
  WAV / Sampler lorsque les transitions sont instables sur un appareil ;
- changer ce réglage sélectionne l'un des deux pipelines existants sans modifier la Structure ni ses données ;
- la tablette conserve exclusivement son comportement direct actuel.

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

**Statut : piste horizontale validée sur tablette et portée dans l'éditeur canonique téléphone.**

### État d'implémentation

Fondation tablette réalisée :

- l'entrée dans l'éditeur Arrangement est désormais signalée explicitement au layout tablette ;
- pendant cette édition, la playlist est repliée par défaut et l'éditeur conserve la même instance tout en prenant toute la largeur disponible ;
- une commande dans Arrangement permet d'afficher la playlist à gauche avec la répartition habituelle `38 % / 62 %`, puis de la replier sans quitter ni recréer l'éditeur ;
- sélectionner un autre titre dans cette playlist conserve le contrat du Playback Control : l'Arrangement courant ne change que lorsque le titre préparé est effectivement lancé ;
- les raccourcis supérieurs du panneau droit sont masqués afin de libérer la hauteur utile ;
- le contenu d'Arrangement est contraint et défilable au-dessus du `Playback Control`, qui reste visible en bas ;
- sur tablette, l'entrée `Arrangement` de l'écran `Paramètres / Plus` ouvre désormais l'éditeur canonique intégré à `Timeline / Grille` lorsqu'un morceau SMP est actif ;
- l'ancien éditeur à deux colonnes n'est plus la route principale ; il reste uniquement un repli lorsqu'aucun morceau SMP actif ne peut alimenter l'éditeur canonique ;
- dans l'éditeur canonique tablette, le bouton `Fermer` et le bouton Retour Android restaurent le cockpit `Playlist | Paroles` sans arrêter ni remplacer le morceau actif ;
- les adaptations de cockpit, de playlist latérale et de plein écran restent strictement conditionnées au layout tablette.

Implémenté dans l'éditeur canonique partagé :

- au premier affichage, la waveform utilise une vue générale échantillonnée de 2 000 points et un cache distinct afin d'éviter le décodage complet du MP3 ; le placement de la tête et des points IN / OUT reste calculé en millisecondes et conserve sa précision ;
- les deux points d'entrée Arrangement du téléphone ouvrent le même éditeur canonique et réutilisent le même extracteur échantillonné avec 720 points et le même cache ;
- les colonnes `Segments` et `Structure` sont fusionnées visuellement en une seule liste ordonnée ;
- le bouton `Ajouter` insère directement le nouveau segment en tête de cette liste et dans la Structure de lecture ;
- supprimer une ligne retire cette occurrence et supprime aussi son segment interne lorsqu'aucune autre occurrence ne le référence ;
- sur téléphone uniquement, les nouveaux segments reçoivent par défaut un nom alphabétique compact : `A` à `Z`, puis `AA`, `AB`, etc. ;
- sur tablette, le nom proposé reste `Segment N` ;
- ces noms par défaut restent entièrement modifiables avec le même dialogue de renommage sur les deux appareils ;
- les projets existants conservent leurs noms : aucune migration ni réécriture automatique n'est effectuée ;
- le modèle persistant V2 d'une occurrence est disponible avec `entryId`, nom, `startMs`, `endMs`, `repeatCount`, `muted` et `color` ;
- la lecture d'un ancien fichier V1 crée en mémoire des occurrences indépendantes et déterministes, sans réécrire automatiquement le fichier ;
- un fichier V2 conserve une projection `segments` / `structureSegmentIds` rétrocompatible ;
- téléphone et tablette enregistrent le modèle V2 uniquement après une modification explicite dans l'éditeur canonique ;
- les champs d'occurrence V2 participent au hash de synchronisation afin que répétition, mute et couleur soient transférés comme données de l'Arrangement.
- la Structure est rendue comme une piste horizontale statique et défilable juste au-dessus du `Playback Control` ;
- sur tablette, chaque bloc affiche son nom sans préfixe automatique de position et sa durée, avec une largeur liée à la durée et une largeur tactile minimale ;
- sur téléphone, seul le nom est affiché dans le bloc et détermine sa largeur, avec une sécurité tactile discrète de `48 dp` et une limite pour les noms très longs ;
- la sélection active reste verte, la prochaine occurrence préparée reste jaune et la suppression conserve le comportement existant ;
- le défilement horizontal est un état d'interface local : il ne modifie ni l'ordre, ni le stockage, ni la lecture audio ;
- lors du chargement, chaque occurrence de la Structure reçoit son `entryId` V2 indépendant, y compris lorsque plusieurs occurrences provenaient du même segment V1 ;
- toucher un bloc recharge ses propres `startMs` / `endMs` dans les poignées IN / OUT de la waveform source ;
- une correction IN / OUT, une suppression ou un undo conserve cette identité d'occurrence et sauvegarde la Structure en V2 ;
- les segments V1 non placés dans la Structure restent conservés dans le fichier pour éviter toute perte pendant la migration ;
- ouvrir l'Arrangement ou sélectionner un bloc ne réécrit pas le fichier : la migration V2 est enregistrée seulement lors d'une modification explicite ;
- chaque bloc propose renommage, couleur, collage à la tête, mute, répétition et suppression explicite ;
- la poignée de déplacement déplace une occurrence d'un voisin vers la gauche ou la droite sans changer son `entryId` ;
- sur téléphone, la poignée et le bouton d'options sont retirés du bloc pour gagner de la place : l'appui long ouvre le menu, qui conserve notamment le déplacement d'un voisin vers la gauche ou la droite ;
- un collage crée à la frontière choisie une occurrence avec un nouvel `entryId` indépendant et ne duplique jamais le fichier audio ;
- la couleur, le mute et `repeatCount` sont sauvegardés dans l'entrée V2 ;
- avant la preview ou l'export, les occurrences en mute sont exclues et les répétitions sont développées dans la liste audio préparée ;
- pendant une lecture Structure, une préparation ou un export, l'ajout, le déplacement et les actions de structure sont verrouillés afin de ne jamais reconstruire le montage en pleine lecture ;
- la tête turquoise reste visible sur la piste hors lecture ; après Stop, elle conserve la dernière position exacte, y compris au milieu d'un segment ;
- toucher ou faire glisser la zone supérieure de la piste passe en édition et quantifie la tête sur la frontière de segment la plus proche ;
- après un Stop au milieu d'un segment, `Coller ici` reste indisponible tant qu'un toucher n'a pas choisi une frontière non ambiguë ;
- `Coller ici`, dans le menu du segment sélectionné, duplique ce segment à la frontière indiquée par la tête ;
- pour coller sans déclencher de lecture, l'utilisateur place d'abord la tête sur la frontière voulue, puis effectue un appui long sur le bloc source afin d'ouvrir directement son menu et choisit `Coller ici` ;
- l'appui court conserve son rôle d'écoute du segment ; l'appui long est le geste d'édition qui ouvre le menu sans Play et sans déplacer la tête ;
- sa position utilise le temps cumulé du montage préparé, jamais le temps absolu de la waveform source ;
- pour un bloc répété, la tête parcourt successivement chaque fraction du bloc et affiche la répétition active sous la forme `1/N`, `2/N`, etc. ;
- les occurrences en mute restent visibles dans l'éditeur mais sont sautées par la tête de lecture comme elles le sont par la liste audio préparée ;
- le défilement horizontal conserve l'alignement entre la tête et le bloc actif sans créer d'horloge ou d'animation indépendante du lecteur ;
- la piste compacte n'affiche plus la ligne de titre `Structure` et sa hauteur est réduite afin de limiter le défilement vertical de l'écran ;
- tant qu'aucun bloc n'est sélectionné, le `Playback Control` pilote le titre complet ;
- toucher un bloc arrête d'abord le titre complet puis donne temporairement les commandes du `Playback Control` à cette occurrence ;
- pendant ce ciblage, Pause arrête entièrement la preview secondaire et Play relance le segment sélectionné depuis son début ;
- toucher la waveform source arrête immédiatement la preview secondaire, libère le ciblage et place le titre complet en pause à la position touchée ; le Play suivant démarre donc le titre complet depuis cette position ;
- le bouton retour début arrête la preview secondaire, libère le ciblage du bloc, replace le titre complet à `00:00` et rend les commandes au lecteur principal ;
- le lecteur principal et la preview de segment ne doivent jamais jouer simultanément.
- toute preview Arrangement active devient une source Player officielle et coupe le fond sonore via `PlaybackCoordinator` avant de produire du son.
- sur tablette, le bandeau supérieur de navigation reste visible dans Arrangement afin d'ouvrir directement les autres écrans ; l'action textuelle `Fermer` n'est pas affichée dans ce mode, tandis que le bouton Retour Android reste disponible.

### Périmètre appareil

La gestion des segments et la construction de la Structure utilisent désormais la même piste horizontale sur téléphone et tablette.

- le téléphone emploie l'éditeur canonique avec une waveform échantillonnée et une piste horizontalement défilable adaptée à sa largeur ;
- les nouveaux noms compacts `A`, `B`, `C`… sont propres au téléphone ;
- la tablette conserve ses noms `Segment N`, son layout plein écran et son comportement audio validé ;
- la playlist latérale escamotable dans Arrangement reste strictement conditionnée au mode tablette ;
- les données, les actions d'occurrence et le dialogue de renommage restent communs ;
- toute évolution du stockage partagé doit rester transparente et rétrocompatible pour les projets existants.

L'interface actuelle ne sépare plus :

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
- peut être déplacé d'un voisin vers la gauche ou la droite pour modifier l'ordre final ;
- ne contient ni ne duplique le fichier audio source.

Présentation par appareil :

- sur tablette, le bloc affiche son nom sans préfixe automatique de position et sa durée ; sa largeur est liée à la durée
  et ses contrôles directs restent visibles ;
- sur téléphone, seul le nom détermine la largeur, avec une sécurité tactile discrète ;
  l'appui long ouvre le menu complet et aucun bouton d'options ou poignée n'occupe le bloc.

Sur les deux appareils, le libellé visible correspond exactement au nom du segment : `Couplet 2`,
jamais `3. Couplet 2`. L'ordre interne des occurrences reste conservé ; les indications de
répétition `×N` / `1/N` et les noms initiaux `Segment N` restent inchangés.

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

Le nom proposé à la création est `Segment N` sur tablette et une lettre compacte (`A`, `B`, `C`… puis `AA`…) sur téléphone. Ce nom n'est qu'une valeur initiale : l'utilisateur peut le remplacer librement par `Intro`, `Couplet`, `Refrain`, `Pont`, `Solo`, `Outro` ou tout autre libellé.

### Édition d'un segment existant

- toucher un bloc sélectionne cette occurrence ;
- ses points IN et OUT sont rechargés dans l'éditeur de droite ;
- l'utilisateur peut corriger IN ou OUT sans recréer le segment ;
- les modifications restent non destructives pour l'audio source ;
- la durée visible de la ligne est recalculée depuis `endMs - startMs`.

### Actions sur une occurrence

Chaque bloc de la piste unique peut être :

- déplacé d'un voisin vers la gauche ou la droite ;
- copié puis collé ;
- dupliqué ;
- supprimé explicitement ;
- mis en mute sans être supprimé ;
- configuré pour être joué plusieurs fois consécutivement.

Sur tablette, les contrôles directs restent dans le bloc. Sur téléphone, les mêmes actions
sont accessibles par appui long dans le menu contextuel afin de conserver une piste compacte.

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
3. préparer la liste logique complète des segments avant Play ;
4. seulement ensuite démarrer la preview Arrangement.

Modes audio actuels :

- tablette : lecture directe depuis l'audio source ;
- téléphone par défaut : même lecture directe, avec segments Media3 clipés et file préparée ;
- téléphone en mode de compatibilité : préparation historique WAV, extraction PCM puis
  `SamplerEngine`, avec ses replis ExoPlayer existants.

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
6. ✅ ajouter ensuite la playlist latérale escamotable et valider le redimensionnement sans changement d'état.

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

- tablette : suppression accessible depuis les contrôles du bloc
- téléphone : suppression accessible par appui long dans le menu contextuel
- suppression explicite de l'occurrence concernée
- aucune seconde Structure à nettoyer ou synchroniser

---

## 🔁 LECTURE STRUCTURE

### Principe

- preview de travail rapide
- transport visible unique via le `Playback Control` officiel
- lecteur secondaire local en mode direct, avec segments clipés Media3
- `SamplerEngine` préchargé uniquement en mode téléphone de compatibilité
- sur tablette et en mode téléphone recommandé, le premier Play utilise directement le
  fichier audio source et n'attend aucun transcodage WAV
- sur téléphone, le mode avancé de compatibilité réutilise volontairement le pipeline
  WAV/Sampler historique sans en modifier le comportement
- structure basée sur segments préparés

---

### Tête de lecture structure

- une tête de lecture est affichée au-dessus de la piste horizontale
- elle suit la position réelle dans le montage préparé
- elle identifie l'occurrence et la répétition courantes
- son déplacement visuel ne constitue jamais une seconde horloge audio

---

### Seek structure

- après Stop, la tête conserve la position exacte atteinte, y compris au milieu d'un segment
- un toucher ou glissement manuel quantifie ensuite la tête sur la frontière de segment la plus proche
- les frontières donnent un accès direct aux transitions et servent de positions non ambiguës pour le collage

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

## 🧹 RETRAIT DES OUTILS EXPÉRIMENTAUX

Décision validée le 22 juillet 2026 :

- l'écran utilisateur `Sampler Test` est supprimé ;
- le bouton `Écouter` et sa pré-écoute WAV temporaire sont supprimés ;
- la piste et le `Playback Control` restent l'unique parcours utilisateur de préécoute ;
- la lecture directe est recommandée et activée par défaut sur téléphone ;
- un réglage avancé téléphone permet de réutiliser intégralement le moteur WAV/Sampler
  comme mode de compatibilité, sans réintroduire `Sampler Test` ni `Écouter` ;
- `Assembler` reste la seule génération WAV, déclenchée volontairement pour créer le fichier final.

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

- la sauvegarde complète reconstruit le `.smp` du parent depuis le runtime normalisé ;
- ce `.smp` transporte l'audio une seule fois, le projet parent et ses variantes ;
- les données propres aux variantes actuellement prises en charge sont incluses : Structure,
  paroles et brouillon brut, accords, couleurs, Timeline, annotations, MIDI/DMX, grille, contenu
  de prompteur lié au morceau, titre personnalisé et réglages de lecture transportables ;
- le nom de sauvegarde proposé est modifiable avant création ;
- `state.json` transporte automatiquement les playlists, leurs groupes et leurs associations.

---

## 🔄 RESTAURATION

- les choix `Conserver / Remplacer` sont indépendants pour les morceaux et les playlists ;
- les parents sont restaurés avant leurs variantes ;
- l'index Bibliothèque est rafraîchi avant la reconstruction des playlists ;
- les groupes, l'ordre, les couleurs, les occurrences et les références parent/variante
  sont restaurés automatiquement ;
- aucun `songId` valide ne doit devenir « Titre manquant » ;
- le Playback ne doit jamais servir à réparer une référence restaurée.

Le contrat complet et les limites restent définis dans `FEATURE_EXPORT_BACKUP.md`.

---

## 🧠 PRINCIPES CLÉS

- ExoPlayer = source de vérité (timeMs)
- aucun traitement lourd en live
- aucun seek utilisé pour simuler une structure
- structure basée sur segments préparés

### Séparation fondamentale

- preview Structure = travail rapide sur segments
- export = génération finale SMP/runtime

### Règles critiques

- La Structure ne doit jamais piloter le Player principal.
- Aucun traitement lourd pendant le live.
- Les transitions doivent être préparées.
- Ne pas modifier AudioEngine pour corriger Arrangement preview.

---

## 🛑 INTERDIT

- seekTo pour simuler une structure
- logique cachée ou automatique imprévisible
- accumulation de fichiers temporaires
- dépendance UI pour logique audio
- modifier AudioEngine pour corriger la preview Arrangement

---

## 🏁 OBJECTIF FINAL

Créer un système :

- rapide à éditer
- précis
- fiable en live
- sans friction utilisateur

👉 un véritable outil de travail musical  
