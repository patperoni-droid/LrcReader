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

## Quarantaine temporaire (QuickPlaylistsScreenTest)
Si un device flaky casse le run global, tu peux isoler temporairement:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.patrick.lrcreader.ui.QuickPlaylistsScreenTest
```

Puis lancer ce test seul:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.patrick.lrcreader.ui.QuickPlaylistsScreenTest
```
