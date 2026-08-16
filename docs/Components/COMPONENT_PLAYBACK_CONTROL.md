# COMPONENT — Playback Control

## Mission

Le `Playback Control` est le centre de contrôle universel du **Playback principal actif** de SMP.

Il permet au musicien de contrôler immédiatement le morceau actuellement en lecture sans revenir systématiquement dans le Player.

Le Player reste l'écran complet.

Le `Playback Control` est un composant officiel, réutilisable et indépendant des écrans.

---

## Philosophie

Le `Playback Control` ne remplace jamais le Player.

Le Player conserve notamment :

- les paroles ;
- les accords ;
- la timeline détaillée ;
- Define Next ;
- les fonctions avancées.

Le `Playback Control` regroupe uniquement les commandes les plus utilisées pendant une répétition ou un concert.

Son objectif est de permettre au musicien de conserver le contrôle du concert sans changer d'écran.

En mode Live (tablette), SMP distingue volontairement deux notions :

- le morceau actuellement en lecture ;
- le morceau actuellement sélectionné.

Cette séparation permet de préparer le morceau suivant sans interrompre celui qui est en cours.

**La navigation prépare.**

**Le bouton Play confirme.**

Le mode actuel reste entièrement manuel. Le projet de bouton AUTO est suspendu et ne fait pas partie du composant implémenté.

---

## Principes SMP

- composant officiel ;
- même ergonomie partout ;
- toujours reconnaissable ;
- compact ;
- priorité téléphone ;
- compatible tablette ;
- aucune duplication de logique.

Le `Playback Control` appartient à SMP et jamais à un écran particulier.

Il ne contrôle jamais un écran.

Il contrôle toujours le Playback principal actif.

Il ne contrôle jamais les moteurs audio complémentaires, notamment le Fond sonore.

Toute évolution du Playback Control doit être pensée comme une évolution d'un composant partagé
et transversal, jamais comme un correctif local d'un écran, sauf nécessité explicitement
démontrée.

👉 Stabilité live > fonctionnalité > esthétique.

---

## Architecture du composant

Le composant possède deux niveaux de responsabilité dans le code :

### `PlaybackControl`

Défini dans `app/src/main/java/com/patrick/lrcreader/ui/PlaybackControl.kt`, il constitue le
composant partagé de niveau supérieur.

Il assemble :

- `PlaybackProgressBar`, pour la progression, les temps, le seek et, selon le mode, la Structure ;
- `PlayerControls`, pour la rangée de commandes.

Il reçoit les états et callbacks du Playback actif : position, durée, seek, lecture, retour début,
gain, mode compact, mode console live et éventuels callbacks de Structure.

### `PlayerControls`

Défini dans `app/src/main/java/com/patrick/lrcreader/ui/PlayerControls.kt`, il contient réellement
la rangée des commandes :

- Play ou Play/Pause selon le mode ;
- Pause séparée en `liveConsoleMode` ;
- Retour début ;
- réglage rapide du gain.

`PlayerControls` est appelé par `PlaybackControl`. Il ne doit pas être copié ou réimplémenté dans
un écran pour résoudre un problème local. Une correction de sa géométrie ou de son état visuel
peut affecter tous les écrans qui utilisent le Playback Control officiel.

---

## Cartographie actuelle des usages

Le code utilise actuellement `PlaybackControl` dans les contextes suivants :

- Player officiel ;
- éditeur Paroles / Accords ;
- Timeline et Arrangement, via le Playback Control officiel fourni par le Player ;
- Track Console, via le même Playback Control officiel ;
- Bibliothèque, dans plusieurs vues utilisant le contrôle commun ;
- LEVELS ;
- Bus principal / console ;
- écran Fond sonore, où il pilote uniquement le Playback principal et non le lecteur local du
  Fond sonore ;
- Accordeur ;
- Waveform, pour le Playback principal officiel.

Les points de construction directs se trouvent dans :

- `PlayerScreen.kt` ;
- `LibraryScreen.kt` ;
- `MixerHomePreviewScreen.kt` ;
- `FillerSoundScreen.kt` ;
- `TunerScreen.kt` ;
- `WaveformPreviewScreen.kt`.

Une modification du composant partagé doit donc être évaluée dans toutes ces surfaces, même si le
besoin initial n'apparaît que dans un seul écran.

---

## Playback principal actif

À un instant donné, SMP possède un unique Playback principal actif.

Ce Playback principal peut avoir été démarré depuis :

- Lecteur Audio / Paroles ;
- Bibliothèque ;
- LEVELS ;
- Bus Principal ;
- Track Console ;
- toute autre interface autorisée.

Lorsqu'un nouveau morceau est lancé :

- il devient automatiquement le Playback actif ;
- le Playback précédent est arrêté conformément aux règles de coordination audio ;
- tous les Playback Control pilotent désormais ce nouveau Playback principal actif.

Le changement d'écran ne modifie jamais le Playback principal actif.

Le Fond sonore n'est pas un Playback principal.

Il possède son propre lecteur, son propre état et ses propres commandes locales.

Même sur l'écran Fond sonore, `Playback Control` conserve sa mission unique : piloter le Playback principal.

---

## Sélection

Le Playback actif et la sélection représentent deux états distincts du système SMP.

Ils peuvent être synchronisés.

Exemple :

- le morceau sélectionné est aussi le morceau actuellement en lecture.

Ils peuvent aussi être désynchronisés.

Exemple :

- un morceau continue de jouer ;
- le musicien prépare un autre morceau en le sélectionnant.

En mode Live tablette, toute méthode de sélection prépare uniquement un morceau.

Cette sélection peut provenir :

- d'un appui tactile sur la playlist ;
- de la Navigation Séquentielle.

Dans tous les cas :

- le morceau devient sélectionné ;
- le Playback actif continue normalement ;
- aucune lecture immédiate n'est déclenchée.

Cette séparation est volontaire.

Elle constitue le fondement du mode Live de SMP.

### États à ne jamais confondre

Le système distingue au moins trois notions indépendantes :

- la multi-sélection de playlist, utilisée pour des actions de groupe ou de lot ;
- la sélection préparée pour un futur appui manuel sur Play ;
- `PlaybackCoordinator.nextTrack`, qui représente le morceau explicitement défini comme prochain.

La multi-sélection ne constitue pas une vérité de lecture.

La sélection préparée ne constitue pas automatiquement un Define Next.

`PlaybackCoordinator.nextTrack` ne doit jamais être déduit de la multi-sélection, de la couleur du
bouton Play ou de l'état visuel d'une ligne de playlist.

---

## Responsabilités

Le `Playback Control` :

- ne modifie jamais la playlist ;
- ne déplace jamais la sélection ;
- n'invente jamais un morceau suivant hors de la sélection et de l'ordre officiel de la playlist.

Il agit uniquement sur le Playback actif et utilise la sélection fournie par la Navigation Séquentielle.

---

## Composition

### Téléphone

Le Playback Control reste volontairement compact.

Il comprend :

- barre de progression ;
- temps courant ;
- temps total ;
- Retour début ;
- bouton Lecture / Pause ;
- réglage rapide ;
- affichage de la valeur.

Cette ergonomie reste prioritaire.

Sur téléphone, le comportement historique est conservé.

Un appui sur un morceau de la playlist lance immédiatement sa lecture.

Cette règle privilégie la rapidité d'action sur un écran compact.

Le téléphone n'affiche pas de bouton AUTO et ne propose pas ce mode d'enchaînement dans le Playback Control.

Cette exclusion est volontaire afin de ne pas surcharger l'interface téléphone.

---

### Tablette (Mode Live)

La console tablette est conçue pour fonctionner **en complément de la Navigation Séquentielle**.

Elle sépare explicitement les commandes Play et Pause.

Elle comprend :

- barre de progression ;
- temps courant ;
- temps total ;
- Retour début ;
- bouton Play ;
- bouton Pause ;
- réglage rapide ;
- affichage de la valeur.

Le bouton Play reste un bouton Play permanent.

Il ne devient jamais un bouton Pause.

En mode Live tablette, la sélection prépare uniquement le morceau ciblé.

Elle ne déclenche jamais une lecture automatiquement.

La sélection peut provenir :

- d'un appui tactile sur la playlist ;
- de la Navigation Séquentielle.

Le Playback actif continue normalement tant que le musicien n'appuie pas explicitement sur Play.

Le bouton Play devient jaune lorsque la sélection ne correspond plus au Playback actif.

La Navigation Séquentielle reste un composant indépendant chargé de gérer la sélection.

Cette ergonomie est spécifique au mode Live.

---

## Bouton Play

Le bouton Play possède toujours la même responsabilité :

> lancer le morceau actuellement sélectionné.

Sa fonction ne change jamais.

Le lancement constitue toujours une décision explicite du musicien.

Le bouton Play ne déclenche jamais automatiquement une lecture.

Le bouton Play ne modifie jamais la sélection.

Il utilise simplement la sélection courante comme cible de lecture.

Sur tablette, le bouton Play reste toujours visible et conserve toujours sa signification.

Il ne devient jamais un bouton Pause, même lorsque le Playback actif est en lecture ou suspendu.

Sa couleur indique uniquement la relation entre :

- la sélection ;
- le Playback actif.

### Vert

Le morceau sélectionné correspond au Playback actif.

Lecture et sélection sont synchronisées.

L'état lecture ou pause du Playback actif ne transforme pas le bouton Play en bouton Pause.

### Jaune

Le musicien a sélectionné un autre morceau.

Le Playback actif continue normalement.

Cette sélection prépare un autre morceau sans déclencher de lecture automatique.

Le bouton Play indique qu'un nouveau morceau est prêt à être lancé.

Sa couleur dépend de l'état `liveSelectionInSync` transmis au composant. Elle indique
conceptuellement :

> Un appui manuel sur Play lancera la ligne actuellement préparée.

Elle ne signifie jamais :

> Ce morceau démarrera automatiquement à la fin du morceau courant.

Lorsque ce morceau est lancé, il devient le nouveau Playback actif et le bouton Play redevient vert.

---

## Define Next réel

Le véritable morceau explicitement défini comme prochain est porté par :

`PlaybackCoordinator.nextTrack`

Cet état est distinct :

- de la multi-sélection de playlist ;
- de la sélection préparée pour Play ;
- de `liveSelectionInSync` ;
- de la couleur jaune du bouton Play.

Un futur changement ne doit pas fusionner ces états pour simplifier l'interface. Le Playback Control
peut afficher une information issue de `PlaybackCoordinator.nextTrack`, mais cet affichage ne doit
jamais faire de l'état UI une source de vérité de lecture et ne doit pas modifier la résolution du
prochain morceau.

---

## Projet AUTO — suspendu

Le bouton AUTO n'est implémenté ni sur tablette ni sur téléphone.

Son étude est mise de côté tant que le comportement manuel du Playback Control, de la sélection officielle et des transitions n'est pas stabilisé sur tous les écrans concernés.

La documentation de conception conservée dans la roadmap V2.5 décrit uniquement une possibilité future. Elle ne constitue pas un comportement disponible.

---

## Bouton Pause

Le bouton Pause agit uniquement sur le Playback principal actif.

Sur tablette, il constitue une commande distincte du bouton Play.

Il suspend la lecture du Playback principal actif.

Il ne modifie jamais :

- la sélection ;
- la playlist ;
- la Navigation Séquentielle.

Si le musicien a préparé un autre morceau par la sélection, cette préparation est conservée.

---

## Bouton Retour

Le bouton Retour remet uniquement le Playback principal actif au début.

Il ne modifie jamais :

- la sélection ;
- l'état Lecture / Pause.

Comportement :

- lecture → retour à 0:00 et lecture conservée ;
- pause → retour à 0:00 et pause conservée ;
- aucun Playback actif → aucune action.

---

## Navigation Séquentielle

Les commandes ▲ ▼ ne déclenchent jamais une lecture.

Elles préparent uniquement la sélection dans la playlist.

En mode Live tablette, elles produisent le même résultat qu'un appui tactile sur un morceau :

- la sélection change ;
- le Playback actif continue ;
- le bouton Play devient jaune si un autre morceau est préparé.

Cette sélection peut être préparée pendant qu'un autre morceau continue de jouer.

Le lancement reste une décision explicite du musicien via le bouton Play.

---

## Réglage rapide contextuel

Le Playback Control contient une zone de réglage rapide du Playback principal.

```
[-] Valeur [+]
```

Le composant ne possède jamais de réglage propre.

Il pilote uniquement un réglage lié au Playback principal.

Exemples :

- Player → Gain Playback ;
- LEVELS → Gain Playback ;
- Bibliothèque → Gain Playback ;
- Bus Principal → Gain Playback ;
- Track Console → Gain Playback.

Le GainDrawer reste le réglage précis.

Le Playback Control permet un ajustement rapide.

Le volume du Fond sonore est réglé par les commandes locales du lecteur Fond sonore.

Le volume des autres moteurs audio n'est jamais piloté par Playback Control.

---

## Pipeline de gain stable

Le téléphone et la tablette utilisent exactement le même chemin de gain dans `AudioEngine`.

- un étage de gain léger est installé à la création de chaque Player principal ou de transition ;
- le passage autour de `0 dB` ne sélectionne jamais un autre pipeline Player ;
- une modification de gain ne doit jamais arrêter, libérer, reconstruire, préparer ou repositionner le Player actif ;
- le gain neutre ou négatif conserve le réglage de volume ExoPlayer existant ;
- le gain positif utilise uniquement l'étage de gain dédié ;
- le gain positif n'active jamais SoundTouch à lui seul ;
- la sélection du pipeline SoundTouch reste réservée à un pitch ou un speed non neutre ;
- le chemin neutre copie les données PCM dans un tampon détenu par le processeur avant de les transmettre, afin d'éviter toute réutilisation prématurée du tampon ExoPlayer ;
- le son neutre, le pitch, le speed et les passages `-1 / 0 / +1 dB` ont été validés sur appareil réel ;
- les futurs EQ, compresseur et limiteur restent hors de ce correctif de stabilisation du gain.

---

## Intégration Track Console

Track Console affiche le `Playback Control` officiel du Playback principal.

En mode Live tablette :

- la sélection reste pilotée par la playlist gauche ;
- sélectionner un autre titre ne coupe pas le titre actif ;
- le bouton Play devient jaune ;
- un appui sur Play lance le titre sélectionné avec le pipeline Playback officiel ;
- les commandes Lecture, Pause, Retour début, position et gain utilisent exactement les mêmes actions que dans le Player.

Track Console ne possède aucune copie locale de la logique Playback.

L'ancienne zone basse `TOUCH HERE TO RETURN` n'existe plus. La navigation hors de Track Console utilise les commandes permanentes de l'application, notamment la navigation supérieure du mode Split tablette.

---

## Intégration Timeline — implémentée

La Timeline utilise un seul contrôle principal de lecture : le `Playback Control` officiel du Playback principal.

Règles validées :

- la Timeline affichée appartient toujours au morceau actuellement actif dans le Playback principal ;
- sélectionner un autre morceau dans la playlist ne change pas la Timeline affichée et n'interrompt pas la lecture ;
- lorsque la sélection diffère du Playback actif, le bouton Play devient jaune selon la règle commune ;
- un appui sur Play jaune lance le morceau sélectionné avec le pipeline Playback officiel ;
- seulement après ce lancement, le nouveau morceau devient actif et sa Timeline remplace la précédente ;
- l'écran Timeline reste ouvert pendant ce changement de morceau ;
- le `Playback Control` reste placé en bas de l'écran Timeline, comme dans les autres écrans tablette ;
- les anciennes commandes locales Play, Pause et Retour de la Timeline ont été supprimées ;
- aucun lecteur secondaire, lecteur d'aperçu ou état audio propre à la Timeline ne doit être créé.

Cette intégration est implémentée et validée sur tablette.

---

## Intégration Arrangement — conception cible, mise en œuvre progressive

En mode Arrangement tablette :

- le `Playback Control` officiel reste fixé en bas de l'écran et toujours visible ;
- le contenu d'édition ne doit jamais le repousser hors de l'écran ;
- la Structure est présentée comme une piste horizontale de blocs juste au-dessus du composant ;
- la playlist est repliée par défaut mais peut être ouverte ou fermée dans un panneau latéral gauche interne à Arrangement ;
- ouvrir ou fermer la playlist redimensionne uniquement la fenêtre visible de la piste et ne change jamais la lecture, le titre actif, le zoom ou l'ordre des blocs ;
- si A est actif et B est seulement sélectionné dans cette playlist, le composant devient jaune et l'Arrangement A reste affiché ;
- B et son Arrangement ne deviennent actifs qu'après pression sur le Play jaune, via le pipeline Playback officiel ;
- le composant continue de contrôler le Playback principal ;
- la preview de Structure reste un moteur secondaire isolé et ne doit pas être présentée comme une variante du `Playback Control` officiel.

Cette intégration ne concerne pas le téléphone. Le Playback Control et l'écran Arrangement téléphone conservent leur disposition et leur comportement actuels.

La définition complète de la piste horizontale, des couleurs, de la playlist escamotable, des répétitions, du mute et de la duplication se trouve dans `docs/Features/FEATURE_TEMPO_ARRANGEMENT.md`.

---

## Principes UX

Le Playback Control doit :

- être immédiatement identifiable ;
- conserver une géométrie stable ;
- conserver les commandes au même emplacement ;
- conserver les mêmes commandes sur un même périphérique ;
- ne jamais changer la signification d'un bouton ;
- garder Play et Pause comme deux commandes distinctes en mode Live tablette ;
- utiliser les couleurs uniquement pour représenter un état ;
- rester utilisable sans apprentissage.

Toute évolution de la rangée de commandes doit également :

- éviter tout déplacement des commandes pendant le live ;
- réserver une largeur stable aux contenus dynamiques ;
- interdire le retour à la ligne dans la rangée ;
- éviter tout texte animé ou défilant susceptible de distraire ;
- éviter tout scroll automatique lié au Playback Control ;
- vérifier les contraintes horizontales avant d'ajouter un élément ;
- préserver le comportement téléphone portrait lorsque l'espace est insuffisant ;
- être validée sur les largeurs réellement concernées.

`liveConsoleMode` décrit un mode de présentation et de commande. Il ne faut pas supposer qu'il
signifie nécessairement « tablette physique » : certaines configurations larges ou en paysage
peuvent emprunter des branches adaptatives proches du mode tablette.

La mémoire musculaire du musicien est prioritaire.

En mode Live tablette, toutes les méthodes de sélection possèdent le même comportement.

La préparation d'un morceau est indépendante de son lancement.

Le lancement est confirmé directement avec Play.

Cette différence avec le téléphone est volontaire :

- le téléphone privilégie la rapidité ;
- la console Live tablette privilégie la sécurité.

---

## Champ Define Next dans la rangée — implémenté

Lorsque la largeur réelle du conteneur le permet en `liveConsoleMode`, la rangée insère entre Play
et Pause un champ de largeur fixe affichant uniquement :

`PlaybackCoordinator.nextTrack?.title`

Représentation conceptuelle :

`PLAY | L'Italiano… | PAUSE`

Règles implémentées :

- aucun libellé avant le titre ;
- aucun mot `Prochain`, `Next` ou `Suiv.` ;
- titre uniquement ;
- largeur fixe ;
- une seule ligne ;
- troncature avec `…` si nécessaire ;
- aucun défilement du texte ;
- aucune animation ;
- emplacement conservé et vide lorsqu'aucun `nextTrack` n'existe, afin que Play et Pause ne se
  déplacent jamais ;
- alimentation exclusive par le véritable `PlaybackCoordinator.nextTrack` ;
- interdiction d'utiliser la sélection préparée, `liveSelectionInSync` ou la multi-sélection pour
  alimenter ce champ ;
- aucun changement de logique audio, de playlist ou de résolution du prochain morceau.

Le champ mesure `96 dp`. Compose tronque dynamiquement le contenu selon cette largeur sans limiter
arbitrairement le nombre de caractères.

L'affichage dépend exclusivement de la largeur réelle fournie au `PlayerControls`, jamais de
l'orientation ou du type physique d'appareil :

- géométrie compacte : affichage à partir de `560 dp` disponibles ;
- géométrie standard : affichage à partir de `510 dp` disponibles ;
- sous le seuil correspondant : conservation stricte de la rangée existante, sans champ, sans
  réduction et sans réorganisation des commandes.

Ces seuils additionnent les dimensions actuelles des commandes, le champ de `96 dp`, les
espacements, l'enveloppe du gain affiché jusqu'à `-24 dB` et une marge anti-overflow de `16 dp`.

### Contraintes de largeur connues

La validation sur tablette physique établit les résultats suivants :

- conteneur large en paysage : champ vide et titre long tronqué validés sans overflow ajouté ;
- titre absent puis défini : positions de Play et Pause identiques ;
- conteneur étroit simulé à environ `372 dp` : champ correctement omis et rangée précédente
  conservée sans réorganisation ;
- les limitations horizontales préexistantes de cette rangée très étroite restent hors du
  périmètre de cette évolution ;
- tablette portrait : non validée, le cockpit testé maintenant son affichage paysage ;
- téléphone portrait et paysage : non validés sur appareil pendant cette évolution.

Toute évolution ultérieure devra continuer à vérifier au minimum :

- l'absence d'overflow horizontal ;
- la stabilité de la position de Play et Pause quand le titre change ou disparaît ;
- la disponibilité de Retour début et des contrôles de gain ;
- l'absence de modification de hauteur ;
- le Player, la Bibliothèque, LEVELS, le Bus principal, le Fond sonore, l'Accordeur, Waveform,
  Timeline, Arrangement et Track Console dans leurs dispositions concernées.

---

## Statut

**Statut : ARCHITECTURE VALIDÉE**

Le Playback Control constitue désormais la console officielle de pilotage du Playback actif.

En mode Live (tablette), il sépare volontairement :

- la préparation du morceau suivant ;
- le contrôle du morceau actuellement en lecture.

Sur téléphone, le comportement historique reste prioritaire.

Cette séparation constitue une règle officielle de l'architecture SMP.

---

## Règle fondamentale

En mode Live, SMP sépare volontairement :

- la préparation d'un morceau ;
- son exécution.

La préparation consiste à choisir ou déplacer la sélection.

L'exécution consiste à lancer effectivement le morceau.

Cette séparation garantit que le musicien conserve la maîtrise du lancement par validation manuelle avec Play.

---

## Point d'entrée pour une future intervention

Un agent intervenant sur Playback Control doit charger en priorité, dans cet ordre :

1. `docs/Components/COMPONENT_PLAYBACK_CONTROL.md` ;
2. `app/src/main/java/com/patrick/lrcreader/ui/PlaybackControl.kt` ;
3. `app/src/main/java/com/patrick/lrcreader/ui/PlayerControls.kt`.

Il ne doit ensuite ouvrir que les écrans appelants concernés par le problème ou la validation.

Avant toute modification, il doit déterminer si le changement concerne :

- la barre de progression ;
- la rangée de commandes ;
- le mode téléphone ;
- `liveConsoleMode` ;
- la sélection préparée ;
- le véritable `PlaybackCoordinator.nextTrack` ;
- ou uniquement un écran appelant.

Cette classification évite de transformer une correction locale en divergence du composant partagé.
