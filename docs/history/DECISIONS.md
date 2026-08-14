# Décisions importantes

Ce document conserve les décisions structurantes du projet Stage Music Player.

Chaque nouvelle décision importante doit être ajoutée avec :

- la date ;
- le contexte ;
- la décision ;
- les conséquences pratiques.

---

# 14/08/2026 — LEVELS remplace définitivement l'ancien système de niveau

## Contexte

L'ancien système de mesure et de normalisation automatique a été revu. Le produit dispose
désormais de **LEVELS**, un atelier volontairement plus simple : le musicien écoute des passages
représentatifs et règle manuellement le niveau du morceau en décibels.

Des noms historiques peuvent encore subsister dans le code ou les données persistées. Ils sont
des éléments de compatibilité ou une dette technique et ne constituent pas une fonctionnalité.

## Décisions

- **LEVELS** est la seule fonctionnalité actuelle de préparation des niveaux ;
- l'ancien système est abandonné et ne figure plus dans la feuille de route ;
- aucune mesure, cible ou normalisation automatique n'appartient au contrat fonctionnel LEVELS ;
- `docs/Features/FEATURE_LEVELS.md` est la référence fonctionnelle actuelle ;
- `docs/Features/FEATURE_LEVELS_V2.md` et `docs/Features/FEATURE_LUFS_PREPARATION.md` sont conservés
  uniquement comme archives historiques explicitement obsolètes ;
- la suppression ou la migration des identifiants et champs historiques du code devra faire
  l'objet d'une tâche applicative distincte, avec étude de compatibilité des données existantes.

## Conséquences pratiques

- le manuel utilisateur décrit uniquement l'écoute et l'ajustement manuel proposés par LEVELS ;
- les documents actifs ne présentent plus l'ancien système comme disponible ou futur ;
- les formats persistés et les imports existants ne doivent pas être cassés par une future
  opération de nettoyage technique.

---

# 11/08/2026

## Contexte

La Bibliothèque exposait sous le nom `Lyrics` un catalogue de textes autonomes destinés au
défilement, alors que le mot Lyrics désigne déjà les paroles `.lrc` synchronisées avec l'audio.
L'ouverture de ces textes utilisait aussi encore l'ancien parcours plein écran sur tablette.

Le chantier de persistance précédent a par ailleurs rendu l'aller-retour SMP des Familles
SongUnit beaucoup plus complet, et une première commande de mise à jour de sauvegarde a été
livrée.

## Décisions

- la terminologie utilisateur officielle devient **Textes défilants**, **Créer un texte
  défilant** et **Nouveau texte défilant** ;
- un texte défilant autonome appartient au catalogue global et reste distinct des paroles `.lrc`
  d'une SongUnit et des contenus de prompteur liés à un morceau ;
- la création depuis la Bibliothèque n'affecte aucune playlist, tandis que la création depuis une
  playlist ajoute une occurrence à cette playlist ;
- sur tablette moderne, le texte défilant s'ouvre dans le panneau droit du mode Split, avec ses
  commandes de défilement contraintes à ce panneau ; le plein écran téléphone reste inchangé ;
- l'aller-retour SMP d'une Famille préserve toutes les données de variantes actuellement prises
  en charge et est certifié par un test de famille complet ;
- **Mettre à jour la bibliothèque** est livré comme une étape minimale limitée aux Familles
  SongUnit et ne doit pas être présenté comme une reconstruction complète de l'État global.

## Conséquences pratiques

- toute documentation utilisateur doit réserver Lyrics / Paroles aux contenus synchronisés avec
  l'audio ;
- `Config/text_songs.json` et `prompter://` restent des détails internes documentés uniquement
  dans la spécification de persistance ;
- les futures extensions de mise à jour devront intégrer `state.json`, playlists, groupes,
  textes défilants et suppressions avant de représenter une sauvegarde entièrement à jour ;
- les évolutions tablette du prompteur texte ne doivent pas modifier la disposition téléphone.

---

# 28/07/2026

## Contexte

L'éditeur Arrangement tablette a servi de référence pour harmoniser progressivement le
téléphone : workflow des variantes, Playback Control, retour à la waveform source, piste
horizontale compacte, blocs à largeur adaptative et chargement Waveform échantillonné.

Le pipeline téléphone WAV/PCM/Sampler restait plus lent au premier lancement que la lecture
directe déjà validée sur tablette.

## Décisions

- téléphone et tablette partagent désormais le même workflow fonctionnel Arrangement ;
- le téléphone adapte uniquement la présentation à sa largeur : noms compacts, blocs ajustés
  au texte et actions complètes par appui long ;
- la lecture directe devient le mode recommandé et le comportement par défaut sur téléphone ;
- le pipeline WAV/Sampler historique reste intact derrière un mode avancé de compatibilité ;
- l'ancien éditeur Arrangement n'est pas supprimé tant qu'un parcours de repli l'utilise ;
- `docs/BACKLOG.md` devient l'unique liste priorisée des travaux futurs.

## Conséquences pratiques

- la validation multi-téléphones de la lecture directe reste obligatoire avant de retirer
  toute compatibilité historique ;
- la tablette conserve son comportement direct actuel ;
- aucune Structure, variante ou donnée persistante ne dépend du mode audio choisi ;
- les documents Feature et roadmap décrivent les détails, mais la priorité vient du backlog.

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
