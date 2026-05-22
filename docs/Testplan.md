# Testplan für Törnberechnung App

## 1. Projektzusammenfassung
Die "Törnberechnung" Android-App unterstützt Segler und Bootsführer bei der Planung ihrer Törns, insbesondere in Tidengewässern. Sie bietet Funktionen wie Gezeitenberechnung, Wettervorhersagen, Logbuch-Einträge, Crew-Verwaltung und Checklisten. Ziel dieses Testplans ist es, die systematische Qualitätssicherung der App durch automatisierte und manuelle Tests sicherzustellen.

## 2. Testumgebung
- **Entwicklungsumgebung:** JDK 17/21, Android Studio
- **Android SDK:** Compile SDK 36, Min SDK 26, Target SDK 36
- **Test-Geräte:** Emulator Pixel 7 (API 34) für UI/Instrumented Tests
- **Frameworks & Bibliotheken:**
  - JUnit 4
  - MockK (Mocking)
  - Kotest Assertions (Auswertung/Matchings)
  - Turbine (Testen von StateFlows)
  - JaCoCo (Testabdeckung)
  - AndroidX Core Testing
  - Room Testing

## 3. Testkategorien
Das Projekt verwendet folgende Testarten:
1. **Unit-Tests (Lokal):** Tests für Business-Logik, ViewModels, Mapping-Funktionen und DTOs. Führen schnell und ohne Android-Gerät aus.
2. **Integrationstests (Instrumentiert):** Tests der Room Database (DAOs), die ein echtes oder emuliertes Android-System erfordern.
3. **GUI-Tests (Instrumentiert):** Jetpack Compose UI-Tests zur Validierung von Bildschirmen und Nutzer-Interaktionen.
4. **Manuelle Tests:** Explorative und szenario-basierte Tests für das Gesamtsystem (z.B. Offline-Fähigkeit, Hardware-Sensorik, UX/UI).

## 4. Testmatrix und Granularität

| Testbereich | Beschreibung / Klassen | Typ | Methode |
|---|---|---|---|
| **Business-Logik** | `RuleOfTwelfths`, `DecisionLogic` | Unit | Automatisch |
| **Model & Mappings** | `MappingExtensions`, Entity/DTO-Konvertierung | Unit | Automatisch |
| **ViewModels** | `TideViewModel`, `RoutePlanningViewModel` | Unit | Automatisch |
| **Data/DAO** | `TideDao`, `LogbookDao`, `CrewMemberDao` | Integration | Automatisch (Device) |
| **Netzwerk/DTO** | `WeatherDto`, Deserialisierung | Unit | Automatisch |
| **User Interface** | Compose Screens (`MapScreen`, etc.) | GUI/Manuell | Auto/Manuell |
| **Systemtest** | App-Start, Navigation, Offline | System | Manuell |

### Testgranularitäten
- **Methoden-Tests:** Testen einzelner Berechnungen (z.B. `calculateUKC`).
- **Klassen-Tests:** Validierung der Zustände einer ViewModel-Klasse über Flow-Emissionen.
- **Modul-Tests:** Interaktion zwischen Repository und lokaler Datenbank (Room).
- **System-Tests:** Applikationsweite Überprüfung von Use-Cases (Manuelle_Testfaelle.md).

## 5. Testabdeckung (Coverage)
Die Testabdeckung wird mittels **JaCoCo** berechnet.
- Ausgeschlossen sind automatisch generierte Klassen (`R.class`, `BuildConfig`, generierter Hilt/Dagger/Room Code, UI-Previews).
- Ziel-Abdeckung für Business-Logik (`com.example.trnberechnung.logic.*`): > 80%
- Ziel-Abdeckung für ViewModels: > 70%

## 6. Verantwortlichkeiten
- **Entwickler:** Schreiben von Unit- und Integrationstests (TDD-Ansatz bei Bugfixes).
- **QA/Tester:** Durchführung der manuellen Testfälle vor jedem Release, Überprüfung der Coverage-Reports.

## 7. Zeitplan und Ausführung
- Automatische Unit-Tests werden kontinuierlich (lokal) oder bei jedem PR in einer CI/CD-Pipeline ausgeführt.
- Manuelle Systemtests werden als "Regressionstest" vor Haupt-Releases (Major/Minor) durchgeführt.

## 8. Risiken und Abhängigkeiten
- **Externe APIs (BSH / DWD):** Die APIs könnten ausfallen oder ihr Datenformat ändern. DTO-Tests und Mocking isolieren diese Risiken für die Business-Logik.
- **Hardware-Abhängigkeiten:** Standortdienste (GPS) und MapLibre-Rendering benötigen teilweise reale Geräte für realistische Tests. Diese sind schwer automatisiert abdeckbar und erfordern manuelles Testen.
