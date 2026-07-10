package atb.core.model

/** Kind of dependency between two classes. */
enum DepKind:
  case FieldAccess, MethodCall, Inheritance, Reference

/** A single class-level dependency edge. */
final case class ClassDep(from: Fqcn, to: Fqcn, kind: DepKind)

/** Metadata about a class when known. */
final case class ClassMeta(sourcePath: Option[String], loc: Option[Int])

/** Raw class-level dependency graph from a provider. */
final case class DependencyGraph(
    classes: Set[Fqcn],
    deps: Vector[ClassDep],
    meta: Map[Fqcn, ClassMeta]
)
