# Stage Music Player — Google Play Release Guide

## Objectif

Décrire pas à pas la procédure officielle pour préparer une release Google Play de Stage Music Player sur la piste explicitement choisie.

Ce document doit permettre de refaire une publication plusieurs mois plus tard sans rechercher dans l'historique du projet.

---

## 1. Variante officielle Google Play

La variante officielle Google Play est :

```text
laboRelease
```

Commande officielle :

```bash
./gradlew :app:bundleLaboRelease
```

Ne jamais utiliser `concertRelease` pour Google Play sans décision explicite.

Historique :

- `labo` est la variante utilisée pour l'état réel du produit SMP.
- `concert` existe comme variante séparée, mais ce n'est pas la variante officielle de publication Google Play.
- Les corrections tablette, DJ, playlists, restauration, split tablette et UX sont portées par le code principal et publiées via `laboRelease`.

---

## 2. Versioning

La configuration de version Android se trouve dans :

```text
app/build.gradle.kts
```

Champs à vérifier avant chaque publication, après avoir déterminé les versions déjà utilisées dans Google Play :

```kotlin
versionCode = ...
versionName = "..."
```

Règles :

- Avant toute génération d'AAB, identifier l'application, la piste concernée et le plus grand `versionCode` déjà utilisé pour cette application.
- Vérifier d'abord Play Console lorsqu'elle est accessible, puis `docs/releases/`, puis l'historique Git et les artefacts précédents si nécessaire.
- Play Console est la source de vérité lorsqu'elle est accessible ; consigner dans la fiche de release la valeur trouvée et sa provenance.
- Le nouveau `versionCode` doit être strictement supérieur au plus grand code déjà utilisé, même si Gradle contient déjà une autre valeur.
- Si Play Console est inaccessible, annoncer le dernier code connu et le prochain code possible. En cas de preuve insuffisante ou contradictoire, arrêter avant génération et demander une vérification manuelle.
- Google Play refuse un bundle dont le `versionCode` a déjà été publié.
- `versionName` doit rester lisible pour une bêta publique.

Ordre obligatoire :

```text
VERSION PLAY CONSOLE → VERSION LOCALE → VALIDATION → AAB
```

Convention actuelle :

```text
0.4-beta-labo
0.5-beta-labo
```

Note :

- Dans Gradle, `versionName` peut être défini comme `0.4-beta`.
- La variante `labo` ajoute ensuite le suffixe `-labo`.
- Le nom visible final attendu pour la variante Google Play est donc du type `0.4-beta-labo`.

---

## 3. Signature Release

Keystore officiel :

```text
/Users/patrickperoni/Keystore/stage_music_player.jks
```

Alias :

```text
stageplayer
```

Certificat :

```text
C=FR, O=Stage Music Player, CN=Patrick Peroni
```

Règles critiques :

- Ce keystore est indispensable pour publier les mises à jour de l'application.
- Ne jamais perdre ce fichier.
- Ne jamais remplacer ce keystore sans décision explicite.
- Ne jamais publier une version signée avec un autre certificat sans comprendre les conséquences Google Play.
- Ne jamais écrire les vrais mots de passe dans la documentation du dépôt.

---

## 4. gradle.properties

Les secrets de signature sont configurés hors dépôt dans :

```text
~/.gradle/gradle.properties
```

Variables obligatoires :

```properties
stageReleaseStorePassword=...
stageReleaseKeyPassword=...
```

Ces valeurs doivent contenir les vrais mots de passe du keystore et de la clé privée.

Historique incident :

Le build `bundleLaboRelease` échouait avec :

```text
Failed to read key stageplayer ... keystore password was incorrect
```

Cause :

`~/.gradle/gradle.properties` contenait encore des placeholders :

```properties
stageReleaseStorePassword=TON_MDP_KEYSTORE
stageReleaseKeyPassword=TON_MDP_CLE
```

Résolution :

Remplacer ces placeholders par les vrais mots de passe dans `~/.gradle/gradle.properties`.

Ne jamais committer ce fichier.

---

## 5. Génération du bundle

Ne commencer cette étape qu'après avoir verrouillé le prochain `versionCode` disponible selon la section 2.

Commande officielle :

```bash
./gradlew :app:bundleLaboRelease
```

Emplacement attendu du bundle :

```text
app/build/outputs/bundle/laboRelease/app-labo-release.aab
```

Validation minimum :

- le build Gradle se termine sans erreur ;
- le fichier `.aab` existe à l'emplacement attendu ;
- le bundle est signé avec le certificat officiel ;
- l'alias de signature correspond à `stageplayer`.

Vérification de signature possible :

```bash
jarsigner -verify -certs app/build/outputs/bundle/laboRelease/app-labo-release.aab
```

Résultat attendu :

```text
jar verified.
```

Des avertissements liés à un certificat auto-signé, à l'absence de timestamp ou aux attributs POSIX peuvent apparaître. Ils ne bloquent pas nécessairement l'upload Google Play si le bundle est bien signé avec le certificat attendu.

---

## 6. Upload Google Play

Procédure :

1. Ouvrir Google Play Console.
2. Confirmer l'application par son application ID.
3. Confirmer la piste existante destinée à la release ; ne pas changer de piste sur la seule base de ce guide.
4. Créer une nouvelle version.
5. Déposer le fichier :

```text
app-labo-release.aab
```

6. Vérifier les messages de Google Play Console.
7. Ajouter les notes de version.
8. N'effectuer l'action finale de distribution que si elle est explicitement autorisée.

Référence locale au 18/08/2026 : l'utilisateur a confirmé visuellement dans Play Console que la piste active est `Tests fermés / Alpha` et qu'elle distribue `0.4.2-beta-labo`, `versionCode 6`. Cette valeur courante appartient à l'historique de release et ne remplace jamais la vérification de Play Console lors d'une future préparation.

---

## 7. Check-list Release

Avant publication :

- application Google Play identifiée ;
- piste concernée identifiée ;
- plus grand `versionCode` Play connu et provenance consignés ;
- prochain `versionCode` disponible déterminé avant tout build ;
- Git propre ou modifications restantes explicitement identifiées ;
- documentation à jour ;
- `versionCode` strictement supérieur au plus grand code déjà utilisé ;
- `versionName` mis à jour ;
- compilation OK ;
- bundle généré ;
- bundle signé avec le certificat officiel ;
- tests minimum réalisés ;
- notes de version rédigées ;
- chemin du `.aab` vérifié ;
- aucun changement métier ou UI non prévu dans la release.

Commande de référence :

```bash
./gradlew :app:bundleLaboRelease
```

Bundle attendu :

```text
app/build/outputs/bundle/laboRelease/app-labo-release.aab
```

---

## 8. Historique important

Difficultés déjà rencontrées :

- confusion entre `concertRelease` et `laboRelease` ;
- proposition initiale de `bundleConcertRelease`, alors que la publication Google Play doit utiliser `bundleLaboRelease` ;
- oubli des vrais mots de passe dans `~/.gradle/gradle.properties` ;
- présence de placeholders dans `gradle.properties` ;
- build signé impossible tant que les placeholders n'étaient pas remplacés ;
- nécessité de conserver précieusement le keystore officiel ;
- nécessité de vérifier explicitement la signature du bundle avant upload.
- génération d'un AAB avec un `versionCode` déjà utilisé, faute d'avoir interrogé l'état Play avant le build.

Décision actuelle :

```text
Google Play public beta = laboRelease
```

Commande officielle :

```bash
./gradlew :app:bundleLaboRelease
```

Fichier à uploader :

```text
app/build/outputs/bundle/laboRelease/app-labo-release.aab
```
