# Créer un Arrangement et ses variantes

Arrangement permet de préparer une autre version d’un morceau pour la scène : raccourcir une introduction, répéter un refrain, masquer une partie ou changer l’ordre des passages. Le fichier audio d’origine n’est pas modifié.

## Comprendre les segments et la Structure

Un **segment** est une partie du morceau, par exemple une intro, un couplet, un refrain ou un solo. Ses points IN et OUT indiquent où cette partie commence et se termine.

La **Structure** est la suite de blocs qui sera lue. Chaque bloc représente un segment placé dans le morceau final. Un même segment peut y apparaître plusieurs fois sans copier le fichier audio.

## Ouvrir Arrangement

1. ouvrez le morceau dans le Lecteur depuis la Bibliothèque ;
2. appuyez sur **ARR** ;
3. vérifiez le titre et la forme d’onde ;
4. réglez la grille si vous souhaitez placer plus facilement les coupes sur le rythme.

Sur tablette, vous pouvez masquer la playlist pour donner toute la largeur à l’éditeur. Sur téléphone, un mode de compatibilité est disponible dans **Plus > Avancé** si la lecture directe de la Structure pose problème.

## Créer un segment

1. placez les points **IN** et **OUT** autour de la partie à conserver ;
2. choisissez le mode `+` ;
3. appuyez sur **Ajouter** — ou **Ajout** sur l’affichage compact ;
4. le nouveau segment est ajouté à la Structure ;
5. faites un appui long sur son bloc pour le renommer, par exemple « Intro », « Couplet 1 » ou « Refrain ».

Le mode `-` sert plutôt à retirer la zone comprise entre IN et OUT : MusiMio conserve alors les parties situées avant et après cette zone.

## Organiser la Structure

Faites un appui long sur un bloc pour ouvrir son menu. Vous pouvez alors :

- le renommer et choisir une couleur ;
- le déplacer vers la gauche ou la droite ;
- régler ses **Répétitions** avec `−` et `+` ;
- choisir **Mettre en mute** pour le conserver dans la Structure sans le lire ;
- le supprimer.

Vous pouvez aussi déplacer les blocs directement lorsque les commandes de déplacement sont affichées. L’ordre visible de gauche à droite est l’ordre de lecture.

## Dupliquer un segment avec « Coller ici »

Le **segment source** est celui sur lequel vous venez de faire l’appui long. C’est ainsi que MusiMio sait quel segment reproduire : il n’existe ni commande **Copier** préalable, ni presse-papiers Arrangement.

1. placez la tête de lecture sur la limite de Structure où vous voulez insérer la copie ;
2. faites un appui long sur le segment que vous voulez reproduire ;
3. choisissez **Coller ici** ;
4. MusiMio crée une nouvelle occurrence de ce segment à l’emplacement choisi. Le segment original reste à sa place.

Exemple — ajouter une nouvelle occurrence de **Refrain** après **Couplet 2** :

**Structure initiale**

```text
Intro
Couplet 1
Refrain
Couplet 2
```

**Procédure**

- placez la tête après **Couplet 2** ;
- faites un appui long sur **Refrain** ;
- choisissez **Coller ici**.

**Résultat**

```text
Intro
Couplet 1
Refrain
Couplet 2
Refrain
```

Le premier **Refrain** reste présent.

Si **Coller ici** est grisé, touchez d’abord une frontière précise dans la Structure, puis rouvrez le menu du bloc à reproduire.

## Répéter ou dupliquer ?

- utilisez **Répétitions** pour rejouer immédiatement le même bloc plusieurs fois de suite ;
- utilisez **Coller ici** pour créer un nouveau bloc à un autre endroit, que vous pourrez ensuite déplacer ou modifier séparément.

## Écouter la Structure

Lancez la lecture de la Structure et écoutez-la du début à la fin. La tête de lecture suit les blocs dans leur ordre, tient compte des répétitions et saute les blocs en mute.

Vérifiez surtout les raccords entre deux segments. Sur certains téléphones, si vous entendez des coupures ou un mauvais enchaînement, activez le **Mode de compatibilité Arrangement** dans **Plus > Avancé**, puis refaites un essai complet.

## Enregistrer une variante

Une variante Arrangement apparaît dans la Bibliothèque comme une autre version du morceau. Elle réutilise l’audio du morceau parent, mais conserve sa propre Structure ainsi que ses paroles, accords et réglages associés.

1. testez la Structure du début à la fin ;
2. appuyez sur **Bibliothèque** ;
3. donnez un nom clair à la variante, par exemple « Radio », « Sans intro » ou « Rappel » ;
4. enregistrez-la.

Le morceau parent reste indispensable. Sa suppression entraîne également celle de ses variantes.

## Modifier une variante existante

Ouvrez la variante depuis la Bibliothèque, puis revenez dans Arrangement avec **ARR**. Après vos modifications :

- choisissez **Mettre à jour** pour remplacer la Structure de cette variante ;
- choisissez **Nouvelle variante** pour conserver la variante ouverte et enregistrer une autre version.

## Assembler une version audio

**Assembler** crée un nouveau fichier audio, notamment au format WAV, puis l’importe comme morceau live dans la Bibliothèque. Cette opération est différente d’une variante : elle produit un nouvel audio et peut demander du temps et de l’espace de stockage.

Testez toujours le fichier assemblé sur l’appareil utilisé avant une prestation.

## Partager une variante

Une variante peut être exportée en fichier `.smp`. L’export contient l’audio parent une seule fois et les données nécessaires à la variante ciblée. Consultez [Partager et exporter](24-partager-et-exporter.md).

## Précautions

- Conservez toujours le morceau parent.
- Testez les raccords au casque puis sur la sonorisation.
- Vérifiez les paroles, les accords et la Timeline après une modification de Structure.
- Sauvegardez la Bibliothèque avant une réorganisation importante.

## Problèmes courants

### « Coller ici » est indisponible

Touchez une frontière entre deux blocs, avant le premier bloc ou après le dernier, puis ouvrez de nouveau le menu du bloc à reproduire.

### Une variante n’apparaît plus

Recherchez sa famille dans la Bibliothèque. Si le parent a été supprimé, ses variantes ont également été supprimées.

### Un raccord produit une coupure

Élargissez légèrement les limites du segment ou choisissez un point de coupe plus silencieux. Sur téléphone, essayez le mode de compatibilité.

### Les paroles ne correspondent plus

Une Structure différente change la chronologie. Utilisez les paroles propres à la variante et vérifiez leur synchronisation.

Chapitre suivant : [Utiliser la Timeline, MIDI, DMX et les annotations](17-timeline-midi-dmx-et-annotations.md).
