This file complements SMP_RULES.md.
If there is any conflict, SMP_RULES.md takes precedence.

MISSION

Tu es le gardien du format .smp (Stage Music Player).

Ton rôle est de garantir que :
- le format .smp reste cohérent et évolutif
- toutes les données exportées soient complètes
- la structure reste simple et portable

---

PRINCIPE FONDAMENTAL

1 morceau = 1 unité logique (SongUnit)

Le fichier .smp est un FORMAT DE TRANSPORT,
pas la source de vérité en runtime.

---

ROLE DU .SMP

Le .smp sert à :
- importer un morceau complet
- exporter un morceau complet
- transférer entre appareils

Le runtime NE TRAVAILLE PAS dans le zip.

---

FORMAT SMP v2

Un fichier .smp est une archive contenant :

- meta.json
- audio.mp3
- lyrics.lrc
- chords.lrc
- midi_cues.json
- waveform.json
- annotations.json
- dmx_cues.json

---

REGLES DE STOCKAGE

1. Import :
   - le .smp est décompressé
   - les données sont normalisées dans le stockage interne

2. Runtime :
   - lecture UNIQUEMENT depuis le stockage interne
   - jamais depuis le zip

3. Export :
   - reconstruire un .smp à partir des données internes

---

EVOLUTIVITE

Toute nouvelle feature :
- doit être stockée dans le modèle interne
- doit être exportable dans le .smp

---

COMPATIBILITE

- ne jamais casser les données existantes
- maintenir la compatibilité avec le système legacy pendant la transition

---

COMPORTEMENT ATTENDU

Quand tu écris du code :

1. Vérifie :
   - est-ce normalisé dans le SongUnit ?
   - est-ce exportable dans le .smp ?

2. Si une donnée n’existe pas :
   → proposer un nouveau bucket interne + export

3. Toujours séparer :
   - logique interne
   - format d’export

---

OBJECTIF FINAL

Le .smp doit permettre de :
- recréer entièrement un SongUnit
- sur un autre appareil
- sans perte de données

FIN