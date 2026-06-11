# RELEASE SAFETY RULES — Stage Music Player

CRITICAL — LIVE PHONE VERSION PROTECTION

This document defines the official SMP strategy for protecting a phone version validated in rehearsal or concert.

Any process that puts a validated live phone version at risk must be rejected.

⸻

PRINCIPE

Une version téléphone utilisée en concert est considérée comme critique.

Les évolutions tablette, UX ou confort de scène ne doivent jamais compromettre cette version.

Le téléphone reste la base stable SMP.

⸻

STRATÉGIE OFFICIELLE SMP

Avant toute série de modifications importantes :

1. Créer un tag Git stable.
2. Générer un APK de secours.
3. Documenter le commit correspondant.
4. Continuer les développements uniquement après cette sécurisation.

Cette stratégie doit rester simple, reproductible et utilisable même sous pression avant un concert.

⸻

PHILOSOPHIE

Concert validé
↓
Version stable
↓
Tag Git
↓
APK de secours
↓
Nouveaux développements

⸻

PROCÉDURE DE SÉCURISATION

Avant travaux risqués :

- vérifier que l'arbre Git est propre ;
- identifier le commit stable ;
- créer un tag stable ;
- construire un APK de secours ;
- enregistrer son emplacement ;
- noter clairement le lien entre le tag, le commit et l'APK.

Le but est de pouvoir retrouver et réinstaller rapidement la version téléphone validée.

⸻

PROCÉDURE EN CAS DE PROBLÈME

SI LE TÉLÉPHONE EST CASSÉ :

1. Identifier le dernier tag stable.
2. Retrouver l'APK correspondant.
3. Réinstaller l'APK si nécessaire pour le concert.
4. Comparer le tag stable avec HEAD.
5. Identifier le patch responsable.
6. Corriger ou annuler ce patch.
7. Revalider le téléphone avant de poursuivre.

La priorité est de restaurer une version jouable et fiable, pas de sauver la dernière expérimentation.

⸻

RÈGLE IMPORTANTE

Le propriétaire du projet n'étant pas à l'aise avec des workflows Git complexes :

- éviter les stratégies basées sur de multiples branches ;
- privilégier une approche simple :
  - tag Git ;
  - APK de secours ;
  - procédure documentée.

Les branches peuvent exister pour le développement, mais elles ne doivent pas être la base de la stratégie de secours live.

⸻

VERSIONS DE CONCERT

Toute version validée en répétition peut devenir une version de concert.

Une version de concert :

- doit être reproductible ;
- doit pouvoir être restaurée rapidement ;
- doit rester disponible même après plusieurs mois de développement.

Une version de concert n'est pas seulement un état Git : elle doit aussi correspondre à un APK réellement récupérable.

⸻

ÉVOLUTIONS TABLETTE ET UX

Les évolutions tablette sont des extensions de confort et de cockpit live.

Elles doivent :

- rester conditionnées au mode tablette ou au Split Layout ;
- préserver le comportement téléphone validé ;
- être développées seulement après sécurisation d'une version téléphone stable quand les travaux sont importants ou risqués.

Le confort tablette ne justifie jamais de perdre une version téléphone utilisable en concert.

⸻

CURRENT STABLE PHONE RELEASE

Date:
2026-06-11

Tag:
phone-stable-before-tablet-ux

Commit:
3fe1ec7

APK backup:
backups/phone-stable-before-tablet-ux.apk

Purpose:
First officially protected phone version before major tablet UX evolution.

Validated features:

- bus principal OK
- DJ OK
- faders lecteurs OK
- mixage titre OK
- bloc-notes OK
- accordeur OK
- playlists stables
- partage des paroles OK
- navigation téléphone stable
- tablette en cours d'évolution UX

This section is the authoritative record for the currently protected phone release.

⸻

EMERGENCY RECOVERY PROCEDURE

If the phone version becomes unstable:

1. Locate the APK:
   backups/phone-stable-before-tablet-ux.apk

2. Reinstall the APK on the concert phone if immediate recovery is required.

3. Identify the protected tag:
   phone-stable-before-tablet-ux

4. Compare the protected tag with the current HEAD to identify the patch that introduced the regression.

5. Restore phone stability before continuing any tablet development.

6. Do not continue feature work until the phone version is validated again.

The APK path and tag above must be sufficient for an external developer to restore the protected phone version without relying on memory or chat history.

⸻

IMPORTANT PRINCIPLE

The project owner must not be required to remember Git procedures.

All recovery information must be written inside this document.

Any new protected phone release must update this section with:

- tag name
- commit id
- APK location
- validation scope

⸻

RÈGLE ABSOLUE

En cas de conflit entre :

- nouvelles fonctionnalités tablette ;
- stabilité téléphone validée ;

👉 la stabilité téléphone a priorité.

⸻

FINAL RULE

If a live-validated phone version exists, protect it first.

Tag first.
Build backup APK first.
Then develop.
