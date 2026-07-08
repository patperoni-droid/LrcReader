# Coordination Audio SMP

## Principe

SMP est constitue de plusieurs moteurs audio independants.

Chaque moteur possede :

- son propre cycle de vie ;
- son propre volume ;
- sa propre logique metier.

Les moteurs ne partagent jamais leurs reglages.

## Moteurs actuels

### Playback

Lecture des morceaux.

Reglage :

Gain de piste.

### Fond sonore

Ambiance d'attente.

Reglage :

Volume du Fond sonore.

### DJ

Lecture DJ.

Reglage :

Volume DJ.

## Regle fondamentale

A un instant donne, un seul moteur audio principal peut etre actif.

Le demarrage d'un moteur principal entraine automatiquement l'arret du moteur principal precedemment actif.

Exemples :

- lancement Playback -> arret Fond sonore
- lancement Playback -> arret DJ
- lancement DJ -> arret Playback
- lancement DJ -> arret Fond sonore

Aucun melange involontaire entre moteurs principaux n'est autorise.

## Exception

Le Fond sonore constitue une ambiance d'attente.

Lorsque plus aucun moteur principal n'est actif, il peut reprendre automatiquement si l'utilisateur a active cette option.

## Bus Principal

Le Bus Principal constitue le cockpit audio de SMP.

Il permet de visualiser et regler simultanement :

- le gain Playback ;
- le volume Fond sonore ;
- le volume DJ.

Ces trois reglages restent totalement independants.

## Architecture

Le PlaybackControl ne possede jamais de volume propre.

Il pilote le reglage fourni par l'ecran qui l'utilise.

Exemples :

- Player -> Gain Playback
- LEVELS -> Gain Playback
- Bibliotheque -> Gain Playback
- Fond sonore -> Volume Fond sonore
- DJ -> Volume DJ

## Philosophie SMP

Les moteurs audio sont independants.

Le Bus Principal offre une vue unifiee de ces moteurs sans fusionner leurs responsabilites.

Toute future fonctionnalite audio devra respecter ces regles.
