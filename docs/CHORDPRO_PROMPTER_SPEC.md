# MusiMio — ChordPro dans le Prompteur

> **Annexe spécialisée.** Le contrat transversal
> [FEATURE_CHORDPRO.md](Features/FEATURE_CHORDPRO.md) définit les invariants communs
> ChordPro de MusiMio. Le présent document détaille uniquement le Prompteur autonome,
> son éditeur, son rendu et ses interactions. Après validation humaine, le contrat
> transversal prévaut en cas de contradiction sur une règle commune.

## Statut et objectif

État documentaire réconcilié le **27 septembre 2026**, branche
`feature/chordpro-audio-lyrics`. La navigation par portions d'écran, l'édition
exacte d'un accord depuis la palette et l'import assisté ont été validés séparément.
Ce document actualise le cahier des charges initial : les comportements présents sont
décrits ci-dessous, les intentions restantes sont regroupées en fin de document.
Il ne constitue ni une certification complète de la bêta ni une annonce de publication.

ChordPro a été introduit pour lire **paroles et accords ensemble**, sans horodatage,
dans le Prompteur des textes défilants. Le musicien peut saisir ou coller
`Je [Am]voulais te [F]dire`, puis voir les accords au-dessus des paroles.
La palette produit la même source que la saisie manuelle ; il n'existe pas de second
modèle musical réservé à la palette.

L'intégration actuelle est un **sous-ensemble ChordPro pour les accords**, complété
par une mise en forme légère propre à MusiMio. Ce n'est pas un interpréteur complet
du standard ChordPro.

## 1. Périmètre et composants

| Responsabilité | Code actuel, sous `app/src/main/java/com/patrick/lrcreader/` |
|---|---|
| Reconnaissance des accords et positions source | `core/ChordProParser.kt` |
| Titres, sections, emphase et couleurs | `core/PrompterRichTextParser.kt` |
| Correspondance source / texte visible / accords colorés | `core/PrompterTextPreparation.kt` |
| Affichage et retour depuis l'éditeur | `ui/TextPrompterScreen.kt` |
| Modèle des lignes, wrapping, styles et rendu Compose | `ui/PrompterUi.kt` |
| Interface d'édition et panneaux partagés | `ui/ScrollingTextEditorDialog.kt` |
| Conditions de visibilité téléphone/tablette | `ui/ScrollingTextEditorVisibility.kt` |
| Insertion, remplacement et retrait de marqueurs | `ui/ChordProTextEditing.kt` |
| Import assisté et remappage du brouillon | `core/ChordProImportNormalizer.kt`, `core/ChordLineImportNormalizer.kt`, `ui/ScrollingTextChordProImport.kt` |
| Session d'édition Bibliothèque / crayon du Prompteur | `ui/EditScrollingTextDialog.kt` |
| Création | `ui/CreateScrollingTextDialog.kt`, `ui/ScrollingTextCreation.kt` |
| Texte et réglages d'affichage | `core/TextSongRepository.kt`, `core/TextPrompterDisplaySettingsStore.kt` |

Le parcours principal concerne les textes autonomes du catalogue Bibliothèque,
aussi référencés depuis les playlists. `TextPrompterScreen` sait également lire
les anciennes entrées `NotesRepository` ; leur affichage utilise la même préparation,
mais elles ne disposent pas du nouveau crayon direct du catalogue.

Les paroles et accords **synchronisés du Lecteur** sont une intégration ChordPro
existante mais restent des contenus, timings et parcours d'édition distincts. Les deux
surfaces partagent le parser et la transposition des accords définis par le contrat transversal ;
les détails du Lecteur sont dans
[CHORDPRO_AUDIO_LYRICS_SPEC.md](CHORDPRO_AUDIO_LYRICS_SPEC.md). Le composant distinct
`PrompterArea` affiche encore du texte simple : son nom ne signifie pas qu'il utilise
le rendu ChordPro.
Aucune généralisation aux assets de prompteur d'une SongUnit ou au Deuxième écran
ne doit être déduite de ce patch.

## 2. Syntaxe effectivement reconnue

### Accords

Le Prompteur utilise exclusivement le parser et le validateur canoniques décrits dans
[FEATURE_CHORDPRO.md](Features/FEATURE_CHORDPRO.md#5-parser-et-validateur-canoniques).
Un groupe `[...]` reconnu devient un `ChordAnchor` ; les autres caractères restent
dans le texte visible. L'ancre conserve l'offset dans les paroles, la plage source et
le symbole structuré nécessaires au rendu et à l'édition du Prompteur.

`[Refrain]`, `[H]`, `[Am` ou un groupe invalide restent du texte visible.
En revanche, `[C]` est reconnu même si l'auteur l'entendait comme une annotation.
Il n'existe pas de mécanisme d'échappement dédié.

Les directives `{title:}`, `{subtitle:}`, `{artist:}`, `{key:}`, `{comment:}`,
`{start_of_chorus}` et `{end_of_chorus}` ne sont **pas interprétées**.
Les accolades restent du texte ; elles ne désactivent pas les autres parsers.

### Mise en forme MusiMio et commandes de la palette

| Commande disponible | Source produite / reconnue | Effet |
|---|---|---|
| Accord de la palette | `[Am]` par exemple | Accord au-dessus des paroles |
| Titre | `# Titre` | Titre dans le corps, distinct du titre du catalogue |
| Section | `## Section` | En-tête de section |
| Couplet | `## Couplet` | Même type de section |
| Refrain | `## Refrain` | Même type de section |
| Commentaire | `*Commentaire*` sur une ligne séparée | Indication en italique |
| Gras | `**texte**` | Emphase grasse |
| Italique | `*texte*` | Emphase italique |
| Séparateur | `---` sur une ligne séparée | Trait horizontal |
| Jaune, orange, rouge, bleu, vert | `<c=nom>texte</c>` | Couleur explicite |
| Aucune / défaut (retour à la couleur par défaut) | Retrait des balises couleur concernées | Couleurs habituelles du rendu |

`# ` et `## ` sont reconnus au début de la ligne, sans indentation préalable.
Le séparateur peut avoir des espaces autour, mais doit occuper la ligne entière.
Gras et italique nécessitent des paires complètes non vides sur une même ligne.
Les emphases imbriquées et le Markdown complet ne sont pas pris en charge.

Il n'existe pas de commande sous-titre, de bloc refrain avec début/fin, ni de
répétition automatique de section. Couplet et refrain sont des intitulés de section,
pas des instructions de lecture. Les commandes de bloc ajoutent au besoin des
retours à la ligne autour de l'insertion. Sur une sélection multiligne, Titre et
Section ne préfixent que son début ; Commentaire encadre chaque ligne non vide.

### Couleurs

Les noms reconnus, sensibles à la casse, sont `yellow`, `orange`, `red`, `blue`,
`green`, `white`. Une plage doit être non vide et tenir sur une ligne. Plusieurs
plages séparées sont possibles ; les couleurs imbriquées ne sont pas prises en charge.
Une couleur peut coexister avec du gras ou de l'italique simple.
Les balises inconnues, incomplètes ou imbriquées restent littérales ; les autres
passes (accords/emphase) peuvent cependant interpréter leur contenu.

```text
## Refrain
Je <c=yellow>[Am]voulais</c> te [F]dire
<c=blue>*Plus doucement*</c>
<c=red>[G7]</c>
```

Dans cet exemple, `Am` et « voulais » sont jaunes ; `G7` seul est rouge.
La préparation conserve la couleur couvrant l'accord **avant** de retirer ses
crochets, puis remappe ses positions après retrait des marqueurs de mise en forme.
Un accord seul coloré ne laisse pas de balises vides à l'écran.
Un `<c=yellow></c>` réellement vide dans la source reste en revanche littéral.

Le rendu emploie jaune `#FFD54F`, orange `#FFB74D`, rouge `#FF6B6B`, bleu
`#64B5F6`, vert `#81C784`. `white` signifie la couleur courante des paroles
(blanche dans cet écran), pas une couleur blanche imposée indépendamment du thème.
Sans couleur explicite, les paroles utilisent leur couleur habituelle et les accords
`SplColors.Accent`. Le bouton **Aucune / défaut**, représenté par une flèche de restauration,
supprime les balises ; il n'insère pas `white`. Les anciennes balises `white`
restent lisibles.

### Texte normal et compatibilité

Sans accord ni formatage reconnu, le viewport conserve son chemin historique :
un `Text` reçoit directement le contenu source. Sans accord mais avec une mise en
forme reconnue, le rendu enrichi est utilisé. Aucune migration des anciens textes
n'est nécessaire. Les marqueurs valides déjà présents dans un ancien texte sont
interprétés : « compatible » ne signifie donc pas que `[C]` ou `# Titre` restent
littéraux.

## 3. Rendu du Prompteur

`preparePrompterText(content)` est mémorisé par contenu dans `TextPrompterScreen`.
Les lignes de rendu sont également mémorisées par document et mode. Le parsing
n'est pas relancé à chaque pas de défilement.

Chaque accord est placé au-dessus de la portion de paroles où se trouvait son
ancre. Un mot peut contenir plusieurs portions ; le mot entier reste une unité de
retour à la ligne. Les accords adjacents au même offset sont affichés côte à côte.
Un accord terminal ou une ligne contenant seulement des accords possède aussi une
unité de rendu.

Les mots sont disposés dans un `FlowRow` : une ligne longue se replie entre les
mots, sans séparer volontairement un accord de son mot. La largeur d'une portion
peut être augmentée par celle de ses accords ; il ne s'agit pas d'une grille
monospace conservant toutes les coordonnées horizontales du texte source.
Un mot indivisible ou une grappe d'accords plus large que l'écran n'a pas de
stratégie spéciale de découpage et peut déborder.

Les lignes avec accords réservent une bande d'accords au-dessus de leurs portions,
y compris celles sans accord dans cette ligne. Les lignes sans accord n'ajoutent
pas cette bande. Les lignes vides conservent un espace de hauteur de ligne ; titres,
sections, séparateurs et wrapping produisent des hauteurs variables mesurées par Compose.
L'alignement Gauche/Centré s'applique au texte et aux rangées de mots avec accords.

### Taille, zoom et défilement

Le viewport expose `fontSize` et `lineHeight`, mais l'appel actuel utilise leurs
valeurs par défaut : **26 sp / 32 sp**. Aucun contrôle de zoom ou de taille propre
au Prompteur ChordPro n'est branché dans cet écran. Le réglage global de taille des
paroles synchronisées ne lui est pas transmis.

La taille des accords est dérivée de celle de la ligne (70 %, bande de 82 %).
Les titres utilisent un facteur 1,18 et les sections 1,08. Un changement de largeur
ou d'échelle Android modifie les mesures et le wrapping, sans modifier les ancres
source. La taille indépendante des accords et la conservation d'un mot de lecture
précis lors d'un changement de géométrie restent absentes.

Le défilement autonome utilise le `ScrollState` vertical et une animation linéaire
vers sa limite mesurée. Les lignes n'ont pas de hauteur fixe supposée par le parser.
Le Prompteur reste autonome : il n'utilise ni horodatage ChordPro ni ExoPlayer.

### Transposition des accords

Le Prompteur transpose les accords affichés avec le moteur commun, dans la plage
`-11..+11`. Le texte ChordPro source reste inchangé. La valeur est enregistrée dans
`TextPrompterDisplaySettingsStore` avec l'identité stable du texte puis restaurée à
la réouverture. Ce réglage est local au texte sur cet appareil, distinct de Transpo
accords d'Audio Lyrics. Il ne modifie aucun pitch audio et n'utilise pas Sync Pitch.
Play/Pause pilote le défilement, pas une lecture musicale synchronisée.

Dans l'écran de lecture, le contrôle de transposition des accords est affiché lorsque le document
préparé contient au moins un accord reconnu. L'éditeur partagé reçoit la même valeur
pour permettre son ajustement sans créer de second état musical. Les invariants de
transposition des accords sont définis dans
[FEATURE_CHORDPRO.md](Features/FEATURE_CHORDPRO.md#8-transposition-des-accords-commune).

La navigation manuelle précédente/suivante est commune aux deux boutons tactiles et
aux commandes clavier Android. Elle déplace le texte de **65 % de la hauteur réellement
visible**, conserve **35 % de chevauchement**, puis anime le déplacement avec un `tween`
de **300 ms**. La cible reste bornée entre zéro et `ScrollState.maxValue`.

Le Prompteur traite les touches suivantes lorsqu'il est actif et possède le focus :

- précédent : gauche, haut et Page Up ;
- suivant : droite, bas et Page Down ;
- début/fin : Home et End.

Une pédale Bluetooth compatible n'est pas connectée par une API propriétaire : Android
la présente comme un clavier physique et MusiMio traite ses `KeyEvent`. Le focus de la
racine du Prompteur est demandé à l'ouverture et repris avant une commande matérielle si
nécessaire.

Pendant Play, une correction manuelle remplace l'animation automatique en cours. Une fois
le déplacement terminé, `manualScrollRevision` relance l'auto-scroll depuis la nouvelle
position sans désactiver Play. Home suit la même mécanique. End conserve Play actif et
aboutit naturellement à une distance restante nulle. La hauteur du viewport et la valeur
maximale défilable font partie des clés de relance : une rotation ou un changement de
dimensions annule le calcul obsolète et recalcule la distance restante.

Cette navigation appartient exclusivement au Prompteur. Elle ne décrit ni le scroll ni
les paroles synchronisées du lecteur Audio + Paroles.

## 4. Éditeur partagé et sélection

Le crayon de l'entrée **Bibliothèque → Textes défilants** et celui situé en haut à
droite du Prompteur ouvrent le même `EditScrollingTextDialog(textSongId)`.
Celui-ci reçoit l'identité du texte réellement affiché, pas son titre visuel ni le
morceau audio actif. Après ✓, le même texte est mis à jour, le dialogue se ferme et
le Prompteur recharge contenu et alignement via `editRevision`.
Le Prompteur reste monté ; ce retour ne constitue pas une navigation nouvelle.
Il ne garantit pas la même phrase à l'écran si la hauteur du texte a changé.

La création et l'édition depuis une playlist réutilisent aussi
`ScrollingTextEditorDialog`, avec leurs propres callbacks de session. La création
et l'édition depuis une playlist exigent titre et contenu non vides ; l'édition
Bibliothèque/Prompteur exige le titre non vide. ✓ valide et ferme, × annule la session.
Il n'y a pas de sauvegarde automatique du brouillon dans ce dialogue.

### Import assisté

L'éditeur partagé analyse le brouillon et peut proposer une conversion ChordPro pour
un balisage explicite `**accord**` ou pour des lignes et blocs d'accords placés avant
des paroles. Une bannière unique demande **Convertir** ou **Ignorer** ; aucune
conversion n'est silencieuse et une analyse calculée sur une ancienne version du
texte est refusée.

Les règles communes de validation token par token, de blocs de une à trois lignes,
de projection, de faux positifs et d'évolution multilingue sont définies dans
[FEATURE_CHORDPRO.md](Features/FEATURE_CHORDPRO.md#11-import-de-balisage-accord).

### Palette d'accords

La palette courante est dérivée automatiquement des `ChordAnchor` reconnus dans la
source du morceau. Elle conserve l'ordre de première apparition et supprime les doublons
exacts. Il n'existe pas de seconde liste musicale à maintenir ni de remplacement fondé
sur du texte libre.

Un clic appelle `editOrInsertPrompterChord` :

- au curseur, insère `[accord]` sans ajouter d'espace ;
- sur une sélection ordinaire, insère à son début sans supprimer le texte sélectionné ;
- avec le curseur strictement à l'intérieur d'un accord reconnu, ou son tag entièrement
  sélectionné, remplace cet accord ;
- place ensuite le curseur après le tag inséré ou remplacé.

L'accord se supprime comme du texte normal. Aucun bouton de suppression d'accord
spécifique n'existe. Les boutons d'accords ne prennent pas le focus et redemandent
celui du champ après l'opération.

Un appui long sur un accord de la palette du Prompteur ouvre **Modifier l'accord** avec
la valeur actuelle préremplie. Après validation, une seconde confirmation propose de
remplacer toutes ses occurrences dans le morceau courant. Le remplacement :

- reparcourt la source avec `parseChordPro` ;
- conserve uniquement les ancres dont `ChordSymbol.raw` est exactement égal à l'ancien
  accord ;
- remplace les plages `sourceRange` en ordre inverse pour préserver leurs offsets ;
- remappe la sélection dans le nouveau `TextFieldValue`.

Ainsi, remplacer `G` ne touche pas `Gm`, `G7`, `Gmaj7`, `G/B`, `G#` ni un autre symbole
contenant cette lettre. Si la cible, par exemple `G7`, existe déjà, la palette recalculée
depuis le ChordPro ne présente qu'un bouton `G7`. Annuler l'un des dialogues ne modifie
pas le contenu. Ce geste n'est branché que dans l'éditeur du Prompteur ; le composant
Audio + Paroles conserve son comportement propre.

### Texte/couleur et Alignement

Les deux panneaux sont mutuellement exclusifs et ne restaurent pas l'en-tête
pendant la saisie. L'action choisie ferme son panneau et redonne le focus au texte.
Alignement propose **Gauche** et **Centré** pour tout le texte ; ce n'est pas une
commande appliquée à une sélection.

Gras/italique encadrent la sélection simple ; une nouvelle pression sur le même
style dans une plage simple retire les marqueurs et remappe curseur/sélection.
Un style différent dans une plage déjà formatée ou une sélection coupant un tag
est laissé inchangé. Sans sélection, un texte indicatif traduit est inséré et sélectionné.
Le séparateur s'insère avant une sélection sans la supprimer.

La couleur utilise des pastilles visibles avec libellés. Elle encadre la sélection
ligne par ligne ; sans sélection, elle insère un texte indicatif sélectionné.
À l'intérieur d'une plage déjà colorée, elle recolore **toute la plage**, même si
seule une partie était sélectionnée, en conservant la position visible du curseur.
Une sélection chevauchant des plages simples est étendue à leurs limites avant
recoloration, pour éviter une imbrication de couleurs.
**Aucune / défaut** retire les balises des plages concernées et remappe la sélection,
y compris inversée ; sur du texte non coloré, cette action ne fait rien.

L'éditeur reste un éditeur de **source**, pas un éditeur visuel sans balises.
La création d'un format ne conserve pas toujours une sélection identique : selon
l'action, elle sélectionne le texte indicatif, le bloc formaté ou place le curseur
après l'insertion. Les gardes de gras/italique/couleur ne constituent pas un moteur
général de réparation : titres, sections et commentaires ne corrigent pas un
balisage complexe déjà présent. Les cas ambigus restent à éditer manuellement.

## 5. Téléphone et tablette : disposition actuelle

La distinction locale est `LocalConfiguration.current.screenWidthDp < 600` pour
le téléphone, et `>= 600` pour la tablette. C'est un seuil de largeur de configuration,
pas un modèle matériel ni une mesure du clavier. L'orientation ou le multifenêtrage
peut donc changer la branche utilisée. Le dialogue tient compte du clavier par
`imePadding`, indépendamment des états de visibilité.

Dans les deux branches, le focus du contenu masque l'en-tête (titre du dialogue
et champ titre), **sans masquer les boutons
d'accords**. La zone de texte occupe la hauteur restante. Perdre ce focus permet
à l'en-tête de revenir ; fermer seulement le clavier n'implique pas sa restauration.
Il n'y a plus de libellé visuel « Accords » ni de barre de validation séparée en bas.

### Téléphone

- Rangée d'accords horizontale dédiée, puis barre compacte Texte/couleur,
  Alignement, ✓, × ; les deux premières icônes restent turquoise.
- Le focus est suivi avec `hasFocus`.
- Reprendre la saisie, recevoir le focus ou retoucher le champ déjà actif replie
  les panneaux secondaires seulement. Accords et barre d'actions restent visibles.
- Ouvrir un panneau pendant l'édition n'affiche que ce panneau ; le titre reste masqué.

### Tablette — dernière compaction

Une seule ligne principale, dans cet ordre :

**Texte/couleur → Alignement → accords → ✓ → ×**

- Texte/couleur (`TextFormat`) et Alignement (`FormatAlignLeft`) sont **blancs**,
  distincts des boutons d'accords turquoise.
- Les icônes ont des zones de 48 dp ; les accords conservent une taille minimale
  de 48 × 48 dp, leur ordre et l'espacement de 6 dp.
- La zone accords utilise **la largeur restante** avec `Modifier.weight(1f)`.
  Son `Row.horizontalScroll(rememberScrollState())` permet d'atteindre tous les
  accords ; les icônes et ✓/× restent fixes.
- Aucun retour à la ligne dans la barre : ce n'est pas un `FlowRow`.
- La fusion retire une ligne verticale par rapport aux deux rangées précédentes,
  sans colonne latérale ni palette verticale.
- Les panneaux demandés s'ouvrent sous cette barre ; ils peuvent donc reprendre de
  la hauteur tant qu'ils sont ouverts.
- Le focus reste suivi avec `isFocused`. Le repli au retoucher/saisie propre au
  téléphone n'est pas appliqué à la tablette ; fermeture par le bouton du panneau,
  l'autre panneau ou le choix d'une action.

La compaction tablette de `dcd57d70` n'a changé ni la disposition ni les conditions
ni les callbacks de la branche téléphone. Les libellés d'accessibilité des actions
restent traduits ; les conditions d'activation de ✓ viennent du parcours appelant.

## 6. Persistance et compatibilité

Le texte source, accords et marqueurs compris, est sauvegardé sous l'identité
stable du texte. Il n'y a ni liste d'accords extraite sauvegardée en parallèle,
ni conversion en LRC, HTML ou texte de rendu.

**Nuance par rapport au cahier initial « sans transformation » :** les chemins
actuels de création, mise à jour et import du catalogue appliquent `trim()` au
titre et au contenu. Les blancs et retours à la ligne aux extrémités ne sont donc
pas garantis ; le contenu intérieur et ses balises restent conservés.
Le parser et les modèles de rendu sont des représentations dérivées en mémoire.

L'alignement et la transposition des accords sont mémorisés localement par identité
dans `TextPrompterDisplaySettingsStore` : `text:<textSongId>` pour le catalogue,
`audio-lyrics:<songId>` pour Audio Lyrics. Ils ne sont pas encodés dans la source
ChordPro et ne sont pas transportés avec le texte. Le champ de compatibilité
`syncPitchCompensation` n'implique aucun couplage au pitch audio dans le Prompteur.
La palette affichée est dérivée du texte enregistré ; elle suit donc le contenu sans
persistance parallèle. Le store historique de palette n'est pas la source de la palette
automatique actuelle et reste distinct de `ChordPaletteStore` des accords synchronisés.

Les formats physiques, la compatibilité historique et les limites de transport sont
détaillés dans [SMP_PERSISTENCE_SPEC.md](SMP_PERSISTENCE_SPEC.md#36-textes-défilants-autonomes--implémentation-actuelle).
Comme la palette automatique est reconstruite depuis le texte, elle suit le contenu
transporté. Les couleurs intégrées au texte suivent également ce texte.
Les morceaux audio existants et leurs fichiers de paroles/accords ne sont pas migrés.

## 7. Fonctionnel aujourd'hui / limites / suite

### Fonctionnel aujourd'hui

- Parsing indépendant des accords courants, tolérance des crochets non musicaux.
- Rendu accords au-dessus des paroles, wrapping par mots, hauteurs variables.
- Titres, sections, commentaire, gras, italique, séparateur et couleurs sur paroles/accords.
- Saisie manuelle, copier-coller de source et palette automatique dédupliquée.
- Insertion par appui court et remplacement exact global confirmé par appui long.
- Navigation tactile/clavier par portions de 65 %, chevauchement 35 % et animation 300 ms.
- Correction manuelle pendant Play avec reprise automatique depuis la nouvelle position.
- Crayon du Prompteur et éditeur commun avec Bibliothèque.
- Repli de l'en-tête au focus, accords accessibles et panneaux indépendants.
- Barre tablette unique horizontale, téléphone conservant sa disposition propre.
- Transposition des accords persistée localement, sans réécriture de la source.
- Import assisté `**accord**` et lignes/blocs, soumis à Convertir ou Ignorer.
- Sauvegarde/rechargement du catalogue avec identité conservée.

### Partiellement fonctionnel / limites actuelles

- ChordPro limité aux accords : le format complet et ses directives ne sont pas pris en charge.
- Sélection et mise en forme fiables pour les cas simples décrits, sans normalisation
  générale des imbrications, ni retrait automatique d'un titre/section existant.
- Wrapping par mots, sans solution spéciale pour un mot ou groupe d'accords trop large.
- Une ligne qui devient `---` après extraction d'accords est rendue comme un séparateur :
  les accords de cette ligne restent dans le modèle préparé mais ne sont pas affichés.
- Rechargement en place, sans ancrage sémantique de lecture après changement de hauteur.
- Alignement conservé localement ; palette reconstruite depuis la source.
- Compatibilité du texte intérieur ; espaces de début/fin retirés à l'enregistrement.
- Présence de tests JVM ne valant pas validation de tous les claviers, orientations,
  longues chansons et conditions réelles de scène.

### Non encore implémenté / prévu plus tard

Intentions du cahier initial et pistes ergonomiques non livrées,
**sans engagement de livraison ni nouvelle priorité** :

- import/export dédié de fichiers `.cho`, `.pro`, `.chopro` (le collage de texte
  existe déjà, mais ce n'est pas un import de fichier) ;
- capo et directives ChordPro avancées ;
- éventuelle synchronisation individuelle de chaque accord dans Audio Lyrics ;
- option afficher/masquer les accords et taille indépendante paroles/accords ;
- zoom utilisateur dédié, suppression d'accord assistée et édition musicale complexe ;
- extension du rendu ChordPro au Deuxième écran et aux autres parcours de texte ;
- éventuelle palette verticale tablette : **non créée** par la compaction horizontale.

Les priorités restent définies par [BACKLOG.md](BACKLOG.md). Audio Lyrics est déjà
intégré ; ses propres fonctions livrées et limites sont décrites dans
[CHORDPRO_AUDIO_LYRICS_SPEC.md](CHORDPRO_AUDIO_LYRICS_SPEC.md).

## 8. Validation et repères historiques

Tests JVM existants à consulter, sans les confondre avec des captures Compose :

- `ChordProParserTest`, `PrompterRichTextParserTest`, `PrompterTextPreparationTest` :
  reconnaissance, textes littéraux, remappage, styles et couleurs ;
- `ChordProPrompterLayoutTest` : modèle des mots/ancres, chemins texte simple/enrichi,
  alignement et résolution des couleurs ;
- `ChordProTextEditingTest`, `PrompterMarkupPaletteTest`, `PrompterEditingErgonomicsTest` :
  insertion, sélection, remplacement, retrait et cas ambigus ;
- `PrompterColorPersistenceTest` : sauvegarde dans un dossier temporaire, vidage du
  cache, relecture du catalogue et vérification des couleurs paroles/accords ;
- `TextPrompterChordPaletteStoreTest`, `TextPrompterDisplaySettingsStoreTest`,
  `ScrollingTextEditorVisibilityTest` : stores et règles de visibilité ;
- `ChordTranspositionTest` et `DisplayedChordTranspositionTest` : transposition des accords commune
  et rendu ;
- `ChordProImportNormalizerTest`, `ChordLineImportNormalizerTest` et
  `ScrollingTextChordProImportTest` : import explicite, lignes/blocs, priorité,
  remappage et protection des sources obsolètes.

Cet audit documentaire lit ces preuves sans modifier ni relancer les tests.
Les validations utilisateur rapportées portent sur les patchs d'édition et de
compaction ; elles ne clôturent pas tous les critères initiaux de validation bêta.
Pour une future validation complète, garder les scénarios du cahier initial :
chanson entière pendant plusieurs minutes, texte sans accords, accords adjacents/en
fin de ligne, changement d'orientation/échelle, scroll lent/rapide, pause/reprise,
clavier et sélection, sauvegarde puis réouverture sur téléphone et tablette.

Repères Git : `8f9eb559` (crayon partagé), `dbb4576e` (palette/couleur),
`702d8a4a` (édition simple des formats), `aae27f3f`, `fd8b5c80`, `dd56cc49`
(repli téléphone puis accords maintenus), `64656d41` (barre téléphone),
`f2737c88` (barre compacte tablette), `4208a670` (libellé Accords retiré),
`dcd57d70` (ligne tablette fusionnée). Les anciens états de repli ne décrivent
plus l'interface courante.

Le cahier initial séquençait audit → parser → rendu → validation du scroll → palette
→ ergonomie → bêta. Parser, rendu, palette et plusieurs finitions sont présents ;
ce séquencement ne prouve ni la clôture de toute validation terrain, ni une release.

Voir aussi le [manuel des textes défilants](user-guide/12-textes-defilants.md),
la [fiche Bibliothèque](Features/FEATURE_LIBRARY.md) et la
[fiche Player](Features/FEATURE_PLAYER.md). La reconnaissance est multilingue par
conception ; les éventuels filtres linguistiques restent séparés du parser musical,
conformément au [contrat transversal](Features/FEATURE_CHORDPRO.md#16-évolution-multilingue).
