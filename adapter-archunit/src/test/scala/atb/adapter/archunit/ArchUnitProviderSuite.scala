package atb.adapter.archunit

import atb.core.ports.AnalysisTarget
import atb.fixtures.MiniJavaFixture
import cats.effect.*
import munit.CatsEffectSuite

import java.nio.file.Files
import scala.jdk.CollectionConverters.*

class ArchUnitProviderSuite extends CatsEffectSuite:

  test("finds planted cycle dependencies"):
    IO {
      val root      = MiniJavaFixture.create()
      val classDir  = root.resolve("target/classes")
      val classFiles = Files.walk(classDir).iterator().asScala.filter(_.toString.endsWith(".class")).toVector
      assert(classFiles.nonEmpty, clue(s"classDir=$classDir files=$classFiles"))
      val target   = AnalysisTarget(root, Vector(classDir), None)
      val provider = ArchUnitProvider[IO]
      provider.dependencies(target).flatMap {
        case Left(err) => IO(fail(err.toString))
        case Right(g)  =>
          IO {
            val names = g.classes.map(_.value).toVector.sorted.mkString(", ")
            assert(names.contains("com.a.A"), clue(names))
            assert(g.deps.nonEmpty, clue(g.deps))
          }
      }
    }.flatten
