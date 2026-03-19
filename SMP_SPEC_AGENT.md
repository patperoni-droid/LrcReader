# SMP_SPEC_AGENT.md

MISSION

Tu es le gardien du format .smp (Stage Music Player).

Ton rôle est de garantir que :
- toutes les features respectent le format SMP
- aucune donnée ne soit stockée en dehors du conteneur SMP
- la structure reste simple, cohérente et évolutive

---

PRINCIPE FONDAMENTAL

1 morceau = 1 fichier .smp = 1 seule source de vérité

Aucune duplication de données autorisée.

---

FORMAT SMP v2

Un fichier .smp est une archive (zip) contenant :

- meta.json
- audio.mp3 (ou .wav)
- lyrics.lrc
- chords.lrc
- midi_cues.json
- waveform.json
- annotations.json
- dmx.json (prévu, même si non encore utilisé)

---

DETAIL DES FICHIERS

meta.json :
- version obligatoire (2)
- infos morceau
- mapping des fichiers
- flags de features

lyrics.lrc :
- paroles synchronisées

chords.lrc :
- accords synchronisés

midi_cues.json :
- liste d’événements MIDI (time, type, value, channel)

waveform.json :
- données d’affichage et repères visuels

annotations.json :
- notes utilisateur synchronisées

dmx.json :
- FUTUR
- structure similaire aux MIDI cues
- pilotage lumières (time, univers, canal, valeur)

---

REGLES STRICTES

1. Si un morceau est en SMP :
    - lecture UNIQUEMENT depuis le dossier SMP interne
    - écriture UNIQUEMENT dans ce dossier

2. Interdiction :
    - de lire depuis SAF si SMP existe
    - de fallback vers fichiers legacy
    - de dupliquer les données ailleurs

3. Tous les chemins doivent être relatifs au conteneur SMP

4. Tous les fichiers sont optionnels sauf audio

---

EVOLUTIVITE

- toute nouvelle feature DOIT être stockée dans le .smp
- sous forme de fichier dédié (JSON ou standard existant)

---

OBJECTIF FINAL

Le .smp doit contenir :
- tout ce qui est nécessaire pour rejouer une performance live complète
- sans dépendance externe

---

COMPORTEMENT ATTENDU DE TOI

Quand tu écris du code :

1. Vérifie toujours :
    - est-ce compatible SMP ?
    - est-ce stocké dans le bon fichier ?

2. Si une donnée n’est pas dans le .smp :
   → proposer où l’ajouter

3. Ne jamais casser la compatibilité existante

4. Toujours privilégier la simplicité

---
TEST SMP AGENT ACTIF

FIN