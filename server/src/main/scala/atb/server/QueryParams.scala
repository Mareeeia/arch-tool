package atb.server

import atb.core.metrics.OverlayKind
import atb.core.view.{NodeId, ViewGranularity}
import org.http4s.Request

/** Parsed and validated graph query parameters. */
final case class GraphQuery(
    depth: Int,
    expanded: Set[NodeId],
    overlay: OverlayKind,
    scope: Option[String],
    group: ViewGranularity
)

/** Query parameter extractors for API routes. */
private[server] object QueryParams:

  def graphQuery(req: Request[cats.effect.IO]): GraphQuery =
    GraphQuery(
      depth = depth(req),
      expanded = req.params.get("expanded").fold(Set.empty[NodeId]) { s =>
        s.split(",").filter(_.nonEmpty).map(NodeId(_)).toSet
      },
      overlay = req.params.get("overlay") match
        case Some("hotspot")   => OverlayKind.Hotspot
        case Some("busfactor") => OverlayKind.BusFactor
        case _                 => OverlayKind.None
      ,
      scope = req.params.get("scope").map(_.trim).filter(_.nonEmpty),
      group = req.params.get("group").flatMap(ViewGranularity.parse).getOrElse(ViewGranularity.Package)
    )

  def depth(req: Request[cats.effect.IO], default: Int = 2): Int =
    req.params.get("depth").flatMap(_.toIntOption).getOrElse(default)

  def limit(req: Request[cats.effect.IO], default: Int = 50): Int =
    req.params.get("limit").flatMap(_.toIntOption).getOrElse(default)
