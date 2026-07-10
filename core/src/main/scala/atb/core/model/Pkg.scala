package atb.core.model

/** Java package name, e.g. `com.acme.billing`. */
opaque type Pkg = String

object Pkg:

  /** Parses a dotted package name. */
  def parse(value: String): Option[Pkg] =
    if value.isEmpty then Some("")
    else
      val segments = value.split("\\.")
      if segments.forall(isValidSegment) then Some(value)
      else None

  /** Default empty package. */
  val Root: Pkg = ""

  extension (p: Pkg)
    def value: String = p

    /** Number of dot-separated segments (0 for root). */
    def depth: Int = if value.isEmpty then 0 else value.count(_ == '.') + 1

    /** Parent package, if any. */
    def parent: Option[Pkg] =
      if value.isEmpty then None
      else value.lastIndexOf('.') match
        case -1  => Some(Root)
        case idx => Pkg.parse(value.substring(0, idx))

    /** Truncate to the first `depth` segments. */
    def truncate(depth: Int): Pkg =
      if depth <= 0 then Root
      else Pkg.parse(value.split("\\.").take(depth).mkString(".")).getOrElse(Root)

  private def isValidSegment(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')
