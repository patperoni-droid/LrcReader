# Régler le mixage du titre et le Bus principal

MusiMio sépare les réglages propres à un morceau du mixage général des différentes sources audio. Cette séparation permet de préparer les titres à l’avance sans dérégler toute la prestation.

## Track Console : réglages du morceau

Track Console n'est plus accessible depuis l'interface actuelle, sur téléphone
comme sur tablette. Son code, son Volume interne et son prototype EQ restent
conservés pour une future V2/V3. Aucun EQ fonctionnel n'est disponible dans cette
version.

Les réglages accessibles restent le gain du Lecteur, [LEVELS dans la Bibliothèque](13-levels-et-niveaux-des-morceaux.md)
et [TRANSPO / SPEED dans le Lecteur audio/paroles](08-lecteur-et-commandes-de-lecture.md).

## Régler un morceau

1. ouvrez le morceau dans le Lecteur ;
2. sélectionnez **SPEED** dans la barre du Lecteur et touchez sa valeur pour revenir à `×1,00` si vous souhaitez une vitesse neutre ;
3. ouvrez **Bibliothèque → LEVELS** et la case dB du morceau ;
4. ajustez son niveau avec **−1 dB / +1 dB** ;
5. écoutez le début, une partie forte et la fin ;
6. passez à un autre morceau puis revenez pour vérifier la mémorisation.

La disponibilité ou la mémorisation de certains réglages peut dépendre de l’édition de l’application.

## Gain rapide du Lecteur

Le Lecteur propose un accès plus direct au gain du morceau : fader visible sur certaines dispositions tablette ou tiroir latéral sur téléphone. Utilisez-le pour une petite correction pendant la répétition ou la scène.

La plage actuelle va de `-24 dB` à `+6 dB`. Une valeur élevée demande une vérification attentive de la saturation.

## Mixage général

L’écran **Mixage général** contrôle les sources indépendantes suivantes :

- **Player** : morceaux principaux ;
- **DJ** : musique diffusée depuis le mode DJ ;
- **Fond sonore** : ambiance entre les morceaux ;
- **PADS** dans Labo : niveau global des Sound Pads.

Concert release conserve les trois sources historiques. Le fader PADS du Bus sonore et celui du Mixage général partagent le même réglage, mémorisé après redémarrage. Il agit immédiatement pendant la lecture et multiplie le volume individuel, sans le modifier. Stop Pads ne coupe pas les autres sources. Voir [utiliser Sound Pads](03-navigation-telephone-et-tablette.md#sound-pads).

Les faders règlent l’équilibre global de ces sources. Pour une première balance, un niveau autour de 70 à 80 % laisse généralement une marge de correction, mais le réglage final dépend du matériel utilisé.

## Bus principal

Le Bus principal donne une vue centrale sur la sortie et les sources audio. Sur tablette, il peut rester accessible dans la disposition de scène. Utilisez-le pour surveiller quelle source est active et corriger le niveau général sans modifier chaque morceau.

## Vitesse et hauteur sonore

**SPEED** change la vitesse dans la barre **TRANSPO | SPEED** du Lecteur audio/paroles. Sa valeur neutre est `×1,00` ; voir [les commandes du Lecteur](08-lecteur-et-commandes-de-lecture.md) pour la plage et le pas.

Dans le Lecteur, **Sync Pitch** peut lier la hauteur sonore aux actions **Transpo** sur les accords. Ces deux réglages restent différents : consultez [Afficher les paroles et les accords](10-paroles-et-accords.md).

Une transition impliquant un morceau dont la vitesse ou le pitch est modifié peut devenir séquentielle au lieu d’utiliser un chevauchement. Ce comportement protège la stabilité de la lecture.

## Problèmes courants

### Le niveau revient à une autre valeur

Vérifiez que vous avez réglé le bon morceau et que sa correction LEVELS n’impose pas un autre comportement. Changez de titre puis revenez pour contrôler la valeur enregistrée.

### Une source est audible alors que son fader est correct

Identifiez d’abord la source active dans la navigation ou le Bus principal : Player, DJ et Fond sonore utilisent des chemins séparés.

### Le son sature

Réduisez d’abord LEVEL ou le gain rapide du morceau, puis vérifiez le Mixage général. Évitez d’additionner plusieurs augmentations importantes.

Chapitre suivant : [Utiliser Waveform et les points IN/OUT](15-waveform-et-points-in-out.md).
