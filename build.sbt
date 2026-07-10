ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.4"

lazy val root = (project in file("."))
  .settings(
    name := "arch-tool",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.1.0" % Test
    )
  )
