# Typzuordnung - Leichtgewichtige Lösung (On-the-Fly)

## 🎯 Zielsetzung

Leichtgewichtige, automatische Zuordnung von `JsonTypeDescriptor`/`JsonFieldDescriptor` zu `EditNode`-Instanz während der Bearbeitung, mit sofortiger Status-Markierung bei Nicht-Übereinstimmung.

**Zweck:**
- Automatische Typzuordnung ohne manuellen Aufruf
- Schnelle Rückmeldung an den Benutzer über inkonsistente Nodes
- Minimaler Performance-Overhead

---

## 🔄 Trigger-Punkte

Die Typzuordnung wird automatisch bei folgenden Ereignisse ausgelöst:

| Situation | Auslöser | Methode |
|-----------|----------|---------|
| Neuer Node wird erstellt | `EditTree.addNode()`, `EditNodeAbstract.deepCopy()` | Nach Erstellung |
| Node wird verschoben | `MoveNodeCommand.execute()` | Nach Verschiebung |
| Modelldeskriptor wird gesetzt | `EditTree.setJsonModelDescriptor()` | Nach Setzen |
| Node-Attribut ändert sich | `EditNode.setName()`, `EditNodeProperty.setType()` | Nach Änderung |
| Baumerstellung/Import | `EditTree()` (Konstruktor mit JSON), `PasteFromStashCommand` | Nach Initialisierung |

---

## 🏗️ Implementierungsstrategie

### EditStatus Enum (Neu)

**Pfad:** `de.jare.jsoncasted.editor.core.EditStatus`

Analog zu `EdCursor.java` implementiertes Enum für Typensicherheit:
- **Enum-Konstanten:** `STATELESS`, `OKAY`, `WARNING`, `ERROR`
- **Integer-Konstanten:** `STATELESS_VALUE=0`, `OKAY_VALUE=1`, `WARNING_VALUE=2`, `ERROR_VALUE=3`
- **Utility-Methoden:** `get(String literal)`, `getByName(String name)`, `get(int value)`, `VALUES`
- **Hilfsmethoden:** `isError()`, `isWarning()`, `isOkay()`, `isStateless()`

**Migration:** Die alten String-Konstanten `EDIT_*` in `EditNode.java` sind als `@Deprecated` markiert.

---

### A. EditTree-Level - Zentrale Iteration

**Methode:** `EditTree.assignTypesFromModel()`

**Verantwortung:**
- Iteration durch den gesamten Baum (BFS oder DFS)
- Aufruf von `tryAssignType()` auf jedem Node
- Aufruf bei Modellwechsel: `EditTree.setJsonModelDescriptor(JsonModelDescriptor)` → automatischer Aufruf von `assignTypesFromModel()`

**Iterationsalgorithmus:**
```
Start bei root
├── für jeden Child: tryAssignType()
├── rekursiv oder iterativ (Stack-basiert für DFS)
└── Setzen von Status/JsonType bei jedem Node
```

**Wahl der Iteration:**
- **BFS (Breitensuche):** Gut für flache Bäume, gleichmäßige Verarbeitung
- **DFS (Tiefensuche):** Gut für tiefe Hierarchien, Stack-basiert oder rekursiv

---

### B. Node-Level - Dezidierte Logik pro Node-Typ

Jeder `EditNode` implementiert (oder erbt) die Methode:

**Methode:** `tryAssignType(JsonModelDescriptor modelDescriptor)`

**Rückgabe:** `boolean` (true = Typ zugewiesen, false = Problem gefunden)


#### Implementation pro Node-Typ:

**1. EditNodeObject**
```
Logik:
1. Holen: node.getName() → Suchname
2. Suche: modelDescriptor.getType(name) oder modelDescriptor.getTypePerceptive(name)
3. Wenn gefunden:
   - node.setJsonType(foundType)
   - node.setEditStatus(EditStatus.OKAY)
   - node.setEditMessage(null)
   - return true
4. Wenn nicht gefunden:
   - node.setEditStatus(EditStatus.WARNING)
   - node.setEditMessage("Type '" + name + "' not found in model")
   - return false
```

**2. EditNodeProperty**
```
Logik:
1. Prüfen: Hat Parent einen JsonTypeDescriptor? (node.getParent() instanceof EditNodeObject)
2. Wenn Parent kein EditNodeObject oder kein JsonType:
   - node.setEditStatus(EditStatus.WARNING)
   - node.setEditMessage("Cannot resolve field: parent has no type")
   - return false
3. Wenn Parent JsonType hat:
   - Holen: parentType = parent.getJsonType()
   - Holen: fieldName = node.getName()
   - Suche: parentType.getField(fieldName)
   - Wenn gefunden:
     - node.setJsonField(foundField)
     - node.setEditStatus(EditStatus.OKAY)
     - node.setEditMessage(null)
     - return true
   - Wenn nicht gefunden:
     - node.setEditStatus(EditStatus.ERROR)  // Strengere Fehlerklasse
     - node.setEditMessage("Field '" + fieldName + "' not found in type '" + parentType.getTypeName() + "'")
     - return false
```

**3. EditNodePropertyArr**
```
Logik: Wie EditNodeProperty, aber:
- Zusätzlich prüfen: node.getType() == JsonNodeType.ARRAY
- Feld muss Array-Typ unterstützen (JsonFieldDescriptor.isArray())
```

---

### C. Status-Markierung

**Status-Konstanten (EditStatus Enum):**
- `EditStatus.STATELESS` – Neutraler Zustand
- `EditStatus.OKAY` – ✅ Typ/Field erfolgreich zugewiesen
- `EditStatus.WARNING` – ⚠️ Typ/Field nicht gefunden, aber nicht kritisch
- `EditStatus.ERROR` – ❌ Kritisches Problem (z.B. Field existiert nicht im Parent-Typ)

**Hinweis:** Die alten String-Konstanten `EDIT_*` in `EditNode.java` sind als `@Deprecated` markiert und sollten durch `EditStatus` ersetzt werden.

**Fehlermeldungen:**
- Kurze, prägnante Nachrichten für UI-Anzeige
- Beispiel: `"Type 'Person' not found"`, `"Field 'age' missing in 'Person'"`

---

## ✅ Vorteile

| Vorteil | Beschreibung |
|---------|--------------|
| **Automatisch** | Kein manueller Aufruf nötig, wird bei Änderungen getriggert |
| **Schnell** | Minimaler Overhead, einfache Lookups |
| **Benutzerfreundlich** | Sofortige visuelle Rückmeldung über Status |
| **Einfach** | Keine komplexe Validierungslogik, nur direkte Zuordnung |
| **Inkrementell** | Einzelne Nodes können bei Bedarf neu geprüft werden |

---

## ❌ Einschränkungen / Nachteile

| Einschränkung | Beschreibung |
|---------------|--------------|
| **Einfache Logik** | Nur direkte Typ/Field-Lookups, keine komplexen Constraints |
| **Keine Hierarchieprüfung** | Keine Validierung von Vererbungshierarchien |
| **Keine Required-Fields** | Prüft nicht, ob alle Pflichtfelder eines Typs vorhanden sind |
| **Keine Cross-Referenzen** | Keine Validierung von Wood-Links oder Referenzen |
| **Keine Sammelberichte** | Kein zentrales Error-Reporting, nur pro-Node Status |

---

## 🎯 Integration

### In EditTree
```java
public void setJsonModelDescriptor(JsonModelDescriptor descriptor) {
    this.jsonModelDescriptor = descriptor;
    assignTypesFromModel(); // Automatische Zuordnung bei Modellwechsel
}

public void assignTypesFromModel() {
    if (jsonModelDescriptor != null) {
        assignTypesRecursive(getRoot(), jsonModelDescriptor);
    }
}

private void assignTypesRecursive(EditNodeAbstract node, JsonModelDescriptor descriptor) {
    node.tryAssignType(descriptor);
    for (EditNode child : node.getChildren()) {
        assignTypesRecursive((EditNodeAbstract) child, descriptor);
    }
}
```

### In EditNode (Interface)
```java
/**
 * Versucht, den passenden Typ/Field aus dem Modell zuzuordnen.
 * Setzt bei Erfolg den Deskriptor, bei Misserfolg den Edit-Status.
 * 
 * @param descriptor Der aktuelle JsonModelDescriptor
 * @return true wenn Zuordnung erfolgreich, false sonst
 */
default boolean tryAssignType(JsonModelDescriptor descriptor) {
    // Standardimplementierung: OKAY, kann von Subklassen überschrieben werden
    this.setEditStatus(EditStatus.OKAY);
    this.setEditMessage(null);
    return true;
}
```

---

## 📝 Zusammenfassung

Die leichtgewichtige Lösung bietet:
- **Automatische Typzuordnung** bei Änderungen
- **Sofortige Status-Rückmeldung** für Benutzer
- **Minimalen Overhead** für interaktive Bearbeitung
- **Einfache Erweiterbarkeit** pro Node-Typ

**Geeignet für:** Interaktive Bearbeitung, schnelle Feedback-Zyklen, einfache Konsistenzprüfungen.

**Nicht geeignet für:** Komplexe Validierungsregeln, umfassende Modellprüfungen, Batch-Operationen.
