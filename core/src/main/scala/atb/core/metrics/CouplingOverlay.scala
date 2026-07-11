package atb.core.metrics

import atb.core.graph.Rollup
import atb.core.model.DependencyGraph
import atb.core.view.*

/** Maps file-level temporal coupling onto rolled-up graph components. */
object CouplingOverlay:

  private final case class Agg(maxConfidence: Double, coChanges: Int, filePairs: Int)

  /** Attach ghost coupling edges between distinct visible components. */
  def enrich(
      view: GraphView,
      coupling: Vector[CouplingPair],
      graph: DependencyGraph,
      depth: Int,
      expanded: Set[NodeId],
      granularity: ViewGranularity
  ): GraphView =
    val visible = view.nodes.map(_.id).toSet
    val nodeFor = (path: String) => Rollup.nodeForSourcePath(path, graph, depth, expanded, granularity)

    val aggregated = coupling.flatMap { pair =>
      for
        na <- nodeFor(pair.a)
        nb <- nodeFor(pair.b)
        if na != nb
      yield
        val (from, to) = order(na, nb)
        ((from, to), Agg(pair.confidence, pair.coChanges, 1))
    }.groupMapReduce(_._1) { case (_, agg) => agg }(merge)

    val edges = aggregated.toVector
      .filter { case ((from, to), _) => visible.contains(from) && visible.contains(to) }
      .sortBy { case (_, agg) => (-agg.maxConfidence, -agg.coChanges) }
      .map { case ((from, to), agg) =>
        CouplingEdge(from, to, agg.maxConfidence, agg.coChanges, agg.filePairs)
      }

    view.copy(couplingEdges = edges)

  private def order(a: NodeId, b: NodeId): (NodeId, NodeId) =
    if a.value <= b.value then (a, b) else (b, a)

  private def merge(left: Agg, right: Agg): Agg =
    Agg(
      maxConfidence = math.max(left.maxConfidence, right.maxConfidence),
      coChanges = left.coChanges + right.coChanges,
      filePairs = left.filePairs + right.filePairs
    )
