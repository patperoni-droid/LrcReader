# Coordination Audio SMP

## Principe

Ce document décrit la coordination officielle entre :

- le Playback principal ;
- le lecteur Fond sonore.

La source de vérité fonctionnelle du Fond sonore est `FEATURE — BACKGROUND SOUND`.

SMP sépare strictement ces deux moteurs audio.

Chaque moteur possede :

- son propre cycle de vie ;
- son propre volume ;
- sa propre logique metier.

Les moteurs ne partagent jamais leurs reglages.

Ils ne partagent jamais non plus :

- leurs commandes ;
- leur état de lecture ;
- leur position ;
- leur logique de navigation.

Seule la coordination de priorité les relie.

## Moteurs actuels

### Playback principal

Lecture des morceaux.

Controleur :

PlaybackControl.

Reglage :

Gain de piste.

### Fond sonore

Ambiance d'attente.

Controleur :

Commandes locales du lecteur Fond sonore.

Reglage :

Volume du Fond sonore.

## Regle fondamentale

Le Playback principal possede toujours la priorite.

Tout demarrage d'un Playback principal provoque immediatement l'arret du Fond sonore.

Le Fond sonore ne doit jamais empecher le lancement d'un morceau principal.

Le Fond sonore ne doit jamais arreter ou remplacer le Playback principal.

Il assure uniquement une continuite sonore lorsque le Playback principal n'est plus actif.

## Etat arme

Lorsque le Fond sonore est active pendant qu'un morceau principal joue :

- il reste arme ;
- il ne demarre pas immediatement ;
- il attend que le Playback principal devienne inactif selon les regles officielles.

Lorsque le Playback principal devient inactif et que le Fond sonore est arme, le Fond sonore peut demarrer automatiquement.

Le Fond sonore peut aussi etre demarre manuellement lorsqu'aucun Playback principal n'est actif, notamment avant le concert pour choisir ou ecouter une ambiance.

## Arret automatique

Le Fond sonore s'arrete automatiquement lorsqu'un nouveau Playback principal demarre.

Cette regle est systematique.

Exemples :

- lancement Playback -> arret Fond sonore

Aucun melange involontaire entre Playback principal et Fond sonore n'est autorise.

## Controleurs

PlaybackControl pilote exclusivement le Playback principal.

Cette regle reste valable dans tous les ecrans, y compris dans l'ecran Fond sonore.

PlaybackControl ne change jamais de mission selon l'ecran.

Le lecteur Fond sonore possede ses propres commandes locales :

- activation ON/OFF ;
- lecture ;
- pause ou arret selon l'ergonomie retenue ;
- retour debut si disponible ;
- choix du dossier ;
- volume.

Ces commandes ne pilotent jamais le Playback principal.

## Bus Principal

Le Bus Principal constitue le cockpit audio de SMP.

Il permet de visualiser et regler simultanement :

- le gain Playback ;
- le volume Fond sonore.

Ces reglages restent totalement independants.

## Architecture

Le PlaybackControl ne possede jamais de volume propre.

Il pilote uniquement le reglage du Playback principal.

Exemples :

- Player -> Gain Playback
- LEVELS -> Gain Playback
- Bibliotheque -> Gain Playback
- Bus Principal -> Gain Playback

Le volume Fond sonore est pilote par les commandes locales du lecteur Fond sonore ou par le Bus Principal lorsqu'il expose ce reglage.

Il n'est jamais pilote par PlaybackControl.

## Philosophie SMP

Les moteurs audio sont independants.

Le Bus Principal offre une vue unifiee de ces moteurs sans fusionner leurs responsabilites.

Toute future fonctionnalite audio devra respecter ces regles.

## Évolutivité

Ce document constitue la règle d'architecture audio de SMP.

Tout futur moteur audio (Arrangement, Métronome, Cues, etc.) devra :

- définir son propre cycle de vie ;
- posséder son propre réglage de niveau ;
- respecter les règles de coordination décrites dans ce document ;
- ne jamais contourner le coordinateur audio officiel.
