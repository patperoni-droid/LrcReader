# Sound Pads — interface Patch 3 et finalisation V1 Patch 4

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

## Rapports historiques des patchs

Les constats ci-dessous décrivent leurs interventions à la date indiquée. Les mentions de prototype, debug uniquement ou recette/finalisation en attente ne décrivent pas la disponibilité actuelle ; l’état ci-dessus fait référence.

4 octobre 2026. Branche `feature/soundpads`. Les paragraphes de présentation et réglages décrivent le Patch 3 ; la section Patch 4 ci-dessous décrit la finalisation actuelle. Candidat debug en attente de recette physique avant fusion, aucune fusion vers `stable`.

## Accès et navigation actuels

Android Studio : **app → laboDebug → Run**. Dans **MusiMio Sound Pads Test**, toucher l’icône dédiée de quatre carrés arrondis (2 × 2), sur téléphone comme dans la rangée principale du cockpit tablette. L’icône est sélectionnée sur Pads ; la navigation reste accessible. Les anciennes entrées « Sound Pads — test audio » des menus ⋮/⚙️ ont été retirées.

Sound Pads est une destination intégrée. Sur tablette split, la grille occupe le panneau de droite et conserve les raccourcis du cockpit ; sur téléphone, elle conserve la barre principale. Aucun changement d’orientation système. La banque est rechargée depuis le stockage du Patch 2.

Depuis le Patch 4, le lecteur Pads appartient à l’Activity : un pad continue lors d’un changement de destination, ce qui permet de régler le Bus pendant lecture puis de revenir aux Pads. Stop ne touche que Pads. Le passage de l’application en arrière-plan arrête Pads ; la destruction de l’Activity libère son lecteur. Aucun redéclenchement automatique au retour ou au redémarrage.

## Présentation et gestes

- Téléphone : 2 colonnes × 3 lignes de référence, 6 emplacements visibles en disposition normale.
- Tablette paysage : 4 × 3 ; tablette portrait : 3 × 4. Toujours 12 emplacements de référence.
- Banque vide : emplacements « Libre », sans son de démonstration imposé. Toucher un emplacement libre réserve les pads vides précédents et celui choisi en une seule écriture atomique, afin de conserver sa position. + ajoute en fin de banque et ouvre les réglages.
- Les pads déjà enregistrés ne sont ni limités ni tronqués : davantage de pads restent accessibles en défilement vertical. Aucune pagination, banque supplémentaire ou réorganisation n’est ajoutée.
- Appui court : déclenchement, ou réglages si le pad ne possède pas de fichier. Réappui : même comportement moteur qu’avant.
- Appui long : réglages uniquement. `combinedClickable` distingue le geste long du clic court ; aucune voix n’est lancée par ce geste.
- Contour plus lumineux, fond renforcé et LED claire dès préparation/lecture. Les noms, couleur et état sont les seules informations du pad ; aucun chemin, trim ou compteur de latence sur la grille.
- Stop global accessible dans la barre supérieure ; Stop aussi dans l’éditeur pour interrompre un essai sonore.

## Réglages

Téléphone : feuille basse Material3, limitée à la largeur de l’écran, défilable avec clavier. Tablette large : panneau latéral de 320 dp conservant la grille ; tablette portrait/étroite : panneau à droite devant la grille, avec fond assombri protégeant les pads des touches involontaires.

Nom, choisir/remplacer le son, volume individuel, IN/OUT numériques en ms, palette de 8 couleurs, Tester, Enregistrer, Supprimer avec confirmation. OUT vide = fin du fichier. Test utilise le brouillon valide sans l’enregistrer. Fermer sans Enregistrer abandonne les modifications de nom/trim/volume/couleur ; le choix d’un fichier audio est une opération durable immédiate, comme au Patch 2. Un nouveau fichier repart sur 0/durée tout en conservant identité et paramètres précédemment enregistrés.

Nom/volume/couleur en cours de saisie restent dans le brouillon pendant un remplacement audio ; IN/OUT sont réinitialisés aux bornes du nouveau fichier. Enregistrer applique les valeurs du brouillon via le même store. Les métadonnées de durée nécessaires à la validation sont lues sur Dispatchers.IO, jamais dans le rendu de grille.

La suppression d’un pad peut resserrer la liste ordonnée, mais aucun autre UUID, fichier ou réglage n’est changé. Supprimer conserve le nettoyage et la protection des références partagées du Patch 2.

## Conservation et différences voulues avec les références

- Même `files/soundpads/bank.json` schemaVersion=1 et dossier `audio`. Aucune migration, réinitialisation ni conversion audio. `pitchSemitones=0` reste réservé et invisible.
- Store enrichi seulement par un paramètre de couleur dans update et l’ajout groupé de pads vides ; même codec, verrou et transactions AtomicFile. Les appels historiques sans couleur conservent la couleur précédente.
- Aucun changement du moteur Media3 ou des moteurs Lecteur/DJ/Fond sonore.
- Dégradés, relief léger et éclairage discret plutôt qu’un halo permanent marqué : lisibilité, dessin natif simple, pas d’animation continue ou asset bitmap à décoder.
- Typographie Material du projet, contrôles communs MusicControlUi et palette sombre de la console/Fond sonore ; pas de cadre d’iPhone, de nouvel habillage système ni de thème global modifié.
- Icône musicale générique ; aucun sélecteur d’icône ou modèle supplémentaire.
- Panneau ouvert seulement pour l’édition ; la grille reste l’usage principal. Portrait tablette en 3 × 4 pour garder des pads larges.
- IN/OUT sont des positions de découpe, **pas Fade In/Fade Out**. Aucun fondu ajouté à partir des labels des images.
- Pas de fader global sur la grille : le Bus sonore utilise désormais la source commune du Patch 4, sans valeur parallèle.

## Fichiers historiques du Patch 3

| Fichier | Rôle |
| --- | --- |
| `app/src/main/java/com/patrick/lrcreader/ui/soundpads/SoundPadsScreen.kt` | Nouvelle destination, grille adaptative, chargement/gestes/feuille/panneau/navigation |
| `app/src/main/java/com/patrick/lrcreader/ui/soundpads/SoundPadTile.kt` | Pad natif, couleur/état/LED, identité visuelle et gestes |
| `app/src/main/java/com/patrick/lrcreader/ui/soundpads/SoundPadSettings.kt` | Brouillon compact, validation de durée, contrôles et palette |
| `app/src/main/java/com/patrick/lrcreader/ui/soundpads/SoundPadsPrototypeDialog.kt` | Retiré, remplacé par le nouvel écran |
| `app/src/main/java/com/patrick/lrcreader/MainActivity.kt` | Accès existants redirigés, plein écran et retour/entrées protégés |
| `app/src/main/java/com/patrick/lrcreader/core/soundpads/SoundPadsStore.kt` | Couleur et réservation atomique des emplacements vides |
| `app/src/main/java/com/patrick/lrcreader/ui/MusicControlUi.kt` | Helper visuel préexistant, réutilisé sans modification, intégré comme dépendance au commit |
| `app/src/main/res/values/strings.xml`, `values-en/strings.xml`, `values-es/strings.xml` | Chaînes écran, palette, états et accessibilité |
| `app/src/androidTest/java/com/patrick/lrcreader/ui/soundpads/SoundPadsScreenTest.kt` | Remplace le test du dialogue ; dispositions, gestes, édition, couleur, Stop, retour, emplacements et défilement |
| `app/src/androidTest/java/com/patrick/lrcreader/core/soundpads/SoundPadsStoreInstrumentedTest.kt` | Vérifie aussi la conservation de couleur quand l’appel ne la modifie pas |
| Ce rapport et `screenshots/patch3/` | Recette et captures Compose natives |

Les autres modifications locales préexistantes ne font pas partie de ce patch.

## Validation historique du Patch 3

- `assembleLaboDebug` et `assembleConcertDebug` : réussis.
- 5 tests JVM SoundPad par variante : réussis.
- 16 tests instrumentés sur emulator-5556 dans le package isolé `com.patrick.lrcreader.soundpadsprototype.labo.soundpads.debug` : **7 UI + 4 audio + 5 stockage**, réussis.
- UI : vues contrôlées téléphone 400 × 820 dp, tablette portrait 720 × 1050 dp et paysage 1200 × 800 dp. Comptage 6/12, appui long sans audio, essai/Stop depuis panneau, nom/volume/IN/OUT/couleur persistés avec UUID/fichier préservés, suppression annulée/confirmée, retour arrêtant seulement Pads, sélection du 6e emplacement vide, accès aux pads au-delà des 6 initiaux.
- Ces vues sont rendues nativement par Compose avec dimensions/densité contrôlées sur émulateur ; elles ne constituent pas une validation physique téléphone/tablette.
- Tests audio : coexistence avec les vrais moteurs Lecteur/DJ/Fond sonore, Stop et réappuis rapides. Moteur comparé au fichier avant patch : identique.
- Tests stockage : banque extensible, paramètres/IDs, indépendance du fichier source, rollback, références partagées et corruption protégée.
- Captures contrôlées visuellement ; contraste corrigé dans le panneau et les icônes. La locale de l’émulateur de test est anglaise ; ressources FR/EN/ES présentes.
- Pas de tests instrumentés sur le package utilisateur ; aucune installation physique. La branche/tag stable restent inchangés. Aucun push ou contrôle GitHub Actions.

### Captures natives avec des sons de test

- [Téléphone, pad actif](screenshots/patch3/phone-active.png)
- [Téléphone, réglages](screenshots/patch3/phone-settings.png)
- [Tablette paysage](screenshots/patch3/tablet-landscape.png)
- [Tablette paysage, panneau](screenshots/patch3/tablet-landscape-settings.png)
- [Tablette portrait](screenshots/patch3/tablet-portrait.png)
- [Tablette portrait, panneau](screenshots/patch3/tablet-portrait-settings.png)

Les noms Pad 01…12 et sons des captures sont des fixtures de test du package isolé. Ils ne sont pas injectés dans la banque utilisateur. Le retour visuel de toucher peut inclure le ripple Material natif transitoire.

## Recette interface (complétée par la recette Patch 4)

1. Run laboDebug depuis Android Studio. Ouvrir Pads par l’icône dédiée 2 × 2. Les pads du Patch 2 doivent être retrouvés avec leurs mêmes fichiers et paramètres.
2. Vérifier les 6 emplacements téléphone, les 12 tablette en portrait/paysage autorisés par MusiMio, et le retour au contexte précédent. Vérifier confort de lecture et hauteur des pads avec la taille de texte habituelle.
3. Appui court et réappuis rapides ; observer le contour/LED. Stop pendant lecture et juste après une rafale. Refaire pendant un morceau, puis DJ et Fond sonore : ils doivent continuer.
4. Appui long sur un pad au repos : réglages sans aucun son. Long sur un pad en lecture : aucun nouveau déclenchement ; utiliser Stop de l’éditeur si nécessaire.
5. Modifier nom, volume, IN/OUT et couleur ; Enregistrer. Tester OUT vide, extrait 1000–3000 ms sur un fichier assez long, refus d’un OUT inférieur à IN/dépassant la durée. Tester sans enregistrer doit jouer le brouillon valide.
6. + ou emplacement Libre : ajout vide au bon endroit, puis import. Fichier source déplacé/supprimé après import : la copie reste lisible. Remplacement invalide/annulé : ancien fichier préservé.
7. Supprimer, annuler d’abord puis confirmer. Fermer complètement/relancer : paramètres, couleurs et suppression conservés.
8. Banque de plus de 6/12 : défiler pour retrouver tous les pads. Aucun effacement ou import de banque n’est nécessaire pour cette recette.
9. Sur appareil réel, vérifier précision de l’appui long, clavier/scroll de feuille, portrait/paysage tablette, réactivité audio et coexistence. La qualification acoustique reste celle du moteur déjà validé par le Créateur ; aucune nouvelle garantie de latence mesurée n’est inventée.

Le Patch 4 ci-dessous est autorisé séparément après validation des Patchs 1–3 par le Créateur.


## Patch 4 — Bus sonore Pads et finalisation V1

### Source de vérité et calcul

`PadsBusController.uiLevel` est l’unique niveau global modifiable. `PadsVolumePrefs` le restaure avant d’autoriser le fader ou les pads ; ses écritures suivent les préférences existantes du Bus (SharedPreferences, `apply()` asynchrone). Les deux surfaces `MixerHomePreviewScreen` et `GlobalMixScreen` observent le même StateFlow et appellent le même contrôleur. Aucun niveau global parallèle dans le moteur ou la grille.

Stockage local privé : `shared_prefs/pads_volume_prefs.xml`, clé float `pads_volume_ui`, plage 0–1. Le niveau par défaut reste **0,5** pour conserver le niveau du prototype validé ; la proposition initiale d’un défaut à 1 dans l’étude n’a pas été retenue pour éviter une hausse sonore imprévue. Pas de migration de banque : schéma, UUID, fichiers internes et paramètres individuels inchangés. Cette préférence locale n’est pas un export/import de banque et n’est pas synchronisée.

La course du fader utilise la courbe douce déjà présente : `gainGlobalPads = uiLevel³`. Le gain Media3 est `volumeIndividuelPad × gainGlobalPads`, calculé une seule fois. Exemple : pad à 0,4 et fader à 0,5 → 0,05 ; fader à 1 → 0,4 ; fader à 0 → silence. Le volume individuel enregistré reste inchangé. Les valeurs non finies sont ignorées ; les valeurs finies sont bornées.

La modification appelle immédiatement `player.volume` sur le thread principal, sans remplacer le média, préparer à nouveau, rechercher une position ou interrompre la voix. Les bus Lecteur/DJ/Fond ne sont pas modifiés. Media3, une voix, IN/OUT, réappui et changement de pad restent ceux du prototype.

### Finitions ciblées

Quatrième tranche **PADS**, cyan, après Lecteur / Fond sonore / DJ. L’icône reprend les quatre petits pads de la navigation ; son bouton arrête uniquement Pads. Un voyant « Lecture » / « Prêt » indique l’activité ; il ne prétend pas mesurer le niveau audio. Les quatre tranches se partagent la largeur et leur hauteur s’adapte en debug pour garder le fader et Stop accessibles. La disposition release existante reste inchangée.

La grille conserve ses états actifs, noms, emplacements Libre, gestes `combinedClickable` et ses éditeurs du Patch 3. Aucun nouvel écran de diagnostic, waveform, transposition, timeline, polyphonie, MIDI, export, PCM ou SoundTouch.

La disponibilité Sound Pads reste protégée par les gardes **DEBUG** de la branche de test ; la mission ne rend pas implicitement la fonction disponible en production. Une décision explicite sera nécessaire avant une release finale. L’identifiant release et les suffixes déjà en place ne sont pas modifiés par ce patch.

### Fichiers modifiés par Patch 4

Chemins relatifs à la racine du dépôt.

| Fichier | Rôle |
| --- | --- |
| `app/src/main/java/com/patrick/lrcreader/core/PadsVolumePrefs.kt` | Nouveau : préférence locale |
| `app/src/main/java/com/patrick/lrcreader/core/PadsBusController.kt` | Nouveau : source de vérité du Bus Pads |
| `app/src/main/java/com/patrick/lrcreader/core/soundpads/SoundPadsPrototypeEngine.kt` | Application du gain commun, attachement au contrôleur |
| `app/src/main/java/com/patrick/lrcreader/MainActivity.kt` | Instance cockpit, navigation, retrait des entrées de diagnostic |
| `app/src/main/java/com/patrick/lrcreader/ui/soundpads/SoundPadsScreen.kt` | Utilisation de l’instance cockpit, restauration préalable du gain |
| `app/src/main/java/com/patrick/lrcreader/ui/MixerHomePreviewScreen.kt` | Quatrième tranche, activité, Stop, dimensions, accessibilité |
| `app/src/main/java/com/patrick/lrcreader/ui/GlobalMixScreen.kt` | Même source de volume global dans la seconde surface |
| `app/src/main/res/values/strings.xml` | Cinq chaînes Bus FR |
| `app/src/main/res/values-en/strings.xml` | Cinq chaînes Bus EN |
| `app/src/main/res/values-es/strings.xml` | Cinq chaînes Bus ES |
| `app/src/androidTest/java/com/patrick/lrcreader/core/soundpads/SoundPadsPrototypeInstrumentedTest.kt` | Tests audio adaptés au gain persistant |
| `app/src/androidTest/java/com/patrick/lrcreader/ui/soundpads/SoundPadsCockpitNavigationTest.kt` | Continuité de lecture entre Pads et Bus |
| `app/src/androidTest/java/com/patrick/lrcreader/ui/soundpads/SoundPadsBusTest.kt` | Nouveau : deux surfaces, volume Media3, gestes, Stop |
| `app/src/androidTest/java/com/patrick/lrcreader/core/soundpads/SoundPadsBusRestartTest.kt` | Nouveau : deux processus, préférence et banque |
| `docs/soundpads/SOUNDPADS_PATCH3_INTERFACE.md` | Rapport actualisé et recette V1 |
| `docs/soundpads/screenshots/patch4/bus-phone-active.png` | Capture native finale, faders alignés et pad actif |
| `docs/soundpads/screenshots/patch4/bus-globalmix.png` | Capture native finale, seconde surface |

Les autres modifications locales préexistantes sont conservées hors Patch 4.

### Recette physique obligatoire avant fusion

1. Choisir `laboDebug` et lancer depuis Android Studio sur le téléphone, puis la tablette. Ouvrir Pads avec son icône dédiée. Choisir un son assez long ; garder le volume matériel bas au début.
2. Déclencher le pad, ouvrir le **Bus sonore** (Accueil/maison dans le cockpit), repérer la quatrième tranche **PADS**. Faire varier son fader pendant lecture : effet immédiat, zéro silencieux, maximum égal au volume individuel ; le son ne recommence pas. Son bouton de quatre pads arrête seulement Pads. Revenir à la grille : le pad doit garder son état actif jusqu’à Stop ou fin OUT.
3. Refaire pad seul, avec Lecteur, DJ, puis Fond sonore. Régler uniquement Pads ; les autres volumes et lectures doivent rester inchangés. Vérifier changement de pad, rafale de réappuis sur le même pad, Stop immédiat pendant lecture/préparation.
4. Naviguer Pads → Fond sonore → Pads, Pads → DJ → Pads, Pads → Lecteur → Pads. Vérifier navigation visible et sélection correcte. Refaire téléphone, tablette paysage et portrait autorisé par MusiMio. Vérifier noms longs, Libre, fader/Stop accessibles, appui long sans déclenchement nouveau.
5. Mettre le fader Pads à une valeur reconnaissable et un pad à un volume individuel différent. Quitter normalement puis fermer/relancer l’application : les deux valeurs, banque, noms, couleurs, IN/OUT et fichiers doivent être restaurés. Aucun son ne démarre automatiquement.
6. Ajouter un pad, importer, éditer nom/IN/OUT/volume/couleur, supprimer (annuler puis confirmer), remplacer le fichier. Annuler ou faire échouer un remplacement : l’ancien doit rester jouable. Déplacer/supprimer la source après import : le pad utilise toujours sa copie interne.
7. Valider à l’oreille la réactivité en sortie réellement utilisée (haut-parleur, filaire ou interface habituelle). L’émulateur ne mesure pas la latence physique et ne qualifie pas une chaîne Bluetooth. Ne fusionner dans `stable` qu’après cette recette réelle.


### Validation exécutée du Patch 4 (4 octobre 2026)

- Compilations habituelles `:app:assembleLaboDebug` et `:app:assembleConcertDebug` réussies, y compris après la dernière correction d’alignement. APKs normaux conservés sous `app/build/outputs/apk/{labo,concert}/debug/`. Identifiants : `com.patrick.lrcreader.exo.labo.soundpads.debug` et `com.patrick.lrcreader.exo.concert.soundpads.debug` ; nom **MusiMio Sound Pads Test**.
- Suites complètes `:app:testLaboDebugUnitTest` et `:app:testConcertDebugUnitTest` exécutées avant puis après le patch : **964 tests par variante, 963 réussis et 1 échec identique préexistant**, aucun nouvel échec. `PlaybackStructureModelAdapterTest / groups live repetitions like the arrangement window` attend d’anciennes couleurs de l’Arrangement (ligne 29). Ce sujet est hors périmètre Sound Pads. Le gate JVM complet n’est donc pas vert ; les commandes combinées terminent en échec à cause des tests, malgré les deux assemblages réussis.
- Les **5 tests JVM SoundPad de chaque variante** passent dans ces suites complètes.
- **21 tests instrumentés réussis**, sur `emulator-5556`, package isolé `com.patrick.lrcreader.soundpadsprototype.labo.soundpads.debug` : 7 grille/réglages/gestes, 1 barre téléphone, 2 cockpit tablette, 2 Bus, 4 audio, 5 stockage. Les tests utilisent les vrais moteurs Media3/Lecteur/DJ/Fond et vérifient leur état, position/média/volume selon la source ; ils ne mesurent pas la latence acoustique.
- Bus : contrôle du `player.volume` réel à 0 / 0,25 / 0,5 / 0,8 / 1, volume individuel inchangé, phase et ID préservés pendant réglage, geste vertical, source partagée entre les deux surfaces, persistance du niveau, entrée NaN ignorée, Stop et voyant Lecture/Prêt. Navigation native Pads → Bus → Pads pendant lecture vérifiée.
- **2 vérifications supplémentaires réussies dans deux processus distincts** : écriture du gain 0,62, pad avec UUID/fichier interne/volume 0,37/IN 100/OUT 1500 ; source supprimée ; arrêt forcé du seul package isolé ; relance et restauration, puis vérification du gain appliqué. Nettoyage du seul pad fixture et restauration du niveau antérieur.
- Premières tentatives de deux nouveaux tests ajustées : registre d’autorisations du test Compose conservé malgré le contexte substitué ; test de redémarrage attend l’écriture asynchrone avant un arrêt forcé. Les versions finales passent. Aucune modification des moteurs Lecteur/DJ/Fond pour résoudre ces essais.
- Présentations contrôlées visuellement : Bus en cockpit tablette paysage/portrait et en taille téléphone 360 × 720 dp sur émulateur ; capture Compose finale 360 × 700 dp, faders/Stop alignés, et capture de la seconde surface. Ces rendus ne remplacent pas la recette physique ni une mesure audio réelle.
- `git diff --check` passe. Branche `feature/soundpads`, référence `stable` et tag/APK protégés inchangés. Aucune installation ou test sur le téléphone/tablette physique. Aucune fusion ni push ; GitHub Actions non exécutées.
- Modifications locales laissées non commitées : le gate JVM complet reste en échec préexistant, et la recette live physique demandée reste à effectuer avant finalisation/fusion. Les modifications locales étrangères au patch sont préservées.

Captures finales natives (fixtures isolées, locale anglaise, dimensions Compose contrôlées) : [Bus, Pads actif](screenshots/patch4/bus-phone-active.png), [mixage global, même niveau Pads](screenshots/patch4/bus-globalmix.png).

### Écarts et limites restants

La validation physique de la réactivité, du mixage audible et des gestes/orientations reste au Créateur, selon la recette ci-dessus. Le test JVM de couleurs de l’Arrangement doit être traité séparément avant un gate global entièrement vert. Sound Pads reste debug-only à ce stade ; aucune disponibilité production implicite.

Comme les autres préférences du Bus, `apply()` écrit sur disque de façon asynchrone. Un arrêt forcé brutal immédiatement après le dernier déplacement, avant la fin de cette écriture, peut perdre ce dernier réglage ; le test de redémarrage attend l’écriture effective, et la recette utilisateur inclut une fermeture normale. Pas d’écriture synchrone sur le thread audio/UI.


### Correction tactile et validation finale (4 octobre 2026)

Cause démontrée : le conteneur racine consommait les événements à la passe `PointerEventPass.Final`. Dès un léger mouvement du doigt, cette consommation annulait le geste `combinedClickable` du pad, y compris les maintiens. La protection contre les touches traversant l’écran est désormais un composant frère placé derrière le contenu : elle protège uniquement les zones vides et ne consomme plus les gestes des pads. Aucun changement du moteur audio ou du stockage.

Fichiers : `SoundPadsScreen.kt`, `SoundPadTile.kt`, nouveau `SoundPadsTouchTrace.kt`, nouveau `SoundPadsTouchTest.kt`. La trace debug `SoundPadsTouch`, désactivée par défaut, distingue ACTION_DOWN, TAP, LONG_PRESS et AUDIO_CALLBACK sans consommer les événements.

Avant correction : cinq des six tests tactiles échouaient. Après correction : 27 tests instrumentés réussis, dont 20 pressions avec léger déplacement sur téléphone, tablette paysage et tablette portrait ; appuis longs fiables sans déclenchement audio ; scroll et protection des zones vides. Les six tests tactiles passent aussi avec instrumentation désactivée. Les compilations laboDebug/concertDebug réussissent ; le seul échec JVM reste celui des anciennes couleurs Arrangement décrit ci-dessus.

Le Créateur confirme explicitement la validation de la V1 et de la correction tactile sur appareil réel et autorise les commits et l’intégration. Cette confirmation remplace le statut « recette physique à effectuer » des rapports précédents. Elle ne signifie pas qu’une release a été publiée.

Finalisation Git : le Bus Pads est enregistré dans `b9097ad1`. La fusion reste en attente de clarification de la cible : `stable` pointe sur `636d18ec` (17 mars 2026), et la branche feature contient 1 071 commits supplémentaires avant finalisation. Une fusion complète entraînerait aussi l’historique des autres évolutions MusiMio ; aucun changement de la branche stable ni push n’est effectué sans résolution de ce point. Les modifications locales hors Sound Pads restent exclues des commits.
