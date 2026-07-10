package atb.core.ports

import atb.core.AtbError
import atb.core.model.DependencyGraph

/** Port for extracting class-level dependency graphs from compiled artifacts. */
trait DependencyGraphProvider[F[_]]:
  def name: String
  def supports(target: AnalysisTarget): F[Boolean]
  def dependencies(target: AnalysisTarget): F[Either[AtbError, DependencyGraph]]
