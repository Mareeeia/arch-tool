package atb.core

/** Typed errors surfaced by adapters and services. */
enum AtbError:
  case NoCompiledClasses(searched: Vector[String])
  case NotAGitRepo(path: String)
  case InvalidTarget(path: String, reason: String)
  case ProviderFailure(provider: String, message: String)
