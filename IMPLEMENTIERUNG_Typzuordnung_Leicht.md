# Implementierung der Leichtgewichtigen On-the-Fly Typzuordnung

## Übersicht

Diese Implementierung fügt eine automatische Typzuordnung zwischen EditTree/EditNode-Hierarchie und JsonModelDescriptor hinzu. Die Zuordnung erfolgt on-the-fly bei verschiedenen Trigger-Punkten.

## Implementierte Komponenten

### 1. EditNode Interface (`EditNode.java`)
- **Hinzugefügt**: Import für `JsonModelDescriptor`
- **Hinzugefügt**: `tryAssignType(JsonModelDescriptor descriptor)` Methode mit Standardimplementierung
  - Setzt `EDIT_OKAY` Status und löscht Fehlermeldungen
  - Kann von Subklassen überschrieben werden

### 2. EditNodeAbstract (`EditNodeAbstract.java`)
- **Hinzugefügt**: `editTree` Feld (schache Referenz zum EditTree)
- **Hinzugefügt**: `getEditTree()` und `setEditTree(EditTree)` Methoden
- **Modifiziert**: `addChildPhase1()` - setzt Tree-Referenz für neue Kinder
- **Modifiziert**: `removeChild()` - setzt Tree-Referenz auf null für entfernte Kinder

### 3. EditNodeObject (`EditNodeObject.java`)
- **Hinzugefügt**: Import für `JsonModelDescriptor`
- **Implementiert**: `tryAssignType(JsonModelDescriptor descriptor)`
  - Sucht nach `JsonTypeDescriptor` über `descriptor.getType(name)` oder `getTypePerceptive(name)`
  - Bei Erfolg: setzt `jsonType`, Status `EDIT_OKAY`
  - Bei Misserfolg: Status `EDIT_WARNING` mit Fehlermeldung
- **Modifiziert**: `setName()` - triggert `assignTypesForNode(this)` falls Tree verfügbar

### 4. EditNodeProperty (`EditNodeProperty.java`)
- **Hinzugefügt**: Importe für `JsonModelDescriptor` und `JsonTypeDescriptor`
- **Implementiert**: `tryAssignType(JsonModelDescriptor descriptor)`
  - Prüft, ob Parent ein `EditNodeObject` mit `jsonType` ist
  - Sucht nach `JsonFieldDescriptor` über `parentType.getField(fieldName)`
  - Bei Erfolg: setzt `jsonField`, Status `EDIT_OKAY`
  - Bei Misserfolg: Status `EDIT_ERROR` mit Fehlermeldung
- **Modifiziert**: `setName()` - triggert `assignTypesForNode(this)` falls Tree verfügbar
- **Modifiziert**: `setType()` - triggert `assignTypesForNode(this)` falls Tree verfügbar

### 5. EditNodePropertyArr (`EditNodePropertyArr.java`)
- **Hinzugefügt**: Importe für `JsonModelDescriptor` und `JsonTypeDescriptor`
- **Implementiert**: `tryAssignType(JsonModelDescriptor descriptor)`
  - Ähnlich wie EditNodeProperty, aber mit zusätzlicher Prüfung für Array-Typen
  - Prüft: `foundField.isAsArray() || foundField.isAsListOrArray()`
  - Bei Erfolg: setzt `jsonField`, Status `EDIT_OKAY`
  - Bei nicht-Array-Feld: Status `EDIT_ERROR` mit spezifischer Fehlermeldung

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

Die folgenden Status-Konstanten werden verwendet:

- `EDIT_STATELESS` – Neutraler Zustand (kein Modell gesetzt)
- `EDIT_OKAY` – ✅ Typ/Field erfolgreich zugewiesen
- `EDIT_WARNING` – ⚠️ Typ/Field nicht gefunden, aber nicht kritisch
- `EDIT_ERROR` – ❌ Kritisches Problem (z.B. Field existiert nicht im Parent-Typ)

## Spezifische Logik pro Node-Typ

### EditNodeObject
1. Holt Node-Name über `getName()`
2. Sucht Typ über `descriptor.getType(name)` oder `descriptor.getTypePerceptive(name)`
3. Bei Erfolg: `setJsonType(foundType)`, Status `EDIT_OKAY`
4. Bei Misserfolg: Status `EDIT_WARNING`, Meldung: `"Type '{name}' not found in model"`

### EditNodeProperty
1. Prüft, ob Parent ein `EditNodeObject` ist
2. Prüft, ob Parent einen `jsonType` hat
3. Holt Parent-Typ und Feldname
4. Sucht Feld über `parentType.getField(fieldName)`
5. Bei Erfolg: `setJsonField(foundField)`, Status `EDIT_OKAY`
6. Bei Misserfolg: Status `EDIT_ERROR`, Meldung: `"Field '{name}' not found in type '{typeName}'"`

### EditNodePropertyArr
1. Gleiche Logik wie EditNodeProperty
2. Zusätzliche Prüfung: `foundField.isAsArray() || foundField.isAsListOrArray()`
3. Bei nicht-Array-Feld: Status `EDIT_ERROR`, Meldung: `"Field '{name}' in type '{typeName}' is not an array type"`

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