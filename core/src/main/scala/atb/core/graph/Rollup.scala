package atb.core.graph

import atb.core.model.*
import atb.core.view.*

/** Rolls up class-level graphs to package views at configurable depth. */
object Rollup:

  /** Roll up all classes to the given package depth (0 = class level in class mode). */
  def at(graph: DependencyGraph, depth: Int, granularity: ViewGranularity = ViewGranularity.Class): GraphView =
    view(graph, depth, Set.empty, granularity)

  /** Roll up with mixed expansion for specific package nodes. */
  def view(
      graph: DependencyGraph,
      defaultDepth: Int,
      expanded: Set[NodeId],
      granularity: ViewGranularity = ViewGranularity.Class
  ): GraphView =
    val nodeForClass = granularity match
      case ViewGranularity.Package => (fqcn: Fqcn) => packageOnlyNodeId(fqcn, defaultDepth)
      case ViewGranularity.Class   => (fqcn: Fqcn) => nodeIdFor(fqcn, defaultDepth, expanded)

    val classLocs    = graph.meta.view.mapValues(_.loc.getOrElse(0)).toMap
    val classesByNode = graph.classes.groupBy(nodeForClass)
    val nodeClassCounts = classesByNode.view.mapValues(_.size).toMap
    val nodeLocs = classesByNode.view.mapValues { classes =>
      classes.map(c => classLocs.getOrElse(c, 0)).sum
    }.toMap

    val edgeWeights = graph.deps
      .flatMap { dep =>
        val from = nodeForClass(dep.from)
        val to   = nodeForClass(dep.to)
        Option.when(from != to)((from, to))
      }
      .groupMapReduce(identity)(_ => 1)(_ + _)

    val nodeIds = (classesByNode.keys ++ edgeWeights.keys.flatMap { case (f, t) => List(f, t) }).toSet
    val inDeg   = edgeWeights.keys.groupMapReduce(_._2)(_ => 1)(_ + _)
    val outDeg  = edgeWeights.keys.groupMapReduce(_._1)(_ => 1)(_ + _)

    val nodes = nodeIds.toVector.sortBy(_.value).map { id =>
      val count = nodeClassCounts.getOrElse(id, 0)
      ViewNode(
        id = id,
        label = id.value,
        kind = nodeKind(granularity, count),
        classCount = count,
        loc = nodeLocs.getOrElse(id, 0),
        inDegree = inDeg.getOrElse(id, 0),
        outDegree = outDeg.getOrElse(id, 0),
        metrics = NodeMetrics.Empty
      )
    }

    val edges = edgeWeights.toVector.sortBy { case ((f, t), _) => (f.value, t.value) }.map {
      case ((from, to), weight) => ViewEdge(from, to, weight, cyclic = false)
    }

    val rawView       = GraphView(nodes, edges, Vector.empty)
    val cycles        = Cycles.tarjan(rawView)
    val cyclicEdges   = cycles.flatMap(c => c.zip(c.tail :+ c.head).map { case (a, b) => (a, b) }).toSet
    val markedEdges   = edges.map(e => if cyclicEdges.contains((e.from, e.to)) then e.copy(cyclic = true) else e)
    GraphView(nodes, markedEdges, cycles)

  /** Total edge weight sum — preserved by rollup. */
  def totalWeight(view: GraphView): Int = view.edges.map(_.weight).sum

  private def nodeKind(granularity: ViewGranularity, classCount: Int): NodeKind =
    granularity match
      case ViewGranularity.Package => NodeKind.Package
      case ViewGranularity.Class   => if classCount == 1 then NodeKind.Class else NodeKind.Package

  /** Package-only rollup: always map a class to its parent package at `depth`. */
  private def packageOnlyNodeId(fqcn: Fqcn, depth: Int): NodeId =
    val parts = fqcn.segments
    if parts.length <= 1 then NodeId(parts.mkString("."))
    else
      val pkgDepth =
        if depth <= 0 then parts.length - 1
        else math.min(depth, parts.length - 1)
      NodeId(parts.take(pkgDepth).mkString("."))

  private def nodeIdFor(fqcn: Fqcn, depth: Int, expanded: Set[NodeId]): NodeId =
    val parts = fqcn.segments
    if depth <= 0 then NodeId(fqcn.value)
    else
      val parentId = NodeId(parts.take(depth).mkString("."))
      if expanded.contains(parentId) && parts.length > depth then
        NodeId(parts.take(depth + 1).mkString("."))
      else if expanded.contains(parentId) && parts.length == depth then
        NodeId(fqcn.value)
      else
        parentId
