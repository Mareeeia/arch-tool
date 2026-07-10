package atb.core.model

/** Fully qualified class name, e.g. `com.acme.billing.Invoice`. */
opaque type Fqcn = String

object Fqcn:

  /** Parses a dotted JVM class name; rejects empty or invalid segments. */
  def parse(value: String): Option[Fqcn] =
    val trimmed = value.trim
    if trimmed.isEmpty then None
    else
      val segments = trimmed.split("\\.")
      if segments.forall(isValidSegment) then Some(trimmed)
      else None

  extension (f: Fqcn)
    /** Raw dotted class name. */
    def value: String = f

    /** Package containing this class. */
    def packageName: Pkg = Pkg.parse(segments.init.mkString(".")).getOrElse(Pkg.Root)

    /** Dot-separated name segments. */
    def segments: Vector[String] = value.split("\\.").toVector

  private def isValidSegment(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')
