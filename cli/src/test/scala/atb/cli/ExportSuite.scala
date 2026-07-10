package atb.cli

import atb.fixtures.MiniJavaFixture
import cats.effect.*
import io.circe.parser.*
import munit.CatsEffectSuite

import java.nio.file.Files

class ExportSuite extends CatsEffectSuite:

  test("export json on fixture exits successfully and contains cycle graph"):
    IO {
      val root = MiniJavaFixture.create()
      val out  = Files.createTempFile("atb-export-", ".json")
      Main.run(List("export", root.toString, "--format", "json", "--out", out.toString)).flatMap { code =>
        IO {
          assertEquals(code, ExitCode.Success)
          val json = parse(Files.readString(out)).toOption.get
          assert(json.hcursor.downField("cycles").focus.exists(_.asArray.exists(_.nonEmpty)))
          assert(json.hcursor.downField("nodes").focus.exists(_.asArray.exists(_.size >= 3)))
          assert(json.hcursor.downField("edges").focus.exists(_.asArray.exists(_.nonEmpty)))
        }
      }
    }.flatten

  test("export html is self-contained"):
    IO {
      val root = MiniJavaFixture.create()
      val out  = Files.createTempFile("atb-export-", ".html")
      Main.run(List("export", root.toString, "--format", "html", "--out", out.toString)).flatMap { code =>
        IO {
          assertEquals(code, ExitCode.Success)
          val html = Files.readString(out)
          assert(!html.contains("unpkg.com"))
          assert(html.contains("window.GRAPH_DATA"))
        }
      }
    }.flatten
