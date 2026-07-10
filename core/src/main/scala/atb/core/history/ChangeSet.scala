package atb.core.history

/** A file change within a commit. */
final case class FileChange(path: String, linesAdded: Int, linesRemoved: Int)

/** A VCS changeset with author and file deltas. */
final case class ChangeSet(
    commitId: String,
    author: String,
    timestamp: java.time.Instant,
    files: Vector[FileChange]
)
