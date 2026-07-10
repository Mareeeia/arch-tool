package atb.core.ports

import atb.core.history.ChangeSet
import fs2.Stream

import java.time.Instant

/** Port for streaming VCS history as changesets. */
trait VcsHistoryProvider[F[_]]:
  def name: String
  def changeSets(repo: AnalysisTarget, since: Option[Instant]): Stream[F, ChangeSet]
  /** Current HEAD commit id, if the target is a git repository. */
  def headCommit(repo: AnalysisTarget): F[Option[String]]
