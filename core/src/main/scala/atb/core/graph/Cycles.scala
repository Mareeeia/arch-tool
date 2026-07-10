package atb.core.graph

import atb.core.view.*

import scala.collection.mutable

/** Strongly connected components via Tarjan's algorithm. */
object Cycles:

  /** Returns SCCs of size > 1 as cycles. */
  def tarjan(view: GraphView): Vector[Vector[NodeId]] =
    val adj = view.edges.groupBy(_.from).view.mapValues(_.map(_.to).distinct.toVector).toMap
    val allNodes = view.nodes.map(_.id).toSet
    val graph    = allNodes.map(n => n -> adj.getOrElse(n, Vector.empty)).toMap

    val index   = mutable.Map.empty[NodeId, Int]
    val lowLink = mutable.Map.empty[NodeId, Int]
    val onStack = mutable.Set.empty[NodeId]
    val stack   = mutable.Stack.empty[NodeId]
    var idx     = 0
    val sccs    = mutable.Buffer.empty[Vector[NodeId]]

    def strongConnect(v: NodeId): Unit =
      index(v) = idx
      lowLink(v) = idx
      idx += 1
      stack.push(v)
      onStack += v
      graph(v).foreach { w =>
        if !index.contains(w) then
          strongConnect(w)
          lowLink(v) = lowLink(v) min lowLink(w)
        else if onStack.contains(w) then lowLink(v) = lowLink(v) min index(w)
      }
      if lowLink(v) == index(v) then
        val comp = mutable.Buffer.empty[NodeId]
        var w    = stack.pop()
        onStack -= w
        comp += w
        while w != v do
          w = stack.pop()
          onStack -= w
          comp += w
        if comp.size > 1 then sccs += comp.toVector

    allNodes.foreach(n => if !index.contains(n) then strongConnect(n))
    sccs.toVector
