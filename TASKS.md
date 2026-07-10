# Architecture Toolbox — Design & Task Specification

**Audience:** an autonomous coding agent. Follow this document top to bottom. Do not deviate from the architectural rules in §3 without recording the deviation in `DECISIONS.md`.

---

## 1. Product summary

Architecture Toolbox (`atb`) is a pluggable, largely language-agnostic tool for analyzing the architecture of a code repository. Written in **Scala 3**, structured as **onion/clean architecture** with a **functional core and imperative shell** (cats-effect).

Two features in v1, both driven by pointing the tool at a local repo:

1. **Dependency graph view** — an interactive, beautiful graph of dependency flow. Nodes are packages at arbitrary depth (drill down to classes). Users drag/rearrange nodes; node size, color, and edges convey architectural information. v1 extraction target: **Java repos**, via bytecode analysis (ArchUnit's `ClassFileImporter`).
2. **Architectural hotspot metrics** — mined from VCS history (git in v1): change-frequency hotspots, hidden/temporal coupling (files that change together), and bus factor per component. Metrics overlay onto the graph and are also available as tables.

Usage model:

```
atb serve /path/to/java-repo            # analyze + open interactive UI at http://localhost:7070
atb export /path/to/java-repo --format json --out graph.json

```

Future (design for, don't build): C# and JS providers (ArchUnit-style tools exist for both), Mercurial history provider.

---

## 2. Tech stack (fixed — do not substitute)


| Concern            | Choice                                                                                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Language           | Scala 3 (latest 3.3.x LTS)                                                                                                                                               |
| Build              | sbt, multi-module                                                                                                                                                        |
| Effects / shell    | cats-effect 3, fs2                                                                                                                                                       |
| HTTP server        | http4s (ember server)                                                                                                                                                    |
| JSON               | circe (+ circe-generic)                                                                                                                                                  |
| CLI parsing        | decline                                                                                                                                                                  |
| Java bytecode deps | ArchUnit (`com.tngtech.archunit:archunit`) via Java interop                                                                                                              |
| Git history        | JGit (`org.eclipse.jgit`)                                                                                                                                                |
| Logging            | log4cats + slf4j-simple (shell only)                                                                                                                                     |
| Tests              | munit + munit-cats-effect + scalacheck                                                                                                                                   |
| Frontend           | Single static `index.html` + vanilla JS + **Cytoscape.js** (with `fcose` layout extension), vendored into server resources. **No frontend build step** (no npm/webpack). |


---

## 3. Architecture rules (enforced, non-negotiable)

Onion layering, dependency direction strictly inward:

```
cli / server / adapters  →  application services  →  core (domain)

```

1. `core` **is pure.** No cats-effect, no IO, no clocks, no logging, no ArchUnit/JGit/http4s imports. Only `scala` stdlib + `cats-core`. Everything in core is deterministic functions over immutable data.
2. **Ports are traits defined in** `core` (parameterized `F[_]` where effectful). Adapters implement them; core never references adapters.
3. **All side effects live in adapters and the shell** (CLI/server wiring).
4. **Errors are ADTs** (`enum AtbError`), surfaced as `Either`/typed failures. No exceptions escape adapters — catch third-party exceptions at the adapter boundary and translate.
5. No `null`, no `var` in core, no runtime reflection.
6. Cross-module dependency rule is checked in CI: adapters may not appear on core's classpath (enforced naturally by sbt module graph — never add such a dependency).

Dogfooding note: this repo should itself be a clean example of what the tool visualizes.

---

## 4. Module layout (sbt)

```
architecture-toolbox/
  build.sbt
  core/          // pure domain: model, graph algebra, metrics, ports
  app/           // application services: use-case orchestration over ports (F[_]: Sync)
  adapter-archunit/  // DependencyGraphProvider via ArchUnit ClassFileImporter
  adapter-git/       // VcsHistoryProvider via JGit
  server/        // http4s routes + static UI assets (src/main/resources/web/)
  cli/           // decline CLI, wiring (the imperative shell / main)
  fixtures/      // tiny synthetic Java+git repo used by integration tests (see §10)

```

Module dependencies: `app → core`; `adapter-* → core`; `server → app, core`; `cli → everything`. Nothing depends on `cli`.

### 4.1 Package & class structure (prescriptive — create these packages and files)

Root package: `atb`. One top-level concept per file, file named after it. Anything not listed as **public** below must be `private[<pkg>]`.

```
core/src/main/scala/atb/core/
  model/            // "what code looks like" — data only, zero behavior beyond smart constructors
    Fqcn.scala          // opaque type + companion: Fqcn.parse(String): Option[Fqcn], .packageName: Pkg, .segments
    Pkg.scala           // opaque type + companion: Pkg.parse, .truncate(depth: Int): Pkg, .depth, .parent
    DependencyGraph.scala   // DependencyGraph, ClassDep, DepKind, ClassMeta
  history/          // "what change looks like" — data only
    ChangeSet.scala     // ChangeSet, FileChange
  view/             // "what the UI consumes" — data only
    GraphView.scala     // GraphView, ViewNode, ViewEdge, NodeId, NodeKind, NodeMetrics
  graph/            // pure algorithms over model → view
    Rollup.scala        // object Rollup: view(graph, defaultDepth, expanded): GraphView (metrics-less)
    Cycles.scala        // object Cycles: tarjan(...): Vector[Vector[NodeId]]
    PathMapping.scala   // object PathMapping: toFqcn(path, sourceRoots): Option[Fqcn]
  metrics/          // pure algorithms over history → metric values
    Hotspots.scala      // object Hotspots + case class Hotspot + HotspotConfig(halfLifeDays, ignoreGlobs)
    Coupling.scala      // object Coupling + CouplingPair + CouplingConfig(minSupport, minConfidence, maxCommitSize)
    BusFactor.scala     // object BusFactor + ComponentBusFactor
    Overlay.scala       // object Overlay: apply(view, metrics, kind): GraphView — merges metrics into NodeMetrics
  ports/            // the ONLY traits with F[_]; depend on model/history only
    DependencyGraphProvider.scala
    VcsHistoryProvider.scala
    AnalysisTarget.scala
  AtbError.scala

app/src/main/scala/atb/app/
  AnalysisService.scala   // public trait + object AnalysisService: def make[F[_]: Async](...): F[AnalysisService[F]]
  AnalysisResult.scala    // public: graph + changesets + precomputed metrics, immutable
  AnalysisCache.scala     // private[app]: Ref-based, keyed by (repoRoot, headCommit)
  SourceRoots.scala       // private[app]: discovers source roots for PathMapping
  LocLoader.scala         // private[app]: fills ClassMeta.loc from source files (IO at the app edge is
                          // acceptable ONLY via injected FileReader port-like function — keep it swappable)

adapter-archunit/src/main/scala/atb/adapter/archunit/
  ArchUnitProvider.scala  // PUBLIC — the only public type in this module
  ClassDirLocator.scala   // private[archunit]: finds target/classes etc., pure given a dir listing fn
  DepTranslator.scala     // private[archunit]: JavaClass/Dependency → ClassDep (pure, unit-testable)

adapter-git/src/main/scala/atb/adapter/git/
  JGitHistoryProvider.scala  // PUBLIC — the only public type in this module
  CommitWalker.scala         // private[git]: first-parent walk → RevCommits
  DiffExtractor.scala        // private[git]: commit pair → Vector[FileChange]

server/src/main/scala/atb/server/
  Routes.scala           // public: HttpRoutes[F] built from AnalysisService — no logic beyond param parsing
  JsonCodecs.scala       // private[server]: all circe encoders (core stays JSON-free)
  QueryParams.scala      // private[server]: depth/expanded/overlay extractors + validation
  StaticAssets.scala     // private[server]: serves resources/web

cli/src/main/scala/atb/cli/
  Main.scala             // IOApp — nothing but CommandIOApp delegation
  Commands.scala         // decline command tree → pure Config ADT (ServeConfig | ExportConfig)
  Wiring.scala           // constructs providers, service, server; the ONE place `new`/make calls happen
  Exporter.scala         // json/html export logic

```

Encapsulation rules the agent must follow:

1. **Data and behavior are separate.** `model/history/view` packages contain only immutable data + smart constructors in companions. Algorithms live in `graph/` and `metrics/` as stateless `object`s whose methods take all inputs explicitly. No method on a case class reaches outside its own fields.
2. **One public façade per module.** Adapters expose exactly one public class; `app` exposes `AnalysisService` + `AnalysisResult`. Everything else `private[pkg]`. If server/cli needs something private, that's a design signal — promote deliberately, note it in `DECISIONS.md`.
3. **Opaque types with smart constructors.** `Fqcn`/`Pkg`/`NodeId` cannot be raw strings at call sites; construction validates. All parsing returns `Option`/`Either`, never throws.
4. **Config as data.** Every tunable (§6 thresholds) is a case class with a `default` in its companion, threaded explicitly — no globals, no implicit config.
5. **Wiring in exactly one place** (`cli/Wiring.scala`). No class constructs its own dependencies; everything arrives via constructor/`make` parameters.
6. **No** `Utils`**/**`Helpers`**/**`Manager`**/**`Common` **files.** If a helper has no obvious home, its concept is missing — name the concept and make it a file.

### 4.2 How to approach decomposition (order of work within any milestone)

1. **Types first.** Write the data types and their smart constructors; make them compile with no logic.
2. **Signatures second.** Write every public function signature with scaladoc and `???` bodies; check that callers (next layer up) can be written against them. If a signature is awkward to call, fix the signature now.
3. **Tests third, implementation fourth.** Write the milestone's tests against the signatures, then fill in bodies until green.
4. **Grow by splitting, not appending.** When a file exceeds ~150 lines or an object gains a second responsibility, split it along the noun (`Rollup` staying rollup; degree computation → its own private function or `Degrees` object). Never widen an existing class to absorb a new feature.
5. **Check the dependency direction after every file:** imports in `core` must never mention `atb.adapter`, `atb.app`, `atb.server`, cats-effect, or any third-party lib besides cats-core. Run a grep for these before each commit.

---

## 5. Core domain model (`core`)

```scala
// Identity
opaque type Fqcn = String            // "com.acme.billing.Invoice"
opaque type Pkg  = String            // "com.acme.billing"
final case class NodeId(value: String)

enum NodeKind { case Package, Class }

// The raw, class-level graph produced by providers
final case class ClassDep(from: Fqcn, to: Fqcn, kind: DepKind)
enum DepKind { case FieldAccess, MethodCall, Inheritance, Reference }

final case class DependencyGraph(
  classes: Set[Fqcn],
  deps: Vector[ClassDep],
  meta: Map[Fqcn, ClassMeta]        // loc, sourcePath if known
)
final case class ClassMeta(sourcePath: Option[String], loc: Option[Int])

// The rendered/rolled-up view served to the UI
final case class ViewNode(id: NodeId, label: String, kind: NodeKind,
                          classCount: Int, loc: Int, inDegree: Int, outDegree: Int,
                          metrics: NodeMetrics)
final case class ViewEdge(from: NodeId, to: NodeId, weight: Int, cyclic: Boolean)
final case class GraphView(nodes: Vector[ViewNode], edges: Vector[ViewEdge],
                           cycles: Vector[Vector[NodeId]])

final case class NodeMetrics(hotspotScore: Option[Double], busFactor: Option[Int],
                             churn: Option[Int], authors: Option[Int])

// VCS history
final case class ChangeSet(commitId: String, author: String,
                           timestamp: java.time.Instant, files: Vector[FileChange])
final case class FileChange(path: String, linesAdded: Int, linesRemoved: Int)

// Metric results
final case class Hotspot(path: String, revisions: Int, loc: Int, score: Double)
final case class CouplingPair(a: String, b: String, coChanges: Int,
                              support: Int, confidence: Double)
final case class ComponentBusFactor(component: Pkg, busFactor: Int,
                                    topAuthors: Vector[(String, Double)])

enum AtbError {
  case NoCompiledClasses(searched: Vector[String])
  case NotAGitRepo(path: String)
  case InvalidTarget(path: String, reason: String)
  case ProviderFailure(provider: String, message: String)
}

```

### Ports (in `core.ports`)

```scala
trait DependencyGraphProvider[F[_]]:
  def name: String
  def supports(target: AnalysisTarget): F[Boolean]
  def dependencies(target: AnalysisTarget): F[Either[AtbError, DependencyGraph]]

trait VcsHistoryProvider[F[_]]:
  def name: String
  def changeSets(repo: AnalysisTarget, since: Option[java.time.Instant]): fs2.Stream[F, ChangeSet]

final case class AnalysisTarget(repoRoot: java.nio.file.Path,
                                classDirs: Vector[java.nio.file.Path],
                                includePattern: Option[String])  // e.g. "com.acme.."

```

Pluggability = registering additional implementations of these two traits. That is the whole extension story for C#/JS/Mercurial later.

---

## 6. Core algorithms (pure functions in `core`, all property-tested)

### 6.1 Package rollup — `Rollup.at(graph, depth): GraphView`

- Map each `Fqcn` to its package prefix truncated to `depth` segments (`depth = 0` ⇒ whole class-level graph, i.e. nodes are classes).
- Aggregate edges between rolled-up nodes; edge `weight` = count of underlying class deps; drop self-loops.
- Node `loc` = sum of member class LOC; `classCount`, `inDegree`, `outDegree` computed here.
- Also support **mixed expansion**: a set of "expanded" package NodeIds whose children render one level deeper (drives UI expand/collapse). Signature: `Rollup.view(graph, defaultDepth, expanded: Set[NodeId])`.

### 6.2 Cycle detection — `Cycles.tarjan(view): Vector[Vector[NodeId]]`

- Tarjan SCC over the rolled-up view. SCCs of size > 1 are cycles; mark participating edges `cyclic = true`. Cycles are the #1 architectural smell to surface — the UI colors them red.

### 6.3 Hotspots — `Hotspots.compute(changes: Vector[ChangeSet], locByPath: Map[String, Int]): Vector[Hotspot]`

- `revisions(path)` = number of commits touching path, with exponential recency decay: each commit contributes `2^(-ageDays/halfLife)`, `halfLife = 180` days (configurable).
- `score = normalize(revisions) * normalize(loc)` where `normalize` is min-max to [0,1] over the analyzed set. LOC is the complexity proxy for v1 (fallback when class LOC unknown: current line count of the file, provided by adapter).
- Sort descending; exclude paths matching configurable ignore globs (default: `**/test/**`, lockfiles, generated dirs).

### 6.4 Temporal coupling — `Coupling.compute(changes, minSupport = 5, minConfidence = 0.4)`

- For every unordered pair of files co-occurring in a commit (skip commits touching > 50 files — bulk renames/reformats), count `coChanges`.
- `support = min(changes(a), changes(b))`, `confidence = coChanges / support`.
- Report pairs meeting thresholds, sorted by confidence then coChanges. Flag pairs whose files map to **different top-level packages** as "hidden coupling" (crosses an architectural boundary yet co-evolves).

### 6.5 Bus factor — `BusFactor.compute(changes, componentOf: String => Option[Pkg])`

- Knowledge per author per component = sum of `linesAdded` by that author on files in the component.
- Bus factor = minimum number of authors whose combined knowledge ≥ 50% of the component total.
- Report per component with top authors and their knowledge share.

### 6.6 Path ↔ package mapping — `PathMapping`

- Overlaying git metrics (file paths) on the dependency graph (packages) needs a mapping. Heuristic: strip known source-root prefixes (`src/main/java/`, `src/test/java/`, `src/`, module-prefixed variants like `*/src/main/java/`), convert remaining dir path to dotted package, filename to class name. Prefer `ClassMeta.sourcePath` from the provider when available; heuristic is the fallback. Must be a total function returning `Option`.

---

## 7. Adapters

### 7.1 `adapter-archunit` — `ArchUnitProvider extends DependencyGraphProvider[IO]`

- Locate compiled classes: if `classDirs` given, use them; else search repo for `target/classes`, `build/classes/java/main`, `out/production` (record which were searched → `NoCompiledClasses` error with a helpful message telling the user to build first, e.g. `mvn compile` / `gradle classes`).
- `ClassFileImporter().importPaths(...)`, then for each `JavaClass` walk `getDirectDependenciesFromSelf`, translating `Dependency` into `ClassDep` with `DepKind`. Filter to classes matching `includePattern` (default: packages present in the analyzed dirs; always exclude `java..`, `javax..`, third-party jars).
- Populate `ClassMeta.sourcePath` from ArchUnit's source info when present; `loc` left `None` (filled from files by the app layer when source paths resolve).
- Wrap all ArchUnit calls in `IO.blocking`; translate exceptions to `ProviderFailure`.

### 7.2 `adapter-git` — `JGitHistoryProvider extends VcsHistoryProvider[IO]`

- Open repo with JGit; stream commits (first-parent walk of default branch), computing per-commit `FileChange` via diff to parent (rename detection ON, ~60% score). Honor `since`.
- Merge commits: skip (first-parent diffs cover their content).
- Emit as `fs2.Stream[IO, ChangeSet]`; all JGit calls in `IO.blocking`.

---

## 8. Application services (`app`)

`AnalysisService[F[_]: Sync]` — the only thing `server` talks to:

- `analyze(target): F[Either[AtbError, AnalysisResult]]` — runs provider + history provider (in parallel via `parMapN`), computes metrics, joins them via `PathMapping`, caches result in a `Ref` keyed by repo path + HEAD commit.
- `view(depth, expanded, overlay): F[GraphView]` — rollup from cached `AnalysisResult`; `overlay ∈ {none, hotspot, busfactor}` decides which metric fills `NodeMetrics` used for coloring.
- `hotspots / coupling / busFactor: F[Vector[...]]` — tabular metric access.

History analysis window default: last 24 months (`--since`).

---

## 9. HTTP API + frontend (`server`)

### API (all JSON via circe; codecs colocated in `server`, not `core`)

```
GET /api/status                        → { state: "ready"|"analyzing"|"error", error? }
GET /api/graph?depth=2&expanded=a,b&overlay=hotspot   → GraphView JSON
GET /api/metrics/hotspots?limit=50     → [Hotspot]
GET /api/metrics/coupling?limit=50     → [CouplingPair]
GET /api/metrics/busfactor             → [ComponentBusFactor]
GET /                                  → index.html (static, from resources/web)

```

`GraphView` JSON shape is Cytoscape-friendly: `{ nodes: [{ data: {...} }], edges: [{ data: {...} }], cycles: [...] }`.

### Frontend requirements (`resources/web/index.html` + `app.js` + `style.css`)

- Cytoscape.js + cytoscape-fcose, vendored (download the two minified JS files into resources; no CDN at runtime).
- **Interactions:** drag nodes (positions persist in-page); double-click a package node → expand one level (adds it to `expanded` set, refetch); double-click expanded node → collapse; depth slider (1–6); search box that highlights matching nodes; hover tooltip with full package name, class count, LOC, in/out degree, and metric values; click edge → show contributing class-level deps count.
- **Visual encoding (defaults):** node size ∝ `sqrt(loc)` (fallback `classCount`); node color = selected overlay — hotspot: white→red scale by `hotspotScore`; busfactor: green (≥3) → amber (2) → red (1); none: hue by top-level package. Edge width ∝ `log(weight+1)`; cyclic edges bright red with arrow emphasis. Directed arrows always on.
- **Overlay switcher** (None / Hotspots / Bus factor) and a side panel with three tabs rendering the metric tables (hotspots, coupling pairs, bus factor), rows clickable → focus/zoom the corresponding node.
- Aesthetic bar: dark theme, smooth fcose layout animation, subtle curved edges. This view is the product's centerpiece — spend real effort here.
- Keep it framework-free vanilla JS (ES modules fine). Target ≤ ~800 lines of JS.

Performance target: usable at 300 visible nodes / 2000 edges (rollup keeps counts low; never ship the full class graph to the browser unless depth=0 on a small repo).

---

## 10. Testing strategy

1. **Core (unit + property, munit-scalacheck):** rollup preserves total dep count as edge-weight sum; rollup at depth d then d-1 is consistent; Tarjan finds planted cycles; hotspot normalization ∈ [0,1]; coupling is symmetric; bus factor ≥ 1 for nonempty components; PathMapping round-trips generated paths.
2. **Fixture repo (**`fixtures/mini-java/`**):** generate programmatically in a test setup step (do NOT commit a nested `.git`): ~10 Java classes across 3 packages with a deliberate cycle (`a→b→c→a`), compiled with `javac` at test time; a scripted git history (JGit-built in test temp dir, ~20 commits, 3 authors, two files that always change together).
3. **Adapter integration tests:** ArchUnit provider finds the planted cycle's deps; JGit provider yields expected commit/author/file counts.
4. **Server tests:** http4s routes golden-tested against fixture-derived JSON (stable field ordering via circe printer with sorted keys).
5. **End-to-end smoke:** CLI `export --format json` on the fixture exits 0 and output parses back into `GraphView`.
6. No UI automation tests in v1; instead `export --format html` (see M6) gives a manually inspectable artifact.

---

## 11. CLI (`cli`)

```
atb serve  <repo> [--classes DIR...] [--include PATTERN] [--since 24m] [--port 7070] [--no-open]
atb export <repo> [--format json|html] [--out FILE] [same analysis flags]

```

`serve` prints the URL and opens the browser (`--no-open` to suppress). Analysis runs on startup; UI polls `/api/status` until ready. All config also readable from optional `.atb.conf` (HOCON via pure-config is NOT needed — parse a simple `key = value` file or skip file config if time-constrained; CLI flags are the contract).

---

## 12. Milestones — execute in order; each must compile, pass all tests, and be committed before starting the next

**M0 — Scaffold.** sbt multi-module skeleton per §4, scalafmt config, GitHub Actions CI (`sbt test`), empty-but-compiling modules, `README.md` stub, `DECISIONS.md`. *Done when CI is green.*

**M1 — Core domain.** All of §5 + §6 implemented and property-tested. Pure module only. *Done when core tests pass and core has no effectful dependencies.*

**M2 — ArchUnit adapter + JSON export.** §7.1, fixture repo generator, `atb export --format json` works end-to-end on the fixture. *Done when the exported JSON contains the planted cycle.*

**M3 — Server + interactive UI.** §9 minus overlays/metric tabs: serve graph, drag, expand/collapse, depth slider, search, cycle highlighting, tooltips. *Done when* `atb serve fixtures` *renders an interactive graph with the cycle in red.*

**M4 — Git metrics.** §7.2 + §6.3–6.6 wired through `AnalysisService`; `/api/metrics/`* endpoints live. *Done when fixture history yields the planted coupled pair and correct author counts.*

**M5 — Overlays + metric panel.** Overlay switcher, node coloring, side-panel tables, click-to-focus. *Done when hotspot overlay visibly colors the fixture's churned package.*

**M6 — Polish.** `export --format html` (self-contained single file: inlined JS + embedded graph JSON), README with screenshots/GIF, `--include`/ignore-glob config, error UX for `NoCompiledClasses`/`NotAGitRepo`, cache invalidation on HEAD change.

---

## 13. Non-goals for v1

No source-code parsing providers, no C#/JS/Mercurial (ports only), no persistence/database, no auth/multi-user, no live file-watching re-analysis, no cyclomatic-complexity computation (LOC proxy only), no npm toolchain.

## 14. Conventions for the implementing agent

- Conventional commits, one commit per coherent change; never commit failing tests.
- When a spec detail is ambiguous, choose the simplest option consistent with §3, and log the choice in `DECISIONS.md`.
- Keep functions small; prefer total functions; exhaustive matches on all enums.
- Every public type/function in `core` gets a one-line scaladoc.
- If a milestone is at risk of ballooning, cut UI polish before cutting correctness or tests.

