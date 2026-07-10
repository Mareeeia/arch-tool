package atb.app

/** Discovers Java/Kotlin source root prefixes for path mapping. */
private[app] object SourceRoots:

  val Default: Vector[String] = Vector(
    "src/main/java/",
    "src/test/java/",
    "src/main/kotlin/",
    "src/",
    "main/java/",
    "test/java/"
  )
