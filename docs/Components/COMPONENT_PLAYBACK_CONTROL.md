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

Le mode actuel reste entièrement manuel. Le projet de bouton AUTO est suspendu et ne fait pas partie du composant implémenté.

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
- Track Console ;
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
- aucune lecture immédiate n'est déclenchée.

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

## Projet AUTO — suspendu

Le bouton AUTO n'est implémenté ni sur tablette ni sur téléphone.

Son étude est mise de côté tant que le comportement manuel du Playback Control, de la sélection officielle et des transitions n'est pas stabilisé sur tous les écrans concernés.

La documentation de conception conservée dans la roadmap V2.5 décrit uniquement une possibilité future. Elle ne constitue pas un comportement disponible.

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

Le lancement reste une décision explicite du musicien via le bouton Play.

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
- Bus Principal → Gain Playback ;
- Track Console → Gain Playback.

Le GainDrawer reste le réglage précis.

Le Playback Control permet un ajustement rapide.

Le volume du Fond sonore est réglé par les commandes locales du lecteur Fond sonore.

Le volume des autres moteurs audio n'est jamais piloté par Playback Control.

---

## Pipeline de gain stable

Le téléphone et la tablette utilisent exactement le même chemin de gain dans `AudioEngine`.

- un étage de gain léger est installé à la création de chaque Player principal ou de transition ;
- le passage autour de `0 dB` ne sélectionne jamais un autre pipeline Player ;
- une modification de gain ne doit jamais arrêter, libérer, reconstruire, préparer ou repositionner le Player actif ;
- le gain neutre ou négatif conserve le réglage de volume ExoPlayer existant ;
- le gain positif utilise uniquement l'étage de gain dédié ;
- le gain positif n'active jamais SoundTouch à lui seul ;
- la sélection du pipeline SoundTouch reste réservée à un pitch ou un speed non neutre ;
- le chemin neutre copie les données PCM dans un tampon détenu par le processeur avant de les transmettre, afin d'éviter toute réutilisation prématurée du tampon ExoPlayer ;
- le son neutre, le pitch, le speed et les passages `-1 / 0 / +1 dB` ont été validés sur appareil réel ;
- les futurs EQ, compresseur et limiteur restent hors de ce correctif de stabilisation du gain.

---

## Intégration Track Console

Track Console affiche le `Playback Control` officiel du Playback principal.

En mode Live tablette :

- la sélection reste pilotée par la playlist gauche ;
- sélectionner un autre titre ne coupe pas le titre actif ;
- le bouton Play devient jaune ;
- un appui sur Play lance le titre sélectionné avec le pipeline Playback officiel ;
- les commandes Lecture, Pause, Retour début, position et gain utilisent exactement les mêmes actions que dans le Player.

Track Console ne possède aucune copie locale de la logique Playback.

L'ancienne zone basse `TOUCH HERE TO RETURN` n'existe plus. La navigation hors de Track Console utilise les commandes permanentes de l'application, notamment la navigation supérieure du mode Split tablette.

---

## Intégration Timeline — implémentée

La Timeline utilise un seul contrôle principal de lecture : le `Playback Control` officiel du Playback principal.

Règles validées :

- la Timeline affichée appartient toujours au morceau actuellement actif dans le Playback principal ;
- sélectionner un autre morceau dans la playlist ne change pas la Timeline affichée et n'interrompt pas la lecture ;
- lorsque la sélection diffère du Playback actif, le bouton Play devient jaune selon la règle commune ;
- un appui sur Play jaune lance le morceau sélectionné avec le pipeline Playback officiel ;
- seulement après ce lancement, le nouveau morceau devient actif et sa Timeline remplace la précédente ;
- l'écran Timeline reste ouvert pendant ce changement de morceau ;
- le `Playback Control` reste placé en bas de l'écran Timeline, comme dans les autres écrans tablette ;
- les anciennes commandes locales Play, Pause et Retour de la Timeline ont été supprimées ;
- aucun lecteur secondaire, lecteur d'aperçu ou état audio propre à la Timeline ne doit être créé.

Cette intégration est implémentée et validée sur tablette.

---

## Intégration Arrangement — conception validée, implémentation à venir

En mode Arrangement tablette :

- le `Playback Control` officiel reste fixé en bas de l'écran et toujours visible ;
- le contenu d'édition ne doit jamais le repousser hors de l'écran ;
- la playlist de gauche est remplacée uniquement dans ce mode par la liste unique ordonnée des occurrences de segments ;
- le composant continue de contrôler le Playback principal ;
- la preview de Structure reste un moteur secondaire isolé et ne doit pas être présentée comme une variante du `Playback Control` officiel.

La définition complète de l'interface à liste unique, des répétitions, du mute et de la duplication se trouve dans `docs/Features/FEATURE_TEMPO_ARRANGEMENT.md`.

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

Le lancement est confirmé directement avec Play.

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

Sur téléphone, le comportement historique reste prioritaire.

Cette séparation constitue une règle officielle de l'architecture SMP.

---

## Règle fondamentale

En mode Live, SMP sépare volontairement :

- la préparation d'un morceau ;
- son exécution.

La préparation consiste à choisir ou déplacer la sélection.

L'exécution consiste à lancer effectivement le morceau.

Cette séparation garantit que le musicien conserve la maîtrise du lancement par validation manuelle avec Play.
