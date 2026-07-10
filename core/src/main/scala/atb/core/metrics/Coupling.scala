package atb.core.metrics

import atb.core.graph.PathMapping
import atb.core.history.ChangeSet

/** Temporal coupling between two files. */
final case class CouplingPair(
    a: String,
    b: String,
    coChanges: Int,
    support: Int,
    confidence: Double
)

/** Tunable temporal coupling parameters. */
final case class CouplingConfig(minSupport: Int, minConfidence: Double, maxCommitSize: Int)

object CouplingConfig:
  val Default: CouplingConfig = CouplingConfig(minSupport = 5, minConfidence = 0.4, maxCommitSize = 50)

/** Computes temporal coupling between files that change together. */
object Coupling:

  /** Find file pairs that co-change frequently. */
  def compute(
      changes: Vector[ChangeSet],
      config: CouplingConfig = CouplingConfig.Default
  ): Vector[CouplingPair] =
    val filtered    = changes.filter(_.files.size <= config.maxCommitSize)
    val changeCount = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    val pairCount   = scala.collection.mutable.Map.empty[(String, String), Int].withDefaultValue(0)

    filtered.foreach { cs =>
      val paths = cs.files.map(_.path).distinct.sorted
      paths.foreach(p => changeCount(p) += 1)
      paths.combinations(2).foreach {
        case Vector(a, b) =>
          val key = if a <= b then (a, b) else (b, a)
          pairCount(key) += 1
        case _ => ()
      }
    }

    pairCount.toVector
      .flatMap { case ((a, b), co) =>
        val support = changeCount(a) min changeCount(b)
        val conf    = if support == 0 then 0.0 else co.toDouble / support
        Option.when(support >= config.minSupport && conf >= config.minConfidence)(
          CouplingPair(a, b, co, support, conf)
        )
      }
      .sortBy(p => (-p.confidence, -p.coChanges))

  /** True when two paths belong to different top-level packages (hidden coupling). */
  def isHiddenCoupling(a: String, b: String): Boolean =
    topLevelPackage(a) != topLevelPackage(b)

  private def topLevelPackage(path: String): Option[String] =
    PathMapping.sourcePathToPackage(path).map(_.value.split("\\.").headOption.getOrElse(""))
