package atb.adapter.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk

import java.time.Instant

/** First-parent commit walk over the default branch. */
private[git] object CommitWalker:

  def walk(git: Git, since: Option[Instant]): Vector[org.eclipse.jgit.revwalk.RevCommit] =
    val repo = git.getRepository
    val walk = new RevWalk(repo)
    try
      val head = repo.resolve("HEAD")
      if head == null then Vector.empty
      else
        var current = walk.parseCommit(head)
        val buf     = Vector.newBuilder[org.eclipse.jgit.revwalk.RevCommit]
        while
          current != null && since.forall(s => current.getCommitTime.toLong >= s.getEpochSecond)
        do
          buf += current
          current =
            if current.getParentCount > 0 then walk.parseCommit(current.getParent(0))
            else null
        buf.result()
    finally walk.close()

  def headId(git: Git): Option[String] =
    Option(git.getRepository.resolve("HEAD")).map(_.getName)
