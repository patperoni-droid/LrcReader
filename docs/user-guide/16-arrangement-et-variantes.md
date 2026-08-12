# Créer un Arrangement et ses variantes

Arrangement permet de construire une autre structure à partir d’un morceau existant : raccourcir une introduction, répéter un refrain, masquer une section ou réordonner des passages. L’audio source n’est pas réécrit.

## Comprendre les variantes

Une variante Arrangement appartient à la famille de son morceau parent. Elle réutilise son audio, mais possède sa propre Structure et peut conserver ses propres paroles, accords et réglages associés.

Le morceau parent reste indispensable. Sa suppression entraîne également celle de ses variantes.

## Ouvrir Arrangement

1. sélectionnez le morceau parent dans la Bibliothèque ;
2. ouvrez ses outils ;
3. choisissez **Arrangement** ;
4. vérifiez le titre et la forme d’onde ;
5. définissez les points IN/OUT si nécessaire.

Sur tablette, la playlist peut être repliée pour donner toute la largeur à l’éditeur. Sur téléphone, un mode de compatibilité est disponible dans **Plus > Avancé** si la lecture directe de l’Arrangement pose problème.

## Créer un premier segment

1. sélectionnez une zone sur la piste horizontale ;
2. utilisez le mode `+` pour créer un segment avec la zone sélectionnée ;
3. utilisez le mode `-` pour conserver les parties situées à l’extérieur de la sélection ;
4. ajoutez le résultat à la Structure ;
5. lancez une préécoute.

Vous pouvez également ajouter un segment au début de la Structure.

## Organiser la Structure

Pour chaque occurrence, les actions disponibles peuvent inclure :

- renommer et choisir une couleur ;
- déplacer dans l’ordre ;
- dupliquer ;
- copier et coller ;
- rendre muette ;
- définir un nombre de répétitions.

Une occurrence est une utilisation d’un segment dans la Structure. Le même segment peut donc apparaître plusieurs fois sans dupliquer le fichier audio.

## Écouter l’Arrangement

Par défaut, la préécoute utilise la lecture directe des segments. Le Playback Control pilote la Structure préparée et enchaîne ses occurrences.

Sur certains téléphones, la compatibilité de cette lecture directe doit encore être vérifiée sur plusieurs modèles. Si vous constatez des coupures ou un mauvais enchaînement, activez le **mode de compatibilité Arrangement (téléphone)** dans **Plus > Avancé**, puis refaites un essai complet.

## Enregistrer une variante

1. testez la Structure du début à la fin ;
2. choisissez la création d’une variante virtuelle ;
3. donnez-lui un titre distinct ;
4. enregistrez-la dans la Bibliothèque.

En rouvrant une variante, vous pouvez mettre à jour celle-ci ou enregistrer le travail sous un nouveau nom. Vérifiez soigneusement le choix proposé afin de ne pas remplacer une version que vous souhaitez conserver.

## Assembler une version audio

L’assemblage crée explicitement un nouveau rendu, notamment au format WAV, puis l’importe comme morceau SMP dans la Bibliothèque. Cette opération est différente d’une variante virtuelle : elle produit un nouvel audio et peut demander du temps et de l’espace de stockage.

La compatibilité du rendu WAV et de la lecture assemblée doit être testée sur l’appareil utilisé avant une prestation.

## Partager une variante

Une variante peut être exportée en fichier `.smp`. L’export contient l’audio parent une seule fois et les données nécessaires à la variante ciblée. Consultez [Partager et exporter](24-partager-et-exporter.md).

## Précautions

- Conservez toujours le morceau parent.
- Donnez des noms explicites aux variantes : « radio », « sans intro », « rappel », etc.
- Testez les raccords au casque puis sur la sonorisation.
- Vérifiez paroles, accords et Timeline après une modification de Structure.
- Sauvegardez la Bibliothèque avant une réorganisation importante.

## Problèmes courants

### Une variante n’apparaît plus

Recherchez sa famille dans la Bibliothèque. Si le parent a été supprimé, ses variantes ont également été supprimées.

### Un raccord produit une coupure

Élargissez légèrement les limites du segment ou choisissez un point de coupe plus silencieux. Sur téléphone, essayez le mode de compatibilité.

### Les paroles ne correspondent plus

Une Structure différente change la chronologie. Utilisez les paroles propres à la variante et vérifiez leur synchronisation.

Chapitre suivant : [Utiliser la Timeline, MIDI, DMX et les annotations](17-timeline-midi-dmx-et-annotations.md).
