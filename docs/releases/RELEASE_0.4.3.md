# Release 0.4.3

## Identification

- VersionName : `0.4.3-beta-labo`
- VersionCode : `7`
- Date de préparation : 18/08/2026
- Statut : AAB importé manuellement et reconnu par Google Play Console ; enregistrement de la release et déploiement non confirmés
- Variante : `laboRelease`
- Application ID : `com.patrick.lrcreader.exo.labo`
- Piste prévue : `Tests fermés / Alpha`

## Verrou versionCode avant génération

- Application confirmée : Stage Music Player Labo, `com.patrick.lrcreader.exo.labo`
- Piste active confirmée : `Tests fermés / Alpha`
- Plus grand `versionCode` Google Play connu avant génération : `6`
- Version distribuée correspondante : `0.4.2-beta-labo`
- Provenance : vérification visuelle manuelle effectuée par l'utilisateur dans Play Console le 18/08/2026
- Prochain `versionCode` disponible retenu : `7`
- Cohérence locale : Gradle utilise `versionCode 7` et `versionName 0.4.3-beta`; la variante `labo` ajoute le suffixe `-labo`

## Source Git

- Branche : `fix/group-perf`
- Commit source applicatif : `8387bd3a2c9e0f94023be514db91437b2c107538`
- Modification fonctionnelle depuis l'AAB de validation 0.4.2 : aucune
- Modification incluse : uniquement `versionCode 7` et `versionName 0.4.3-beta`
- Modification locale exclue : `.idea/deploymentTargetSelector.xml`
- Correctif SoundTouch 16 Ko conservé sans modification

Le fichier `base/root/META-INF/version-control-info.textproto` du bundle désigne directement le commit source ci-dessus.

## AAB candidat

- Tâche Gradle : `./gradlew :app:bundleLaboRelease --console=plain`
- Chemin : `/Users/patrickperoni/AndroidStudioProjects/LrcReader_EXO_V2/app/build/outputs/bundle/laboRelease/app-labo-release.aab`
- Nom : `app-labo-release.aab`
- Taille : `21 919 938` octets
- Date/heure du fichier : `2026-08-18 15:18:26 +0200`
- SHA-256 : `3c6dfc7be4cf96ce545dccaf0c99526a86e39d47511496dd5341d3025b4cab35`
- Intégrité ZIP : aucune erreur détectée
- Statut de l'artefact : candidat importé manuellement dans Play Console

Cet AAB précis est l'unique candidat publiable de cette release. Tout nouveau build produirait un autre artefact et imposerait de refaire les contrôles et d'actualiser cette fiche.

## Manifeste vérifié directement dans l'AAB

- Application ID : `com.patrick.lrcreader.exo.labo`
- VersionName : `0.4.3-beta-labo`
- VersionCode : `7`
- `compileSdkVersion` : `36`
- `targetSdkVersion` : `36`
- `minSdkVersion` : `23`
- Bundletool utilisé pour la lecture : `1.18.1`

## Signature

- Résultat `jarsigner` : `jar verified.`
- Sujet et émetteur : `C=FR, O=Stage Music Player, CN=Patrick Peroni`
- Empreinte SHA-256 du certificat : `91:53:8D:5D:97:99:7B:E3:13:0E:66:91:94:79:B0:C3:85:EB:2D:B4:9E:55:0E:08:9C:09:B0:99:8C:AF:11:B7`
- Algorithme : `SHA256withRSA`, clé RSA 2048 bits
- Validité : du 17/03/2026 au 11/03/2051
- Avertissements non bloquants : certificat auto-signé, chaîne non reconnue par le magasin local, absence d'horodatage et attributs POSIX non protégés par la signature

## Compatibilité pages mémoire 16 Ko

- Bibliothèques natives non compressées : oui
- Alignement demandé dans `BundleConfig.pb` : `PAGE_ALIGNMENT_16K`
- ABI présentes : `arm64-v8a`, `armeabi-v7a`

| ABI | Bibliothèque | Alignement des segments ELF `LOAD` | Résultat |
|---|---|---:|---|
| `arm64-v8a` | `libandroidx.graphics.path.so` | `0x4000` | compatible 16 Ko |
| `arm64-v8a` | `libsoundtouch_jni.so` | `0x4000` | compatible 16 Ko |
| `armeabi-v7a` | `libandroidx.graphics.path.so` | `0x4000` | information complémentaire 32 bits |
| `armeabi-v7a` | `libsoundtouch_jni.so` | `0x4000` | information complémentaire 32 bits |

Conclusion statique : le bundle demande l'alignement ZIP 16 Ko et tous les segments ELF `LOAD` des bibliothèques natives contrôlées sont alignés à `0x4000`.

La validation runtime sur environnement à pages `16384` n'a pas été exécutée. La validation physique utilisateur du correctif SoundTouch, limitée à une lecture réelle jugée normale sans problème perceptible, reste documentée dans `RELEASE_0.4.2.md`. Aucun nouveau test physique spécifique à l'AAB versionCode 7 n'est revendiqué.

## Validations exécutées

### Gate local `./tests.sh`

- Tests unitaires Labo : succès
- Tests unitaires Concert : succès
- Assemblage des APK debug Labo et Concert : succès
- Résultat global : `ALL TESTS PASSED`

### Bundle release

- `bundleLaboRelease` : succès
- Compilation Kotlin `laboRelease` : succès
- Compilation native SoundTouch pour `arm64-v8a` et `armeabi-v7a` : succès
- Lint vital release : aucune erreur ni avertissement hors baseline
- Signature du bundle : succès
- Intégrité ZIP : succès

Les avertissements Kotlin/Kapt préexistants observés pendant la compilation ne sont pas bloquants. Aucun changement de comportement applicatif n'a été introduit par cette release de versionnement.

## Notes de version proposées

- Amélioration de la sélection live sur tablette.
- Meilleure indication du prochain morceau.
- Corrections et améliorations de stabilité.

## Play Console

- Import manuel de l'AAB par l'utilisateur : effectué dans `Tests fermés / Alpha`
- AAB reconnu par Play Console : `0.4.3-beta-labo`, `versionCode 7`
- Avertissement affiché : aucun fichier de désobscurcissement n'est associé à cet App Bundle ; Play Console indique qu'un tel fichier facilite l'analyse des plantages et ANR lorsque R8/ProGuard obscurcit le code
- Nature du message : avertissement non bloquant, pas une erreur d'import
- Décision R8/ProGuard à ce stade : aucune modification décidée
- Release enregistrée ou préparée dans Play Console : non confirmé par les informations disponibles
- Soumission ou démarrage du déploiement : non confirmé
- Distribution effective aux testeurs : non confirmée

### Diagnostic de l'avertissement de désobscurcissement

- `laboRelease` et `concertRelease` utilisent le même build type `release`, configuré avec `isMinifyEnabled = false`.
- R8 n'effectue donc ni shrinking, ni optimisation, ni obfuscation sur `laboRelease`.
- Les fichiers `proguard-android-optimize.txt` et `app/proguard-rules.pro` sont déclarés, mais leurs règles ne sont pas appliquées tant que la minification reste désactivée.
- Aucun `mapping.txt` n'est généré par `bundleLaboRelease` et aucun mapping R8/ProGuard n'est inclus dans cet AAB.
- L'avertissement Play Console est donc informatif pour cette version : le code Kotlin/Java n'est pas obscurci et ne nécessite pas de mapping pour restaurer ses noms.
- Les symboles natifs sont un mécanisme distinct. L'AAB versionCode 7 contient directement, sous `BUNDLE-METADATA/com.android.tools.build.debugsymbols/`, les tables de symboles de `libsoundtouch_jni.so` pour `arm64-v8a` et `armeabi-v7a`.
- Les Build ID de ces fichiers `.sym` correspondent aux bibliothèques SoundTouch empaquetées dans ce même AAB. Ils contiennent les tables `.symtab` permettant la symbolication des fonctions, sans informations `.debug_info` permettant de garantir les fichiers et numéros de ligne.
- Un fichier externe `app/build/outputs/native-debug-symbols/laboRelease/native-debug-symbols.zip` existe encore localement, mais il est antérieur à cet AAB et ses Build ID ne correspondent pas au versionCode 7. Il ne doit pas être utilisé pour cette release.
- Aucun symbole natif distinct n'est présent pour `libandroidx.graphics.path.so`, bibliothèque tierce déjà livrée sans ces métadonnées.
- Aucune activation ou modification de R8/ProGuard n'est décidée dans le cadre de cette release.

Le précédent AAB `0.4.2-beta-labo`, `versionCode 6`, SHA-256 `3fa5a091719b0217ed2a95a28530ea76e6923305d92060ad2decd910d415539d`, reste documenté dans `RELEASE_0.4.2.md` comme artefact de validation 16 Ko non publiable.

Le statut de cette fiche est limité à : **AAB importé et reconnu par Play Console, avec avertissement non bloquant sur le fichier de désobscurcissement ; déploiement et distribution non confirmés**.
