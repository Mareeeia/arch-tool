package atb.cli

import atb.core.AtbError
import atb.core.metrics.OverlayKind
import atb.server.CytoscapeGraph
import atb.server.JsonCodecs
import cats.effect.*
import io.circe.syntax.*

import java.nio.file.{Files, Path}

/** JSON and HTML export logic. */
object Exporter:

  def writeJson(out: Option[String], graph: CytoscapeGraph): IO[ExitCode] =
    import JsonCodecs.given
    val json = graph.asJson.spaces2
    out match
      case Some(path) => IO(Files.writeString(Path.of(path), json)).as(ExitCode.Success)
      case None       => IO.println(json).as(ExitCode.Success)

  def writeHtml(out: Option[String], graph: CytoscapeGraph): IO[ExitCode] =
    import JsonCodecs.given
    val embedded = graph.asJson.noSpaces
    val html =
      s"""<!DOCTYPE html>
         |<html lang="en"><head><meta charset="UTF-8"><title>ATB Export</title>
         |<script src="https://unpkg.com/cytoscape@3.30.2/dist/cytoscape.min.js"></script>
         |<script src="https://unpkg.com/cytoscape-fcose@2.2.0/cytoscape-fcose.js"></script>
         |</head><body><div id="cy" style="width:100vw;height:100vh;background:#1a1a2e"></div>
         |<script>window.GRAPH_DATA=$embedded;</script>
         |<script>
         |cytoscape.use(window.cytoscapeFcose);
         |const data=window.GRAPH_DATA;
         |cytoscape({container:document.getElementById('cy'),elements:[...data.nodes.map(n=>({group:'nodes',data:n.data})),...data.edges.map(e=>({group:'edges',data:e.data}))],style:[{selector:'node',style:{label:'data(label)','background-color':'#4cc9f0',color:'#fff','text-valign':'center','font-size':10}},{selector:'edge',style:{'curve-style':'bezier','target-arrow-shape':'triangle','line-color':'#888','target-arrow-color':'#888','width':'data(weight)'}}],layout:{name:'fcose',animate:true}});
         |</script></body></html>""".stripMargin
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
