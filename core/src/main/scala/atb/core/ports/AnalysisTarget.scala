package atb.core.ports

import atb.core.AtbError
import atb.core.model.DependencyGraph

import java.nio.file.Path

/** Target repository and classpath configuration for analysis. */
final case class AnalysisTarget(
    repoRoot: Path,
    classDirs: Vector[Path],
    includePattern: Option[String]
)
