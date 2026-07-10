package atb.core.graph

import atb.core.model.*

/** Maps source file paths to FQCNs and packages for metric overlay. */
object PathMapping:

  val DefaultSourceRoots: Vector[String] = Vector(
    "src/main/java/",
    "src/test/java/",
    "src/main/kotlin/",
    "src/",
    "main/java/",
    "test/java/"
  )

  /** Maps a file path to its FQCN using known source-root prefixes. */
  def toFqcn(path: String, sourceRoots: Vector[String] = DefaultSourceRoots): Option[Fqcn] =
    strippedPath(path, sourceRoots).flatMap { stripped =>
      val segments = stripped.split("/").filter(_.nonEmpty)
      if segments.length <= 1 then None
      else
        val className = segments.last.stripSuffix(".java").stripSuffix(".kt").stripSuffix(".scala")
        val pkg       = segments.init.mkString(".")
        if className.nonEmpty && segments.init.forall(isValidSegment) then
          Fqcn.parse(s"$pkg.$className")
        else None
    }

  /** Returns package for a source path using metadata or heuristics. */
  def toPackage(path: String, meta: Map[Fqcn, ClassMeta], sourceRoots: Vector[String] = DefaultSourceRoots): Option[Pkg] =
    meta.values.view
      .flatMap(_.sourcePath)
      .find(p => path.endsWith(p) || p.endsWith(path))
      .flatMap(p => sourcePathToPackage(p, sourceRoots))
      .orElse(sourcePathToPackage(path, sourceRoots))

  /** Strips known source roots and converts directory path to a dotted package. */
  def sourcePathToPackage(path: String, sourceRoots: Vector[String] = DefaultSourceRoots): Option[Pkg] =
    strippedPath(path, sourceRoots).flatMap { stripped =>
      val withoutExt = stripped.stripSuffix(".java").stripSuffix(".kt").stripSuffix(".scala")
      val segments   = withoutExt.split("/").filter(_.nonEmpty)
      if segments.length <= 1 then None
      else
        val pkgSegments = segments.init
        if pkgSegments.forall(isValidSegment) then Pkg.parse(pkgSegments.mkString("."))
        else None
    }

  private def strippedPath(path: String, sourceRoots: Vector[String]): Option[String] =
    val normalized = path.replace('\\', '/')
    sourceRoots
      .flatMap(root => Option.when(normalized.contains(root))(normalized.split(root, 2).last))
      .headOption
      .orElse(Some(normalized))

  private def isValidSegment(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')
