# Sound Pads — Architecture proposée et audit de réutilisation

## Jalon du 5 octobre 2026 — état historique

Pour la référence `stable` publiée le 7 octobre et les changements Lecteur/LEVELS, voir [PROJECT_STATUS.md](../../PROJECT_STATUS.md). Le SHA et l’absence de push ci-dessous décrivent uniquement ce jalon antérieur.

La référence locale **`stable` pointe sur `ed5c8506`**, identique à `feature/soundpads` lors de la bascule. Elle contient la MusiMio Labo récente, Sound Pads V1 et les correctifs locaux consolidés. L’ancienne stable de mars (`636d18ec`) est conservée sous `backup/legacy-stable-march` et le tag `legacy-stable-march-2026` ; aucun merge des historiques n’a été créé. Aucun push distant ni publication Google Play n’est revendiqué.

**Version de référence : `0.5.0-beta-labo`, code 15.** Le Créateur a confirmé la validation physique de Labo release : accès Pads, tactile, lecture, Bus PADS, fader et Stop. L’évaluation acoustique reste celle de ses appareils et sorties, sans promesse de latence universelle.

| Variante | Sound Pads | Version |
| --- | --- | --- |
| `laboDebug` | présent | `0.5.0-beta-labo`, 15 |
| `laboRelease` | présent | `0.5.0-beta-labo`, 15 |
| `concertDebug` | présent | `0.5.0-beta-concert`, 15 |
| `concertRelease` | absent ; Bus trois sources | `0.5.0-beta-concert`, 15 |

La garde fonctionnelle est `BuildConfig.DEBUG || BuildConfig.FLAVOR == "labo"` ; les traces tactiles restent strictement debug. Icône 2 × 2 dédiée, navigation principale conservée, grille 6/12 par défaut et banque extensible à IDs stables ; ajout/édition/suppression, copie interne persistante, nom/couleur/volume/IN/OUT. Media3 joue un seul pad à la fois, en coexistence avec les sources principales ; leurs règles d’exclusion entre elles restent inchangées.

Le Bus PADS et le Mixage général utilisent la même préférence `pads_volume_prefs` / `pads_volume_ui`. Le gain appliqué est `volumeIndividuel × niveauUI³`, selon la courbe du Bus. Stop coupe uniquement Pads. Banque : `filesDir/soundpads/bank.json`, audio sous `filesDir/soundpads/audio/`. Les paramètres IN/OUT sont des positions de lecture en millisecondes, pas des fondus.

Pas de transposition, timeline, polyphonie, waveform complète, MIDI, export/import de banque ni moteur PCM. **La banque Pads n’est pas incluse dans les sauvegardes générales MusiMio** ; effacement des données/désinstallation peut la supprimer. Les installations debug et release ont des banques privées séparées, sans transfert automatique.

Les quatre builds ont été compilés depuis la nouvelle stable et **346 tests JVM ciblés par variante ont réussi (1 384 au total)**. Ce résultat ne signifie pas que la suite globale est verte : l’échec préexistant des couleurs Arrangement reste hors périmètre. Aucun nouvel essai instrumenté sur un appareil utilisateur n’est revendiqué.

[Utilisation et import](../user-guide/03-navigation-telephone-et-tablette.md#sound-pads) · [Bus et volumes](../user-guide/14-mixage-du-titre-et-bus-principal.md) · [Android Studio et identités](SOUNDPADS_DEVELOPPEMENT_ANDROID_STUDIO.md).

## Historique de conception et du prototype

**Les sections ci-dessous conservent l’étude et les constats datés du 3 octobre.** Leurs mentions « non implémenté », « validation physique en attente », propositions de moteur lourd et budgets initiaux ne décrivent pas la V1 actuelle. Les décisions actuelles ci-dessus et le rapport Patchs 3/4 priment. V2/V3 restent des perspectives.

> **État courant au 3 octobre 2026 : étape 1 prototype Media3 compilée et vérifiée sur émulateur.**
> Pads autorisés au-dessus du Lecteur, du DJ **et** du Fond sonore, décision utilisateur
> plus récente que les variantes d’étude ci-dessous. Leurs règles d’exclusion entre eux
> sont conservées. Interface V1 complète et validation physique encore en attente.
> [Résultats, limites et protocole appareils](SOUNDPADS_PROTOTYPE_TESTS.md).


Statut : **analyse terminée pour le code inspecté ; proposition à valider avant implémentation**.
Audit du 3 octobre 2026, dépôt `LrcReader_EXO_V2`, HEAD `51da14df` (« Update MusiMio documentation and user guide »). Arbre de travail non propre : notamment MainActivity, FillerSoundManager, PlaybackCoordinator et commandes audio modifiés localement. Cette analyse porte sur cet arbre local, pas uniquement sur HEAD et pas sur l’AAB publié. Aucun code Sound Pads, build ou essai appareil réalisé.

[Cahier des charges](SOUNDPADS_CAHIER_DES_CHARGES.md) · [Roadmap](SOUNDPADS_ROADMAP.md).

> **Complément courant : section 12 — V1 simple Media3 sans export ni PCM.**
> Les stratégies pool, préparation PCM, waveform, sauvegarde et voix multiples
> décrites plus haut restent des options futures. Pads + Player est confirmé.

## 1. Contraintes du projet

Application Android à module unique `:app`, UI Compose, MainActivity orchestratrice. Références lues : [règles projet](../00_PROJECT_RULES.md), [architecture SMP](../01_SMP_ARCHITECTURE.md), [stabilité live](../02_LIVE_STABILITY_RULES.md), [sécurité release](../03_RELEASE_SAFETY_RULES.md), [SMP_RULES](../../SMP_RULES.md), [SMP_SPEC_AGENT](../../SMP_SPEC_AGENT.md), [persistance](../SMP_PERSISTENCE_SPEC.md), [Player](../Features/FEATURE_PLAYER.md), [Waveform](../Features/FEATURE_WAVEFORM.md), [Timeline](../Features/FEATURE_TIMELINE.md), [coordination audio](../Components/COMPONENT_AUDIO_COORDINATION.md), [Screen Shell](../Components/COMPONENT_SCREEN_SHELL.md), [statut](../../PROJECT_STATUS.md) et [backlog](../BACKLOG.md).

Principes conservés : assets locaux normalisés, pas de zip runtime, pas d’I/O ni de préparation lourde au déclenchement, IDs stables, séparation données/exécution/sortie, compatibilité des sauvegardes anciennes. Préserver le Player comme horloge des événements attachés aux morceaux. Ne pas transformer un pad global en SongUnit fictive pour recycler la Bibliothèque.

## 2. Composants existants : usages et limites vérifiés

Chemins Kotlin relatifs à `app/src/main/java/com/patrick/lrcreader/`.

| Fichier / module | Ce qui existe réellement | Réutilisation Sound Pads |
| --- | --- | --- |
| `core/audio/AudioEngine.kt` | ExoPlayer Media3, pipelines gain et SoundTouch, gestion du Player principal et transitions | Bibliothèque Media3 et principes audio ; ne pas appeler son singleton pour jouer un pad, car il est propriétaire du morceau et du gain |
| `core/audio/SamplerEngine.kt` | PCM16 stéréo, AudioTrack MODE_STREAM, segments séquentiels, crossfade/anti-click | Référence pour frames PCM et anti-click ; pas de réutilisation directe comme moteur rapide : `play()` appelle `stop()`, crée un Thread puis un AudioTrack ; `stop()` peut joindre le thread |
| `core/audio/SampleSegment.kt` | Modèle de segments PCM Arrangement | Référence technique ; padId ne doit pas devenir un index de segment |
| `core/audio/ArrangementWavRenderer.kt` | MediaExtractor/MediaCodec, `decodeSourceToWav(audioPath, outputFile)` public, sortie WAV préparée hors thread UI | Brique de décodage réutilisable avec destination explicite ; `render()` décode la source entière et utilise cacheDir pour son travail, donc vérifier temps/disque avant usage pour pads |
| `core/audio/WavFileWriter.kt` | Écriture WAV du pipeline existant | Réutilisation si préparation PCM/WAV retenue, sans créer une deuxième implémentation WAV |
| `core/audio/ArrangementSourceWavCache.kt` | Cache de source décodée pour Arrangement | Ne doit jamais devenir propriétaire de la copie durable d’un pad |
| `core/audio/SoundTouchBridge.kt`, `SoundTouchAudioProcessor.kt`, `app/src/main/cpp/soundtouch_bridge.cpp` | Bridge JNI avec handles, contrôles d’availability, tempo/pitch | Candidat V2 avec handle indépendant ou rendu préparé ; aucune garantie ±12/polyphonie par simple présence du bridge |
| `core/FillerSoundManager.kt`, `core/DjEngine.kt` | Moteurs secondaires existants ; Fond sonore utilise MediaPlayer | Exemples de cycle de vie et coordination ; pas de fork de leur logique playlist/ambiance |
| `core/PlaybackCoordinator.kt` | Source None/Player/Dj/Filler et callbacks exclusifs ; requestStartX coupe les autres | Point d’intégration obligatoire, extension ciblée pour une couche pads ; ne pas ajouter aveuglément Pads à l’enum exclusif |
| `MainActivity.kt` : resolveTrimConfig et trimStopJob | Trim SMP puis replis EditSoundPrefs ; IN via seek, OUT en mode seek-stop par interrogation toutes les 40 ms | Réutiliser le contrat millisecondes ; ne pas recycler la boucle OUT pour un sampler précis ni appeler onEnded du morceau |
| `core/EditSoundPrefs.kt`, `smp/SmpConfig.kt` | Réglages du son / playback d’une SongUnit | Consulter conventions ; les pads ont un propriétaire et un store distincts, pas de clés URI legacy |
| `core/waveform/WaveformExtractor.kt` | Aperçu échantillonné et décodage détaillé MediaCodec | Réutiliser depuis la copie interne, hors lecture live |
| `core/waveform/WaveformPeaksCache.kt` | Cache dérivé, sous `context.cacheDir` | Acceptable pour peaks reconstructibles uniquement ; clé tenant compte de l’asset/version, pas seulement du padId |
| `ui/WaveformPreviewScreen.kt` | Écran couplé SongUnit, sauvegarde trim morceau et Playback principal ; WaveformCanvas privé | Ne pas réutiliser l’écran entier. Éditeur pad simple en V1 ; éventuelle extraction minimale du canvas purement visuel, sans refonte de l’écran existant |
| `core/ImportAudioManager.kt` | Import SAF vers dossier SPL_Music, filtrage mp3/wav/flac/m4a/aac/ogg | Conventions MIME et sélection ; destination externe et helpers privés : pas un import interne pads prêt à appeler |
| `core/InternalStoragePaths.kt`, `smp/SmpSecureImportPipeline.kt` | Chemins internes et normalisation SMP vers filesDir/tracks | Réutiliser conventions internes et publication transactionnelle, pas importer un pad comme .smp |
| `core/config/TrackSettingsAtomicIo.kt`, `smp/SmpTimelineStore.kt` | Modèles de write tmp/backup/rollback et verrouillage JSON | Modèles à suivre ; API existante liée aux réglages/timeline, pas utilitaire générique directement réutilisable |
| `ui/BottomTabs.kt`, `MainActivity.kt` | Sealed BottomTab, barre directe, conversion tabKey, restauration session, enum panneau droit Split | Ajouter une destination et toutes les branches exhaustives/restauration ; garder les icônes existantes accessibles |
| `ui/adaptive/SmpAdaptive.kt` | tablette si screenWidthDp >=600, tokens taille/padding et orientation | Réutiliser détection actuelle ; calcul de grille avec largeur du panneau, jamais largeur tablette entière en Split |
| `ui/theme/Theme.kt`, `Color.kt`, `Type.kt`, `ui/BottomTabs.kt` | Material3, couleurs dynamiques ; barre noire, icônes blanches et indication audio ambre | Réutiliser styles/tokens visibles, éviter un nouveau thème. Distinguer source principale et pads actifs si superposition |
| `ui/PlaybackControl.kt`, `PlayerControls.kt` | Contrôles officiels du Playback principal | Garder leur mission si présents dans le shell ; Stop pads et volume pads ont leurs propres commandes |
| `smp/TimelineMarker.kt`, `smp/SmpTimelineStore.kt` | timeMs/label/kind/durationMs ; types TEXT/MIDI/NOTE/DMX, pas de padId ni payload audio | Extension V3 explicite ; un marqueur texte ne constitue pas un déclencheur audio |
| `core/MidiCueDispatcher.kt` | Chemin SMP par fenêtre temporelle, suivi position et retour arrière ; chemin legacy par ligne séparé | Exemple d’exécution timeMs, pas réutilisation du dispatch MIDI pour audio ; définir traitement du seek plutôt que recopier les hypothèses |
| `core/backup/BackupBundlePlanner.kt`, `BackupBundleExporter.kt`, `BackupBundleImporter.kt`, `BackupBundleManifest.kt`, `core/BackupManager.kt` | Planification/export/restauration d’état et SongUnits | Ajouter une section de banque et assets explicitement ; aucun transport pads déjà présent |

## 3. Modèle et propriété proposés

Banque globale, indépendante de la sélection et de la lecture d’un morceau. Les données éditoriales des pads et leurs copies audio appartiennent à **l’État global de bibliothèque** selon le modèle de persistance existant ; layout, page sélectionnée et zoom restent des préférences d’appareil. V3 : événements possédés par la Famille SongUnit, références vers la banque globale. Documenter cette extension dans la spécification de persistance lors de l’implémentation, sans créer un quatrième périmètre normatif.

Schéma conceptuel proposé :

```text
SoundPadBank(schemaVersion, pads: List<SoundPad>, assets: List<SoundPadAsset>)
SoundPad(padId, name, assetId?, volume=1, inMs=0, outMs?, pitchSemitones=0, order)
SoundPadAsset(assetId, relativePath, durationMs, contentVersion, originalDisplayName)
```

ID UUID recommandé. L’ID 17 du cahier des charges est un exemple d’identité, jamais une position de grille. Nouvelle importation audio indépendante : nouvel assetId. Remplacement conserve padId. Les emplacements vides n’exigent pas de fichier audio. Lecture V1 exige pitchSemitones ==0 ; ne pas effacer silencieusement une future valeur inconnue au chargement d’une banque plus récente.

Stockage proposé (création uniquement à la première écriture réelle) :

```text
filesDir/soundpads/bank.json
filesDir/soundpads/assets/{assetId}/original.{ext}
filesDir/soundpads/prepared/{preparationKey}/...   # si utile au backend retenu
cacheDir/soundpads-waveform/...                  # dérivés reconstruisibles seulement
```

original + bank constituent la vérité durable. prepared est un dérivé versionné ; sa disparition force un état « préparation » avant lecture, jamais la recherche de l’URI d’origine. Pas d’asset redécodé après chaque appui. Clé préparation = version contenu + IN + OUT + format sortie + version pipeline + pitch futur. Ne pas doubler l’audio dans JSON/state.json.

## 4. Import et transactions

1. Recevoir une URI temporaire du sélecteur ; streamer vers un fichier provisoire interne sur Dispatchers.IO, sans charger le fichier entier en RAM.
2. Contrôler taille, espace disponible, durée et décodabilité ; noms externes neutralisés, chemins produits par l’app et non par le document source.
3. Préparer/valider la plage et les dérivés selon backend ; opération annulable, état de progression.
4. Publier un nouveau dossier asset ; écrire bank.json versionné par transaction avec rollback/récupération. Un échec conserve l’ancien pad.
5. Publier l’état prêt seulement après succès. Nettoyer les provisoires non référencés hors live, jamais supprimer automatiquement des assets encore utilisés.

Crash entre publication asset et manifeste : asset orphelin détectable et récupérable/nettoyable plus tard. Crash pendant remplacement : ancien manifeste récupérable. Une erreur de JSON ne doit pas écraser la banque par une banque vide. Suppression utilisateur explicitement confirmée ; vérifier références V3 avant suppression. Limites de ressources exprimées en octets/durée/voix et feedback explicite, jamais en « seulement 12 pads ».

## 5. Contrat moteur et choix de backend

```text
prepare(padSnapshot) -> ReadyHandle ou erreur
trigger(padId, readyRevision) -> VoiceId ou refus
stopVoice(voiceId)
stopAllPads()  # invalide aussi les commandes en attente
release()
states: préparation / prêt / lecture / erreur
```

Moteur détenu au niveau application/session, pas par une cellule Compose. File de commandes bornée, arrêt prioritaire, génération de banque/commandes pour ignorer callbacks périmés. V1 : maxVoices=1 est une politique d’exécution, distincte du nombre de pads. Futur : plusieurs VoiceId indépendants et limite de ressources explicite. Aucun audio focus concurrent détenu par chaque pad : centraliser avec la politique de l’application. Libérer/arrêter proprement en perte de focus, changement de route et destruction de session ; aucune reprise automatique après Stop.

### Options comparées

| Option | Avantage | Limite / décision |
| --- | --- | --- |
| ExoPlayer Media3 indépendant, sources clippées et prépréparées | Formats existants, peu de nouveau décodage, conserve le Player intact | Mesurer restart/IN et ressources ; préparer un lecteur par pad ne passe pas à l’échelle. Pool borné ou stratégie de préparation, pas N lecteurs permanents non bornés |
| SoundPool avec extraits préparés | Déclenchement court et plusieurs streams | Limite Android par son décodé de 1 Mo, troncature possible ; `rate` change vitesse et pitch. Inadapté comme unique solution sans durée imposée par l’utilisateur |
| AudioTrack PCM préparé, sortie persistante et commandes de voix | Contrôle des frames IN/OUT et prédisposition au mixage | Nouveau runtime nécessaire ; normalisation des taux/canaux, mémoire, buffers, anti-click. SamplerEngine actuel ne remplit pas ce contrat |

**Recommandation minimale :** commencer lors du futur développement par une qualification courte du backend Media3 indépendant. Le retenir seulement si le premier appui prêt, les retriggers et Stop respectent la cible sur téléphone/tablette et avec la charge audio retenue. Sinon retenir un backend PCM à sortie persistante, construit autour des briques existantes de décodage/WAV ; ne pas refondre le Player ou l’Arrangement. Architecture d’interface indépendante du backend dès V1. Pas de choix « définitif » de moteur sans cette mesure : aucun moteur du projet n’est déjà démontré conforme à l’exigence pads.

Préparer le clip dans le média ou en PCM ; ne pas transférer le polling OUT 40 ms du morceau aux pads. Pour PCM : début/fin convertis en frames alignées ; conversion mono/stéréo et sample rate explicites, préparation hors live. Décodage source entier existant potentiellement coûteux pour une longue source dont le pad n’utilise qu’une seconde : optimisation par plage ultérieure si mesurée nécessaire, pas cachée dans le devis simple.

## 6. Coordination et niveaux

Conserver l’exclusion actuelle Player/DJ/Filler. Ajouter une activité pads séparée de la source principale, avec porte d’entrée dans PlaybackCoordinator et callbacks Stop pads ; ne pas écraser activeSource avec Pads lorsque Player joue. Pads ne lancent ni autoplay, ni onNaturalEnd du morceau, ni changement de playlist.

Matrice à arrêter avant code : Player + pads autorisé (proposition) ; DJ + pads à décider ; Filler + pads à décider ; démarrage d’un morceau, Pause, Stop principal et perte de focus ont des comportements explicitement définis pour les voix pads. « Arrêter les pads » ne doit jamais lancer le Fond sonore par effet indirect.

Volume par pad séparé ; système STREAM_MUSIC commun. En superposition, risque de saturation : préciser headroom/gain de bus pads et addition des sorties, puis écouter/mesurer. Aucun volume système remonté automatiquement au trigger. La superposition proposée complète le contrat audio actuel et doit être validée avant implémentation.

## 7. V2 sans couplage au Player

SoundTouch est un candidat présent, mais l’AudioEngine actuel possède un état global de pitch. Ne pas l’utiliser pour transposer un pad. Préférer, si coût acceptable, un dérivé préparé à tempo 1 et pitch choisi ; changer de valeur invalide sa préparation. Autre option : traitement par voix indépendante, plus coûteux en CPU/latence. Vérifier disponibilité native/ABI, rendu aux extrêmes ±12, durée, transitoires et mémoire. Aucun engagement de qualité ou de polyphonie pitch sans essais. Pas de commandes V2 dans V1.

## 8. V3 et transport

Événement futur typé : `(eventId, timeMs, action=triggerPad, padId)` ; `123250 ms` pour 02:03.250. Extension de stockage versionnée : le modèle TimelineMarker actuel ne sait pas porter cette référence. Séparer modèle, préparation runtime triée et sortie SoundPadsEngine.

La position officielle du morceau reste l’unique horloge ; pas de timer d’UI ni seconde horloge de pad pour planifier les cues. Fenêtre temporelle, identités d’événements, génération du morceau actif et prévention des doublons. Seek avant : ne pas jouer tous les effets sautés ; seek arrière : réarmer seulement les événements futurs ; pause : aucun nouveau trigger ; reprise/loop : contrat explicite et testé. Position initiale 0 et événements à 0 à traiter spécialement. Les sons déjà lancés sont arrêtés ou conservés selon contrat à décider. Pour Arrangement, préciser temps source versus temps cumulé de Structure avant extension ; pas de projection implicite.

Millisecondes stockées ne signifie pas exactitude audible à la milliseconde. Cadence du dispatch et buffers de sortie doivent être mesurés avant toute promesse de synchronisation V3.

Sauvegarde globale : banque + originaux, restaurés avant résolution des références ; manifeste/version et collisions explicites. Événements V3 transportés dans la Famille ; partage d’un morceau seul : embarquer dépendances pads ou signaler manque/refuser partage incomplet. Préserver IDs ou remapper atomiquement toutes les références lors d’un import conflictuel. Rien de cela n’est implémenté aujourd’hui et aucun nouveau format SMP n’est nécessaire pour la lecture manuelle V1.

## 9. Surface probable de modification

Nouveaux fichiers conceptuels, sous le package actuel (noms à confirmer au diagnostic de développement) :

- `core/soundpads/SoundPadModels.kt`, `SoundPadsStore.kt`, `SoundPadImporter.kt`.
- `core/soundpads/SoundPadsEngine.kt`, `SoundPadsPreparation.kt` ; backend `Media3SoundPadsBackend.kt` ou `PcmSoundPadsBackend.kt` selon qualification.
- `ui/soundpads/SoundPadsScreen.kt`, `SoundPadEditor.kt` ; composant grille local si nécessaire.
- `core/backup/SoundPadsBackupCodec.kt` si le bundle existant ne peut intégrer la banque directement.
- Tests ciblés modèles/store/import/runtime et intégration navigation/coordination.

Modifications ciblées probables : MainActivity, BottomTabs, PlaybackCoordinator, chaînes values/values-en/values-es, branche sauvegarde/restauration et documentation de persistance/coordination. Theme/adaptive : réutilisation, aucune réécriture prévue. WaveformCanvas : extraction facultative minimale seulement si retenue dans l’éditeur. AudioEngine/SamplerEngine : pas de changement prévu pour y greffer des pads.

Futurs fichiers hors V1 : `SoundPadPitchPreparer.kt`, `SoundPadCue.kt`, `SoundPadCueDispatcher.kt` et extension store/éditeur/transport timeline. Ne pas les créer maintenant.

## 10. Incertitudes et limites de l’audit

Aucune mesure de latence, profil mémoire, compatibilité exhaustive codecs ou vérification visuelle Sound Pads. Chiffrage par scénarios, pas devis ferme. Les changements locaux actuels peuvent déplacer les points d’intégration : refaire un diff ciblé avant développement. Attention à la différence entre documentation normative et implémentation : le store timeline stocke aujourd’hui des marqueurs limités et le cache peaks est effectivement temporaire, même si certains textes parlent de cache « persistant ». Pour les pads, seules les données originales et éditoriales sont obligatoirement durables.

Sources Android vérifiées le 3 octobre 2026 : [SoundPool](https://developer.android.com/reference/android/media/SoundPool) pour limite/rate ; [formats Media3](https://developer.android.com/media/media3/exoplayer/supported-formats) pour dépendance aux codecs de la plateforme. Les comparaisons et la recommandation de moteur sont des conclusions de conception de cet audit, pas des validations constructeur.

## 11. Bus sonore : audit complémentaire et intégration V1

Complément demandé le 3 octobre 2026 : source Pads et volume global persistant obligatoires en V1.

### Briques réellement inspectées

| Fichier | Réutilisation / point de vigilance |
| --- | --- |
| `ui/MixerHomePreviewScreen.kt` | Bus effectivement appelé dans MainActivity ; trois `MixerChannelColumn` dans une Row, faders et boutons Stop. Ajouter la quatrième tranche avec ce composant privé dans le même fichier, sans copier un écran de mixer |
| `ui/GlobalMixScreen.kt` | Autre surface de mixage appelée par MainActivity ; `MixFader` privé et conversion cubique. Couvrir ce parcours s’il reste accessible, afin d’éviter une section Pads absente selon la route |
| `core/PlayerBusController.kt`, `core/PlayerVolumePrefs.kt` | Patron prefs + contrôleur + application au moteur ; préférence UI 0..1 rechargée avant application |
| `core/DjBusController.kt` | StateFlow UI et conversion u³ vers moteur ; exemple d’état partagé. Ce contrôleur n’effectue pas lui-même de persistance : ne pas supposer que tous les bus utilisent déjà exactement le même store |
| `core/FillerSoundPrefs.kt`, `FillerSoundManager.kt` | Persistance du niveau réel et application distincte au moteur ; le Bus reconvertit par racine cubique |
| `core/BackupManager.kt` | Export/restauration explicite du volume Fond sonore repéré ; aucun mécanisme universel prouvé pour tous les niveaux, donc pads à intégrer explicitement si transport du réglage retenu |

Les conventions existantes mélangent niveaux UI et réels selon la surface. La V1 Pads doit définir une seule convention et appliquer la courbe une seule fois, sans refactor des faders existants.

### Proposition ciblée

Créer `core/PadsBusController.kt` et `core/PadsVolumePrefs.kt`, à côté des contrôleurs existants. Stocker `pads_volume_ui` dans un fichier SharedPreferences dédié, valeur proposée 1 et borne 0..1. Le contrôleur charge ce niveau dès initialisation de la session, publie un StateFlow unique observé par les deux surfaces de mixage, sauvegarde les changements et transmet `u³` à `SoundPadsEngine.setBusGain()`.

`SoundPadsEngine` conserve le gain du bus séparément des snapshots individuels. Toutes les voix, y compris les futures voix polyphoniques et l’aperçu pad, passent par cette multiplication : `effectiveGain = pad.volume × padsBusGain × éventuelFade`. Aucun pad à volume 0 n’est réamplifié par le bus. Une mise à jour de fader est légère, ne recharge aucun fichier et ne reconstruit aucune source. Le moteur lit le gain courant au trigger et l’applique aussi aux voix actives. Pas de dépendance à une recomposition du Bus pour rendre ce réglage effectif.

En backend PCM : multiplication au stade voix/bus ; en backend Media3 : volume de chaque voix mis à jour par le moteur. Éviter une seconde multiplication dans la cellule UI ou le pad editor. Le volume système est un étage extérieur partagé, pas une préférence Pads.

La banque et les réglages individuels appartiennent à l’État global de bibliothèque ; le niveau de bus est une préférence d’appareil. La sauvegarde complète peut transporter cette préférence dans sa section réglages si la politique produit le demande, avec compatibilité quand la clé manque. Elle ne doit pas modifier la propriété des assets.

Le quatrième fader réduit la largeur disponible : adapter uniquement la disposition des tranches à la largeur utile (quatre colonnes si confortables, disposition 2×2 ou défilement si nécessaire). Le style, le composant de tranche et la mission de PlaybackControl restent les mêmes. Tester le droit d’accès au Bus suivant l’édition : MainActivity conditionne certains parcours à EditionConfig ; décider si Sound Pads et son fader sont disponibles dans toutes les éditions sans changer implicitement le modèle commercial.

Nouveaux fichiers supplémentaires : `PadsBusController.kt`, `PadsVolumePrefs.kt` et tests ciblés de composition/persistance du gain. Modifications supplémentaires : `MixerHomePreviewScreen.kt`, `GlobalMixScreen.kt`, connexions MainActivity et chaînes FR/EN/ES. Aucun nouveau framework de mixage. Un vrai vu-mètre Pads n’est pas demandé : ne pas ajouter de capture audio lourde au devis de base ; un éventuel indicateur doit être honnête sur ce qu’il mesure.


## 12. Complément — architecture minimale de la V1 simple

Le périmètre utilisateur réduit la première livraison : aucun export/sauvegarde/sync, aucune transformation audio, aucune timeline. **Pads pendant le Player obligatoire**, confirmé dans cette analyse complémentaire. Conserver la propriété globale de banque, les IDs et pitchSemitones=0 ; ne pas développer pour autant une infrastructure générique de cues ou de mixage polyphonique.

### Chemin de réalisation retenu pour le chiffrage

- Une seule instance ExoPlayer Media3 propre à la section pads, réutilisée pendant la session ; pas le singleton AudioEngine du morceau.
- Banque JSON versionnée et copie originale en filesDir ; pas de nouvelle base de données, pas de déduplication entre pads en V1.
- Ajouter/importer/remplacer/supprimer via un formulaire Compose et le sélecteur Android ; écriture atomique simple, conservation de l’ancien pad tant que remplacement non validé. Confirmation avant suppression et Stop de la voix concernée.
- IN/OUT dans MediaItem.ClippingConfiguration, édition numérique ; aucun appel au rendu Arrangement WAV ou à WaveformPreviewScreen.
- Au toucher, lancer la plage locale avec le gain actuel ; un autre pad remplace seulement le pad actif. Garder une génération de requête pour Stop/remplacement afin d’ignorer les callbacks périmés.
- Fader via PadsBusController/PadsVolumePrefs, volume moteur = volume individuel × position globale³. Un changement s’applique à la voix courante, sans remplacer sa source.
- Une couche d’activité pads dans la coordination, sans changer activeSource du Player ni appeler son onNaturalEnd/autoplay. Le lecteur principal conserve son timing/gain ; les pads respectent la gestion de focus de la session au lieu de demander un focus exclusif concurrent. Les callbacks actuels doivent être vérifiés pendant la qualification.
- Écran pleine taille téléphone / panneau droit tablette, style et tranche Bus réutilisés, défilement simple si davantage de pads. Aucune refonte du Shell ou des composants principaux.

### Ce qui ne doit pas être promis

Media3 sait clipper et préparer un média, mais préparer le nouvel audio au changement de pad peut être perceptible. Une instance unique ne rend pas tous les sons de la banque prêts simultanément. La V1 simple abandonne donc la promesse d’une banque entière préchargée à latence garantie, en faveur d’une qualification concrète. Elle ne retire pas l’exigence de mesurer et d’obtenir une réactivité acceptable pour l’utilisateur.

Prototype de qualification : tester premier toucher, changement de fichier, retrigger et Stop avec Player en cours, sur téléphone et tablette. Continuer ce même moteur dans la V1 si acceptable. Sinon documenter le résultat ; aucune création implicite d’un backend PCM. Les options de la section 5 restent disponibles pour une décision ultérieure, pas pour le budget courant.

Avec une seule voix, volume et Stop sont simples ; cela ne supprime ni la préparation Media3, ni la coordination avec le Player, ni les courses entre import/appuis/Stop. La polyphonie avait déjà été exclue du devis précédent : elle n’explique pas la différence de budget.

### Surface minimale probable

Conserver seulement modèles/store/import, contrôleur audio pads Media3, écran/formulaire et contrôleur/prefs de bus. Il est possible de regrouper préparation et backend Media3 dans le contrôleur dédié, plutôt que créer immédiatement chaque fichier conceptuel de la section 9. Noms exacts à arrêter lors du diagnostic d’implémentation. Pas de fichier SoundPadsBackupCodec, DSP/pitch, cue ou nouveau runtime PCM en V1. Tests ciblés de données/gain/Stop et validation des parcours restent requis.

Sources techniques : [clipping Media3](https://developer.android.com/media/media3/exoplayer/media-items), [états Player](https://developer.android.com/media/media3/exoplayer/listening-to-player-events), vérifiées le 3 octobre 2026. L’exigence de latence et les budgets sont une estimation d’ingénierie, pas une propriété démontrée par ces API.
