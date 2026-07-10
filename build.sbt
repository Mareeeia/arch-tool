ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "com.atb"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings")

lazy val catsVersion       = "2.12.0"
lazy val catsEffectVersion = "3.5.7"
lazy val fs2Version        = "3.11.0"
lazy val http4sVersion     = "0.23.30"
lazy val circeVersion      = "0.14.10"
lazy val declineVersion    = "2.5.0"
lazy val archunitVersion   = "1.4.2"
lazy val jgitVersion       = "6.9.0.202403050737-r"
lazy val log4catsVersion   = "2.7.0"
lazy val munitVersion      = "1.0.4"
lazy val scalacheckVersion = "1.18.1"

lazy val commonSettings = Seq(
  Test / parallelExecution := false,
  libraryDependencies ++= Seq(
    "org.scalameta" %% "munit" % munitVersion % Test
  )
)

lazy val root = (project in file("."))
  .aggregate(core, app, adapterArchunit, adapterGit, server, cli, fixtures)
  .settings(
    name := "architecture-toolbox",
    publish / skip := true
  )

lazy val core = (project in file("core"))
  .settings(commonSettings *)
  .settings(
    name := "atb-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "co.fs2"        %% "fs2-core"  % fs2Version,
      "org.scalameta" %% "munit-scalacheck" % "1.0.0" % Test,
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test
    )
  )

lazy val app = (project in file("app"))
  .dependsOn(core)
  .dependsOn(fixtures % Test, adapterArchunit % Test, adapterGit % Test)
  .settings(commonSettings *)
  .settings(
    name := "atb-app",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test
    )
  )

lazy val adapterArchunit = (project in file("adapter-archunit"))
  .dependsOn(core)
  .dependsOn(fixtures % Test)
  .settings(commonSettings *)
  .settings(
    name := "atb-adapter-archunit",
    libraryDependencies ++= Seq(
      "org.typelevel"    %% "cats-effect" % catsEffectVersion,
      "com.tngtech.archunit" % "archunit" % archunitVersion,
      "org.typelevel"    %% "munit-cats-effect" % "2.0.0" % Test
    )
  )

lazy val adapterGit = (project in file("adapter-git"))
  .dependsOn(core)
  .dependsOn(fixtures % Test)
  .settings(commonSettings *)
  .settings(
    name := "atb-adapter-git",
    libraryDependencies ++= Seq(
      "org.typelevel"          %% "cats-effect" % catsEffectVersion,
      "org.eclipse.jgit"       % "org.eclipse.jgit" % jgitVersion,
      "org.typelevel"          %% "munit-cats-effect" % "2.0.0" % Test
    )
  )

lazy val server = (project in file("server"))
  .dependsOn(app, core)
  .dependsOn(fixtures % Test, adapterArchunit % Test, adapterGit % Test)
  .settings(commonSettings *)
  .settings(
    name := "atb-server",
    libraryDependencies ++= Seq(
      "org.typelevel"    %% "cats-effect"     % catsEffectVersion,
      "org.http4s"       %% "http4s-ember-server" % http4sVersion,
      "org.http4s"       %% "http4s-dsl"      % http4sVersion,
      "org.http4s"       %% "http4s-circe"   % http4sVersion,
      "io.circe"         %% "circe-core"     % circeVersion,
      "io.circe"         %% "circe-generic"  % circeVersion,
      "org.typelevel"    %% "munit-cats-effect" % "2.0.0" % Test,
      "io.circe"         %% "circe-parser"   % circeVersion % Test
    )
  )

lazy val fixtures = (project in file("fixtures"))
  .dependsOn(core)
  .settings(commonSettings *)
  .settings(
    name := "atb-fixtures",
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % jgitVersion
    )
  )

lazy val cli = (project in file("cli"))
  .dependsOn(core, app, adapterArchunit, adapterGit, server)
  .dependsOn(fixtures % Test)
  .settings(commonSettings *)
  .settings(
    name := "atb-cli",
    Compile / mainClass := Some("atb.cli.Main"),
    libraryDependencies ++= Seq(
      "org.typelevel"    %% "cats-effect"   % catsEffectVersion,
      "org.http4s"       %% "http4s-ember-server" % http4sVersion,
      "com.monovore"     %% "decline"       % declineVersion,
      "com.monovore"     %% "decline-effect" % declineVersion,
      "org.typelevel"    %% "log4cats-slf4j" % log4catsVersion,
      "org.slf4j"        % "slf4j-simple"  % "2.0.16" % Runtime,
      "org.typelevel"    %% "munit-cats-effect" % "2.0.0" % Test,
      "io.circe"         %% "circe-parser" % circeVersion % Test
    )
  )
