$ErrorActionPreference = "Stop"

Write-Host "[SPL] Running Labo unit tests..."
& .\gradlew.bat :app:testLaboDebugUnitTest

Write-Host "[SPL] Running Concert unit tests..."
& .\gradlew.bat :app:testConcertDebugUnitTest

Write-Host "[SPL] Building debug APKs..."
& .\gradlew.bat assembleDebug

Write-Host "[SPL] ALL TESTS PASSED"
