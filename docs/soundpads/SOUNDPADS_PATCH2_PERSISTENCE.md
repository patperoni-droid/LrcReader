# Sound Pads — Patch 2, données durables

4 octobre 2026. Le Créateur a validé fonctionnellement le prototype audio (import, lecture, changement, réappui, Stop, coexistence). Ce patch conserve le moteur Media3 et une seule voix. Le Patch 3, grille téléphone/tablette 6/12, n’est pas commencé.

## Gestion disponible

Dans le même écran debug accessible par le menu téléphone ou ⚙️ du cockpit tablette :

- Banque vide au premier lancement ; « Ajouter un pad » crée un pad vide avec un UUID stable. Aucun plafond 6/12 dans le modèle ou le stockage.
- « Choisir un son » copie le fichier dans le stockage interne ; le fichier source peut ensuite être déplacé ou supprimé.
- « Modifier » conserve le nom, le volume individuel, IN et OUT en millisecondes. OUT vide signifie fin du fichier. Les trims négatifs, inversés ou dépassant la durée sont refusés. Un pad sans fichier ne peut pas être déclenché.
- Un remplacement conserve ID, nom, volume et couleur ; IN/OUT repartent sur 0/durée du nouveau fichier. Annulation du sélecteur : aucune modification. Échec de copie, de durée ou d’écriture de banque : ancienne référence et ancien fichier conservés.
- « Supprimer » demande une confirmation. Annuler conserve le pad. Confirmer publie la banque sans ce pad avant de nettoyer son audio, seulement si aucune référence ne subsiste.
- Fermeture/réouverture et redémarrage restaurent automatiquement la banque à l’ouverture du diagnostic. Aucun seed automatique de MP3 d’ambiance.

Le volume global reste le curseur de test non mémorisé déjà présent ; le fader de Bus définitif ne fait pas partie de ce patch. L’éditeur ne comporte ni waveform, transposition, timeline, export ni grille finale. `pitchSemitones=0` et `colorArgb` nullable sont persistés ; aucune commande de pitch ni palette d’apparence n’est activée.

## Stockage

Tout est privé à l’installation Android en cours, dans `context.filesDir` :

```text
soundpads/
  bank.json             # schemaVersion=1, tableau ordonné de pads
  audio/
    <UUID>.audio        # copie durable, conteneur original, sans conversion PCM
```

Dans `bank.json`, `audioFile` est un simple nom interne relatif, vide pour un pad sans son. Il n’existe aucune dépendance au chemin/URI d’origine. `audioPath` dans le modèle runtime est reconstruit sous le dossier audio de cette installation. Chaque entrée conserve `id`, `name`, `audioFile`, `volume`, `inMs`, `outMs` nullable, `pitchSemitones` et `colorArgb` nullable.

Le nom `.audio` ne transforme pas le format. Les conteneurs restent détectés par le moteur actuel. La récupération d’une durée valide contrôle l’import ; elle ne certifie pas tous les codecs décodables. Une éventuelle erreur de lecture reste gérée par Media3.

Les mutations sont sérialisées, exécutées hors du thread UI et protégées de l’annulation de coroutine en cours de transaction. La copie temporaire est synchronisée avant publication ; Android AtomicFile protège l’écriture de banque. La banque doit être enregistrée avant suppression de l’ancien audio. Le nettoyage est limité aux noms UUID gérés dans ce dossier et réessaie à la prochaine lecture réussie s’il échoue. Les références partagées sont prises en compte. Une coupure peut laisser un audio orphelin, nettoyé après lecture valide ; elle ne nécessite pas de suppression préalable de l’ancien fichier.

Une banque illisible ou de version non supportée provoque une erreur sans réinitialisation ni nettoyage destructif. Les contrôles d’écriture refusent les références hors du dossier géré. Une disparition extérieure d’un audio peut provoquer une erreur de lecture ; pas de recréation silencieuse.

## Migration et limites

Aucune migration de données MusiMio, de SongUnit ou de préférence existante. L’identifiant debug isolé reste inchangé.

L’ancien dossier `filesDir/soundpads-prototype` est conservé et n’est jamais nettoyé par le nouveau store. Le prototype précédent ne mémorisait pas les affectations aux pads : elles ne peuvent pas être reconstituées automatiquement. Pour la nouvelle banque, ajouter un pad et sélectionner de nouveau le fichier source. Aucun fichier externe original n’est modifié.

Ces données internes survivent aux redémarrages et mises à jour du même package. Elles ne survivent pas à une désinstallation/effacement des données. Aucun export ou transfert entre Labo/Concert n’est ajouté. Le schemaVersion permet de refuser une banque future incompatible plutôt que de l’écraser ; aucune migration vers une autre version n’est implémentée maintenant.

Risques restant à qualifier manuellement : espace disque insuffisant réel, très gros fichiers/providers distants, interruption du processus pendant import et codecs variés. Les copies longues sont faites en arrière-plan ; les boutons de mutation et fermeture restent désactivés pendant l’opération. Stop reste accessible.

## Vérifications exécutées

- APK debug Labo et Concert compilés avec les tâches normales.
- Cinq tests du modèle sur chaque variante : banque extensible, pad vide, paramètres réservés, volume et trim.
- Cinq tests de store sur application isolée/emulator-5556 : 25 pads restaurés, identités et paramètres restaurés, indépendance du fichier source, rollback d’import et d’écriture, remplacement et nettoyage, références partagées/couleur, refus d’une banque corrompue sans suppression d’audio.
- Quatre tests audio et un test Compose exécutés avec succès lors de cette étape sur le même package isolé. Le moteur audio est inchangé.
- Le premier scénario de panne d’écriture ne créait pas la panne attendue, Android supprimant son fichier temporaire vide. Le scénario corrigé bloque réellement l’écriture et passe.
- Les tests instrumentés utilisent `com.patrick.lrcreader.soundpadsprototype.labo.soundpads.debug` et son APK de test, jamais le package habituel de l’utilisateur. Pas d’installation sur appareil physique.
- La suite générale n’est pas relancée hors périmètre ; l’échec de couleurs Arrangement précédemment identifié reste hors de ce patch.

## Recette du Créateur avant Patch 3

1. Depuis Android Studio, lancer `laboDebug` puis **MusiMio Sound Pads Test → ⚙️ → Sound Pads — test audio**. Banque vide attendue au premier lancement du Patch 2.
2. Ajouter deux pads, choisir deux fichiers, renommer et régler des volumes différents. Vérifier le déclenchement, la substitution, le réappui et Stop pendant un morceau, puis avec DJ/Fond sonore.
3. Régler IN=1000 et OUT=3000 sur un fichier de plus de trois secondes ; vérifier l’extrait. OUT vide doit lire jusqu’à la fin. Vérifier le refus d’un OUT inférieur à IN ou supérieur à la durée.
4. Fermer la fenêtre, rouvrir, puis fermer complètement/relancer l’application : les pads, fichiers, noms, volumes et trims doivent être identiques. Le curseur global de diagnostic reste temporaire.
5. Déplacer/supprimer le fichier externe après import : la copie doit rester lisible après redémarrage.
6. Remplacer un son par un autre : identité/nom/volume conservés, plage 0/durée du nouveau son. Annuler le sélecteur ne change rien. Essayer un fichier vide ou non audio : l’ancien son doit rester lisible et ses trims conservés.
7. Supprimer un pad, annuler d’abord la confirmation, puis confirmer. Après redémarrage, seul le pad confirmé disparaît ; les autres fonctionnent.
8. Ajouter au moins 13 pads et vérifier leur conservation. L’écran reste une liste de gestion provisoire, sans pagination/grille finale.
9. Si souhaité, dans Device Explorer et uniquement le package de test : contrôler `files/soundpads/bank.json` et `files/soundpads/audio` avant/après remplacement et suppression. Ne pas modifier les données de l’application habituelle.
10. Refaire la recette sur téléphone/tablette réelle. Donner le retour avant tout lancement du Patch 3.
