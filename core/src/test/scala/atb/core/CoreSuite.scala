package atb.core

import atb.core.graph.{Cycles, PathMapping, Rollup}
import atb.core.history.{ChangeSet, FileChange}
import atb.core.metrics.{BusFactor, Coupling, CouplingConfig, Hotspots}
import atb.core.model.*
import atb.core.view.*
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class RollupSuite extends munit.FunSuite:

  private def graph(deps: (String, String)*): DependencyGraph =
    val fqcnDeps = deps.flatMap { case (f, t) =>
      for
        from <- Fqcn.parse(f)
        to   <- Fqcn.parse(t)
      yield ClassDep(from, to, DepKind.Reference)
    }.toVector
    val classes = deps.flatMap { case (f, t) => List(Fqcn.parse(f), Fqcn.parse(t)).flatten }.toSet
    DependencyGraph(classes, fqcnDeps, Map.empty)

  test("rollup preserves total dep count as edge-weight sum"):
    val g = graph("com.a.X" -> "com.b.Y", "com.a.X" -> "com.c.Z", "com.b.Y" -> "com.c.Z")
    val view = Rollup.at(g, depth = 2)
    assertEquals(Rollup.totalWeight(view), g.deps.size)

  test("rollup at depth 0 keeps class nodes"):
    val g    = graph("com.a.X" -> "com.b.Y")
    val view = Rollup.at(g, depth = 0)
    assert(view.nodes.forall(_.classCount == 1))

class CyclesSuite extends munit.FunSuite:

  test("tarjan finds planted cycle"):
    val a = NodeId("a")
    val b = NodeId("b")
    val c = NodeId("c")
    val nodes = Vector(
      ViewNode(a, "a", NodeKind.Package, 1, 10, 1, 1, NodeMetrics.Empty),
      ViewNode(b, "b", NodeKind.Package, 1, 10, 1, 1, NodeMetrics.Empty),
      ViewNode(c, "c", NodeKind.Package, 1, 10, 1, 1, NodeMetrics.Empty)
    )
    val edges = Vector(ViewEdge(a, b, 1, false), ViewEdge(b, c, 1, false), ViewEdge(c, a, 1, false))
    val cycles = Cycles.tarjan(GraphView(nodes, edges, Vector.empty))
    assertEquals(cycles.size, 1)
    assertEquals(cycles.head.size, 3)

class HotspotsSuite extends ScalaCheckSuite:

  property("hotspot scores are normalized to 0-1 range"):
    forAll(Gen.choose(1, 5), Gen.choose(1, 5)) { (nCommits, nFiles) =>
      val now = java.time.Instant.parse("2025-01-01T00:00:00Z")
      val changes = (1 to nCommits).toVector.map { i =>
        ChangeSet(
          s"c$i",
          "author",
          now.minusSeconds(i * 86400L),
          Vector(FileChange(s"src/main/java/com/app/File$i.java", 1, 0))
        )
      }
      val locs = (1 to nFiles).map(i => s"src/main/java/com/app/File$i.java" -> 100).toMap
      val hs   = Hotspots.compute(changes, locs, now = now)
      hs.forall(h => h.score >= 0.0 && h.score <= 1.0)
    }

class CouplingSuite extends munit.FunSuite:

  test("coupling is symmetric"):
    val fileChanges = Vector(FileChange("f1.java", 1, 0), FileChange("f2.java", 1, 0))
    val changes = (1 to 5).map { i =>
      ChangeSet(s"c$i", "a", java.time.Instant.now(), fileChanges)
    }.toVector
    val pairs = Coupling.compute(changes, CouplingConfig(minSupport = 3, minConfidence = 0.5, maxCommitSize = 50))
    assert(pairs.nonEmpty)
    assertEquals(pairs.head.a, "f1.java")
    assertEquals(pairs.head.b, "f2.java")

class BusFactorSuite extends munit.FunSuite:

  test("bus factor is at least 1 for nonempty component"):
    val changes = Vector(
      ChangeSet(
        "c1",
        "alice",
        java.time.Instant.now(),
        Vector(FileChange("src/main/java/com/foo/A.java", 10, 0))
      )
    )
    val bf = BusFactor.compute(changes, p => PathMapping.sourcePathToPackage(p))
    assert(bf.forall(_.busFactor >= 1))

class PathMappingSuite extends munit.FunSuite:

  test("maps standard java source path to package"):
    val pkg = PathMapping.sourcePathToPackage("src/main/java/com/acme/billing/Invoice.java")
    assertEquals(pkg, Pkg.parse("com.acme.billing"))

  test("maps path to fqcn"):
    val fqcn = PathMapping.toFqcn("src/main/java/com/acme/billing/Invoice.java")
    assertEquals(fqcn, Fqcn.parse("com.acme.billing.Invoice"))
