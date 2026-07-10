package atb.core.metrics

import atb.core.history.ChangeSet
import atb.core.model.Pkg

/** Bus factor metric for a package component. */
final case class ComponentBusFactor(
    component: Pkg,
    busFactor: Int,
    topAuthors: Vector[(String, Double)]
)

/** Computes bus factor per package component from VCS authorship. */
object BusFactor:

  /** Minimum authors whose combined knowledge covers at least half of a component. */
  def compute(
      changes: Vector[ChangeSet],
      componentOf: String => Option[Pkg]
  ): Vector[ComponentBusFactor] =
    val knowledge = scala.collection.mutable.Map.empty[Pkg, Map[String, Double]].withDefaultValue(Map.empty)

    changes.foreach { cs =>
      cs.files.foreach { fc =>
        componentOf(fc.path).foreach { comp =>
          val current = knowledge(comp)
          knowledge(comp) = current.updated(cs.author, current.getOrElse(cs.author, 0.0) + fc.linesAdded)
        }
      }
    }

    knowledge.toVector
      .filter(_._2.nonEmpty)
      .map { case (comp, authorKnowledge) =>
        val sorted = authorKnowledge.toVector.sortBy(-_._2)
        val total  = sorted.map(_._2).sum
        val shares = sorted.map { case (a, k) => (a, if total == 0 then 0.0 else k / total) }
        ComponentBusFactor(comp, minimumAuthorsForHalf(shares.map(_._2)).max(1), shares.take(5))
      }
      .sortBy(_.component.value)

  private def minimumAuthorsForHalf(shares: Vector[Double]): Int =
    shares.sorted.reverse.foldLeft((0.0, 0)) { case ((cum, n), s) =>
      if cum >= 0.5 then (cum, n) else (cum + s, n + 1)
    }._2
