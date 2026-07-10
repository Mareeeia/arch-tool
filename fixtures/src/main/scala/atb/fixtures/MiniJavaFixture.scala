package atb.fixtures

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Generates a synthetic Java+git fixture repo for integration tests. */
object MiniJavaFixture:

  val CoupledFiles: Vector[String] = Vector(
    "src/main/java/com/util/X.java",
    "src/main/java/com/util/Y.java"
  )

  def create(): Path =
    val root = Files.createTempDirectory("atb-mini-java-")
    writeSources(root)
    compile(root)
    initGit(root)
    root

  private def writeSources(root: Path): Unit =
    val sources = Vector(
      ("com/a/A.java", "package com.a;\nimport com.b.B;\npublic class A { public B b = new B(); }"),
      ("com/b/B.java", "package com.b;\nimport com.c.C;\npublic class B { public C c = new C(); }"),
      ("com/c/C.java", "package com.c;\nimport com.a.A;\npublic class C { public A a = new A(); }"),
      ("com/util/X.java", "package com.util;\npublic class X { public int value = 1; }"),
      ("com/util/Y.java", "package com.util;\npublic class Y { public X x = new X(); }"),
      (
        "com/service/api/Client.java",
        "package com.service.api;\nimport com.service.impl.Handler;\npublic class Client { public Handler h = new Handler(); }"
      ),
      (
        "com/service/api/Gateway.java",
        "package com.service.api;\npublic class Gateway { public Client client = new Client(); }"
      ),
      (
        "com/service/impl/Handler.java",
        "package com.service.impl;\nimport com.service.repo.Store;\npublic class Handler { public Store store = new Store(); }"
      ),
      (
        "com/service/impl/Processor.java",
        "package com.service.impl;\npublic class Processor { public Handler handler = new Handler(); }"
      ),
      (
        "com/service/repo/Store.java",
        "package com.service.repo;\npublic class Store { public String data = \"ok\"; }"
      )
    )
    sources.foreach { case (rel, content) =>
      val path = root.resolve(s"src/main/java/$rel")
      Files.createDirectories(path.getParent)
      Files.writeString(path, content)
    }

  private def compile(root: Path): Unit =
    val outDir = root.resolve("target/classes")
    Files.createDirectories(outDir)
    val sources = Files
      .walk(root.resolve("src/main/java"))
      .iterator()
      .asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(_.toString)
      .toArray
    val cmd =
      ("javac" +: "-source" +: "21" +: "-target" +: "21" +: "-d" +: outDir.toString +: sources.toSeq).toSeq.asJava
    val process = new ProcessBuilder(cmd).directory(root.toFile).redirectErrorStream(true).start()
    val output  = new String(process.getInputStream.readAllBytes())
    if output.nonEmpty then println(output)
    val code = process.waitFor()
    if code != 0 then throw new RuntimeException(s"javac failed with exit code $code")

  private def initGit(root: Path): Unit =
    val git = Git.init().setDirectory(root.toFile).call()
    try
      val authors = Vector("Alice", "Bob", "Carol")
      (1 to 20).foreach { i =>
        val author = authors(i % authors.size)
        if i % 3 == 0 then
          CoupledFiles.foreach { rel =>
            val path    = root.resolve(rel)
            val content = Files.readString(path) + s"\n// commit $i\n"
            Files.writeString(path, content)
          }
        else
          val path    = root.resolve("src/main/java/com/a/A.java")
          val content = Files.readString(path) + s"\n// commit $i\n"
          Files.writeString(path, content)
        git.add().addFilepattern(".").call()
        git.commit()
          .setMessage(s"commit $i")
          .setAuthor(new PersonIdent(author, s"$author@example.com"))
          .call()
      }
    finally git.close()
