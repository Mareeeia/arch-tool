package atb.adapter.git

import atb.core.history.FileChange
import org.eclipse.jgit.diff.{DiffEntry, DiffFormatter}
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.util.io.DisabledOutputStream

import scala.jdk.CollectionConverters.*

/** Extracts per-commit file changes via JGit diffs. */
private[git] object DiffExtractor:

  def changes(repo: Repository, commit: RevCommit): Vector[FileChange] =
    if commit.getParentCount == 0 then Vector.empty
    else diff(repo, commit.getParent(0).getTree, commit.getTree)

  private def diff(
      repo: Repository,
      oldTree: org.eclipse.jgit.lib.AnyObjectId,
      newTree: org.eclipse.jgit.lib.AnyObjectId
  ): Vector[FileChange] =
    val reader = repo.newObjectReader()
    try
      val oldParser = new CanonicalTreeParser()
      oldParser.reset(reader, oldTree)
      val newParser = new CanonicalTreeParser()
      newParser.reset(reader, newTree)
      val formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)
      formatter.setRepository(repo)
      formatter.setDetectRenames(true)
      formatter.scan(oldParser, newParser).asScala.toVector.flatMap(entryToChange)
    finally reader.close()

  private def entryToChange(entry: DiffEntry): Option[FileChange] =
    val path = Option(entry.getNewPath).filter(_ != DiffEntry.DEV_NULL).getOrElse(entry.getOldPath)
    Option.when(path != DiffEntry.DEV_NULL)(FileChange(path, 1, 0))
