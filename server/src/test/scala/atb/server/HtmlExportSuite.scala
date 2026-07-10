package atb.server

import atb.core.view.*
import io.circe.Json
import munit.FunSuite

class HtmlExportSuite extends FunSuite:

  test("html export inlines vendored cytoscape scripts"):
    val graph = CytoscapeGraph(
      nodes = Vector(CyNode(Json.obj("id" -> Json.fromString("com.a"), "label" -> Json.fromString("com.a")))),
      edges = Vector.empty,
      cycles = Vector.empty
    )
    val html = HtmlExport.render(graph)
    assert(!html.contains("unpkg.com"))
    assert(!html.contains("cdn.jsdelivr"))
    assert(html.contains("window.GRAPH_DATA"))
    assert(html.contains("name: 'cose'"))

  test("sortedJson uses stable key ordering"):
    val graph = CytoscapeGraph(
      nodes = Vector(CyNode(Json.obj("id" -> Json.fromString("z"), "label" -> Json.fromString("z")))),
      edges = Vector.empty,
      cycles = Vector.empty
    )
    val once = HtmlExport.sortedJson(graph)
    val twice = HtmlExport.sortedJson(graph)
    assertEquals(once, twice)
