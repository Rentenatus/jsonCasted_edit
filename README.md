# jsonCasted Edit - A Modular JSON Editing Framework

**jsonCasted Edit** encapsulates JSON editing in a UI-independent way. The same core logic powers both the **Wood Json Jack Editor** (Swing GUI) and any future frontend like an **MCP Server**.

---

## Support
If you like this project, consider [supporting the author](https://github.com/sponsors/Rentenatus).

---

## Architecture Overview

The system has **three cleanly separated layers**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYERS                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  FRONTEND LAYER (UI-Specific)                                   │
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │ Wood Json Jack  │    │   MCP Server    │                     │
│  │  (Swing GUI)    │    │ (Text/JSON API) │                     │
│  └────────┬────────┘    └────────┬────────┘                     │
│           │                      │                              │
│           └──────────┬───────────┘                              │
│                      │ both use:                                │
│                      v                                          │
│  EDITING CORE LAYER (UI-Agnostic)                               │
│  ┌─────────────────────────────────────────────────────┐        │
│  │  de.jare.jsoncasted.editor.*                        │        │
│  │  • EditNode hierarchy (EditNodeAbstract)            │        │
│  │  • EditCommand pattern (Add, Delete, Copy, Paste)   │        │
│  │  • ClipboardManager (multi-stash system)            │        │
│  │  • UndoManager (command history)                    │        │
│  │  • NO Swing/UI dependencies                         │        │
│  └─────────────────────────────────────────────────────┘        │
│                       │                                         │
│                       v                                         │
│  JSONCASTED CORE LIBRARY (UI-Agnostic Foundation)               │
│  ┌─────────────────────────────────────────┐                    │
│  │  JSON Processing, Type System,          │                    │
│  │  Reference Resolution (Wood mechanism)  │                    │
│  │  • JsonNode (OBJECT, ARRAY, STRING, ...)│                    │
│  │  • JsonModel (type registry)            │                    │
│  │  • JsonClass, JsonInter, JsonField      │                    │
│  │  • WoodProvider, WoodResolver           │                    │
│  │  • ParserService, ConvertService        │                    │
│  │  • JsonWriter, BuilderService           │                    │
│  │  • ZERO UI dependencies                 │                    │
│  └─────────────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Projects

### jsonCasted - Core Library

The **UI-agnostic JSON processing engine** with:

| Feature | Implementation |
|---------|---------------|
| Type-safe casting | JsonModel + JsonClass type registry |
| Interface/abstract class support | JsonInter with whitelisted implementations |
| Polymorphic objects | `_class` field + inline `(Type){...}` notation |
| Cross-resource references | Wood mechanism (`_woodLink`, `_woodObjectId`, `_woodProviders`) |
| Security | Whitelist-based construction (no arbitrary instantiation) |
| Validation | Extensible validation framework with severity levels |
| Meta-modeling | Self-describing models that can be edited themselves |

#### Wood Reference System

Enables EMF-like resource management in pure JSON:

```json
{
  "_woodProviders": [{"synonym": "save", "filename": "data.json"}],
  "object": {"_woodObjectId": "123", "_class": "MyClass", "value": "test"},
  "reference": {"_woodLink": "save::123"}
}
```

- `_woodProviders`: Declares external JSON files
- `_woodObjectId`: Unique ID within a resource
- `_woodLink`: Reference format is `"synonym::id"`
- `this::id` or `self::id`: Self-reference within same file

**Resolution Process:**
1. Parse main JSON → JsonSystem with JsonResource
2. Extract `_woodProviders` → load external files
3. Build `LinkingSet` (objectIdMap + linkMap)
4. Iteratively resolve all `_woodLink` references
5. Return `WoodResolution` (resolved objects + unresolved keys + exceptions)

#### Model Pipeline

```
JSON Text → JsonNode (tree) → JsonNode + Type → JsonItem → Java Objects
     ↓            ↓                ↓              ↓
  Parse        Attach           Convert        Build
  Service      _class           Service        Service
```

Every stage is **explicit and introspectable**, enabling:
- Editor tools to visualize/manipulate at any level
- Self-describing models (export as description, edit the description)
- Validation at each stage
- Easy debugging


---

### Wood Json Jack - Swing Editor

The **graphical JSON editor** built on jsonCasted.

#### Features

- Tree-based editing with custom renderer/editor
- Property inspector for detailed editing
- Cross-resource reference support
- Polymorphic type support with `_class` selection
- Undo/Redo with unlimited history
- Multi-stash clipboard (preserves hierarchy and expansion state)
- Multiple tabs for different files
- Theming support (FlatLaf)
- Real-time validation feedback

---

## Editing Core - UI-Agnostic Logic

The **heart of the architecture** - all editing logic without any UI dependencies.

### EditNode Hierarchy

```
EditNode (interface)
   ↑
EditNodeAbstract (abstract)
   ├── EditNodeProperty (leaf: name, value, type)
   └── EditNode (container: children)
```

**EditNodeAbstract** manages:
- `editId`: Unique identifier for undo/redo tracking
- `leftRange`, `timesRange`: Version tracking for conflict resolution
- `parent`: Parent node reference
- `children`: Child node list

**Key difference from JsonNode:** EditNode adds editing metadata (ID, ranges, hierarchy) that enables undo/redo, clipboard, and change tracking.

### Command Pattern

All edits are commands.

**Available Commands:**
- `AddNodeCommand`: Add child to parent
- `DeleteNodeCommand`: Remove nodes
- `CopyToStashCommand`: Copy to clipboard stash
- `CutToStashCommand`: Cut to clipboard stash
- `PasteFromStashCommand`: Paste from stash

### Clipboard System

**Multi-stash system** with:
- Named stashes ("default", "backup", etc.)
- Hierarchical structure preservation
- Expansion state preservation (which nodes were expanded)
- Cross-file copy/paste support

### Undo/Redo Management

```java
UndoManager undoManager = new UndoManager();
undoManager.executeCommand(new AddNodeCommand(parent, child));
undoManager.undo();  // Undo last command
undoManager.redo();  // Redo last undone command
undoManager.addUndoRedoListener(listener);  // UI updates
```

**Features:**
- Unlimited undo/redo (memory limited)
- Command grouping for atomic operations
- Listener notifications for UI updates
- Skip tracking for non-editing commands

---

## Design Principles

### 1. Separation of Concerns

Each layer has a single, well-defined responsibility:
- **jsonCasted Core**: JSON parsing, type system, reference resolution
- **Editing Core**: Node manipulation, commands, undo/redo, clipboard
- **Frontend**: User interaction, presentation

### 2. UI-Agnostic Core

- No Swing dependencies in jsonCasted or editing core
- Pure Java data structures (JsonNode, EditNode, EditCommand)
- Clear interfaces between layers
- Event-based communication

### 3. Explicit Over Implicit

- Multi-stage pipeline vs. magic deserialization
- Explicit `_class` field vs. automatic type detection
- Explicit `_woodLink` syntax vs. hidden references
- Explicit model definitions vs. implicit conventions

### 4. Self-Describing Models

Models can export themselves as descriptions, which can then be edited:
```java
JsonModelDescriptor descriptor = jsonModel.describe();
// descriptor can be serialized, edited, version controlled
```

### 5. Safety First

- Whitelist-based construction (no arbitrary class instantiation)
- Validation at every stage (syntax, type, whitelist)
- No arbitrary code execution
- Immutable where appropriate (WoodProvider, JsonNode internals)

---

## Use Cases

| Use Case | jsonCasted Features | Editing Core Features |
|----------|---------------------|------------------------|
| Game Data Editing | Polymorphic types, Wood references | Undo/redo, clipboard |
| Configuration Management | JsonModel, validation | Commands, history |
| Plugin Systems | Whitelist construction | EditNode manipulation |
| Modding Tools | Cross-resource references | Multi-stash clipboard |
| AI-Generated JSON | Security, validation | Full editing pipeline |
| EMF Alternative | WoodProvider, WoodResolver | EditNode hierarchy |

---


## Status

**Active Development**

Both jsonCasted and Wood Json Jack are actively maintained. The architecture is stable and proven effective for complex JSON editing scenarios. The next focus is formalizing the editing core as a reusable module.

---

## License

Eclipse Public License v2.0 (EPL-2.0)

---

## Contributing

Contributions are welcome! Key areas:
- New frontends (MCP server, web UI, CLI)
- New type builders
- Validation rules
- Performance optimizations
- Documentation improvements

---

*Generated by Mistral Vibe. Co-Authored-By: Mistral Vibe <vibe@mistral.ai>*
