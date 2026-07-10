package atb.adapter.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}

import java.time.Instant
import scala.jdk.CollectionConverters.*

/** First-parent commit walk over the default branch. */
private[git] object CommitWalker:

  def walk(git: Git, since: Option[Instant]): Vector[RevCommit] =
    val repo = git.getRepository
    val walk = new RevWalk(repo)
    try
      val head = repo.resolve("HEAD")
      if head == null then Vector.empty
      else
        walk.markStart(walk.parseCommit(head))
        walk.setRevFilter(RevFilter.NO_MERGES)
        walk.iterator().asScala
          .filter(c => since.forall(s => c.getCommitTime.toLong >= s.getEpochSecond))
          .toVector
    finally walk.close()
