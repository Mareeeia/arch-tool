package atb.server

import atb.core.metrics.OverlayKind
import atb.core.view.NodeId
import org.http4s.Request

/** Parsed and validated graph query parameters. */
final case class GraphQuery(depth: Int, expanded: Set[NodeId], overlay: OverlayKind)

/** Query parameter extractors for API routes. */
private[server] object QueryParams:

  def graphQuery(req: Request[cats.effect.IO]): GraphQuery =
    GraphQuery(
      depth = req.params.get("depth").flatMap(_.toIntOption).getOrElse(2),
      expanded = req.params.get("expanded").fold(Set.empty[NodeId]) { s =>
        s.split(",").filter(_.nonEmpty).map(NodeId(_)).toSet
      },
      overlay = req.params.get("overlay") match
        case Some("hotspot")   => OverlayKind.Hotspot
        case Some("busfactor") => OverlayKind.BusFactor
        case _                 => OverlayKind.None
    )

  def limit(req: Request[cats.effect.IO], default: Int = 50): Int =
    req.params.get("limit").flatMap(_.toIntOption).getOrElse(default)
