# Afficher les paroles et les accords

Le Lecteur suit la position du morceau dans les paroles. Les accords ChordPro peuvent apparaître avec ces paroles et, sur tablette, dans une grille séparée lorsqu’ils sont disponibles. La transposition des accords à l’écran ne réécrit ni les paroles ni le fichier audio.

## Ouvrir l’affichage synchronisé

1. lancez un morceau depuis la Bibliothèque ou une playlist ;
2. ouvrez **Lecteur** si nécessaire ;
3. suivez les paroles affichées ; sur tablette, choisissez **Lyrics** ou **Grid** si la grille d’accords est disponible.

Sur tablette en mode partagé, la destination **Paroles** affiche le contenu synchronisé dans le panneau droit, avec la playlist à gauche.

## Lire les paroles et la grille d’accords

- Sur téléphone, le Lecteur reste en vue **Lyrics** : il n’y a plus de boutons Lyrics/Grid dans la barre live.
- Sur tablette, **Lyrics** affiche les paroles et **Grid** affiche la grille d’accords synchronisée lorsqu’elle est disponible.

Grid est une vue de lecture dérivée : chaque ligne Lyrics contenant des accords
reconnus produit une ligne Grid avec le même timing, l'ordre et les répétitions.
Une ligne sans accord ne produit pas de ligne Grid. En l'absence d'accords reconnus
dans Lyrics, une ancienne grille séparée peut rester affichée. Ce fallback ne reçoit
pas la transposition des accords de la Grid dérivée. Sans l'une ou l'autre source,
Grid reste vide. Changer de vue sur tablette ne modifie pas la lecture audio.

MusiMio reconnaît les tags tels que `[Am]` et `[G/B]`. Les crochets non musicaux
restent du texte. Il prend en charge un sous-ensemble ChordPro, pas tout le standard
ni toutes ses directives.

## Transpo accords, pitch audio et Sync Pitch

Dans la barre live **TRANSPO | SPEED**, sélectionnez **TRANSPO**, puis utilisez **−** ou **+** pour abaisser ou monter les accords affichés d’un demi-ton. La valeur centrale indique la transposition des accords ; touchez-la pour revenir à `0`. La plage des accords va de `−11` à `+11` demi-tons.

La **transposition des accords** désigne la valeur appliquée à leur affichage. Le **pitch audio** change la hauteur entendue du morceau ; sa plage est de `−6` à `+6` demi-tons. Ces deux valeurs peuvent donc différer, notamment près de leurs limites.

Sync Pitch utilise l'icône de maillons : accent si actif, atténuée si inactif. Le premier appui affiche l'aide sans changer ON/OFF ; après fermeture, les appuis suivants activent ou désactivent la liaison. L'aide est mémorisée une fois pour l'appareil, tous morceaux confondus.

Avec **Sync Pitch** activé, une action live **Transpo accords** ajoute sa variation
au pitch audio courant, puis limite celui-ci à `−6..+6`. Activer Sync Pitch seul
ne réaligne pas les valeurs et ne garantit pas leur égalité permanente.

Exemple : accords à `+2`, pitch audio à `0` ; activer Sync Pitch ne change rien.
Un appui live sur **+** donne ensuite accords `+3` et pitch audio `+1`.
Aux limites, le pitch audio peut aussi s'arrêter alors que les accords continuent
de monter ou descendre. Le reset Transpo de la barre live avec Sync Pitch activé
remet les deux valeurs à `0`.

Désactiver Sync Pitch remet le pitch audio actif à `0`. Avec Sync Pitch désactivé,
une action live Transpo demande aussi un pitch audio à `0`. Au lancement ou au
rechargement du titre dans ce mode, un ancien pitch audio stocké n'est pas appliqué.
Ne supposez donc pas que le pitch audio précédent sera conservé.

Si HQ est indisponible, les actions live Transpo couplées au pitch audio, reset
compris, sont bloquées. Transpo dans l'éditeur Lyrics change seulement l'affichage
des accords, sans ce couplage live. La vitesse audio reste séparée et ne transpose
pas les accords.

La transposition des accords est mémorisée localement par morceau. Sync Pitch est
conservé pour l'appareil, pas séparément pour chaque titre ; il est désactivé par
défaut. Le pitch audio possède ses propres réglages par titre, selon l'édition.
Ces mémorisations ne réalignent pas les valeurs. Les accords transposés ne remplacent
pas la source enregistrée et le réglage visuel local n'est pas transporté avec elle.
Vérifiez toujours à l'oreille le résultat audio avant de jouer en public.

## Ligne active et ligne suivante

En mode normal :

- la ligne active est mise en évidence ;
- la ligne suivante peut recevoir une mise en évidence plus douce ;
- les autres lignes restent moins présentes.

Cette présentation aide à lire la phrase suivante avant qu’elle devienne active.

## Mode lisibilité

Le mode lisibilité conserve toutes les lignes bien visibles, notamment pour :

- une scène très lumineuse ;
- une utilisation en extérieur ;
- une lecture à distance.

Pour l’activer, utilisez l’icône de lisibilité située dans l’en-tête du Lecteur. La ligne active reste identifiable par sa couleur, son épaisseur ou sa taille.

Le choix est conservé entre les sessions.

## Taille des paroles

1. ouvrez **Paramètres / Plus** ;
2. recherchez la section **Paroles** ;
3. choisissez **Taille des paroles** ;
4. sélectionnez Petit, Normal, Grand ou Très grand.

La taille est globale pour l’appareil. Elle ne s’applique pas séparément à chaque morceau.

À très grande taille, des phrases longues peuvent occuper davantage de place. Testez les morceaux importants sur l’appareil réellement utilisé sur scène.

## Couleurs de lecture guidée

Les couleurs de lecture guidée peuvent alterner automatiquement les lignes pour faciliter le suivi visuel.

Dans **Paramètres / Plus** :

1. activez **Couleurs de lecture guidée** ;
2. choisissez la couleur A ;
3. choisissez la couleur B.

Les couleurs manuelles attribuées dans l’éditeur restent prioritaires. Les couleurs guidées ne modifient pas le fichier LRC.

## Couleurs manuelles des lignes

Une ligne peut recevoir une couleur particulière pour signaler :

- un avertissement ;
- une partie parlée ;
- une intervention du public ;
- un changement d’instrument ;
- une section importante.

Ces couleurs sont enregistrées avec le morceau et peuvent être transportées dans un fichier `.smp` compatible.

## Revenir au début

Utilisez la commande de retour au début du Playback Control. La position audio revient à `00:00` et la première ligne doit redevenir la ligne active.

## Paroles d’une variante Arrangement

Une variante peut posséder ses propres paroles et accords. Le Lecteur affiche alors le contenu de la variante, pas celui du parent.

Modifier les paroles d’une variante ne doit pas modifier les paroles du morceau source.

## Différence avec un texte défilant

| Paroles synchronisées | Texte défilant |
|---|---|
| Liées à un morceau | Autonome |
| Pilotées par le temps audio | Défilement continu réglable |
| Utilisent des horodatages | Ne nécessite aucun horodatage |
| Affichées dans le Lecteur | Ouvertes dans le prompteur de texte |

Consultez [Créer et utiliser des textes défilants](12-textes-defilants.md) pour les textes autonomes.

Le Prompteur des textes défilants possède son propre éditeur ChordPro et sa propre présentation. Ne le confondez pas avec les accords ChordPro d’un morceau synchronisé, décrits ici et dans [Éditer et synchroniser les paroles](11-editer-et-synchroniser-les-paroles.md).

## Problèmes courants

### Aucune parole n’apparaît

- Vérifiez que le morceau contient des paroles.
- Ouvrez l’éditeur pour importer ou saisir le texte.
- Revenez au début après l’enregistrement.

### Les lignes changent au mauvais moment

Corrigez les horodatages dans l’onglet **Sync**.

### Les dernières modifications ne sont pas visibles

Quittez l’éditeur avec son action normale d’enregistrement, puis revenez dans le Lecteur. Si nécessaire, changez temporairement de morceau puis revenez au morceau édité.

### Les couleurs sont difficiles à lire

Choisissez des couleurs plus contrastées, augmentez la taille des paroles ou activez le mode lisibilité.

Chapitre suivant : [Éditer et synchroniser les paroles](11-editer-et-synchroniser-les-paroles.md).
