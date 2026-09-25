# Typzuordnung - Validatoren-Welt (Schwergewichtig)

## 🎯 Zielsetzung

Umfassendes Validierungs-Framework für die Konsistenz zwischen `EditTree`/`EditNode`-Hierarchie und `JsonModelDescriptor`/`JsonTypeDescriptor`/`JsonFieldDescriptor`.

**Zweck:**
- Detaillierte Prüfung aller Modell-Constraints
- Generierung von Diagnostics für Fehleranalyse
- Explizite Aufrufe vor kritischen Operationen (Speichern, Export, etc.)
- Erweiterbares System für neue Validierungsregeln

---

## 🏗️ Architektur

Die Validatoren-Welt folgt dem **Builder-Pattern / Visitor-Pattern** aus `jsonCasted` und besteht aus:

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
     │  │ EditTreeModelConsistencyValidator                    │ │
     │  │ EditNodeRequiredFieldsValidator                      │ │
     │  │ EditNodeTypeHierarchyValidator                      │ │
     │  └──────────────────────────────────────────────────────┘ │
     └────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 ValidationContext                                │
│  • EditTree-Referenz                                            │
│  • JsonModelDescriptor-Referenz                                │
│  • List<EditNodeDiagnostic> (Sammelstelle für Ergebnisse)       │
│  • Aktueller Pfad im Baum (für Fehlerlokalisierung)             │
│  • getPathString() mit Iterator-basierter Pfadkonvertierung   │
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

---

## 📋 Validator-Typen

### 1. EditNodeObjectTypeValidator

**Zweck:** Prüft, ob `EditNodeObject`-Instanz zu einem existierenden `JsonTypeDescriptor` im Modell passt.

**Validiert:**
- `EditNodeObject.getName()` entspricht einem Typ im `JsonModelDescriptor`
- Typ kann über exakten Namen oder perceptiv (perceptual matching) gefunden werden

**Fehler:**
- `editnode.object.type.missing`: Typ nicht im Modell gefunden
- `editnode.object.type.ambiguous`: Mehrere Typen passen (perceptual matching)

**Severity:** ERROR (kritisch, ohne Typ kann Object nicht validiert werden)

---

### 2. EditNodePropertyFieldValidator

**Zweck:** Prüft, ob `EditNodeProperty` zu einem gültigen Feld des Parent-Typs gehört.

**Validiert:**
- Parent ist `EditNodeObject` mit gültigem `JsonTypeDescriptor`
- Feldname existiert im Typ
- Feldtyp (JsonNodeType) ist kompatibel mit dem Deskriptor

**Fehler:**
- `editnode.property.parent.missing`: Parent hat keinen Typ-Deskriptor
- `editnode.property.field.missing`: Feld nicht im Parent-Typ gefunden
- `editnode.property.type.mismatch`: JsonNodeType passt nicht zum Feldtyp

**Severity:** ERROR (Feld ohne gültige Definition ist ungültig)

---

### 3. EditTreeModelConsistencyValidator

**Zweck:** Prüft die globale Konsistenz zwischen EditTree und JsonModelDescriptor.

**Validiert:**
- Alle `EditNodeObject`-Typen existieren im Modell
- Alle `EditNodeProperty`-Felder gehören zu existierenden Parent-Typen
- Keine zyklischen Referenzen in der Typenhierarchie
- Alle referenzierten Typen sind im Modell registriert

**Fehler:**
- `editnode.tree.inconsistent`: Generelle Inkonsistenz zwischen Baum und Modell
- `editnode.tree.orphaned.types`: Typen im Baum, die nicht im Modell sind

**Severity:** ERROR

---

### 4. EditNodeRequiredFieldsValidator

**Zweck:** Prüft, ob alle Required-Felder eines Typs im EditTree vorhanden sind.

**Validiert:**
- Für jedes `EditNodeObject` mit `JsonTypeDescriptor`:
  - Alle Felder mit `isRequired() == true` sind als Kinder vorhanden
  - Feldnamen stimmen überein
  - Feldtypen sind kompatibel

**Fehler:**
- `editnode.type.required.missing`: Required-Feld fehlt
- `editnode.type.required.incomplete`: Nicht alle Required-Felder vorhanden

**Severity:** WARNING (kann gültig sein, aber unvollständig)

---

### 5. EditNodeTypeHierarchyValidator

**Zweck:** Prüft Vererbungshierarchien und Typkompatibilität.

**Validiert:**
- `EditNodeObject` mit `extends`-Attribut referenzieren existierende Parent-Typen
- Polymorphe Nodes haben gültige Typ-Deskriptoren
- Interface-Implementierungen sind korrekt

**Fehler:**
- `editnode.type.hierarchy.invalid`: Parent-Typ existiert nicht
- `editnode.type.hierarchy.cyclic`: Zyklische Vererbung erkannt

**Severity:** ERROR

---

## ⚙️ Aufruf-Strategien

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

**Typische Aufrufstellen:**
- Vor dem Speichern: `SaveCommand.execute()` → Validierung → bei Fehlern Abbruch
- Vor dem Export: `ExportCommand.execute()` → Validierung
- Bei Modellwechsel: `EditTree.setJsonModelDescriptor()` → Validierung
- Auf Benutzeranforderung: Menüpunkt "Validieren"

---

### B. Integration in Befehle

Jeder Command kann Validierung vor der Ausführung durchführen:

```java
public class AddNodeCommand extends AbstractEditCommand {
    @Override
    public CommandResult execute() {
        // Validierung vor dem Hinzufügen
        ValidationResult validation = validateNode(parent, newNode);
        if (validation.hasErrors()) {
            return CommandResult.failure("Cannot add node: " + validation.getErrors().get(0).getMessage());
        }
        
        // Node hinzufügen
        parent.addChild(newNode);
        return CommandResult.success();
    }
    
    private ValidationResult validateNode(EditNode parent, EditNode newNode) {
        EditValidationRunner runner = new EditValidationRunner();
        return runner.validateSingleNode(newNode, editTree.getJsonModelDescriptor());
    }
}
```

**Integrierbar in:**
- `AddNodeCommand`
- `PasteFromStashCommand`
- `MoveNodeCommand`
- `SetValueCommand`
- `SetJsonModelDescriptorCommand`

---

### C. Inkrementelle Validierung

Validierung nur eines Subtrees oder einzelner Nodes:

```java
// Validierung eines einzelnen Nodes
ValidationResult result = runner.validateSingleNode(node, descriptor);

// Validierung eines Subtrees
ValidationResult result = runner.validateSubtree(rootNode, descriptor);
```

**Vorteile:**
- Performance-Optimierung bei großen Bäumen
- Gezielte Fehlerprüfung nach lokalen Änderungen

---

## 📝 Diagnostics-System

### EditNodeDiagnostic

```java
public class EditNodeDiagnostic {
    private final Severity severity;      // ERROR, WARNING, INFO
    private final String code;            // Eindeutiger Fehlercode, z.B. "editnode.type.missing"
    private final String message;         // Menschlich lesbare Nachricht
    private final EditNode sourceNode;    // Der betroffene Node
    private final JsonModelDescriptor modelContext;  // Modellkontext (optional)
    private final Object relatedData;     // Zusätzliche Daten (z.B. erwarteter Typ)
    
    // Helper-Methoden
    public boolean isError() { return severity == Severity.ERROR; }
    public boolean isWarning() { return severity == Severity.WARNING; }
}
```

**Severity-Levels:**
| Level | Verwendung | Beispiel |
|-------|------------|----------|
| ERROR | Kritische Probleme, Blockieren von Operationen | Typ nicht gefunden, Feld fehlt |
| WARNING | Potenzielle Probleme, keine Blockade | Required-Feld fehlt |
| INFO | Informative Hinweise | Typ wurde perceptiv matched |

---

### ValidationResult

```java
public class ValidationResult {
    private final List<EditNodeDiagnostic> diagnostics = new ArrayList<>();
    
    public void add(EditNodeDiagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }
    
    public boolean hasErrors() { 
        return diagnostics.stream().anyMatch(d -> d.isError()); 
    }
    
    public boolean hasWarnings() { 
        return diagnostics.stream().anyMatch(d -> d.isWarning()); 
    }
    
    public boolean isValid() { 
        return !hasErrors(); 
    }
    
    public List<EditNodeDiagnostic> getErrors() { ... }
    public List<EditNodeDiagnostic> getWarnings() { ... }
    public List<EditNodeDiagnostic> getByNode(EditNode node) { ... }
    public List<EditNodeDiagnostic> getBySeverity(Severity severity) { ... }
    
    // Für UI: Gruppierung nach Node
    public Map<EditNode, List<EditNodeDiagnostic>> getDiagnosticsByNode() { ... }
}
```

---

## 🎛️ ValidatorRegistry & Contributor-Pattern

### ValidatorRegistry

Sammelstelle für alle Validatoren, ähnlich wie in `jsonCasted`:

```java
public class ValidatorRegistry {
    private final List<EditNodeValidator> nodeValidators = new ArrayList<>();
    private final List<EditTreeValidator> treeValidators = new ArrayList<>();
    
    public ValidatorRegistry addNodeValidator(EditNodeValidator validator) {
        nodeValidators.add(validator);
        return this;
    }
    
    public ValidatorRegistry addTreeValidator(EditTreeValidator validator) {
        treeValidators.add(validator);
        return this;
    }
    
    public List<EditNodeValidator> getNodeValidators() { ... }
    public List<EditTreeValidator> getTreeValidators() { ... }
}
```

### ValidatorContributor

Erlaubt modulare Erweiterungen:

```java
public interface ValidatorContributor {
    void contribute(ValidatorRegistry registry);
}

// Beispiel: Core-Validatoren
public class CoreEditValidatorContributor implements ValidatorContributor {
    @Override
    public void contribute(ValidatorRegistry registry) {
        registry.addNodeValidator(new EditNodeObjectTypeValidator());
        registry.addNodeValidator(new EditNodePropertyFieldValidator());
        registry.addTreeValidator(new EditTreeModelConsistencyValidator());
        registry.addTreeValidator(new EditNodeRequiredFieldsValidator());
        registry.addTreeValidator(new EditNodeTypeHierarchyValidator());
    }
}

// Aufruf im ValidationRunner
public class EditValidationRunner {
    private final List<ValidatorContributor> contributors = new ArrayList<>();
    
    public EditValidationRunner() {
        contributors.add(new CoreEditValidatorContributor());
    }
    
    public EditValidationRunner addContributor(ValidatorContributor contributor) {
        contributors.add(contributor);
        return this;
    }
    
    public ValidationResult validate(EditTree tree, JsonModelDescriptor descriptor) {
        ValidatorRegistry registry = new ValidatorRegistry();
        for (ValidatorContributor contributor : contributors) {
            contributor.contribute(registry);
        }
        
        ValidationContext context = new ValidationContext(tree, descriptor, new ValidationResult());
        
        // Nodes validieren
        validateNodesRecursive(tree.getRoot(), registry, context);
        
        // Baum validieren
        for (EditTreeValidator validator : registry.getTreeValidators()) {
            validator.validate(tree, context);
        }
        
        return context.getResult();
    }
}
```

---

## ✅ Vorteile

| Vorteil | Beschreibung |
|---------|--------------|
| **Umfassend** | Alle Constraints werden geprüft |
| **Detailliert** | Präzise Fehlerberichte mit Kontext |
| **Erweiterbar** | Neue Validatoren können einfach hinzugefügt werden |
| **Modular** | Contributor-Pattern ermöglicht pluggable Validierung |
| **UI-freundlich** | Diagnostics ermöglichen detaillierte Fehleranzeige |

---

## ❌ Nachteile / Einschränkungen

| Nachteil | Beschreibung |
|----------|--------------|
| **Performance** | Höherer Overhead, nicht für Echtzeit geeignet |
| **Komplexität** | Aufwändige Implementierung und Wartung |
| **Manueller Aufruf** | Muss explizit getriggert werden |
| **Speicherintensiv** | Diagnostics-Objekte verbrauchen Speicher |

---

## 🔄 Zusammenarbeit mit On-the-Fly Lösung

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

---

## 📝 Implementierungsempfehlungen

### Phase 1: Kern-Validatoren
- `EditNodeObjectTypeValidator`
- `EditNodePropertyFieldValidator`
- `EditTreeModelConsistencyValidator`

### Phase 2: Erweiterte Validatoren
- `EditNodeRequiredFieldsValidator`
- `EditNodeTypeHierarchyValidator`

### Phase 3: Integration
- Integration in `SaveCommand`
- Integration in `PasteFromStashCommand`
- Menüpunkt "Validieren" in UI

### Phase 4: UI-Integration
- Fehler als Baum markieren
- Diagnostics als ToolTip anzeigen
- Sammelberichte als Dialog

---

## 🎯 Zusammenfassung

Die Validatoren-Welt bietet:
- **Umfassende Validierung** aller Modell-Constraints
- **Detaillierte Fehlerberichte** für Diagnose und UI
- **Erweiterbares Framework** für neue Regeln
- **Explizite Kontrolle** über Validierungszeitpunkte

**Geeignet für:** Kritische Operationen, Batch-Verarbeitung, umfassende Modellprüfungen, Fehlerdiagnose.

**Nicht geeignet für:** Echtzeit-Feedback während der Bearbeitung (dafür On-the-Fly Lösung verwenden).
