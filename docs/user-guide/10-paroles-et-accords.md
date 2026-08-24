# Afficher les paroles et les accords

Le Lecteur peut afficher des paroles ou des accords synchronisés avec la position du morceau. Ces deux contenus sont indépendants : modifier les accords ne remplace pas les paroles, et inversement.

## Ouvrir l’affichage synchronisé

1. lancez un morceau depuis la Bibliothèque ou une playlist ;
2. ouvrez **Lecteur** si nécessaire ;
3. choisissez **Paroles** ou **Accords**.

Sur tablette en mode partagé, la destination **Paroles** affiche le contenu synchronisé dans le panneau droit, avec la playlist à gauche.

## Passer des paroles aux accords

- Sur téléphone, utilisez le sélecteur Paroles/Accords du Lecteur.
- Sur tablette, utilisez les commandes **Paroles** et **Accords** de la barre du Lecteur.

Le changement est immédiat et ne modifie pas la lecture audio.

Si aucun accord n’est disponible, le Lecteur indique que le fichier Accords est introuvable.

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

## Problèmes courants

### Aucune parole n’apparaît

- Vérifiez que le morceau contient des paroles.
- Ouvrez l’éditeur pour importer ou saisir le texte.
- Revenez au début après l’enregistrement.

### Les lignes changent au mauvais moment

Corrigez les horodatages dans l’onglet **Synchro**.

### Les dernières modifications ne sont pas visibles

Quittez l’éditeur avec son action normale d’enregistrement, puis revenez dans le Lecteur. Si nécessaire, changez temporairement de morceau puis revenez au morceau édité.

### Les couleurs sont difficiles à lire

Choisissez des couleurs plus contrastées, augmentez la taille des paroles ou activez le mode lisibilité.

Chapitre suivant : [Éditer et synchroniser les paroles](11-editer-et-synchroniser-les-paroles.md).
