package atb.app

import cats.effect.*
import cats.effect.Ref
import cats.syntax.all.*

/** Ref-based cache keyed by repository path and HEAD commit. */
private[app] final class AnalysisCache[F[_]: Sync](ref: Ref[F, Option[CacheEntry]]):

  def get: F[Option[CacheEntry]] = ref.get

  /** Returns cached analysis when repo path and HEAD match. */
  def getIfValid(repoRoot: String, headCommit: Option[String]): F[Option[CacheEntry]] =
    ref.get.map(_.filter(e => e.repoRoot == repoRoot && e.headCommit == headCommit))

  def put(entry: CacheEntry): F[Unit] = ref.set(Some(entry))

private[app] object AnalysisCache:

  def make[F[_]: Sync]: F[AnalysisCache[F]] =
    Ref.of[F, Option[CacheEntry]](None).map(new AnalysisCache(_))

private[app] final case class CacheEntry(
    repoRoot: String,
    headCommit: Option[String],
    result: AnalysisResult
)
