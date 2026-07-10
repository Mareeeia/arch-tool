package atb.app

import atb.core.history.ChangeSet
import atb.core.metrics.{ComponentBusFactor, CouplingPair, Hotspot}
import atb.core.model.DependencyGraph
import atb.core.view.GraphView

/** Immutable result of a full repository analysis. */
final case class AnalysisResult(
    graph: DependencyGraph,
    changeSets: Vector[ChangeSet],
    hotspots: Vector[Hotspot],
    coupling: Vector[CouplingPair],
    busFactor: Vector[ComponentBusFactor],
    locByPath: Map[String, Int],
    headCommit: Option[String]
)
