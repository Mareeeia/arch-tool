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
      case ViewGranularity.Package => (fqcn: Fqcn) => packageNodeIdFor(fqcn, defaultDepth, expanded)
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

  /** Direct child nodes revealed by expanding `parent` one level at `defaultDepth`. */
  def subnodes(
      graph: DependencyGraph,
      parent: NodeId,
      defaultDepth: Int,
      granularity: ViewGranularity,
      alreadyExpanded: Set[NodeId] = Set.empty
  ): GraphView =
    val before = view(graph, defaultDepth, alreadyExpanded, granularity)
    val after  = view(graph, defaultDepth, alreadyExpanded + parent, granularity)
    val beforeIds = before.nodes.map(_.id).toSet
    val childNodes = after.nodes.filter(n => !beforeIds.contains(n.id))
    val childIds   = childNodes.map(_.id).toSet
    if childIds.isEmpty then GraphView(Vector.empty, Vector.empty, Vector.empty)
    else
      val edges = after.edges.filter(e => childIds.contains(e.from) || childIds.contains(e.to))
      GraphView(childNodes, edges, Vector.empty)

  private def nodeKind(granularity: ViewGranularity, classCount: Int): NodeKind =
    granularity match
      case ViewGranularity.Package => NodeKind.Package
      case ViewGranularity.Class   => if classCount == 1 then NodeKind.Class else NodeKind.Package

  /** Package-only rollup: map a class to its parent package at `depth`, honoring expansion. */
  private def packageNodeIdFor(fqcn: Fqcn, depth: Int, expanded: Set[NodeId]): NodeId =
    rolledNodeId(fqcn, depth, expanded, classAtLeaf = false)

  private def nodeIdFor(fqcn: Fqcn, depth: Int, expanded: Set[NodeId]): NodeId =
    rolledNodeId(fqcn, depth, expanded, classAtLeaf = true)

  /** Walk package depth from `depth`, expanding while the current node id is in `expanded`. */
  private def rolledNodeId(fqcn: Fqcn, depth: Int, expanded: Set[NodeId], classAtLeaf: Boolean): NodeId =
    val parts = fqcn.segments
    if depth <= 0 then NodeId(fqcn.value)
    else if parts.length <= 1 then NodeId(parts.mkString("."))
    else
      val maxPkgDepth = parts.length - 1
      var d           = if depth <= 0 then maxPkgDepth else math.min(depth, maxPkgDepth)
      while d < maxPkgDepth do
        val id = NodeId(parts.take(d).mkString("."))
        if expanded.contains(id) then d += 1
        else return id
      val deepestPkg = NodeId(parts.take(maxPkgDepth).mkString("."))
      if expanded.contains(deepestPkg) && classAtLeaf then NodeId(fqcn.value) else deepestPkg
