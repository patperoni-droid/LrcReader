# Sound Pads — développement dans Android Studio

Configuration du 3 octobre 2026, branche `feature/soundpads`, créée depuis `feature/chordpro-audio-lyrics` à `51da14df`. Les changements locaux préexistants et le prototype sont conservés. La branche `stable`, le tag `phone-stable-before-tablet-ux` et l’APK de secours restent inchangés. Cette branche de développement ne constitue pas une release validée.

## Parcours habituel

1. Ouvrir le projet `LrcReader_EXO_V2` dans Android Studio, sur `feature/soundpads`.
2. Effectuer la synchronisation Gradle si proposée.
3. Dans **Build Variants**, sélectionner **laboDebug** pour le module **app**.
4. Sélectionner la configuration **app**, puis l’appareil et **Run ▶**.
5. Sur l’appareil, ouvrir **MusiMio Sound Pads Test**, puis le menu ⋮ → **Sound Pads — test audio**.

`concertDebug` reste également disponible pour tester le backend Concert. Les deux debug portent le nom MusiMio Sound Pads Test ; installer uniquement celui souhaité pour éviter deux icônes identiques. Aucun script Gradle temporaire ni propriété spéciale nécessaire.

## Identités des variantes

| Variante Android Studio | applicationId | Nom visible |
| --- | --- | --- |
| laboDebug | com.patrick.lrcreader.exo.labo.soundpads.debug | MusiMio Sound Pads Test |
| concertDebug | com.patrick.lrcreader.exo.concert.soundpads.debug | MusiMio Sound Pads Test |
| laboRelease | com.patrick.lrcreader.exo.labo | MusiMio Labo |
| concertRelease | com.patrick.lrcreader.exo.concert | MusiMio |

Il n’existe pas de flavor Production distinct dans ce projet. La configuration release conserve ses identifiants, labels, signature et réglages actuels. Les différences natives Labo/Concert restent celles du projet.

Le suffixe est défini uniquement dans le build type debug. La ressource `app_name` dans `src/debug` remplace les labels de flavor seulement en debug. Le namespace Kotlin reste inchangé. FileProvider et AndroidX Startup utilisent des autorités dérivées de l’applicationId : aucun conflit avec l’installation habituelle.

## Protection des données

Android installe le debug Sound Pads dans un package distinct : il ne met pas à jour MusiMio stable et ne reprend pas ses préférences, fichiers internes ou permissions. Les mises à jour suivantes de ce debug mettent à jour uniquement l’application de test.

Lors de son premier setup, choisir un **dossier de bibliothèque de test distinct** si un dossier externe est demandé. Deux applications peuvent accéder au même dossier externe si on leur donne cette autorisation : le suffixe ne clone pas et ne protège pas un dossier partagé. Préparer des copies de morceaux pour les tests. Ne pas choisir la bibliothèque de concert, ni restaurer une sauvegarde vers celle-ci.

L’ancienne application de prototype `com.patrick.lrcreader.soundpadsprototype.labo` peut encore être présente ; cette configuration la remplace dans le workflow, sans la désinstaller automatiquement.

## Vérification

Les deux APK debug sont compilés avec les tâches normales du projet. Les manifestes et ressources release sont générés pour contrôler identifiants et labels sans publier ni installer de release. Les APK debug sont vérifiés pour le package et le label du lanceur.

Coexistence vérifiée sur les deux émulateurs disponibles : installation du nouveau package à côté de `com.patrick.lrcreader.exo.labo`, puis contrôle du chemin et de l’empreinte de l’APK existant. Aucun effacement, remplacement ou désinstallation du package existant. Aucun appareil physique connecté ; la recette manuelle téléphone/tablette reste à effectuer avec Run. Cette vérification porte sur l’isolation de l’installation, pas sur la latence acoustique.

Le périmètre du prototype et son accès debug restent inchangés. Aucune fonction audio ajoutée par cette configuration.
