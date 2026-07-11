package atb.core.view

/** Temporal coupling between two rolled-up components (git co-change, not a dependency). */
final case class CouplingEdge(
    from: NodeId,
    to: NodeId,
    confidence: Double,
    coChanges: Int,
    filePairs: Int
)
