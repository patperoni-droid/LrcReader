# COMPONENT — Playback Control

## Mission

Le `Playback Control` est le centre de contrôle universel du **Playback principal actif** de SMP.

Il permet au musicien de contrôler immédiatement le morceau actuellement en lecture sans revenir systématiquement dans le Player.

Le Player reste l'écran complet.

Le `Playback Control` est un composant officiel, réutilisable et indépendant des écrans.

---

## Philosophie

Le `Playback Control` ne remplace jamais le Player.

Le Player conserve notamment :

- les paroles ;
- les accords ;
- la timeline détaillée ;
- Define Next ;
- les fonctions avancées.

Le `Playback Control` regroupe uniquement les commandes les plus utilisées pendant une répétition ou un concert.

Son objectif est de permettre au musicien de conserver le contrôle du concert sans changer d'écran.

En mode Live (tablette), SMP distingue volontairement deux notions :

- le morceau actuellement en lecture ;
- le morceau actuellement sélectionné.

Cette séparation permet de préparer le morceau suivant sans interrompre celui qui est en cours.

**La navigation prépare.**

**Le bouton Play confirme.**

**Sur tablette uniquement, le bouton AUTO peut armer cette confirmation pour la fin naturelle du morceau en cours.**

---

## Principes SMP

- composant officiel ;
- même ergonomie partout ;
- toujours reconnaissable ;
- compact ;
- priorité téléphone ;
- compatible tablette ;
- aucune duplication de logique.

Le `Playback Control` appartient à SMP et jamais à un écran particulier.

Il ne contrôle jamais un écran.

Il contrôle toujours le Playback principal actif.

Il ne contrôle jamais les moteurs audio complémentaires, notamment le Fond sonore.

---

## Playback principal actif

À un instant donné, SMP possède un unique Playback principal actif.

Ce Playback principal peut avoir été démarré depuis :

- Lecteur Audio / Paroles ;
- Bibliothèque ;
- LEVELS ;
- Bus Principal ;
- toute autre interface autorisée.

Lorsqu'un nouveau morceau est lancé :

- il devient automatiquement le Playback actif ;
- le Playback précédent est arrêté conformément aux règles de coordination audio ;
- tous les Playback Control pilotent désormais ce nouveau Playback principal actif.

Le changement d'écran ne modifie jamais le Playback principal actif.

Le Fond sonore n'est pas un Playback principal.

Il possède son propre lecteur, son propre état et ses propres commandes locales.

Même sur l'écran Fond sonore, `Playback Control` conserve sa mission unique : piloter le Playback principal.

---

## Sélection

Le Playback actif et la sélection représentent deux états distincts du système SMP.

Ils peuvent être synchronisés.

Exemple :

- le morceau sélectionné est aussi le morceau actuellement en lecture.

Ils peuvent aussi être désynchronisés.

Exemple :

- un morceau continue de jouer ;
- le musicien prépare un autre morceau en le sélectionnant.

En mode Live tablette, toute méthode de sélection prépare uniquement un morceau.

Cette sélection peut provenir :

- d'un appui tactile sur la playlist ;
- de la Navigation Séquentielle.

Dans tous les cas :

- le morceau devient sélectionné ;
- le Playback actif continue normalement ;
- aucune lecture immédiate n'est déclenchée ;
- si AUTO est armé, cette sélection peut devenir la prochaine cible automatique.

Cette séparation est volontaire.

Elle constitue le fondement du mode Live de SMP.

---

## Responsabilités

Le `Playback Control` :

- ne modifie jamais la playlist ;
- ne déplace jamais la sélection ;
- n'invente jamais un morceau suivant hors de la sélection et de l'ordre officiel de la playlist.

Il agit uniquement sur le Playback actif et utilise la sélection fournie par la Navigation Séquentielle.

---

## Composition

### Téléphone

Le Playback Control reste volontairement compact.

Il comprend :

- barre de progression ;
- temps courant ;
- temps total ;
- Retour début ;
- bouton Lecture / Pause ;
- réglage rapide ;
- affichage de la valeur.

Cette ergonomie reste prioritaire.

Sur téléphone, le comportement historique est conservé.

Un appui sur un morceau de la playlist lance immédiatement sa lecture.

Cette règle privilégie la rapidité d'action sur un écran compact.

Le téléphone n'affiche pas de bouton AUTO et ne propose pas ce mode d'enchaînement dans le Playback Control.

Cette exclusion est volontaire afin de ne pas surcharger l'interface téléphone.

---

### Tablette (Mode Live)

La console tablette est conçue pour fonctionner **en complément de la Navigation Séquentielle**.

Elle sépare explicitement les commandes Play et Pause.

Elle comprend :

- barre de progression ;
- temps courant ;
- temps total ;
- Retour début ;
- bouton Play ;
- bouton Pause ;
- bouton AUTO ;
- réglage rapide ;
- affichage de la valeur.

Le bouton Play reste un bouton Play permanent.

Il ne devient jamais un bouton Pause.

En mode Live tablette, la sélection prépare uniquement le morceau ciblé.

Elle ne déclenche jamais une lecture automatiquement.

La sélection peut provenir :

- d'un appui tactile sur la playlist ;
- de la Navigation Séquentielle.

Le Playback actif continue normalement tant que le musicien n'appuie pas explicitement sur Play ou que la fin du morceau n'est pas atteinte avec AUTO armé.

Le bouton Play devient jaune lorsque la sélection ne correspond plus au Playback actif.

La Navigation Séquentielle reste un composant indépendant chargé de gérer la sélection.

Cette ergonomie est spécifique au mode Live.

---

## Bouton Play

Le bouton Play possède toujours la même responsabilité :

> lancer le morceau actuellement sélectionné.

Sa fonction ne change jamais.

Le lancement constitue toujours une décision explicite du musicien.

Le bouton Play ne déclenche jamais automatiquement une lecture.

Le bouton Play ne modifie jamais la sélection.

Il utilise simplement la sélection courante comme cible de lecture.

Sur tablette, le bouton Play reste toujours visible et conserve toujours sa signification.

Il ne devient jamais un bouton Pause, même lorsque le Playback actif est en lecture ou suspendu.

Sa couleur indique uniquement la relation entre :

- la sélection ;
- le Playback actif.

### Vert

Le morceau sélectionné correspond au Playback actif.

Lecture et sélection sont synchronisées.

L'état lecture ou pause du Playback actif ne transforme pas le bouton Play en bouton Pause.

### Jaune

Le musicien a sélectionné un autre morceau.

Le Playback actif continue normalement.

Cette sélection prépare un autre morceau sans déclencher de lecture automatique.

Le bouton Play indique qu'un nouveau morceau est prêt à être lancé.

Si AUTO est armé, ce morceau préparé devient prioritaire pour le prochain lancement automatique.

Lorsque ce morceau est lancé, il devient le nouveau Playback actif et le bouton Play redevient vert.

---

## Bouton AUTO — Tablette uniquement

Le bouton AUTO est réservé au Playback Control du mode Live tablette.

Il active ou désactive l'enchaînement automatique du Playback principal sur tablette.

Il reste une commande distincte du bouton Play et ne change jamais la signification ni la couleur du bouton Play.

Il n'est jamais affiché sur téléphone et ne modifie jamais le comportement historique du Playback Control téléphone.

### AUTO désactivé

- le comportement manuel actuel est conservé ;
- un bouton Play jaune indique uniquement qu'un autre morceau est préparé ;
- le musicien doit appuyer sur Play pour le lancer.

### AUTO activé

À la fin naturelle du morceau actif ou lorsque son point OUT officiel est atteint :

1. une cible explicitement définie par `Define Next` reste prioritaire ;
2. sinon, un morceau sélectionné et préparé, signalé par le bouton Play jaune, devient la prochaine cible ;
3. sinon, SMP utilise le prochain morceau jouable dans l'ordre officiel de la playlist.

Le lancement utilise toujours le pipeline Playback officiel.

AUTO n'ajoute :

- aucun délai artificiel entre les titres ;
- aucun silence supplémentaire ;
- aucun compte à rebours ;
- aucune analyse du silence ou de la voix.

Les blancs, fins longues et introductions présents dans les morceaux restent les seuls espaces musicaux entre les titres.

Le nouveau morceau démarre dès que la fin effective du morceau courant est confirmée par le moteur de lecture.

Si aucune cible jouable et valide ne peut être résolue :

- aucun morceau n'est lancé ;
- le Playback s'arrête proprement ;
- aucune cible de secours ambiguë n'est inventée.

### État live

Sur tablette, l'état AUTO doit être immédiatement visible et ne jamais dépendre de l'écran affiché.

Il doit survivre :

- aux recompositions Compose ;
- aux changements d'écran ;
- aux changements de sélection pendant le morceau.

Pour éviter un démarrage inattendu après une nouvelle ouverture de l'application, AUTO est un état de session et revient désactivé après un redémarrage complet.

Le musicien peut désarmer AUTO à tout moment sans interrompre le morceau en cours.

---

## Bouton Pause

Le bouton Pause agit uniquement sur le Playback principal actif.

Sur tablette, il constitue une commande distincte du bouton Play.

Il suspend la lecture du Playback principal actif.

Il ne modifie jamais :

- la sélection ;
- la playlist ;
- la Navigation Séquentielle.

Si le musicien a préparé un autre morceau par la sélection, cette préparation est conservée.

---

## Bouton Retour

Le bouton Retour remet uniquement le Playback principal actif au début.

Il ne modifie jamais :

- la sélection ;
- l'état Lecture / Pause.

Comportement :

- lecture → retour à 0:00 et lecture conservée ;
- pause → retour à 0:00 et pause conservée ;
- aucun Playback actif → aucune action.

---

## Navigation Séquentielle

Les commandes ▲ ▼ ne déclenchent jamais une lecture.

Elles préparent uniquement la sélection dans la playlist.

En mode Live tablette, elles produisent le même résultat qu'un appui tactile sur un morceau :

- la sélection change ;
- le Playback actif continue ;
- le bouton Play devient jaune si un autre morceau est préparé.

Cette sélection peut être préparée pendant qu'un autre morceau continue de jouer.

Lorsque AUTO est désactivé, le lancement reste une décision explicite du musicien via le bouton Play.

Lorsque AUTO est activé, la Navigation Séquentielle continue uniquement à préparer la sélection ; seul le Playback Control déclenche le lancement à la fin effective du morceau actif.

---

## Réglage rapide contextuel

Le Playback Control contient une zone de réglage rapide du Playback principal.

```
[-] Valeur [+]
```

Le composant ne possède jamais de réglage propre.

Il pilote uniquement un réglage lié au Playback principal.

Exemples :

- Player → Gain Playback ;
- LEVELS → Gain Playback ;
- Bibliothèque → Gain Playback ;
- Bus Principal → Gain Playback.

Le GainDrawer reste le réglage précis.

Le Playback Control permet un ajustement rapide.

Le volume du Fond sonore est réglé par les commandes locales du lecteur Fond sonore.

Le volume des autres moteurs audio n'est jamais piloté par Playback Control.

---

## Principes UX

Le Playback Control doit :

- être immédiatement identifiable ;
- conserver une géométrie stable ;
- conserver les commandes au même emplacement ;
- conserver les mêmes commandes sur un même périphérique ;
- ne jamais changer la signification d'un bouton ;
- garder Play et Pause comme deux commandes distinctes en mode Live tablette ;
- utiliser les couleurs uniquement pour représenter un état ;
- rester utilisable sans apprentissage.

La mémoire musculaire du musicien est prioritaire.

En mode Live tablette, toutes les méthodes de sélection possèdent le même comportement.

La préparation d'un morceau est indépendante de son lancement.

Le lancement est soit confirmé directement avec Play, soit préalablement autorisé par l'armement explicite de AUTO.

Cette différence avec le téléphone est volontaire :

- le téléphone privilégie la rapidité ;
- la console Live tablette privilégie la sécurité.

---

## Statut

**Statut : ARCHITECTURE VALIDÉE**

Le Playback Control constitue désormais la console officielle de pilotage du Playback actif.

En mode Live (tablette), il sépare volontairement :

- la préparation du morceau suivant ;
- le contrôle du morceau actuellement en lecture.

Sur tablette, le mode AUTO ajoute un enchaînement préalablement armé sans supprimer cette séparation.

Sur téléphone, cette évolution ne change rien : aucun bouton AUTO n'est ajouté et le comportement historique reste prioritaire.

Cette séparation constitue une règle officielle de l'architecture SMP.

---

## Règle fondamentale

En mode Live, SMP sépare volontairement :

- la préparation d'un morceau ;
- son exécution.

La préparation consiste à choisir ou déplacer la sélection.

L'exécution consiste à lancer effectivement le morceau.

Cette séparation garantit que le musicien conserve la maîtrise du mode de lancement :

- validation manuelle avec Play ;
- ou lancement automatique armé à la fin effective du morceau.

AUTO ne crée jamais de temps supplémentaire entre deux titres.
