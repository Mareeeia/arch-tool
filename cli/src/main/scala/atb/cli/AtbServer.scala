package atb.cli

import atb.app.AnalysisService
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*

/** Boots the http4s server for the interactive UI. */
object AtbServer:

  def serve(service: AnalysisService[IO], port: Int): Resource[IO, Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(port).get)
      .withHttpApp(atb.server.Routes(service).orNotFound)
      .build
      .void
