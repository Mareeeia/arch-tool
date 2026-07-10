package atb.server

import atb.core.metrics.{ComponentBusFactor, CouplingPair, Hotspot}
import atb.core.model.Pkg
import atb.core.view.{GraphView, NodeKind}
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*

/** Cytoscape-friendly graph JSON shape. */
final case class CyNode(data: Json)
final case class CyEdge(data: Json)
final case class CytoscapeGraph(nodes: Vector[CyNode], edges: Vector[CyEdge], cycles: Vector[Vector[String]])
final case class StatusResponse(state: String, error: Option[String])

object JsonCodecs:

  given Encoder[StatusResponse] = deriveEncoder
  given Encoder[CyNode] = Encoder.instance(n => n.data)
  given Encoder[CyEdge] = Encoder.instance(e => e.data)
  given Encoder[CytoscapeGraph] = Encoder.instance { g =>
    Json.obj(
      "nodes"  -> Json.arr(g.nodes.map(_.asJson)*),
      "edges"  -> Json.arr(g.edges.map(_.asJson)*),
      "cycles" -> g.cycles.asJson
    )
  }
  given Encoder[Hotspot] = deriveEncoder
  given Encoder[CouplingPair] = deriveEncoder
  given Encoder[ComponentBusFactor] = deriveEncoder
  given Encoder[NodeKind] = Encoder.encodeString.contramap(_.toString)
  given Encoder[Pkg] = Encoder.encodeString.contramap(_.value)

  /** Convert a domain GraphView to Cytoscape JSON. */
  def cytoscapeGraph(view: GraphView): CytoscapeGraph =
    val nodes = view.nodes.map { n =>
      CyNode(
        Json.obj(
          "id"           -> n.id.value.asJson,
          "label"        -> n.label.asJson,
          "kind"         -> n.kind.asJson,
          "classCount"   -> n.classCount.asJson,
          "loc"          -> n.loc.asJson,
          "inDegree"     -> n.inDegree.asJson,
          "outDegree"    -> n.outDegree.asJson,
          "hotspotScore" -> n.metrics.hotspotScore.asJson,
          "busFactor"    -> n.metrics.busFactor.asJson,
          "churn"        -> n.metrics.churn.asJson,
          "authors"      -> n.metrics.authors.asJson
        )
      )
    }
    val edges = view.edges.map { e =>
      CyEdge(
        Json.obj(
          "id"     -> s"${e.from.value}->${e.to.value}".asJson,
          "source" -> e.from.value.asJson,
          "target" -> e.to.value.asJson,
          "weight" -> e.weight.asJson,
          "cyclic" -> e.cyclic.asJson
        )
      )
    }
    CytoscapeGraph(nodes, edges, view.cycles.map(_.map(_.value)))
