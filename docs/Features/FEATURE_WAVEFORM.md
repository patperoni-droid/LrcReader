# FEATURE — WAVEFORM

## Objectif

Waveform permet de preparer un morceau en visualisant sa forme d'onde et en ajustant ses marqueurs de travail, notamment IN et OUT.

L'ecran Waveform est un outil d'edition du titre selectionne. Il utilise exclusivement le Playback principal officiel SMP et ne possede aucun lecteur audio local ou secondaire.

## Mode Split tablette

En mode tablette Split, Waveform est integre au cockpit live :

- le panneau gauche contient la playlist ;
- le panneau droit affiche l'editeur Waveform ;
- la playlist reste disponible pendant l'utilisation de Waveform.

Lorsqu'un morceau est selectionne dans la playlist du Split :

- il devient la selection officielle de la playlist SMP ;
- Waveform se met immediatement a jour ;
- la forme d'onde du morceau selectionne est chargee ;
- les informations du morceau sont chargees ;
- les marqueurs IN / OUT existants sont charges ;
- les modifications IN / OUT peuvent etre effectuees et sauvegardees.

Une simple selection ne declenche jamais de lecture et n'interrompt jamais le Playback principal actif.

## Selection et lancement

Dans Waveform, selectionner un morceau et le lancer dans le Playback principal sont deux actions distinctes.

La selection peut provenir de la playlist Split tablette. Elle prepare simultanement :

- le titre cible du Playback principal officiel ;
- le contenu de l'editeur Waveform.

Cette preparation ne produit aucun son.

## PlaybackControl officiel

Waveform affiche le vrai `PlaybackControl` SMP. Il remplace entierement l'ancien lecteur d'apercu Waveform.

Il conserve exactement la meme mission et le meme comportement que dans les autres ecrans tablette :

- il pilote exclusivement le Playback principal actif ;
- il utilise la selection officielle de la playlist gauche ;
- son bouton Play devient jaune lorsque le titre selectionne differe du titre actif ;
- un seul appui sur le bouton Play jaune lance immediatement le titre selectionne dans le Playback principal ;
- le titre precedemment actif n'est pas arrete au moment de la selection, mais uniquement lorsque le lancement est confirme ;
- la commande Pause reste separee du bouton Play.

Sur telephone, le meme PlaybackControl pilote le Playback principal avec l'ergonomie telephone officielle.

## Curseur Waveform et Playback

Lorsque le titre affiche dans Waveform correspond au Playback principal actif :

- le curseur Waveform suit la position reelle du Playback ;
- un appui sur la forme d'onde deplace le Playback principal ;
- les commandes de navigation Waveform agissent sur le Playback principal ;
- les points IN / OUT restent editables et sauvegardables.

Lorsque le titre affiche dans Waveform differe du Playback principal actif :

- le Playback actif continue sans interruption ;
- Waveform permet d'editer les points IN / OUT du titre selectionne ;
- le curseur d'edition du titre selectionne reste independant de la position du titre actif ;
- aucun geste sur la forme d'onde ne doit deplacer le Playback d'un autre titre ;
- l'ecoute du titre selectionne commence uniquement apres confirmation avec le bouton Play jaune.

## Lecteur unique

Waveform ne cree, ne prepare et ne conserve aucun `ExoPlayer` secondaire.

Regle metier :

> Le Playback principal SMP est l'unique lecteur audio de Waveform.

Concretement :

- une simple selection dans la playlist ne modifie jamais la lecture en cours ;
- si le Playback officiel SMP est en cours, il continue pendant l'edition Waveform d'un autre titre ;
- le bouton Play du PlaybackControl utilise toujours le pipeline Playback officiel ;
- les paroles, accords, timeline, MIDI et DMX restent pilotes par ce meme Playback principal ;
- aucun moteur audio propre a Waveform n'existe.

## Chargement de la forme d'onde

- téléphone et tablette utilisent le même `WaveformPreviewScreen`
- l'ouverture utilise sur les deux appareils l'aperçu échantillonné persistant de 2 000 points
- le décodage complet de 20 000 points est chargé à la demande pour le zoom poussé et le trim automatique, uniquement lorsque la lecture principale est arrêtée
- le cache persistant évite de recalculer un aperçu ou une analyse complète déjà disponibles
- l'analyse MP3 préfère le décodeur logiciel adapté au travail hors ligne lorsqu'il est disponible
- la tête de lecture et les points IN/OUT restent calculés à partir de la durée en millisecondes, indépendamment de la résolution visuelle
