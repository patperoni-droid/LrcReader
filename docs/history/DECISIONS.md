# Décisions importantes

Ce document conserve les décisions structurantes du projet Stage Music Player.

Chaque nouvelle décision importante doit être ajoutée avec :

- la date ;
- le contexte ;
- la décision ;
- les conséquences pratiques.

---

# 19/07/2026

## Contexte

Le Playback Control officiel a été progressivement déployé dans le cockpit tablette. Les essais ont révélé deux dettes historiques : plusieurs écrans utilisaient encore des commandes locales imitant le composant, et le gain pouvait changer de pipeline autour de `0 dB`.

## Décisions

- le Playback principal reste l'unique lecteur audio de Waveform ; l'ancien lecteur d'aperçu est supprimé ;
- Track Console utilise le Playback Control officiel et ne conserve pas de logique Playback locale ;
- les écrans tablette intégrés utilisent la même sélection officielle : sélectionner prépare, Play jaune confirme le lancement ;
- le gain positif utilise un étage léger installé à la création du Player ; le gain seul ne sélectionne plus SoundTouch et ne reconstruit plus le Player autour de `0 dB` ;
- le projet AUTO est suspendu et n'appartient pas au composant actuellement implémenté ;
- la prochaine intégration étudiée est la Timeline : un seul Playback Control officiel, Timeline toujours liée au titre actif, et changement de Timeline uniquement après lancement du titre sélectionné.

## Conséquences pratiques

- Waveform, Track Console et les autres écrans intégrés pilotent le même Playback principal ;
- aucun lecteur audio secondaire ne doit être réintroduit dans Waveform ou Timeline ;
- le bouton Play jaune conserve partout la même signification sur tablette ;
- la Timeline actuellement affichée ne doit jamais changer sur une simple sélection de playlist ;
- la future intégration Timeline doit supprimer ses commandes Play, Pause et Retour locales, rester ouverte pendant le lancement du titre préparé, puis charger la Timeline du nouveau titre actif ;
- le son neutre, Pitch, Speed et les passages autour de `0 dB` ont été validés sur appareil réel après correction du tampon PCM.

---

# 18/07/2026

## Contexte

Stage Music Player entre dans une phase de publications régulières sur Google Play, d'abord via les Tests fermés Alpha.

## Décisions

- adoption d'une procédure officielle de publication ;
- création d'un historique des releases ;
- adoption du versionnement sémantique ;
- toute publication doit être documentée.

## Conséquences pratiques

- les releases sont documentées dans `docs/releases/` ;
- les évolutions futures sont suivies dans `docs/roadmap/` ;
- les décisions importantes sont conservées dans ce document ;
- une publication Google Play ne doit pas dépendre de souvenirs ou de conversations externes.
