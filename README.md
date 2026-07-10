# Architecture Toolbox (`atb`)

Pluggable, language-agnostic tool for analyzing the architecture of a code repository. It builds a dependency graph from compiled bytecode (Java via ArchUnit in v1), overlays git history metrics (hotspots, temporal coupling, bus factor), and serves an interactive Cytoscape graph UI.

## Requirements

- JDK 21+ (for compiling fixture/test Java sources)
- sbt 1.9+

## Quick start

Analyze a Java repo and open the UI (default port 7070):

```bash
sbt "cli/run serve /path/to/java-repo"
```

Export the graph as JSON or a self-contained HTML file:

```bash
sbt "cli/run export /path/to/java-repo --format json --out graph.json"
sbt "cli/run export /path/to/java-repo --format html --out graph.html"
```

Point `--classes` at compiled output if it is not under `target/classes`:

```bash
sbt "cli/run serve /path/to/repo --classes /path/to/repo/build/classes/java/main"
```

## What you get

- **Dependency graph** with package rollup, expand/collapse, depth slider, search, and cycle highlighting
- **Hotspots** — files/packages that change most often
- **Temporal coupling** — files that tend to change together (including cross-package “hidden” coupling)
- **Bus factor** — author concentration per component
- **HTTP API** at `/api/graph`, `/api/metrics/hotspots`, `/api/metrics/coupling`, `/api/metrics/busfactor`

## Development

```bash
sbt test
sbt "cli/run serve $(pwd)/fixtures --no-open"   # after building a fixture locally
```

See [TASKS.md](TASKS.md) for the full specification and [DECISIONS.md](DECISIONS.md) for architecture notes.
