# Sound Pads — développement dans Android Studio

État du 5 octobre 2026 : `stable` et `feature/soundpads` pointent sur le code validé `ed5c8506`. La base Labo avant Pads est `51da14df`. L’ancienne stable est protégée ; sa référence locale a été déplacée sans merge. Aucun push ni publication Google Play effectué dans cette finalisation.

État Git courant : `stable` et `origin/stable` sur `0b6eb4b9`, publié le 7 octobre 2026 avec `--force-with-lease` ; voir [PROJECT_STATUS.md](../../PROJECT_STATUS.md). Le jalon ci-dessus reste historique.

## Parcours habituel

1. Ouvrir `LrcReader_EXO_V2` dans Android Studio sur `stable`.
2. Synchroniser Gradle, puis choisir `app → laboDebug` dans **Build Variants**.
3. Sélectionner l’appareil et **Run**.
4. Dans **MusiMio Sound Pads Test**, toucher l’icône des quatre carrés arrondis ; elle est présente sur téléphone et dans le cockpit tablette. Les anciennes entrées « test audio » ne constituent plus le parcours.
5. Pour Labo release, sélectionner `laboRelease` et utiliser le flux release habituel avec la signature existante. **Cette installation met à jour MusiMio Labo**, contrairement au debug séparé.

## Identités et disponibilité

| Variante | applicationId | Nom visible | Pads | Version / code |
| --- | --- | --- | --- | --- |
| `laboDebug` | `com.patrick.lrcreader.exo.labo.soundpads.debug` | MusiMio Sound Pads Test | oui | 0.5.0-beta-labo / 15 |
| `laboRelease` | `com.patrick.lrcreader.exo.labo` | MusiMio Labo | oui | 0.5.0-beta-labo / 15 |
| `concertDebug` | `com.patrick.lrcreader.exo.concert.soundpads.debug` | MusiMio Sound Pads Test | oui | 0.5.0-beta-concert / 15 |
| `concertRelease` | `com.patrick.lrcreader.exo.concert` | MusiMio | non | 0.5.0-beta-concert / 15 |

Labo est la référence fonctionnelle ; Concert reste compilable. Il n’existe pas de flavor Production distinct. Le suffixe d’identifiant `.soundpads.debug` ne s’applique qu’au debug. Namespace, identifiants release, signature et chemins de stockage sont conservés.

## Données et coexistence

Debug et release peuvent coexister car leurs packages diffèrent. Chaque installation conserve ses préférences, permissions et fichiers internes : une banque créée dans le debug n’apparaît pas automatiquement dans Labo release.

Un dossier de bibliothèque externe peut néanmoins être partagé si l’utilisateur donne cette autorisation aux deux applications. Pour les essais debug, choisir une bibliothèque de test et des copies de morceaux ; le suffixe ne protège pas un dossier externe partagé.

La banque Pads est persistante dans l’installation courante mais n’est pas incluse dans les sauvegardes générales. Ne pas désinstaller/effacer les données pour transférer une banque : aucun export/import de banque n’est livré en V1.

## Validation

Les quatre compilations et 346 tests JVM ciblés par variante ont réussi depuis `stable`. L’échec Arrangement de la suite globale reste connu. Le Créateur confirme la recette physique de `laboRelease` : accès, tactile, lecture, Bus PADS, fader et Stop. Cette validation locale ne prouve aucune distribution Google Play.
