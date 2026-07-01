# FEATURE — Second Screen V2

## Objectif

Second Screen V2 vise à rendre l'utilisation d'un deuxième appareil SMP simple pour un musicien.

L'utilisateur doit pouvoir :

- ouvrir `Deuxième écran` ;
- choisir `Diffuser les paroles` ou `Afficher les paroles` ;
- voir les appareils SMP disponibles ;
- connecter puis maintenir une session live sans manipuler d'IP, de port, de serveur, de client ou de LocalLink.

LocalLink reste le moteur réseau.

Ce document ne redéfinit pas l'architecture réseau. Il complète `docs/03_SMP_NETWORK_ARCHITECTURE.md` en décrivant les étapes de mise en œuvre de la fonctionnalité `Deuxième écran V2`.

---

## Règle De Progression

Chaque étape est volontairement indépendante.

On ne développe jamais deux étapes en même temps.

Une étape doit être entièrement validée avant de commencer la suivante.

Si une étape introduit une instabilité live, elle doit être corrigée ou suspendue avant de poursuivre la roadmap.

Ordre obligatoire :

```text
V2.1 Découverte automatique
↓
V2.2 Connexion en un clic
↓
V2.3 Appairage
↓
V2.4 Reconnexion automatique
↓
V2.5 Synchronisation robuste
```

---

## Principes Communs

- Aucun remplacement du moteur LocalLink.
- Aucun changement de protocole sans décision explicite.
- Aucun traitement bloquant sur le thread principal.
- Aucune dépendance réseau obligatoire pour jouer.
- ExoPlayer reste la référence temporelle.
- Les options avancées IP/port restent disponibles comme fallback.
- Téléphone, tablette et split tablette doivent rester cohérents.

---

## V2.1 — Découverte Automatique

### Objectif

Les appareils SMP présents sur le même réseau local se voient automatiquement.

L'écran `Diffuser les paroles` affiche une liste d'appareils SMP disponibles.

Exemples :

```text
Tablette Lenovo
Moto G84
Téléphone de Patrick
```

### Périmètre

- Ajouter la publication de présence côté appareil disponible.
- Ajouter la recherche d'appareils côté appareil principal.
- Afficher les appareils découverts dans l'interface.
- Filtrer les appareils non compatibles.
- Conserver le fallback manuel dans les options avancées.

### Exclusions Volontaires

- Aucune connexion automatique.
- Aucun appairage.
- Aucune autorisation distante.
- Aucune reconnexion.
- Aucun échange de paroles.
- Aucun échange SMP Sync.
- Aucun changement du moteur LocalLink.

### Critères De Validation

- Deux appareils SMP sur le même Wi-Fi se voient automatiquement.
- La liste disparaît proprement quand l'autre appareil n'annonce plus sa présence.
- Sortir de l'écran arrête la découverte ou l'annonce.
- Rotation et recomposition ne créent pas de doublons.
- Téléphone et tablette affichent le même comportement.
- Le split tablette reste stable.
- Le fallback manuel reste accessible.
- Le Player continue de fonctionner même si aucun appareil n'est trouvé.

---

## V2.2 — Connexion En Un Clic

### Objectif

L'utilisateur sélectionne un appareil découvert et ouvre une connexion LocalLink existante.

La connexion devient plus simple, mais elle ne crée pas encore d'appairage permanent.

### Périmètre

- Permettre de sélectionner un appareil découvert.
- Utiliser l'endpoint découvert pour ouvrir la connexion LocalLink.
- Afficher un état clair : connexion, connecté, échec.
- Garder la connexion limitée à la session courante.
- Fermer proprement la connexion en quittant l'écran.

### Exclusions Volontaires

- Aucun appairage permanent.
- Aucune reconnexion automatique.
- Aucune demande d'autorisation persistante.
- Aucun contrôle distant.
- Aucun changement du protocole LocalLink.

### Critères De Validation

- Un appareil découvert peut être sélectionné.
- La connexion utilise LocalLink existant.
- Aucun champ IP/port n'est nécessaire dans le parcours principal.
- En cas d'échec, l'utilisateur reçoit un message clair.
- Quitter l'écran ferme la connexion proprement.
- Les options avancées restent disponibles.
- Aucun impact sur le Player local.

---

## V2.3 — Appairage

### Objectif

L'appareil cible demande une autorisation avant d'accepter une connexion durable.

Après acceptation, l'appareil est mémorisé.

### Périmètre

- Afficher une demande d'autorisation sur l'appareil cible.
- Mémoriser l'appareil accepté.
- Conserver l'identité locale stable.
- Empêcher l'écrasement silencieux d'un appareil déjà appairé.
- Ajouter une action explicite pour oublier l'appareil.

### Exclusions Volontaires

- Aucune reconnexion automatique avancée.
- Aucun multi-appairage complexe.
- Aucun mode cloud.
- Aucun changement de moteur LocalLink.

### Critères De Validation

- Une demande d'autorisation apparaît avant appairage.
- Refuser bloque la connexion.
- Accepter mémorise l'appareil.
- L'appareil mémorisé est reconnu au prochain lancement.
- Un nouvel appareil ne remplace pas silencieusement l'ancien.
- Oublier l'appareil remet l'état à zéro.

---

## V2.4 — Reconnexion Automatique

### Objectif

Retrouver automatiquement un appareil déjà appairé quand les deux appareils sont disponibles sur le même réseau.

### Périmètre

- Tenter le dernier endpoint connu.
- Relancer la découverte si l'endpoint connu échoue.
- Reconnecter uniquement les appareils déjà appairés.
- Afficher un état clair de reconnexion.
- Garder la reconnexion non bloquante.

### Exclusions Volontaires

- Aucun appairage multiple avancé.
- Aucun service permanent obligatoire.
- Aucune reconnexion qui bloque le live.
- Aucune suppression du fallback manuel.

### Critères De Validation

- Deux appareils déjà appairés se retrouvent sans saisie IP/port.
- Une IP changée peut être retrouvée via découverte.
- Une reconnexion échouée n'interrompt pas le Player.
- Aucun doublon de connexion n'est créé.
- Le statut utilisateur reste compréhensible.
- Téléphone, tablette et split tablette restent cohérents.

---

## V2.5 — Synchronisation Robuste

### Objectif

Le changement de morceau ne doit pas provoquer de perte de synchronisation.

Les erreurs réseau doivent être gérées sans casser le live local.

### Périmètre

- Renvoyer les données complètes nécessaires lors d'un changement de morceau.
- Envoyer ensuite les messages temporels basés sur `timeMs`.
- Reprendre proprement après reconnexion.
- Identifier les états désynchronisés.
- Afficher un état utilisateur clair.

### Exclusions Volontaires

- Aucun traitement lourd pendant le playback.
- Aucune lecture depuis archive `.smp` en live.
- Aucun couplage direct à l'état visuel de l'UI.
- Aucun changement de source temporelle.
- Aucun remplacement d'ExoPlayer comme référence.

### Critères De Validation

- Changer de morceau renvoie les paroles nécessaires avant l'horloge.
- Le deuxième écran suit le nouveau morceau sans rester sur l'ancien.
- Une perte réseau n'arrête pas la lecture locale.
- Une reconnexion restaure l'état du deuxième écran.
- Les messages temporels restent basés sur `timeMs`.
- Le comportement reste stable en téléphone, tablette et split tablette.

---

## Règle Finale

Second Screen V2 doit progresser par étapes validées.

Aucune étape ne doit commencer tant que l'étape précédente n'est pas stable.

La fonctionnalité réseau enrichit SMP, mais ne doit jamais devenir une condition nécessaire pour jouer.
