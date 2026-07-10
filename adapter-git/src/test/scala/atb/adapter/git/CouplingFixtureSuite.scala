package atb.adapter.git

import atb.core.metrics.Coupling
import atb.core.ports.AnalysisTarget
import atb.fixtures.MiniJavaFixture
import cats.effect.*
import munit.CatsEffectSuite

class CouplingFixtureSuite extends CatsEffectSuite:

  test("fixture history yields planted X/Y coupled pair"):
    IO {
      val root     = MiniJavaFixture.create()
      val target   = AnalysisTarget(root, Vector.empty, None)
      val provider = JGitHistoryProvider[IO]
      provider.changeSets(target, None).compile.toVector.map { changes =>
        val pairs = Coupling.compute(changes)
        val xy    = pairs.find(p => Set(p.a, p.b) == Set(MiniJavaFixture.CoupledFiles(0), MiniJavaFixture.CoupledFiles(1)))
        assert(xy.isDefined, clue(pairs.map(p => s"${p.a} <-> ${p.b} (${p.coChanges})").mkString("\n")))
        assert(xy.exists(_.coChanges >= 5))
      }
    }.flatten
