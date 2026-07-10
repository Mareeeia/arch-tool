package atb.server

import atb.adapter.archunit.ArchUnitProvider
import atb.adapter.git.JGitHistoryProvider
import atb.app.{AnalysisService, AnalysisStatus}
import atb.core.ports.AnalysisTarget
import atb.fixtures.MiniJavaFixture
import cats.effect.*
import io.circe.{Json, Printer}
import io.circe.parser.parse
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*

class RoutesSuite extends CatsEffectSuite:

  private val sortedPrinter: Printer = Printer.spaces2.copy(sortKeys = true)

  private def sorted(json: Json): String = sortedPrinter.print(json)

  test("GET /api/graph returns stable cycle-bearing graph JSON"):
    IO {
      val root   = MiniJavaFixture.create()
      val target = AnalysisTarget(root, Vector(root.resolve("target/classes")), None)
      AnalysisService.make(ArchUnitProvider[IO], JGitHistoryProvider[IO]).flatMap { service =>
        for
          _        <- service.analyze(target, None)
          status   <- service.status
          routes    = Routes(service)
          graphReq  = Request[IO](Method.GET, uri"/api/graph?depth=2")
          graphResp <- routes.orNotFound.run(graphReq)
          body     <- graphResp.as[String]
          again    <- routes.orNotFound.run(graphReq).flatMap(_.as[String])
        yield
          status match
            case AnalysisStatus.Ready(_) => ()
            case other                   => fail(s"expected Ready, got $other")
          assertEquals(graphResp.status, Status.Ok)
          val json = parse(body).toOption.get
          assert(json.hcursor.downField("cycles").focus.exists(_.asArray.exists(_.nonEmpty)))
          assert(json.hcursor.downField("nodes").focus.exists(_.asArray.exists(_.nonEmpty)))
          assertEquals(sorted(parse(body).toOption.get), sorted(parse(again).toOption.get))
      }
    }.flatten

  test("GET /api/metrics/coupling includes fixture pair"):
    IO {
      val root   = MiniJavaFixture.create()
      val target = AnalysisTarget(root, Vector(root.resolve("target/classes")), None)
      AnalysisService.make(ArchUnitProvider[IO], JGitHistoryProvider[IO]).flatMap { service =>
        for
          _       <- service.analyze(target, None)
          routes   = Routes(service)
          req      = Request[IO](Method.GET, uri"/api/metrics/coupling?limit=50")
          resp    <- routes.orNotFound.run(req)
          body    <- resp.as[String]
        yield
          assertEquals(resp.status, Status.Ok)
          val json = parse(body).toOption.get
          assert(json.asArray.exists(_.exists { row =>
            val a = row.hcursor.get[String]("a").toOption
            val b = row.hcursor.get[String]("b").toOption
            (a, b) match
              case (Some(aPath), Some(bPath)) =>
                Set(aPath, bPath) == MiniJavaFixture.CoupledFiles.toSet
              case _ => false
          }))
      }
    }.flatten
