package atb.core.view

/** Stable identifier for a node in a rolled-up graph view. */
final case class NodeId(value: String)

/** Whether a view node represents a package or a single class. */
enum NodeKind:
  case Package, Class

/** Metrics attached to a view node for overlay coloring. */
final case class NodeMetrics(
    instability: Option[Double],
    hotspotScore: Option[Double],
    busFactor: Option[Int],
    churn: Option[Int],
    authors: Option[Int]
)

object NodeMetrics:
  val Empty: NodeMetrics = NodeMetrics(None, None, None, None, None)

/** A node in the rolled-up graph served to the UI. */
final case class ViewNode(
    id: NodeId,
    label: String,
    kind: NodeKind,
    classCount: Int,
    loc: Int,
    inDegree: Int,
    outDegree: Int,
    metrics: NodeMetrics
)

/** An aggregated edge between rolled-up nodes. */
final case class ViewEdge(from: NodeId, to: NodeId, weight: Int, cyclic: Boolean)

/** Rolled-up graph view with cycle information. */
final case class GraphView(
    nodes: Vector[ViewNode],
    edges: Vector[ViewEdge],
    cycles: Vector[Vector[NodeId]],
    couplingEdges: Vector[CouplingEdge] = Vector.empty
)
