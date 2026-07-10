package atb.cli

import atb.core.metrics.OverlayKind
import atb.core.view.ViewGranularity
import atb.server.JsonCodecs
import cats.effect.*
import cats.syntax.all.*
import com.monovore.decline.*
import com.monovore.decline.effect.*

import java.awt.Desktop
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit

/** CLI entry point — delegates to Commands and Wiring. */
object Main extends CommandIOApp(name = "atb", header = "Architecture Toolbox"):

  override def main: Opts[IO[ExitCode]] =
    Commands.root.map {
      case CommandConfig.Serve(cfg)  => runServe(cfg)
      case CommandConfig.Export(cfg) => runExport(cfg)
    }

  private def runServe(cfg: ServeConfig): IO[ExitCode] =
    val target       = Commands.toTarget(cfg.repo, cfg.classDirs, cfg.include)
    val sinceInstant = parseSince(cfg.since)
    Wiring.service.flatMap { service =>
      for
        _   <- service.analyze(target, sinceInstant).flatMap {
                 case Left(err) => IO.raiseError(new RuntimeException(Exporter.formatError(err)))
                 case Right(_)  => IO.unit
               }
        url  = s"http://localhost:${cfg.port}/"
        _   <- IO.println(s"Architecture Toolbox ready at $url")
        _   <- openBrowser(url).whenA(!cfg.noOpen)
        _   <- AtbServer.serve(service, cfg.port).use(_ => IO.never)
      yield ExitCode.Success
    }

  private def runExport(cfg: ExportConfig): IO[ExitCode] =
    val target       = Commands.toTarget(cfg.repo, cfg.classDirs, cfg.include)
    val sinceInstant = parseSince(cfg.since)
    Wiring.service.flatMap { service =>
      for
        result <- service.analyze(target, sinceInstant)
        code   <- result match
                    case Left(err) => IO.println(Exporter.formatError(err)).as(ExitCode.Error)
                    case Right(_)  =>
                      service.view(2, Set.empty, OverlayKind.None, None, ViewGranularity.Package).flatMap {
                        case None    => IO.println("No analysis result").as(ExitCode.Error)
                        case Some(v) =>
                          val graph = JsonCodecs.cytoscapeGraph(v)
                          cfg.format match
                            case "html" => Exporter.writeHtml(cfg.out, graph)
                            case _      => Exporter.writeJson(cfg.out, graph)
                      }
      yield code
    }

  private def parseSince(s: String): Option[Instant] =
    if s.endsWith("m") then
      Some(Instant.now().minus(s.stripSuffix("m").toIntOption.getOrElse(24) * 30L, ChronoUnit.DAYS))
    else None

  private def openBrowser(url: String): IO[Unit] =
    IO.whenA(Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE))(
      IO(Desktop.getDesktop.browse(URI.create(url)))
    ).handleErrorWith(_ => IO.unit)
