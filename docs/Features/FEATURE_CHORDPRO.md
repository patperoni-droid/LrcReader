# FEATURE_CHORDPRO

## 1. Statut et objectif produit

Ce document est le contrat transversal ChordPro de MusiMio. Il définit les règles
communes au parser musical, au rendu, à la transposition des accords, à l'import assisté et aux
différentes surfaces qui utilisent des accords ChordPro.

Les documents suivants restent des annexes spécialisées :

- `CHORDPRO_PROMPTER_SPEC.md` décrit les détails du Prompteur, de son éditeur, de
  son rendu et de ses dispositions téléphone/tablette ;
- `CHORDPRO_AUDIO_LYRICS_SPEC.md` décrit les détails de Lyrics, Sync, Grid, de la
  synchronisation audio et des contraintes propres au Player.

Une fois validé humainement, ce document devient la référence lorsqu'une règle
**commune** ChordPro contredit une annexe. Les règles spécialisées de layout, de
synchronisation ou d'interaction restent dans leur annexe et ne sont pas recopiées
ici.

L'objectif produit est de permettre à un musicien de conserver, afficher, importer
et transposer des paroles accompagnées d'accords avec un comportement stable,
prévisible et compatible avec les données existantes.

## 2. Périmètre et terminologie

Dans MusiMio :

- un **symbole d'accord** est un token musical accepté intégralement par le parser
  canonique ;
- une **source ChordPro** est le texte enregistré contenant des accords entre
  crochets, par exemple `Je [Am]chante` ;
- un **ancrage** associe un accord reconnu à une position dans le texte de paroles ;
- le **Prompteur autonome** affiche un texte défilant sans timeline audio ;
- **Audio Lyrics** affiche des paroles synchronisées avec un morceau ;
- **Grid** est une vue d'accords dérivée de Lyrics, avec compatibilité des anciennes
  données ;
- un **normaliseur d'import** analyse une photographie de texte et décrit des
  remplacements possibles sans écrire dans le stockage ;
- la **Transpo accords**, ou **transposition des accords**, modifie leur affichage,
  jamais la source ChordPro ;
- le **pitch audio** modifie la hauteur entendue du morceau ;
- **Sync Pitch** couple les actions live Transpo accords au pitch audio par
  variation, sans garantir une égalité permanente.

MusiMio prend en charge un sous-ensemble ChordPro centré sur les accords. Il ne doit
pas être présenté comme un interpréteur complet du standard ChordPro.

## 3. Invariants et sources de vérité

Les invariants suivants sont absolus :

- il existe un seul parser musical canonique ;
- aucun écran, importeur ou profil linguistique ne crée un second parser d'accords ;
- un symbole est validé dans son intégralité, sans reconnaissance partielle dans un
  mot ;
- la reconnaissance musicale est indépendante de la langue naturelle ;
- la reconnaissance d'un accord et son positionnement dans les paroles sont deux
  étapes distinctes ;
- les moteurs core restent purs, déterministes et testables ;
- les normaliseurs core ne dépendent ni de Compose, ni d'un `TextFieldValue`, ni du
  presse-papiers Android, ni d'un repository ou d'un stockage ;
- la transposition des accords réutilise le moteur commun et reste séparée de la source ;
- la source ChordPro n'est jamais réécrite par une transposition des accords ;
- toute conversion issue d'un texte externe nécessite une confirmation utilisateur ;
- toute nouvelle chaîne UI existe en français, anglais et espagnol ;
- la compatibilité ascendante est obligatoire ;
- stabilité > fonctionnalité.

Le texte enregistré constitue la source de vérité du contenu. Le parser, les ancres,
les palettes, les lignes de rendu et les propositions d'import sont des données
dérivées. Côté audio, ExoPlayer et les timestamps restent les sources de vérité du
temps ; aucun état ChordPro ou état UI ne peut les remplacer.

## 4. Architecture commune

Le flux commun est :

```text
Source textuelle
    ↓
Parser / validateur musical canonique
    ↓
Accords structurés et positions source
    ↓
Préparation propre à la surface
    ↓
Rendu Prompteur ou Audio Lyrics / Grid
```

L'import de texte externe ajoute une étape de proposition avant de produire une
source ChordPro :

```text
Photographie du brouillon
    ↓
Analyse pure et remplacements proposés
    ↓
Confirmation Convertir / Ignorer
    ↓
Application au même brouillon uniquement
```

Les composants canoniques actuels sont notamment :

- `core/ChordProParser.kt` pour le parsing et la validation ;
- `core/ChordTransposition.kt` pour la transposition des accords ;
- `core/ChordProImportNormalizer.kt` pour `**accord**` ;
- `core/ChordLineImportNormalizer.kt` pour les lignes et blocs d'accords ;
- `ui/ScrollingTextChordProImport.kt` pour la sélection de la proposition et le
  remappage de l'état d'édition.

## 5. Parser et validateur canoniques

`parseChordPro()` analyse une source contenant des tags `[accord]` et produit des
lignes de paroles accompagnées d'ancres. `parseChordSymbol()` est le validateur
canonique réutilisé par les importeurs.

Un token valide doit correspondre entièrement à la grammaire existante :

- fondamentale majuscule de `A` à `G` ;
- altération éventuelle `#` ou `b` ;
- suffixe accepté par la grammaire actuelle ;
- basse éventuelle après `/`, de `A` à `G`, avec altération éventuelle ;
- aucun espace à l'intérieur du symbole ;
- aucune partie restante non reconnue.

Exemples vérifiés valides :

- `A`
- `C`
- `Am`
- `Bm7`
- `F#`
- `Bb`
- `Csus4`
- `Am7/D`
- `Bm7/E`

Exemples invalides comme tokens complets :

- `C'est`
- `D'accord`
- `Bonjour`

La validation est syntaxique. Elle ne garantit pas la pertinence harmonique de toute
combinaison de suffixes acceptée.

## 6. Sous-ensemble ChordPro supporté

Le format commun supporté aujourd'hui comprend les accords entre crochets, leurs
positions dans les paroles et les formes de symboles reconnues par le parser canonique.
Les accords peuvent apparaître au milieu d'une ligne ou constituer une ligne entière.
Les séparateurs de lignes LF, CRLF et CR sont pris en charge par le parser et les
normaliseurs concernés.

Les annotations comme `[Refrain]` restent du texte lorsque leur contenu n'est pas un
accord valide. Les directives complètes telles que `{title:}` ou
`{start_of_chorus}` ne sont pas interprétées par le moteur commun actuel.

Le formatage riche propre à MusiMio et ses interactions détaillées sont décrits dans
la spécification du Prompteur. Ils ne doivent pas élargir implicitement la grammaire
musicale.

## 7. Modèle de rendu

Le parser retire les tags d'accords reconnus du texte de paroles dérivé et conserve
pour chacun :

- le symbole structuré ;
- sa plage dans la source ;
- son offset dans le texte de paroles visible.

Les surfaces de rendu utilisent ces ancres pour placer les accords au-dessus ou à
côté des paroles selon leur propre présentation. Le rendu ne devient jamais une
seconde source éditable et ne doit pas être persisté comme substitut du texte source.

Le Prompteur et Audio Lyrics partagent la reconnaissance et les fonctions de
préparation utiles, tout en conservant des modèles d'affichage et des contraintes
de navigation distincts.

## 8. Transposition des accords commune

`ChordTransposition.kt` et `transposeChord()` constituent le moteur partagé. La
fondamentale et la basse éventuelle sont transposées ; le suffixe reconnu est conservé.
Les altérations explicites conservent leur orientation bémol ou dièse, tandis que les
notes naturelles utilisent une représentation dièse déterministe lorsqu'une
altération devient nécessaire.

La plage d'affichage actuelle est de `-11` à `+11` demi-tons. Une transposition des accords
équivalente à zéro restitue exactement l'écriture source. La valeur est un réglage
séparé et persistable par identité stable ; elle ne modifie ni les tags ChordPro, ni
les paroles, ni les timestamps, ni les octets audio.

Le Prompteur autonome et Audio Lyrics utilisent ce même moteur. Les détails de leurs
contrôles et, côté audio, la relation avec `Sync Pitch`, restent dans leurs annexes
spécialisées.

## 9. Prompteur autonome

Le Prompteur autonome :

- affiche ensemble paroles et accords reconnus ;
- utilise le parser et le moteur de transposition des accords communs ;
- conserve un seul texte source, sans modèle musical parallèle ;
- dispose d'une transposition des accords de `-11..+11`, mémorisée localement
  par texte, sans modification du pitch audio ;
- utilise Play/Pause pour le défilement, sans lecture musicale synchronisée ;
- propose l'import assisté dans l'éditeur partagé des Textes défilants ;
- reste indépendant d'une timeline ou d'un fichier audio.

La création depuis la Bibliothèque et la création depuis une playlist écrivent dans
le même catalogue de textes, puis suivent leurs règles propres de référencement.
Les détails de rendu, de scrolling, de palette, de sélection et de layout sont dans
`CHORDPRO_PROMPTER_SPEC.md`.

## 10. Audio Lyrics / Grid synchronisés

Audio Lyrics :

- réutilise le parser et la transposition des accords communs ;
- conserve Lyrics comme source éditable du contenu musical textuel ;
- conserve Sync comme source des timings ;
- dérive Grid des lignes Lyrics contenant des accords avec leurs timings ;
- conserve le fallback Grid historique tel quel, sans lui appliquer la
  transposition des accords de la Grid dérivée ;
- impose Lyrics sur téléphone sans boutons live Lyrics/Grid ; expose le
  sélecteur tablette selon l'édition ;
- ne propose plus d'onglet Accords séparé dans l'éditeur courant ;
- ne réécrit pas Lyrics lorsque l'affichage est transposé ;
- ne modifie pas la timeline audio à partir du rendu ChordPro.

Une ligne synchronisée reste un seul item temporel, qu'elle contienne des paroles,
des accords ou les deux. Les détails de Grid, du fallback, de `Sync Pitch` et des
dispositions téléphone/tablette sont dans `CHORDPRO_AUDIO_LYRICS_SPEC.md`.

L'import assisté de lignes externes décrit dans ce document est actuellement intégré
à l'éditeur des Textes défilants. Il ne doit pas être attribué automatiquement à
l'éditeur Audio Lyrics.

## 11. Import de balisage `**accord**`

`ChordProImportNormalizer` recherche uniquement des paires simples `**contenu**`
sur une même ligne. Le contenu entier est soumis à `parseChordSymbol()`.

Exemples :

```text
**Em**     → [Em]
**Am/F#**  → [Am/F#]
**Bonjour** → inchangé
```

Le texte déjà écrit sous la forme `[Em]` reste inchangé. Les marqueurs incomplets,
échappés, imbriqués ou ambigus sont ignorés de façon conservatrice. Les espaces,
tabulations, accents, apostrophes, emoji, ponctuation et séparateurs de lignes hors
des plages remplacées sont conservés.

L'analyse retourne une description des remplacements. Elle ne modifie pas le texte
et ne déclenche aucune sauvegarde.

## 12. Import de lignes et blocs d'accords

`ChordLineImportNormalizer` analyse le texte token par token. Les tokens sont séparés
par les espaces et tabulations de la source ; chaque token candidat doit être validé
entièrement par `parseChordSymbol()`.

Règles actuelles :

- une ligne peut alimenter un bloc dès qu'elle contient au moins deux accords valides ;
- les tokens ordinaires non musicaux sont ignorés ;
- un token inconnu ressemblant à une section ou à une balise protège la ligne et
  empêche sa conversion ;
- un bloc peut contenir une, deux ou trois lignes d'accords consécutives ;
- quatre lignes d'accords consécutives ou plus sont refusées ;
- le bloc doit être immédiatement suivi d'une ligne de paroles exploitable ;
- une ligne vide, une section, une autre ligne d'accords ou une ligne contenant déjà
  du ChordPro ne peut pas servir de ligne de paroles ;
- toutes les projections doivent réussir, sinon le bloc entier est refusé ;
- les lignes supérieures du bloc disparaissent uniquement lorsque la conversion est
  confirmée ; les tokens inconnus ne sont ni convertis ni déplacés.

Exemple réel :

```text
C Suis D Bm7
N'oublie pas...
```

Les accords reconnus sont `C`, `D` et `Bm7`. `Suis` est ignoré. Après confirmation,
les accords sont projetés dans la ligne de paroles selon leurs colonnes et la ligne
supérieure est remplacée par la ligne ChordPro résultante.

Protections génériques :

- `A partir de quand ?` ne fournit qu'un accord, `A` : la ligne est insuffisante ;
- `C'est à dire` ne contient pas de token accord complet ;
- `D'accord` ne contient pas de token accord complet.

## 13. Reconnaissance, colonnes et projection

L'import de lignes respecte quatre étapes séparées :

1. reconnaître les tokens entièrement valides ;
2. conserver leurs colonnes calculées sur chaque ligne source originale ;
3. projeter chaque accord vers un offset sûr de la ligne de paroles ;
4. produire le remplacement ChordPro seulement après confirmation.

Un token non musical ordinaire ne doit pas invalider par principe un accord voisin.
Il ne doit toutefois jamais être interprété comme un accord ou déplacé dans les
paroles.

Les tabulations utilisent des taquets logiques déterministes de quatre colonnes.
Lorsqu'un accord tombe dans un espace de la ligne de paroles, la projection peut
s'aligner sur une limite voisine selon les bornes conservatrices du moteur. Un léger
dépassement de fin de ligne est accepté dans la limite prévue par le normaliseur.
Toute autre projection impossible refuse le bloc entier.

Les insertions sont appliquées de droite à gauche. Lorsque plusieurs accords arrivent
au même offset, leur ordre suit l'ordre des lignes du bloc puis l'ordre des tokens
dans ces lignes.

## 14. Confirmation UI et sécurité des snapshots

L'éditeur affiche au maximum une proposition à la fois. La priorité actuelle est :

1. balisage explicite `**accord**` ;
2. lignes ou blocs d'accords.

Les actions sont :

- **Convertir**, qui applique la proposition au brouillon courant ;
- **Ignorer**, qui masque la proposition pour cette photographie exacte du texte.

La conversion n'est jamais silencieuse. Ignorer ne modifie pas le texte. Une nouvelle
proposition redevient possible lorsque le texte change ou lorsqu'une nouvelle session
d'édition commence.

Une analyse conserve sa source exacte. Si le `TextFieldValue.text` courant ne lui est
plus identique, elle est considérée comme obsolète et son application retourne un
refus. Lors d'une application valide, le curseur, la sélection et la composition IME
sont remappés vers le nouveau texte.

La conversion ne modifie que la copie de travail de l'éditeur. Le repository ou le
stockage n'est modifié que par le parcours normal de validation de l'éditeur.

## 15. Faux positifs et cas refusés

Les accords d'une seule lettre peuvent aussi être des mots ou des éléments de prose.
La règle actuelle exigeant au moins deux accords par ligne réduit ce risque sans
l'éliminer. Une phrase telle que `De A à D` peut encore fournir deux candidats
musicaux dans un contexte naturel.

La confirmation utilisateur reste donc une protection essentielle. Aucune règle
linguistique avancée n'est implémentée aujourd'hui.

Sont notamment refusés ou protégés :

- une ligne ne fournissant qu'un accord ;
- un bloc de quatre lignes d'accords ou plus ;
- une section ou un token ressemblant à une balise ;
- une ligne cible vide ou déjà ChordPro ;
- une projection qui dépasse les garde-fous ;
- une proposition calculée sur une ancienne version du texte ;
- un marqueur `**...**` incomplet ou ambigu.

## 16. Évolution multilingue

**CHORDPRO EST MULTILINGUE PAR CONCEPTION.**

L'architecture cible est :

```text
Parser musical canonique
        ↓
Candidats accords
        ↓
Filtre d'ambiguïté linguistique optionnel
        ↓
Positionnement dans les paroles
        ↓
Sortie ChordPro
```

Règles absolues :

- aucune exception française, anglaise, espagnole ou propre à une autre langue ne
  doit entrer dans le parser musical canonique ;
- un profil linguistique ne modifie jamais la grammaire musicale ;
- il peut seulement réduire les faux positifs issus du texte naturel ;
- en l'absence de profil, le comportement générique actuel est conservé ;
- toute règle linguistique est pure, déterministe et testable ;
- les premières langues prioritaires sont le français, l'anglais et l'espagnol ;
- les autres langues sont ajoutées à partir de cas et de corpus réels ;
- aucune abstraction linguistique ne doit être ajoutée au code avant qu'un besoin
  réel et testé la justifie.

Exemples de risques à étudier, et non de règles déjà codées :

- FR : `A partir de quand ?` ;
- EN : `A beautiful day`, `Am I wrong?` ;
- ES : ambiguïtés à identifier avec un corpus réel avant implémentation.

Les heuristiques actuelles constituent une V1 générique et évolutive. Elles ne sont
pas une définition définitive de la détection en texte naturel.

## 17. Persistance et propriété des données

La source ChordPro et les réglages d'affichage ont des propriétaires distincts :

- les tags `[accord]` restent dans le contenu textuel possédé par le catalogue ou la
  SongUnit concernée ;
- la transposition des accords est une préférence locale séparée, associée à une
  identité stable par les stores existants, sans transport avec la source ;
- Sync Pitch est une préférence globale de l'appareil, distincte du pitch audio
  mémorisé parmi les réglages du morceau ;
- le Prompteur autonome et Audio Lyrics utilisent des espaces de clés distincts ;
- les palettes et modèles de rendu sont dérivés et ne deviennent pas des sources
  persistantes parallèles.

Dans un contexte SongUnit, l'identité persistante repose sur `songId`, jamais sur un
nom de fichier ou une URI. Pour un texte autonome, l'identité stable du catalogue est
utilisée. Les normaliseurs d'import n'accèdent à aucun de ces stockages.

Les formats physiques, la sauvegarde, le transport et les compatibilités historiques
sont définis dans `SMP_PERSISTENCE_SPEC.md`.

## 18. Contraintes live et performance

Les règles live restent prioritaires :

- aucun parsing lourd dans la boucle de suivi audio ;
- aucune I/O sur le thread principal ;
- aucune lecture de zip au runtime live ;
- les modèles ChordPro nécessaires sont préparés avant leur utilisation sensible ;
- les recalculs dépendent du contenu ou de la transposition des accords, pas de chaque tick audio ;
- ExoPlayer fournit le temps absolu côté audio ;
- le rendu ChordPro, une position visuelle ou un index de ligne ne devient jamais une
  source temporelle.

Toute évolution touchant Audio Lyrics doit également respecter
`02_LIVE_STABILITY_RULES.md` et les gardes de transition audio existants.

## 19. Tests et matrice de validation

Les familles de tests nécessaires sont :

### Parser

- accords simples, enrichis et slash chords ;
- altérations et suffixes supportés ;
- token complet invalide ;
- crochets non musicaux et séparateurs LF, CRLF et CR.

### Import `**accord**`

- conversions sûres ;
- mots et balisages ambigus refusés ;
- préservation exacte du texte extérieur ;
- idempotence.

### Import lignes et blocs

- minimum de deux accords ;
- tokens inconnus ordinaires ignorés ;
- blocs d'une, deux et trois lignes ;
- quatre lignes refusées ;
- sections et ChordPro existant protégés ;
- projection dangereuse refusée ;
- LF, CRLF et CR préservés ;
- ordre des accords arrivant au même offset ;
- idempotence.

### Intégration UI

- Convertir et Ignorer ;
- priorité entre les suggestions ;
- source obsolète refusée ;
- remappage du curseur, de la sélection et de la composition IME ;
- nouvelle session et changement de texte.

### Transposition des accords et rendu

- notes naturelles, dièses, bémols et basses slash ;
- bornes `-11..+11` ;
- source non réécrite ;
- persistance par identité ;
- rendu Prompteur, Lyrics et Grid.

Une matrice de corpus FR/EN/ES devra être ajoutée progressivement lorsque des filtres
linguistiques seront réellement implémentés.

## 20. Compatibilité ascendante et limites

Les contenus sans accords conservent leur comportement historique. Les annotations
non reconnues restent visibles. Les anciennes données de grille audio restent
disponibles selon le fallback documenté. Aucune migration destructive ne doit être
déduite de l'ajout d'une nouvelle capacité ChordPro.

Limites actuelles :

- heuristiques génériques uniquement ;
- faux positifs linguistiques encore possibles ;
- maximum de trois lignes d'accords dans un bloc ;
- projection dépendante des colonnes présentes dans le texte source ;
- perte possible d'alignement lorsqu'un copier-coller HTML ne fournit pas les espaces
  réels ;
- largeur des tabulations approximée par des taquets logiques ;
- colonnes logiques différentes de l'affichage d'une police proportionnelle ;
- sous-ensemble ChordPro limité, sans directives complètes ;
- validation sur corpus multilingue à enrichir progressivement.

Une limite connue doit conduire à un refus conservateur ou à une confirmation, jamais
à une réécriture implicite des données utilisateur.

## 21. Évolutions futures

Pistes possibles, sans engagement de livraison :

- profils linguistiques français, anglais et espagnol ;
- ajout progressif d'autres langues ;
- constitution d'un corpus de faux positifs ;
- meilleure détection contextuelle sans modification du parser musical ;
- extension du sous-ensemble ChordPro si un besoin réel est démontré ;
- enrichissement contrôlé des usages Audio Lyrics ;
- intégrations timeline supplémentaires compatibles avec la séparation Intent,
  Runtime et Output ;
- amélioration de la projection lorsque les sources externes conservent des
  informations d'alignement fiables.

Toute évolution commence par un diagnostic, conserve la compatibilité ascendante et
ajoute des tests ciblés avant intégration dans une surface live.

## 22. Liens vers les spécifications spécialisées

- [Règles globales du projet](../00_PROJECT_RULES.md)
- [Architecture SMP](../01_SMP_ARCHITECTURE.md)
- [Règles de stabilité live](../02_LIVE_STABILITY_RULES.md)
- [Spécification ChordPro du Prompteur](../CHORDPRO_PROMPTER_SPEC.md)
- [Spécification ChordPro Audio Lyrics](../CHORDPRO_AUDIO_LYRICS_SPEC.md)
- [Feature Bibliothèque](FEATURE_LIBRARY.md)
- [Feature Player](FEATURE_PLAYER.md)
- [Spécification de persistance](../SMP_PERSISTENCE_SPEC.md)
- [Stratégie et commandes de tests](../TESTS.md)
