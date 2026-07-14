# FEATURE — WAVEFORM

## Objectif

Waveform permet de preparer un morceau en visualisant sa forme d'onde et en ajustant ses marqueurs de travail, notamment IN et OUT.

L'ecran Waveform est un outil de preparation et de controle local. Il ne remplace pas le Playback officiel SMP et ne modifie pas son architecture.

## Mode Split tablette

En mode tablette Split, Waveform est integre au cockpit live :

- le panneau gauche contient la playlist ;
- le panneau droit affiche l'editeur Waveform ;
- la playlist reste disponible pendant l'utilisation de Waveform.

Lorsqu'un morceau est selectionne dans la playlist du Split :

- Waveform se met immediatement a jour ;
- la forme d'onde du morceau selectionne est chargee ;
- les informations du morceau sont chargees ;
- les marqueurs IN / OUT existants sont charges ;
- le lecteur local Waveform est prepare.

Une simple selection ne declenche jamais de lecture.

## Selection et lancement

Dans Waveform, selectionner un morceau et lancer sa lecture sont deux actions distinctes.

La selection prepare uniquement Waveform. Elle peut provenir de la playlist Split tablette et met a jour l'editeur sans produire de son.

Le demarrage de la lecture Waveform reste toujours une action volontaire de l'utilisateur via le bouton Play du bloc de lecture Waveform.

## Bloc de lecture Waveform

Waveform possede un bloc de lecture visuellement harmonise avec le PlaybackControl utilise dans le Player.

Ce bloc reste independant du PlaybackControl officiel :

- il conserve la logique interne de Waveform ;
- il pilote uniquement le lecteur local Waveform ;
- il ne modifie pas le Playback actif SMP ;
- il conserve les commandes propres a Waveform, dont Stop et Retour au debut.

Le bouton Play peut indiquer qu'un morceau est pret a etre lance. Dans cet etat, aucune lecture n'a encore demarre.

## Lecture exclusive

LRC Reader ne doit jamais produire deux lectures audio simultanees.

Regle metier :

> A tout instant, une seule source audio peut produire du son.

Concretement :

- une simple selection dans la playlist ne modifie jamais la lecture en cours ;
- si le Playback officiel SMP est en cours, il continue pendant la preparation Waveform ;
- lorsqu'un utilisateur clique sur Play dans Waveform, le Playback officiel est d'abord arrete s'il est actif ;
- le lecteur local Waveform demarre ensuite.

L'utilisateur ne peut donc pas obtenir simultanement le Playback officiel SMP et la lecture locale Waveform.

