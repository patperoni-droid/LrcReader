# Release 0.4.2

## Identification

- Date de préparation : 18/08/2026
- Statut : artefact de validation du correctif 16 Ko, non publiable car son `versionCode 6` est déjà utilisé sur Google Play
- Canal confirmé manuellement par l'utilisateur le 18/08/2026 : `Tests fermés / Alpha`, actif
- Variante : `laboRelease`
- Tâche Gradle : `./gradlew :app:bundleLaboRelease --console=plain`
- Commit de base avant correctif : `6ae9c55e87a31b4bdda90433e18ad90bfab9d4f2`
- Branche source : `fix/group-perf`

## État Git avant génération

- Branche en avance de 12 commits sur `origin/fix/group-perf`.
- Modification IDE préexistante : `.idea/deploymentTargetSelector.xml`, explicitement exclue du correctif et du commit.
- Fiche de release préexistante non suivie : `docs/releases/RELEASE_0.4.2.md`, mise à jour dans le cadre de cette correction.
- Modification applicative autorisée : ajout des seules options d'édition de liens 16 Ko à la cible CMake `soundtouch_jni`.

## Correctif natif 16 Ko

- SoundTouch reste en version `2.3.2`.
- Le NDK reste en version `27.0.12077973`.
- Aucun code Kotlin, JNI ou DSP n'a été modifié.
- Options ajoutées uniquement à la cible `soundtouch_jni` :

```text
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

## Version

- `versionCode` Gradle : `6`
- `versionName` Gradle : `0.4.2-beta`
- `versionName` final vérifié dans l'AAB : `0.4.2-beta-labo`
- Plus grand `versionCode` Google Play connu avant la prochaine génération : `6`
- Provenance : vérification visuelle manuelle effectuée par l'utilisateur dans Play Console le 18/08/2026
- Version distribuée affichée : `0.4.2-beta-labo`, `versionCode 6`, date de sortie affichée 27/07/2026
- Conséquence : le code `6` n'est plus disponible et l'AAB décrit ci-dessous ne doit pas être envoyé à Google Play.
- Prochaine version envisagée, non générée dans cette mission : `0.4.3-beta-labo`, `versionCode 7`, variante `laboRelease`.

## Artefact de validation non publiable

- Application ID vérifié dans l'AAB : `com.patrick.lrcreader.exo.labo`
- Chemin : `/Users/patrickperoni/AndroidStudioProjects/LrcReader_EXO_V2/app/build/outputs/bundle/laboRelease/app-labo-release.aab`
- Nom : `app-labo-release.aab`
- Taille : `21 919 339` octets
- Date/heure du fichier : `2026-08-18 14:35:09 +0200`
- SHA-256 : `3fa5a091719b0217ed2a95a28530ea76e6923305d92060ad2decd910d415539d`
- Intégrité ZIP : aucune erreur détectée

Cet AAB précis reste la preuve de validation du correctif 16 Ko. Il n'est plus candidat à l'upload : son `versionCode 6` est déjà utilisé par la version distribuée sur Google Play. Il doit être conservé comme artefact de validation et ne doit pas être envoyé à Play Console.

## Signature

- Résultat `jarsigner` : `jar verified.`
- Sujet et émetteur : `C=FR, O=Stage Music Player, CN=Patrick Peroni`
- Empreinte SHA-256 du certificat : `91:53:8D:5D:97:99:7B:E3:13:0E:66:91:94:79:B0:C3:85:EB:2D:B4:9E:55:0E:08:9C:09:B0:99:8C:AF:11:B7`
- Algorithme : `SHA256withRSA`, clé RSA 2048 bits
- Validité : du 17/03/2026 au 11/03/2051
- Avertissements non bloquants observés : certificat auto-signé, chaîne non reconnue par le magasin local, absence d'horodatage et attributs POSIX non protégés par la signature.

## Validations exécutées

### Gate local `./tests.sh`

- Tests unitaires Labo : succès
- Tests unitaires Concert : succès
- Assemblage des APK debug Labo et Concert : succès
- Résultat global : `ALL TESTS PASSED`

### Contrôles ciblés supplémentaires

- Compilation Kotlin `compileLaboDebugKotlin` : succès
- Compilation native Labo avec SoundTouch réel, `arm64-v8a` et `armeabi-v7a` : succès
- Compilation native Concert en mode stub, `arm64-v8a` et `armeabi-v7a` : succès
- Tests unitaires Labo : succès
- Tests unitaires Concert : succès
- Assemblage `assembleLaboDebug` : succès

### Bundle release

- `bundleLaboRelease` : succès
- Compilation Kotlin `laboRelease` : succès
- Lint vital release : aucune erreur ni avertissement hors baseline
- Signature du bundle : succès

### Validation appareil

- Validation physique utilisateur après le correctif 16 Ko : lecture réelle réussie, fonctionnement audio jugé normal et aucun problème perceptible signalé.
- Cette validation correspond uniquement au retour utilisateur disponible ; aucune mesure instrumentée ni validation détaillée scénario par scénario n'est revendiquée.
- Un émulateur `arm64-v8a`, Android 14/API 34, à pages mémoire `4096` octets était disponible.
- Smoke test sur cet émulateur : lancement à froid réussi, `libsoundtouch_jni.so` chargée, flavor `REAL`, disponibilité native `true`, autotest natif `true`, chemin neutre `PURE_EXO`.
- Ce smoke test ne valide pas l'exécution sur un appareil à pages mémoire 16 Ko. La validation physique utilisateur n'inclut pas de mesure instrumentée de la latence, des coupures, du gain ou des vumètres.

## Contrôles Google Play et Android

### Preuves obtenues directement sur cet AAB

- `compileSdkVersion` : `36`
- `targetSdkVersion` : `36`
- `minSdkVersion` : `23`
- `versionCode` : `6`
- `versionName` : `0.4.2-beta-labo`
- Application ID : `com.patrick.lrcreader.exo.labo`
- Android Gradle Plugin : `8.12.3`
- Bundletool : `1.18.1`
- Bibliothèques natives non compressées : oui
- Alignement demandé par `BundleConfig.pb` : `PAGE_ALIGNMENT_16K`
- ABI présentes : `arm64-v8a`, `armeabi-v7a`

Bibliothèques natives présentes :

| ABI | Bibliothèque | Alignement ELF `LOAD` | Résultat 16 Ko |
|---|---|---:|---|
| `arm64-v8a` | `libandroidx.graphics.path.so` | `0x4000` | compatible |
| `arm64-v8a` | `libsoundtouch_jni.so` | `0x4000` | compatible |
| `armeabi-v7a` | `libandroidx.graphics.path.so` | `0x4000` | information complémentaire 32 bits |
| `armeabi-v7a` | `libsoundtouch_jni.so` | `0x4000` | information complémentaire 32 bits |

Conclusion statique 16 Ko : le packaging du bundle demande un alignement ZIP 16 Ko et tous les segments ELF `LOAD` des quatre bibliothèques contrôlées sont alignés à `0x4000`. La compatibilité ELF 16 Ko de cet AAB est vérifiée.

Conclusion runtime 16 Ko : non testée, faute d'appareil ou d'émulateur à pages mémoire `16384` disponible pendant la préparation.

### Exigences externes vérifiées le 18/08/2026

- Google Play exigera `targetSdk 36` pour les nouvelles applications et mises à jour à partir du 31/08/2026. Cet AAB cible bien l'API 36.
- La documentation Android officielle actuelle indique qu'une mise à jour ciblant l'API 35 ou supérieure doit supporter les pages mémoire 16 Ko sur les appareils 64 bits ; elle annonce le blocage des mises à jour non compatibles à partir du 01/02/2027.
- Source API cible : <https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-AU>
- Source 16 Ko : <https://developer.android.com/guide/practices/page-sizes>

### Grands écrans et orientation

- Le manifeste de cet AAB contient `android:screenOrientation="portrait"` pour `MainActivity`.
- Aucun opt-out `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` n'est présent dans l'AAB.
- Comme l'AAB cible l'API 36, Android 16 ignore par défaut la restriction d'orientation sur les écrans `sw600dp` et plus. La compatibilité visuelle dépend donc des dispositions adaptatives et des validations téléphone/tablette réelles.
- Source officielle : <https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability>

## Notes de version proposées

- Amélioration de la sélection live sur tablette.
- Meilleure indication du prochain morceau.
- Corrections et améliorations de stabilité.

Ces notes correspondent aux changements présents dans le commit candidat, notamment la séparation des modes de sélection live et la priorité de l'indicateur du prochain morceau.

## Points restant à contrôler

1. Si une traçabilité plus fine est nécessaire, consigner séparément les scénarios audio exécutés sur appareil physique ; le retour actuel confirme uniquement une lecture réelle normale sans problème perceptible.
2. Si possible, installer et tester l'application dans un environnement dont `getconf PAGE_SIZE` retourne `16384` ; cette validation runtime 16 Ko n'est pas encore acquise.
3. Avant toute future génération, reconfirmer le plus grand `versionCode` déjà utilisé et vérifier que le nouveau code lui est strictement supérieur.
4. Pour la prochaine release envisagée, préparer séparément `0.4.3-beta-labo`, `versionCode 7`, sans réutiliser cet AAB.

## Play Console

- Piste active : `Tests fermés / Alpha`
- Version distribuée au 18/08/2026 : `0.4.2-beta-labo`, `versionCode 6`
- Date de sortie affichée : 27/07/2026
- Provenance de ces informations : vérification visuelle manuelle de Play Console par l'utilisateur ; aucune vérification directe par Codex n'est revendiquée.
- Upload de l'AAB décrit dans cette fiche : non effectué et désormais interdit
- Nouvelle release Play Console : non créée
- Soumission : non effectuée
- Promotion ou publication : non effectuée
- Analyse Google de cet AAB : non disponible

Le statut de cette fiche est limité à : **artefact local du correctif 16 Ko, vérifié mais non publiable en raison du `versionCode 6` déjà utilisé**.
