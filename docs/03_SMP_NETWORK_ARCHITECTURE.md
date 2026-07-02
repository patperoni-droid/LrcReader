# SMP Network Architecture — Stage Music Player

## Objectif

Ce document définit l'architecture cible V2 des communications entre appareils SMP.

Il devient la référence pour toutes les futures fonctionnalités réseau :

- deuxième écran ;
- SMP Sync ;
- accords ;
- prompteur ;
- annotations ;
- contrôle distant ;
- futures communications SMP.

Le but n'est pas de remplacer le moteur réseau actuel. Le but est de construire une couche d'orchestration plus intelligente au-dessus du transport existant.

---

## Philosophie

L'utilisateur ne doit jamais manipuler les notions techniques suivantes :

- IP ;
- port ;
- serveur ;
- client ;
- LocalLink.

Ces notions restent internes au code.

L'utilisateur exprime uniquement son intention.

Exemple :

```text
Afficher les paroles sur un deuxième appareil.
```

L'application doit ensuite :

- découvrir les sessions SMP disponibles ;
- établir la connexion adaptée à l'action demandée ;
- maintenir la connexion ;
- envoyer automatiquement les données nécessaires au live ;
- se reconnecter sans intervention quand c'est possible.

Principe SMP :

```text
L'utilisateur choisit ce qu'il veut faire, jamais la technologie utilisée.
```

Un réseau SMP peut contenir plusieurs appareils.

Une connexion entre deux appareils n'est qu'un cas particulier de cette architecture.

La cible V2 doit donc raisonner en réseau d'appareils SMP, pas seulement en liaison ponctuelle. Cette vision prépare les évolutions futures : deuxième écran, SMP Sync, prompteur, accords, annotations, contrôle distant et autres services SMP.

Le réseau SMP s'organise autour d'une session pilotée par l'appareil qui diffuse.

Pour le deuxième écran, `Diffuser les paroles` est le maître de la session réseau :

- il possède ExoPlayer ;
- il possède la timeline ;
- il pilote la lecture ;
- il héberge `LocalLinkServer` ;
- il annonce la session SMP sur le réseau via NSD.

Les appareils `Afficher les paroles` ne publient pas de session de diffusion. Ils découvrent les sessions disponibles et, depuis le deuxième écran V2.3, se connectent automatiquement au diffuseur dès qu'une session compatible est disponible.

---

## Architecture Générale

Le moteur actuel est conservé :

- LocalLink TCP ;
- protocole existant ;
- messages existants ;
- transport JSON line-based existant ;
- messages `Hello`, `LyricsPacket`, `Clock`, `ReceiverStatus` et messages SMP Sync existants.

L'architecture V2 ajoute une couche d'orchestration au-dessus de LocalLink.

Architecture cible :

```text
SmpDeviceDiscovery
↓
SmpPairingCoordinator
↓
SmpConnectionSupervisor
↓
SmpLiveSession
↓
LocalLink
```

LocalLink reste le moteur bas niveau.

Les couches V2 ne doivent pas dupliquer le transport TCP. Elles doivent uniquement décider :

- quel appareil viser ;
- si l'utilisateur a autorisé la connexion ;
- quand se connecter ;
- quand se reconnecter ;
- quoi renvoyer pour restaurer une session stable.

Pour le deuxième écran, le diffuseur est le serveur logique de la session.

Schéma de principe :

```text
Diffuser les paroles
↓
Annonce NSD de la session SMP
↓
Découverte par les deuxièmes écrans
↓
Connexion automatique LocalLink vers le diffuseur
↓
Envoi automatique du morceau courant et des changements de morceau
↓
Synchronisation pilotée par ExoPlayer
```

Ce modèle respecte le moteur LocalLink existant : la connexion TCP est ouverte vers l'appareil qui possède déjà la source temporelle et le serveur `LocalLinkServer`.

---

## Méthode De Découverte

La méthode cible de découverte automatique est Android NSD / mDNS.

Service recommandé :

```text
_smp-locallink._tcp
```

NSD / mDNS annonce une session de diffusion SMP, pas un simple appareil.

Pour le deuxième écran, la session est publiée par l'appareil `Diffuser les paroles`, après ouverture du serveur LocalLink.

Chaque session publiée contient :

- nom lisible de la session ou de l'appareil diffuseur ;
- `deviceId` du diffuseur ;
- rôle de session ;
- port TCP LocalLink réel ;
- version de protocole ;
- capacités disponibles.

Le port annoncé doit correspondre au port réellement ouvert par `LocalLinkServer`.

Les appareils `Afficher les paroles` découvrent ces sessions mais ne publient pas eux-mêmes une session de diffusion.

Exemples de capacités :

```text
lyrics
chords
prompter
smpSync
annotations
remoteControl
```

Un appareil SMP ne représente pas une fonctionnalité unique.

Chaque session annonce les capacités qu'elle sait fournir. Un téléphone peut par exemple diffuser les paroles et préparer SMP Sync, tandis qu'une tablette peut afficher les paroles, les accords ou un prompteur.

Cette approche permet d'ajouter de nouvelles fonctionnalités sans modifier l'architecture réseau. Les futures capacités deviennent des annonces et des sessions spécialisées au-dessus de la même couche de découverte, d'appairage et de supervision.

Avant tout appairage, les appareils doivent vérifier leur compatibilité :

- `protocolVersion` ;
- `appVersion` ;
- capacités minimales nécessaires à l'action demandée.

Si les versions ou capacités sont incompatibles :

- la connexion est refusée ;
- l'utilisateur reçoit un message clair ;
- aucun comportement partiel ou imprévisible ne doit être tenté.

Cette règle évite les comportements instables entre versions SMP différentes.

NSD / mDNS sert uniquement à trouver l'appareil et son endpoint.

Après découverte, la connexion réelle continue d'utiliser LocalLink TCP.

---

## Responsabilités

### SmpDeviceDiscovery

Responsabilité :

- rechercher les sessions SMP disponibles sur le réseau local ;
- exposer une liste de sessions lisibles par l'UI ;
- filtrer les sessions incompatibles ;
- rafraîchir la liste sans bloquer l'interface ;
- s'arrêter proprement quand l'écran n'en a plus besoin.

Cette couche ne doit pas :

- ouvrir de session live ;
- envoyer des paroles ;
- importer des données ;
- modifier le moteur LocalLink.

Elle fournit seulement des candidats de connexion.

### SmpDeviceAdvertiser

Responsabilité :

- publier une session SMP sur le réseau via NSD / mDNS ;
- annoncer le port LocalLink réellement disponible ;
- annoncer l'identité du diffuseur ;
- annoncer les capacités disponibles.

Cette couche est active uniquement quand l'utilisateur rend une session joignable.

Exemples :

- l'utilisateur ouvre `Diffuser les paroles` et démarre une session de diffusion ;
- l'utilisateur ouvre une future session d'envoi SMP Sync ;
- une future option explicite active un mode joignable.

Elle ne doit pas créer de service Android permanent dans une première version.

### SmpPairingCoordinator

Responsabilité :

- gérer l'appairage entre deux appareils ;
- présenter la demande d'autorisation ;
- mémoriser l'appareil accepté ;
- refuser les appareils inattendus si un appareil est déjà appairé ;
- permettre d'oublier explicitement un appareil.

Parcours cible :

```text
Patrick souhaite se connecter.
Accepter ?
```

L'appairage doit être basé sur une identité stable :

- `localDeviceId` ;
- `localDeviceName` ;
- rôle préféré ;
- appareil associé ;
- dernier endpoint connu.

Le stockage existant de type `SmpSyncPeerStore` est la base naturelle de cette couche.

Règles :

- ne jamais régénérer silencieusement un identifiant local ;
- ne jamais écraser silencieusement un appareil associé différent ;
- ne jamais accepter une connexion nouvelle sans validation utilisateur, sauf si l'appareil est déjà appairé.

### SmpConnectionSupervisor

Responsabilité :

- établir la connexion LocalLink à partir d'un appareil découvert ou appairé ;
- surveiller l'état de connexion ;
- mémoriser le dernier endpoint connu ;
- relancer la découverte si l'endpoint connu échoue ;
- tenter une reconnexion non bloquante ;
- exposer un état simple à l'UI.

États utilisateur possibles :

- recherche d'appareils ;
- appareil trouvé ;
- demande d'autorisation ;
- connecté ;
- reconnexion ;
- impossible de se connecter.

Cette couche ne doit jamais bloquer le Player.

Toutes les opérations réseau doivent rester hors thread principal.

### SmpLiveSession

Responsabilité :

- représenter la session live active entre deux appareils ;
- envoyer les données nécessaires au deuxième écran ;
- maintenir la synchronisation ;
- renvoyer un paquet complet quand le morceau change ;
- gérer une reprise propre après reconnexion.

Règles live :

- ExoPlayer reste la seule référence temporelle ;
- la synchronisation se fait par `timeMs` ;
- le changement de morceau doit renvoyer les données nécessaires avant les messages d'horloge ;
- aucune logique ne doit dépendre de l'état visuel de l'UI ;
- aucune reconstruction lourde ne doit se produire pendant le playback.

Pour les paroles :

```text
changement de morceau
↓
envoyer LyricsPacket complet
↓
envoyer Clock avec timeMs courant
↓
continuer les mises à jour légères
```

Ce principe doit aussi s'appliquer aux futurs accords, prompteur, annotations ou contrôles distants.

---

## UX Cible

Parcours `Diffuser les paroles` :

```text
Diffuser les paroles
↓
Ouverture du serveur LocalLink
↓
Annonce NSD de la session
↓
Découverte par le deuxième écran
↓
Connexion automatique
↓
Envoi automatique des morceaux depuis le Player
↓
Reconnexion automatique
↓
Synchronisation permanente
```

Sur l'appareil principal :

- l'utilisateur active `Diffuser les paroles` avec `Activer Deuxième écran` ;
- l'application ouvre une session de diffusion ;
- le serveur LocalLink est l'endpoint réel de cette session ;
- la session est annoncée sur le réseau via NSD ;
- l'appareil principal reste maître de la lecture et de la timeline ;
- les changements de morceau du Player envoient automatiquement les données nécessaires au deuxième écran.

Sur le deuxième appareil :

- l'utilisateur appuie sur `Afficher les paroles` ;
- l'application découvre les sessions SMP disponibles ;
- l'application se connecte automatiquement à la session compatible du diffuseur ;
- la demande d'autorisation et la mémorisation appartiennent à l'étape future d'appairage.

Après appairage :

- plus aucune saisie d'IP ;
- plus aucune saisie de port ;
- reconnexion automatique si les deux appareils sont sur le même réseau ;
- fallback manuel disponible uniquement dans les options avancées.

---

## Fonctionnalités Partagées

Les futures fonctionnalités réseau SMP doivent partager cette architecture car elles ont les mêmes besoins fondamentaux :

- trouver un appareil ;
- vérifier son identité ;
- demander ou réutiliser une autorisation ;
- établir une connexion ;
- maintenir la connexion ;
- reprendre proprement après perte réseau ;
- ne jamais perturber le live.

Cette architecture doit servir à :

- deuxième écran paroles ;
- SMP Sync ;
- affichage des accords ;
- prompteur distant ;
- annotations ;
- contrôle distant ;
- futures communications SMP.

La différence entre ces fonctionnalités ne doit pas être le moteur réseau.

La différence doit être la capacité utilisée dans `SmpLiveSession` ou dans une session spécialisée au-dessus de LocalLink.

Exemples :

- paroles : `LyricsPacket` + `Clock` ;
- accords : paquet d'accords + `Clock` ;
- prompteur : texte ou état prompteur + `Clock` si nécessaire ;
- SMP Sync : manifest, package, import confirmé ;
- contrôle distant : intention utilisateur, jamais couplée directement au Player sans garde de sécurité.

---

## Principes SMP

Cette architecture doit respecter les règles suivantes :

- conserver LocalLink ;
- avancer par patchs progressifs ;
- privilégier la stabilité avant les fonctionnalités ;
- ne faire aucun traitement bloquant ;
- ne faire aucune I/O sur le thread principal ;
- garder ExoPlayer comme référence temporelle unique ;
- rendre la reconnexion non bloquante ;
- conserver la compatibilité téléphone / tablette ;
- ne jamais lire un `.smp` directement en live ;
- ne jamais rendre le live dépendant du réseau.

Le réseau est un confort.

Le live local doit rester jouable même si le réseau tombe.

---

## Téléphone Et Tablette

L'architecture doit être identique sur téléphone et tablette.

Téléphone :

- préparation ;
- répétition ;
- secours ;
- diffusion possible vers tablette ou autre téléphone.

Tablette :

- cockpit live principal ;
- deuxième écran confortable ;
- affichage simultané de playlist, paroles, accords ou prompteur.

Mode split tablette :

- la connexion doit rester dans les écrans `Deuxième écran` ou `SMP Sync` ;
- le cockpit live ne doit pas être bloqué par la découverte ou la reconnexion ;
- la perte réseau ne doit pas modifier la navigation live ;
- le Player et la playlist restent prioritaires.

---

## Risques Techniques

Risques principaux :

- NSD / mDNS peut être filtré par certains routeurs ou hotspots ;
- certains hotspots isolent les appareils entre eux ;
- Android peut limiter la découverte réseau selon l'état Wi-Fi ou batterie ;
- l'IP peut changer après veille ou changement de réseau ;
- le port préféré peut être indisponible ;
- une reconnexion mal conçue peut créer plusieurs connexions concurrentes ;
- une resynchronisation trop lourde peut perturber le live.

Mitigations :

- conserver le fallback manuel en options avancées ;
- utiliser des timeouts courts ;
- limiter la découverte aux écrans ou modes explicites ;
- garder une seule session active par rôle ;
- fermer proprement les anciennes connexions ;
- renvoyer un paquet complet au changement de morceau ;
- garder toutes les opérations réseau hors thread principal ;
- ne jamais bloquer ExoPlayer.

---

## Roadmap

### V2.1 — Découverte Automatique

Objectif :

- publier les sessions de diffusion disponibles depuis l'appareil `Diffuser les paroles` ;
- découvrir les sessions SMP sur le réseau local depuis les appareils `Afficher les paroles` ;
- afficher une liste simple de sessions à l'utilisateur ;
- conserver la connexion manuelle en fallback.

LocalLink reste inchangé.

### V2.2 — Connexion En Un Clic

Objectif :

- connecter une session découverte en utilisant son véritable endpoint LocalLink ;
- supprimer la saisie manuelle IP/port du parcours principal ;
- conserver les options avancées comme fallback et diagnostic.

LocalLink reste inchangé.

### V2.3 — Gestion Automatique De La Session

Objectif :

- connecter automatiquement le récepteur à la session compatible découverte ;
- relancer la découverte si la connexion est perdue ;
- envoyer automatiquement le morceau courant et les changements de morceau depuis le Player ;
- conserver les commandes de test dans les options avancées.

Cette étape est validée pour le deuxième écran V2.3.

### V2.4 — Appairage

Objectif :

- demander une autorisation sur l'appareil cible ;
- mémoriser l'appareil accepté ;
- empêcher l'écrasement silencieux d'un appareil déjà appairé.

Le stockage d'identité existant doit être réutilisé autant que possible.

### V2.5 — Synchronisation Robuste

Objectif :

- éviter les pertes de synchronisation lors des changements de morceau ;
- renvoyer les données complètes nécessaires avant les messages temporels ;
- reprendre proprement après reconnexion ;
- vérifier que `timeMs` reste la seule référence temporelle.

Cette étape concerne directement le deuxième écran live.

### Extensions SMP

Objectif :

- réutiliser la même architecture pour accords, prompteur, annotations, contrôle distant et SMP Sync ;
- ajouter des capacités sans créer de nouveau moteur réseau ;
- conserver une UX simple par intention utilisateur.

Chaque extension doit rester progressive, testable et désactivable si elle menace la stabilité live.

---

## Règle Finale

La V2 réseau SMP n'est pas un nouveau protocole imposé à l'utilisateur.

C'est une couche d'orchestration qui rend le moteur existant utilisable par un musicien :

```text
intention utilisateur
↓
découverte
↓
appairage
↓
supervision
↓
session live
↓
LocalLink
```

Si une évolution réseau menace :

- la stabilité live ;
- la compatibilité téléphone / tablette ;
- la simplicité utilisateur ;
- la possibilité de fallback manuel ;

elle doit être rejetée ou repoussée.

Le réseau SMP ne doit jamais devenir un prérequis pour jouer.

Le fonctionnement local de Stage Music Player reste toujours prioritaire. Le réseau enrichit les possibilités de SMP, mais il ne doit jamais empêcher un musicien de jouer si aucun autre appareil n'est disponible.

Stabilité live > fonctionnalité réseau.
