package atb.fixtures

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Generates a synthetic Java+git fixture repo for integration tests. */
object MiniJavaFixture:

  def create(): Path =
    val root = Files.createTempDirectory("atb-mini-java-")
    writeSources(root)
    compile(root)
    initGit(root)
    root

  private def writeSources(root: Path): Unit =
    val cycleA = root.resolve("src/main/java/com/cycle/a/A.java")
    val cycleB = root.resolve("src/main/java/com/cycle/b/B.java")
    val cycleC = root.resolve("src/main/java/com/cycle/c/C.java")
    val utilX  = root.resolve("src/main/java/com/util/X.java")
    val utilY  = root.resolve("src/main/java/com/util/Y.java")
    List(cycleA, cycleB, cycleC, utilX, utilY).foreach { p =>
      Files.createDirectories(p.getParent)
    }

    Files.writeString(
      cycleA,
      """
        |package com.cycle.a;
        |import com.cycle.b.B;
        |public class A { public B b = new B(); }
        |""".stripMargin
    )
    Files.writeString(
      cycleB,
      """
        |package com.cycle.b;
        |import com.cycle.c.C;
        |public class B { public C c = new C(); }
        |""".stripMargin
    )
    Files.writeString(
      cycleC,
      """
        |package com.cycle.c;
        |import com.cycle.a.A;
        |public class C { public A a = new A(); }
        |""".stripMargin
    )
    Files.writeString(
      utilX,
      """
        |package com.util;
        |public class X { public int value = 1; }
        |""".stripMargin
    )
    Files.writeString(
      utilY,
      """
        |package com.util;
        |public class Y { public X x = new X(); }
        |""".stripMargin
    )

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
    val cmd = ("javac" +: "-source" +: "21" +: "-target" +: "21" +: "-d" +: outDir.toString +: sources.toSeq).toSeq.asJava
    val process = new ProcessBuilder(cmd)
      .directory(root.toFile)
      .redirectErrorStream(true)
      .start()
    val output = new String(process.getInputStream.readAllBytes())
    if output.nonEmpty then println(output)
    val code = process.waitFor()
    if code != 0 then throw new RuntimeException(s"javac failed with exit code $code")

  private def initGit(root: Path): Unit =
    val git = Git.init().setDirectory(root.toFile).call()
    try
      val authors = Vector("Alice", "Bob", "Carol")
      val coupled = Vector("src/main/java/com/util/X.java", "src/main/java/com/util/Y.java")
      (1 to 20).foreach { i =>
        val author = authors(i % authors.size)
        val file =
          if i % 3 == 0 then coupled(i % coupled.size)
          else s"src/main/java/com/cycle/a/A.java"
        val path = root.resolve(file)
        val content = Files.readString(path) + s"\n// commit $i\n"
        Files.writeString(path, content)
        git.add().addFilepattern(".").call()
        git.commit()
          .setMessage(s"commit $i")
          .setAuthor(new PersonIdent(author, s"$author@example.com"))
          .call()
      }
    finally git.close()
