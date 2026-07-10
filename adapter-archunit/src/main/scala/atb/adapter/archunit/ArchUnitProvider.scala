package atb.adapter.archunit

import atb.core.AtbError
import atb.core.model.*
import atb.core.ports.*
import cats.effect.*
import cats.syntax.all.*
import com.tngtech.archunit.core.importer.ClassFileImporter

import scala.jdk.CollectionConverters.*

/** Extracts dependency graphs from compiled Java bytecode via ArchUnit. */
class ArchUnitProvider[F[_]: Sync] extends DependencyGraphProvider[F]:

  val name: String = "archunit"

  def supports(target: AnalysisTarget): F[Boolean] =
    Sync[F].delay:
      val dirs = ClassDirLocator.resolve(target.repoRoot, target.classDirs)
      dirs.nonEmpty

  def dependencies(target: AnalysisTarget): F[Either[AtbError, DependencyGraph]] =
    Sync[F].blocking:
      try
        val classDirs = ClassDirLocator.resolve(target.repoRoot, target.classDirs)
        if classDirs.isEmpty then
          Left(AtbError.NoCompiledClasses(ClassDirLocator.searchedPaths(target.repoRoot, target.classDirs)))
        else
          Right(importGraph(classDirs, target.includePattern))
      catch
        case e: Exception =>
          Left(AtbError.ProviderFailure(name, Option(e.getMessage).getOrElse(e.toString)))

  private def importGraph(classDirs: Vector[java.nio.file.Path], includePattern: Option[String]): DependencyGraph =
    val javaClasses = classDirs.flatMap { dir =>
      new ClassFileImporter().importPath(dir).asScala
    }.distinct
    val filtered    = javaClasses.filter(jc => DepTranslator.matchesInclude(jc, includePattern))
    val classes     = filtered.flatMap(jc => Fqcn.parse(Option(jc.getFullName).getOrElse(jc.getName))).toSet
    val deps = filtered.flatMap { jc =>
      jc.getDirectDependenciesFromSelf.asScala.toVector.flatMap { dep =>
        DepTranslator.toClassDep(jc, dep).filter(d => classes.contains(d.to))
      }
    }
    val meta = filtered.flatMap { jc =>
      val name = Option(jc.getFullName).getOrElse(jc.getName)
      Fqcn.parse(name).map(_ -> ClassMeta(DepTranslator.sourcePath(jc), None))
    }.toMap
    DependencyGraph(classes, deps.toVector, meta)

object ArchUnitProvider:
  def apply[F[_]: Sync]: ArchUnitProvider[F] = new ArchUnitProvider[F]
