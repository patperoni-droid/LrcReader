# FEATURE — LEVELS

## Statut

**Référence fonctionnelle actuelle — décision définitive du 14 août 2026.**

`LEVELS` est l'atelier manuel de préparation du niveau des morceaux avant répétition ou concert.

L'ancien système LUFS est abandonné. Il ne constitue ni une fonctionnalité actuelle, ni une cible future de Stage Music Player. Les noms techniques ou données hérités encore présents dans le code relèvent uniquement de la compatibilité et de la dette technique ; ils ne définissent pas le produit.

---

## Objectif

Permettre au musicien de :

- écouter rapidement un passage utile de chaque morceau ;
- comparer les morceaux à l'oreille dans des conditions cohérentes ;
- ajuster manuellement leur `LEVEL` ;
- mémoriser un niveau propre à chaque morceau ;
- préparer un enchaînement homogène avant le live.

`LEVELS` ne présente aucune mesure de sonie, aucune cible et aucune commande de normalisation automatique. La décision fonctionnelle appartient au musicien.

---

## Source De Vérité

La seule valeur fonctionnelle de référence est :

```text
LEVEL mémorisé du morceau, exprimé en dB
```

Cette valeur est celle que le Playback principal applique au morceau.

Le `LEVEL` appartient au morceau et doit rester cohérent entre :

- la page `LEVELS` ;
- le Playback Control ;
- le tiroir de gain ;
- le Player et, techniquement, la Track Console conservée mais non exposée ;
- l'export, la sauvegarde, la restauration et la synchronisation du morceau.

---

## Parcours Actuellement Implémenté

### Accès

La Bibliothèque contient un onglet `LEVELS`.

Cet onglet affiche les morceaux live jouables connus de la Bibliothèque. Chaque ligne présente :

- le titre du morceau ;
- une commande de lecture rapide ;
- le `LEVEL` courant en dB.

Toucher une ligne sélectionne le morceau sans lancer la lecture. Toucher sa case dB ouvre le petit panneau de réglage direct du morceau concerné.

### Démarrage Rapide

Un appui sur la commande de lecture ouvre les points de départ suivants :

- `Début` ;
- `20 s` ;
- `40 s` ;
- `60 s` ;
- `90 s`.

Choisir un point lance le morceau avec son niveau courant. Si ce morceau est déjà en lecture, la même commande l'arrête.

Cette fonction permet d'atteindre directement un refrain ou un passage dense sans attendre une longue introduction.

### Playback Control

Le composant officiel `Playback Control` reste affiché dans `LEVELS`.

Il permet notamment de :

- lancer ou mettre en pause la lecture active ;
- suivre et déplacer la position ;
- revenir au début ;
- voir le niveau courant ;
- ajuster le niveau par pas.

`LEVELS` ne possède pas de moteur audio autonome. Il réutilise le Playback principal et ses règles de stabilité.

### Réglage direct LEVEL

Le fader tiroir n'est plus affiché dans cet onglet. La case dB de chaque titre ouvre
un petit panneau avec les commandes existantes `−1 dB` et `+1 dB`, bornées à
`-24 dB` et `+6 dB`. Les commandes attendent la fin d'une préparation en cours.

Le morceau est ciblé par `songId`. Le réglage réutilise `TrackVolumePrefs` et le
callback audio existant ; aucune nouvelle source de vérité n'est créée. Le niveau
mémorisé est retrouvé à la réouverture et au lancement dans les autres parcours ;
la modification agit immédiatement sur le morceau s'il est actif en lecture.

L'aide utilisateur FR, également traduite en EN et ES, ne mentionne plus LUFS :

> Ici, vous pouvez harmoniser le volume de vos morceaux.
>
> Cela permet d’éviter les différences de niveau entre les titres, pour un rendu plus confortable et professionnel en live.
>
> Vous pouvez appliquer ou retirer ce réglage à tout moment.

---

## Méthode De Préparation Recommandée

```text
Ouvrir LEVELS
↓
Choisir un morceau
↓
Écouter un passage représentatif
↓
Ajuster le LEVEL à l'oreille
↓
Comparer avec un morceau de référence
↓
Passer au morceau suivant
```

Conserver le même volume général, la même enceinte ou console et des passages comparables pendant la préparation.

L'objectif n'est pas de rendre tous les morceaux identiques. Il est d'éviter les écarts gênants tout en conservant leur dynamique musicale.

---

## Règles Fonctionnelles

- Aucun bouton d'analyse ou de normalisation automatique ne doit structurer le parcours `LEVELS`.
- Aucune cible théorique ne doit être présentée comme vérité produit.
- Le niveau choisi manuellement doit rester non destructif pour le fichier audio source.
- Une simple sélection ne doit pas lancer la lecture.
- Le traitement de préparation ne doit pas perturber le Playback live.
- Aucun traitement lourd ne doit être déclenché pendant une prestation.
- Les changements de niveau doivent préserver l'identité `songId` et le stockage normalisé du morceau.

---

## Compatibilité Technique

Le code actuel conserve encore des identifiants, champs de configuration et fonctions portant l'ancien nom `lufs`. Certains participent encore au chargement ou à la sauvegarde du gain.

Un écart d'implémentation reste présent : à l'ouverture de LEVELS, le code peut encore extraire des crêtes de waveform et calculer une estimation héritée de l'ancienne approche LUFS abandonnée. Le gain `song.volumeDb` mémorisé est désormais prioritaire ; en son absence, cette estimation peut encore servir de repli au niveau initial affiché ou écouté. Ce calcul résiduel n'est ni une commande utilisateur ni une promesse de normalisation automatique. Le retrait du paragraphe d'aide relatif à −14 LUFS n'a pas supprimé ce code.

Ces éléments sont hérités de l'ancien système. Ils peuvent être lus pour préserver les morceaux existants, mais :

- ils ne doivent pas être documentés comme une fonctionnalité utilisateur ;
- ils ne doivent pas réintroduire une analyse ou une cible automatique dans l'UX ;
- leur éventuelle migration relève d'un chantier de code séparé, avec diagnostic et compatibilité ascendante.

La suppression de cet écart nécessite donc une tâche applicative distincte. D'ici là, la présente spécification décrit la décision produit et signale explicitement la divergence du code.

---

## Critères De Validation

- L'onglet visible s'appelle `LEVELS`.
- La liste affiche le titre et le niveau en dB de chaque morceau.
- Le démarrage rapide propose `Début`, `20 s`, `40 s`, `60 s` et `90 s`.
- La sélection d'une ligne cible le bon morceau sans le lancer.
- Le Playback Control et le panneau ouvert depuis la case dB réutilisent le niveau mémorisé existant.
- Les commandes directes respectent les bornes `-24 dB` / `+6 dB` et la valeur revient à la réouverture ; aucun fader tiroir n'est affiché dans LEVELS.
- Le morceau retrouve ce niveau dans le Player.
- Aucun vocabulaire ni commande de l'ancien système n'est nécessaire pour comprendre ou utiliser la page.

---

## Principe Final

`LEVELS` sert à préparer le concert à l'oreille.

La vérité est le niveau réellement choisi par le musicien pour chaque morceau.
