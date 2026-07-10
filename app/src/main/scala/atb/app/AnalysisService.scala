package atb.app

import atb.core.AtbError
import atb.core.graph.{PathMapping, Rollup}
import atb.core.history.ChangeSet
import atb.core.metrics.*
import atb.core.model.DependencyGraph
import atb.core.ports.*
import atb.core.view.{GraphView, NodeId}
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream

/** Public application service orchestrating analysis use cases. */
trait AnalysisService[F[_]]:
  def analyze(target: AnalysisTarget, since: Option[java.time.Instant]): F[Either[AtbError, AnalysisResult]]
  def view(depth: Int, expanded: Set[NodeId], overlay: OverlayKind): F[Option[GraphView]]
  def hotspots: F[Vector[Hotspot]]
  def coupling: F[Vector[CouplingPair]]
  def busFactor: F[Vector[ComponentBusFactor]]
  def status: F[AnalysisStatus]

enum AnalysisStatus:
  case Analyzing
  case Ready(result: AnalysisResult)
  case Error(message: String)

object AnalysisService:

  /** Construct a wired analysis service. */
  def make[F[_]: Async](
      graphProvider: DependencyGraphProvider[F],
      historyProvider: VcsHistoryProvider[F]
  ): F[AnalysisService[F]] =
    AnalysisCache.make[F].map(cache => new LiveAnalysisService(graphProvider, historyProvider, cache))

private final class LiveAnalysisService[F[_]: Async](
    graphProvider: DependencyGraphProvider[F],
    historyProvider: VcsHistoryProvider[F],
    cache: AnalysisCache[F]
) extends AnalysisService[F]:

  def analyze(target: AnalysisTarget, since: Option[java.time.Instant]): F[Either[AtbError, AnalysisResult]] =
    for
      graphResult <- graphProvider.dependencies(target)
      result      <- graphResult match
                       case Left(err) => Async[F].pure(Left(err))
                       case Right(graph) =>
                         loadHistory(target, since).flatMap { changes =>
                           Async[F].delay(buildResult(graph, changes, None)).flatMap { r =>
                             cache
                               .put(CacheEntry(target.repoRoot.toString, r.headCommit, r))
                               .as(Right(r))
                           }
                         }
    yield result

  def view(depth: Int, expanded: Set[NodeId], overlay: OverlayKind): F[Option[GraphView]] =
    cache.get.map(_.map { entry =>
      val rolled = Rollup.view(entry.result.graph, depth, expanded)
      Overlay.apply(
        rolled,
        MetricsBundle(entry.result.hotspots, entry.result.busFactor, entry.result.graph.meta),
        overlay
      )
    })

  def hotspots: F[Vector[Hotspot]] =
    cache.get.map(_.fold(Vector.empty[Hotspot])(_.result.hotspots))

  def coupling: F[Vector[CouplingPair]] =
    cache.get.map(_.fold(Vector.empty[CouplingPair])(_.result.coupling))

  def busFactor: F[Vector[ComponentBusFactor]] =
    cache.get.map(_.fold(Vector.empty[ComponentBusFactor])(_.result.busFactor))

  def status: F[AnalysisStatus] =
    cache.get.map:
      case None    => AnalysisStatus.Analyzing
      case Some(e) => AnalysisStatus.Ready(e.result)

  private def loadHistory(target: AnalysisTarget, since: Option[java.time.Instant]): F[Vector[ChangeSet]] =
    historyProvider.changeSets(target, since).compile.toVector

  private def buildResult(
      graph: DependencyGraph,
      changes: Vector[ChangeSet],
      headCommit: Option[String]
  ): AnalysisResult =
    val locs     = LocLoader.locByPath(graph, changes)
    val enriched = graph.copy(meta = LocLoader.enrichMeta(graph, locs))
    val hotspots = Hotspots.compute(changes, locs)
    val coupling = Coupling.compute(changes)
    val bus      = BusFactor.compute(changes, p => PathMapping.toPackage(p, enriched.meta, SourceRoots.Default))
    AnalysisResult(enriched, changes, hotspots, coupling, bus, locs, headCommit)
