package atb.server

import cats.effect.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Cache-Control`
import org.http4s.implicits.*

/** Serves static web assets from classpath resources. */
private[server] object StaticAssets:

  private val AssetFiles = List(
    "app.js",
    "style.css",
    "cytoscape.min.js"
  )

  // Force revalidation on every load so UI changes show up on plain reload.
  private def noCache(resp: Response[IO]): Response[IO] =
    resp.putHeaders(`Cache-Control`(CacheDirective.`no-cache`()))

  def routes: HttpRoutes[IO] =
    val index = HttpRoutes.of[IO] { case GET -> Root =>
      StaticFile.fromResource[IO]("/web/index.html", None).getOrElseF(NotFound()).map(noCache)
    }
    val favicon = HttpRoutes.of[IO] { case GET -> Root / "favicon.ico" =>
      NoContent()
    }
    val assets = HttpRoutes.of[IO] {
      case GET -> Root / file if AssetFiles.contains(file) =>
        StaticFile.fromResource[IO](s"/web/$file", None).getOrElseF(NotFound()).map(noCache)
    }
    index <+> favicon <+> assets
