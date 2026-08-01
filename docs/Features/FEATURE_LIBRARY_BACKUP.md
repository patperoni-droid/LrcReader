# FEATURE — Sauvegarde et mise à jour de la bibliothèque SMP

## Statut

Ce document formalise la philosophie produit et le cycle de vie cible de la
sauvegarde globale de la bibliothèque SMP.

Il s'agit d'une référence d'architecture et d'UX pour un chantier futur. Il ne
décrit pas une fonctionnalité déjà entièrement implémentée.

Le comportement actuellement livré de l'export et de la restauration reste
décrit dans `FEATURE_EXPORT_BACKUP.md`. Les hypothèses techniques historiques de
`FEATURE_BACKUP_V2.md` devront être rediagnostiquées avant toute implémentation.
Pour le futur cycle de vie utilisateur, le présent document remplace le libellé
historique **Synchroniser la sauvegarde** par **Mettre à jour la bibliothèque**.

---

## Problème utilisateur

SMP conserve localement le travail effectué dans l'application. Après avoir
importé ou modifié des morceaux, l'utilisateur peut fermer puis rouvrir
l'application et retrouver sa bibliothèque dans le même état.

Cette persistance locale peut donner l'impression que le travail est déjà
protégé. Elle ne constitue pourtant pas une sauvegarde de sécurité contre :

- la panne ou la perte de l'appareil ;
- un changement d'appareil ;
- la désinstallation de l'application ;
- une corruption du stockage local ;
- toute autre perte du runtime interne.

Règle produit :

> Voir ses morceaux dans SMP signifie qu'ils sont disponibles sur l'appareil,
> pas qu'ils sont protégés par une sauvegarde externe.

La persistance locale ne doit jamais être présentée comme une sauvegarde de
sécurité.

---

## Modèle mental utilisateur

La sauvegarde doit reprendre le modèle mental d'un projet de MAO, par exemple
dans Cubase :

1. le musicien travaille dans son projet ;
2. la première sauvegarde crée une référence durable identifiable ;
3. les sauvegardes suivantes mettent cette référence à jour ;
4. le musicien prend naturellement l'habitude de sauvegarder avant de quitter.

La notion centrale visible par l'utilisateur est :

> Ma bibliothèque SMP contient mon travail.

L'interface ne doit pas exiger qu'il comprenne les notions techniques de
`SongUnit`, d'archive `.smp`, de `config.json`, de `meta.json`, de stockage
runtime ou de normalisation.

La bibliothèque interne normalisée reste néanmoins la source de vérité
technique. La sauvegarde est une copie durable reconstruite depuis cette source ;
elle ne devient jamais le runtime de travail.

---

## Cycle de vie officiel

### Avant la première sauvegarde

Le menu affiche :

> Sauvegarder la bibliothèque

Cette action :

- permet à l'utilisateur de choisir la première destination durable ;
- crée la première sauvegarde globale de la bibliothèque ;
- établit la référence qui pourra ensuite être mise à jour.

### Après la première sauvegarde

Le menu affiche :

> Mettre à jour la bibliothèque

Cette action met à jour la sauvegarde de référence existante afin qu'elle
représente l'état actuel de la bibliothèque.

La première sauvegarde et les mises à jour suivantes doivent rester deux états
UX clairement distincts. Leur implémentation exacte devra réutiliser les
pipelines SMP de sauvegarde existants et conserver la rétrocompatibilité.

---

## État conceptuel de la bibliothèque

Le futur diagnostic devra permettre de représenter au minimum trois états :

1. **Jamais sauvegardée** : aucune sauvegarde durable de référence n'a encore
   été créée.
2. **Sauvegardée et à jour** : aucune modification pertinente n'est intervenue
   depuis la dernière sauvegarde ou mise à jour réussie.
3. **Modifiée depuis la dernière sauvegarde** : le runtime contient au moins une
   modification qui n'est pas encore reflétée dans la sauvegarde de référence.

Ce document n'impose pas encore de mécanisme technique, de compteur de version,
d'empreinte ou de journal de modifications.

### Modifications à étudier

Le diagnostic du code réel devra déterminer précisément quelles opérations
rendent la bibliothèque modifiée, notamment :

- import ou suppression d'un morceau ;
- création, suppression, renommage ou réorganisation d'une playlist ;
- modification des groupes et de la structure des playlists ;
- modification des paroles ou des accords ;
- création, modification ou suppression d'une variante ;
- modification d'un Arrangement ;
- ajout ou modification d'annotations ;
- modification de réglages persistants appartenant à la bibliothèque ou à une
  SongUnit ;
- changement de structure globale de la bibliothèque ;
- toute future donnée transportée par la sauvegarde complète.

Cette liste est une base d'analyse, pas encore le contrat technique définitif.

---

## Fermeture de l'application

### Bibliothèque inchangée

Si rien n'a changé depuis la dernière sauvegarde ou mise à jour :

- la fermeture reste normale ;
- aucun message n'est affiché.

### Modifications avant la première sauvegarde

Si la bibliothèque n'a jamais été sauvegardée et contient des modifications non
protégées, afficher :

> Voulez-vous vraiment quitter sans sauvegarder votre bibliothèque ?

Actions :

- **Oui** : quitter sans sauvegarder ;
- **Annuler** : revenir dans l'application.

### Modifications après la première sauvegarde

Si une sauvegarde de référence existe mais n'est plus à jour, afficher :

> Voulez-vous vraiment quitter sans mettre à jour votre bibliothèque ?

Actions :

- **Oui** : quitter sans mettre à jour ;
- **Annuler** : revenir dans l'application.

### Règle d'interaction

La boîte de dialogue de fermeture ne lance jamais directement une sauvegarde ou
une mise à jour.

Après **Annuler**, l'utilisateur revient dans l'application et déclenche lui-même
l'action **Sauvegarder la bibliothèque** ou **Mettre à jour la bibliothèque**
depuis le menu.

L'objectif est d'installer une habitude claire sans rendre la fermeture
intrusive ni dissimuler une opération lourde.

---

## Sécurité et stabilité

Toute future implémentation doit respecter les règles suivantes :

- aucune sauvegarde automatique silencieuse ;
- aucune opération lourde pendant une utilisation Live ;
- aucune écriture bloquante sur le thread principal ;
- construction de la sauvegarde à partir du runtime normalisé, jamais à partir
  d'une ancienne archive considérée comme source de vérité ;
- publication atomique : une sauvegarde incomplète ne doit jamais remplacer une
  sauvegarde valide ;
- échec explicite et état précédent préservé si la mise à jour ne peut pas être
  finalisée ;
- compatibilité ascendante et lecture des sauvegardes historiques ;
- restauration vérifiable sur un appareil réellement réinstallé ;
- aucune perte silencieuse de SongUnit, variante, playlist, groupe ou donnée
  transportée ;
- stabilité supérieure à la commodité et aux optimisations.

Une mise à jour peut reconstruire entièrement la sauvegarde plutôt que tenter
une synchronisation différentielle si cette approche est plus sûre. Le choix
exact devra être confirmé par le diagnostic technique.

---

## Portée

Cette fonctionnalité concerne exclusivement la sauvegarde globale de la
bibliothèque SMP et de l'état applicatif prévu par ce type de sauvegarde.

Elle ne doit pas être confondue avec :

- le partage ou l'export d'un morceau individuel en `.smp` ;
- la sérialisation d'une seule SongUnit ;
- le partage ciblé d'une variante ;
- les états temporaires d'une session ;
- Android Auto Backup ;
- le stockage runtime interne sous `/files/tracks/<songId>/` ;
- SMP Sync ou un transfert direct entre appareils.

La présence des morceaux dans le stockage runtime assure leur disponibilité
locale. Seule une sauvegarde durable externe et restaurable assure leur
protection contre la perte de l'appareil ou du runtime.

---

## Expérience utilisateur attendue

La sauvegarde doit donner au musicien le même sentiment de sécurité qu'un projet
Cubase enregistré dans un dossier durable, identifiable et réutilisable.

L'UX doit rendre immédiatement compréhensible :

- si aucune sauvegarde n'a encore été créée ;
- si la sauvegarde existante est à jour ;
- si le travail local a changé depuis la dernière mise à jour ;
- quelle action protège maintenant le travail : **Sauvegarder** ou **Mettre à
  jour**.

Les détails internes du format SMP restent invisibles. Les messages doivent
parler de bibliothèque et de travail sauvegardé, pas de fichiers techniques.

---

## Chantiers recommandés

L'ordre de travail recommandé est :

1. diagnostiquer le mécanisme actuel de sauvegarde et de restauration dans le
   code réel ;
2. identifier de manière fiable la première sauvegarde et sa cible durable ;
3. définir et valider la détection des modifications pertinentes ;
4. créer l'action **Mettre à jour la bibliothèque** en réutilisant le pipeline
   existant ;
5. adapter les libellés selon l'état de la bibliothèque ;
6. intercepter la fermeture sans perturber Android, la navigation ou la lecture
   Live ;
7. réaliser des tests de restauration réels, y compris après désinstallation et
   sur un autre appareil ;
8. mettre à jour la documentation utilisateur et le parcours d'onboarding.

Chaque étape doit suivre l'ordre officiel du projet : diagnostic, patch minimal,
compilation, validation, puis commit.

---

## Critères de validation futurs

La fonctionnalité ne pourra être considérée comme stable qu'après validation des
scénarios suivants :

- première sauvegarde créée et restaurée intégralement ;
- mise à jour d'une sauvegarde existante sans création involontaire d'une
  seconde référence ;
- échec pendant la mise à jour sans destruction de la sauvegarde précédente ;
- détection correcte d'une bibliothèque inchangée ou modifiée ;
- fermeture sans message lorsque tout est à jour ;
- avertissement adapté avant et après la première sauvegarde ;
- restauration des parents, variantes, paroles, accords, playlists, groupes,
  ordre et références `songId` ;
- compatibilité avec les sauvegardes existantes ;
- comportement cohérent sur téléphone et tablette ;
- absence de traitement lourd ou d'I/O bloquante pendant le Live.

---

## Principe final

> La bibliothèque interne SMP est l'espace de travail. La sauvegarde externe est
> la protection durable de ce travail.

L'application doit toujours distinguer clairement disponibilité locale et
sécurité externe.
