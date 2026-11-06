# Implementierung der Leichtgewichtigen On-the-Fly Typzuordnung

## Übersicht

Diese Implementierung fügt eine automatische Typzuordnung zwischen EditTree/EditNode-Hierarchie und JsonModelDescriptor hinzu. Die Zuordnung erfolgt on-the-fly bei verschiedenen Trigger-Punkten.

## Wichtige Aktualisierung: EditStatus Enum

**Neu in dieser Version:** Die Edit-Status-Struktur wurde von String-Konstanten auf ein typsicheres Enum (`EditStatus.java`) migriert, analog zu `EdCursor.java`. Dies bietet:
- **Typensicherheit** anstelle von String-Vergleichen
- **Bessere IDE-Unterstützung** mit Autocomplete und Typprüfung
- **Erweiterte Utility-Methoden** wie `isError()`, `isWarning()`, etc.
- **Rückwärtskompatibilität** durch `@Deprecated` String-Konstanten in `EditNode.java`

## Implementierte Komponenten

### 1. EditNode Interface (`EditNode.java`)
- **Hinzugefügt**: Import für `JsonModelDescriptor`
- **Hinzugefügt**: `tryAssignType(JsonModelDescriptor descriptor)` Methode mit Standardimplementierung
  - Setzt `EditStatus.OKAY` Status und löscht Fehlermeldungen
  - Kann von Subklassen überschrieben werden
- **Geändert**: `getEditStatus()` Return-Typ von `Object` zu `EditStatus`
- **Geändert**: `getEditMessage()` Return-Typ von `Object` zu `String`
- **Veraltet**: String-Konstanten `EDIT_STATELESS`, `EDIT_OKAY`, `EDIT_WARNING`, `EDIT_ERROR` als `@Deprecated` markiert

### 2. EditNodeAbstract (`EditNodeAbstract.java`)
- **Hinzugefügt**: `editTree` Feld (schache Referenz zum EditTree)
- **Hinzugefügt**: `getEditTree()` und `setEditTree(EditTree)` Methoden
- **Modifiziert**: `addChildPhase1()` - setzt Tree-Referenz für neue Kinder
- **Modifiziert**: `removeChild()` - setzt Tree-Referenz auf null für entfernte Kinder

### 3. EditNodeObject (`EditNodeObject.java`)
- **Hinzugefügt**: Import für `JsonModelDescriptor`
- **Implementiert**: `tryAssignType(JsonModelDescriptor descriptor)`
  - Sucht nach `JsonTypeDescriptor` über `descriptor.getType(name)` oder `getTypePerceptive(name)`
  - Bei Erfolg: setzt `jsonType`, Status `EditStatus.OKAY`
  - Bei Misserfolg: Status `EditStatus.WARNING` mit Fehlermeldung
- **Modifiziert**: `setName()` - triggert `assignTypesForNode(this)` falls Tree verfügbar

### 4. EditNodeProperty (`EditNodeProperty.java`)
- **Hinzugefügt**: Importe für `JsonModelDescriptor` und `JsonTypeDescriptor`
- **Implementiert**: `tryAssignType(JsonModelDescriptor descriptor)`
  - Prüft, ob Parent ein `EditNodeObject` mit `jsonType` ist
  - Sucht nach `JsonFieldDescriptor` über `parentType.getField(fieldName)`
  - Bei Erfolg: setzt `jsonField`, Status `EditStatus.OKAY`
  - Bei Misserfolg: Status `EditStatus.ERROR` mit Fehlermeldung
- **Modifiziert**: `setName()` - triggert `assignTypesForNode(this)` falls Tree verfügbar
- **Modifiziert**: `setType()` - triggert `assignTypesForNode(this)` falls Tree verfügbar

### 5. EditNodePropertyArr (`EditNodePropertyArr.java`)
- **Hinzugefügt**: Importe für `JsonModelDescriptor` und `JsonTypeDescriptor`
- **Implementiert**: `tryAssignType(JsonModelDescriptor descriptor)`
  - Ähnlich wie EditNodeProperty, aber mit zusätzlicher Prüfung für Array-Typen
  - Prüft: `foundField.isAsArray() || foundField.isAsListOrArray()`
  - Bei Erfolg: setzt `jsonField`, Status `EditStatus.OKAY`
  - Bei nicht-Array-Feld: Status `EditStatus.ERROR` mit spezifischer Fehlermeldung

### 6. EditTree (`EditTree.java`)
- **Hinzugefügt**: `assignTypesFromModel()` - durchläuft gesamten Baum (DFS) und ruft `tryAssignType()` auf
- **Hinzugefügt**: `assignTypesRecursive()` - rekursive Hilfsmethode für Typzuordnung
- **Hinzugefügt**: `assignTypesForNode(EditNodeAbstract node)` - ordnet Typen für einen Node und seine Kinder zu
- **Modifiziert**: `setJsonModelDescriptor()` - ruft `assignTypesFromModel()` nach dem Setzen auf
- **Modifiziert**: `EditTree(EditNodeAbstract, EditTimes)` - setzt Tree-Referenz für Root-Node
- **Modifiziert**: `addNode()` - ruft `tryAssignType()` für neue Nodes auf
- **Modifiziert**: `addNewChild()` - ruft `tryAssignType()` für neue Nodes auf
- **Modifiziert**: `addChild()` - ruft `tryAssignType()` für neue Nodes auf

### 7. MoveNodeCommand (`MoveNodeCommand.java`)
- **Modifiziert**: `doExecute()` - ruft `assignTypesForNode()` für alle verschobenen Nodes auf
- **Modifiziert**: `doUndo()` - ruft `assignTypesForNode()` für alle verschobenen Nodes auf

### 8. PasteFromStashCommand
- **Keine Änderungen nötig**: Die Typzuordnung erfolgt automatisch über `addNode()` in `AbstractEditCommand.doAdd()`

## Trigger-Punkte

Die Typzuordnung wird automatisch bei folgenden Ereignissen ausgelöst:

| Situation | Auslöser | Methode |
|-----------|----------|---------|
| Neuer Node wird erstellt | `EditTree.addNode()`, `EditTree.addNewChild()`, `EditTree.addChild()` | `tryAssignType()` auf neuem Node |
| Node wird verschoben | `MoveNodeCommand.execute()`, `MoveNodeCommand.doUndo()` | `assignTypesForNode()` auf verschobenem Node |
| Modelldeskriptor wird gesetzt | `EditTree.setJsonModelDescriptor()` | `assignTypesFromModel()` auf Root |
| Node-Attribut ändert sich | `EditNode.setName()`, `EditNodeProperty.setType()` | `assignTypesForNode()` auf betroffenem Node |
| Baum importiert | `PasteFromStashCommand` über `addNode()` | `tryAssignType()` auf neuen Nodes |

## Status-Markierung

### EditStatus Enum (Neu)

**Pfad:** `de.jare.jsoncasted.editor.core.EditStatus`

Analog zu `EdCursor.java` implementiertes Enum mit:
- **Enum-Konstanten:** `STATELESS`, `OKAY`, `WARNING`, `ERROR`
- **Integer-Konstanten:** `STATELESS_VALUE=0`, `OKAY_VALUE=1`, `WARNING_VALUE=2`, `ERROR_VALUE=3`
- **Utility-Methoden:** `get(String literal)`, `getByName(String name)`, `get(int value)`, `VALUES`
- **Instanzmethoden:** `getValue()`, `getName()`, `getLiteral()`, `toString()`
- **Hilfsmethoden:** `isError()`, `isWarning()`, `isOkay()`, `isStateless()`

Die Status-Konstanten werden verwendet:

- `EditStatus.STATELESS` – Neutraler Zustand (kein Modell gesetzt)
- `EditStatus.OKAY` – ✅ Typ/Field erfolgreich zugewiesen
- `EditStatus.WARNING` – ⚠️ Typ/Field nicht gefunden, aber nicht kritisch
- `EditStatus.ERROR` – ❌ Kritisches Problem (z.B. Field existiert nicht im Parent-Typ)

### Migration von String-Konstanten

**Veraltet:** Die alten String-Konstanten `EDIT_STATELESS`, `EDIT_OKAY`, `EDIT_WARNING`, `EDIT_ERROR` in `EditNode.java` sind als `@Deprecated` markiert und verweisen auf das neue `EditStatus` Enum. Bestehender Code funktioniert weiter, sollte aber migriert werden.

## Spezifische Logik pro Node-Typ

### EditNodeObject
1. Holt Node-Name über `getName()`
2. Sucht Typ über `descriptor.getType(name)` oder `descriptor.getTypePerceptive(name)`
3. Bei Erfolg: `setJsonType(foundType)`, Status `EditStatus.OKAY`
4. Bei Misserfolg: Status `EditStatus.WARNING`, Meldung: `"Type '{name}' not found in model"`

### EditNodeProperty
1. Prüft, ob Parent ein `EditNodeObject` ist
2. Prüft, ob Parent einen `jsonType` hat
3. Holt Parent-Typ und Feldname
4. Sucht Feld über `parentType.getField(fieldName)`
5. Bei Erfolg: `setJsonField(foundField)`, Status `EditStatus.OKAY`
6. Bei Misserfolg: Status `EditStatus.ERROR`, Meldung: `"Field '{name}' not found in type '{typeName}'"`

### EditNodePropertyArr
1. Gleiche Logik wie EditNodeProperty
2. Zusätzliche Prüfung: `foundField.isAsArray() || foundField.isAsListOrArray()`
3. Bei nicht-Array-Feld: Status `EditStatus.ERROR`, Meldung: `"Field '{name}' in type '{typeName}' is not an array type"`

## Vorteile

- **Automatisch**: Kein manueller Aufruf nötig, wird bei Änderungen getriggert
- **Schnell**: Minimaler Overhead, einfache Lookups
- **Benutzerfreundlich**: Sofortige visuelle Rückmeldung über Status
- **Einfach**: Keine komplexe Validierungslogik, nur direkte Zuordnung
- **Inkrementell**: Einzelne Nodes können bei Bedarf neu geprüft werden

## Einschränkungen

- **Einfache Logik**: Nur direkte Typ/Field-Lookups, keine komplexen Constraints
- **Keine Hierarchieprüfung**: Keine Validierung von Vererbungshierarchien
- **Keine Required-Fields**: Prüft nicht, ob alle Pflichtfelder eines Typs vorhanden sind
- **Keine Cross-Referenzen**: Keine Validierung von Wood-Links oder Referenzen
- **Keine Sammelberichte**: Kein zentrales Error-Reporting, nur pro-Node Status

## Nächste Schritte

- Integration in UI zur Anzeige der Status-Farben/Stile
- Unit-Tests für die Typzuordnung
- Integrationstests für Trigger-Punkte