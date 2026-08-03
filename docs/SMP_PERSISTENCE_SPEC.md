# SMP PERSISTENCE SPEC — Modèle officiel de persistance

## Statut

Ce document est la référence normative de Stage Music Player pour la propriété
et le périmètre des données persistantes.

Il définit le modèle conceptuel que doivent respecter :

- l'export SMP ;
- l'import SMP ;
- la sauvegarde et la restauration de bibliothèque ;
- la future fonction **Mettre à jour la bibliothèque** ;
- SMP Sync ;
- toute future fonctionnalité persistante.

Ce document ne décrit pas une implémentation particulière. Il complète
`SMP_RULES.md`, qui conserve sa priorité générale en cas de conflit.

---

## 1. Principe fondamental

Toute donnée persistante faisant partie de la bibliothèque SMP appartient
obligatoirement à un seul des trois périmètres suivants :

1. **Famille SongUnit** ;
2. **État global de bibliothèque** ;
3. **Manifest**.

Les préférences d'appareil, de présentation et de session sont extérieures au
modèle de persistance de la bibliothèque. Elles ne constituent pas un quatrième
périmètre SMP.

Par exemple, le pitch ou un Arrangement décrit le morceau et appartient à la
Famille SongUnit. Le mode d'affichage des paroles ou le zoom de la Waveform
décrit le confort d'utilisation sur un appareil et reste une préférence locale.

Une donnée ne peut jamais être possédée simultanément par plusieurs périmètres.
Un périmètre peut référencer l'identité d'un autre périmètre, mais cette
référence ne lui transfère pas la propriété de la donnée.

Les données temporaires, les caches et les représentations entièrement
reconstructibles ne sont pas des données persistantes et n'appartiennent à
aucun de ces trois périmètres.

La question obligatoire avant toute évolution est :

> Dans quel périmètre appartient cette donnée ?

Aucune fonctionnalité persistante ne doit être développée tant que cette
question n'a pas reçu une réponse unique et explicite.

---

## 2. Périmètre 1 — Famille SongUnit

### 2.1 Définition

Une **Famille SongUnit** est l'unité persistante complète représentant un morceau
et toutes ses formes Arrangement.

Elle est composée de :

- une SongUnit parent ;
- zéro, une ou plusieurs variantes rattachées à ce parent.

La SongUnit parent possède l'audio source. Chaque variante possède sa propre
identité et ses propres données, mais dépend obligatoirement de ce parent.

Une Famille SongUnit est cohérente uniquement si son parent et toutes ses
variantes valides peuvent être reconstruits ensemble.

### 2.2 Identités

- `songId` est l'identité absolue d'une SongUnit parent ou variante.
- `sourceSongId` est l'identité immuable du parent d'une variante.
- le `songId` d'une variante est toujours différent de son `sourceSongId`.
- un rattachement variante vers parent ne change jamais automatiquement.

Il est interdit d'utiliser comme identité :

- le titre du morceau ;
- le nom d'un fichier ;
- un chemin ;
- une URI ;
- le nom d'une archive ;
- un emplacement de sauvegarde.

### 2.3 Données appartenant à la Famille

Appartient à la Famille SongUnit toute donnée dont le sens fonctionnel dépend du
contenu musical, de l'identité ou de l'exécution du parent ou d'une variante.

Être indexé par un `songId` ne suffit pas à rendre une donnée propriétaire de la
Famille. La classification dépend de sa signification fonctionnelle. Une
préférence locale peut utiliser un `songId` pour mémoriser un confort propre à
un morceau sans devenir pour autant une donnée transportable de sa Famille.

Cela comprend notamment :

- l'identité et les métadonnées persistantes du parent ;
- l'audio source du parent ;
- les paroles ;
- les accords ;
- les annotations liées au morceau ;
- les notes liées au parent ou à une variante ;
- les contenus de prompteur liés au morceau ;
- les titres personnalisés et autres métadonnées utilisateur liées au morceau ;
- les réglages persistants propres au morceau ;
- les réglages de lecture propres au morceau ;
- les données temporelles propres au morceau ;
- les événements MIDI propres au morceau ;
- les événements DMX propres au morceau ;
- la grille musicale ;
- le projet Arrangement du parent ;
- les segments et la Structure Arrangement ;
- les identités et métadonnées persistantes des variantes ;
- l'Arrangement propre à chaque variante ;
- les paroles, accords, annotations, réglages et autres données propres à une
  variante ;
- toute future donnée dont la durée de vie est liée à celle d'un parent ou
  d'une variante.

Les éléments optionnels restent la propriété de la Famille même lorsqu'ils sont
absents. L'absence d'un élément optionnel ne change ni l'identité ni le
périmètre de la Famille.

### 2.4 Règle parent / variantes

Une variante est une SongUnit distincte dans le runtime, mais elle n'est jamais
une unité autonome de sauvegarde.

Elle ne possède pas l'audio source et ne peut pas exister durablement sans son
parent.

Pour l'export, le partage, la sauvegarde, la restauration et les échanges entre
appareils :

- l'audio parent n'est transporté qu'une seule fois ;
- la variante est transportée avec les données nécessaires de son parent ;
- une variante n'est jamais sérialisée seule dans une archive audio-less ;
- un partage ciblé de variante reste un paquet adossé au parent ;
- une sauvegarde complète transporte toutes les variantes valides avec leur
  parent.

### 2.5 Unité de modification

Toute modification de l'un des éléments suivants rend la Famille SongUnit
entière modifiée :

- le parent ;
- une variante ;
- l'Arrangement ;
- les segments ou la Structure ;
- les paroles ;
- les accords ;
- les annotations ;
- les notes liées au morceau ;
- les réglages persistants ;
- les contenus temporels ou de prompteur ;
- tout futur contenu appartenant à la Famille.

La reconstruction d'une sauvegarde s'effectue donc toujours au niveau de la
Famille SongUnit. Elle ne s'effectue jamais au niveau d'une variante isolée.

### 2.6 Cycle de vie

- La création d'une variante modifie sa Famille.
- La modification d'une variante modifie sa Famille.
- La suppression d'une variante modifie sa Famille.
- La suppression du parent supprime nécessairement la Famille et ses variantes.
- La suppression d'une occurrence dans une playlist ne supprime pas la
  SongUnit correspondante et ne modifie pas sa Famille.

---

## 3. Périmètre 2 — État global de bibliothèque

### 3.1 Définition

L'**État global de bibliothèque** contient les données utilisateur qui décrivent
la bibliothèque dans son ensemble, son organisation ou des contenus autonomes
qui ne dépendent pas d'une Famille SongUnit unique.

Ce périmètre est indépendant des fichiers et contenus possédés par les Familles
SongUnit.

### 3.2 Données appartenant à l'État global

L'État global de bibliothèque comprend notamment :

- les playlists ;
- l'ordre des playlists ;
- les groupes internes des playlists ;
- l'ordre et les occurrences des éléments dans les playlists ;
- les couleurs et métadonnées propres aux playlists ;
- les références de playlists vers les `songId` des parents et variantes ;
- les familles de bibliothèque utilisées comme organisation utilisateur ;
- les relations et choix actifs de ces familles de bibliothèque ;
- les prompteurs globaux ;
- les notes globales ;
- les réglages persistants qui concernent la bibliothèque entière ;
- les autres éléments persistants de `state.json` qui décrivent l'état global
  restaurable ;
- toute future donnée qui reste significative indépendamment d'une Famille
  SongUnit unique.

### 3.3 Distinction entre les deux sens de « famille »

La **Famille SongUnit** définie par ce document signifie exclusivement :

> un parent et toutes ses variantes Arrangement.

Une **famille de bibliothèque** est une organisation utilisateur pouvant
référencer plusieurs SongUnit. Elle appartient à l'État global de bibliothèque
et ne possède pas les données des SongUnit qu'elle référence.

Ces deux notions ne doivent jamais être confondues.

### 3.4 Références vers les SongUnit

Les playlists, groupes et familles de bibliothèque peuvent référencer des
parents ou des variantes par leur `songId`.

Ces références appartiennent à l'État global. Les SongUnit référencées restent
la propriété de leur Famille SongUnit.

L'État global ne doit jamais dupliquer l'audio, les paroles, les accords,
l'Arrangement ou les autres contenus d'une Famille.

### 3.5 Frontière des données liées à un morceau

Une donnée liée exclusivement à un morceau ou à une variante appartient à sa
Famille SongUnit, même si son stockage physique historique se trouve dans un
fichier global.

Une donnée réellement indépendante des morceaux appartient à l'État global.

L'emplacement physique actuel ne décide donc pas du périmètre conceptuel. La
propriété fonctionnelle de la donnée est la seule règle.

---

## 4. Périmètre 3 — Manifest

### 4.1 Définition

Le **Manifest** décrit l'état d'une sauvegarde de bibliothèque.

Il n'est pas une donnée utilisateur et ne constitue jamais une source de vérité
musicale ou fonctionnelle.

### 4.2 Rôle

Le Manifest représente uniquement :

- l'identité de la sauvegarde ;
- l'état de la sauvegarde au moment de sa publication ;
- les Familles SongUnit présentes ;
- les éléments d'État global présents ;
- les identités nécessaires pour relier ces éléments ;
- les empreintes nécessaires pour reconnaître les modifications pertinentes ;
- les informations de format nécessaires pour interpréter la sauvegarde.

Il devient la référence descriptive de la future fonction **Mettre à jour la
bibliothèque**.

### 4.3 Limites

Le Manifest ne possède jamais :

- l'audio ;
- les paroles ;
- les accords ;
- les variantes ;
- les playlists ;
- les prompteurs ;
- les notes ;
- les réglages utilisateur ;
- toute autre donnée utilisateur.

Il ne fait que décrire la présence et l'état des données appartenant aux deux
autres périmètres.

La suppression ou la reconstruction du Manifest ne doit pas altérer le contenu
utilisateur de la sauvegarde.

---

## 5. Modèle officiel d'une bibliothèque persistante

Une bibliothèque SMP persistante est toujours composée de :

```text
Bibliothèque SMP
├── Familles SongUnit
│   ├── Parent A
│   │   └── Variantes de A
│   ├── Parent B
│   │   └── Variantes de B
│   └── ...
├── État global de bibliothèque
│   ├── Playlists et groupes
│   ├── Familles de bibliothèque
│   ├── Prompteurs globaux
│   ├── Notes globales
│   └── Réglages globaux persistants
└── Manifest
```

Formule officielle :

> Bibliothèque persistante = Familles SongUnit + État global + Manifest

Aucun quatrième périmètre persistant implicite de bibliothèque n'est autorisé.
Les préférences locales restent hors de ce modèle, comme défini au principe
fondamental.

---

## 6. Données non persistantes et dérivables

Les éléments suivants ne font pas partie du modèle persistant lorsqu'ils sont
entièrement reconstructibles à partir des données officielles :

- caches de forme d'onde ;
- fichiers PCM ou WAV temporaires ;
- fichiers temporaires de préparation ou d'export ;
- index reconstruits depuis les Familles et l'État global ;
- caches de scan ;
- représentations d'affichage dérivées ;
- fichiers temporaires ou de secours produits pendant une écriture atomique.

Ces éléments peuvent exister dans le runtime pour la performance ou la sûreté,
mais ils ne doivent jamais devenir nécessaires pour restaurer fidèlement une
bibliothèque.

Si une donnée apparemment dérivable contient une information utilisateur qui ne
peut pas être reconstruite, elle doit être reclassée avant toute évolution dans
la Famille SongUnit ou dans l'État global.

---

## 7. Règles officielles de persistance

1. `songId` est l'unique identité d'une SongUnit.
2. `sourceSongId` est le rattachement immuable d'une variante à son parent.
3. Un titre, un nom de fichier, un chemin ou une URI n'est jamais une identité.
4. Une Famille SongUnit est composée d'un parent et de toutes ses variantes.
5. Une variante est distincte dans le runtime mais jamais autonome dans une
   sauvegarde.
6. Une modification d'un membre ou d'un contenu modifie la Famille entière.
7. L'État global référence les SongUnit sans posséder leurs contenus.
8. Le Manifest décrit la sauvegarde sans posséder de données utilisateur.
9. Toute donnée persistante de la bibliothèque possède un périmètre unique.
10. Toute donnée reconstructible reste hors du modèle persistant.
11. Une bibliothèque restaurable contient les Familles, l'État global et son
    Manifest.
12. Aucun pipeline ne doit définir sa propre liste indépendante de données
    persistantes.

---

## 8. Application de la spécification

Cette spécification doit être utilisée comme contrat commun par :

- l'export, pour transporter une Famille cohérente ;
- l'import, pour reconstruire cette Famille sans modifier son identité ;
- la sauvegarde, pour protéger toutes les Familles et l'État global ;
- la restauration, pour restituer les deux périmètres de données utilisateur ;
- la future fonction **Mettre à jour la bibliothèque**, pour représenter la
  bibliothèque actuelle dans la sauvegarde de référence ;
- SMP Sync, pour considérer les mêmes propriétaires et les mêmes frontières ;
- les diagnostics futurs, pour vérifier qu'aucune donnée persistante n'est
  oubliée ou dupliquée ;
- les futures évolutions, avant l'ajout de toute nouvelle donnée.

Les pipelines peuvent employer des formats de transport différents, mais ils
doivent tous respecter le même modèle de propriété.

---

## 9. Règle d'évolution

Avant toute fonctionnalité future créant ou modifiant une donnée persistante,
la documentation d'architecture doit d'abord préciser si cette donnée appartient
à la bibliothèque SMP ou si elle reste une préférence locale. Pour une donnée
de bibliothèque, elle doit ensuite préciser :

- son périmètre unique ;
- son propriétaire ;
- son identité de rattachement ;
- sa durée de vie ;
- son comportement lorsque son propriétaire est supprimé ;
- son caractère utilisateur ou dérivable.

Une nouvelle donnée ne peut pas être laissée dans un store, un fichier ou une
préférence sans rattachement conceptuel.

L'ajout d'un nouveau format physique ne crée jamais un nouveau périmètre.

---

## 10. Principe final

> Toute donnée persistante de la bibliothèque possède un propriétaire unique.

> Le parent et ses variantes forment une seule unité de sauvegarde.

> La bibliothèque persistante est l'addition des Familles SongUnit, de l'État
> global et du Manifest.

Cette définition est la base obligatoire de toute évolution future de la
persistance SMP.
