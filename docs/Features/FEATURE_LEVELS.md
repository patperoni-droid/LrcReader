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
- le Player et la Track Console ;
- l'export, la sauvegarde, la restauration et la synchronisation du morceau.

---

## Parcours Actuellement Implémenté

### Accès

La Bibliothèque contient un onglet `LEVELS`.

Cet onglet affiche les morceaux live jouables connus de la Bibliothèque. Chaque ligne présente :

- le titre du morceau ;
- une commande de lecture rapide ;
- le `LEVEL` courant en dB.

Toucher une ligne sélectionne le morceau à régler et le relie au tiroir de gain.

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

### Tiroir LEVEL

Le tiroir de gain officiel permet de régler le morceau sélectionné de `-24 dB` à `+6 dB`.

Le réglage est manuel, immédiatement audible et mémorisé pour le morceau. Le même niveau doit être retrouvé lorsque le morceau est ensuite lancé depuis la Bibliothèque, une playlist ou le Player.

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

Un écart d'implémentation reste notamment présent : à l'ouverture de LEVELS, le code peut encore extraire des crêtes de waveform, calculer une estimation héritée par rapport à une cible `-14` et utiliser le résultat comme niveau initial affiché ou écouté. Cette opération n'est pas exposée comme une commande utilisateur, mais elle est encore active dans le parcours actuel.

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
- Le Playback Control et le tiroir modifient le même niveau mémorisé.
- Le morceau retrouve ce niveau dans le Player.
- Aucun vocabulaire ni commande de l'ancien système n'est nécessaire pour comprendre ou utiliser la page.

---

## Principe Final

`LEVELS` sert à préparer le concert à l'oreille.

La vérité est le niveau réellement choisi par le musicien pour chaque morceau.
