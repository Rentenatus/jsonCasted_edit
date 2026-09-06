# Implementierung der Schwergewichtigen Validatoren-Welt

## Übersicht

Diese Implementierung fügt ein umfassendes Validierungs-Framework für die Konsistenz zwischen EditTree/EditNode-Hierarchie und JsonModelDescriptor hinzu. Im Gegensatz zur leichtgewichtigen On-the-Fly Lösung wird diese Validatoren-Welt explizit aufgerufen und bietet detaillierte Diagnostics.

## Implementierte Komponenten

### 1. Basis-Klassen

#### Severity.java
- **Enum** mit drei Stufen: `ERROR`, `WARNING`, `INFO`
- Dient zur Kategorisierung von Diagnostics nach Schweregrad

#### EditNodeDiagnostic.java
- **Hauptklasse** für einzelne Diagnose-Einträge
- Enthält: `severity`, `code`, `message`, `sourceNode`, `modelContext`, `relatedData`
- **Factory-Methoden**: `error()`, `warning()`, `info()` für einfache Erstellung
- **Hilfsmethoden**: `isError()`, `isWarning()`, `isInfo()`

#### ValidationResult.java
- **Sammelklasse** für alle Diagnostics eines Validierungslaufs
- **Hauptmethoden**:
  - `add()`, `addAll()` – Diagnostics hinzufügen
  - `hasErrors()`, `hasWarnings()`, `isValid()` – Statusabfragen
  - `getErrors()`, `getWarnings()`, `getInfos()` – Gefilterte Listen
  - `getByNode()`, `getBySeverity()` – Gefiltert nach Kriterien
  - `getDiagnosticsByNode()` – Gruppiert nach Nodes für UI
  - `getTotalCount()`, `getErrorCount()`, `getWarningCount()` – Statistiken

#### ValidationContext.java
- **Kontextobjekt** für Validierungsoperationen
- Enthält: `rootNode`, `modelDescriptor`, `result`, `path` (Stack für Baumtraversierung)
- **Pfadverwaltung**: `pushPath()`, `popPath()`, `peekPath()`, `clearPath()`, `getPath()`
- **Convenience-Methoden**: `addError()`, `addWarning()`, `addInfo()` für einfache Diagnostic-Erstellung

### 2. Validator-Interfaces

#### EditNodeValidator.java
- **Interface** für Validatoren, die einzelne Nodes prüfen
- **Methode**: `validate(EditNodeAbstract node, ValidationContext context)`
- **Default-Methoden**: `getId()`, `getDescription()`

#### EditTreeValidator.java
- **Interface** für Validatoren, die den gesamten Baum prüfen
- **Methode**: `validate(EditTree tree, ValidationContext context)`
- **Default-Methoden**: `getId()`, `getDescription()`

### 3. Registry & Contributor

#### ValidatorRegistry.java
- **Sammelstelle** für alle Validatoren
- **Methoden**:
  - `addNodeValidator()`, `addTreeValidator()` – Validatoren registrieren
  - `removeNodeValidator()`, `removeTreeValidator()` – Validatoren entfernen
  - `getNodeValidators()`, `getTreeValidators()` – Zugriff auf registrierte Validatoren
  - `hasNodeValidators()`, `hasTreeValidators()` – Prüfung auf Vorhandensein
  - `clear()` – Alle Validatoren entfernen

#### ValidatorContributor.java
- **Interface** für das Contributor-Pattern
- **Methode**: `contribute(ValidatorRegistry registry)`
- Ermöglicht modulare Erweiterungen des Validierungs-Frameworks

#### CoreEditValidatorContributor.java
- **Standard-Contributor**, der automatisch mit `EditValidationRunner` registriert wird
- Registriert alle Kern-Validatoren:
  - Node-Validatoren: `EditNodeObjectTypeValidator`, `EditNodePropertyFieldValidator`
  - Tree-Validatoren: `EditTreeModelConsistencyValidator`, `EditNodeRequiredFieldsValidator`, `EditNodeTypeHierarchyValidator`

### 4. Hauptklasse: EditValidationRunner.java
- **Haupt-Einstiegspunkt** für Validierungsoperationen
- **Konstruktor**: Registriert automatisch `CoreEditValidatorContributor`
- **Hauptmethoden**:
  - `validate(EditTree, JsonModelDescriptor)` – Validiert gesamten Baum
  - `validateSingleNode(EditNodeAbstract, JsonModelDescriptor)` – Validiert einzelnen Node
  - `validateSubtree(EditNodeAbstract, JsonModelDescriptor)` – Validiert Subtree
  - `addContributor()`, `removeContributor()`, `clearContributors()` – Contributor-Verwaltung
- **Interne Methoden**:
  - `buildRegistry()` – Baut Registry aus Contributors auf
  - `validateNodesRecursive()` – Rekursive Node-Validierung
  - `validateWithoutModel()` – Eingeschränkte Validierung ohne Modell

### 5. Konkrete Validatoren

#### Node-Validatoren

##### EditNodeObjectTypeValidator.java
- **Zweck**: Prüft, ob EditNodeObject-Instanzen zu existierenden Typen im Modell passen
- **Validiert**:
  - Node-Name entspricht einem Typ in JsonModelDescriptor
  - Typ kann über exakten Namen oder perceptiv gefunden werden
- **Fehler**:
  - `editnode.object.name.missing` – Node hat keinen Namen
  - `editnode.object.type.missing` – Typ nicht im Modell gefunden (ERROR)
  - `editnode.object.type.ambiguous` – Mehrere Typen passen perceptiv (WARNING)

##### EditNodePropertyFieldValidator.java
- **Zweck**: Prüft, ob EditNodeProperty-Instanzen zu gültigen Feldern des Parent-Typs gehören
- **Validiert**:
  - Parent ist EditNodeObject mit gültigem JsonTypeDescriptor
  - Feldname existiert im Typ
  - Für EditNodePropertyArr: Feld muss Array-Typ unterstützen
- **Fehler**:
  - `editnode.property.parent.missing` – Parent hat keinen Typ-Deskriptor (ERROR)
  - `editnode.property.parent.invalid` – Parent ist kein EditNodeObject (ERROR)
  - `editnode.property.name.missing` – Property hat keinen Namen (ERROR)
  - `editnode.property.field.missing` – Feld nicht im Parent-Typ gefunden (ERROR)
  - `editnode.property.type.mismatch` – Feld ist kein Array-Typ, aber Node ist EditNodePropertyArr (ERROR)

#### Tree-Validatoren

##### EditTreeModelConsistencyValidator.java
- **Zweck**: Prüft globale Konsistenz zwischen EditTree und JsonModelDescriptor
- **Validiert**:
  - Alle EditNodeObject-Typen existieren im Modell
  - Alle EditNodeProperty-Felder gehören zu existierenden Parent-Typen
  - Strukturkonsistenz (z. B. Property-Nodes mit gültigem Parent)
- **Fehler**:
  - `editnode.tree.inconsistent` – Generelle Inkonsistenz (ERROR)
  - `editnode.tree.orphaned.types` – Typen im Baum, die nicht im Modell sind (ERROR)
  - `editnode.property.parent.invalid` – Property-Node hat ungültigen Parent (ERROR)

##### EditNodeRequiredFieldsValidator.java
- **Zweck**: Prüft, ob alle Required-Felder eines Typs im EditTree vorhanden sind
- **Validiert**:
  - Für jedes EditNodeObject mit JsonTypeDescriptor:
    - Alle Felder mit `isRequired() == true` sind als Kinder vorhanden
    - Feldnamen stimmen überein
- **Fehler**:
  - `editnode.type.required.missing` – Required-Feld fehlt (WARNING)
- **Severity**: WARNING (kann gültig sein, aber unvollständig)

##### EditNodeTypeHierarchyValidator.java
- **Zweck**: Prüft Vererbungshierarchien und Typkompatibilität
- **Validiert**:
  - Parent-Typen existieren (wenn spezifiziert)
  - Keine zyklische Vererbung
  - Interface-Implementierungen sind korrekt
- **Fehler**:
  - `editnode.type.hierarchy.invalid` – Parent-Typ existiert nicht (ERROR)
  - `editnode.type.hierarchy.cyclic` – Zyklische Vererbung erkannt (ERROR)
  - `editnode.type.implementor.missing` – Implementor-Typ nicht im Modell (WARNING)

## Aufruf-Strategien

### A. Expliziter Aufruf

```java
// Manuelle Validierung des gesamten Baumes
EditValidationRunner runner = new EditValidationRunner();
ValidationResult result = runner.validate(editTree, jsonModelDescriptor);

if (result.hasErrors()) {
    // Fehler anzeigen
    for (EditNodeDiagnostic diagnostic : result.getErrors()) {
        EditNode node = diagnostic.getSourceNode();
        String message = diagnostic.getMessage();
        // UI: Node markieren, Fehler anzeigen
    }
}
```

### B. Integration in Befehle

Jeder Command kann Validierung vor der Ausführung durchführen:

```java
public class SaveCommand extends AbstractEditCommand {
    @Override
    public CommandResult execute() {
        EditValidationRunner runner = new EditValidationRunner();
        ValidationResult validation = runner.validate(editTree, editTree.getJsonModelDescriptor());
        
        if (validation.hasErrors()) {
            return CommandResult.failure("Cannot save: validation failed");
        }
        
        // Speichern durchführen
        return CommandResult.success();
    }
}
```

### C. Inkrementelle Validierung

```java
// Validierung eines einzelnen Nodes
ValidationResult result = runner.validateSingleNode(node, descriptor);

// Validierung eines Subtrees
ValidationResult result = runner.validateSubtree(rootNode, descriptor);
```

## Implementierungs-Phasen

### ✅ Phase 1: Kern-Validatoren
- [x] `EditNodeObjectTypeValidator`
- [x] `EditNodePropertyFieldValidator`
- [x] `EditTreeModelConsistencyValidator`

### ✅ Phase 2: Erweiterte Validatoren
- [x] `EditNodeRequiredFieldsValidator`
- [x] `EditNodeTypeHierarchyValidator`

### ❌ Phase 3: Integration
- [ ] Integration in `SaveCommand`
- [ ] Integration in `PasteFromStashCommand`
- [ ] Menüpunkt "Validieren" in UI

### ❌ Phase 4: UI-Integration
- [ ] Fehler als Baum markieren
- [ ] Diagnostics als ToolTip anzeigen
- [ ] Sammelberichte als Dialog

## Architektur-Überblick

```
┌─────────────────────────────────────────────────────────────┐
│                     EditValidationRunner                         │
│  (Haupt-Einstiegspunkt für Validierung)                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   ValidatorRegistry                              │
│  (Sammelt alle Validatoren)                                     │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
     ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
     │ ModelValidator  │  │ TypeValidator   │  │ FieldValidator  │
     │ (JsonModel)     │  │ (JsonType)      │  │ (JsonField)     │
     └────────────────┘  └────────────────┘  └────────────────┘
                              │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
     ┌────────────────────────────────────────────────────────────┐
     │                 EditNodeValidator (NEU)                      │
     │  (Validiert EditNode-Objekte gegen Modelldeskriptoren)       │
     │  ┌──────────────────────────────────────────────────────┐ │
     │  │ EditNodeObjectTypeValidator                          │ │
     │  │ EditNodePropertyFieldValidator                       │ │
     │  └──────────────────────────────────────────────────────┘ │
     └────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────────┐
│                 EditTreeValidator (NEU)                           │
│  (Validiert den gesamten Baum)                                    │
│  ┌────────────────────────────────────────────────────────────┐│
│  │ EditTreeModelConsistencyValidator                         ││
│  │ EditNodeRequiredFieldsValidator                           ││
│  │ EditNodeTypeHierarchyValidator                           ││
│  └────────────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 ValidationContext                                │
│  • EditTree-Referenz                                            │
│  • JsonModelDescriptor-Referenz                                │
│  • List<EditNodeDiagnostic> (Sammelstelle für Ergebnisse)       │
│  • Aktueller Pfad im Baum (für Fehlerlokalisierung)             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   ValidationResult                               │
│  • List<EditNodeDiagnostic>                                     │
│  • hasErrors(), hasWarnings(), isValid()                         │
│  • getDiagnosticsByNode(), getDiagnosticsBySeverity()          │
└─────────────────────────────────────────────────────────────┘
```

## Vorteile

| Vorteil | Beschreibung |
|---------|--------------|
| **Umfassend** | Alle Constraints werden geprüft |
| **Detailliert** | Präzise Fehlerberichte mit Kontext |
| **Erweiterbar** | Neue Validatoren können einfach hinzugefügt werden |
| **Modular** | Contributor-Pattern ermöglicht pluggable Validierung |
| **UI-freundlich** | Diagnostics ermöglichen detaillierte Fehleranzeige |

## Nachteile / Einschränkungen

| Nachteil | Beschreibung |
|----------|--------------|
| **Performance** | Höherer Overhead, nicht für Echtzeit geeignet |
| **Komplexität** | Aufwändige Implementierung und Wartung |
| **Manueller Aufruf** | Muss explizit getriggert werden |
| **Speicherintensiv** | Diagnostics-Objekte verbrauchen Speicher |

## Zusammenarbeit mit On-the-Fly Lösung

| Szenario | On-the-Fly | Validatoren-Welt |
|----------|-------------|------------------|
| **Node Erstellung** | ✅ Automatische Typzuordnung | ❌ |
| **Modellwechsel** | ✅ Schnelle Zuordnung | ✅ Vollständige Validierung |
| **Vor dem Speichern** | ❌ | ✅ Komplette Prüfung |
| **Interaktive Bearbeitung** | ✅ Status-Updates | ❌ |
| **Batch-Operationen** | ❌ | ✅ Sammelvalidierung |
| **Fehlerdiagnose** | ❌ (nur Status) | ✅ Detaillierte Diagnostics |

**Empfohlene Kombination:**
1. **On-the-Fly** für interaktive Bearbeitung (schnelle Rückmeldung)
2. **Validatoren-Welt** für kritische Operationen (Speichern, Export, Modellwechsel)

## Nächste Schritte

### Phase 3: Integration
- [ ] Integration in `SaveCommand`
- [ ] Integration in `PasteFromStashCommand`
- [ ] Integration in `MoveNodeCommand`
- [ ] Integration in `SetJsonModelDescriptorCommand`
- [ ] Menüpunkt "Validieren" in UI

### Phase 4: UI-Integration
- [ ] Fehler als Baum markieren (Farben basierend auf Severity)
- [ ] Diagnostics als ToolTip anzeigen
- [ ] Sammelberichte als Dialog
- [ ] Fehlerfilterung und -suche

### Erweitert
- [ ] Spezifische Validatoren für Domänenlogik
- [ ] Custom ValidatorContributor für Projekte
- [ ] Performance-Optimierungen für große Bäume
- [ ] Asynchrone Validierung für UI-Responsiveness