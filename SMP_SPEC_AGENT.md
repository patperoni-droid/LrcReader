CRITICAL — SMP IMPLEMENTATION SPEC

This document defines how SMP_RULES.md must be applied in code.

It is intended for coding agents (Codex).

If there is any conflict:
👉 SMP_RULES.md takes precedence.

This file complements SMP_RULES.md.
If there is any conflict, SMP_RULES.md takes precedence.

MISSION

Tu es le gardien du format .smp (Stage Music Player).

Ton rôle est de garantir que :
- le format .smp reste cohérent et évolutif
- toutes les données exportées soient complètes
- la structure reste simple, portable et fiable

---

PRINCIPE FONDAMENTAL

1 morceau = 1 unité logique (SongUnit)

Le fichier .smp est un FORMAT DE TRANSPORT,
pas la source de vérité en runtime.

---

ROLE DU .SMP

Le .smp sert à :
- importer un morceau complet
- exporter un morceau complet
- transférer entre appareils

Le runtime NE TRAVAILLE PAS dans le zip.

---

MODELE RUNTIME (IMPORTANT)

Le runtime travaille sur un modèle interne normalisé :

- chaque morceau est une SongUnit
- chaque variante Arrangement est également une SongUnit distincte
- toutes les données sont locales et prêtes à être utilisées
- aucune lecture depuis archive en live

Identité :
- `songId` = identité absolue d'une SongUnit parent ou variante
- `sourceSongId` = identité immuable du parent audio d'une variante
- nom de fichier, URI, chemin et titre = jamais des identités
- une variante possède son propre dossier runtime mais ne possède ni ne duplique l'audio parent

Principe :
IMPORT → NORMALISATION → UTILISATION → EXPORT

---

FORMAT SMP v2

Un fichier .smp est une archive contenant toujours :

- `config.json`

Selon les données disponibles, il peut également contenir :

- `audio.mp3`, `audio.wav`, `audio.flac`, `audio.m4a`, `audio.aac` ou `audio.ogg`
- `lyrics.lrc`
- `chords.lrc`
- `timeline.json`
- `midi_cues.json`
- `waveform.json`
- `grid.json`
- `annotations.json`
- `dmx_cues.json`
- `prompteur.txt` ou `prompteur.json`
- `arrangement.json`
- `arrangement_variants.json`

`meta.json` est une métadonnée runtime interne. Le contrat de transport actuel utilise `config.json`.

Arrangement transport rule:
- `arrangement.json` stores the editable Arrangement project of the source SongUnit when present
- `arrangement_variants.json` stores lightweight virtual variants belonging to that source
- a virtual variant must never be exported alone without its source audio
- the parent audio is stored once and the variants are normalized into separate runtime SongUnits after import
- every variant entry carries its own `id`, title, Arrangement structure, and optional assets
- current optional variant assets include lyrics, chords, and lyrics line colors
- `selectedVariantId`, when present, identifies the single variant intentionally shared by the user

---

MODELE PARENT / VARIANTE (CRITICAL)

Parent :
- possède son propre `songId`
- possède l'audio source
- peut posséder son propre `arrangement.json`
- transporte ses variantes dans `arrangement_variants.json`

Variante :
- possède un `songId` distinct
- possède un `sourceSongId` qui correspond exactement au `songId` du parent
- possède son propre `config.json` et son propre `arrangement.json`
- peut posséder ses propres `lyrics.lrc`, `chords.lrc` et couleurs de lignes
- résout l'audio depuis le parent uniquement au moment de préparer le Playback

Invariants :
- `variant.songId != variant.sourceSongId`
- un `songId` de variante ne peut apparaître qu'une fois dans un manifeste
- `arrangement.sourceSongId` doit correspondre au parent du manifeste
- une variante existante ne peut jamais changer automatiquement de parent
- une variante orpheline n'est jamais considérée comme valide

---

REGLES DE STOCKAGE

1. Import :
   - le .smp est décompressé
   - `config.json` et les identités sont validés
   - les données sont normalisées dans le modèle interne
   - le parent est prêt avant la publication de ses variantes

2. Runtime :
   - lecture UNIQUEMENT depuis le stockage interne
   - jamais depuis le zip
   - toutes les données doivent être disponibles localement

3. Export :
   - reconstruire un .smp à partir des données internes
   - ne jamais recopier un ancien zip comme source de vérité
   - inclure l'état édité actuel des assets

---

IMPORT SMP (CRITICAL)

Pipeline commun :

1. Lire l'identité stable depuis `config.json`.
2. Valider l'archive et ses entrées reconnues.
3. Extraire dans un espace temporaire hors du thread principal.
4. Normaliser vers `/files/tracks/{songId}/`.
5. Publier la SongUnit runtime.
6. Restaurer les variantes valides.
7. Rafraîchir l'index Bibliothèque.
8. Persister l'archive durable selon le pipeline existant.

Règles :
- utiliser uniquement `songId` pour décider si une SongUnit existe déjà
- ne jamais comparer le nom de l'archive, le titre, le chemin ou l'URI
- rejeter les manifestes de variantes invalides avant publication
- préserver la compatibilité avec les anciennes archives ne contenant pas de données Arrangement
- ne jamais exécuter ce pipeline sur le thread principal

---

IMPORT CIBLE D'UNE VARIANTE PARTAGEE

Si `selectedVariantId` est présent :

### Parent local déjà présent

- le parent local est la référence
- ne pas réimporter, remplacer ou modifier le parent
- ne restaurer que l'entrée dont `id == selectedVariantId`
- conserver le `songId` de la variante
- mettre à jour la variante existante seulement si son `sourceSongId` correspond
- refuser explicitement si ce même `songId` est rattaché à un autre parent

### Parent local absent

- importer et normaliser la SongUnit parent complète
- restaurer ensuite uniquement la variante sélectionnée
- si la restauration de la variante échoue, tenter de supprimer le parent nouvellement installé
- signaler explicitement tout échec de rollback

Une archive sans `selectedVariantId` conserve le pipeline complet historique.

---

RESTAURATION SELECTIVE DES VARIANTES

Règle :

> Une archive de variante ne modifie que les données qu'elle transporte.

Application :
- Structure présente → restaurer la Structure
- paroles présentes → restaurer `lyrics.lrc`
- accords présents → restaurer `chords.lrc`
- couleurs présentes → restaurer les couleurs
- asset absent → conserver l'asset local existant
- fichier futur absent de l'archive → ne pas le supprimer silencieusement

La préparation se fait dans un dossier temporaire.

Avant remplacement :
- préserver le dossier publié existant
- publier la nouvelle variante seulement lorsque tous ses fichiers sont prêts
- en cas d'échec, restaurer la version précédemment publiée

Cette règle sélective ne remplace pas les modes explicites Conserver / Remplacer d'une restauration complète de bibliothèque.

---

PAROLES ET ACCORDS PROPRES AUX VARIANTES

- les paroles d'une variante sont stockées dans son propre `lyrics.lrc`
- les accords d'une variante sont stockés dans son propre `chords.lrc`
- lecture et édition utilisent le `songId` de la variante
- le parent ne doit jamais être modifié par l'édition d'une variante
- l'export lit ces fichiers depuis le dossier runtime de la variante
- le transport les place dans les assets de l'entrée correspondante dans `arrangement_variants.json`
- l'import les restaure dans le dossier de la variante
- une ancienne archive sans ces assets reste valide et ne supprime pas les assets locaux

Le même contrat doit être utilisé pour tout futur asset propre à une variante.

---

PARTAGE D'UNE VARIANTE

Le partage réutilise l'export SMP existant.

Pipeline :

1. Recevoir la SongUnit variante sélectionnée.
2. Résoudre son parent par `sourceSongId`.
3. Vérifier que le parent est une vraie SongUnit source et possède son audio.
4. Exporter `config.json` et l'audio du parent une seule fois.
5. Exporter uniquement la variante demandée dans `arrangement_variants.json`.
6. Écrire son `songId` dans `selectedVariantId`.

Interdit :
- créer un `.smp` audio-less autonome pour la variante
- exporter les variantes sœurs lors d'un partage ciblé
- modifier le parent ou la variante pendant l'export
- utiliser le titre ou le nom du fichier pour retrouver le parent

Le partage normal du parent continue à transporter toutes ses variantes valides.

---

SAUVEGARDE ET RESTAURATION COMPLETE

Sauvegarde :
- reconstruire les `.smp` depuis le runtime normalisé
- exporter l'audio parent une seule fois
- transporter les variantes avec leur parent
- exporter `state.json` pour l'état applicatif et les playlists

Ordre de restauration obligatoire :

1. parents
2. variantes
3. rafraîchissement de l'index Bibliothèque
4. remapping des références `songId`
5. playlists et état applicatif

La restauration des playlists doit préserver :
- ordre
- groupes internes
- couleurs
- occurrences répétées
- références distinctes vers parents et variantes

Le Playback ne doit jamais servir à réparer un titre ou une référence après restauration.

---

FILESYSTEM RULE (CRITICAL)

Le filesystem doit refléter uniquement la réalité.

- aucun dossier fictif
- aucune création automatique inutile
- aucun fallback legacy visible

Exemples interdits :
- création automatique de "Midi"
- création automatique de "Videos"
- création automatique de "DJ"
- création automatique de "Config" sans write réel

Principe :
Ce qui n’existe pas → n’apparaît pas

---

TIMELINE = SOURCE DE VERITE

Toutes les données temporelles doivent être basées sur le temps :

- MIDI cues → timeMs
- DMX cues → timeMs
- Lyrics → time

Interdictions :
- dépendre de lineIndex
- dépendre de l’état UI

Principe :
Temps → décision → action

---

SEPARATION INTENT / EXECUTION

Toujours séparer :

1. Intent (données)
   - MIDI cues
   - DMX cues
   - annotations

2. Runtime
   - lecture timeline

3. Output
   - MIDI
   - DMX

Aucune dépendance directe entre ces couches.

---

EVOLUTIVITE

Toute nouvelle feature :
- doit être stockée dans le modèle interne
- doit être exportable dans le .smp

Si nécessaire :
→ créer un nouveau fichier dans le conteneur

---

COMPATIBILITE

- ne jamais casser les données existantes
- maintenir compatibilité legacy en lecture uniquement
- accepter les archives sans `arrangement.json`
- accepter les archives sans `arrangement_variants.json`
- accepter les manifestes sans `selectedVariantId`
- accepter les variantes sans paroles ou accords transportés
- ne jamais inventer une donnée absente d'une ancienne archive

IMPORTANT :
Le legacy ne doit plus être une source active d’écriture.

---

COMPORTEMENT ATTENDU

Quand tu écris du code :

1. Vérifie :
   - est-ce dans le SongUnit ?
   - est-ce exportable dans le .smp ?

2. Si une donnée n’existe pas :
   → proposer un stockage interne + export

3. Toujours séparer :
   - logique interne
   - format d’export

4. Pour une variante, vérifier :
   - le `songId` est-il conservé ?
   - le `sourceSongId` correspond-il au parent et reste-t-il immuable ?
   - l'audio parent est-il stocké une seule fois ?
   - les assets absents sont-ils préservés ?

5. Pour une restauration complète, vérifier :
   - parents et variantes sont-ils visibles avant les playlists ?
   - les groupes et références de playlists sont-ils conservés ?
   - aucun Playback n'est-il requis pour réparer l'affichage ?

---

VALIDATIONS DE REFERENCE

Les scénarios suivants constituent le comportement attendu déjà validé :

- parent existant + variante partagée → parent inchangé, variante restaurée
- parent absent + variante partagée → parent recréé, variante restaurée
- variante existante avec même identité → mise à jour sans doublon
- même `songId` de variante avec autre parent → refus explicite
- archive sans paroles ou accords de variante → données locales conservées
- sauvegarde/restauration complète → SongUnits, variantes, playlists et groupes restaurés
- parent et variante restent deux références de playlist distinctes
- lecture d'une variante retrouve son parent sans réparer ni modifier la playlist
- comportement commun sur téléphone et tablette

---

OBJECTIF FINAL

Le .smp doit permettre de :

- recréer entièrement un SongUnit
- sur un autre appareil
- sans perte de données

- garantir un comportement identique en live
- préserver les identités parent / variante
- restaurer les données transportées sans supprimer silencieusement les données optionnelles absentes

---

PRINCIPE GLOBAL

Le .smp transporte la vérité  
Le runtime exécute la vérité

FINAL IMPLEMENTATION RULE

At runtime:
- NEVER depend on .smp archive structure
- ALWAYS depend on normalized SongUnit
- ALWAYS use `songId` as identity
- NEVER silently change a variant `sourceSongId`

Any code directly reading from .smp during playback must be rejected.

FIN
