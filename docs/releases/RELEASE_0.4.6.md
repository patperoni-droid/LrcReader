# Release 0.4.6

## Identification

- VersionName : `0.4.6-beta-labo`
- VersionCode : `10`
- Date de préparation : 28/08/2026
- Statut : AAB candidat préparé et vérifié localement, non uploadé
- Variante : `laboRelease`
- Application ID : `com.patrick.lrcreader.exo.labo`
- Piste prévue : `Tests fermés / Alpha`

## Verrou versionCode avant génération

- Application Google Play existante : `com.patrick.lrcreader.exo.labo`
- Plus grand `versionCode` Google Play connu avant génération : `9`
- Version correspondante : `0.4.5-beta-labo`
- État Play : actif, déploiement complet sur `Tests fermés / Alpha`
- Provenance : vérification directe dans Play Console le 28/08/2026 ; AAB code 9 importé le 27/08/2026 et release mise à jour le 27/08/2026
- Prochain `versionCode` disponible retenu : `10`
- Cohérence locale : Gradle utilise `versionCode 10` et `versionName 0.4.6-beta`; la variante `labo` ajoute le suffixe `-labo`

## Source Git

- Branche : `fix/group-perf`
- HEAD de départ de la préparation : `1a509aaddcb78719ebd547a952863578b3b3336d`
- Commit source de l'AAB : `59506568eb5bee05dd9d65abcc49b91b84947157` (`chore(release): bump MusiMio beta to 0.4.6`)
- Révision embarquée dans `base/root/META-INF/version-control-info.textproto` : `59506568eb5bee05dd9d65abcc49b91b84947157`
- Modifications locales exclues : réglage IDE `.idea/deploymentTargetSelector.xml` et arborescence graphique préexistante sous `branding/musimio/`

## Périmètre depuis la bêta Play code 9

- état de mise à jour des sauvegardes clarifié et prise en compte des modifications de paroles ;
- confirmation d'enregistrement des paroles rendue explicite et rapprochée de l'action ;
- cycle de vie de la Liste Live fiabilisé et état `Next` obsolète supprimé ;
- placement du curseur stabilisé dans l'éditeur de paroles ;
- règles de protection des appareils utilisateur renforcées pour interdire les tests instrumentés destructifs par défaut.

Les correctifs `fix(player): prevent SAF Binder ANR during lyrics load`, `fix(saf): keep remaining Binder calls off main thread` et `fix(playlists): make duration cache thread-safe` sont bien ancêtres du commit de version du code 9. Ils sont donc inclus dans le code 10, mais ne constituent pas un delta par rapport à la bêta code 9 actuellement distribuée.

## AAB candidat

- Tâches Gradle : `:app:lintVitalLaboRelease` et `:app:bundleLaboRelease --rerun-tasks`
- Chemin : `/Users/patrickperoni/AndroidStudioProjects/LrcReader_EXO_V2/app/build/outputs/bundle/laboRelease/app-labo-release.aab`
- Taille : `22 119 164` octets
- Date/heure du fichier : `2026-08-28 18:55:49 +0200`
- SHA-256 : `1ec1bcc6a04efd790663f8ad1a05e81df4d673833e6ee2bbd59e89b1dcab16de`
- Intégrité ZIP : aucune erreur détectée
- Validation structurelle bundletool 1.18.1 : succès
- Statut : candidat à l'upload manuel, non uploadé dans cette mission

Cet AAB précis est le candidat documenté. Toute nouvelle génération produit un nouvel artefact et impose de refaire les contrôles et de mettre à jour cette fiche.

## Manifeste vérifié dans l'AAB

- Application ID : `com.patrick.lrcreader.exo.labo`
- VersionCode : `10`
- VersionName : `0.4.6-beta-labo`
- `compileSdkVersion` : `36`
- `targetSdkVersion` : `36`
- `minSdkVersion` : `23`
- Variante : Labo release, sans suffixe ni configuration debug
- FileProvider : `com.patrick.lrcreader.exo.labo.fileprovider`
- Bundletool utilisé pour la lecture : `1.18.1`

## Signature et continuité Google Play

- Résultat `jarsigner` : `jar verified.`
- Sujet et émetteur : `C=FR, O=Stage Music Player, CN=Patrick Peroni`
- Empreinte SHA-256 du certificat : `91:53:8D:5D:97:99:7B:E3:13:0E:66:91:94:79:B0:C3:85:EB:2D:B4:9E:55:0E:08:9C:09:B0:99:8C:AF:11:B7`
- Algorithme : `SHA256withRSA`, clé RSA 2048 bits
- Validité : du 17/03/2026 au 11/03/2051
- Comparaison : empreinte identique aux AAB précédemment documentés
- Avertissements `jarsigner` non bloquants : certificat auto-signé, chaîne locale non reconnue, absence d'horodatage et attributs POSIX non protégés

Conclusion de continuité locale : même application ID, versionCode strictement supérieur et même certificat. L'acceptation effective reste à confirmer lors de l'upload et de l'analyse dans Play Console.

## Compatibilité native et pages mémoire 16 Ko

- ABI présentes dans ce nouvel AAB : `arm64-v8a`, `armeabi-v7a`
- Bibliothèques natives présentes pour chaque ABI : `libandroidx.graphics.path.so`, `libsoundtouch_jni.so`
- Configuration du bundle : `uncompressNativeLibraries.enabled = true`
- Alignement demandé par `BundleConfig.pb` : `PAGE_ALIGNMENT_16K`
- Tous les segments ELF `LOAD` des quatre bibliothèques analysées sont alignés à `0x4000` (16 Ko)

Cette conclusion porte sur l'AAB local exact ci-dessus. La validation Google Play n'a pas été exécutée puisque l'artefact n'a pas été uploadé.

## SDK 36, grands écrans et orientation

- Le manifeste final confirme `compileSdkVersion 36` et `targetSdkVersion 36`.
- `MainActivity` conserve la déclaration historique `screenOrientation="portrait"` pour les écrans compacts.
- Sur Android 16 / API 36, le système ignore cette restriction sur les grands écrans `sw600dp` et plus ; l'application contient déjà son routage Compose adaptatif téléphone/tablette.
- Aucune nouvelle validation instrumentée ou installation sur appareil physique n'a été effectuée pendant cette préparation.
- Les preuves fonctionnelles téléphone/tablette déjà documentées restent les preuves disponibles ; aucune nouvelle modification de layout ou d'orientation n'entre dans cette release.

## Validations exécutées

- Tests unitaires Labo : succès
- Tests unitaires Concert : succès
- Suite `./tests.sh` : `ALL TESTS PASSED`
- Assemblage des APK debug Labo et Concert : succès
- Hook pré-commit `compileLaboDebugKotlin` : succès
- `lintVitalLaboRelease` : succès, aucune erreur ni aucun avertissement nouveau
- `bundleLaboRelease --rerun-tasks` : succès
- Signature : succès
- Intégrité ZIP : succès
- Validation structurelle bundletool : succès
- `git diff --check` : succès
- Tests instrumentés / appareil physique : non exécutés, conformément aux règles de protection des appareils

## Compatibilité des données

- `applicationId`, namespace, packages, FileProvider et signature inchangés
- Aucun changement de schéma, de format `.smp`, de base de données ou de stockage n'a été introduit par la préparation de release
- Mise à jour prévue sur l'installation Google Play existante, avec conservation des données selon les mécanismes Android normaux de mise à jour signée

## Notes Play Store proposées

- Liste Live et préparation du prochain morceau rendues plus fiables.
- Éditeur de paroles : placement du curseur et confirmation d'enregistrement améliorés.
- Détection des modifications de paroles renforcée pour les sauvegardes.
- Correctifs généraux de stabilité et de fiabilité.

## Avertissements avant publication

- La Play Console affichait encore le 28/08/2026 une notification concernant l'échéance `targetSdk 36` du 31/08/2026. Le manifeste du candidat local cible bien l'API 36 ; la disparition de l'avertissement devra être contrôlée après l'analyse de l'AAB par Google Play.
- Les avertissements de compilation Kotlin et de dépréciation sont préexistants et non bloquants ; `lintVitalLaboRelease` ne rapporte aucune erreur ni aucun avertissement nouveau.
- La validation Play Console de la compatibilité, de la signature de mise à jour et des pages 16 Ko n'existe pas encore tant que l'AAB n'a pas été importé.

## Play Console

- Upload : non effectué
- Release Play Console : non créée
- Fiche Store : non modifiée
- Captures et vidéo : non modifiées
- Piste : non modifiée
- Soumission : non effectuée
- Déploiement : non effectué
- Statut réel : AAB candidat préparé localement, en attente de décision et d'upload manuel

## Points à vérifier avant upload

1. Relire le chemin, la taille et l'empreinte SHA-256 de l'AAB candidat.
2. Importer exclusivement cet AAB dans l'application `com.patrick.lrcreader.exo.labo`, piste `Tests fermés / Alpha`.
3. Confirmer dans Play Console `versionCode 10` et `0.4.6-beta-labo`.
4. Attendre et contrôler l'analyse Google Play, notamment les exigences API 36, grands écrans et pages mémoire 16 Ko.
5. Ne déployer qu'après lecture des éventuels avertissements Play.
