package atb.cli

import atb.core.ports.AnalysisTarget
import com.monovore.decline.*
import cats.implicits.*

import java.nio.file.Paths

/** Pure CLI configuration ADTs produced by decline. */
enum CommandConfig:
  case Serve(cfg: ServeConfig)
  case Export(cfg: ExportConfig)

final case class ServeConfig(
    repo: String,
    classDirs: List[String],
    include: Option[String],
    since: String,
    port: Int,
    noOpen: Boolean
)

final case class ExportConfig(
    repo: String,
    classDirs: List[String],
    include: Option[String],
    since: String,
    format: String,
    out: Option[String]
)

object Commands:

  private val repoArg = Opts.argument[String]("repo")
  private val classDirs: Opts[List[String]] =
    Opts
      .options[String]("classes", help = "Compiled classes directory (repeatable)")
      .map(_.toList)
      .orElse(Opts(List.empty[String]))
  private val include = Opts.option[String]("include", help = "Package include pattern (e.g. com.acme..)").orNone
  private val since = Opts.option[String]("since", help = "History window, e.g. 24m").withDefault("24m")
  private val port  = Opts.option[Int]("port", help = "HTTP port").withDefault(7070)
  private val noOpen = Opts.flag("no-open", help = "Do not open browser").orFalse
  private val format = Opts.option[String]("format", help = "Export format: json|html").withDefault("json")
  private val outFile = Opts.option[String]("out", help = "Output file path").orNone

  val serve: Opts[CommandConfig] =
    Opts.subcommand("serve", "Analyze repo and open interactive UI") {
      (repoArg, classDirs, include, since, port, noOpen).mapN { (r, c, i, s, p, n) =>
        CommandConfig.Serve(ServeConfig(r, c, i, s, p, n))
      }
    }

  val exportCommand: Opts[CommandConfig] =
    Opts.subcommand("export", "Analyze repo and export graph") {
      (repoArg, classDirs, include, since, format, outFile).mapN { (r, c, i, s, f, o) =>
        CommandConfig.Export(ExportConfig(r, c, i, s, f, o))
      }
    }

  val root: Opts[CommandConfig] = serve.orElse(exportCommand)

  def toTarget(repo: String, classes: List[String], include: Option[String]): AnalysisTarget =
    val root = Paths.get(repo).toAbsolutePath.normalize
    AnalysisTarget(root, classes.map(root.resolve).toVector, include)
