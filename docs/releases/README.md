# Procédure complète de publication Alpha

Ce dossier centralise la documentation durable des publications Google Play de Stage Music Player.

Références officielles à consulter avant toute publication :

- `docs/03_RELEASE_SAFETY_RULES.md`
- `docs/04_GOOGLE_PLAY_RELEASE_GUIDE.md`
- `docs/releases/RELEASE_TEMPLATE.md`

---

# Préparation avant release

Avant de préparer une version Alpha :

- vérifier que la version ciblée est cohérente avec l'état réel du projet ;
- relire les changements depuis la dernière release ;
- vérifier que les décisions importantes sont documentées ;
- vérifier que les bugs connus sont identifiés ;
- vérifier que les notes Play Store sont prêtes ;
- confirmer que la variante Google Play reste `laboRelease`.

La publication Google Play ne doit jamais servir à tester une version incertaine.

---

# Commit Git

Avant génération du bundle :

- tous les changements inclus dans la release doivent être commités ;
- le dernier commit doit être identifié dans la fiche de release ;
- les fichiers non commités restants doivent être explicitement exclus de la release ou traités avant publication ;
- aucune publication ne doit dépendre d'un état local impossible à retrouver.

Chaque fiche de release doit indiquer le commit Git exact utilisé.

---

# Incrément du versionCode

Avant chaque upload Google Play :

- incrémenter `versionCode` dans `app/build.gradle.kts` ;
- vérifier que ce `versionCode` n'a jamais été publié ;
- conserver la correspondance entre `versionCode`, `versionName` et commit Git dans la fiche de release.

Google Play refuse tout App Bundle dont le `versionCode` a déjà été utilisé.

---

# Convention versionName

Convention actuelle :

```text
0.x-beta-labo
```

Exemple :

```text
0.4.1-beta-labo
```

La variante officielle Google Play est `laboRelease`.

Si Gradle définit `versionName` sans suffixe `-labo`, vérifier le nom final produit pour la variante `labo`.

---

# Génération du App Bundle (.aab)

Commande officielle :

```bash
./gradlew :app:bundleLaboRelease
```

Bundle attendu :

```text
app/build/outputs/bundle/laboRelease/app-labo-release.aab
```

Vérifications minimales :

- la commande se termine sans erreur ;
- le fichier `.aab` existe ;
- le bundle est signé avec le keystore officiel ;
- aucun secret de signature n'est documenté dans le dépôt.

Vérification de signature possible :

```bash
jarsigner -verify -certs app/build/outputs/bundle/laboRelease/app-labo-release.aab
```

Résultat attendu :

```text
jar verified.
```

---

# Publication dans Tests fermés Alpha

Dans Google Play Console :

1. Ouvrir l'application Stage Music Player.
2. Aller dans `Tester et publier`.
3. Ouvrir le canal `Tests fermés Alpha`.
4. Créer une nouvelle version.
5. Importer le fichier `.aab` généré.
6. Vérifier les avertissements Google Play.
7. Ajouter les notes de version.
8. Enregistrer la version.
9. Soumettre la version à examen.

---

# Vérifications Google Play

Avant soumission :

- vérifier que le `versionCode` affiché correspond à la fiche de release ;
- vérifier que le `versionName` affiché correspond à la version attendue ;
- vérifier que le canal est bien `Tests fermés Alpha` ;
- vérifier que les notes de version sont présentes ;
- vérifier qu'aucune erreur bloquante n'est signalée ;
- vérifier que la version ne vise pas par erreur un canal Beta ou Production.

---

# Validation

Une release Alpha est considérée prête à soumettre seulement si :

- le bundle est généré ;
- le bundle est signé ;
- le commit Git est identifié ;
- la fiche de release existe ;
- les notes Play Store sont prêtes ;
- les changements inclus sont compris ;
- les bugs connus sont assumés.

---

# Examen Google

Après soumission :

- noter le statut dans la fiche de release ;
- surveiller les messages Google Play ;
- ne pas supposer que la version est disponible tant que Google ne l'a pas validée ;
- conserver les commentaires ou demandes de Google dans la fiche de release.

Statuts usuels :

- préparée ;
- soumise à examen ;
- acceptée ;
- refusée ;
- disponible pour les testeurs.

---

# Disponibilité pour les testeurs

Après validation Google :

- vérifier que la version apparaît dans le canal Alpha ;
- vérifier que les testeurs fermés ont accès à la version ;
- installer ou mettre à jour l'application depuis Google Play si possible ;
- noter la disponibilité réelle dans la fiche de release.

---

# Actions après publication

Après disponibilité :

- compléter la fiche de release ;
- noter les premiers retours testeurs ;
- créer ou mettre à jour les documents de roadmap si nécessaire ;
- conserver les décisions importantes dans `docs/history/DECISIONS.md` ;
- préparer les corrections dans des commits séparés.

---

# Checklist de release

- [ ] Changements de la release identifiés.
- [ ] Documentation utile mise à jour.
- [ ] Fiche de release créée dans `docs/releases/`.
- [ ] Bugs connus renseignés.
- [ ] Notes Play Store rédigées.
- [ ] Tous les changements de la release sont commités.
- [ ] SHA du commit Git noté.
- [ ] `versionCode` incrémenté.
- [ ] `versionName` vérifié.
- [ ] Commande `./gradlew :app:bundleLaboRelease` exécutée.
- [ ] `.aab` généré à l'emplacement attendu.
- [ ] Signature vérifiée si nécessaire.
- [ ] Upload effectué dans `Tests fermés Alpha`.
- [ ] Messages Google Play vérifiés.
- [ ] Version soumise à examen.
- [ ] Statut Google noté dans la fiche.
- [ ] Disponibilité testeurs vérifiée après validation.
- [ ] Retours post-publication consignés.
