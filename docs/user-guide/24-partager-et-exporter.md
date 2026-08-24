# Partager et exporter

MusiMio utilise le format `.smp` pour transporter un morceau avec ses données. Le fichier peut être conservé, envoyé par une application Android ou importé sur un autre appareil.

## Exporter un morceau parent

1. ouvrez la Bibliothèque ;
2. ouvrez le menu du morceau ;
3. choisissez **Partager** ou l’action d’export au format `.smp` ;
4. attendez la préparation du fichier ;
5. choisissez l’application ou l’emplacement de destination ;
6. vérifiez que le fichier `.smp` a bien été créé.

L’export du parent transporte l’audio une seule fois et inclut ses variantes valides ainsi que les données associées présentes dans l’archive.

## Exporter une variante

1. ouvrez la famille du morceau ;
2. sélectionnez la variante souhaitée ;
3. choisissez **Partager** ;
4. contrôlez son nom avant l’envoi.

L’archive ciblée transporte le parent nécessaire et la variante choisie, sans recopier plusieurs fois l’audio. Elle ne doit pas modifier les autres variantes du destinataire.

## Données généralement transportées

Selon le morceau et la version du format, un export peut contenir :

- audio ;
- titre et identité du morceau ;
- paroles et brouillon d’édition ;
- accords et couleurs de lignes ;
- points IN/OUT et réglages compatibles ;
- variante ou famille de variantes concernée.

Une ancienne archive ne peut pas contenir des données ajoutées après sa création. Vérifiez toujours le résultat après import.

## Importer un fichier reçu

1. enregistrez le fichier `.smp` sur l’appareil ;
2. ouvrez **Bibliothèque > Importer** ;
3. choisissez **Importer un fichier SMP** ;
4. sélectionnez le fichier ;
5. attendez la fin de l’import ;
6. ouvrez et testez le morceau.

L’ouverture directe d’un `.smp` depuis toutes les applications Android n’est pas encore garantie. Passez par l’import de la Bibliothèque.

## Importer une variante reçue

Si son parent est absent, l’archive installe d’abord le parent transporté, puis recrée la variante. Si le parent existe déjà avec la même identité, ses autres données locales ne doivent pas être remplacées sans nécessité.

Un conflit d’identité incohérent peut provoquer un refus d’import afin de protéger la Bibliothèque.

## Partage Android

Le sélecteur Android peut proposer messagerie, courrier, stockage en ligne ou application de fichiers. MusiMio prépare le fichier, mais la réussite de l’envoi dépend ensuite de l’application choisie.

Pour un fichier volumineux, préférez une destination qui accepte sa taille. Attendez la fin de l’envoi avant de supprimer le fichier local.

## Export d’une playlist isolée

Un import/export JSON dédié peut rester disponible pour une playlist isolée. Il transporte l’organisation de la playlist, pas nécessairement tous les fichiers audio. Pour déplacer un ensemble jouable vers un autre appareil, utilisez plutôt MusiMio Sync ou une sauvegarde complète.

## Problèmes courants

### L’application destinataire refuse le fichier

Le `.smp` peut être trop volumineux. Enregistrez-le dans un dossier ou utilisez un service acceptant les gros fichiers.

### La variante importée ne joue pas

Vérifiez que l’import s’est terminé et que l’audio parent est présent. Réimportez l’archive si nécessaire.

### Le fichier reçu ne s’ouvre pas par un toucher

Utilisez **Bibliothèque > Importer > Importer un fichier SMP**. L’association Android directe est une évolution non finalisée.

### Des données récentes manquent après import

L’archive peut être ancienne. Créez un nouvel export depuis la Bibliothèque source puis réimportez-le.

Chapitre suivant : [Sauvegarder et restaurer la Bibliothèque](25-sauvegarder-et-restaurer.md).
