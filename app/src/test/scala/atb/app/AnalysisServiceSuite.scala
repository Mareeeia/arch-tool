package atb.app

import atb.adapter.archunit.ArchUnitProvider
import atb.adapter.git.JGitHistoryProvider
import atb.core.ports.AnalysisTarget
import atb.fixtures.MiniJavaFixture
import cats.effect.*
import munit.CatsEffectSuite

class AnalysisServiceSuite extends CatsEffectSuite:

  test("analyze returns head commit and hits cache on second run"):
    IO {
      val root   = MiniJavaFixture.create()
      val target = AnalysisTarget(root, Vector(root.resolve("target/classes")), None)
      AnalysisService.make(ArchUnitProvider[IO], JGitHistoryProvider[IO]).flatMap { service =>
        for
          first  <- service.analyze(target, None)
          second <- service.analyze(target, None)
        yield
          assert(first.isRight)
          assert(second.isRight)
          assertEquals(first, second)
          first.foreach(r => assert(r.headCommit.nonEmpty))
      }
    }.flatten
