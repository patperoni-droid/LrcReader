# Utiliser un Deuxième écran

Le Deuxième écran affiche les paroles du morceau courant sur un autre téléphone ou une autre tablette. Les deux appareils communiquent sur le même réseau local ; Internet et le cloud ne sont pas nécessaires.

## Rôles des appareils

- **Diffuser les paroles** : appareil principal qui lit le morceau et envoie l’affichage.
- **Afficher les paroles** : appareil secondaire qui reçoit les paroles.

Le réseau ne pilote pas la lecture audio principale. Une perte de connexion ne doit donc pas arrêter le spectacle sur l’appareil diffuseur.

## Préparer la connexion

1. connectez les deux appareils au même Wi-Fi ou au même point d’accès local ;
2. ouvrez Stage Music Player sur les deux ;
3. laissez l’écran Deuxième écran ouvert pendant la connexion ;
4. vérifiez qu’Android n’isole pas les appareils sur le réseau invité.

## Sur l’appareil secondaire

1. ouvrez **Plus > Deuxième écran** ;
2. choisissez **Afficher les paroles** ;
3. laissez la recherche automatique démarrer ;
4. attendez l’état de connexion.

## Sur l’appareil principal

1. ouvrez **Plus > Deuxième écran** ;
2. choisissez **Diffuser les paroles** ;
3. appuyez sur **Activer Deuxième écran** ;
4. revenez au Lecteur et ouvrez un morceau avec paroles.

Le récepteur recherche une session compatible et s’y connecte. Le morceau courant et les changements suivants sont alors transmis automatiquement.

## Vérifier avant la scène

1. lancez un morceau avec paroles ;
2. contrôlez le titre et le texte sur le récepteur ;
3. changez de morceau ;
4. déplacez la lecture ;
5. coupez brièvement le réseau puis vérifiez la reconnexion ;
6. testez l’orientation et la lisibilité à la distance réelle.

La version actuelle a été validée pour le parcours principal. Des améliorations d’appairage explicite et de synchronisation plus robuste sont encore prévues ; elles ne doivent pas être supposées disponibles.

## Options avancées

Le mode manuel par adresse IP et port reste disponible comme solution de secours et outil de diagnostic. Les actions d’envoi du morceau courant ou d’un morceau test peuvent également y apparaître.

Utilisez ce mode uniquement si la découverte automatique échoue et si vous connaissez l’adresse affichée par l’appareil diffuseur. Un changement de réseau peut modifier cette adresse.

## Précautions réseau

- Utilisez un réseau local stable et privé.
- Évitez les Wi-Fi publics qui bloquent les communications entre appareils.
- Désactivez temporairement les VPN si la découverte ne fonctionne pas.
- Gardez les deux appareils suffisamment proches du point d’accès.
- Préparez les paroles localement sur l’appareil principal avant le concert.

## Problèmes courants

### Aucun diffuseur n’est trouvé

Vérifiez que les appareils utilisent le même réseau, que **Activer Deuxième écran** est actif et que le réseau autorise les échanges locaux. Essayez ensuite le mode IP/port avancé.

### Les paroles restent sur l’ancien morceau

Vérifiez l’état de connexion, revenez au Lecteur principal puis changez de morceau une nouvelle fois. Si nécessaire, rouvrez **Afficher les paroles**.

### La connexion se coupe

Rapprochez les appareils du point d’accès et désactivez les économies d’énergie agressives. Le récepteur tente de se reconnecter, mais une validation dans les conditions réelles reste indispensable.

### La musique s’arrête sur l’appareil principal

Ce n’est pas le comportement attendu du Deuxième écran. Continuez localement, désactivez la fonction réseau et ne la réactivez qu’après le morceau.

Chapitre suivant : [Transférer une sélection avec SMP Sync](23-smp-sync.md).
