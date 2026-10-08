# Sound Pads — Roadmap, coût et décisions

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


Statut : **étude documentaire ; aucun développement autorisé dans cette mission**.
3 octobre 2026. [Cahier des charges](SOUNDPADS_CAHIER_DES_CHARGES.md) · [Architecture et inventaire](SOUNDPADS_ARCHITECTURE.md).

> **Périmètre courant : V1 simple Media3, sans export/sauvegarde ni PCM.**
> Le complément en section 9 remplace le budget de livraison V1 des sections 2–4.
> Le budget initial 17–27 jours reste présenté pour expliquer ses hypothèses ; il ne
> s’applique plus à la V1 simple demandée. Aucune implémentation engagée.

## 1. Conclusion de faisabilité

V1 réalisable sans grosse refonte. UI et persistance : difficulté moyenne. Déclenchement réellement rapide, préparation bornée et coexistence avec le Player : difficulté moyenne à élevée. Le projet fournit Media3, du décodage WAV/PCM, une waveform, des faders et des exemples de coordination ; il ne fournit pas un moteur de pads déjà prêt et mesuré.

Le fader global **Pads du Bus sonore est inclus en V1**, avec persistance et multiplication par le volume individuel. L’intégration réutilise MixerChannelColumn/MixFader et les patrons de contrôleurs/prefs, sans reconstruire le Bus.

## 2. Hypothèses de l’estimation

Un développeur connaissant Kotlin/Compose et le projet, journées de 7 heures effectives. Chiffrage d’ingénierie fondé sur le code local inspecté, pas devis ferme ni performance déjà constatée. Une seule banque, one-shot, un seul pad audible à la fois en V1, liste extensible sans éditeur de pagination, éditeur IN/OUT simple, FR/EN/ES. Lecture au-dessus du Player si politique approuvée, pas de refonte DJ/Arrangement. Banque et originaux inclus dans sauvegarde/restauration globale. Deux appareils de référence disponibles, qualification à chaud sur sortie intégrée/filaire ; Bluetooth évalué séparément.

Aucune contrainte arbitraire de 6/12 fichiers ; préparation limitée par un budget de RAM et de voix défini après mesures. Les très longues sources/énormes banques restent une incertitude : si Media3 échoue à la cible et si le backend PCM ne tient pas le budget, annoncer l’état non prêt et traiter la stratégie mémoire, sans cacher une limitation sous « 12 pads maximum ».

## 3. Charge V1 détaillée

| Lot | Travail concret | Jours |
| --- | --- | ---: |
| Qualification audio | Comparer Media3 préparé/retrigger/clip, mesurer latence, Stop, ressources et coexistence ; choisir backend | 1,5–2,5 |
| Modèle/store/import | IDs, liste extensible, copie interne, validation, transactions, crash/rollback, erreurs | 2–3 |
| Moteur V1 Media3 | Préparation bornée, voix exclusive, IN/OUT, génération des commandes, arrêt/cycle de vie | 2–3 |
| UI/navigation | Grille 6/12, édition nom/son/volume/IN/OUT, états, icône directe, téléphone/Split tablette, langues | 2,5–4 |
| Coordination live | Politique Player/DJ/Filler, focus, niveaux, interruptions et non-régression | 1–2 |
| **Bus sonore Pads** | Quatrième tranche sur les routes actuelles, contrôleur/prefs, gain individuel × global, mise à jour live et restauration du niveau | **1–2** |
| Sauvegarde/restauration | Banque + assets, versions, restauration transactionnelle et anciennes sauvegardes | 1,5–2,5 |
| Validation et corrections | Tests ciblés utiles + recette réelle téléphone/tablette, latence, codecs, mémoire, navigation et faders | 2,5–3 |
| **Sous-total** | Backend Media3 conforme aux mesures | **14–22** |
| Réserve d’incertitude | Variabilité codecs/routes, changements locaux, retours ergonomie et bugs de préparation | 3–5 |
| **Budget V1 conseillé** | Base complète avec fader Pads et sauvegarde | **17–27 jours (119–189 h)** |

Le lot Bus inclut la petite adaptation de disposition à quatre faders et les tests de gain/persistance. Son coût incrémental est **1–2 jours**, hors vrai vu-mètre et refonte graphique. Il ne double pas le lot coordination ou le lot validation générale.

Si Media3 ne respecte pas la cible : backend PCM à sortie persistante, gestion de buffers/canaux/taux et allocation de voix : **+5–9 jours nets** après réutilisation du diagnostic et du décodage existant. Budget prudent correspondant : **22–36 jours (154–252 h)**. Les 6/12 pads ne permettent pas à eux seuls de prédire la RAM : longueur des extraits et format PCM dominent.

Ordre de grandeur calendaire pour une personne à temps plein : environ 4–6 semaines scénario Media3 ; 5–8 semaines scénario PCM, hors attente des appareils/décisions. Pas de coût monétaire inventé : `coût = jours × tarif journalier`, ou heures × taux horaire fourni. L’incertitude doit être réduite après qualification audio avant un devis ferme.

## 4. Ce qui peut être fait rapidement plus tard

- Grille statique 6/12, icône et formulaire local : 1–2 jours de prototype, insuffisant pour déclarer une V1 utilisable en concert.
- Première chaîne copie interne → pad sauvegardé → lecture simple, avec fader Pads élémentaire : démonstrateur en environ 3–5 jours au total. Ce travail est inclus dans le devis V1, pas à additionner ; pas de garantie de latence/robustesse à ce stade.
- Champ pitchSemitones neutre, UUID et liste extensible : très faible supplément si présents dès le modèle initial.
- Vrai temps court de lancement, stops répétés sans voix fantôme, sauvegarde complète et comportement live : demandent préparation et recette, pas seulement des boutons.

Si la coexistence avec le Player est reportée avec un refus clair lorsque toute autre source joue, environ 1–2 jours peuvent être évités sur coordination/recette. Le fader Pads et la copie durable restent obligatoires. Une V1 sans sauvegarde réduit environ 1,5–2,5 jours mais expose la banque à la perte lors de désinstallation/transfert ; cette variante n’est pas la recommandation et doit être acceptée explicitement, pas présentée comme sauvegardée.

## 5. Risques avec conséquences et protections

| Risque | Conséquence | Traitement ciblé |
| --- | --- | --- |
| Latence Media3 ou recréation AudioTrack | Pad ressenti lent | Mesurer après état prêt ; sortie prépréparée/persistante, décision backend au premier jalon |
| SoundPool utilisé sans qualification durée | Sons tronqués | Ne pas l’utiliser comme backend universel ; limite documentée dans architecture |
| PCM de longue durée | RAM/disque élevés, OOM | Budget explicite et préparation par zone/banque ; pas tout charger sans borne |
| Décodage pendant le morceau | Glitches et chauffe | Préparation hors live ; pad indisponible tant que non prêt |
| Double application de la courbe du bus | Fader et niveau incohérents | Convention unique UI u / gain u³, multiplication une seule fois, tests numériques |
| Fader chargé seulement à l’ouverture du Bus | Premier son trop fort | Chargement avant toute voix ; réglage global partagé et persistant |
| Quatre faders trop serrés | Mauvaise manipulation téléphone/Split | Adapter disposition à largeur utile, vraie recette tactile |
| Coordination exclusive actuelle | Appui pad coupe le morceau | Couche d’effets explicite, jamais détourner requestStartPlayer/Dj/Filler |
| Addition Player + pads | Saturation ou niveau surprenant | Politique de headroom, écoute et mesure, volumes indépendants |
| Import interrompu/remplacement | Fichier perdu ou référence invalide | Publication transactionnelle, ancien pad conservé, récupération |
| Cache confondu avec original | Son disparu après nettoyage | Originaux filesDir ; cache réservé aux dérivés |
| OUT par polling | Dépassement et effet coupé tard | Clip préparé ou limite frames PCM |
| Source AAC/WAV atypique | Import accepté puis échec lecture | Décodage réel avant prêt ; jeux de fichiers représentatifs |
| Arbre local en cours de modification | Inventaire dérive avant code | Refaire état Git/diff ciblé avant commencer ; préserver changements existants |
| Référence pad supprimée/collision ID | V3 déclenche mauvais son ou rien | IDs stables, refus/alerte explicite et transport de dépendances |

## 6. Décisions à prendre avant implémentation

1. Confirmer V1 one-shot exclusive entre pads et comportement du réappui. La polyphonie est préparée dans l’API, pas développée.
2. Confirmer l’usage par-dessus le Player et matrice DJ/Fond sonore ; comportements Stop principal/changement de morceau/perte de focus.
3. Retenir cible de latence et appareils/routes de référence. Décider backend seulement après mesures du futur prototype.
4. Confirmer éditeur trim simple versus waveform interactive. L’ajout d’une extraction visuelle minimale et d’un éditeur waveform complet peut coûter **+1–3 jours** ; pas de détournement de WaveformPreviewScreen.
5. Définir politique de ressources pour sources longues et readiness d’une grande banque. Une obligation « tout prêt à tout instant sans budget » demanderait un devis supplémentaire.
6. Confirmer banque sauvegardée en V1, traitement collisions et transfert téléphone/tablette ; préférence globale Pads sauvegardée ou locale seulement.
7. Confirmer courbe du fader Pads `u³`, niveau initial 1 et disponibilité suivant l’édition. La persistance locale du global est obligatoire, même si son export reste une décision.
8. Choisir icône/libellé final et grille tablette dans le Split selon largeur utile ; pas de refonte du menu pour ce chantier.

## 7. Phases futures et portes de validation

### Maintenant — documentation uniquement

Livrer les trois documents et inscrire l’étude au backlog sans priorité arbitraire. Aucun Kotlin, ressource, Gradle ou moteur modifié. Aucun test sur appareils, commit ou push requis pour cette étude.

### V1 — après validation de l’architecture et nouvelle instruction de développement

A. Qualifier latence/backend et figer matrice audio, budgets et scope.
B. Modèle/store/import durable et récupération, sans toucher Player.
C. Moteur dédié et tests de commandes/IN/OUT/Stop.
D. Grille/navigation/éditeur + contrôleur et fader Pads persistant.
E. Sauvegarde/restauration et compatibilité.
F. Recette réelle, correction ciblée, documentation de comportement livré.

Protéger la version téléphone live avant modifications risquées selon les règles du dépôt. Ne pas lancer d’instrumentation sur l’installation utilisateur sans autorisation explicite : préférer émulateur ou appareil isolé et recette manuelle.

### V2 — pitch -12..+12 (hors V1)

Boutons -/+, préparation à vitesse constante, gestion availability native et qualités aux extrêmes, ressources et sauvegarde. Première estimation séparée : **5–10 jours**, davantage si traitement polyphonique en temps réel exigé. Le champ réservé évite une migration lourde ; il ne rend pas cette évolution gratuite.

### V3 — triggers timeline (hors V1)

Événements timeMs/padId, éditeur, dispatch sur position officielle, pad absent, seek/loop/transition, transport des dépendances. Estimation exploratoire **7–12 jours** hors précision musicale stricte et projection Arrangement avancée ; refaire diagnostic et mesures au moment venu. Pas de réutilisation directe d’un marqueur TEXT ni d’un index de grille.

Ajout de pads/pagination et polyphonie effective : évolutions distinctes à spécifier et chiffrer, sans leur attribuer automatiquement le nom V2 ou V3.

## 8. Validation à prévoir

Tests JVM ciblés : invariants trim, décodage du schéma/anciennes versions, conservation ID, récupération transactionnelle, composition volume (`0,5 × 0,4 = 0,2`), courbe une fois, Stop invalidant commandes tardives, round-trip backup. Compiler les flavors pertinentes après implémentation ; aucun build nécessaire pour ces fichiers Markdown.

**Téléphone et tablette réels indispensables** : latence appui/son et Stop, retriggers rapides, première lecture après préparation, fichiers MP3/WAV/M4A/AAC représentatifs, routes intégrée/filaire/USB/Bluetooth, chauffe et mémoire, Player/transitions/Define Next non perturbés, DJ/Fond sonore selon matrice. Mesurer la latence de bout en bout par capture adaptée, pas uniquement durée de l’appel trigger.

Recette UI réelle : portrait/paysage, petites largeurs, quatre faders, panneau droit Split, retour Lyrics, menus, noms longs, gros texte, état >12 pads. Recette données : source supprimée/permission révoquée, cache vidé, processus tué pendant import, relancement après fader à 0 et après fader intermédiaire, sauvegarde restaurée sur l’autre appareil. Aucun effacement des données utilisateur pour ces essais.

Critère de clôture V1 : cahier des charges satisfait, chiffres de latence enregistrés, aucune régression live observée dans la recette définie, risques résiduels annoncés. Les documents présents n’attestent aucun de ces résultats.


## 9. Complément — V1 simple Media3 demandée le 3 octobre 2026

### 9.1. Ce qui change dans l’estimation

Le budget 17–27 jours était un budget prudent de livraison avec préparation audio évolutive, coexistence live, sauvegarde/restauration, recette étendue et réserve. Il n’était pas le coût minimum d’une première version locale testable. Les tâches de qualification, moteur et validation y avaient aussi des marges partiellement recouvrantes : le prototype de qualification doit être réutilisé, ses mesures ne doivent pas être facturées une seconde fois à l’identique en recette. Le présent complément distingue les livrables et corrige ce manque de précision.

**Un seul pad à la fois était déjà l’hypothèse initiale.** Ce choix réduit fortement le coût par rapport à la polyphonie, mais ne produit pas une nouvelle économie de plusieurs jours par rapport au devis initial. Transposition et timeline étaient également déjà exclues de ce budget : les exclure à nouveau ne retranche aucun jour.

À l’inverse, l’ajout/modification/suppression devient explicitement demandé en V1 ; il est inclus ci-dessous. La pagination avancée ne l’est pas. 6 et 12 restent des nombres par défaut, sans limite du modèle.

### 9.2. Ventilation analytique de l’ancien budget

Journées de 7 heures ; décomposition indicative des fourchettes d’origine, pas relevé d’heures réellement travaillées.

| Poste initial | Indispensable pour la première version | Sécurisation / finition / optimisation au-delà du minimum | Reportable pour ce périmètre | Total initial |
| --- | ---: | ---: | ---: | ---: |
| Qualification audio | 0,5–1 j | 1–1,5 j : matrice appareils/routes approfondie | — | 1,5–2,5 j |
| Modèle/store/import | 1–1,5 j | 1–1,5 j : récupération poussée, cas de fichiers/banques extrêmes | — | 2–3 j |
| Moteur Media3 | 1–1,5 j | 1–1,5 j : pool/préparation bornée évolutive et stress prolongé | — | 2–3 j |
| UI/navigation | 1,5–2,5 j | 1–1,5 j : raffinement adaptatif/accessibilité large | — | 2,5–4 j |
| Coordination live | 0,5–1 j | 0,5–1 j : matrice DJ/Fond sonore et transitions étendue | — | 1–2 j |
| Bus Pads | 0,5–1 j | 0,5–1 j : adaptations et cas limites sur plusieurs surfaces | — | 1–2 j |
| Sauvegarde/restauration | — | — | 1,5–2,5 j | 1,5–2,5 j |
| Validation/corrections | 1–1,5 j | 1,5 j : couverture live/routes/codecs plus large | — | 2,5–3 j |
| **Sous-total** | **6–10 j** | **6,5–9,5 j** | **1,5–2,5 j** | **14–22 j** |
| Réserve initiale | — | 3–5 j, risque non affecté à une tâche | — | 3–5 j |
| **Total initial** | | | | **17–27 j** |

Cette ventilation explique l’ancien chiffre. Le nouveau devis n’est pas une simple soustraction : réduction de la stratégie audio, formulaire minimal et réutilisation du prototype changent le mode de réalisation. Certaines vérifications restent indispensables malgré leur classement antérieur dans un grand lot « validation ».

### 9.3. Estimation de la V1 réduite

| Tâche et livrable exact | Basse | Réaliste | Haute |
| --- | ---: | ---: | ---: |
| A. Prototype Media3 réutilisable + mesures sur les deux appareils : fichier local, clip IN/OUT, premier toucher, alternance de pads, réappui, Stop ; intégrer ensuite cette base au moteur final | 4 h | 6 h | 10 h |
| B. Modèle versionné et persistance : UUID, liste non bornée à 6/12, nom/volume/IN/OUT, pitch neutre réservé ; écriture atomique simple, chargement et erreur sans écrasement | 3 h | 4 h | 6 h |
| C. Import et opérations : sélecteur, copie en filesDir hors UI, validation taille/durée/ouvrabilité, remplacement sans perte de l’ancien, ajout et suppression confirmée, nettoyage du fichier supprimé hors lecture | 4 h | 6 h | 9 h |
| D. Écran et édition : grille par défaut 6/12, ajouter/éditer/supprimer, nom, deux temps IN/OUT numériques, volume, prêt/chargement/erreur, lecture tactile, Stop ; débordement simple pour davantage de pads | 5 h | 8 h | 12 h |
| E. Fader Bus Pads : tranche existante, niveau global persistant chargé avant lecture, multiplication individuelle × globale, modification pendant lecture, Stop de tranche ; couvrir les routes Bus actuellement accessibles | 4 h | 6 h | 9 h |
| F. Navigation et cycle de vie : icône directe, restauration de route, panneau droit tablette et retour Lyrics, lecteur indépendant, priorité/focus, annulation des appuis périmés, FR/EN/ES ; politique audio minimale | 4 h | 6 h | 9 h |
| G. Tests ciblés et documentation : limites trim, persistance/gain et Stop tardif ; documenter fonctionnement et absence d’export ; compilation après développement | 2 h | 3 h | 5 h |
| H. Recette finale et corrections : import/source supprimée, relancement, quatre faders, portrait/paysage/Split, répétitions/Stop et non-régression audio ; re-mesurer seulement ce qui a changé depuis A | 5 h | 8 h | 12 h |
| **Total, sans réserve ajoutée** | **31 h** | **47 h** | **72 h** |
| **Jours à 7 h** | **4,4 j** | **6,7 j** | **10,3 j** |

Présenter au planning : **basse 4–5 jours ; réaliste 6–7 jours ; haute 10–11 jours**. Haute = difficultés raisonnables de Media3/import/UI et corrections sur les appareils, pas développement PCM. Si Media3 est rejeté pour latence, ces chiffres ne garantissent pas une V1 conforme : terminer le rapport de mesure et suspendre la livraison audio, sans lancer un moteur lourd implicitement.

Hypothèses : développeur déjà familiarisé avec MusiMio ; appareils disponibles et mesure de bout en bout réalisable ; aucun changement conflictuel majeur dans le code local ; formulaire simple ; une seule voix pads ; banque locale ; aucun export/sauvegarde/sync ; aucun nouveau vu-mètre. Coexistence principale chiffrée : pads au-dessus du Player, sans altérer sa timeline ; refuser le déclenchement lorsque DJ ou Fond sonore jouent plutôt que modifier leur logique. La coexistence **Pads + Player est confirmée par l’utilisateur** dans ce complément. Le refus quand DJ ou Fond sonore jouent reste une proposition minimale à confirmer ; ne pas les couper implicitement. Perte de focus arrête les pads sans reprise automatique. La V1 ne garantit pas le maintien de la lecture pads en arrière-plan avec un nouveau service.

Les postes A/H sont distincts : A décide de la viabilité et fournit la base audio ; H teste le produit intégré et corrige ses défauts. Un défaut corrigé dans A n’est pas rechiffré artificiellement dans H. La colonne haute contient l’aléa normal ; ne pas lui ajouter automatiquement la réserve de 3–5 jours de l’ancien budget.

### 9.4. Indispensable même dans une version courte

- Copie durable indépendante de la source, I/O hors thread UI, ancien son conservé si remplacement échoue.
- Persistance correcte et identités stables ; pas de plafond de 6/12 dans le stockage.
- Ajout/modification/suppression fonctionnels, suppression confirmée et aucune suppression d’un son encore actif.
- IN/OUT réellement appliqués par Media3, plages invalides refusées ; pas seulement deux nombres affichés.
- Lecteur pads distinct du singleton Player ; un toucher ne coupe jamais implicitement le morceau.
- Stop sûr annulant tout lancement tardif ; pas de son relancé par un callback devenu périmé.
- Global Pads chargé avant première lecture, gain appliqué une fois et aucun effet sur les autres bus.
- Mesure réelle sur téléphone/tablette et recette de base ; pas de promesse « instantané » issue de la seule durée d’appel play().
- Compilation et contrôles ciblés après futur développement, FR/EN/ES suivant les règles du projet.

Écriture atomique simple et conservation de l’ancien fichier ne sont pas des finitions à supprimer. La récupération exhaustive de tous les scénarios de crash, la déduplication et l’outillage de maintenance peuvent attendre.

### 9.5. Reports proposés

| Version | Livrables reportés | Effet / compromis |
| --- | --- | --- |
| V1.1 | Export, sauvegarde/restauration et transfert de banque | V1 survit au redémarrage/mise à jour normale, mais pas à désinstallation/effacement des données ; ne pas annoncer que la sauvegarde actuelle protège les pads |
| V1.1 | Waveform interactive et ajustement visuel fin des marqueurs | V1 : saisie numérique IN/OUT et écoute par pad, sans extraction waveform |
| V1.1 | Pagination élaborée, drag/reorder, densité personnalisable | V1 : bouton Ajouter et grille avec défilement ; aucune entrée au-delà de 12 ne disparaît |
| V1.1 | Pool/préchargement sophistiqué, optimisations grandes banques | V1 : lecteur Media3 unique réutilisé ; latence au changement de fichier mesurée et éventuellement jugée insuffisante |
| V1.1 | Matrice élargie DJ/Fond sonore, arrière-plan, routes USB/Bluetooth variées, stress long | Usage validé sur appareils/routes effectivement testés uniquement ; aucun arrêt caché des autres sources |
| V2 ou chantier distinct | Plusieurs voix pads, nouveau backend si nécessaire | Aucun mixage/polyphonie implémenté en V1 ; modèle et commandes simples ne dépendant pas d’un index UI |
| V2 | Transposition ±12 | Champ neutre conservé, aucun DSP ni interface de transposition |
| V3, inchangée | Événements timeline | IDs stables conservés ; aucun dispatcher ou transport de dépendances ajouté |

La finition essentielle du quatrième fader sur les deux formats reste dans V1. Un rendu sophistiqué, animations, vu-mètre réel et couverture de tous les formats atypiques n’y entrent pas.

### 9.6. Media3 : expérience minimale et limite technique

Hypothèse économique : **une instance Media3 propre aux pads**, réutilisée pendant la session, un seul pad audible. Changement de pad = nouveau MediaItem local clippé, préparation et lecture ; réappui sur le même pad = retour au début du clip et lecture, selon les mesures. Après Stop, conserver les ressources utiles seulement si cela ne laisse aucun son/commande en attente. Pas de lecteur par pad, de pool de 12 décodeurs ou de WAV préparé systématiquement.

Media3 fournit le clipping start/end et un état prêt à lire ; ces capacités ne garantissent pas la latence d’un autre fichier qui doit être préparé. Un `MediaItem` construit à l’avance n’est pas un audio déjà décodé. Réutiliser une instance n’assure donc pas 50 ms sur chaque toucher. Références : [Media items et clipping](https://developer.android.com/media/media3/exoplayer/media-items), [états Player](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).

Mesurer séparément :

1. Premier toucher après ouverture/initialisation.
2. Retour sur le même pad après lecture ou Stop.
3. Alternance A→B→A, fichiers différents et IN non nul.
4. Appuis rapides puis Stop pendant préparation : aucune commande tardive audible.
5. Même séquence pendant le Player si coexistence retenue, fader global à 0 puis intermédiaire.

Sur chaque appareil : p50/p95/max, échecs, sortie intégrée/filaire et sensation d’usage ; relever latence physique quand possible, et distinguer les mesures logicielles. Utiliser 50 ms comme cible exploratoire de l’étude précédente, pas garantie de cette variante. L’utilisateur doit pouvoir juger acceptable le résultat mesuré. Si non acceptable : comparer seulement des ajustements simples Media3 dans le temps de qualification ; documenter ensuite options/coût, sans PCM imposé.

### 9.7. Ce qui consomme le plus de temps et réutilisation

L’incertitude dominante est le moteur **au changement de fichier**, puis les parcours téléphone/tablette et la recette intégrée. La copie du fichier et le calcul de volume sont simples ; les erreurs d’import, appuis pendant préparation et règles de navigation demandent davantage d’attention.

Réutilisation directe : dépendance Media3, composants Material/Compose et styles ; composant de tranche du Bus dans son fichier actuel ; conventions de navigation BottomTabs/MainActivity et tokens SmpAdaptive. Patrons réutilisés avec adaptation locale : PlayerBusController/PlayerVolumePrefs pour le global ; sélection SAF/conventions MIME d’ImportAudioManager pour la copie ; écriture atomique des stores existants. L’API ImportAudioManager actuelle vise un dossier externe, donc elle ne peut pas être appelée telle quelle pour remplir filesDir.

Ne pas mobiliser pour cette V1 : SoundTouch, SamplerEngine, rendu WAV/PCM, WaveformPreviewScreen, stores timeline ou codecs de backup. Aucun utilitaire générique ou refactor de grande ampleur nécessaire. Garder padId et pitchSemitones=0 coûte peu ; construire dès V1 un système complet de voix futures ne se justifie pas.

### 9.8. Sous une semaine : objectif raisonnable mais conditionnel

Une semaine = 5 × 7 h = **35 heures effectives**. Le scénario bas (31 h) tient dans cette durée ; le scénario réaliste (47 h) la dépasse d’environ 12 h. La bonne cible est une **V1 locale testable**, avec mesures enregistrées, pas une garantie de maturité concert.

Périmètre confirmé : **les pads doivent jouer pendant un morceau du Player**. La variante entre morceaux seulement n’est donc pas retenue et aucune économie liée à son retrait n’est comptée. Pour approcher une semaine : UI minimale sans réglages de densité, réorganisation ou waveform ; protocole de mesures concentré sur les deux appareils et sorties intégrée/filaire, incluant le morceau actif ; correction limitée aux défauts bloquants du périmètre. Ces choix avec absence de difficultés particulières rapprochent le travail de **31–35 h**, sans réduction artificielle des protections de données/Stop. Ce n’est pas une réduction garantie de 47 à 35 h : elle dépend aussi des résultats Media3 et de l’intégration actuelle.

Ordre de travail proposé pour ce scénario favorable :

- Jour 1 : prototype réutilisable + premières mesures, décision poursuivre Media3.
- Jour 2 : persistance/import et opérations de banque.
- Jour 3 : grille/édition/navigation minimale.
- Jour 4 : fader persistant, cycle de vie, finalisation intégration.
- Jour 5 : recette sur les deux appareils, corrections bloquantes et rapport de latence.

Ce sont des regroupements de la colonne basse, pas cinq promesses indépendantes. La coexistence pendant un morceau étant confirmée, conserver le scénario principal 6–7 jours réalistes ; ne pas masquer son retrait pour tenir une date. Si le premier test Media3 révèle une latence gênante, consacrer d’abord le temps à l’établir, pas à compléter une UI qui prétendrait résoudre le problème.

Conclusion de ce complément : **viser 4–5 jours pour une première version favorable, prévoir 6–7 jours pour le périmètre simple complet ; 10–11 jours si difficultés raisonnables.** Aucun moteur PCM dans ce budget et aucune implémentation réalisée pendant cette analyse.


## 10. Variante comparative demandée — pads uniquement en solo

La variante principale reste Pads + Player confirmée. L’utilisateur demande aussi le coût de l’alternative solo ; ce complément compare les options sans remplacer silencieusement son choix précédent.

### 10.1. Contrat solo proposé

Une seule voix pads, et aucune autre source audio audible simultanément. Si Player, DJ, Fond sonore ou aperçu Arrangement est actif, toucher un pad est refusé avec explication ; ne pas arrêter implicitement le morceau. Si le Player/DJ/Fond sonore démarre ensuite, Stop pads avant la prise de contrôle. Pas de reprise automatique d’un pad après arrêt de l’autre source. Tout démarrage tardif de préparation pads est invalidé si une autre source prend le contrôle.

Le Fond sonore armé ne doit pas se lancer sous un pad : intégrer l’activité pads à ses conditions de démarrage automatique et au coordinateur, plutôt que vérifier seulement isMainPlaying au toucher. Un Player en pause est un état à traiter explicitement : s’il est officiellement inactif et aucune autre voix ne joue, pad autorisé ; sa reprise arrête le pad. L’implémentation doit s’appuyer sur l’autorité audio réelle et les portes du coordinateur, pas sur l’écran visible. Cette logique demande encore du travail ; solo ne signifie pas zéro coordination.

Les autres exigences sont identiques : ajout/modification/suppression, copies durables, 6/12 par défaut et davantage possibles par défilement, IN/OUT, volume individuel × global Pads persistant, Stop sûr, Media3 seulement, sans export/pitch/timeline.

### 10.2. Chiffrage solo, même unité et mêmes tâches

| Poste | Basse | Réaliste | Haute |
| --- | ---: | ---: | ---: |
| A. Qualification Media3, deux appareils, sans superposition Player | 3 h | 5 h | 8 h |
| B. Modèle/persistance | 3 h | 4 h | 6 h |
| C. Import/ajout/remplacement/suppression | 4 h | 6 h | 9 h |
| D. Grille/formulaire/IN/OUT/Stop | 5 h | 8 h | 12 h |
| E. Fader Pads persistant et gain | 4 h | 6 h | 9 h |
| F. Navigation/langues/cycle de vie et coordination solo | 2 h | 3 h | 5 h |
| G. Tests ciblés/compilation/documentation | 2 h | 3 h | 5 h |
| H. Recette finale et corrections, sans mix simultané | 4 h | 6 h | 10 h |
| **Total** | **27 h** | **41 h** | **64 h** |
| **Jours à 7 h** | **3,9 j** | **5,9 j** | **9,1 j** |

Synthèse : **solo basse 4 jours, réaliste 6 jours, haute 9–10 jours**, contre **avec Player 4–5 / 6–7 / 10–11 jours**. Économie nette estimée **4 / 6 / 8 heures** selon scénario, principalement qualification/coexistence/recette. Ce n’est pas une division par deux ; la grille, les données et le fader restent identiques. Ni l’un ni l’autre scénario ne garantit le respect de la cible de latence avant mesure. Aucune réserve supplémentaire ni coût PCM inclus.

### 10.3. Une semaine et compromis

Solo rapproche la V1 d’une semaine : **27–35 h dans une réalisation favorable**, avec formulaire numérique, défilement simple et mesures sur les deux appareils. Son estimation réaliste reste **41 h**, environ six jours : ne pas promettre 35 h avant le prototype Media3.

Compromis principal : impossible d’ajouter un effet pendant un morceau, ce qui retire une partie de l’usage musical et impose une évolution avant la future timeline V3. On gagne des tests de mixage et de coexistence, pas le temps d’import ni la latence au changement de fichier. Le fader Pads conserve toute son utilité pour ajuster les sons joués entre morceaux.

Passer de solo à Pads + Player pourra représenter environ **1–2 jours supplémentaires** lors d’une V1.1 (extension de coordination et recette, sans changer les données/écran), davantage si le prototype révèle des soucis sous charge. C’est une estimation de migration séparée ; l’économie de six heures du devis initial n’est pas le prix garanti d’une évolution ultérieure.

Pour prioriser le test de Media3 au moindre coût, solo convient à un premier démonstrateur. Pour une V1 destinée dès maintenant à ponctuer un morceau, conserver Pads + Player : l’écart réaliste est d’environ une journée et évite de repousser cette capacité pourtant demandée.
