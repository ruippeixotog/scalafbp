import java.nio.file.NoSuchFileException

import scalariform.formatter.preferences._

name := "scalafbp"
organization := "net.ruippeixotog"
version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "ch.megard"                     %% "akka-http-cors"                      % "0.1.4",
  "com.github.fommil"             %% "spray-json-shapeless"                % "1.3.0",
  "com.github.julien-truffaut"    %% "monocle-core"                        % "1.2.2",
  "com.github.julien-truffaut"    %% "monocle-macro"                       % "1.2.2",
  "com.typesafe"                   % "config"                              % "1.3.0",
  "com.typesafe.akka"             %% "akka-actor"                          % "2.4.8",
  "com.typesafe.akka"             %% "akka-contrib"                        % "2.4.8",
  "com.typesafe.akka"             %% "akka-http-experimental"              % "2.4.8",
  "com.typesafe.akka"             %% "akka-http-spray-json-experimental"   % "2.4.8",
  "com.typesafe.akka"             %% "akka-slf4j"                          % "2.4.8",
  "com.typesafe.akka"             %% "akka-stream"                         % "2.4.8",
  "io.spray"                      %% "spray-json"                          % "1.3.2",
  "org.clapper"                   %% "classutil"                           % "1.0.12",
  "ch.qos.logback"                 % "logback-classic"                     % "1.1.7"   % "runtime",
  "com.typesafe.akka"             %% "akka-testkit"                        % "2.4.8"   % "test",
  "org.specs2"                    %% "specs2-core"                         % "3.8.4"   % "test")

scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)

fork in Test := true

// -- general packaging settings --

enablePlugins(JavaServerAppPackaging)

val buildUi = TaskKey[Unit]("buildUi")

buildUi := {
  val env = Seq(
    "NOFLO_REGISTRY_SERVICE" -> "/registry",
    "NOFLO_APP_NAME" -> "ScalaFBP UI",
    "NOFLO_APP_TITLE" -> "ScalaFBP Development Environment",
    "NOFLO_OFFLINE_MODE" -> "true",
    "NOFLO_APP_ANALYTICS" -> "")

  Process("npm install", file("src/main/webapp")).!
  Process("grunt build", file("src/main/webapp"), env: _*).!

  IO.listFiles(file("src/main/resources/ui")).foreach { file =>
    if(file.getName != ".gitignore") IO.delete(file)
  }

  IO.listFiles(file("src/main/webapp")).find(_.getName.matches("^noflo-.*\\.zip$")) match {
    case Some(zipFile) => IO.unzip(zipFile, file("src/main/resources/ui"))
    case None => throw new NoSuchFileException("src/main/webapp/noflo-<version>.zip was not found")
  }
}

packageBin in Compile <<= (packageBin in Compile).dependsOn(buildUi)

val excludedResources = Seq("application.conf")
mappings in (Compile, packageBin) ~= { _.filterNot {
  case (_, resName) => excludedResources.contains(resName)
}}

// -- Docker packaging settings --

maintainer in Docker := "Rui Gonçalves <ruippeixotog@gmail.com>"
dockerExposedPorts in Docker := Seq(3569)
dockerRepository := Some("ruippeixotog")
