# FEATURE — SMP SYNC

## Objectif Produit

SMP Sync permet de tenir un téléphone secours à jour depuis un téléphone principal avant un concert.

Le but est simple :

- préparer le téléphone principal
- envoyer localement les éléments choisis
- importer manuellement sur le téléphone secours
- garder un backup exploitable sans cloud

SMP Sync est une fonction de confort Premium. Elle ne remplace pas l’export/import manuel classique disponible en sauvegarde.

Retour terrain concert réel :

Le même principe s'applique au couple téléphone → tablette :

- préparation sur téléphone
- synchronisation périodique vers tablette
- tablette utilisée comme cockpit live principal

Le téléphone reste utile pour la préparation, la mobilité, la répétition et le secours.
La tablette est plus adaptée à la scène grâce à la vision globale, la playlist visible en permanence et les paroles simultanées.

---

## Principes SMP

- pas de cloud
- pas d’Internet obligatoire
- transfert local via LocalLink / Wi-Fi local / hotspot
- `.smp` reste un format de transport
- import toujours normalisé vers `/files/tracks/{songId}/`
- aucun `.smp` lu en runtime live
- Player, AudioEngine, autoplay, Define Next et timeline restent hors du système Sync

Le runtime live continue de manipuler de vrais `SongUnit` locaux.

---

## Parcours Utilisateur Actuel

1. Téléphone A, principal : créer une connexion
2. Téléphone B, secours : rejoindre la connexion avec IP/port
3. Téléphone A : choisir les éléments à synchroniser
4. Téléphone A : envoyer
5. Téléphone B : recevoir le package
6. Téléphone B : importer après confirmation utilisateur

Le flux cible est :

choisir → envoyer → importer

---

## Base V2 — Identité Et Reconnexion

SMP Sync V2 prépare un parcours plus simple sans remplacer le moteur V1.

Chaque appareil possède une identité locale persistante :

- `localDeviceId`
- `localDeviceName`
- rôle préféré : téléphone principal, appareil secours ou non défini
- appareil associé si connu
- dernier endpoint connu : host + port

LocalLink transporte maintenant l’identité SMP Sync dans le message `Hello` :

- `deviceId`
- `deviceName`
- `deviceRole`

Ces champs restent optionnels pour préserver la compatibilité avec les anciens messages.

Règles :

- ne jamais régénérer l’identifiant local sans action explicite future
- ne pas écraser silencieusement un appareil associé différent
- mémoriser l’endpoint dès qu’une connexion client LocalLink réussit
- compléter ensuite nom, identifiant et rôle quand le `Hello` est reçu
- conserver le mode manuel IP/port comme fallback

---

## Mode Recevoir

Le mode `Recevoir` sert à rendre l’appareil secours joignable de façon explicite.

Workflow recommandé :

1. Tablette ou appareil secours : ouvrir SMP Sync
2. appuyer sur `Recevoir`
3. vérifier l’état `Prêt à recevoir`
4. téléphone principal : appuyer sur `Reconnecter` si un endpoint est connu

Le serveur LocalLink reste actif tant que l’écran Sync est ouvert ou que le mode réception est actif dans cet écran.

Contraintes :

- pas de service Android permanent
- pas de découverte NSD/mDNS à cette étape
- pas d’import automatique
- pas de transfert automatique silencieux
- si la reconnexion échoue, l’utilisateur doit ouvrir SMP Sync sur l’appareil secours et appuyer sur `Recevoir`

Le port de réception peut être réutilisé pour rendre la reconnexion plus stable après redémarrage. Si le port préféré est indisponible, l’application peut basculer vers un autre port et l’afficher clairement.

---

## Mode Manuel V1

La V1 actuelle est pilotée par l’utilisateur.

L’utilisateur choisit explicitement ce qui part du téléphone principal :

- morceaux
- playlists

Prévu plus tard :

- bloc-notes
- prompteurs

Ce mode ne dépend pas de la détection automatique de différences. Il évite les ambiguïtés liées aux titres similaires, aux anciens `songId`, aux réglages locaux ou aux états playlist.

---

## Import Sur Téléphone Secours

L’import est toujours manuel et confirmé.

Règles :

- aucun import automatique silencieux
- aucune suppression automatique
- aucun merge automatique complexe
- remplacement uniquement après validation utilisateur
- `songId` conservé
- si un morceau existe déjà avec le même `songId`, ses données peuvent être mises à jour depuis le package reçu

Pour un morceau, l’import peut mettre à jour les données associées :

- audio
- paroles
- accords
- timeline
- réglages, incluant `playback` et les champs LUFS de `config.json`
- arrangement
- autres fichiers normalisés du SongUnit

Après import, le téléphone secours doit travailler depuis son runtime local normalisé.

Référence LUFS : `FEATURE_LUFS_PREPARATION.md`. Les réglages LUFS font partie du `SongUnit/config.json` et doivent suivre la sync manuelle avec le morceau.

---

## Playlists Et Familles

Les playlists peuvent être envoyées manuellement via SMP Sync.

Elles restent des structures :

- références `songId`
- ordre
- groupes
- couleurs
- familles playlist si présentes

Le transfert ne duplique pas l’audio dans les playlists. Une famille playlist reste une couche UX qui résout vers un vrai `songId` actif.

---

## Freemium / Premium

Freemium :

- export manuel
- import manuel
- sauvegarde/restauration classique

Premium :

- SMP Sync local
- confort de transfert entre téléphone principal et téléphone secours
- analyse différentielle et diagnostics d’aide
- transfert manuel guidé

---

## Analyse Différentielle

Le moteur automatique de manifest/diff/package reste disponible comme outil de diagnostic et d’aide.

En V1, il ne doit pas être la seule source de vérité utilisateur :

- les faux positifs restent possibles
- deux morceaux visuellement identiques peuvent avoir des `songId` différents
- certains états locaux peuvent différer entre appareils

Le mode manuel est donc prioritaire pour l’UX live.

---

## Limites Actuelles

- IP/port restent disponibles comme fallback manuel
- QR code et découverte automatique : futur
- pas de cloud
- pas de DJ en V1
- pas de suppression automatique
- pas de résolution automatique des conflits `songId`
- pas de merge complexe multi-utilisateur

---

## Règles De Stabilité

SMP Sync ne doit jamais perturber le live.

Interdits :

- lecture de `.smp` pendant le live
- traitement lourd sur le thread principal
- remplacement silencieux
- suppression automatique
- modification du Player ou de l’AudioEngine pour la sync

Priorité :

stabilité live > automatisation > confort.
