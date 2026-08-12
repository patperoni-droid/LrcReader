# Utiliser le Lecteur et ses commandes

Le Lecteur pilote l’audio principal de Stage Music Player. Il synchronise également les paroles, les accords et les événements temporels du morceau actif.

## Ouvrir un morceau

Vous pouvez lancer un morceau depuis :

- une playlist ;
- la Bibliothèque ;
- la Recherche ;
- une famille de versions.

Le Lecteur prépare le morceau live correspondant, applique ses réglages puis démarre l’audio.

## Commandes principales

Le Playback Control officiel regroupe les commandes de lecture. Selon l’écran et l’appareil, il propose :

- **Play** : lancer ou reprendre la cible préparée ;
- **Pause** : mettre en pause ou arrêter la préécoute ciblée selon l’outil ;
- **Retour au début** : revenir à `00:00` ;
- **Précédent / Suivant** : naviguer selon le contexte ;
- une barre de progression et la durée ;
- le gain rapide du morceau sur certaines dispositions.

Les positions des commandes restent aussi stables que possible afin de conserver les mêmes gestes sur scène.

## Bouton Play vert ou jaune sur tablette

### Play vert

La sélection de la playlist correspond au morceau actif. Le bouton contrôle ce morceau.

### Play jaune

Vous avez sélectionné un autre morceau sans interrompre le morceau actif.

1. vérifiez le titre sélectionné ;
2. appuyez une fois sur le bouton jaune ;
3. le nouveau morceau est lancé par le parcours normal ;
4. il devient la source active pour l’audio, les paroles et la Timeline.

## Play, Pause et retour au début

- **Play** démarre la lecture à la position préparée.
- **Pause** conserve généralement la position du morceau principal.
- **Retour au début** remet le morceau à zéro et replace les paroles sur la première ligne.

Dans les outils Arrangement, le même Playback Control peut temporairement piloter un segment. Le comportement ciblé est alors indiqué par l’outil.

## Se déplacer dans le morceau

La barre de progression permet de changer de position. Le déplacement actualise les paroles et les accords à partir du nouveau temps.

Évitez les déplacements répétés pendant une transition automatique ou l’exécution d’événements MIDI/DMX, sauf si ce comportement a été testé pendant la préparation.

## Afficher les paroles ou les accords

Le Lecteur propose deux contenus synchronisés :

- **Paroles** ;
- **Accords**.

Le changement d’affichage ne recharge pas l’audio et ne change pas sa position. Consultez [Afficher les paroles et les accords](10-paroles-et-accords.md).

## Régler le gain du morceau

Le gain est mémorisé pour chaque morceau.

- Sur tablette, un fader peut rester visible dans le panneau droit.
- Sur téléphone, une poignée sur le bord droit peut ouvrir un tiroir de gain.

La plage active actuelle va de `-24 dB` à `+6 dB`. Un niveau positif élevé peut provoquer de la saturation selon le fichier et le système de sonorisation.

Lorsque vous changez de morceau, le fader doit afficher le gain mémorisé pour le nouveau titre.

## Réglages du morceau

La Track Console donne accès, selon l’édition et la configuration, à :

- LEVEL ;
- vitesse ;
- pitch ;
- égaliseur ;
- indicateur de niveau.

Les réglages sont non destructifs : ils ne modifient pas le fichier audio d’origine.

## Points IN et OUT

Un morceau peut posséder :

- un point **IN** pour commencer après le début du fichier ;
- un point **OUT** pour terminer avant sa fin.

Ces points sont préparés dans Waveform ou dans les outils de réglage compatibles. Vérifiez-les avant d’utiliser Auto Play ou une transition.

## Morceau suivant

Le Lecteur affiche le prochain titre lorsqu’il est connu. Celui-ci peut provenir :

- de l’ordre de la playlist ;
- de **Définir comme prochain** ;
- d’une liste live ;
- d’un groupe live rouge.

Si le titre suivant n’est pas lisible ou semble incorrect, ne comptez pas sur l’enchaînement automatique avant d’avoir corrigé la playlist.

## Transitions et crossfade

Une transition peut utiliser un fondu dont la durée est réglable dans **Paramètres / Plus**.

Lorsque le morceau actuel ou le suivant utilise une vitesse ou un pitch non neutre, Stage Music Player privilégie une transition séquentielle sûre plutôt qu’un chevauchement audio complexe.

Le résultat peut donc différer d’un crossfade normal, afin d’éviter un second lecteur audible ou des réglages hérités du mauvais morceau.

## Retour automatique à -10 secondes

L’option **Retour auto vers la playlist (10 s avant la fin)** ramène l’affichage vers la sélection sans interrompre la musique. Elle sert à préparer le prochain morceau.

Elle peut être activée ou désactivée dans **Paramètres / Plus**.

## Mode d’ouverture du Lecteur

Trois comportements sont disponibles :

- **Toujours** : le Lecteur s’ouvre à chaque lancement ;
- **Jamais** : la lecture démarre sans changer automatiquement d’écran ;
- **Automatique** : le Lecteur s’ouvre si des paroles sont disponibles.

Le mode Toujours est le choix le plus simple pour commencer.

## Priorité des sources audio

Le morceau principal a la priorité sur le Fond sonore. Lorsqu’un morceau démarre, le Fond sonore s’arrête automatiquement.

Le DJ utilise également un moteur séparé. Les indicateurs de navigation permettent d’identifier la source audio active.

## Problèmes courants

### Un autre morceau est sélectionné mais ne démarre pas

Sur tablette, appuyez sur le bouton Play jaune pour confirmer son lancement.

### Le volume change entre deux morceaux

Vérifiez le gain enregistré pour chacun et préparez leurs niveaux avant le concert.

### Les paroles ne suivent plus après un déplacement

Revenez au début, puis vérifiez les horodatages dans l’éditeur Synchro.

### Une transition ne fait pas de chevauchement

Le pitch ou la vitesse de l’un des morceaux peut imposer une transition séquentielle plus sûre.

### Le morceau ne commence ou ne finit pas au bon endroit

Vérifiez ses points IN/OUT dans Waveform.

Chapitre suivant : [Afficher les paroles et les accords](10-paroles-et-accords.md).
