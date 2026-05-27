# Törnberechnung – Android

Törnberechnung ist eine Android-Anwendung für fortgeschrittene nautische Routenplanung und Navigation, die speziell für die anspruchsvollen Bedingungen des Wattenmeers und der Ostfriesischen Inseln entwickelt wurde.

Die App automatisiert den komplexen Prozess der maritimen Routenplanung durch die Auswertung von Echtzeit-Tiefendaten der Seegebiete, Wetterbedingungen und Gezeiteninformationen, um sichere, reine Wasserrouten zwischen Häfen bereitzustellen.

## Funktionen

- **Interaktive Karte:** Angetrieben von MapLibre GL für ein nahtloses Karten-Erlebnis im nautischen Stil.
- **Hafenauswahl:** Einfache Auswahl von Start- und Zielhäfen im gesamten Wattenmeer (Deutschland, Niederlande, Dänemark).
- **Automatisches See-Routing:** Verwendet einen maßgeschneiderten, gitterbasierten A*-Routing-Algorithmus, um sicherzustellen, dass Routen strikt befahrbaren Wasserwegen folgen und Landmassen meiden.
- **Sicherheitsbewertung:** Bietet automatische GO/NO-GO-Sicherheitsempfehlungen basierend auf dem Tiefgang, Echtzeit-Tiefendaten und aktuellen Wetterbedingungen.
- **Gezeiten-Simulation:** Integriert Gezeitendaten, um die sichersten Abfahrtszeiten und Zeitfenster für die Überfahrt zu berechnen.
- **Offline-First:** Speichert wichtige nautische Daten lokal mit der Room Database, um die Funktionalität auch ohne aktive Internetverbindung auf See zu gewährleisten.
- **Daten-Authentizität:** Bezieht zuverlässige nautische und geografische Daten direkt von den WFS-Endpunkten des BSH (Bundesamt für Seeschifffahrt und Hydrographie).

##  Tech Stack

- **Sprache:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architektur:** MVVM (Model-View-ViewModel)
- **Karten-Rendering:** MapLibre GL Android SDK
- **Netzwerk:** Retrofit2, OkHttp, Kotlinx Coroutines
- **Lokaler Speicher:** Room Database
- **Dokumentation:** KDoc (generiert mit Dokka)
- **Code-Stil:** ktlint

## Erste Schritte

### Voraussetzungen
- Android Studio (neueste stabile Version empfohlen)
- JDK 17 (oder neuer)
- Android SDK (API 36)

### Installation
1. Repository klonen:
   ```bash
   git clone https://github.com/everybodydaniel/Toernberechnung-Android.git
   ```
2. Das Projekt in Android Studio öffnen.
3. Das Projekt mit den Gradle-Dateien synchronisieren.
4. Die App auf einem Emulator oder einem physischen Gerät kompilieren und ausführen.

##  CI/CD Pipeline

Dieses Projekt nutzt GitHub Actions für kontinuierliche Integration und automatisierte Aufgaben:

- **Android CI (`android-ci.yml`):** Baut das Projekt automatisch und führt Unit-Tests bei jedem Push und Pull Request auf die Branches `master` und `develop` aus.
- **Style Check (`style-check.yml`):** Führt `ktlint` aus, um die Konsistenz des Code-Stils sicherzustellen.
- **Dokka Pages (`dokka_pages.yml`):** Generiert automatisch eine HTML-Dokumentation mit Dokka und stellt sie auf GitHub Pages bereit.

Coverage:
https://everybodydaniel.github.io/Toernberechnung-Android/coverage/
## Lizenz

Dieses Projekt ist unter der **MIT-Lizenz** lizenziert.
