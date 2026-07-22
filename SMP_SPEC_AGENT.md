CRITICAL — SMP IMPLEMENTATION SPEC

This document defines how SMP_RULES.md must be applied in code.

It is intended for coding agents (Codex).

If there is any conflict:
👉 SMP_RULES.md takes precedence.

This file complements SMP_RULES.md.
If there is any conflict, SMP_RULES.md takes precedence.

MISSION

Tu es le gardien du format .smp (Stage Music Player).

Ton rôle est de garantir que :
- le format .smp reste cohérent et évolutif
- toutes les données exportées soient complètes
- la structure reste simple, portable et fiable

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

MODELE RUNTIME (IMPORTANT)

Le runtime travaille sur un modèle interne normalisé :

- chaque morceau est une SongUnit
- toutes les données sont locales et prêtes à être utilisées
- aucune lecture depuis archive en live

Principe :
IMPORT → NORMALISATION → UTILISATION → EXPORT

---

FORMAT SMP v2

Un fichier .smp est une archive contenant :

- meta.json
- audio.mp3 (ou wav)
- lyrics.lrc
- chords.lrc
- midi_cues.json
- waveform.json
- annotations.json
- dmx_cues.json
- arrangement.json
- arrangement_variants.json

Arrangement transport rule:
- `arrangement.json` stores the editable Arrangement project of the source SongUnit when present
- `arrangement_variants.json` stores lightweight virtual variants belonging to that source
- a virtual variant must never be exported alone without its source audio
- the parent audio is stored once and the variants are normalized into separate runtime SongUnits after import

---

REGLES DE STOCKAGE

1. Import :
   - le .smp est décompressé
   - les données sont normalisées dans le modèle interne

2. Runtime :
   - lecture UNIQUEMENT depuis le stockage interne
   - jamais depuis le zip
   - toutes les données doivent être disponibles localement

3. Export :
   - reconstruire un .smp à partir des données internes

---

FILESYSTEM RULE (CRITICAL)

Le filesystem doit refléter uniquement la réalité.

- aucun dossier fictif
- aucune création automatique inutile
- aucun fallback legacy visible

Exemples interdits :
- création automatique de "Midi"
- création automatique de "Videos"
- création automatique de "DJ"
- création automatique de "Config" sans write réel

Principe :
Ce qui n’existe pas → n’apparaît pas

---

TIMELINE = SOURCE DE VERITE

Toutes les données temporelles doivent être basées sur le temps :

- MIDI cues → timeMs
- DMX cues → timeMs
- Lyrics → time

Interdictions :
- dépendre de lineIndex
- dépendre de l’état UI

Principe :
Temps → décision → action

---

SEPARATION INTENT / EXECUTION

Toujours séparer :

1. Intent (données)
   - MIDI cues
   - DMX cues
   - annotations

2. Runtime
   - lecture timeline

3. Output
   - MIDI
   - DMX

Aucune dépendance directe entre ces couches.

---

EVOLUTIVITE

Toute nouvelle feature :
- doit être stockée dans le modèle interne
- doit être exportable dans le .smp

Si nécessaire :
→ créer un nouveau fichier dans le conteneur

---

COMPATIBILITE

- ne jamais casser les données existantes
- maintenir compatibilité legacy en lecture uniquement

IMPORTANT :
Le legacy ne doit plus être une source active d’écriture.

---

COMPORTEMENT ATTENDU

Quand tu écris du code :

1. Vérifie :
   - est-ce dans le SongUnit ?
   - est-ce exportable dans le .smp ?

2. Si une donnée n’existe pas :
   → proposer un stockage interne + export

3. Toujours séparer :
   - logique interne
   - format d’export

---

OBJECTIF FINAL

Le .smp doit permettre de :

- recréer entièrement un SongUnit
- sur un autre appareil
- sans perte de données

- garantir un comportement identique en live

---

PRINCIPE GLOBAL

Le .smp transporte la vérité  
Le runtime exécute la vérité

FINAL IMPLEMENTATION RULE

At runtime:
- NEVER depend on .smp archive structure
- ALWAYS depend on normalized SongUnit

Any code directly reading from .smp during playback must be rejected.

FIN
