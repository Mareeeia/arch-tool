package atb.server

import cats.effect.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*

/** Serves static web assets from classpath resources. */
private[server] object StaticAssets:

  private val AssetFiles = List(
    "app.js",
    "style.css",
    "cytoscape.min.js"
  )

  def routes: HttpRoutes[IO] =
    val index = HttpRoutes.of[IO] { case GET -> Root =>
      StaticFile.fromResource[IO]("/web/index.html", None).getOrElseF(NotFound())
    }
    val favicon = HttpRoutes.of[IO] { case GET -> Root / "favicon.ico" =>
      NoContent()
    }
    val assets = HttpRoutes.of[IO] {
      case req @ GET -> Root / file if AssetFiles.contains(file) =>
        StaticFile.fromResource[IO](s"/web/$file", Some(req)).getOrElseF(NotFound())
    }
    index <+> favicon <+> assets
