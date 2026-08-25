# Release 0.4.4

## Identification

- VersionName : `0.4.4-beta-labo`
- VersionCode : `8`
- Date de préparation : 25/08/2026
- Statut : AAB candidat préparé et vérifié localement, non uploadé
- Variante : `laboRelease`
- Application ID : `com.patrick.lrcreader.exo.labo`
- Piste prévue : `Tests fermés / Alpha`

## Verrou versionCode avant génération

- Application Google Play existante : `com.patrick.lrcreader.exo.labo`
- Plus grand `versionCode` Google Play connu avant génération : `7`
- Version correspondante : `0.4.3-beta-labo`
- Provenance : vérification directe dans Play Console le 25/08/2026
- Prochain `versionCode` disponible retenu : `8`
- Cohérence locale : Gradle utilise `versionCode 8` et `versionName 0.4.4-beta`; la variante `labo` ajoute le suffixe `-labo`

## Source Git

- Branche : `fix/group-perf`
- Commit de finalisation du nom public : `417d13d1` (`Finalize MusiMio public Android naming`)
- Commit source de l'AAB : `0e417917f22740198cc4953002cd40d8671cbfc5` (`Prepare MusiMio 0.4.4 beta release`)
- Révision embarquée dans `base/root/META-INF/version-control-info.textproto` : `0e417917f22740198cc4953002cd40d8671cbfc5`
- Modifications locales exclues : réglage IDE `.idea/deploymentTargetSelector.xml` et arborescence de préparation graphique sous `branding/musimio/`

## AAB candidat

- Tâche Gradle : `./gradlew :app:bundleLaboRelease --console=plain`
- Chemin : `/Users/patrickperoni/AndroidStudioProjects/LrcReader_EXO_V2/app/build/outputs/bundle/laboRelease/app-labo-release.aab`
- Taille : `22 090 528` octets
- Date/heure du fichier : `2026-08-25 18:29:18 +0200`
- SHA-256 : `4fc8d4ddd511430b2d75e63301bad037c9c61af270a00e884d94932bc180a1ee`
- Intégrité ZIP : aucune erreur détectée
- Statut : candidat à l'upload manuel, non uploadé dans cette mission

Cet AAB précis est le candidat documenté. Toute nouvelle génération produit un nouvel artefact et impose de refaire les contrôles et de mettre à jour cette fiche.

## Manifeste vérifié dans l'AAB

- Application ID : `com.patrick.lrcreader.exo.labo`
- VersionCode : `8`
- VersionName : `0.4.4-beta-labo`
- Nom public (`app_name`) : `MusiMio Labo`
- `compileSdkVersion` : `36`
- `targetSdkVersion` : `36`
- `minSdkVersion` : `23`
- Icône : `@mipmap/ic_launcher`
- Icône ronde : `@mipmap/ic_launcher_round`
- FileProvider : `com.patrick.lrcreader.exo.labo.fileprovider`, inchangé
- Bundletool utilisé pour la lecture : `1.18.1`

## Identité MusiMio

- Nouveau nom public : MusiMio, avec le libellé de variante `MusiMio Labo`
- Icônes launcher raster présentes pour `mdpi`, `hdpi`, `xhdpi`, `xxhdpi` et `xxxhdpi`
- Adaptive Icons normale et ronde présentes sous `mipmap-anydpi-v26`
- Foreground MusiMio empaqueté dans l'AAB et identique à la ressource Android source `xxxhdpi`
- Background Android conservé à `#090A0B`
- Les références publiques résiduelles à l'ancien nom ont été remplacées ; les occurrences techniques SMP nécessaires au format et à la compatibilité restent intactes

## Signature et continuité Google Play

- Résultat `jarsigner` : `jar verified.`
- Sujet et émetteur : `C=FR, O=Stage Music Player, CN=Patrick Peroni`
- Empreinte SHA-256 du certificat : `91:53:8D:5D:97:99:7B:E3:13:0E:66:91:94:79:B0:C3:85:EB:2D:B4:9E:55:0E:08:9C:09:B0:99:8C:AF:11:B7`
- Algorithme : `SHA256withRSA`, clé RSA 2048 bits
- Validité : du 17/03/2026 au 11/03/2051
- Comparaison : empreinte identique à celle de l'AAB `0.4.3-beta-labo`, versionCode 7, documentée dans `RELEASE_0.4.3.md`
- Avertissements `jarsigner` non bloquants : certificat auto-signé, chaîne locale non reconnue, absence d'horodatage et attributs POSIX non protégés

Conclusion de continuité locale : même application ID, versionCode strictement supérieur et même certificat que la version 7. Cet AAB possède les caractéristiques requises pour être accepté comme mise à jour de l'application Google Play existante. L'acceptation effective reste à confirmer lors de l'upload dans Play Console.

## Compatibilité native et pages mémoire 16 Ko

- Bibliothèques natives non compressées : oui
- Alignement demandé par `BundleConfig.pb` : `PAGE_ALIGNMENT_16K`
- ABI présentes : `arm64-v8a`, `armeabi-v7a`
- `libandroidx.graphics.path.so` : segments ELF `LOAD` alignés à `0x4000`
- `libsoundtouch_jni.so` : segments ELF `LOAD` alignés à `0x4000`

## Validations exécutées

- Compilation Kotlin Labo debug : succès
- Compilation Kotlin Concert debug : succès
- Tests unitaires Labo : succès
- Tests unitaires Concert : succès
- Suite `./tests.sh` : `ALL TESTS PASSED`
- Assemblage des APK debug Labo et Concert : succès
- `lintVitalLaboRelease` : succès, aucune erreur ni aucun avertissement
- `bundleLaboRelease` : succès
- Signature : succès
- Intégrité ZIP : succès
- `git diff --check` : succès avant les commits de code et de version

### Dette lint globale préexistante

Le lint debug global reste en échec sur une dette antérieure à cette release :

- Labo : 158 erreurs, 709 avertissements et 25 informations
- Concert : 157 erreurs, 709 avertissements et 25 informations
- Première erreur : appel à `HashMap.putIfAbsent` nécessitant API 24 dans `LibraryBackendInternal.kt`, alors que le minimum est 23

Les lignes modifiées pour finaliser le nom MusiMio et les nouvelles ressources de traduction n'ajoutent aucun diagnostic. Aucun nettoyage lint global ni changement fonctionnel sans rapport n'a été entrepris.

## Compatibilité des données

- `applicationId`, namespace, packages, FileProvider et signature inchangés
- Extension `.smp`, URI historiques, MIME, préférences, base de données, stockage, protocoles réseau et formats de sauvegarde inchangés
- Mise à jour prévue sur l'installation Google Play existante, sans création d'une nouvelle application
- Données et fichiers `.smp` existants conservés par les mécanismes de compatibilité actuels

## Notes Play Store proposées

- Stage Music Player devient MusiMio.
- Nouvelle identité visuelle et nouvelle icône MusiMio.
- Terminologie clarifiée autour des fichiers au format `.smp`.
- Compatibilité avec les installations, bibliothèques et sauvegardes existantes conservée.

## Play Console

- Upload : non effectué
- Release Play Console : non créée
- Fiche Store : non modifiée
- Soumission : non effectuée
- Déploiement : non effectué
- Statut réel : AAB candidat préparé localement, en attente d'upload manuel

## Points à vérifier avant upload

1. Relire le chemin et l'empreinte SHA-256 de l'AAB candidat.
2. Importer exclusivement cet AAB dans l'application `com.patrick.lrcreader.exo.labo`, piste `Tests fermés / Alpha`.
3. Confirmer dans Play Console `versionCode 8` et `0.4.4-beta-labo`.
4. Vérifier les éventuels messages d'analyse Google Play avant tout enregistrement ou déploiement.
