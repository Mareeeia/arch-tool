package atb.server

import atb.app.{AnalysisService, AnalysisStatus}
import cats.effect.*
import cats.syntax.all.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.server.middleware.CORS

/** HTTP routes for the Architecture Toolbox API. */
object Routes:

  import JsonCodecs.given

  def apply(service: AnalysisService[IO]): HttpRoutes[IO] =
    val api = HttpRoutes.of[IO] {
      case GET -> Root / "api" / "status" =>
        service.status.flatMap {
          case AnalysisStatus.Analyzing     => Ok(StatusResponse("analyzing", None).asJson)
          case AnalysisStatus.Ready(_)      => Ok(StatusResponse("ready", None).asJson)
          case AnalysisStatus.Error(msg)    => Ok(StatusResponse("error", Some(msg)).asJson)
        }

      case req @ GET -> Root / "api" / "graph" =>
        val q = QueryParams.graphQuery(req)
        service.view(q.depth, q.expanded, q.overlay, q.scope, q.group).flatMap {
          case None       => NotFound("Analysis not ready")
          case Some(view) => Ok(JsonCodecs.cytoscapeGraph(view).asJson)
        }

      case req @ GET -> Root / "api" / "metrics" / "hotspots" =>
        service.hotspots.flatMap(hs => Ok(hs.take(QueryParams.limit(req)).asJson))

      case req @ GET -> Root / "api" / "metrics" / "coupling" =>
        service.coupling.flatMap(cs => Ok(cs.take(QueryParams.limit(req)).asJson))

      case GET -> Root / "api" / "metrics" / "busfactor" =>
        service.busFactor.flatMap(bf => Ok(bf.asJson))
    }

    CORS.policy.withAllowOriginAll(api <+> StaticAssets.routes)
