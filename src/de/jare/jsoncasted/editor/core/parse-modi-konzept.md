# Parse-Modi-Konzept im Editor-Kern (jsonCasted_edit / WoodJsonJack)

## Ueberblick

Der Editor kennt drei Parse-Modi je Baum (`ParseMode` in `de.jare.jsoncasted.editor.core`,
Ablage im `EditTree`, Auswahl pro Baum über die ComboBox am resourceLabel):

- **WITHOUT_SEMANTICS** ("without semantics") - der Parser schläft. Kein Modell,
  keine Typbindung, keine Statusfarben jenseits der Struktur. Edits verändern
  nur den Baum.
- **SOFT_PARSE** ("soft parse") - nebenläufiges Parsing über die Queue: Edits
  werden gesetzt, der Hintergrundparser typisiert später. Toleranz: unbekannte
  Felder sind ERROR, freie Annotationen sind WARNING, Interface-Typen sind
  "annähernd richtig" (geerbter Cast). Das ist der **unterstützte Modus**.
- **HARD_PARSE** ("hard parse") - jede Änderung wird **synchron** geparst, bevor
  der aufrufende Edit zurückkehrt. Der Baum ist immer in einem geprüften,
  modellgueltigen Zustand. **Noch nicht unterstützt** - dieses Konzept
  beschreibt, was dazugehört.

Der Parser schläft ohne Deskriptor (`EditTree#parserAsleep`): `WITHOUT_SEMANTICS`
oder fehlendes Modell lassen jede Parse-Aktivität ruhen. Ohne Modell ist die
ComboBox disabled und zeigt "without semantics".

## Umsetzungsstand

| Thema | Status |
|---|---|
| Modi, Ablage pro Baum, Combo am resourceLabel | implementiert |
| Parser schläft ohne Modell / bei WITHOUT | implementiert |
| Eintrittsregel Hard (Baum muss gruen sein) | implementiert |
| Harte Menuestruktur (Untermenüs je Knotenart) | offen (Abschnitt 4) |
| Synchron-Regel inkl. Propagations-Bug | offen (Abschnitt 5) |
| Bruch-Dialog | offen (Abschnitt 6) |
| Wert-/Namensbearbeitung im Hard-Modus | offen (Abschnitt 7) |

## 1. Modi und Ablage

- Der Modus ist eine Eigenschaft **eines Baums** (`EditTree#parseMode`), nicht
  einer Ressource oder des Fensters. Jeder Editor-Tab hat seine eigene Auswahl.
- Ohne Deskriptor erzwingt der Baum `WITHOUT_SEMANTICS`: Die ComboBox ist
  disabled, der Modus wird beim Deskriptor-Load automatisch gesetzt.
- Der Soft-Modus ist der Arbeitsmodus für Prototyping und Nacharbeit; der
  Hard-Modus ist der "bau-dir-nur-Richtiges"-Modus für das disziplinierte
  Ausfüllen eines Modells.

## 2. Eintrittsregel Hard (implementiert)

Die Auswahl von "hard parse" prüft zuerst, ob **jeder** Knoten des Baums den
Edit-Status OKAY trägt.

- Ist ein Status nicht okay, erscheint eine Meldung (englisch):
  > "The tree has parse problems. The N parse problems are listed in the search
  > results. Please fix the parse errors first."

  und der Modus fällt auf "soft parse" zurück. Die betroffenen Knoten werden
  als "parse problems" in die Suche-Ergebnis-Liste publiziert.
- Ohne Modell ist Hard nicht wählbar (Box disabled).

## 3. Leitprinzip des Hard-Modus

**Verhindern statt reparieren.** Der Soft-Modus lässt jede Aktion zu und
färbt danach. Der Hard-Modus muss die Aktion, die den modellgueltigen
Zustand brechen würde, **vorab** unterbinden oder anbieten, dass der User
bewusst in den Soft-Modus wechselt. Ein halbgültiger Zustand (Aktion steht,
Fehler kommt später) ist im Hard-Modus kein zulässiges Ergebnis.

Daraus folgen drei Verteidigungslinien:

1. **Menuestruktur**: das Menü bietet nur an, was das Modell am gewählten
   Knoten erlaubt (Abschnitt 4). Was nicht angeboten wird, kann nicht
   geklickt werden.
2. **Enablement**: was die Struktur hergibt, aber das Modell verbietet, ist
   grau (`check()`/`CommandAvailability` fuettert das Enablement).
3. **Bruch-Dialog**: falls doch etwas bricht (Abschnitt 6).

## 4. Harte Menuestruktur (offen)

Das Popup ist im Soft-Modus ein Struktur-Werkzeug ("baue irgendeinen Knoten,
der Parser sortiert es später"). Im Hard-Modus ist es ein Modell-Werkzeug:
jeder angebotene Eintrag führt garantiert in einen gueltigen Zustand.
"Add child" wird zum Untermenü der zulässigen Typen, vorhandene ausgeblendet.

### 4.1 Objekt (aufgeloester Typ)

- Untermenü **fehlende deklarierte Felder** des Typs, gegliedert nach der Art
  des Felds: Skalar, 0:1-Referenz, Collection (ARRAY/LIST), Map, Enum.
  Bereits vorhandene Felder werden nicht angeboten (ein Feld existiert genau
  einmal).
- Untermenü **fehlende deklarierte Annotationen** (Objektebene): Deklarationen
  des Typs inklusive transienter; reine Wildcards (`doc:*`) decken den Typ
  selbst. Freie/tolerierte Annotationen sind ein Soft-Konzept und werden im
  Hard-Modus nicht angeboten.
- Das anonyme "new" existiert nicht: ohne Modell-Typ kein neuer Knoten.

### 4.2 0:1-Parameter (Referenzfeld)

- Genau **ein** Kind erlaubt, vom deklarierten Typ. Besetztes 0:1 bietet kein
  Add an (offene Entscheidung 3: alternativ eine atomare Replace-Aktion, die
  das alte Kind entfernt und das neue setzt).
- Ist das Feld ein Interface, bietet das Untermenü die **konkreten
  Implementierungen** an; der "annähernd richtige" Interface-Cast des
  Soft-Modus entfällt, der Cast wird sofort hart gesetzt.
- Enum-Feld: kein Baum-Kind, sondern Wert-Auswahl (ComboBox der Literale).

### 4.3 Array / Liste

- Der Feldknoten existiert genau einmal; "Add" heißt "Zeile mit Elementtyp".
  Das Feld selbst wird nicht erneut angeboten, **Zeilen beliebig oft** - die
  "vorhandene ausgeschlossen"-Regel gilt nur für das Feld, nicht für
  Sammlungselemente.
- Bei Interface-Elementtyp: Untermenü der konkreten Implementierungen
  (derselbe Array-Items-Fall, den der Soft-Parser nachhindert, hier als
  Angebot vorab).

### 4.4 Map

- `mappingAllFields`: Keys sind frei. "Add" bietet einen neuen Eintrag an;
  der Key-Name ist frei benennbar (ggf. Validierung gegen den Key-Typ), der
  Wert kommt aus dem Mapping-Elementtyp.
- Deklarierte Map: das Untermenü bietet die deklarierten Keys an.

### 4.5 Annotationen

- Nur deklarierte Annotationen (inklusive transienter) sind anbietbar.
  Feld-Ebene: nur die Deklarationen, die für dieses Feld passen (explizite
  Feld-Deklaration plus Wildcard-Patterns, `*`-Miniregex ausgewertet).
- Der Composite-Anchor ist strukturell wie im Annotationen-Konzept
  (Entscheidung 7 dort): unter dem Feldknoten ist die Annotation Composite,
  am Objekt einfach.
- Anonyme Annotationen ("irgendein `@x`") sind im Hard-Modus nicht
  anbietbar.

### 4.6 Abfrage-Schicht

Zwischen Popup und `JsonModelDescriptor` entsteht eine kleine Abfrage-Schicht
(Arbeitstitel `permissibleChildren(node)` / `permissibleValues(node)`), die je
Knotenart die zulässigen Einträge liefert. Der Deskriptor weiss das alles
bereits (Feldkarte, CollectionType, contains-Hierarchie, Wildcard-
Annotationen); es fehlt nur die harte Menue-Schicht obendrauf. Die gleiche
Schicht fuettert das Enablement für Paste/Cut/Delete/Rename.

## 5. Synchron-Regel (offen)

- Im Hard-Modus parst jede Änderung sofort (`parseNow`), keine Queue, kein
  Timer. Der aufrufende Edit kehrt erst zurück, wenn der Baum wieder
  vollständig geparst ist.
- Es gibt keinen sichtbaren "zu prüfen"-Zustand: die Statuspalette
  beschraenkt sich auf OKAY (und ERROR als Ergebnis eines externen Bruchs,
  siehe Abschnitt 6). WARNING ist ein Soft-Konzept.
- **Voraussetzung**: der geparkte HARD-Bug in
  `EditTree#notifyChildAdded` (Kommentar "TODO hard parse") muss zuerst
  gefixt werden: das Catchall-Auto-Typing des Baums laeuft der
  Elementtyp-Propagation davon, sodass ein hinzugefügtes Element als ERROR
  "does not match" endet, statt den Feld-Elementtyp zu uebernehmen. Ohne
  diesen Fix ist jede Hard-Menue-Aktion sofort kaputt.

## 6. Bruch-Regel und Bruch-Dialog (offen)

### 6.1 Erste Verteidigung: verhindern

Brechende Aktionen duerfen im Hard-Modus gar nicht erst ausfuehrbar sein
(Menuestruktur + Enablement aus `check()`). Der Dialog ist die Ausnahme-
Absicherung, nicht der Normalfall.

### 6.2 Der Dialog

Faellt eine Aktion durch die Pruefung, obwohl sie menue-technisch erreichbar
ist (vom Check nicht gedeckter Fall, oder der User besteht auf eine Struktur,
die das Modell nicht hergibt), erscheint:

> **Titel**: "Hard parse state"
> **Text**: "This action would break the hard parse state of the tree.
> Switch to soft parse?"
> **Buttons**: [Switch to soft]  [Cancel]

- **[Cancel]**: die Aktion wird **nicht ausgefuehrt** (bzw. als eine
  Transaktion zurueckgerollt); der Baum bleibt gueltig-hard. Nichts ist
  passiert.
- **[Switch to soft]**: der Modus kippt auf SOFT_PARSE, die Aktion wird
  ausgefuehrt bzw. bleibt stehen; Fehler zeigen sich wie gewohnt als
  WARNING/ERROR mit Problems-Liste.

Der User kann also immer abbrechen - es entsteht nie ein Zustand, den er
nicht gewollt hat.

### 6.3 Pruefpunkt: vor oder nach der Ausführung

Empfehlung: **Pruefung vor der Ausführung.** Der Dialog erscheint, bevor die
Aktion den Baum anfasst; erst die Antwort entscheidet, ob ausgefuehrt oder
verworfen wird. Damit ist der Nachher-Fall (Zustand schon kaputt, Rollback
noetig) auf zwei Restfaelle beschraenkt:

- **Modellwechsel waehrend der Sitzung** (Deskriptor neu geladen, vorher
  gueltiger Baum ist jetzt ungueltig).
- **Externer Bruch** (Datei-Reparse, Git-Stand, Fremd-Edit).

Fuer diese Restfaelle gilt (offene Entscheidung 1): der Dialog bietet den
Wechsel an; **Cancel haelt den Hard-Modus fest und friert ihn ein** - alle
Aktionen sind grau, bis die Probleme behoben sind (dann wieder frei) oder der
User doch auf Soft wechselt. Ein Zurueckrollen gibt es hier nicht, weil kein
auslösender Edit existiert.

## 7. Wert- und Namensbearbeitung im Hard-Modus (offen)

- **Skalare Werte**: typgetreue Sofort-Validierung - LONG parst oder die
  Eingabe wird abgewiesen; BOOLEAN und Enum sind ComboBoxen, keine freien
  Textfelder.
- **Rename auf Feldern**: im Soft-Modus ist ein Rename eine Art-Aenderung per
  Hintertuer (neuer Name -> anderes Feld -> anderer Typ rueckt nach). Im
  Hard-Modus entweder verboten (Löschen + korrektes Add aus dem Untermenü)
  oder als hartes Re-Type gegen die Deklaration mit sofortiger Umwandlung
  (offene Entscheidung 2). Map-Keys bleiben frei benennbar.
- **Delete**: Felder, die das Modell als Pflicht deklariert (offene
  Entscheidung 4: wo lebt "nicht loeschbar" im Modell - required-Flag,
  Nicht-Null-Faehigkeit), sind nicht loeschbar oder nur mit bewusster
  "Regel brechen"-Bestaetigung, die den Bruch-Dialog auslöst.
  Annotationen sterben mit ihrem Feld (siehe Annotationen-Konzept,
  Entscheidung 7) - unkritisch, weil der Add-Weg sie wieder anbietet.
- **Paste/Cut**: nur typkompatible Kandidaten sind zulässig (inklusive
  Interface-contains-Pruefung); `canPasteTo` und das Enablement fragen die
  Abfrage-Schicht aus Abschnitt 4.6.

## 8. Offene Entscheidungen

1. **Externer Bruch**: Dialog nur mit Wechsel-Option; Cancel friert den
   harten Modus (alle Aktionen grau) ein - oder erlaubt das Weiterarbeiten
   mit sichtbaren ERRORs? Empfehlung: einfrieren.
2. **Rename auf Feldern**: verbieten oder hartes Re-Type gegen die
   Deklaration? Empfehlung: verbieten (kleinere Loesung, kein zweiter
   Typisierungs-Pfad).
3. **0:1-Replace**: kein Add bei besetztem Feld oder atomare
   Replace-Aktion? Empfehlung: erst kein Add, Replace spaeter.
4. **Pflichtfeld-Begriff**: wo deklariert das Modell "nicht loeschbar"?
   (required-Flag existiert bisher nicht in `JsonFieldDescriptor` -
   gegebenenfalls im Modell-Konzept nachziehen.)
5. **Undo im Hard-Modus**: jede Undo-Stufe muss ebenfalls gueltig-hard sein.
   Da Befehle vor der Ausfuehrung geprueft werden, ist das strukturell
   gegeben - zu verifizieren, sobald die Synchron-Regel steht.
6. **Harte Menues auch im Hauptmenue** ("Edit"-Reiter) oder nur im Baum-Popup?
   Empfehlung: nur Popup, bis der Bedarf kommt.

## 9. Umsetzungsplan

- **Schritt 0** (Voraussetzung): HARD-Propagations-Bug in
  `EditTree#notifyChildAdded` fixen - ohne ihn ist jede harte Aktion sofort
  kaputt.
- **Schritt 1**: Abfrage-Schicht `permissibleChildren` / `permissibleValues`
  am `JsonModelDescriptor`.
- **Schritt 2**: Popup-Untermenüs aus den Abfragen (Abschnitt 4).
- **Schritt 3**: check-basiertes Enablement + Bruch-Dialog (Abschnitt 6).
- **Schritt 4**: Wert-Combos und typgetreue Validierung (Abschnitt 7).

Bis auf den Umsetzungsstand in der Tabelle oben ist der Soft-Modus der
unterstützte Modus; der Hard-Modus bleibt bis zum Abschluss von Schritt 0-4
bewusst "TODO-markiert" und nicht waehlbar.
