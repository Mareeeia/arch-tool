package atb.core.view

/** Controls whether the graph shows uniform package nodes or mixed package/class nodes. */
enum ViewGranularity:
  /** Every node is a package at the configured depth — no class nodes in the graph. */
  case Package
  /** Classes and packages may both appear (singleton packages render as class nodes; expand/collapse applies). */
  case Class

object ViewGranularity:

  def parse(value: String): Option[ViewGranularity] =
    value.trim.toLowerCase match
      case "class" | "classes"   => Some(ViewGranularity.Class)
      case "package" | "packages" => Some(ViewGranularity.Package)
      case _                     => None
