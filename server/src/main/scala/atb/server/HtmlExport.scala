package atb.server

import io.circe.{Json, Printer}
import io.circe.syntax.*

/** Self-contained HTML export with vendored JS inlined. */
object HtmlExport:

  private val sortedPrinter: Printer = Printer.spaces2.copy(sortKeys = true)

  def render(graph: CytoscapeGraph): String =
    import JsonCodecs.given
    val embedded = sortedPrinter.print(graph.asJson)
    val cyJs = loadResource("/web/cytoscape.min.js")
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8"/>
       |  <title>Architecture Toolbox Export</title>
       |  <style>
       |    body { margin: 0; background: #1a1a2e; }
       |    #cy { width: 100vw; height: 100vh; }
       |  </style>
       |  <script>$cyJs</script>
       |</head>
       |<body>
       |  <div id="cy"></div>
       |  <script>window.GRAPH_DATA=$embedded;</script>
       |  <script>
       |    const data = window.GRAPH_DATA;
       |    cytoscape({
       |      container: document.getElementById('cy'),
       |      elements: [
       |        ...data.nodes.map(n => ({ group: 'nodes', data: n.data ?? n })),
       |        ...data.edges.map(e => ({ group: 'edges', data: e.data ?? e }))
       |      ],
       |      style: [
       |        { selector: 'node', style: {
       |            label: 'data(label)', color: '#fff', 'text-valign': 'center', 'font-size': 10,
       |            'background-color': '#4cc9f0'
       |        }},
       |        { selector: 'edge', style: {
       |            'curve-style': 'bezier', 'target-arrow-shape': 'triangle',
       |            'line-color': ele => ele.data('cyclic') ? '#ff4466' : '#888',
       |            'target-arrow-color': ele => ele.data('cyclic') ? '#ff4466' : '#888',
       |            width: ele => Math.log(ele.data('weight') + 1) * 2 + 1
       |        }}
       |      ],
       |      layout: { name: 'cose', animate: true, padding: 40 }
       |    });
       |  </script>
       |</body>
       |</html>""".stripMargin

  def sortedJson(graph: CytoscapeGraph): String =
    import JsonCodecs.given
    sortedPrinter.print(graph.asJson)

  private def loadResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match
      case None    => throw new IllegalStateException(s"Missing resource: $path")
      case Some(is) =>
        try new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        finally is.close()
