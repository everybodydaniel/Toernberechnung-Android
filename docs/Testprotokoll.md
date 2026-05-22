# Testprotokoll - Törnberechnung

**Datum:** 2026-05-22
**Tester:** Automatisiertes System / QA
**Build-Version:** 1.0 (Debug)
**Testumgebung:** Lokaler Build (JDK 17) & Emulator (API 34)

---

## 1. Unit-Test-Ergebnisse

Alle automatisierten Unit-Tests befinden sich unter `app/src/test/java/com/example/trnberechnung/`.

| Testklasse | Testfall | Ergebnis | Bemerkung |
|---|---|---|---|
| **RuleOfTwelfthsTest** | calculateWaterLevel target before start | ⏳ Ausstehend | |
| | calculateWaterLevel target after end | ⏳ Ausstehend | |
| | calculateWaterLevel at midpoint | ⏳ Ausstehend | |
| | calculateWaterLevel at 1_6 phase | ⏳ Ausstehend | |
| | calculateWaterLevel zero duration | ⏳ Ausstehend | |
| | calculateUKC positive | ⏳ Ausstehend | |
| | calculateUKC negative or low | ⏳ Ausstehend | |
| | evaluateGoNoGo with clear margin | ⏳ Ausstehend | |
| | evaluateGoNoGo below margin | ⏳ Ausstehend | |
| | evaluateGoNoGo exact margin boundary | ⏳ Ausstehend | |
| **DecisionLogicTest** | null weather returns Keine Wetterdaten | ⏳ Ausstehend | |
| | good conditions returns GUTE BEDINGUNGEN | ⏳ Ausstehend | |
| | storm windSpeed 120 returns NO-GO | ⏳ Ausstehend | |
| | thunderstorm returns NO-GO | ⏳ Ausstehend | |
| | warning conditions due to fog returns EINGESCHRÄNKT | ⏳ Ausstehend | |
| | evaluate null returns expected triple | ⏳ Ausstehend | |
| | evaluate good conditions returns isOk true... | ⏳ Ausstehend | |
| | evaluate storm returns isOk false | ⏳ Ausstehend | |
| **MappingExtensionsTest** | HighLowWaterDto toModel with forecastValue | ⏳ Ausstehend | |
| | HighLowWaterDto toModel with latOffset | ⏳ Ausstehend | |
| | HighLowWaterDto toModel null forecastValue | ⏳ Ausstehend | |
| | WaterLevelItemDto toModel converts correctly | ⏳ Ausstehend | |
| | TideStationData toEntity preserves fields | ⏳ Ausstehend | |
| | TideEntity toModel preserves fields | ⏳ Ausstehend | |
| **ModelEntityTest** | LogbookEntry construction and equality | ⏳ Ausstehend | |
| | CrewMember construction and equality | ⏳ Ausstehend | |
| | ChecklistItem construction and enum values | ⏳ Ausstehend | |
| | TideEvent construction | ⏳ Ausstehend | |
| **TideViewModelTest** | saveCrew calls repository insertCrew | ⏳ Ausstehend | |
| | deleteCrew calls repository deleteCrew | ⏳ Ausstehend | |
| | saveLog calls repository insertLog | ⏳ Ausstehend | |
| | deleteLog calls repository deleteLog | ⏳ Ausstehend | |
| | deleteAllLogs calls repository deleteAllLogs | ⏳ Ausstehend | |
| **WeatherDtoTest** | Deserialize full WeatherDto JSON | ⏳ Ausstehend | |
| | Deserialize minimal WeatherDto JSON | ⏳ Ausstehend | |
| | Deserialize BrightSkyResponseDto | ⏳ Ausstehend | |
| | Deserialize ForecastResponseDto | ⏳ Ausstehend | |

---

## 2. Integrationstests (Room DAOs)
*Diese Tests werden auf einem Android-Gerät/Emulator ausgeführt.*

| Bereich | Status | Bemerkung |
|---|---|---|
| Room Database (LogbookDao, CrewDao, etc.) | ⏳ Ausstehend | Noch nicht implementiert/ausgeführt. |

---

## 3. Manuelle Tests
Siehe Datei `Manuelle_Testfaelle.md` für detaillierte Schritte.

| Test-ID | Titel | Status | Tester |
|---|---|---|---|
| MT-01 | App-Start und Erstanzeige | ⏳ Ausstehend | |
| MT-02 | Gezeitenstationen laden | ⏳ Ausstehend | |
| MT-03 | Wetter-Anzeige | ⏳ Ausstehend | |
| MT-04 | Logbuch-Eintrag erstellen | ⏳ Ausstehend | |
| MT-05 | Crew-Verwaltung | ⏳ Ausstehend | |
| MT-06 | Routenplanung | ⏳ Ausstehend | |
| MT-07 | Offline-Verhalten | ⏳ Ausstehend | |
| MT-08 | Geräterotation | ⏳ Ausstehend | |
| MT-09 | Go/No-Go Entscheidung | ⏳ Ausstehend | |
| MT-10 | Checkliste | ⏳ Ausstehend | |

---

## 4. Coverage-Zusammenfassung (JaCoCo)

| Package | Instruction % | Branch % |
|---|---|---|
| `com.example.trnberechnung.logic` | ⏳ N/A | ⏳ N/A |
| `com.example.trnberechnung.viewmodel` | ⏳ N/A | ⏳ N/A |
| `com.example.trnberechnung.model` | ⏳ N/A | ⏳ N/A |
| `com.example.trnberechnung.dto` | ⏳ N/A | ⏳ N/A |
| **Total Project** | **⏳ N/A** | **⏳ N/A** |

## Gesamtbewertung
**Status:** ⏳ Ausstehend
**Fazit:** Testausführung steht noch aus. Testabdeckung muss verifiziert werden.
