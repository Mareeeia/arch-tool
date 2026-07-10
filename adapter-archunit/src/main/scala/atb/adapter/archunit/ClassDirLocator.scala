package atb.adapter.archunit

import java.nio.file.{Files, Path}

/** Locates compiled class directories within a repository. */
private[archunit] object ClassDirLocator:

  val Candidates: Vector[String] = Vector(
    "target/classes",
    "build/classes/java/main",
    "out/production"
  )

  /** Resolve class dirs from explicit paths or by searching candidates. */
  def resolve(
      repoRoot: Path,
      explicit: Vector[Path],
      listDir: Path => Vector[Path] = defaultListDir
  ): Vector[Path] =
    if explicit.nonEmpty then explicit.filter(p => Files.isDirectory(p))
    else Candidates.flatMap(rel => findDirs(repoRoot, rel, listDir)).distinct

  def searchedPaths(repoRoot: Path, explicit: Vector[Path]): Vector[String] =
    if explicit.nonEmpty then explicit.map(_.toString)
    else Candidates.map(rel => repoRoot.resolve(rel).toString)

  private def findDirs(root: Path, relative: String, listDir: Path => Vector[Path]): Vector[Path] =
    val direct = root.resolve(relative)
    if Files.isDirectory(direct) then Vector(direct)
    else if Files.isDirectory(root) then listDir(root).filter(_.endsWith(relative))
    else Vector.empty

  private def defaultListDir(root: Path): Vector[Path] =
    import scala.jdk.CollectionConverters.*
    Files.walk(root, 4).iterator().asScala.filter(p => Files.isDirectory(p)).toVector
