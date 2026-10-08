# Sound Pads — Cahier des charges

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

## Historique de conception et du prototype

**Les sections ci-dessous conservent l’étude et les constats datés du 3 octobre.** Leurs mentions « non implémenté », « validation physique en attente », propositions de moteur lourd et budgets initiaux ne décrivent pas la V1 actuelle. Les décisions actuelles ci-dessus et le rapport Patchs 3/4 priment. V2/V3 restent des perspectives.

> **État courant au 3 octobre 2026 : étape 1 prototype Media3 compilée et vérifiée sur émulateur.**
> Pads autorisés au-dessus du Lecteur, du DJ **et** du Fond sonore, décision utilisateur
> plus récente que les variantes d’étude ci-dessous. Leurs règles d’exclusion entre eux
> sont conservées. Interface V1 complète et validation physique encore en attente.
> [Résultats, limites et protocole appareils](SOUNDPADS_PROTOTYPE_TESTS.md).


Statut : **conception uniquement, aucune fonctionnalité implémentée**. Analyse du 3 octobre 2026.
Nom provisoire : Sound Pads. [Architecture](SOUNDPADS_ARCHITECTURE.md) · [Roadmap et coût](SOUNDPADS_ROADMAP.md).

> **V1 courante : variante simple Media3 définie au complément de la roadmap.**
> Ajout/modification/suppression inclus ; pas d’export/sauvegarde, de transposition,
> de timeline, de waveform interactive ou de backend PCM. Les pads doivent pouvoir
> jouer pendant le morceau principal, confirmé par l’utilisateur. Les réserves V2/V3
> du modèle sont conservées. Les exigences détaillées ci-dessous s’appliquent avec
> les précisions du complément en section 10.

## 1. But et périmètre

Déclencher manuellement des sons locaux depuis une grille accessible directement parmi les icônes du menu principal MusiMio. Réutiliser les parcours, le style et les bibliothèques présents ; aucune refonte du Player, du DJ, de l’Arrangement ou de la navigation globale.

V1 : choix du son, copie durable, édition du nom/volume/IN/OUT, déclenchement rapide, arrêt des pads et persistance après redémarrage. V2 et V3 sont réservées dans la conception, jamais développées avec V1.

## 2. Écran et navigation

- Téléphone : 6 emplacements par défaut, proposition 2 colonnes × 3 lignes en portrait.
- Tablette : 12 emplacements par défaut, proposition 4 × 3 en plein écran ; adapter au panneau droit du Split et à sa largeur réellement disponible.
- **6 et 12 sont des valeurs de présentation et d’initialisation, jamais des tailles de tableau, contraintes de stockage ou limites du moteur.**
- Un emplacement vide propose de choisir un audio ; un pad configuré affiche son nom et son état (préparation, prêt, lecture, erreur).
- Accès à l’édition distinct du déclenchement, pour éviter une modification accidentelle en concert.
- Bouton permanent « Arrêter les pads », accessible même pendant un chargement. Il arrête seulement les voix des pads et leur aperçu.
- Navigation hors de l’écran ne doit pas couper un son involontairement. Au retour, afficher l’état réel du moteur.
- En Split tablette : Playlist fixe, Sound Pads dans le panneau droit, retour visible vers Playlist | Lyrics. Conserver le parcours téléphone.
- À terme : réorganisation, réduction raisonnable des pads puis pagination. Préserver une cible tactile d’au moins 48 dp ; ne pas réduire indéfiniment pour tout faire tenir.
- V1 inclut les commandes Ajouter, Modifier et Supprimer (suppression confirmée), mais pas la pagination avancée. La liste persistée et le rendu doivent accepter plus de 12 entrées ; test avec 25 entrées et débordement accessible, sans troncature silencieuse.
- Changer d’orientation ou restaurer sur un autre appareil ne recrée ni ne supprime des pads. Le nombre par défaut ne s’applique qu’à une banque nouvelle.

## 3. Données de chaque pad

| Champ conceptuel | Contrat |
| --- | --- |
| padId | Identifiant stable, opaque, indépendant du nom, du fichier et de l’ordre |
| name | Nom affiché non vide, éditable |
| assetId / audioRelativePath | Référence à la copie interne, jamais à la source externe |
| volume | Facteur linéaire 0..1, valeur proposée 1 ; indépendant du gain du morceau |
| inMs | Début inclusif, millisecondes, valeur 0 |
| outMs | Fin exclusive ; null signifie fin du fichier |
| pitchSemitones | Entier, valeur 0 en V1 ; réserve V2 -12..+12 |
| order | Ordre d’affichage indépendant de padId |

Durée et empreinte/version du fichier sont des métadonnées techniques. Une banque versionnée contient une liste de pads et d’emplacements libres, sans champ « limite 6/12 ».

Invariant pour un son prêt : `0 <= inMs < effectiveOutMs <= durationMs`. Refuser une plage vide ou inversée ; ne pas lancer le fichier entier à la place d’un trim invalide. Remplacer le son conserve padId, réinitialise IN/OUT après confirmation explicite et ne publie le changement qu’après import réussi.

## 4. Import durable

Sélectionner via le sélecteur Android un fichier accessible depuis le stockage local, carte SD, USB ou un fournisseur de documents. « N’importe où » signifie accessible au sélecteur et autorisé par Android, pas accès aux espaces privés d’autres applications. Un fournisseur cloud peut nécessiter une connexion pendant la copie ; aucune connexion requise ensuite.

Copier les octets dans `filesDir/soundpads/`, hors du thread principal. Ne pas déplacer/modifier la source. Ne jamais faire de l’URI d’origine, d’une autorisation persistante SAF ou de `cacheDir` une dépendance du pad après publication. Un espace temporaire peut servir à préparer la transaction, mais le son original et les réglages doivent rester dans le stockage persistant.

Après succès : supprimer/déplacer la source, retirer la carte ou révoquer sa permission doit laisser le pad utilisable, y compris après redémarrage. Le stockage privé survit à la fermeture, au redémarrage et à la mise à jour normale ; désinstallation ou effacement des données le supprime. La sauvegarde doit être traitée explicitement, sans promesse de conservation après désinstallation.

Formats ciblés selon le filtrage existant : MP3, WAV, M4A, AAC ; FLAC et OGG peuvent être acceptés si le pipeline réel les décode. L’extension seule ne garantit pas le codec. Contrôler l’ouverture, la durée et le décodage avant l’état prêt ; erreur localisée et conservation de l’ancien pad si échec. Pas de promesse pour tous les profils WAV/AAC, fichiers DRM ou corrompus.

## 5. Lecture V1 proposée

Hypothèse de chiffrage, à valider avant développement : lecture one-shot, sans boucle, un seul pad audible à la fois ; un nouvel appui redémarre le pad depuis IN et remplace la voix précédente. Le contrat moteur manipule néanmoins des identifiants de voix, afin d’ajouter la polyphonie ensuite.

La copie est effectuée avant le déclenchement. La variante simple utilise le clipping Media3 et la préparation du média sans transformation PCM ni waveform ; mesurer les délais au changement de fichier, qui peuvent inclure sa préparation. Un pad en import ou en erreur est indisponible avec feedback. Un toucher sur un fichier importé lance sa préparation Media3 puis sa lecture ; une nouvelle commande remplace la précédente, sans accumuler d’appuis qui partiraient plus tard. Stop invalide toute commande en attente pour empêcher un redémarrage tardif. OUT doit être appliqué dans la source audio préparée ou au niveau des frames, pas par une minuterie Compose.

Objectif proposé, **non mesuré** : p95 appui → première sortie audible <= 50 ms sur sortie intégrée/filaire et appareils de référence, à chaud ; publier p50/p95/max et taux d’échec. Premier lancement après état prêt inclus. Bluetooth mesuré séparément : sa latence ne peut pas être annulée par le moteur. Stop proposé p95 <= 50 ms sur mêmes routes ; mesurer le reliquat des buffers. L’objectif et les appareils doivent être approuvés avant de déclarer la fonction « rapide ».

## 6. Coexistence audio : décision nécessaire

Le coordinateur actuel rend Player, DJ et Fond sonore mutuellement exclusifs. Les pads ne doivent jamais emprunter les commandes ou volumes du Player.

Proposition pour la vocation musicale et V3 : couche d’effets autorisée au-dessus du Player, sans changer son autorité temporelle ; politique explicite pour DJ et Fond sonore. Cette extension de coordination exige une décision d’architecture et des tests, elle n’est pas déjà acquise. Alternative V1 : pads disponibles seulement sans source principale active, avec refus visible ; elle réduit le coût mais limite l’usage. Ne jamais arrêter implicitement le morceau principal au toucher d’un pad.

## 7. Évolutions réservées

### V2 — Transposition par pad

`pitchSemitones = 0` présent dès V1 ; aucune commande de pitch active. V2 : boutons -/+ par demi-ton, plage envisagée -12..+12, conserver idéalement la vitesse. Le pitch du Player et Sync Pitch ne pilotent jamais les pads. Évaluer SoundTouch par traitement indépendant ; toute valeur non neutre requiert préparation valide et validation de qualité/latence. Ne pas utiliser un changement de vitesse comme équivalent de transposition à durée constante.

### V3 — Déclenchement par timeline

Un événement d’une SongUnit référence `padId`, jamais un fichier : `timeMs = 123250 → padId = "17"` (ID d’exemple ; un UUID stable est recommandé). Les pads sont globaux ; les événements appartiennent au morceau. Déclenchement piloté par le temps officiel du Player, même quand l’écran Sound Pads est fermé. Définir seek, pause, boucle, changement de morceau, doublons et pad absent. Préparer les sons référencés avant Play. Transporter les dépendances et préserver/remapper les IDs lors d’un transfert.

## 8. Acceptation V1

1. Icône directe, retours corrects, 6/12 par défaut, pas de perte sur rotation/relancement.
2. Liste >12 représentable et conservée, sans plafond métier.
3. Nom, volume et IN/OUT persistés ; pitch neutre réservé.
4. Copie indépendante de la source et du cache ; import interrompu sans perte du pad précédent.
5. Lecture du seul intervalle choisi, répétition rapide fiable, Stop sans voix fantôme.
6. Mesures de latence et mémoire sur téléphone et tablette ; pas de glitch du Player, des transitions ou du DJ selon politique retenue.
7. V1 simple : aucun export ni sauvegarde des pads, conformément au périmètre demandé. Cette absence est documentée ; ne pas annoncer que les sauvegardes existantes incluent les pads.
8. Libellés FR/EN/ES, état prêt/erreur intelligible, arrêt accessible.

Aucune de ces validations n’a été effectuée sur une implémentation Sound Pads.

## 9. Bus sonore — exigence V1 confirmée

Ajouter une tranche **Pads** avec fader global dans le Bus sonore, sur le même principe visuel que Lecteur, DJ et Fond sonore. Elle fait partie de V1, pas d’une évolution ultérieure.

- Chaque pad conserve son volume individuel ; bouger le fader global ne réécrit aucun de ces volumes.
- Niveau final logiciel : `volumeIndividuel × gainGlobalPads`, avant volume système et éventuels fades de sécurité. Si le fader utilise la courbe cubique existante, `gainGlobalPads = positionFader³`.
- Fader 0 : aucun pad audible ; fader 1 : respect du volume individuel. Exemple : pad 0,5 et gain global 0,4 → gain final 0,2.
- Le niveau global est persistant, rechargé avant toute lecture, y compris sans ouvrir le Bus. Valeur initiale proposée : position 1.
- Les changements s’appliquent aux pads déjà en cours et aux futurs déclenchements, sans redémarrer les sons.
- La tranche Pads ne modifie aucun niveau Lecteur/DJ/Fond sonore. Son bouton de tranche, si présent conformément au Bus actuel, arrête uniquement les pads.
- Ajouter les pads au Bus ne décide pas à lui seul de leur coexistence avec les autres sources.
- Vérifier les quatre tranches sur téléphone, tablette et panneau Split ; conserver leurs accès et cibles tactiles.

Recette supplémentaire : niveau global conservé après arrêt forcé/redémarrage, moteur initialisé au bon niveau avant premier appui, multiplication appliquée une seule fois, son individuel inchangé après déplacement du fader, silence à 0, arrêt dédié et aucune incidence sur les autres faders. Le niveau global relève des préférences d’appareil comme un réglage de bus ; le transport dans une sauvegarde doit être explicite, distinct de la banque musicale.


## 10. Complément de périmètre — V1 simple

Périmètre confirmé le 3 octobre 2026 : ajout/modification/suppression, copie interne durable, édition numérique IN/OUT, volumes individuel et global persistant, lecture au toucher/Stop, un pad à la fois, **pads audibles pendant un morceau du Player**. Le nouvel appui remplace la voix pads précédente, sans toucher au morceau.

Aucun export, sauvegarde de banque, transposition, timeline ou moteur PCM développé en V1. Source interne et réglages restent persistants localement ; désinstallation/effacement des données supprime cette banque sans mécanisme de récupération livré. La section 7 reste une conception future uniquement.

Grille 6/12 par défaut ; bouton Ajouter et défilement simple dès V1 pour davantage d’entrées, sans plafond technique. Suppression vide l’emplacement ou enlève une entrée ajoutée selon la disposition ; elle ne change pas l’identité des autres pads. Un remplacement garde padId et ne perd pas l’ancien fichier en cas d’échec.

« Lecture au toucher » signifie une commande directe, sans étape de confirmation. Sa latence audible doit d’abord être mesurée sur les appareils de l’utilisateur, notamment en alternant différents pads pendant un morceau. La cible exploratoire 50 ms n’est pas une garantie. Si le résultat Media3 est gênant, fournir le constat et discuter des options, sans ajouter automatiquement un moteur PCM.

Qualification et recette locale incluses ; pas de déclaration « validé en concert » sans recette correspondante. Budget révisé : [roadmap, section 9](SOUNDPADS_ROADMAP.md#9-complément--v1-simple-media3-demandée-le-3-octobre-2026).
