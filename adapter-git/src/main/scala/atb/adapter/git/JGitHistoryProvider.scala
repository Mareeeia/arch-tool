package atb.adapter.git

import atb.core.AtbError
import atb.core.history.ChangeSet
import atb.core.ports.*
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import org.eclipse.jgit.api.Git

import java.nio.file.Path
import java.time.Instant

/** Streams VCS history from a git repository via JGit. */
class JGitHistoryProvider[F[_]: Sync] extends VcsHistoryProvider[F]:

  val name: String = "jgit"

  def changeSets(repo: AnalysisTarget, since: Option[Instant]): Stream[F, ChangeSet] =
    Stream.eval(Sync[F].blocking(openRepo(repo.repoRoot))).flatMap {
      case Left(_)    => Stream.empty
      case Right(git) =>
        val emitted = Stream.eval(Sync[F].blocking(collect(git, since))).flatMap(Stream.emits)
        emitted.onFinalizeWeak(Sync[F].blocking { git.close(); () })
    }

  private def openRepo(root: Path): Either[AtbError, Git] =
    try
      if !root.resolve(".git").toFile.exists() then Left(AtbError.NotAGitRepo(root.toString))
      else Right(Git.open(root.toFile))
    catch
      case _: Exception => Left(AtbError.NotAGitRepo(root.toString))

  private def collect(git: Git, since: Option[Instant]): Vector[ChangeSet] =
    val repository = git.getRepository
    CommitWalker.walk(git, since).map { commit =>
      ChangeSet(
        commit.getName,
        commit.getAuthorIdent.getName,
        Instant.ofEpochSecond(commit.getCommitTime.toLong),
        DiffExtractor.changes(repository, commit)
      )
    }

object JGitHistoryProvider:
  def apply[F[_]: Sync]: JGitHistoryProvider[F] = new JGitHistoryProvider[F]
