# Résoudre les problèmes courants

Ce chapitre rassemble les contrôles les plus sûrs. Pendant une prestation, privilégiez la continuité audio : évitez une réimportation, une restauration ou une réanalyse lourde tant qu’un morceau important est en lecture.

## Le morceau ne démarre pas

1. vérifiez que le bon titre est sélectionné ;
2. sur tablette, appuyez sur le Play jaune pour confirmer la sélection ;
3. contrôlez que l’import est terminé ;
4. ouvrez la famille et essayez le parent ;
5. vérifiez le volume Player et le volume Android ;
6. réimportez le `.smp` si l’audio manque réellement.

## Le mauvais morceau démarre

La sélection visible et le morceau actif peuvent différer sur tablette. Relisez le titre dans le Lecteur et la couleur de Play avant de lancer. Vérifiez également si un morceau a été **Défini comme prochain**.

## Aucun son n’est audible

Contrôlez dans cet ordre :

- volume physique de l’appareil ;
- sortie audio ou interface connectée ;
- source active : Player, DJ ou Fond sonore ;
- fader de cette source dans le Mixage général ;
- gain du morceau ;
- câble, console et enceinte.

Évitez d’augmenter tous les gains en même temps : le son peut revenir brutalement.

## Le son sature

Réduisez le gain du morceau, puis le bus concerné. Vérifiez ensuite LEVELS et l’EQ. Une correction positive à plusieurs endroits peut dépasser la marge disponible.

## Les paroles ne suivent pas

1. revenez au début du morceau ;
2. vérifiez que les paroles appartiennent à la bonne variante ;
3. contrôlez les horodatages dans l’éditeur Synchro ;
4. testez après un déplacement dans la barre de progression.

Après un Arrangement, la chronologie du parent peut ne plus correspondre à la variante.

## Le morceau commence ou finit au mauvais endroit

Ouvrez Waveform et contrôlez IN/OUT. Écoutez autour des deux points au lieu de vous fier uniquement à l’image. Vérifiez aussi que vous modifiez la bonne variante.

## Une variante Arrangement se coupe

Testez chaque raccord, élargissez les limites des segments et activez le mode de compatibilité sur téléphone. La lecture directe doit être validée sur le modèle utilisé en concert.

## La Bibliothèque ne montre pas un import récent

Attendez la fin de l’import, revenez à la Bibliothèque et relancez son actualisation. Utilisez **Mettre à jour la bibliothèque** avec prudence : cette action n’est pas encore une synchronisation complète de l’état global.

## Une playlist semble vide

Vérifiez si ses morceaux ont été supprimés de la Bibliothèque ou si le filtre de Recherche est encore actif. Une playlist référence les morceaux ; elle ne duplique pas leur audio.

## Le dossier DJ ou Fond sonore est vide

Contrôlez l’autorisation Android et le dossier sélectionné. Attendez la fin d’une analyse en cours, puis relancez la réanalyse depuis le menu DJ si nécessaire.

## Le Deuxième écran ne se connecte pas

Placez les deux appareils sur le même réseau, activez la diffusion et évitez les Wi-Fi invités. Si la découverte échoue, utilisez l’adresse IP et le port dans les options avancées.

## MusiMio Sync ne se connecte pas

Vérifiez le mode réception, l’IP et le port actuellement affichés. Un redémarrage ou un changement de réseau peut modifier l’adresse. L’import doit ensuite être confirmé manuellement sur l’appareil de secours.

## L’Accordeur ne détecte rien

Autorisez le microphone, jouez près de l’appareil et réduisez le bruit ambiant. Mettez le Player en pause ou utilisez un casque afin que la musique ne perturbe pas la mesure.

## Une restauration ne contient pas les dernières modifications

Vérifiez la date et le nom du dossier. L’action **Mettre à jour la bibliothèque** ne met actuellement à jour que les familles de morceaux ; créez une nouvelle sauvegarde complète pour inclure l’organisation globale prise en charge.

## Questions fréquentes

### Puis-je déplacer les fichiers internes avec une application de fichiers ?

Non. Utilisez les commandes d’import, de partage, de sauvegarde et de restauration de MusiMio. Le déplacement manuel peut casser les liens entre un morceau et ses données.

### Un `.smp` est-il le fichier utilisé directement pendant la lecture ?

Non. Il sert au transport. Après import, MusiMio prépare une copie locale adaptée à la lecture.

### Ajouter un morceau à plusieurs playlists duplique-t-il l’audio ?

Non. Les playlists référencent le même morceau de la Bibliothèque.

### Puis-je supprimer un parent en gardant ses variantes ?

Non. Les variantes dépendent de l’audio du parent et sont supprimées avec lui.

### MusiMio Sync remplace-t-il une sauvegarde ?

Non. Il transfère une sélection entre deux appareils. Conservez une sauvegarde externe complète.

### Le Deuxième écran a-t-il besoin d’Internet ?

Non, mais les deux appareils doivent pouvoir communiquer sur le même réseau local.

### Tous les réglages sont-ils disponibles dans la version gratuite ?

Non nécessairement. Certaines fonctions ou mémorisations sont liées à l’édition. L’application indique les limites lorsqu’elles s’appliquent.

### Que vérifier juste avant de monter sur scène ?

- batterie et alimentation ;
- mode avion adapté au réseau local utilisé ;
- sortie audio, câbles et niveau général ;
- playlist et prochain morceau ;
- variantes, IN/OUT et paroles ;
- appareil de secours et sauvegarde ;
- Deuxième écran, MIDI ou DMX réellement utilisés.

## Si le problème persiste

Notez le titre concerné, l’écran, l’action réalisée et le message affiché. Reproduisez le problème hors lecture avec une copie de sauvegarde disponible. Évitez de supprimer ou restaurer toute la Bibliothèque sans avoir sécurisé ses données.

Retour au [sommaire du manuel](index.md).
