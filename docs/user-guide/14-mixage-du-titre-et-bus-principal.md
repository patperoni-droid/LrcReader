# Régler le mixage du titre et le Bus principal

MusiMio sépare les réglages propres à un morceau du mixage général des différentes sources audio. Cette séparation permet de préparer les titres à l’avance sans dérégler toute la prestation.

## Track Console : réglages du morceau

Ouvrez la Track Console depuis les outils du morceau ou le Lecteur. Elle présente :

- **LEVEL** : niveau mémorisé pour le morceau ;
- **SPEED** : vitesse de lecture, avec retour à `1,00x`.

Les cinq bandes **EQ** visibles sont pour l’instant un aperçu graphique : elles ne se règlent pas et ne modifient pas le son. LEVEL et SPEED ne réécrivent pas le fichier audio source.

## Régler un morceau

1. ouvrez le morceau dans le Lecteur ;
2. ouvrez sa Track Console ;
3. remettez SPEED à `1,00x` si vous ne souhaitez pas changer la vitesse ;
4. ajustez LEVEL par petites étapes ;
5. écoutez le début, une partie forte et la fin ;
6. passez à un autre morceau puis revenez pour vérifier la mémorisation.

La disponibilité ou la mémorisation de certains réglages peut dépendre de l’édition de l’application.

## Gain rapide du Lecteur

Le Lecteur propose un accès plus direct au gain du morceau : fader visible sur certaines dispositions tablette ou tiroir latéral sur téléphone. Utilisez-le pour une petite correction pendant la répétition ou la scène.

La plage actuelle va de `-24 dB` à `+6 dB`. Une valeur élevée demande une vérification attentive de la saturation.

## Mixage général

L’écran **Mixage général** contrôle trois sources indépendantes :

- **Player** : morceaux principaux ;
- **DJ** : musique diffusée depuis le mode DJ ;
- **Fond sonore** : ambiance entre les morceaux.

Les faders règlent l’équilibre global de ces sources. Pour une première balance, un niveau autour de 70 à 80 % laisse généralement une marge de correction, mais le réglage final dépend du matériel utilisé.

## Bus principal

Le Bus principal donne une vue centrale sur la sortie et les sources audio. Sur tablette, il peut rester accessible dans la disposition de scène. Utilisez-le pour surveiller quelle source est active et corriger le niveau général sans modifier chaque morceau.

## Vitesse et hauteur sonore

**SPEED** change la vitesse dans Track Console. Sa valeur neutre est `1,00x`.

La hauteur sonore n’a pas de commande PITCH dans Track Console. Dans le Lecteur, **Sync Pitch** peut lier la hauteur sonore aux actions **Transpo** sur les accords. Ces deux réglages restent différents : consultez [Afficher les paroles et les accords](10-paroles-et-accords.md).

Une transition impliquant un morceau dont la vitesse ou le pitch est modifié peut devenir séquentielle au lieu d’utiliser un chevauchement. Ce comportement protège la stabilité de la lecture.

## Problèmes courants

### Le niveau revient à une autre valeur

Vérifiez que vous avez réglé le bon morceau et que sa correction LEVELS n’impose pas un autre comportement. Changez de titre puis revenez pour contrôler la valeur enregistrée.

### Une source est audible alors que son fader est correct

Identifiez d’abord la source active dans la navigation ou le Bus principal : Player, DJ et Fond sonore utilisent des chemins séparés.

### Le son sature

Réduisez d’abord LEVEL ou le gain rapide du morceau, puis vérifiez le Mixage général. Évitez d’additionner plusieurs augmentations importantes.

Chapitre suivant : [Utiliser Waveform et les points IN/OUT](15-waveform-et-points-in-out.md).
