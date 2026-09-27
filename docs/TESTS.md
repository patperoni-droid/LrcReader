# Tests MusiMio (SMP)

## Sécurité des appareils (CRITIQUE)

Ne jamais lancer automatiquement `tests_device.sh`, `connectedAndroidTest` ou une variante de
cette tâche sur un téléphone ou une tablette contenant l'installation utilisateur. Utiliser par
défaut un émulateur dédié ou un appareil de test isolé. Un appareil utilisateur exige une
autorisation explicite préalable, même avec
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.

Voir la [règle globale de sécurité](00_PROJECT_RULES.md#instrumented-tests-and-user-devices-critical).

## Lancement local (macOS/Linux)
```bash
./tests.sh
```

## Instrumented (device requis)
```bash
./tests_device.sh
```

## Lancement local (Windows PowerShell)
```powershell
.\tests.ps1
```

## Quand lancer
- Avant chaque push.
- Avant chaque release/concert.
- Après un changement dans `app/src/main` ou `app/src/test`.
- Après un changement d'UI Compose : lancer aussi `./tests_device.sh`, uniquement sur un
  émulateur dédié ou un appareil de test isolé.

## Si un test échoue
1. Lire le rapport Gradle dans `app/build/reports/tests/` (unit tests) ou `app/build/reports/androidTests/` (instrumented).
2. Corriger le bug ou le test cassé.
3. Relancer `./tests.sh` (ou `.\tests.ps1`) jusqu'à succès complet.

## Notes
- `./tests.sh` = gate local/pre-push (unit tests + build debug).
- `./tests_device.sh` = tests instrumentés (`connectedAndroidTest`) ; il peut installer puis
  désinstaller le package principal et ne doit jamais être lancé automatiquement sur l'appareil
  physique utilisateur.
- Alias Gradle disponible:
```bash
./gradlew :app:ci
```

## Couvertures ciblées Prompteur, accords et pédale

Le [contrat transversal ChordPro](Features/FEATURE_CHORDPRO.md) définit les invariants
fonctionnels. La présente section inventorie les tests réellement présents ; elle ne
constitue pas une seconde spécification du parser ou de l'import.

### Tests ChordPro existants

- `ChordProParserTest` couvre le parser canonique : accords simples, accords enrichis,
  slash chords, suffixes supportés, tags complets invalides, crochets non musicaux,
  positions UTF-16, Unicode et conservation de la source. Les mots apostrophés
  `C'est` et `D'accord` sont protégés dans les scénarios d'import de lignes, où leur
  token complet est refusé.
- `ChordProImportNormalizerTest` couvre l'import `**accord**` : grammaire canonique,
  plages exactes, mots non musicaux, ChordPro existant, balisages incomplets ou
  ambigus, préservation des caractères et séparateurs, absence de compensation
  d'espaces et idempotence.
- `ChordLineImportNormalizerTest` couvre la reconnaissance token par token, le minimum
  de deux accords, les tokens ordinaires inconnus, les blocs de une, deux et trois
  lignes, le refus de quatre lignes, les sections et balises, le ChordPro existant,
  les projections sûres ou refusées, les tabulations, LF/CRLF/CR, les accords au même
  offset, les caractères Unicode et l'idempotence.
- `ScrollingTextChordProImportTest` couvre la couche pure utilisée par l'UI : priorité
  de `**accord**` sur les lignes/blocs, suggestion unique, Convertir, Ignorer, nouvelle
  proposition après modification ou nouvelle session, refus d'une source obsolète,
  remappage du curseur, des sélections normales ou inversées et de la composition IME.
- `ChordTranspositionTest` couvre le moteur commun : notes naturelles, dièses,
  bémols, suffixes, slash chords, offsets positifs/négatifs, modulo douze et source
  inchangée à zéro.
- `PrompterTranspositionControlTest` couvre le formatage et les bornes `-11..+11` du
  contrôle partagé. `TextPrompterDisplaySettingsStoreTest` couvre la persistance, les
  valeurs par défaut, le clamp, la compatibilité des anciennes valeurs, les espaces
  de clés distincts et la conservation de `syncPitchCompensation`.
- `ChordProPrompterLayoutTest`, `PrompterTextPreparationTest` et
  `SinglePrompterRenderLineTest` couvrent le rendu Prompteur, les ancres, les chemins
  simples/enrichis, le wrapping, les lignes d'accords, la transposition visuelle et la
  conservation du texte visible.
- `AudioLyricsChordGridTest` couvre la Grille dérivée, les occurrences répétées, les
  lignes non minutées, la transposition non destructive, la ligne active et le fallback
  legacy. `LyricsAreaChordProIntegrationTest` couvre le parser/rendu partagé, la
  transposition et la conservation du `timeMs` et de l'identité des `LrcLine`.
  `LyricsEditorChordProTimingMergeTest` couvre la conservation des timings pendant
  les éditions ChordPro. `DisplayedChordTranspositionTest` couvre la relation entre
  accords affichés, pitch et `Sync Pitch` sans réécriture de la source.
- `AudioLyricsChordProToolbarTest` couvre les décisions d'affichage de la palette et
  les actions d'insertion de la barre Audio Lyrics.
- `PrompterKeyMappingTest` couvre les touches du Prompteur, les bornes, le déplacement
  de 65 %, le chevauchement de 35 % et la durée d'animation de 300 ms.
- `ChordProTextEditingTest` couvre le remplacement exact par ancres `parseChordPro`, les
  symboles voisins non modifiés, les occurrences multiples, altérations/slash chords et
  la déduplication de la palette automatique.
- `PlaylistPedalNavigationTest` couvre la logique pure de mapping et de bornes. Ses tests
  de répétition clavier ne certifient pas un appui long réel : ce geste reste non garanti
  avec la pédale testée et ne doit pas être présenté comme fonctionnalité stable.
- La validation de reprise de l'auto-scroll du Prompteur, du focus matériel et du retour
  Player → playlist exige un essai manuel sur l'appareil concerné.

### Tests ChordPro à prévoir

Les points suivants sont recommandés mais ne doivent pas être présentés comme déjà
couverts :

- test Compose direct confirmant que le contrôle de transposition du Prompteur est
  visible avec des accords et absent sans accord ;
- test Compose ou instrumenté de la bannière réelle, de son unicité et de ses actions,
  en complément des tests purs de `ScrollingTextChordProImportTest` ;
- corpus progressif de faux positifs français, anglais et espagnols lorsqu'une couche
  linguistique sera justifiée et implémentée ;
- scénarios supplémentaires issus de copier-coller HTML ou d'autres sources ayant
  perdu leurs espaces d'alignement.

La future matrice FR/EN/ES testera uniquement un filtre d'ambiguïté séparé. Elle ne
doit ni spécialiser ni dupliquer les tests du parser musical canonique selon la
langue. Aucune couche linguistique n'est implémentée aujourd'hui.

## Quarantaine temporaire (QuickPlaylistsScreenTest)
Si un device flaky casse le run global, tu peux isoler temporairement:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.patrick.lrcreader.ui.QuickPlaylistsScreenTest
```

Puis lancer ce test seul:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.patrick.lrcreader.ui.QuickPlaylistsScreenTest
```
