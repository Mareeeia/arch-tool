# Architecture decisions

## AD-001: fs2 in core port signatures

TASKS.md §3 restricts core to `scala` stdlib + `cats-core`, but §5 defines `VcsHistoryProvider` with `fs2.Stream[F, ChangeSet]`. We include `fs2-core` in core for the port type only; no `cats-effect.IO` or runtime effects in core code.

## AD-002: Scala version

Using Scala 3.3.4 (3.3.x LTS) per TASKS.md §2 instead of the initial scaffold's 3.6.4.

## AD-003: Package decomposition per TASKS.md §4.1

Restructured modules to separate `model/`, `history/`, `view/`, `graph/`, `metrics/`, and `ports/` packages in core; split adapters, server, and cli into single-public-façade files with private helpers. `AnalysisResult` lives in `app` (not core). `OverlayKind` replaces `MetricOverlay`. `AtbServer` lives in `cli` (shell) rather than `server` per wiring-boundary rule.

## AD-004: Fixture javac targets Java 21 bytecode

ArchUnit 1.3 cannot read Java 26 class files (major version 70). The fixture compiles with `-source 21 -target 21` so ArchUnit can import test classes. Logged because TASKS.md does not specify this constraint.
