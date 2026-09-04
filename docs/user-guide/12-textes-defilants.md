# Créer et utiliser des textes défilants

Un texte défilant est un contenu autonome destiné à être lu comme un prompteur. Il ne nécessite ni fichier audio ni horodatage LRC.

## Quand utiliser un texte défilant ?

Exemples :

- présentation parlée ;
- annonce au public ;
- texte libre ;
- conducteur de spectacle ;
- paroles à faire défiler manuellement sans audio synchronisé.

Pour des paroles qui doivent suivre automatiquement un morceau, utilisez les paroles synchronisées du Lecteur.

## Ouvrir le catalogue

Dans **Bibliothèque**, choisissez **Textes défilants**.

Ce catalogue est partagé par toutes les playlists. Le même texte peut être utilisé depuis plusieurs endroits sans dupliquer son contenu.

## Créer depuis la Bibliothèque

1. ouvrez **Bibliothèque → Textes défilants** ;
2. appuyez sur l’icône crayon **Créer un texte défilant** ;
3. saisissez un titre obligatoire ;
4. saisissez le texte ;
5. validez.

Le texte apparaît immédiatement dans le catalogue général. Il n’est ajouté automatiquement à aucune playlist.

## Créer depuis une playlist

1. ouvrez la playlist cible ;
2. choisissez **Créer un texte défilant** ;
3. saisissez le titre et le contenu ;
4. validez.

Le texte est créé dans le catalogue général et une occurrence est ajoutée à la playlist ouverte.

Cette différence est importante :

- création depuis la Bibliothèque = catalogue seulement ;
- création depuis une playlist = catalogue et playlist actuelle.

## Ouvrir un texte

- Depuis la Bibliothèque, touchez son entrée.
- Depuis une playlist, touchez son occurrence.

L’ouverture d’un texte défilant ne démarre pas un morceau audio et ne change pas le modèle du Lecteur principal.

## Commandes du prompteur

Selon l’appareil, les commandes disponibles comprennent :

- **Play / Pause** : démarrer ou suspendre le défilement continu ;
- **Précédent / Suivant** : déplacer le texte d’un pas de lecture ;
- réglage de **vitesse** : accélérer ou ralentir le défilement ;
- **Retour au début** : revenir au sommet, notamment sur tablette.

La vitesse choisie est mémorisée pour le texte.

## Sur téléphone

Le prompteur s’ouvre en plein écran. Les commandes restent accessibles autour de la zone de lecture.

Utilisez le bouton Retour pour fermer le prompteur et revenir à l’écran précédent.

## Sur tablette

Dans le cockpit partagé :

- la playlist reste à gauche ;
- le texte s’affiche dans le panneau droit ;
- les commandes et le réglage de vitesse restent contenus dans ce panneau ;
- **Retour au début** est disponible dans la présentation tablette compatible.

Le texte ne doit pas recouvrir la playlist.

## Modifier un texte

Deux accès ouvrent le même éditeur pour un texte du catalogue :

- le crayon de son entrée dans **Bibliothèque → Textes défilants** ;
- le crayon en haut à droite du Prompteur, pour modifier le texte actuellement affiché.

Corrigez le titre ou le contenu, puis touchez **✓** pour enregistrer et fermer.
**×** annule les modifications de cette session. Depuis le crayon du Prompteur,
vous revenez directement au texte actualisé. Il n'y a pas d'enregistrement
automatique du brouillon dans ce dialogue.

Comme le catalogue possède le contenu, une modification est visible depuis toutes les playlists qui référencent ce texte.

## Ajouter des accords

Vous pouvez saisir ou coller des accords entre crochets :

```text
Je [Am]voulais te [F]dire
```

Dans le Prompteur, les accords reconnus apparaissent au-dessus des paroles, sans
crochets. Un texte sans accords reste utilisable. `[Refrain]` reste du texte ;
les directives ChordPro complètes comme `{title:}` ne sont pas interprétées.

Avant d'entrer dans le contenu, renseignez le champ **Palette d’accords** avec,
par exemple, `C Am F G7 C/E`, sans crochets. Vous retrouvez cette palette à la
réouverture du même texte sur l'appareil. Elle n'est pas remplie automatiquement
à partir des accords déjà présents.

Placez le curseur puis touchez un accord : son tag est inséré à cet endroit,
et vous pouvez continuer à taper juste après. Si vous avez sélectionné des paroles,
l'accord est ajouté au début de la sélection sans supprimer ces paroles.
Pour remplacer un accord, placez le curseur à l'intérieur de son tag, ou sélectionnez
le tag entier, puis choisissez un autre accord. Pour le supprimer, effacez son texte.

## Mettre en forme et colorer

L'icône de texte souligné ouvre **Texte et couleur**. Les commandes disponibles sont :

- **Titre**, **Section**, **Couplet**, **Refrain** ;
- **Commentaire**, **Gras**, **Italique**, **Séparateur** ;
- les pastilles jaune, orange, rouge, bleue, verte et **Aucune / défaut** pour la couleur par défaut.

L'éditeur montre les marqueurs (`# Titre`, `## Refrain`, `**gras**`, `*italique*`,
`---`, `<c=yellow>texte</c>`). Leur présentation mise en forme apparaît dans le Prompteur.
Couplet et refrain créent des titres de section, pas des répétitions automatiques.

Sélectionnez le texte avant d'appliquer un style ou une couleur. Sans sélection,
un texte indicatif est proposé et sélectionné pour être remplacé. Pour colorer
un accord seul, sélectionnez son tag complet, crochets compris.

Pour recolorer une plage déjà colorée, placez le curseur à l'intérieur et choisissez
une autre pastille : toute la plage change de couleur. **Aucune / défaut** retire la couleur
explicite ; les paroles et accords reprennent chacun leur couleur habituelle.
Gras ou Italique peuvent également être retirés en réappliquant le même bouton dans
une plage simple. Les cas de styles imbriqués ou de sélection coupant un marqueur
ne sont pas tous pris en charge ; certaines actions ne font alors rien.

L'icône d'alignement ouvre **Gauche / Centré** pour l'ensemble du texte.
Choisir une commande ferme son panneau et ramène au champ de texte.

## Espace d'édition sur téléphone et tablette

Quand le contenu reçoit le focus, l'en-tête, le champ titre et le champ de
configuration de palette se replient. Les **boutons d'accords restent disponibles**.
Ouvrir Texte/couleur ou Alignement ne restaure pas le titre pendant la saisie.
La fermeture du clavier seule ne fait pas nécessairement réapparaître l'en-tête :
c'est la sortie du focus du contenu qui le permet.

Sur téléphone, les accords restent sur leur rangée horizontale, au-dessus de la
barre d'actions. Retoucher le texte ou reprendre la saisie referme les panneaux
secondaires, sans masquer les accords.

Sur tablette, une seule ligne contient :

**Texte/couleur → Alignement → accords → ✓ → ×**

Les deux premières icônes sont blanches. Faites glisser horizontalement les accords
s'ils ne tiennent pas dans la place disponible ; les autres actions restent fixes.
Il n'y a ni ligne « Accords », ni barre de validation séparée en bas, ni palette
latérale. Cette disposition gagne une ligne sans modifier celle du téléphone.
Sur tablette, un panneau reste ouvert jusqu'à sa fermeture par son bouton, l'ouverture
de l'autre panneau ou le choix d'une action ; le repli automatique au retoucher est
propre au téléphone. L'interface dépend de la largeur disponible : une rotation ou
le mode multifenêtre peut changer la disposition proposée.

## Conservation et limites actuelles

Accords, mise en forme et couleurs restent dans le texte enregistré. Les espaces
et lignes vides aux extrémités peuvent être retirés lors de l'enregistrement ; les
espaces et retours à la ligne à l'intérieur du texte sont conservés.
Une sauvegarde complète protège le contenu du catalogue, couleurs comprises,
mais ne transporte pas actuellement la palette personnalisée ni le choix d'alignement.

Cette intégration ne synchronise pas les accords avec l'audio. Elle ne propose pas
encore de transposition, de capo, d'import/export dédié de fichiers ChordPro, de
masquage des accords ou de zoom propre au Prompteur. Le réglage de taille des paroles
du Lecteur concerne les paroles synchronisées. Les mots très longs et les accords
très serrés peuvent demander une mise en page manuelle.

## Retirer un texte d’une playlist

Supprimer son occurrence de la playlist ne supprime pas nécessairement le texte du catalogue général.

Vérifiez la formulation de la confirmation avant de supprimer :

- **retirer de la playlist** conserve le texte général ;
- **supprimer le texte** peut retirer son contenu du catalogue et invalider ses occurrences.

## Matériel de navigation

Lorsque le prompteur possède le focus, certaines commandes matérielles Précédent/Suivant et Début/Fin peuvent agir sur le texte. Leur disponibilité dépend du périphérique Android connecté.

Testez toujours un clavier, une télécommande ou une pédale avant de l’utiliser en concert.

## Problèmes courants

### Le texte créé n’apparaît pas dans la playlist

S’il a été créé depuis la Bibliothèque, c’est normal. Ajoutez-le ensuite à la playlist ou recréez le parcours depuis la playlist concernée.

### Le texte défile trop vite ou trop lentement

Utilisez le contrôle latéral de vitesse. Le réglage est conservé pour ce texte.

### Les commandes débordent sur tablette

Revenez au cockpit, rouvrez le texte dans le panneau droit et vérifiez que le mode tablette partagé est actif. Évitez de forcer une taille d’affichage Android inhabituelle juste avant une prestation.

### Le texte ne suit pas la musique

Un texte défilant n’est pas synchronisé à l’audio. Pour suivre automatiquement un morceau, créez ou importez des paroles LRC et utilisez le Lecteur.

### Le texte a disparu après une restauration

Vérifiez que la sauvegarde complète contenait les textes défilants. La commande actuelle **Mettre à jour la bibliothèque** ne met pas encore à jour ce catalogue ; effectuez une nouvelle sauvegarde complète après avoir créé ou modifié des textes importants.

Chapitre suivant : [Préparer les niveaux avec LEVELS](13-levels-et-niveaux-des-morceaux.md).
