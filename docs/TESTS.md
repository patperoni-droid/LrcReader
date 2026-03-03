# Tests SPL

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
- Après un changement d'UI Compose: lancer aussi `./tests_device.sh`.

## Si un test échoue
1. Lire le rapport Gradle dans `app/build/reports/tests/` (unit tests) ou `app/build/reports/androidTests/` (instrumented).
2. Corriger le bug ou le test cassé.
3. Relancer `./tests.sh` (ou `.\tests.ps1`) jusqu'à succès complet.

## Notes
- `./tests.sh` = gate local/pre-push (unit tests + build debug).
- `./tests_device.sh` = tests instrumentés (`connectedAndroidTest`, appareil/émulateur requis).
- Alias Gradle disponible:
```bash
./gradlew :app:ci
```

## Quarantaine temporaire (QuickPlaylistsScreenTest)
Si un device flaky casse le run global, tu peux isoler temporairement:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.patrick.lrcreader.ui.QuickPlaylistsScreenTest
```

Puis lancer ce test seul:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.patrick.lrcreader.ui.QuickPlaylistsScreenTest
```
