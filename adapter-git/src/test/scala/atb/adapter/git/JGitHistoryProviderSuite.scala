package atb.adapter.git

import atb.core.ports.AnalysisTarget
import atb.fixtures.MiniJavaFixture
import cats.effect.*
import munit.CatsEffectSuite

class JGitHistoryProviderSuite extends CatsEffectSuite:

  test("yields expected commit and author counts"):
    IO {
      val root     = MiniJavaFixture.create()
      val target   = AnalysisTarget(root, Vector.empty, None)
      val provider = JGitHistoryProvider[IO]
      provider.changeSets(target, None).compile.toVector.map { changes =>
        assertEquals(changes.size, 20)
        assertEquals(changes.map(_.author).distinct.size, 3)
        assert(changes.count(_.files.nonEmpty) >= 19)
      }
    }.flatten
