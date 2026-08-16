# Stage Music Player — Plan de lancement de la bêta publique

> **Objectif actuel : préparer Stage Music Player pour le lancement de sa bêta publique.**

Stage Music Player se trouve dans les derniers jours du test fermé Google Play. Le seuil des
12 testeurs est atteint. Cette période doit maintenant servir à stabiliser l'application et à
préparer l'arrivée des premiers utilisateurs publics.

> **Stabilité > nouvelles fonctionnalités jusqu'au lancement bêta.**

Ce document est le point de reprise officiel jusqu'à l'ouverture de la bêta publique. Il coordonne
ce jalon de lancement sans remplacer [le statut du projet](../../PROJECT_STATUS.md),
[le backlog technique](../BACKLOG.md) ni les fiches de release dans [`docs/releases/`](../releases/).

## Point de reprise

| Information | État actuel |
|---|---|
| Dernière mise à jour | 16 août 2026 |
| Phase actuelle | Derniers jours du test fermé Google Play |
| Étape en cours | 1 — Tester l'application |
| Prochaine action recommandée | Utiliser l'application sur téléphone et tablette dans des conditions réelles ou proches du live, puis consigner uniquement les problèmes observés |

## Vue d'ensemble

| Étape | Statut | Résultat attendu |
|---|---|---|
| 1 — Tester l'application | **EN COURS** | Une version candidate stable sur téléphone et tablette |
| 2 — Préparer la nouvelle version Google Play | **À FAIRE** | Un AAB vérifié et une release prête dans Play Console |
| 3 — Créer un premier site Stage Music Player | **À FAIRE** | Une présentation simple avec notice en ligne et contact |
| 4 — Finaliser la fiche Google Play | **EN COURS** | Une fiche claire et cohérente pour le public prioritaire |
| 5 — Préparer les retours utilisateurs | **À FAIRE** | Un canal simple pour questions, problèmes et suggestions |
| 6 — Étudier ou préparer une vidéo courte | **OPTIONNEL** | Une démonstration réelle réutilisable dans la communication |
| 7 — Contrôle final avant ouverture | **À FAIRE** | Une décision explicite d'ouverture ou de report |

## Décisions actives

- aucune grosse fonctionnalité ne doit être engagée avant le lancement ;
- seuls les bugs réellement gênants ou dangereux justifient un correctif immédiat ;
- les améliorations non urgentes sont consignées séparément dans [le backlog](../BACKLOG.md) ;
- le public prioritaire est le musicien ou chanteur utilisant des bandes-son pendant ses
  prestations live ;
- la notice en ligne fait partie du premier site, même si le site reste simple ;
- la vidéo courte est utile, mais ne bloque pas l'ouverture de la bêta ;
- le plan d'acquisition des premiers utilisateurs sera traité après le lancement dans un document
  séparé.

## 1 — Tester l'application

**Statut : EN COURS**

Utiliser Stage Music Player pendant les prochains jours sur téléphone et tablette, de préférence en
situation réelle ou proche des conditions live.

Pendant cette période :

- surveiller particulièrement le Player, les playlists, les transitions, les paroles et les
  parcours de préparation live ;
- corriger les bugs qui menacent la stabilité, la compréhension ou les données utilisateur ;
- éviter les évolutions importantes et les refactors non indispensables ;
- noter les améliorations non urgentes sans les intégrer à la version candidate.

Point récent : la distinction entre tap court et multi-sélection dans la playlist tablette, ainsi
que la priorité de l'indicateur du prochain morceau, ont été corrigées et validées sur tablette.

Cette étape sera terminée lorsque :

- des sessions significatives auront été réalisées sur téléphone et tablette ;
- aucun problème bloquant connu ne restera sans décision ;
- le commit stable candidat à la publication pourra être identifié.

## 2 — Préparer la nouvelle version Google Play

**Statut : À FAIRE**

Cette étape commencera après la période de test en cours.

- confirmer le dernier commit stable destiné à la release ;
- vérifier `versionName` et `versionCode` ;
- refaire les contrôles de sécurité et de publication ;
- générer l'AAB officiel ;
- vérifier que l'AAB produit est bien celui destiné à Google Play ;
- envoyer la nouvelle version dans Play Console ;
- vérifier l'état de la release avant l'ouverture de la bêta.

Références à utiliser sans recopier leur procédure :

- [Règles de sécurité des releases](../03_RELEASE_SAFETY_RULES.md) ;
- [Guide officiel de publication Google Play](../04_GOOGLE_PLAY_RELEASE_GUIDE.md) ;
- [Procédure et checklist des releases](../releases/README.md) ;
- [Modèle de fiche de release](../releases/RELEASE_TEMPLATE.md).

## 3 — Créer un premier site Stage Music Player

**Statut : À FAIRE**

Le premier site peut rester simple. Il doit au minimum :

- présenter Stage Music Player et le public auquel l'application s'adresse ;
- expliquer les fonctions principales ;
- montrer quelques captures représentatives ;
- proposer un accès à Google Play lorsque la bêta sera disponible ;
- fournir un moyen de contact ;
- rendre la notice utilisateur accessible en ligne, avec des chapitres faciles à parcourir.

La notice doit rester maintenable à partir du
[manuel utilisateur du dépôt](../user-guide/index.md), qui reste sa source documentaire.

À mesure que le site évoluera, ses pages et la notice devront aider les musiciens à découvrir Stage
Music Player lorsqu'ils recherchent une solution pour les bandes-son sur scène, les playlists de
concert, les paroles synchronisées ou la préparation de morceaux sur tablette. Une étude SEO
détaillée n'est pas requise avant le lancement.

## 4 — Finaliser la fiche Google Play

**Statut : EN COURS**

Vérifier et finaliser :

- la description courte ;
- la description longue ;
- le positionnement ;
- les captures téléphone et tablette ;
- leur ordre et les éventuels textes présents sur les visuels ;
- la cohérence entre la fiche et le public ciblé.

Public prioritaire :

> **Musicien ou chanteur utilisant des bandes-son pendant ses prestations live.**

Cela comprend notamment les guitaristes-chanteurs, claviéristes-chanteurs et instrumentistes jouant
avec des accompagnements enregistrés. Les chanteurs seuls, animateurs et musiciens-animateurs
restent des publics secondaires possibles ; la première fiche ne doit pas chercher à parler à tout
le monde.

## 5 — Préparer les retours utilisateurs

**Statut : À FAIRE**

Avant l'ouverture, choisir un moyen simple permettant aux premiers utilisateurs de :

- poser une question ;
- signaler un problème ;
- donner leur avis ;
- proposer une amélioration.

Le canal retenu doit être facile à surveiller et empêcher la perte des premiers retours. Une
infrastructure complexe de support n'est pas nécessaire à ce stade.

## 6 — Étudier ou préparer une vidéo courte

**Statut : OPTIONNEL**

Cette étape peut être réalisée avant le lancement, mais ne le bloque pas.

Si le temps le permet, préparer une démonstration courte en situation réelle : tablette sur pied,
choix et lancement d'un morceau, affichage des paroles, puis préparation du morceau suivant.

Cette vidéo pourra ensuite être réutilisée sur le site, Facebook ou d'autres communications. Son
absence ne doit pas retarder l'ouverture de la bêta.

## 7 — Contrôle final avant ouverture

**Statut : À FAIRE**

Rejouer le parcours complet d'une personne qui ne connaît pas Stage Music Player :

> découverte → compréhension → installation → première utilisation → aide/notice → contact

Vérifier que :

- l'application est suffisamment stable pour le public visé ;
- la version et la release Google Play sont correctes ;
- la fiche explique clairement le produit ;
- la notice est accessible ;
- un moyen de contact fonctionne ;
- les principaux parcours de démarrage sont compréhensibles.

Si tous les points essentiels sont satisfaits, décider explicitement l'ouverture de la bêta
publique. Sinon, noter les éléments bloquants et la prochaine action avant de reporter l'ouverture.

## Après le lancement : Objectif 10

Cette phase fera l'objet d'un plan séparé.

> Premier objectif : obtenir environ 10 premiers utilisateurs réels extérieurs et comprendre s'il
> existe un intérêt pour Stage Music Player.

Il faudra d'abord observer qui télécharge, pourquoi, comment l'application est découverte, si son
positionnement est compris, si elle est réellement utilisée et ce qui freine son adoption.

Les paliers envisagés sont ensuite **10 → 100 → 500 utilisateurs ou téléchargements**, avec une
réévaluation du produit à chaque étape. Ce document ne développe pas encore le plan marketing de
l'Objectif 10.

## Règle de maintenance

> Lorsqu'une étape importante est terminée ou qu'une décision modifie ce plan, mettre à jour ce
> document avant de considérer le travail comme clôturé.

À chaque mise à jour :

1. actualiser le **Point de reprise** ;
2. modifier le statut des étapes concernées ;
3. noter brièvement la décision ou le résultat durable ;
4. indiquer une seule prochaine action recommandée.

Cette feuille de route doit rester une roadmap vivante et synthétique, pas devenir un historique
technique détaillé.
