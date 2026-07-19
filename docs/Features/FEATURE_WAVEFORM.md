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

- il devient la selection officielle de la playlist SMP ;
- Waveform se met immediatement a jour ;
- la forme d'onde du morceau selectionne est chargee ;
- les informations du morceau sont chargees ;
- les marqueurs IN / OUT existants sont charges ;
- le lecteur local Waveform est prepare.

Une simple selection ne declenche jamais de lecture et n'interrompt jamais le Playback principal actif.

## Selection et lancement

Dans Waveform, selectionner un morceau, le lancer dans le Playback principal et l'ecouter dans le lecteur local Waveform sont trois actions distinctes.

La selection peut provenir de la playlist Split tablette. Elle prepare simultanement :

- le titre cible du Playback principal officiel ;
- le contenu de l'editeur Waveform ;
- le lecteur local d'apercu Waveform.

Cette preparation ne produit aucun son.

## PlaybackControl officiel — tablette

En mode Split tablette, Waveform affiche le vrai `PlaybackControl` SMP.

Il conserve exactement la meme mission et le meme comportement que dans les autres ecrans tablette :

- il pilote exclusivement le Playback principal actif ;
- il utilise la selection officielle de la playlist gauche ;
- son bouton Play devient jaune lorsque le titre selectionne differe du titre actif ;
- un seul appui sur le bouton Play jaune lance immediatement le titre selectionne dans le Playback principal ;
- le titre precedemment actif n'est pas arrete au moment de la selection, mais uniquement lorsque le lancement est confirme ;
- la commande Pause reste separee du bouton Play.

Le PlaybackControl officiel n'est pas ajoute a l'ecran Waveform du telephone. Le comportement historique du telephone reste inchange.

## Bloc de lecture Waveform

Waveform conserve un bloc de lecture local pour ecouter et controler la zone de travail de la forme d'onde.

Ce bloc reste independant du PlaybackControl officiel :

- il conserve la logique interne de Waveform ;
- il pilote uniquement le lecteur local Waveform ;
- il ne modifie pas le Playback actif SMP ;
- il conserve les commandes propres a Waveform, dont Stop et Retour au debut.

Lorsque les deux controles sont visibles sur tablette, le bloc local doit etre clairement identifie comme un controle d'apercu et ne doit pas reproduire l'etat jaune du PlaybackControl officiel.

Le demarrage de la lecture locale Waveform reste toujours une action volontaire de l'utilisateur via le bouton Play du bloc d'apercu Waveform.

## Lecture exclusive

LRC Reader ne doit jamais produire deux lectures audio simultanees.

Regle metier :

> A tout instant, une seule source audio peut produire du son.

Concretement :

- une simple selection dans la playlist ne modifie jamais la lecture en cours ;
- si le Playback officiel SMP est en cours, il continue pendant la preparation Waveform ;
- lorsqu'un utilisateur clique sur Play dans le bloc d'apercu Waveform, le Playback officiel est d'abord arrete s'il est actif ;
- le lecteur local Waveform demarre ensuite.
- lorsqu'un utilisateur lance le Playback officiel depuis le vrai PlaybackControl, le lecteur local Waveform est d'abord arrete s'il est actif ;

L'utilisateur ne peut donc pas obtenir simultanement le Playback officiel SMP et la lecture locale Waveform.
