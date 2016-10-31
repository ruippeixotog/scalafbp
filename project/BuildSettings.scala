import java.nio.file.NoSuchFileException

import scalariform.formatter.preferences._

import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.SbtScalariform.autoImport._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._

object BuildSettings {

  val buildUi = TaskKey[Unit]("buildUi")

  lazy val basicSettings = Seq(
    organization := "net.ruippeixotog",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.11.8")

  lazy val commonSettings = basicSettings ++ Seq(
    scalariformPreferences := scalariformPreferences.value
      .setPreference(DanglingCloseParenthesis, Prevent)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true),

    resolvers += Resolver.sonatypeRepo("snapshots"),

    libraryDependencies ++= Seq(
      "ch.qos.logback"                 % "logback-classic"                     % "1.1.7"            % "test",
      "org.specs2"                    %% "specs2-core"                         % "3.8.5.1"          % "test"),

    scalacOptions ++= Seq(
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions"),

    fork in Test := true)

  lazy val uiBuildSettings = Seq(
    buildUi := {
      val env = Seq(
        "NOFLO_OAUTH_PROVIDER" -> "/oauth",
        "NOFLO_OAUTH_GATE" -> "/oauth",
        "NOFLO_OAUTH_SERVICE_USER" -> "/oauth",
        "NOFLO_REGISTRY_SERVICE" -> "/registry",
        "NOFLO_APP_NAME" -> "ScalaFBP UI",
        "NOFLO_APP_TITLE" -> "ScalaFBP Development Environment",
        "NOFLO_OFFLINE_MODE" -> "true",
        "NOFLO_APP_ANALYTICS" -> "")

      val uiSrcDir = baseDirectory.value / "src" / "main" / "webapp"
      val uiDistDir = baseDirectory.value / "src" / "main" / "resources" / "ui"

      Process("npm install", uiSrcDir).!
      Process("grunt build", uiSrcDir, env: _*).!

      IO.listFiles(uiDistDir).foreach { file =>
        if(file.getName != ".gitignore") IO.delete(file)
      }

      IO.listFiles(uiSrcDir).find(_.getName.matches("^noflo-.*\\.zip$")) match {
        case Some(zipFile) => IO.unzip(zipFile, uiDistDir)
        case None => throw new NoSuchFileException(s"$uiSrcDir/noflo-<version>.zip was not found")
      }
    },

    packageBin in Compile := {
      buildUi.value
      (packageBin in Compile).value
    })

  lazy val dockerPackagingSettings = Seq(
    mainClass in Compile := Some("net.ruippeixotog.scalafbp.Server"),

    mappings in (Compile, packageBin) ~= { _.filterNot {
      case (_, resName) => resName != "application.conf"
    }},

    maintainer in Docker := "Rui Gonçalves <ruippeixotog@gmail.com>",
    dockerExposedPorts in Docker := Seq(3569),
    dockerRepository := Some("ruippeixotog"))
}
