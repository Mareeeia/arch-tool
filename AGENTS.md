# Agent navigation guide — Architecture Toolbox (`atb`)

Use this file to find code quickly. Spec and product intent live in [TASKS.md](TASKS.md); recorded deviations live in [DECISIONS.md](DECISIONS.md); human quick-start is [README.md](README.md). Prefer this map over rediscovering the tree.

## What this repo is

Scala 3 multi-module tool that:

1. Builds a **dependency graph** from compiled Java bytecode (ArchUnit).
2. Overlays **git metrics** (hotspots, temporal coupling, bus factor).
3. Serves an interactive **Cytoscape** UI (or exports JSON/HTML).

Onion / clean architecture: **functional core, imperative shell**.

## Module graph (sbt)

```
cli ──→ server ──→ app ──→ core
 │         └──────────────→ core
 ├────→ adapter-archunit ─→ core
 └────→ adapter-git ──────→ core

fixtures ─→ core   (test support only)
```

| Module | Path | Role |
|--------|------|------|
| `core` | `core/` | Pure domain: model, graph algos, metrics, ports |
| `app` | `app/` | Use-case orchestration over ports (`AnalysisService`) |
| `adapter-archunit` | `adapter-archunit/` | `DependencyGraphProvider` via ArchUnit |
| `adapter-git` | `adapter-git/` | `VcsHistoryProvider` via JGit |
| `server` | `server/` | http4s routes + JSON + static UI (no server boot) |
| `cli` | `cli/` | Decline CLI, wiring, Ember boot, export |
| `fixtures` | `fixtures/` | Generates temp Java+git repo for tests |

Root aggregates all; publish disabled. Scala **3.3.4**, JDK **21+**. Main class: `atb.cli.Main`.

## Hard boundaries (do not break)

1. **`core` is pure** — no cats-effect, IO, logging, ArchUnit, JGit, http4s, circe. Allowed: scala stdlib, cats-core, and **fs2-core** (port type only; AD-001).
2. **Ports in `core.ports` only**; adapters implement them; core never imports adapters.
3. **Side effects** live in adapters + CLI shell.
4. **Errors** cross layers as `AtbError` (`Either`), not raw exceptions from adapters.
5. **Wiring only in** `cli/Wiring.scala` — single composition root.
6. **JSON stays in `server`** — core stays codec-free.
7. Deviations from TASKS.md §3 → record in `DECISIONS.md`.

Package root: `atb`. One concept per file; avoid `Utils`/`Helpers`/`Manager`/`Common`.

## Runtime pipeline (follow this when debugging)

```
Main → Commands → Wiring
  → AnalysisService.analyze(AnalysisTarget)
      ├─ ArchUnitProvider → DependencyGraph
      └─ JGitHistoryProvider → Stream[ChangeSet]
  → LocLoader + Hotspots/Coupling/BusFactor → AnalysisResult (cached by repo+HEAD)
  → AnalysisService.view / nodeChildren / metrics
  → Routes (JSON) + StaticAssets (UI)
  or Exporter / HtmlExport
```

| Step | Start here |
|------|------------|
| CLI flags / commands | `cli/.../Commands.scala` |
| Provider construction | `cli/.../Wiring.scala` |
| HTTP listen + open browser | `cli/.../AtbServer.scala` |
| Analyze / cache / view | `app/.../AnalysisService.scala` |
| Bytecode → graph | `adapter-archunit/.../ArchUnitProvider.scala` |
| Class dir discovery | `adapter-archunit/.../ClassDirLocator.scala` |
| Git history stream | `adapter-git/.../JGitHistoryProvider.scala` |
| Package rollup / expand | `core/.../graph/Rollup.scala` |
| Cycles (Tarjan) | `core/.../graph/Cycles.scala` |
| Path ↔ Fqcn | `core/.../graph/PathMapping.scala` |
| Scope filter | `core/.../graph/GraphScope.scala` |
| API + query params | `server/.../Routes.scala`, `QueryParams.scala` |
| Cytoscape JSON shape | `server/.../JsonCodecs.scala` |
| UI behavior | `server/src/main/resources/web/app.js` |

## Package map (where to open files)

### `core` — `core/src/main/scala/atb/core/`

| Package | Purpose | Key files |
|---------|---------|-----------|
| `model/` | Identity + raw deps | `Fqcn`, `Pkg`, `DependencyGraph` (`ClassDep`, `DepKind`, `ClassMeta`) |
| `history/` | VCS change data | `ChangeSet`, `FileChange` |
| `view/` | UI-facing graph DTOs | `GraphView`, `CouplingEdge`, `ViewGranularity` |
| `graph/` | Pure graph transforms | `Rollup`, `Cycles`, `PathMapping`, `GraphScope` |
| `metrics/` | Pure history metrics | `Hotspots`, `Coupling`, `BusFactor`, `Stability`, `Overlay`, `CouplingOverlay` |
| `ports/` | `F[_]` traits | `DependencyGraphProvider`, `VcsHistoryProvider`, `AnalysisTarget` |
| (root) | Errors | `AtbError` |

### `app` — `app/src/main/scala/atb/app/`

- **Public:** `AnalysisService`, `AnalysisResult`, `AnalysisStatus`
- **Private helpers:** `AnalysisCache`, `LocLoader`, `SourceRoots`

### Adapters

- `atb.adapter.archunit`: public `ArchUnitProvider`; private `ClassDirLocator`, `DepTranslator`
- `atb.adapter.git`: public `JGitHistoryProvider`; private `CommitWalker`, `DiffExtractor`

### `server` — `server/src/main/scala/atb/server/`

`Routes`, `QueryParams`, `JsonCodecs`, `StaticAssets`, `HtmlExport`

### `cli` — `cli/src/main/scala/atb/cli/`

`Main`, `Commands`, `Wiring`, `AtbServer` (boot lives here per AD-003), `Exporter`

### `fixtures`

`MiniJavaFixture.create()` → temp dir with compiled Java 21 classes + 20-commit git history (planted cycle, hotspot, X/Y coupling). No committed nested fixture repo.

## HTTP API

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/status` | `analyzing` / `ready` / `error` |
| GET | `/api/graph` | depth, expanded, overlay, scope, group |
| GET | `/api/graph/nodes/{id}/children` | package expand |
| GET | `/api/scopes` | selectable package prefixes |
| GET | `/api/metrics/hotspots` | optional limit |
| GET | `/api/metrics/coupling` | optional limit |
| GET | `/api/metrics/busfactor` | |
| GET | `/` | static UI |

Frontend (no build step): `server/src/main/resources/web/` — `index.html`, `app.js`, `style.css`, vendored JS.

**Known gap:** `cytoscape.min.js` is referenced by `index.html` / `StaticAssets` / `HtmlExport` but is **not** present under `web/` (only `cytoscape-fcose.min.js` is). Live UI / HTML export need that asset restored or references fixed.

## Tests — what each suite proves

| Suite | Path | Covers |
|-------|------|--------|
| Core | `core/.../CoreSuite.scala` | Rollup, cycles, metrics, PathMapping, scope, stability, coupling overlay |
| App | `app/.../AnalysisServiceSuite.scala` | Full analyze + cache with real adapters + fixture |
| ArchUnit | `adapter-archunit/.../ArchUnitProviderSuite.scala` | Classes + planted edges |
| Git | `adapter-git/.../JGitHistoryProviderSuite.scala`, `CouplingFixtureSuite.scala` | 20 commits / authors; X↔Y coupling |
| Server | `server/.../RoutesSuite.scala`, `HtmlExportSuite.scala` | API JSON, scopes, expand, export |
| CLI | `cli/.../ExportSuite.scala` | `Main.run` JSON/HTML export |

Run: `sbt test`. Fixture needs local `javac`. CI: `.github/workflows/ci.yml` (Temurin 21).

## Common change recipes

| You want to… | Touch… |
|--------------|--------|
| Add CLI flag | `Commands.scala` → thread into `AnalysisTarget` / service call |
| Add dependency/VCS backend | new adapter module implementing a port; register in `Wiring.scala` |
| Change graph rollup / expand | `Rollup.scala` + `CoreSuite` |
| Change metric formula | matching object under `core/metrics/` + suite |
| Change API response shape | `JsonCodecs.scala` + `RoutesSuite` (+ `app.js` if UI) |
| Change UI interaction | `web/app.js` (+ `style.css` / `index.html`) |
| Change export | `Exporter.scala`, `HtmlExport.scala` |
| Plant new test scenario | `MiniJavaFixture.scala` |

Work order from TASKS.md §4.2: **types → signatures → tests → implementation**. Split files before they grow a second responsibility (~150 lines heuristic).

## Implemented beyond original TASKS layout

These exist in code (keep them when navigating; don’t “remove as undocumented”):

- `GraphScope`, `ViewGranularity`, `Stability`
- Coupling as visible edges (`CouplingEdge` / `CouplingOverlay`)
- `/api/scopes`, `/api/graph/nodes/{id}/children`
- `HtmlExport`, HEAD-keyed `AnalysisCache`

## Commands cheat sheet

```bash
sbt test
sbt "cli/run serve /path/to/java-repo"
sbt "cli/run serve /path/to/repo --classes /path/to/classes --no-open"
sbt "cli/run export /path/to/repo --format json --out graph.json"
sbt "cli/run export /path/to/repo --format html --out graph.html"
```

Optional UI smoke: `scripts/debug-ui.mjs` (Playwright; not a frontend build).

## Doc roles (don’t conflate)

| File | Audience | Use for |
|------|----------|---------|
| **AGENTS.md** (this) | Coding agents | Orientation, file map, change recipes |
| **TASKS.md** | Agents + humans | Normative architecture, algorithms, milestones |
| **DECISIONS.md** | Agents + humans | Approved exceptions to TASKS |
| **README.md** | Humans | Install, run, feature list |
