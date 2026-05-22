# Manuelle Testfälle

In diesem Dokument sind die manuellen Testszenarien definiert, die vor jedem Release der App validiert werden sollten.

### MT-01: App-Start und Erstanzeige
- **Kategorie:** UI / System
- **Priorität:** Hoch
- **Vorbedingung:** App ist frisch installiert, Internetverbindung aktiv.
- **Schritte:**
  1. App starten.
  2. Warten, bis das initiale Layout gerendert wurde.
  3. Prüfen, ob die MapLibre-Karte geladen wird.
  4. Die Bottom-Navigation auf Sichtbarkeit der 5 Tabs überprüfen.
- **Erwartetes Ergebnis:** Karte wird angezeigt, alle 5 Nav-Tabs (Karte, Wetter, Logbuch, Crew, Mehr) sind sichtbar.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-02: Gezeitenstationen laden
- **Kategorie:** Netzwerk / UI
- **Priorität:** Hoch
- **Vorbedingung:** Internetverbindung aktiv.
- **Schritte:**
  1. Den Tab "Karte" oder "Gezeiten" öffnen.
  2. Den Refresh-Button drücken, um BSH-Daten zu laden.
  3. Eine Gezeitenstation auf der Karte (Marker) oder in der Liste auswählen.
- **Erwartetes Ergebnis:** Stationen werden angezeigt, Gezeitendaten (Hoch-/Niedrigwasser) für die ausgewählte Station werden geladen und visualisiert.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-03: Wetter-Anzeige
- **Kategorie:** Netzwerk / UI
- **Priorität:** Mittel
- **Vorbedingung:** Gezeitenstation ausgewählt.
- **Schritte:**
  1. Wetter-Tab (WeatherOverlayScreen) öffnen.
  2. Standort/Station auswählen.
- **Erwartetes Ergebnis:** Aktuelle Temperatur, Windstärke (Bft), Windrichtung und Wetter-Prognose für die nächsten Tage werden korrekt angezeigt.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-04: Logbuch-Eintrag erstellen
- **Kategorie:** Datenbank / UI
- **Priorität:** Hoch
- **Vorbedingung:** Keine.
- **Schritte:**
  1. Logbuch-Tab öffnen.
  2. "Neuer Eintrag" / "+" klicken.
  3. Felder ausfüllen (Route, Distanz, Dauer, Details).
  4. Speichern klicken.
- **Erwartetes Ergebnis:** Der neue Eintrag ist in der Liste sichtbar und die Daten entsprechen der Eingabe.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-05: Crew-Verwaltung
- **Kategorie:** Datenbank / UI
- **Priorität:** Mittel
- **Vorbedingung:** Keine.
- **Schritte:**
  1. Crew-Tab öffnen.
  2. "Mitglied hinzufügen" klicken.
  3. Daten (Name, Rang, Notfallkontakt) eingeben.
  4. Speichern klicken.
- **Erwartetes Ergebnis:** Das Crewmitglied ist in der Liste sichtbar. Der "An Bord"-Status kann gewechselt werden.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-06: Routenplanung
- **Kategorie:** Logik / UI
- **Priorität:** Hoch
- **Vorbedingung:** Internetverbindung aktiv.
- **Schritte:**
  1. Karte öffnen.
  2. Startpunkt und Zielpunkt setzen.
  3. Route berechnen lassen.
- **Erwartetes Ergebnis:** Die Route wird auf der Karte als Polylinie gezeichnet, die Distanz in Seemeilen (nm) wird korrekt angezeigt.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-07: Offline-Verhalten
- **Kategorie:** Edge-Case / UX
- **Priorität:** Hoch
- **Vorbedingung:** WLAN und Mobile Daten am Gerät sind **deaktiviert**.
- **Schritte:**
  1. App starten.
  2. Versuchen, Gezeiten oder Wetter zu aktualisieren.
  3. Logbuch und Crew-Tab öffnen.
- **Erwartetes Ergebnis:** Lokale Daten (Logbuch, Crew, Checklisten) werden fehlerfrei geladen. Bei API-Calls erscheint eine verständliche Fehlermeldung, die App stürzt nicht ab.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-08: Geräterotation
- **Kategorie:** UI
- **Priorität:** Niedrig
- **Vorbedingung:** Auto-Rotate am Gerät aktiviert.
- **Schritte:**
  1. App öffnen und in verschiedenen Tabs (Karte, Logbuch) das Gerät ins Querformat (Landscape) drehen.
- **Erwartetes Ergebnis:** Keine Datenverluste, das Layout passt sich an den neuen Bildschirm an, keine Abstürze.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-09: Go/No-Go Entscheidung
- **Kategorie:** Logik / UI
- **Priorität:** Hoch
- **Vorbedingung:** Wetterdaten und Gezeitendaten verfügbar.
- **Schritte:**
  1. Station mit extremen Wetterdaten (falls möglich simulieren) oder normalen Daten auswählen.
  2. Bewertung im UI prüfen.
- **Erwartetes Ergebnis:** Es wird eine farbcodierte Bewertung (Grün/Gelb/Rot bzw. GUTE BEDINGUNGEN / EINGESCHRÄNKT / NO-GO) angezeigt, die der `DecisionLogic` entspricht.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet

### MT-10: Checkliste
- **Kategorie:** Datenbank / UI
- **Priorität:** Mittel
- **Vorbedingung:** Keine.
- **Schritte:**
  1. Checkliste für einen Trip öffnen.
  2. Mehrere Checkbox-Items abhaken.
  3. App schließen und wieder öffnen.
  4. Checkliste erneut aufrufen.
- **Erwartetes Ergebnis:** Der Checklisten-Status (abgehakte Items) bleibt erhalten, da er in der Datenbank gespeichert wird.
- **Tatsächliches Ergebnis:** 
- **Status:** ⏳ Nicht getestet
