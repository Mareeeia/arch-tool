package atb.app

import atb.core.AtbError
import atb.core.graph.{PathMapping, Rollup, GraphScope}
import atb.core.history.ChangeSet
import atb.core.metrics.*
import atb.core.model.DependencyGraph
import atb.core.ports.*
import atb.core.view.{GraphView, NodeId, ViewGranularity}
import cats.effect.*
import cats.effect.implicits.given
import cats.syntax.all.*
import fs2.Stream

import java.nio.file.{Files, Path}

/** Public application service orchestrating analysis use cases. */
trait AnalysisService[F[_]]:
  def analyze(target: AnalysisTarget, since: Option[java.time.Instant]): F[Either[AtbError, AnalysisResult]]
  def view(
      depth: Int,
      expanded: Set[NodeId],
      overlay: OverlayKind,
      scope: Option[String],
      group: ViewGranularity
  ): F[Option[GraphView]]
  def hotspots: F[Vector[Hotspot]]
  def coupling: F[Vector[CouplingPair]]
  def busFactor: F[Vector[ComponentBusFactor]]
  def scopes: F[Vector[String]]
  def nodeChildren(
      nodeId: NodeId,
      depth: Int,
      expanded: Set[NodeId],
      overlay: OverlayKind,
      scope: Option[String],
      group: ViewGranularity
  ): F[Option[GraphView]]
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
    val repoKey = target.repoRoot.toString
    for
      head         <- historyProvider.headCommit(target)
      cached       <- cache.getIfValid(repoKey, head)
      shortCircuit <- cached match
                          case Some(entry) => Async[F].pure(Some(Right(entry.result)))
                          case None        => Async[F].pure(None)
      result       <- shortCircuit match
                          case Some(r) => Async[F].pure(r)
                          case None    => runAnalysis(target, since, head)
    yield result

  def view(
      depth: Int,
      expanded: Set[NodeId],
      overlay: OverlayKind,
      scope: Option[String],
      group: ViewGranularity
  ): F[Option[GraphView]] =
    cache.get.map(_.map { entry =>
      val graph = scope match
        case Some(prefix) => GraphScope(entry.result.graph, prefix)
        case None         => entry.result.graph
      finishView(
        graph,
        depth,
        expanded,
        group,
        overlay,
        entry,
        Stability.enrich(Rollup.view(graph, depth, expanded, group))
      )
    })

  def hotspots: F[Vector[Hotspot]] =
    cache.get.map(_.fold(Vector.empty[Hotspot])(_.result.hotspots))

  def coupling: F[Vector[CouplingPair]] =
    cache.get.map(_.fold(Vector.empty[CouplingPair])(_.result.coupling))

  def busFactor: F[Vector[ComponentBusFactor]] =
    cache.get.map(_.fold(Vector.empty[ComponentBusFactor])(_.result.busFactor))

  def scopes: F[Vector[String]] =
    cache.get.map(_.fold(Vector.empty[String])(e => GraphScope.availableScopes(e.result.graph)))

  def nodeChildren(
      nodeId: NodeId,
      depth: Int,
      expanded: Set[NodeId],
      overlay: OverlayKind,
      scope: Option[String],
      group: ViewGranularity
  ): F[Option[GraphView]] =
    cache.get.map(_.map { entry =>
      val graph = scope match
        case Some(prefix) => GraphScope(entry.result.graph, prefix)
        case None         => entry.result.graph
      finishView(
        graph,
        depth,
        expanded,
        group,
        overlay,
        entry,
        Stability.enrich(Rollup.subnodes(graph, nodeId, depth, group, expanded))
      )
    })

  def status: F[AnalysisStatus] =
    cache.get.map:
      case None    => AnalysisStatus.Analyzing
      case Some(e) => AnalysisStatus.Ready(e.result)

  private def finishView(
      graph: DependencyGraph,
      depth: Int,
      expanded: Set[NodeId],
      group: ViewGranularity,
      overlay: OverlayKind,
      entry: CacheEntry,
      rolled: GraphView
  ): GraphView =
    val withMetrics = Overlay.apply(
      rolled,
      MetricsBundle(entry.result.hotspots, entry.result.busFactor, graph.meta),
      overlay
    )
    overlay match
      case OverlayKind.Coupling =>
        CouplingOverlay.enrich(withMetrics, entry.result.coupling, graph, depth, expanded, group)
      case _ => withMetrics

  private def runAnalysis(
      target: AnalysisTarget,
      since: Option[java.time.Instant],
      head: Option[String]
  ): F[Either[AtbError, AnalysisResult]] =
    val graphF   = graphProvider.dependencies(target)
    val historyF = historyProvider.changeSets(target, since).compile.toVector
    (graphF, historyF).parMapN { case (graphResult, changes) =>
      graphResult.map(graph => buildResult(target.repoRoot, graph, changes, head))
    }.flatMap {
      case left @ Left(_) => Async[F].pure(left)
      case Right(r)       => cache.put(CacheEntry(target.repoRoot.toString, head, r)).as(Right(r))
    }

  private def buildResult(
      repoRoot: Path,
      graph: DependencyGraph,
      changes: Vector[ChangeSet],
      headCommit: Option[String]
  ): AnalysisResult =
    val readLines = fileLineCountReader(repoRoot)
    val locs      = LocLoader.locByPath(graph, changes, readLines)
    val enriched  = graph.copy(meta = LocLoader.enrichMeta(graph, locs))
    val hotspots  = Hotspots.compute(changes, locs)
    val coupling  = Coupling.compute(changes)
    val bus       = BusFactor.compute(changes, p => PathMapping.toPackage(p, enriched.meta, SourceRoots.Default))
    AnalysisResult(enriched, changes, hotspots, coupling, bus, locs, headCommit)

  private def fileLineCountReader(repoRoot: Path): LocLoader.ReadLines =
    path =>
      val candidates = Vector(
        repoRoot.resolve(path),
        repoRoot.resolve(path.stripPrefix("./"))
      )
      candidates
        .find(p => Files.isRegularFile(p))
        .map(f => Files.readString(f).split("\n").count(_.nonEmpty).max(1))
