# Éditer et synchroniser les paroles

L’éditeur de morceau permet de saisir les paroles, éditer les accords et associer chaque ligne à un moment précis de la musique.

Ce chapitre concerne l'éditeur **synchronisé du morceau**. L'éditeur ChordPro ouvert
par le crayon du Prompteur ou de Bibliothèque → Textes défilants est un autre parcours,
sans onglet Sync ni TAG : voir [Textes défilants](12-textes-defilants.md). Ses
couleurs sont enregistrées dans le texte et son repli d'outils dépend du focus ;
les règles de couleur et de clavier ci-dessous concernent les paroles synchronisées.

## Ouvrir l’éditeur

1. lancez ou sélectionnez le morceau concerné ;
2. ouvrez le Lecteur ;
3. choisissez l’action **Éditer les paroles** ;
4. vérifiez le titre affiché avant de commencer.

L’éditeur travaille sur le morceau actif ou explicitement sélectionné. Si le morceau change pendant l’édition, certaines actions peuvent être bloquées afin d’éviter d’écrire dans le mauvais morceau.

## Les onglets de l’éditeur : Lyrics et Sync

### Lyrics

Saisissez les paroles et les accords ChordPro dans le même contenu, par exemple
`Je [Am]chante`. Une ligne peut contenir uniquement `[Am] [F] [G]`.
Les lignes vides, espacements et textes non encore synchronisés sont conservés
dans le brouillon. Les crochets non reconnus comme accords restent du texte ;
MusiMio ne prend pas en charge l'intégralité du standard ChordPro.

L'ancien onglet Accords séparé n'est plus proposé. Les accords actuels s'éditent
dans Lyrics. L'ancienne grille reste éventuellement lisible en fallback sur
tablette ; Grid n'est pas une seconde source à éditer.

### Sync

Associez les lignes Lyrics au temps audio avec **TAG** et les commandes de lecture.
Le timing appartient à la ligne, même si elle contient des accords seuls.
Changer d'onglet ne quitte pas l'éditeur ; vous pouvez passer de Lyrics à Sync
pendant la même session.

## Saisir les paroles

1. ouvrez l’onglet **Lyrics** ;
2. saisissez une phrase par ligne ;
3. conservez les lignes vides utiles à votre mise en forme ;
4. utilisez **Enregistrer** lorsque vous souhaitez valider la session.

La palette reprend automatiquement les accords reconnus dans Lyrics ; ses boutons
insèrent ou remplacent un tag. **[ ]** permet de commencer un accord quand elle
est vide.

L'enregistrement automatique intervient après de vraies modifications, mais utilisez
Enregistrer avant de quitter après un travail important.

## Vérifier les accords ChordPro dans le Lecteur

1. dans l’onglet **Lyrics**, placez les accords entre crochets dans les lignes voulues ;
2. enregistrez, puis revenez au Lecteur du même morceau ;
3. vérifiez les accords affichés avec les paroles ;
4. sur tablette, ouvrez **Grid** pour vérifier la grille d’accords synchronisée lorsqu’elle est disponible ;
5. utilisez **Transpo − / valeur / +** dans la barre live pour vérifier une autre tonalité d’accords, puis touchez la valeur pour revenir à `0`.

La Transpo accords de l'éditeur modifie uniquement l'affichage local, sans réécrire
le texte ni changer le pitch audio. Dans la barre live du Lecteur, **Sync Pitch**
couple une action Transpo au pitch audio par variation, sans garantir l'égalité.
Consultez [Afficher les paroles et les accords](10-paroles-et-accords.md) pour
les limites, resets et conditions HQ.

## Importer un fichier LRC

1. ouvrez l’éditeur ;
2. choisissez **Import .LRC** ;
3. sélectionnez le fichier `.lrc` avec Android ;
4. vérifiez le texte et les horodatages importés ;
5. enregistrez le morceau.

Le collage de source `[accord]` est possible. L'assistance **Convertir / Ignorer**
pour des accords externes appartient à l'éditeur Textes défilants, pas à Audio Lyrics.
Aucun import complet dédié de fichiers `.cho` n'est à supposer disponible.

L’import peut échouer si le fichier est vide, inaccessible ou mal formé. Une ligne LRC invalide ne doit pas empêcher la lecture des autres lignes valides, mais elle peut être ignorée pendant la lecture.

## Synchroniser les lignes avec TAG

1. préparez les paroles dans l’onglet Lyrics ;
2. ouvrez **Sync** ;
3. revenez au début du morceau ;
4. lancez la musique ;
5. appuyez sur **TAG** au moment précis où chaque phrase doit commencer ;
6. continuez dans l’ordre jusqu’à la fin ;
7. revenez au début et contrôlez le résultat ;
8. enregistrez.

Le temps enregistré provient du Lecteur. Appuyez de façon régulière et anticipez légèrement si votre geste a tendance à être tardif.

## Lire depuis une ligne

Dans l’onglet Sync, l’action de lecture d’une ligne permet de reprendre près de son horodatage afin de contrôler une partie précise sans recommencer tout le morceau.

## Modifier une ligne

Ouvrez le menu ou la boîte de dialogue de la ligne pour :

- corriger son texte ;
- modifier sa couleur ;
- supprimer la ligne ;
- vérifier son horodatage.

Un appui long permet de sélectionner ou désélectionner une ligne dans les parcours qui utilisent la sélection.

## Grouper des lignes

L’action **Grouper** réunit des lignes adjacentes. Des lignes non adjacentes ne peuvent pas être groupées directement.

Utilisez **Annuler** si le résultat ne correspond pas à votre intention.

## Réinitialiser les horodatages

L’action **Reset TAGs** retire ou réinitialise les repères de synchronisation. Utilisez-la avec prudence : vous devrez resynchroniser les lignes concernées.

## Attribuer une couleur

1. ouvrez une ligne ;
2. accédez à la section **Couleur** ;
3. choisissez Aucune, Jaune, Rouge, Bleu, Vert ou Violet ;
4. validez et enregistrez.

La couleur est une information d’affichage distincte du texte LRC.

## Enregistrer et quitter

L’action **Enregistrer** sauvegarde la session du morceau : texte, horodatages, couleurs et accords modifiés.

Si une boîte de dialogue **Modifications non sauvegardées** apparaît :

- enregistrez pour conserver le travail ;
- choisissez **Quitter sans sauvegarder** uniquement si vous voulez réellement abandonner les dernières modifications ;
- choisissez Annuler pour revenir à l’éditeur.

Évitez de fermer brutalement l’application pendant l’enregistrement final.

## Édition sur tablette

Lorsque le clavier Android est ouvert, le panneau droit peut masquer temporairement la barre supérieure et certains contrôles secondaires pour donner plus de place au texte.

Fermer le clavier restaure ces contrôles sans quitter l’éditeur. La playlist reste visible à gauche dans le mode partagé.

## Variantes Arrangement

Lorsque vous éditez une variante :

- les paroles sont enregistrées pour cette variante ;
- les accords sont enregistrés pour cette variante ;
- le parent n’est pas modifié ;
- la synchronisation suit le temps cumulé de la Structure de la variante.

Vérifiez toujours le titre et le badge ARR avant une longue session d’édition.

## Problèmes courants

### L’onglet Sync est vide

Ajoutez d’abord du texte dans l’onglet Lyrics.

### Le bouton TAG ne donne pas le bon résultat

Revenez au début, vérifiez que le bon morceau est actif et recommencez sur une courte section.

### Des lignes vides ont disparu

Vérifiez que vous avez utilisé l’éditeur actuel et quitté par l’action normale d’enregistrement. Le brouillon exact est conservé séparément de la liste de lecture synchronisée.

### Les accords ont remplacé les paroles

Les accords actuels sont des tags dans Lyrics, pas un contenu à importer dans un
onglet Accords. Vérifiez la source et la sélection avant de coller : Lyrics doit
conserver les paroles avec les tags placés au bon endroit.

### Le morceau actif a changé

Revenez au Lecteur, relancez le bon morceau, puis rouvrez l’éditeur. Le blocage protège le contenu du morceau précédent.

Chapitre suivant : [Créer et utiliser des textes défilants](12-textes-defilants.md).
