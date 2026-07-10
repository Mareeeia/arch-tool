package atb.cli

import atb.core.AtbError
import atb.server.{CytoscapeGraph, HtmlExport}
import cats.effect.*

import java.nio.file.{Files, Path}

/** JSON and HTML export logic. */
object Exporter:

  def writeJson(out: Option[String], graph: CytoscapeGraph): IO[ExitCode] =
    val json = HtmlExport.sortedJson(graph)
    out match
      case Some(path) => IO(Files.writeString(Path.of(path), json)).as(ExitCode.Success)
      case None       => IO.println(json).as(ExitCode.Success)

  def writeHtml(out: Option[String], graph: CytoscapeGraph): IO[ExitCode] =
    val html = HtmlExport.render(graph)
    out match
      case Some(path) => IO(Files.writeString(Path.of(path), html)).as(ExitCode.Success)
      case None       => IO.println(html).as(ExitCode.Success)

  def formatError(err: AtbError): String = err match
    case AtbError.NoCompiledClasses(searched) =>
      s"""No compiled classes found. Build the project first (e.g. mvn compile / gradle classes).
         |Searched:
         |${searched.mkString("  - ", "\n  - ", "")}""".stripMargin
    case AtbError.NotAGitRepo(path) =>
      s"Not a git repository: $path"
    case AtbError.InvalidTarget(path, reason) =>
      s"Invalid target $path: $reason"
    case AtbError.ProviderFailure(provider, message) =>
      s"Provider $provider failed: $message"
