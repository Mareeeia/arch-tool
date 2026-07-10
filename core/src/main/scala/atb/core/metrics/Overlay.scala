package atb.core.metrics

import atb.core.graph.PathMapping
import atb.core.model.*
import atb.core.view.*

/** Which metric overlay to apply when rendering the graph. */
enum OverlayKind:
  case None, Hotspot, BusFactor

/** Precomputed metrics used by overlay application. */
final case class MetricsBundle(
    hotspots: Vector[Hotspot],
    busFactor: Vector[ComponentBusFactor],
    meta: Map[Fqcn, ClassMeta]
)

/** Merges metric values into view node metrics for UI coloring. */
object Overlay:

  /** Apply the selected overlay kind to a metrics-less graph view. */
  def apply(view: GraphView, metrics: MetricsBundle, kind: OverlayKind): GraphView =
    kind match
      case OverlayKind.None      => view
      case OverlayKind.Hotspot   => applyHotspots(view, metrics)
      case OverlayKind.BusFactor => applyBusFactor(view, metrics)

  private def applyHotspots(view: GraphView, metrics: MetricsBundle): GraphView =
    val scoreByPkg = metrics.hotspots.flatMap { h =>
      PathMapping.sourcePathToPackage(h.path).map(_.value -> h.score)
    }.groupMapReduce(_._1)(_._2)(math.max)

    val nodes = view.nodes.map { n =>
      val score = scoreByPkg.get(n.id.value)
      n.copy(metrics = n.metrics.copy(hotspotScore = score, churn = score.map(_ * 100).map(_.toInt)))
    }
    view.copy(nodes = nodes)

  private def applyBusFactor(view: GraphView, metrics: MetricsBundle): GraphView =
    val byPkg = metrics.busFactor.map(f => f.component.value -> f.busFactor).toMap
    val nodes = view.nodes.map { n =>
      val bf = byPkg.get(n.id.value)
      n.copy(metrics = n.metrics.copy(busFactor = bf, authors = bf))
    }
    view.copy(nodes = nodes)
