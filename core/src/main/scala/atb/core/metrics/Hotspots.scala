package atb.core.metrics

import atb.core.history.ChangeSet

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Hotspot metric for a source file. */
final case class Hotspot(path: String, revisions: Int, loc: Int, score: Double)

/** Tunable hotspot computation parameters. */
final case class HotspotConfig(halfLifeDays: Int, ignoreGlobs: Vector[String])

object HotspotConfig:
  val Default: HotspotConfig = HotspotConfig(
    halfLifeDays = 180,
    ignoreGlobs = Vector(
      "**/test/**",
      "**/tests/**",
      "**/node_modules/**",
      "**/*.lock",
      "**/target/**",
      "**/build/**"
    )
  )

/** Computes change-frequency hotspot scores from VCS history. */
object Hotspots:

  /** Compute hotspot scores with exponential recency decay. */
  def compute(
      changes: Vector[ChangeSet],
      locByPath: Map[String, Int],
      config: HotspotConfig = HotspotConfig.Default,
      now: Instant = Instant.now()
  ): Vector[Hotspot] =
    val paths = changes.flatMap(_.files.map(_.path)).distinct.filterNot(p => shouldIgnore(p, config.ignoreGlobs))
    if paths.isEmpty then Vector.empty
    else
      val revisions = paths.map { path =>
        val score = changes.foldLeft(0.0) { (acc, cs) =>
          if cs.files.exists(_.path == path) then
            val ageDays = ChronoUnit.DAYS.between(cs.timestamp, now).max(0)
            acc + math.pow(0.5, ageDays.toDouble / config.halfLifeDays)
          else acc
        }
        path -> (score.toInt, score)
      }.toMap

      val locs      = paths.map(p => p -> locByPath.getOrElse(p, 1)).toMap
      val normRev   = normalize(revisions.view.mapValues(_._2).toMap)
      val normLoc   = normalize(locs.view.mapValues(_.toDouble).toMap)

      paths
        .map { path =>
          Hotspot(path, revisions(path)._1, locs(path), normRev(path) * normLoc(path))
        }
        .sortBy(-_.score)

  private def normalize(values: Map[String, Double]): Map[String, Double] =
    if values.isEmpty then values
    else
      val min = values.values.min
      val max = values.values.max
      if max == min then values.view.mapValues(_ => 1.0).toMap
      else values.view.mapValues(v => (v - min) / (max - min)).toMap

  private def shouldIgnore(path: String, globs: Vector[String]): Boolean =
    globs.exists(g => simpleGlobMatch(g, path))

  private def simpleGlobMatch(pattern: String, path: String): Boolean =
    val normalized = path.replace('\\', '/')
    if pattern.contains("**") then
      pattern.split("\\*\\*/").filter(_.nonEmpty).forall(p => normalized.contains(p.replace("*", "")))
    else pattern.replace("*", ".*").r.matches(normalized)
