package atb.core.graph

import atb.core.model.{ClassDep, DependencyGraph, Fqcn}

/** Restricts a dependency graph to classes under a package prefix. */
object GraphScope:

  /** Keep only classes (and internal deps) under `prefix`, e.g. `org.springframework.core`. */
  def apply(graph: DependencyGraph, prefix: String): DependencyGraph =
    val root = normalize(prefix)
    if root.isEmpty then graph
    else
      val classes = graph.classes.filter(inScope(_, root))
      val deps    = graph.deps.filter(d => classes.contains(d.from) && classes.contains(d.to))
      val meta    = graph.meta.view.filterKeys(classes.contains).toMap
      DependencyGraph(classes, deps, meta)

  /** True when `fqcn` equals the prefix or lives in a sub-package. */
  def inScope(fqcn: Fqcn, prefix: String): Boolean =
    val root = normalize(prefix)
    fqcn.value == root || fqcn.value.startsWith(s"$root.")

  /** Package prefixes present in the graph, for scope selection UI. */
  def availableScopes(graph: DependencyGraph): Vector[String] =
    graph.classes.toVector
      .flatMap { fqcn =>
        val parts = fqcn.segments
        (2 until parts.length).map(depth => parts.take(depth).mkString("."))
      }
      .distinct
      .sorted

  private def normalize(prefix: String): String =
    prefix.trim.stripPrefix(".").stripSuffix(".")
