# Review-Plan: feature/OnTheFlyParser

**Branch:** `feature/OnTheFlyParser`
**Erstellt am:** 2026-09-13
**Scope:** Alle Commits von `main` bis `feature/OnTheFlyParser` (20 Commits, 35 Dateien, +6871 Zeilen)

---

## 1. Überblick über den Branch

### 1.1 Was wurde implementiert

Der Branch implementiert zwei parallel existierende Loesungen fuer die Typzuordnung zwischen
`EditTree`/`EditNode`-Hierarchie und `JsonModelDescriptor`:

| System | Zweck | Trigger | Testabdeckung |
|--------|-------|---------|---------------|
| **On-the-Fly Parser** (Leichtgewichtig) | Inkrementelles, asynchrones Typ-Parsen waehrend des Editierens | Automatisch bei Node-Aenderungen via Listener + Thread-Pool | `TypeParserServiceNGTest` (10 Tests, 1 fail) |
| **Validatoren-Welt** (Schwergewichtig) | Umfassende Modellvalidierung mit Diagnostics | Expliziter Aufruf (vor Speichern/Export) | **Keine Tests** |

### 1.2 Commits im Ueberblick

| Phase | Commit | Beschreibung |
|-------|--------|-------------|
| Phase 1 | `03781ac` | On-the-Fly Parser Infrastruktur (ParseState, EditStatus, Felder) |
| Phase 4 | `32f6523` | Threading-Initialisierung mit Thread-Pool |
| Phase 5 | `14aa6a9` | Listener fuer automatische Parse-Queue Aktualisierung |
| Phase 6 | `5ad0c37` | parseNode() Kernlogik in TypeParserService |
| Phase 7 | `70855fd` | Integration - Auto-Start/Stop mit EditTree |
| Test | `7d9f5e0` | TypeParserServiceNGTest |
| Fix | `4b6570a` | RejectedExecutionException in Queue Processor |
| Test-Fix | `edee2ea`-`111c893` | Diverse Test-Anpassungen fuer Race Conditions |
| Feature | `9ab3e52` | Auto-assign ConfigRoot type to root node |
| Optimierung | `6447dfb` | getTypesContainingField nutzt fieldMap |
| Optimierung | `2ab1f33` | EditNodeProperty.tryAssignType nutzt fieldMap |

### 1.3 Geaenderte Dateien (Kern)

```
src/de/jare/jsoncasted/editor/core/
├── EditNode.java                  (+38)   Interface: tryAssignType default, deprecated constants
├── EditNodeAbstract.java          (+135)  parseState, lastParsedHash, computeHash, editTree-Ref
├── EditNodeObject.java            (+47)   tryAssignType, setName trigger
├── EditNodeProperty.java          (+119)  tryAssignType mit fieldMap, setName/Type trigger
├── EditNodePropertyArr.java       (+56)   tryAssignType (Array-Variante, ohne fieldMap)
├── EditStatus.java                (+209)  Enum: STATELESS/OKAY/WARNING/ERROR
├── ParseState.java                (+236)  Enum: NONE/EDITED/PENDING/DONE
├── EditTree.java                  (+387)  parseQueue, pendingNodes, parserService, assignTypes*
├── TypeParserListener.java        (+82)   Interface fuer Aenderungs-Notifications
├── TypeParserService.java         (+575)  Kernlogik: Thread-Pool, parseNode, tryInferParentTypes
└── validation/                    (+1393) 12 Klassen: Validierungs-Framework (ohne Tests)
```

---

## 2. Gefundene Qualitaetsprobleme (Kategorisiert)

### Kategorie A: Thread-Safety (Kritisch)

| ID | Datei | Problem | Schwere |
|----|-------|---------|---------|
| A1 | `EditNodeAbstract.java:54-55` | `parseState` und `lastParsedHash` sind plain fields, nicht `volatile`. Werden von UI-Thread und Parser-Thread gelesen/geschrieben. Konzept fordert `volatile` oder `AtomicReference`. | Hoch |
| A2 | `EditNodeObject.java:191` | `setJsonType()` ist plain assignment ohne `volatile`/`synchronized`. Parser-Thread und UI-Thread koennen gleichzeitig schreiben. Konzept fordert `synchronized` oder `volatile`. | Hoch |
| A3 | `EditNodeProperty.java:186` | `setJsonField()`同样: plain assignment, nicht thread-safe. | Hoch |
| A4 | `EditNodeProperty.java:194` / `EditNodeObject.java:145` | `setEditStatus`/`setEditMessage` ebenfalls plain fields ohne Synchronisation. | Mittel |
| A5 | `EditTree.java:962-971` | `addToParseQueue`: Race zwischen `pendingNodes.add()` und `parseQueue.add()` + `setParseState(PENDING)`. Wenn Parser-Thread `removeFromPending` zwischen diesen Schritten aufruft, kann Node verloren gehen. | Hoch |
| A6 | `EditTree.java:771-784` | `setJsonModelDescriptor` ruft `assignTypesFromModel()` synchron (DFS auf UI-Thread) UND startet danach den asynchronen Parser. Doppeltausfuehrung der Typzuordnung. | Mittel |
| A7 | `EditNodeProperty.java:102-105` | `setName()` ruft `tree.assignTypesForNode(this)` synchron UND `notifyNodeNameChanged` (asynchron via Queue). Doppeltausfuehrung. | Mittel |

### Kategorie B: Logikfehler & Race Conditions (Kritisch)

| ID | Datei | Problem | Schwere |
|----|-------|---------|---------|
| B1 | `EditTree.java:814-825` | `markAllNodesAsEdited` markiert alle Nodes als EDITED, aber nur `addToParseQueue(getRoot())` fuegt den Root zur Queue hinzu. Kinder werden nie einzeln gequeued. Der Parser verarbeitet nur den Root; Kinder bleiben im Zustand EDITED. | Hoch |
| B2 | `TypeParserService.java:333-335` | `tryInferParentTypes` setzt den Parent-Typ und re-queued den Parent, aber **nicht** die Kinder. Kinder mit unveraendertem Hash werden beim naechsten Parse uebersprungen (Hash prueft nur name/value/childCount, nicht parentType). Felder der Kinder werden nicht neu aufgeloest. | Hoch |
| B3 | `EditNodeAbstract.java:194-210` | `computeHash()` inkludiert NICHT `getJsonType()`/`getJsonField()` (vom Konzept gefordert). Wenn sich der Parent-Typ aendert, aendert sich der Hash des Kindes nicht → Parse wird uebersprungen. | Hoch |
| B4 | `TypeParserServiceNGTest.java:316` | `testParseStateTransitions` schlaegt fehl: "State should be DONE but found [EDITED]". Node bleibt in EDITED stecken, weil er bereits in `pendingNodes` ist und `addToParseQueue` ihn nicht re-queued. | Hoch |
| B5 | `TypeParserService.java:313-324` | Hardcodierter Typname `"ConfigRoot"` im Parser. Funktioniert nur fuer `JsonConfigDefinition`, bricht bei anderen Modellen. | Mittel |

### Kategorie C: Code-Duplikation & Inkonsistenz

| ID | Datei | Problem | Schwere |
|----|-------|---------|---------|
| C1 | `EditNodeProperty.java` vs `EditNodePropertyArr.java` | `tryAssignType` ist nahezu identisch in beiden Klassen (~50 Zeilen). `EditNodeProperty` nutzt fieldMap, `EditNodePropertyArr` nicht. Inkonsistente Optimierung. | Mittel |
| C2 | `EditNodeProperty.java` vs `EditNodePropertyArr.java` vs `EditNodeObject.java` | Drei `tryAssignType`-Implementierungen mit paralleler Struktur (null-Checks, setEditStatus, setEditMessage). Koennen in gemeinsame Hilfsmethode extrahiert werden. | Niedrig |
| C3 | `EditStatus.java` vs `ParseState.java` | Beide Enums sind strukturell identisch (generierter Code). `get(String)` und `getByName(String)` ueberlappen sich (getByName ist Superset). Redundante API. | Niedrig |

### Kategorie D: Validatoren-Framework (Schwergewichtig)

| ID | Datei | Problem | Schwere |
|----|-------|---------|---------|
| D1 | Alle 12 Validatoren-Klassen | **Keine Tests** fuer das gesamte Validierungs-Framework (~1393 Zeilen). | Hoch |
| D2 | `EditValidationRunner.java:117-140` | `validateWithoutModel` / `validateSubtreeWithoutModel` sind Stubs, die leere Ergebnisse zurueckgeben. Javadoc behauptet "limited validation". | Mittel |
| D3 | `EditTreeModelConsistencyValidator.java:51,63-69` | Tote Variable `orphanedTypeNames` (nie befuellt), leere Schleife ueber `getTypesKeys()`. | Mittel |
| D4 | `EditTreeModelConsistencyValidator.java:78,85` | Doppelte Diagnostics fuer dieselbe Bedingung: `editnode.tree.orphaned.types` UND `editnode.tree.inconsistent` auf demselben Node. | Niedrig |
| D5 | `EditNodeTypeHierarchyValidator.java:43` | Parameter `visitedTypes` wird durch Rekursion gereicht, aber nie gelesen/geschrieben. Toter Parameter. | Niedrig |
| D6 | `EditNodeTypeHierarchyValidator.java:138,145,152` | `finally { visited.remove(typeName) }` ist redundant, da rekursive Aufrufe mit Kopien von `visited` arbeiten. Tote Logik. | Niedrig |
| D7 | Alle Tree-Validatoren | Kein Cycle-Guard fuer rekursive Node-Traversierung. Zyklischer Node-Graph → `StackOverflowError`. | Mittel |
| D8 | `ValidationContext.java` | `getPathString()` gibt Pfad in umgekehrter Reihenfolge zurueck (Kinder vor Eltern), widerspricht Javadoc ("root to current node"). | Mittel |
| D9 | `ValidationResult.java` | `getDiagnosticsByNode` gibt mutable `HashMap` zurueck (andere Getter geben immutable Listen). Inkonsistenter Immutability-Contract. | Niedrig |
| D10 | `ValidationResult.java` | `getInfoCount()` fehlt (Asymmetrie zu `getErrorCount`/`getWarningCount`). | Niedrig |

### Kategorie E: Debug-Code & Cleanup

| ID | Datei | Problem | Schwere |
|----|-------|---------|---------|
| E1 | `JsonTreeConverter.java:181` | `System.out.println("1 +++++++++++++++ " + jsonNode)` in Produktionscode. Debug-Output pro JSON-Node. | Mittel |
| E2 | `TypeParserService.java:239,257` | `System.err.println` fuer Fehler-Logging. Sollte proper Logging verwenden. | Niedrig |
| E3 | `TypeParserService.java:323` | `System.out.println("Assigned root type (ConfigRoot) to root node")` in Produktionscode. | Niedrig |
| E4 | `EditNodePropertyFieldValidator.java:111-116` | Typ-Kompatibilitaetspruefung ist auskommentiert ("Future: check if propertyType matches"). | Niedrig |

### Kategorie F: Dokumentation vs. Implementation

| ID | Datei | Problem | Schwere |
|----|-------|---------|---------|
| F1 | `CONCEPT_OnTheFlyParser.md:751` | Konzept zeigt `getFieldPerceptive(fieldName)` in `EditNodeProperty.tryAssignType`. Methode existiert nicht in `JsonTypeDescriptor`. Implementation verwendet nur `getField()`. | Niedrig |
| F2 | `CONCEPT_OnTheFlyParser.md:663` | Konzept zeigt `model.getTypesContainingField(node.getName())` als Methode auf `JsonModelDescriptor`. Implementation hat diese Methode in `TypeParserService` als private Methode. | Niedrig |
| F3 | `CONCEPT_OnTheFlyParser.md:264-265` | Konzept fordert `getJsonType()` und `getJsonField()` in `computeHash()`. Implementation inkludiert diese nicht. | Hoch (siehe B3) |
| F4 | `EditNode.java:198-203` | `tryAssignType` default Javadoc ist deutsch, Rest ist englisch. Inkonsistent. | Niedrig |
| F5 | `EditNode.java:202-207` | `tryAssignType` default gibt immer `true`/OKAY zurueck. Wenn eine Subklasse vergisst zu ueberschreiben, wird Typ-Zuordnung stillschweigend als erfolgreich gemeldet. | Mittel |

### Kategorie G: Test-Qualitaet

| ID | Datei | Problem | Schwere |
|----|-------|---------|---------|
| G1 | `TypeParserServiceNGTest.java` | 1 von 10 Tests schlaegt fehl (`testParseStateTransitions`). Test wurde mehrfach angepasst (4 Commits), anstatt die Race Condition zu fixen. | Hoch |
| G2 | `TypeParserServiceNGTest.java:147-148` | Test akzeptiert `DONE || EDITED` als gueltigen Endzustand. Versteckt das eigentliche Problem. | Mittel |
| G3 | Test-Suite | Tests haben hohe Timeouts (5-10 Sekunden) und sind nicht deterministisch (Async-Parser). Flake-Risk. | Mittel |
| G4 | Test-Suite | Keine Tests fuer `EditNodeObject.tryAssignType`, `EditNodePropertyArr.tryAssignType`, `EditStatus`, `ParseState`, `EditNodeAbstract.computeHash`. | Hoch |

---

## 3. Arbeitspakete (Schritt fuer Schritt)

Jedes Arbeitspaket ist unabhaengig durchfuehrbar, ausser where Abhaengigkeiten explizit genannt sind.
Reihenfolge entspricht Prioritaet (kritische Fehler zuerst).

---

### AP-1: Thread-Safety der Node-Felder herstellen
**Prioritaet:** Kritisch
**Abhaengigkeit:** Keine
**Betoffene Dateien:**
- `EditNodeAbstract.java` (parseState, lastParsedHash, editStatus, editMessage)
- `EditNodeObject.java` (jsonType)
- `EditNodeProperty.java` (jsonField)

**Aufgaben:**
1. `parseState` als `volatile` deklarieren oder `AtomicReference<ParseState>` verwenden.
2. `lastParsedHash` als `volatile` deklarieren (oder `AtomicLong`).
3. `editStatus` und `editMessage` als `volatile` deklarieren.
4. `jsonType` in `EditNodeObject` als `volatile` deklarieren oder `synchronized` Block.
5. `jsonField` in `EditNodeProperty` als `volatile` deklarieren oder `synchronized` Block.
6. `getEditTree()`/`setEditTree()` als `volatile` deklarieren.
7. Kompilieren, bestehende Tests ausfuehren, sicherstellen dass keine Regression.

**Akzeptanzkriterium:**
- Alle Felder, die von UI-Thread und Parser-Thread geteilt werden, sind `volatile` oder synchronized.
- Tests kompilieren und laufen (mindestens die 9 bisher gruenen).

---

### AP-2: Race Condition in addToParseQueue / removeFromPending beheben
**Prioritaet:** Kritisch
**Abhaengigkeit:** AP-1 (volatile parseState)
**Betroffene Dateien:**
- `EditTree.java` (addToParseQueue, removeFromPending)

**Aufgaben:**
1. `addToParseQueue` und `removeFromPending` als `synchronized` oder mit atomarem Compare-and-Set implementieren, so dass ein Node nicht verloren gehen kann.
2. Alternative: `addToParseQueue` re-queued einen Node immer (auch wenn bereits pending), indem er zuerst entfernt und dann neu hinzugefuegt wird. Oder: Flag zuruecksetzen statt zu skippen.
3. Test schreiben, der folgende Sequenz deterministisch prueft:
   - Node A in Queue
   - Parser pollt A (entfernt aus Queue, aber removeFromPending noch nicht aufgerufen)
   - UI-Thread ruft addToParseQueue(A) → muss A re-queue (oder sicherstellen dass A geparst wird)
   - Parser beendet A, ruft removeFromPending
   - A muss mit aktuellem Hash geparst werden

**Akzeptanzkriterium:**
- `testParseStateTransitions` laeuft deterministisch gruen (10/10).
- Neuer Race-Condition-Test laeuft gruen.

---

### AP-3: computeHash() um jsonType/jsonField erweitern
**Prioritaet:** Kritisch
**Abhaengigkeit:** AP-1 (volatile Felder)
**Betroffene Dateien:**
- `EditNodeAbstract.java` (computeHash)

**Aufgaben:**
1. `computeHash()` um `getJsonType()` (fuer EditNodeObject) und `getJsonField()` (fuer EditNodeProperty) erweitern, wie im Konzept (Phase 3) beschrieben.
2. Dafuer muss `EditNodeAbstract` either: (a) `getJsonType()`/`getJsonField()` als abstrakte Methoden deklarieren (mit null Default), oder (b) instanceof-Check in `computeHash()`.
3. Variante (b) ist einfacher: in `computeHash()` pruefen ob `this instanceof EditNodeObject` → `getJsonType()` inkludieren; wenn `this instanceof EditNodeProperty` → `getJsonField()` inkludieren.
4. Test schreiben: Parent-Typ wird gesetzt → Kind-Hash aendert sich → Kind wird neu geparst.

**Akzeptanzkriterium:**
- `computeHash()` inkludiert jsonType/jsonField.
- Neuer Test verifiziert, dass Parent-Typ-Aenderung Child-Reparsing ausloest.

---

### AP-4: Kinder-Reparsing bei Parent-Typ-Aenderung sicherstellen
**Prioritaet:** Kritisch
**Abhaengigkeit:** AP-3 (computeHash)
**Betroffene Dateien:**
- `TypeParserService.java` (tryInferParentTypes, parseNode)

**Aufgaben:**
1. In `tryInferParentTypes`: Wenn Parent-Typ erfolgreich abgeleitet wurde, alle Kinder des Parent zur Parse-Queue hinzufuegen (nicht nur den Parent selbst).
2. In `parseNode`: Wenn ein EditNodeObject erfolgreich geparst wurde und sich der Typ geaendert hat, alle Kinder re-queue.
3. `markAllNodesAsEdited`: Statt nur `addToParseQueue(getRoot())`, alle Nodes zur Queue hinzufuegen (BFS/DFS). Oder: nur Root queue, aber Parser muss Kinder explizit verarbeiten.
4. Alternative fuer (3): `triggerFullReparse` queue alle Nodes, nicht nur Root.

**Akzeptanzkriterium:**
- Nach `setJsonModelDescriptor` sind alle Nodes nach Parsen im Zustand DONE.
- `testParseStateInitialization` prueft, dass alle (nicht nur Root) DONE sind.

---

### AP-5: Doppeltausfuehrung synchron/asynchron entfernen
**Prioritaet:** Hoch
**Abhaengigkeit:** AP-1, AP-2
**Betroffene Dateien:**
- `EditTree.java` (setJsonModelDescriptor, assignTypesFromModel)
- `EditNodeProperty.java` (setName, setType)
- `EditNodeObject.java` (setName)

**Aufgaben:**
1. `setJsonModelDescriptor`: Synchronen `assignTypesFromModel()`-Aufruf entfernen. Nur asynchronen Parser starten und Root zur Queue hinzufuegen.
2. `setName()` in EditNodeProperty/EditNodeObject: Synchronen `assignTypesForNode(this)`-Aufruf entfernen. Nur `notifyNodeNameChanged` (→ asynchron via Queue).
3. `setType()` in EditNodeProperty: Synchronen `assignTypesForNode(this)` entfernen. Nur async.
4. Pruefen, ob `assignTypesFromModel` und `assignTypesForNode` noch anderweitig verwendet werden. Wenn nicht, entfernen oder als deprecated markieren.
5. Achtung: `assignTypesForNode` wird evtl. von `MoveNodeCommand` verwendet. Pruefen und ggf. beibehalten oder ebenfalls auf async umstellen.

**Akzeptanzkriterium:**
- `tryAssignType` wird nur noch vom Parser-Thread aufgerufen (nicht mehr vom UI-Thread).
- UI-Thread fuegt nur noch Nodes zur Queue hinzu.
- Alle Tests gruen.

---

### AP-6: Hardcodierten "ConfigRoot" entfernen
**Prioritaet:** Hoch
**Abhaengigkeit:** Keine
**Betroffene Dateien:**
- `TypeParserService.java:313-324`

**Aufgaben:**
1. Statt `model.getType("ConfigRoot")` eine generische Loesung finden:
   - Option A: Root-Typ aus dem Modell ableiten (z.B. `model.getType(rootNode.getName())` mit Fallback).
   - Option B: `JsonModelDescriptor` um `getRootTypeName()` erweitern (Aenderung an jsonCasted-Bibliothek).
   - Option C: Konfigurierbarer Root-Typ-Name am `TypeParserService` oder `EditTree`.
2. `System.out.println` in Zeile 323 entfernen.
3. Test hinzufuegen, der Root-Typ-Zuweisung mit einem anderen Modellnamen verifiziert.

**Akzeptanzkriterium:**
- Keine hardcodierten Typnamen mehr in `TypeParserService`.
- Root-Typ-Zuweisung funktioniert fuer beliebige Modelle.

---

### AP-7: EditNodePropertyArr.tryAssignType an fieldMap anpassen
**Prioritaet:** Mittel
**Abhaengigkeit:** Keine
**Betroffene Dateien:**
- `EditNodePropertyArr.java`

**Aufgaben:**
1. `tryAssignType` in `EditNodePropertyArr` um die gleichen fieldMap-Optimierungen ergaenzen wie in `EditNodeProperty` (Existenzpruefung, Mehrdeutigkeitspruefung, kontextreiche Fehlermeldungen).
2. Oder: Gemeinsame Hilfsmethode extrahieren (`resolveFieldInParent`) und von beiden Klassen verwenden.
3. Kompilieren, Tests ausfuehren.

**Akzeptanzkriterium:**
- `EditNodePropertyArr` und `EditNodeProperty` haben konsistente Fehlermeldungen.
- Code-Duplikation reduziert.

---

### AP-8: tryAssignType gemeinsame Logik extrahieren
**Prioritaet:** Niedrig
**Abhaengigkeit:** AP-7
**Betroffene Dateien:**
- `EditNodeProperty.java`
- `EditNodePropertyArr.java`
- Neue ggf. Hilfsklasse oder Methode in `EditNodeAbstract`

**Aufgaben:**
1. Gemeinsame Null-Checks und Status-Setzungen in Hilfsmethode auslagern.
2. Nur die typspezifische Logik (Array-Check fuer PropertyArr) in der Subklasse belassen.
3. Template-Method-Pattern oder Helper-Methode verwenden.

**Akzeptanzkriterium:**
- Reduzierte Code-Duplikation.
- Alle Tests gruen.

---

### AP-9: Debug-Output entfernen
**Prioritaet:** Mittel
**Abhaengigkeit:** Keine
**Betroffene Dateien:**
- `JsonTreeConverter.java:181`
- `TypeParserService.java:239,257,323`

**Aufgaben:**
1. `System.out.println` in `JsonTreeConverter.java:181` entfernen (oder hinter Debug-Flag).
2. `System.err.println` in `TypeParserService.java` durch proper Logging ersetzen (`java.util.logging.Logger`) oder entfernen.
3. `System.out.println("Assigned root type...")` in Zeile 323 entfernen.
4. Pruefen, ob bereits ein Logging-Framework im Projekt verwendet wird (grep nach `Logger` in jsonCasted). Wenn ja, verwenden.

**Akzeptanzkriterium:**
- Kein `System.out`/`System.err` mehr in Produktionscode (oder nur hinter Debug-Flag).

---

### AP-10: Validatoren-Framework: Tests erstellen
**Prioritaet:** Hoch
**Abhaengigkeit:** Keine (unabhaengig vom Parser)
**Betroffene Dateien:**
- Neue Datei: `test/.../validation/EditValidationRunnerNGTest.java`
- Neue Datei: `test/.../validation/EditNodeObjectTypeValidatorNGTest.java`
- Neue Datei: `test/.../validation/EditNodePropertyFieldValidatorNGTest.java`
- Neue Datei: `test/.../validation/EditTreeModelConsistencyValidatorNGTest.java`
- Neue Datei: `test/.../validation/EditNodeRequiredFieldsValidatorNGTest.java`
- Neue Datei: `test/.../validation/EditNodeTypeHierarchyValidatorNGTest.java`

**Aufgaben:**
1. Test-Setup erstellen: kleines Mock-Modell mit 2-3 Typen und Feldern.
2. Pro Validator: Test fuer positiven Fall (gueltiger Node) und negativen Fall (ungueltiger Node).
3. `EditValidationRunner` Test: validate() mit gueltigem und ungueltigem Baum.
4. `ValidationResult` Test: add, hasErrors, getDiagnosticsByNode, getBySeverity.
5. `ValidationContext` Test: pushPath/popPath/getPathString (Reihenfolge pruefen).
6. Test fuer zyklische Hierarchie (sollte ERROR liefern, nicht StackOverflow).

**Akzeptanzkriterium:**
- Mindestens 80% Code-Abdeckung fuer Validatoren-Package.
- Alle Tests deterministisch gruen.

---

### AP-11: Validatoren: Tote Code entfernen
**Prioritaet:** Niedrig
**Abhaengigkeit:** AP-10 (Tests als Sicherheitsnetz)
**Betroffene Dateien:**
- `EditTreeModelConsistencyValidator.java`
- `EditNodeTypeHierarchyValidator.java`
- `EditValidationRunner.java`

**Aufgaben:**
1. `orphanedTypeNames` Variable in `EditTreeModelConsistencyValidator` entfernen.
2. Leere Schleife ueber `getTypesKeys()` entfernen.
3. Doppelte Diagnostic (`editnode.tree.inconsistent` nach `editnode.tree.orphaned.types`) zusammenfuehren zu einer.
4. `visitedTypes` Parameter in `EditNodeTypeHierarchyValidator.validateObjectHierarchy` entfernen.
5. Redundantes `finally { visited.remove() }` entfernen.
6. `validateWithoutModel`/`validateSubtreeWithoutModel`: entweder implementieren oder als `UnsupportedOperationException` markieren und Javadoc anpassen.
7. `getInfoCount()` in `ValidationResult` ergaenzen (falls gewuenscht) oder `hasInfo` entfernen.

**Akzeptanzkriterium:**
- Keine toten Variablen/Parameter.
- Javadoc stimmt mit Implementierung ueberein.

---

### AP-12: Validatoren: Cycle-Guard fuer rekursive Traversierung
**Prioritaet:** Mittel
**Abhaengigkeit:** AP-10
**Betroffene Dateien:**
- `EditTreeModelConsistencyValidator.java`
- `EditNodeRequiredFieldsValidator.java`
- `EditNodeTypeHierarchyValidator.java`
- `EditValidationRunner.java` (validateNodesRecursive)

**Aufgaben:**
1. `Set<EditNodeAbstract>` visited-Set in rekursiven Traversierungen verwenden.
2. Bei Zyklus: Diagnostic mit Severity WARNING ("Cyclic node reference detected") statt StackOverflow.
3. Test mit zyklischem EditTree (falls moeglich zu konstruieren) erstellen.

**Akzeptanzkriterium:**
- Zyklischer Node-Graph fuehrt zu Diagnostic, nicht zu StackOverflow.
- Test verifiziert.

---

### AP-13: ValidationContext.getPathString Reihenfolge korrigieren
**Prioritaet:** Niedrig
**Abhaengigkeit:** AP-10
**Betroffene Dateien:**
- `ValidationContext.java`

**Aufgaben:**
1. `getPathString()` so anpassen, dass Pfad von Root zu aktuellem Node ausgegeben wird (nicht umgekehrt).
2. Entweder `ArrayDeque` als Stack anders verwenden (addLast/removeLast) oder Iterator umkehren.
3. Test dafuer erstellen (in AP-10 enthalten oder separat).

**Akzeptanzkriterium:**
- `getPathString()` liefert "root -> child -> grandchild", nicht "grandchild -> child -> root".

---

### AP-14: Test testParseStateTransitions deterministisch machen
**Prioritaet:** Hoch
**Abhaengigkeit:** AP-2 (Race Condition behoben)
**Betroffene Dateien:**
- `TypeParserServiceNGTest.java`

**Aufgaben:**
1. Nach AP-2 sollte der Test gruen sein. Wenn nicht: Test-Logik anpassen.
2. `waitForNodeParsing` mit Hoeherem Timeout oder besser: Parser-Service mit synchroner Ausfuehrung im Test (Test-spezifischer Parser ohne Thread-Pool).
3. Assertion von `DONE || EDITED` zurueck auf striktes `DONE` aendern.
4. Flaky-Tests durch deterministische Warte-Logik ersetzen (z.B. CountDownLatch oder CompletableFuture).

**Akzeptanzkriterium:**
- `testParseStateTransitions` laeuft 10x hintereinander deterministisch gruen.
- Assertion ist strikt `DONE`.

---

### AP-15: Test-Abdeckung fuer Kern-Klassen erweitern
**Prioritaet:** Hoch
**Abhaengigkeit:** AP-1 bis AP-5 (erst nach Fixes sinnvoll)
**Betroffene Dateien:**
- Neue Tests fuer:
  - `EditNodeObject.tryAssignType` (Typ gefunden / nicht gefunden / perceptiv)
  - `EditNodeProperty.tryAssignType` (Feld gefunden / nicht gefunden / Mehrdeutig / unbekannt)
  - `EditNodePropertyArr.tryAssignType` (Array-Feld / kein Array-Feld)
  - `EditNodeAbstract.computeHash` (Wert aendert sich → Hash aendert sich)
  - `EditStatus` / `ParseState` (Enum-Konvertierung, get/getByName)
  - `EditTree.addToParseQueue` / `removeFromPending` (Deduplizierung)

**Aufgaben:**
1. Pro Methode: Test fuer Happy-Path und Error-Path.
2. Bei tryAssignType: Test mit null-Descriptor, null-Name, leerem Namen.
3. Bei computeHash: Test, dass Aenderung von Name/Value/childCount den Hash aendert.

**Akzeptanzkriterium:**
- Code-Abdeckung fuer `core`-Package >= 70%.
- Alle Tests deterministisch.

---

### AP-16: Dokumentation aktualisieren
**Prioritaet:** Niedrig
**Abhaengigkeit:** Nach allen Code-Aenderungen
**Betroffene Dateien:**
- `CONCEPT_OnTheFlyParser.md`
- `IMPLEMENTIERUNG_Typzuordnung_Leicht.md`
- `IMPLEMENTIERUNG_Typzuordnung_Validatoren.md`
- `README.md`

**Aufgaben:**
1. Konzept-Dokument an tatsaechliche Implementation anpassen:
   - `getFieldPerceptive` Referenz entfernen (existiert nicht).
   - `model.getTypesContainingField()` → `TypeParserService.getTypesContainingField()`.
   - `computeHash` Bestandteile aktualisieren.
   - Thread-Safety-Markierungen aktualisieren.
2. `EditNodeProperty.tryAssignType` Javadoc auf Englisch uebersetzen (Konsistenz).
3. `README.md` ggf. um Asynchron-Parser- und Validatoren-Sektion ergaenzen.
4. TODO-Liste in `CONCEPT_OnTheFlyParser.md` abhaken, was erledigt ist.

**Akzeptanzkriterium:**
- Konzept-Dokument widerspricht nicht der Implementation.
- Alle Javadocs sind in einer Sprache (Englisch bevorzugt).

---

## 4. Prioritaeten-Matrix

| Arbeitspaket | Prioritaet | Aufwand (schaetzt) | Abhaengigkeit |
|-------------|-----------|--------------------|---------------|
| AP-1: Thread-Safety | Kritisch | Mittel (4h) | Keine |
| AP-2: Race Condition addToParseQueue | Kritisch | Gross (6h) | AP-1 |
| AP-3: computeHash erweitern | Kritisch | Klein (2h) | AP-1 |
| AP-4: Kinder-Reparsing | Kritisch | Mittel (4h) | AP-3 |
| AP-5: Sync/Async-Duplikation | Hoch | Mittel (3h) | AP-1, AP-2 |
| AP-6: ConfigRoot hardcodiert | Hoch | Klein (2h) | Keine |
| AP-7: PropertyArr fieldMap | Mittel | Klein (1h) | Keine |
| AP-8: tryAssignType DRY | Niedrig | Mittel (3h) | AP-7 |
| AP-9: Debug-Output | Mittel | Klein (1h) | Keine |
| AP-10: Validatoren-Tests | Hoch | Gross (8h) | Keine |
| AP-11: Validatoren Dead Code | Niedrig | Klein (1h) | AP-10 |
| AP-12: Validatoren Cycle-Guard | Mittel | Klein (2h) | AP-10 |
| AP-13: getPathString | Niedrig | Klein (1h) | AP-10 |
| AP-14: Test deterministisch | Hoch | Mittel (3h) | AP-2 |
| AP-15: Test-Abdeckung Kern | Hoch | Gross (6h) | AP-1 bis AP-5 |
| AP-16: Doku aktualisieren | Niedrig | Mittel (3h) | Alle |

**Schaetzung Gesamt:** ~50 Stunden

---

## 5. Empfohlene Reihenfolge

```
Phase 1 (Kritisch):     AP-1 → AP-2 → AP-3 → AP-4
Phase 2 (Hoch):         AP-6, AP-5, AP-14 (parallel)
Phase 3 (Hoch):         AP-10, AP-15 (parallel)
Phase 4 (Mittel):       AP-7, AP-9, AP-12
Phase 5 (Niedrig):      AP-8, AP-11, AP-13, AP-16
```

---

## 6. Offene Fragen

1. **Soll die Validatoren-Welt beibehalten werden?** Sie ist vollstaendig implementiert, aber ohne Tests und ohne Integration in Commands. Falls sie nicht verwendet wird, sollte sie entfernt oder als experimentell markiert werden.
2. **Soll `assignTypesFromModel()` / `assignTypesForNode()` entfernt werden?** Diese synchronen Methoden sind nach der Async-Parser-Implementierung potenziell ueberfluessig (ausser fuer `MoveNodeCommand`). Klären, ob MoveNodeCommand synchron oder asynchron arbeiten soll.
3. **Ist `getFieldPerceptive` auf `JsonTypeDescriptor` geplant?** Das Konzept referenziert es, aber es existiert nicht. Soll es in jsonCasted ergaenzt werden?
4. **Soll `JsonModelDescriptor` einen `getRootTypeName()` anbieten?** Damit liesse sich der hardcodierte "ConfigRoot" ersetzen.
5. **Test-Infrastruktur:** Gibt es eine Moeglichkeit, den Parser im Test synchron laufen zu lassen (ohne Thread-Pool)? Das wuerde flaky Tests eliminieren.
