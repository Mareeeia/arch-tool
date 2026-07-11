package atb.core.metrics

import atb.core.view.*

/** Instability metric derived from rolled-up dependency degrees (Martin I = Ce / (Ca + Ce)). */
object Stability:

  /** Instability in [0, 1]: 0 = stable (incoming only), 1 = unstable (outgoing only). */
  def instability(inDegree: Int, outDegree: Int): Option[Double] =
    val total = inDegree + outDegree
    if total == 0 then None
    else Some(outDegree.toDouble / total)

  /** Attach instability scores to every node in a graph view. */
  def enrich(view: GraphView): GraphView =
    val nodes = view.nodes.map { n =>
      n.copy(metrics = n.metrics.copy(instability = instability(n.inDegree, n.outDegree)))
    }
    view.copy(nodes = nodes)
