# COMPONENT — Playback Control

## Mission

Le `Playback Control` est le centre de contrôle universel du **Playback actif** de SMP.

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

Il contrôle toujours le Playback actif.

---

## Playback actif

À un instant donné, SMP possède un unique Playback actif.

Ce Playback peut avoir été démarré depuis :

- Lecteur Audio / Paroles ;
- Bibliothèque ;
- LEVELS ;
- Bus Principal ;
- toute autre interface autorisée.

Lorsqu'un nouveau morceau est lancé :

- il devient automatiquement le Playback actif ;
- le Playback précédent est arrêté conformément aux règles de coordination audio ;
- tous les Playback Control pilotent désormais ce nouveau Playback actif.

Le changement d'écran ne modifie jamais le Playback actif.

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
- aucune lecture automatique n'est déclenchée.

Cette séparation est volontaire.

Elle constitue le fondement du mode Live de SMP.

---

## Responsabilités

Le `Playback Control` :

- ne modifie jamais la playlist ;
- ne déplace jamais la sélection ;
- ne décide jamais du morceau suivant.

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
- réglage rapide ;
- affichage de la valeur.

Le bouton Play reste un bouton Play permanent.

Il ne devient jamais un bouton Pause.

En mode Live tablette, la sélection prépare uniquement le morceau ciblé.

Elle ne déclenche jamais une lecture automatiquement.

La sélection peut provenir :

- d'un appui tactile sur la playlist ;
- de la Navigation Séquentielle.

Le Playback actif continue normalement tant que le musicien n'appuie pas explicitement sur Play.

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

Lorsque ce morceau est lancé, il devient le nouveau Playback actif et le bouton Play redevient vert.

---

## Bouton Pause

Le bouton Pause agit uniquement sur le Playback actif.

Sur tablette, il constitue une commande distincte du bouton Play.

Il suspend la lecture du Playback actif.

Il ne modifie jamais :

- la sélection ;
- la playlist ;
- la Navigation Séquentielle.

Si le musicien a préparé un autre morceau par la sélection, cette préparation est conservée.

---

## Bouton Retour

Le bouton Retour remet uniquement le Playback actif au début.

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

Le lancement reste toujours une décision explicite du musicien via le bouton Play.

---

## Réglage rapide contextuel

Le Playback Control contient une zone de réglage rapide.

```
[-] Valeur [+]
```

Le composant ne possède jamais de réglage propre.

Il pilote toujours le réglage fourni par l'écran hôte.

Exemples :

- Player → Gain Playback ;
- LEVELS → Gain Playback ;
- Bibliothèque → Gain Playback ;
- Bus Principal → Gain Playback ;
- Fond sonore → Volume Fond sonore ;
- DJ → Volume DJ.

Le GainDrawer reste le réglage précis.

Le Playback Control permet un ajustement rapide.

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

Le lancement reste toujours une décision explicite du musicien.

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

Cette séparation constitue une règle officielle de l'architecture SMP.

---

## Règle fondamentale

En mode Live, SMP sépare volontairement :

- la préparation d'un morceau ;
- son exécution.

La préparation consiste à choisir ou déplacer la sélection.

L'exécution consiste à lancer effectivement le morceau.

Cette séparation garantit que le musicien conserve toujours la maîtrise du moment où un nouveau morceau est lancé.
