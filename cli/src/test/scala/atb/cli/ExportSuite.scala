package atb.cli

import atb.fixtures.MiniJavaFixture
import cats.effect.*
import io.circe.parser.*
import munit.CatsEffectSuite

import java.nio.file.Files

class ExportSuite extends CatsEffectSuite:

  test("export json on fixture exits successfully and contains cycle"):
    IO {
      val root = MiniJavaFixture.create()
      val out  = Files.createTempFile("atb-export-", ".json")
      Main.run(List("export", root.toString, "--format", "json", "--out", out.toString)).flatMap { code =>
        IO {
          assertEquals(code, ExitCode.Success)
          val json = Files.readString(out)
          assert(parse(json).isRight)
          assert(json.contains("com.cycle"))
        }
      }
    }.flatten
