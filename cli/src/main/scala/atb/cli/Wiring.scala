package atb.cli

import atb.adapter.archunit.ArchUnitProvider
import atb.adapter.git.JGitHistoryProvider
import atb.app.AnalysisService
import cats.effect.*

/** Constructs providers, service, and server — the single wiring point. */
object Wiring:

  def service: IO[AnalysisService[IO]] =
    AnalysisService.make(ArchUnitProvider[IO], JGitHistoryProvider[IO])
