# Sound Pads — Patch 3, écran téléphone et tablette

4 octobre 2026. Branche `feature/soundpads`. Écran plein écran de performance, direction des deux références fournies, en conservant Media3, une voix, banque/UUID et fichiers du Patch 2. Le Patch 4 Bus sonore n’est pas commencé.

## Accès et navigation

Android Studio : **app → laboDebug → Run**. Dans **MusiMio Sound Pads Test** :

- téléphone : menu ⋮ → **Sound Pads — test audio** ;
- tablette : ⚙️ du cockpit → **Sound Pads — test audio**.

Cet accès debug ouvre maintenant `SoundPadsScreen`, pas un dialogue. Retour par la flèche en haut à gauche ou Retour Android. L’état de destination survit à la recréation de l’Activity ; la banque est rechargée depuis le même stockage. La fermeture arrête seulement la voix Pads et libère son instance Media3.

L’intégration suit la navigation locale existante de MainActivity : plein écran devant la destination précédente, sans changer l’onglet ou reconstruire ses lecteurs. Barre inférieure et chrome de cockpit sont masqués pendant cette destination. Les touches ne traversent pas vers les contrôles précédents ; les raccourcis clavier de la route précédente sont neutralisés pendant cet écran. Le retour réaffiche le contexte précédent. Aucune orientation système n’est modifiée.

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
- Pas de fader global ici : son intégration appartient au Bus sonore au Patch 4. Le réglage provisoire interne du moteur reste celui du prototype, sans nouvelle source de volume/persistance parallèle.

## Fichiers de ce patch

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

## Validation exécutée

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

## Recette manuelle avant validation du patch

1. Run laboDebug depuis Android Studio. Ouvrir le nouvel écran par ⋮/⚙️. Les pads du Patch 2 doivent être retrouvés avec leurs mêmes fichiers et paramètres.
2. Vérifier les 6 emplacements téléphone, les 12 tablette en portrait/paysage autorisés par MusiMio, et le retour au contexte précédent. Vérifier confort de lecture et hauteur des pads avec la taille de texte habituelle.
3. Appui court et réappuis rapides ; observer le contour/LED. Stop pendant lecture et juste après une rafale. Refaire pendant un morceau, puis DJ et Fond sonore : ils doivent continuer.
4. Appui long sur un pad au repos : réglages sans aucun son. Long sur un pad en lecture : aucun nouveau déclenchement ; utiliser Stop de l’éditeur si nécessaire.
5. Modifier nom, volume, IN/OUT et couleur ; Enregistrer. Tester OUT vide, extrait 1000–3000 ms sur un fichier assez long, refus d’un OUT inférieur à IN/dépassant la durée. Tester sans enregistrer doit jouer le brouillon valide.
6. + ou emplacement Libre : ajout vide au bon endroit, puis import. Fichier source déplacé/supprimé après import : la copie reste lisible. Remplacement invalide/annulé : ancien fichier préservé.
7. Supprimer, annuler d’abord puis confirmer. Fermer complètement/relancer : paramètres, couleurs et suppression conservés.
8. Banque de plus de 6/12 : défiler pour retrouver tous les pads. Aucun effacement ou import de banque n’est nécessaire pour cette recette.
9. Sur appareil réel, vérifier précision de l’appui long, clavier/scroll de feuille, portrait/paysage tablette, réactivité audio et coexistence. La qualification acoustique reste celle du moteur déjà validé par le Créateur ; aucune nouvelle garantie de latence mesurée n’est inventée.

Pas de passage automatique au Patch 4 après cette livraison.
