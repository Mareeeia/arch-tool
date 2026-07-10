package atb.adapter.archunit

import atb.core.model.*
import com.tngtech.archunit.core.domain.{Dependency, JavaClass}

/** Pure translation from ArchUnit types to domain ClassDep. */
private[archunit] object DepTranslator:

  def toClassDep(from: JavaClass, dep: Dependency): Option[ClassDep] =
    for
      fromFqcn <- Fqcn.parse(from.getName)
      toFqcn   <- Fqcn.parse(dep.getTargetClass.getName)
    yield
      val kind = dep.getDescription.toLowerCase match
        case d if d.contains("field")       => DepKind.FieldAccess
        case d if d.contains("constructor") => DepKind.MethodCall
        case d if d.contains("method")      => DepKind.MethodCall
        case d if d.contains("extends")     => DepKind.Inheritance
        case d if d.contains("implements")  => DepKind.Inheritance
        case _                              => DepKind.Reference
      ClassDep(fromFqcn, toFqcn, kind)

  def sourcePath(jc: JavaClass): Option[String] =
    Option(jc.getSourceCodeLocation).map(_.toString)

  def matchesInclude(jc: JavaClass, includePattern: Option[String]): Boolean =
    val pkg = jc.getPackageName
    !pkg.startsWith("java.") && !pkg.startsWith("javax.") && !pkg.startsWith("jdk.") &&
    includePattern.forall(p => jc.getName.matches(translatePattern(p)))

  private def translatePattern(pattern: String): String =
    pattern.replace("..", ".*").replace(".", "\\.")
