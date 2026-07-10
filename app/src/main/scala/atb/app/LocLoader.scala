package atb.app

import atb.core.history.ChangeSet
import atb.core.model.{ClassMeta, DependencyGraph, Fqcn}

/** Loads line counts for classes from source files when paths are known. */
private[app] object LocLoader:

  type ReadLines = String => Option[Int]

  /** Merge LOC from graph metadata, changesets, and optional file reads. */
  def locByPath(
      graph: DependencyGraph,
      changes: Vector[ChangeSet],
      readLines: ReadLines = _ => None
  ): Map[String, Int] =
    val fromMeta = graph.meta.toVector.flatMap { case (_, m) =>
      m.sourcePath.zip(m.loc)
    }
    val fromFiles = graph.meta.toVector.flatMap { case (_, m) =>
      m.sourcePath.flatMap(p => readLines(p).map(p -> _))
    }
    val fromChanges = changes.flatMap(_.files).map(f => f.path -> (f.linesAdded + f.linesRemoved).max(1))
    (fromMeta ++ fromFiles ++ fromChanges).groupMapReduce(_._1)(_._2)(math.max)

  /** Fill missing LOC in graph metadata from computed path map. */
  def enrichMeta(graph: DependencyGraph, locs: Map[String, Int]): Map[Fqcn, ClassMeta] =
    graph.meta.map { case (fqcn, meta) =>
      val loc = meta.loc.orElse(meta.sourcePath.flatMap(locs.get))
      fqcn -> meta.copy(loc = loc)
    }
