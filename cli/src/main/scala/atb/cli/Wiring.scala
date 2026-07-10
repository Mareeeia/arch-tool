package atb.cli

import atb.adapter.archunit.ArchUnitProvider
import atb.adapter.git.JGitHistoryProvider
import atb.app.AnalysisService
import cats.effect.*
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Constructs providers, service, and server — the single wiring point. */
object Wiring:

  def service: IO[AnalysisService[IO]] =
    Slf4jLogger.create[IO].flatMap { implicit logger =>
      logger.info("Wiring Architecture Toolbox providers") >>
        AnalysisService.make(ArchUnitProvider[IO], JGitHistoryProvider[IO])
    }
