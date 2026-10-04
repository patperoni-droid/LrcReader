# Sound Pads — prototype Media3 et recette appareils

## État actuel — 4 octobre 2026

La V1 est implémentée sur `feature/soundpads` et **validée sur appareil réel par le Créateur**, y compris la correction tactile. Labo est la variante fonctionnelle de référence ; Concert doit rester compilable. Aucun déploiement Google Play ni intégration dans l’ancienne branche `stable` n’est revendiqué.

Accès par l’icône Pads du cockpit téléphone/tablette, navigation principale conservée ; grille 6/12 par défaut, banque extensible à IDs stables ; ajout/édition/suppression, copie audio interne persistante, nom/couleur/volume/IN/OUT. Media3 joue un pad à la fois au-dessus du Lecteur, du DJ ou du Fond sonore. Stop ne coupe que Pads. Le gain global vient du Bus Pads, persiste via `pads_volume_prefs` / `pads_volume_ui` et s’applique au volume individuel selon la courbe cubique du Bus.

La V1 reste accessible **uniquement en debug** à cette révision : les gardes `BuildConfig.DEBUG` sont conservées, et `laboRelease` n’est pas encore activé. Le debug conserve son identifiant séparé ; l’identifiant Labo release ne change pas. Aucune transposition, timeline, polyphonie, waveform complète, MIDI, export/import de banque ou moteur PCM. Les sauvegardes générales de MusiMio ne sont pas annoncées comme incluant la banque Pads.

Référence de livraison et recette : [rapport Patchs 3/4 et correction tactile](SOUNDPADS_PATCH3_INTERFACE.md). Les six commits V1 s’étendent de `273d94c6` à `f8d60efd` ; la base avant Pads est `51da14df`. La consolidation ultérieure du Fond sonore et du Prompteur est distincte de Sound Pads.

## Historique de conception et du prototype

**Les sections ci-dessous conservent l’étude et les constats datés du 3 octobre.** Leurs mentions « non implémenté », « validation physique en attente », propositions de moteur lourd et budgets initiaux ne décrivent pas la V1 actuelle. Les décisions actuelles ci-dessus et le rapport Patchs 3/4 priment. V2/V3 restent des perspectives.

3 octobre 2026. **Étape 1 compilée et testée sur émulateur ; latence acoustique et stabilité sur téléphone/tablette physiques non validées.** Aucun téléphone ou tablette physique connecté lors de cette intervention. Arrêt du chantier au prototype, avant interface V1 complète ou autre moteur.

## Fonctionnement exact

Accès dans un build **debug** : menu ⋮ → « Sound Pads — test audio ». Deux boutons de diagnostic uniquement, pas encore la grille V1 de 6/12 pads. Une instance Media3 dédiée pendant l’ouverture de la fenêtre ; aucun appel aux commandes de lecture/volume du morceau, du DJ ou du Fond sonore. Pas de demande de focus audio concurrente. Les trois sources principales gardent leur exclusivité actuelle entre elles ; le pad peut se superposer à celle qui joue.

Deux MP3 d’ambiance déjà livrés sont copiés en `filesDir/soundpads-prototype/`, hors thread UI : pad 1 joue 0–2000 ms, pad 2 joue 1000–3000 ms. Clipping Media3, sans WAV/PCM préparé ni waveform. À chaque appui, y compris réappui, même instance Media3 mais nouvelle MediaItem et nouvelle préparation : un seul pad audible, précédent remplacé. Ceci mesure la solution minimale sans préchargement complexe.

« Choisir un son » permet de substituer un audio via le sélecteur Android : copie interne durable, contrôle de durée avec MediaMetadataRetriever, IN=0 et OUT=min(2000 ms, durée). Échec d’import : pad précédent conservé. Détection du codec réellement lisible par Media3 au lancement, erreur affichée sans toucher aux autres moteurs. La configuration du fichier choisi reste **en mémoire dans ce prototype** ; fermer la fenêtre recharge les deux sources de diagnostic à la réouverture. Les copies elles-mêmes restent internes. Aucun store de banque définitif ni export/sauvegarde ajouté.

Volume individuel de diagnostic =0,7. Curseur global de diagnostic =0,5 par défaut ; gain effectif =0,7 × global³ (0,0875 au démarrage). Le curseur agit pendant lecture sans changement de source. **Ce curseur n’est pas encore la tranche persistante Pads du Bus sonore** : elle viendra après validation du moteur. Aucun changement des autres faders.

Stop invalide l’intention de lecture, met playWhenReady à false, arrête et vide la MediaItem. Aucun callback de préparation ne relance play(). Fermeture et passage de l’Activity en arrière-plan arrêtent le prototype seulement. Les données existantes ne sont pas effacées. Un événement système « casque débranché » est pris en compte par Media3 ; appels entrants/focus/route restent à qualifier physiquement.

## Fichiers de cette étape

Sous `app/src/main/java/com/patrick/lrcreader/` :

- Nouveaux : `core/soundpads/SoundPad.kt`, `SoundPadsPrototypeEngine.kt`, `SoundPadsPrototypeFiles.kt`, `ui/soundpads/SoundPadsPrototypeDialog.kt`.
- `MainActivity.kt` : état de fenêtre, entrée de menu debug et overlay ; ses changements locaux préexistants sont préservés.
- `core/PlaybackCoordinator.kt` : état activePadId distinct et notifications start/stop pads, sans changement des règles Player/DJ/Filler ni de nextTrack.
- Ressources `values/strings.xml`, `values-en/strings.xml`, `values-es/strings.xml` : chaînes de diagnostic.
- Tests : `src/test/.../core/soundpads/SoundPadTest.kt`, `src/androidTest/.../core/soundpads/SoundPadsPrototypeInstrumentedTest.kt`, `src/androidTest/.../ui/soundpads/SoundPadsPrototypeDialogTest.kt`.
- Documentation : ce rapport, notes d’état dans cahier/architecture/roadmap et fichiers WAV de recette sous `test-audio/`.

Aucune modification de AudioEngine, DjEngine, FillerSoundManager, de SoundTouch ou d’un moteur PCM par cette étape. Ces fichiers peuvent déjà porter des modifications locales antérieures, indépendantes de Sound Pads.

## Validation exécutée

- Compilation Kotlin **labo debug et concert debug réussie** ; APK debug des deux variantes produits.
- Quatre tests JVM SoundPad : réussis sur les deux variantes (liste de 25 pads, bornes de trim, volume global × individuel, rejet des paramètres non supportés).
- Tests audio instrumentés : **4/4 réussis** dans un package isolé sur émulateur API 34 arm64. Vérifications : fin du clip, rafale de 20 appuis suivie de Stop et absence de voix fantôme, lancement après Stop, alternance et réappuis avec les vrais AudioEngine/ExoPlayer, DjEngine/MediaPlayer et FillerSoundManager/MediaPlayer. Source principale, référence/gain et progression du moteur conservés selon les assertions de chaque test.
- Test Compose de l’overlay : **1/1 réussi** après correction de son contexte d’assets/registre d’Activity de test. Le premier essai UI échouait dans l’Activity hébergée par l’APK de test ; aucune modification des lecteurs principaux nécessaire.
- Test Fond sonore : reproduit la porte requestStartFiller de son écran avant startFromUi. Appeler le manager seul ne déclarait pas la source ; le test initial a été corrigé pour reproduire le parcours réel.
- Suite JVM générale : **963 tests par variante, 1 échec** dans PlaybackStructureModelAdapterTest sur une attente de couleurs Arrangement. Test et adaptateur vérifiés inchangés depuis avant le prototype ; aucune correction hors périmètre.
- Diff : absence d’erreur whitespace. Pas de tests instrumentés exécutés sur un package utilisateur.

Un essai de nouvel émulateur temporaire a échoué faute d’espace pour son image système. Isolation finalement effectuée par un package séparé `com.patrick.lrcreader.soundpadsprototype.labo` installé sur emulator-5556 : les deux packages MusiMio existants n’ont pas été remplacés. Les sorties de cette compilation isolée sont sous /tmp ; aucun changement Gradle source nécessaire. Un conflit transitoire de sources BuildConfig entre sorties de compilation a été résolu par une régénération séquentielle de l’APK normal.

## Délais logiciels relevés sur émulateur

10 déclenchements par source, alternance et réappuis. Colonnes : **p50 / p95 / maximum**, méthode nearest-rank ; avec 10 mesures p95=maximum. Ce faible échantillon sert au diagnostic uniquement.

| Source en cours | n | Appel → STATE_READY | Appel → réception du callback audio advancing | Underruns pads signalés |
| --- | ---: | --- | --- | ---: |
| Player | 10 | 26 / 48 / 48 ms | 130 / 220 / 220 ms | 0 |
| Dj | 10 | 27 / 37 / 37 ms | 142 / 166 / 166 ms | 0 |
| Filler | 10 | 24 / 37 / 37 ms | 132 / 155 / 155 ms | 0 |

La mesure commence à l’appel trigger() après onClick, pas au contact initial du doigt. Le callback audio intervient lorsque la position de sortie commence à avancer, avec délai de notification possible ; **il ne mesure ni le premier son entendu ni la latence Bluetooth**. Ces valeurs ne prouvent donc pas le respect ou le rejet de la cible acoustique 50 ms. Aucune transformation du backend n’est justifiée par ces seules mesures d’émulateur.

Les logs identifient requête, pad, source principale, IN/OUT, gain, readyMs, callbackMs et underruns sous SOUND_PADS. La fenêtre affiche le dernier résultat logiciel et le nombre de mesures/underruns.

## APK et conservation

[APK de test isolé](../../backups/soundpads-prototype-20261003/soundpads-prototype-isolated-labo-debug.apk) : package expérimental séparé. Il peut coexister avec MusiMio ; sa bibliothèque/prefs sont séparées, donc préparer un morceau et un son DJ dans cette installation pour tester les parcours. Depuis l’écran d’installation, utiliser « Ouvrir » pour sélectionner la nouvelle installation (icône et nom de l’application restent MusiMio).

[APK avant prototype](../../backups/soundpads-prototype-20261003/before-soundpads-labo-debug.apk), patch préexistant et manifeste baseline conservés dans le même dossier ignoré par Git. Cette baseline correspond à l’arbre local avant Sound Pads et n’est pas présentée comme une version validée live. La version stable officielle et son APK de secours restent intacts.

Aucune installation automatique sur téléphone/tablette utilisateur. Aucun commit/push : validation physique encore attendue et gate JVM général en échec hors scope. Les preuves émulateur sont conservées dans le dossier backups, sans prétendre à une validation concert.

## Protocole précis téléphone et tablette

### Préparation, sur chaque appareil

1. Utiliser l’installation de test isolée ; terminer son setup et importer un morceau de test ainsi qu’un son DJ pour les essais correspondants. Ne pas désinstaller/effacer l’installation habituelle.
2. Commencer avec haut-parleur intégré ou sortie filaire habituelle, Bluetooth désactivé. Noter modèle, Android, version de l’APK et sortie choisie.
3. Ouvrir ⋮ → Sound Pads — test audio. Les sons d’ambiance intégrés vérifient rapidement que le moteur fonctionne, mais leur attaque douce rend la mesure acoustique imprécise.
4. Pour une attaque nette, copier `test-audio/pad-a.wav` et `test-audio/pad-b.wav` sur l’appareil, puis les choisir sur les deux pads. Sons distincts, impulsions à 0/1/2/3 s, duration 4 s ; la plage importée joue 0–2 s. WAV de recette générés hors application, aucun moteur PCM ajouté.
5. Régler un niveau sonore confortable et conserver les mêmes volumes pendant toute une série. La sélection importée revient aux sons par défaut si la fenêtre est fermée : rechoisir les fichiers pour la série suivante.

### Séquences de recette

Réaliser chacune d’abord sans autre source, puis avec le Lecteur, le DJ et le Fond sonore **séparément** en cours de lecture :

- 10 appuis espacés sur pad 1 ; noter le premier démarrage séparément des suivants.
- 10 alternances pad 1 → pad 2, espacées d’environ une seconde ; chercher délai, silence excessif ou double son.
- 10 réappuis rapides sur le même pad, environ 100–200 ms d’écart : le dernier appui doit remplacer la lecture précédente.
- Rafale pad 1/2 puis Stop immédiatement ; aucun son ne doit repartir une seconde plus tard.
- Déclencher puis Stop pendant que le son est audible ; écouter le reliquat de buffer.
- Déplacer le niveau global à 0 puis revenir : le pad est muet à 0, les autres sources ne changent pas de niveau.
- Le morceau/son principal doit continuer sans pause, seek, remise à zéro du gain, changement de titre ou décalage paroles/timeline. Vérifier son état/position après fermeture de la fenêtre. La fermeture arrête seulement le pad.
- Tester les extraits par défaut 0–2 s et 1–3 s : aucune lecture du MP3 complet. Le prototype n’a pas encore de formulaire IN/OUT, ces deux plages sont fixes pour cette qualification.
- Choisir un fichier personnel MP3/M4A/AAC/WAV puis déplacer/supprimer la source : dans la fenêtre ouverte, la copie interne doit rester lisible. Aucune dépendance à l’URI d’origine après copie.

### Mesure acoustique

Pour une première mesure pratique : filmer avec un autre appareil à fréquence élevée (120/240 images/s), **en conservant l’audio synchrone**, le doigt qui relâche le bouton et le début de l’impulsion au haut-parleur. Le bouton déclenche sur onClick, donc prendre le relâchement comme repère, pas la première pose du doigt. La capture externe doit enregistrer le son réellement diffusé ; une capture d’écran interne seule est insuffisante.

Mesurer dans la vidéo le délai entre relâchement et début sonore, 10 fois par scénario et appareil, et retenir médiane/p95/max avec la précision de capture. Si le mode ralenti ne conserve pas une piste audio synchronisée exploitable, utiliser une capture audio/vidéo adaptée ou relever seulement le ressenti ; **ne pas produire un chiffre en ms non mesuré**. Pour une mesure technique précise à 50 ms, utiliser un dispositif de référence contact/appui et capture de sortie sur la même horloge ; la vidéo constitue une approximation, pas une certification.

Éloigner le moins possible le microphone du haut-parleur et conserver sa position entre essais. Éviter tout silence initial dans le fichier de test. Mesurer Bluetooth ensuite dans une série distincte : route et buffers propres, jamais mélangés aux résultats filaires.

### Résultats à relever

| Appareil / Android | Source active | Route | Premier lancement | Médiane / p95 / max acoustiques | Stop / réappui | Glitch autre source | Commentaire |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Téléphone | None / Player / DJ / Fond | intégrée/filaire | à mesurer | à mesurer | à tester | à écouter | |
| Tablette | None / Player / DJ / Fond | intégrée/filaire | à mesurer | à mesurer | à tester | à écouter | |

Rapporter aussi les délais logiciels affichés, sans les confondre avec cette colonne acoustique. Aucun PCM, pool lourd ou SoundTouch avant résultat réel et décision explicite.

## Limites observées et prochaine porte

Le changement de fichier impose une préparation Media3 ; la réactivité physique reste inconnue. Le prototype n’ajoute ni fader définitif au Bus, ni banque persistée, ni grille complète, ni édition finale. Risque de saturation lorsque pad et source principale s’additionnent : régler les niveaux et écouter, les tests d’état ne prouvent pas la qualité du mix. Les appels/focus externes, toutes les routes audio, transitions live prolongées et stabilité de concert restent à vérifier.

Prochaine porte : retour des mesures physiques et appréciation de l’usage. Si Media3 est acceptable, poursuivre la petite V1 ; sinon documenter précisément les cas gênants et proposer des options. Pas de changement de moteur automatique.

Les WAV cités sont des fichiers locaux de recette, non suivis par Git à la demande du Créateur. Ils sont protégés dans les backups ; aucun test automatisé Kotlin ne dépend de leur présence.
