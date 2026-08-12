# Importer et gérer ses morceaux

Stage Music Player propose un point d’entrée commun pour importer un fichier audio ou un morceau complet au format SMP.

## Ouvrir le menu d’import

1. ouvrez **Bibliothèque** ;
2. appuyez sur **Importer…** ;
3. choisissez le type de contenu à importer.

Deux choix sont proposés :

- **Importer un morceau audio** ;
- **Importer un fichier SMP**.

## Importer un fichier audio

### Formats

La chaîne d’import prend en charge les formats audio courants acceptés par l’application et Android, notamment MP3, WAV, FLAC, M4A, AAC et OGG.

La compatibilité d’un fichier précis peut dépendre de son encodage et de l’appareil. Un fichier portant une extension reconnue peut tout de même être refusé s’il est endommagé ou utilise un codec non pris en charge.

### Procédure

1. choisissez **Importer un morceau audio** ;
2. sélectionnez le fichier dans le navigateur Android ;
3. attendez la fin de l’import ;
4. vérifiez que le morceau apparaît dans la vue Songs ;
5. ouvrez-le pour contrôler le titre, l’audio et les éventuelles paroles.

L’import prépare une copie de travail interne. La lecture sur scène ne dépend ensuite plus de l’emplacement d’origine.

## Paroles et fichiers associés

Si le parcours d’import trouve des paroles intégrées ou un fichier `.lrc` associé compatible, elles peuvent être rattachées au même morceau.

Après l’import, vérifiez toujours :

- le contenu des paroles ;
- leur synchronisation ;
- la présence éventuelle des accords ;
- le titre affiché.

## Importer un fichier SMP

Un fichier `.smp` transporte un morceau complet avec les données qui étaient présentes au moment de son export.

1. choisissez **Importer un fichier SMP** ;
2. sélectionnez l’archive dans le navigateur Android ;
3. laissez Stage Music Player vérifier et extraire son contenu ;
4. attendez son apparition dans la Bibliothèque.

Un fichier SMP peut contenir :

- l’audio ;
- les paroles et accords ;
- les réglages de lecture ;
- un projet Arrangement ;
- des variantes ;
- une Timeline et d’autres données liées au morceau.

Une ancienne archive ne peut restaurer que les données qu’elle contenait lors de sa création.

## Importer une variante partagée

Une variante Arrangement partagée voyage avec l’audio de son morceau parent :

- si le parent existe déjà sur l’appareil, il est conservé et seule la variante est ajoutée ou mise à jour ;
- si le parent est absent, il est d’abord importé, puis la variante est créée ;
- si la même variante est déjà liée à un autre parent, l’import est refusé pour éviter une incohérence.

## Importer plusieurs morceaux

Lorsque le sélecteur ou le parcours choisi autorise la sélection multiple :

1. sélectionnez les fichiers ;
2. confirmez l’import ;
3. laissez le traitement se terminer ;
4. consultez le résumé des réussites et des échecs ;
5. vérifiez les nouveaux morceaux dans Songs.

Évitez de lancer une lecture importante pendant une conversion ou un import volumineux.

## Ajouter un morceau à une playlist

Depuis la Bibliothèque :

1. sélectionnez un ou plusieurs morceaux ;
2. choisissez **Attribuer** ou **Ajouter à une playlist** ;
3. sélectionnez la playlist cible ;
4. vérifiez leur ordre dans la playlist.

Une variante peut être ajoutée sans que son parent soit visible dans la même playlist. Le parent doit néanmoins rester présent dans la Bibliothèque.

## Renommer

1. ouvrez le menu du morceau ;
2. choisissez **Renommer** ;
3. saisissez le titre souhaité ;
4. validez.

Le renommage change l’affichage dans Stage Music Player, pas le nom du fichier audio d’origine.

## Copier ou déplacer un fichier visible

Les actions **Copier vers…** et **Déplacer vers…** concernent le stockage accessible par Android. Choisissez un dossier déjà autorisé ou accordez l’accès à un autre emplacement.

Pour un morceau live, privilégiez les actions prévues par l’application afin de ne pas séparer l’audio de ses paroles ou de ses réglages.

## Supprimer un morceau live

1. ouvrez le menu du morceau ou activez la sélection multiple ;
2. choisissez **Supprimer** ;
3. lisez le contenu exact de la confirmation ;
4. vérifiez le nombre de variantes éventuellement concernées ;
5. confirmez seulement si le bon morceau est ciblé.

Conséquences :

- le morceau est retiré de Stage Music Player ;
- ses occurrences sont retirées des playlists ;
- ses variantes sont également supprimées s’il s’agit de leur parent ;
- les sauvegardes externes déjà créées ne sont pas supprimées.

Supprimer une variante seule ne supprime pas son parent ni les autres variantes.

## Problèmes courants

### Le fichier n’apparaît pas dans le sélecteur

- Vérifiez son extension et son emplacement.
- Essayez un dossier local standard.
- Vérifiez que le fournisseur de fichiers Android autorise sa lecture.

### L’import échoue

- Vérifiez l’espace libre.
- Essayez de lire le fichier dans une autre application.
- Pour un SMP, demandez une nouvelle exportation si l’archive est incomplète.
- Relancez l’import depuis la Bibliothèque, pas depuis une autre application : l’ouverture directe d’un `.smp` depuis Gmail ou Files n’est pas encore le parcours officiellement validé.

### Un doublon apparaît

Les anciennes archives dépourvues d’identité stable peuvent être reconnues comme de nouveaux morceaux. Comparez soigneusement le contenu avant de supprimer l’une des copies.

### Le titre est incorrect

Utilisez **Renommer**. Ce titre personnalisé est prioritaire sur les anciennes métadonnées du fichier.

Chapitre suivant : [Rechercher un morceau et consulter l’historique](06-recherche-et-historique.md).
