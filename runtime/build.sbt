import java.nio.file.NoSuchFileException

libraryDependencies ++= Seq(
  "ch.megard"                     %% "akka-http-cors"                      % "0.2.1",
  "com.github.fommil"             %% "spray-json-shapeless"                % "1.4.0",
  "com.github.julien-truffaut"    %% "monocle-core"                        % "1.4.0",
  "com.github.julien-truffaut"    %% "monocle-macro"                       % "1.4.0",
  "com.typesafe"                   % "config"                              % "1.3.1",
  "com.typesafe.akka"             %% "akka-actor"                          % "2.4.19",
  "com.typesafe.akka"             %% "akka-contrib"                        % "2.4.19",
  "com.typesafe.akka"             %% "akka-http"                           % "10.0.9",
  "com.typesafe.akka"             %% "akka-http-spray-json"                % "10.0.9",
  "com.typesafe.akka"             %% "akka-slf4j"                          % "2.4.19",
  "com.typesafe.akka"             %% "akka-stream"                         % "2.4.19",
  "io.spray"                      %% "spray-json"                          % "1.3.3",
  "org.clapper"                   %% "classutil"                           % "1.1.2",
  "net.ruippeixotog"              %% "akka-testkit-specs2"                 % "0.2.1"            % "test")

val buildUi = TaskKey[Unit]("buildUi")

buildUi := {
  val env = Seq(
    "NOFLO_OAUTH_PROVIDER" -> "/oauth",
    "NOFLO_OAUTH_GATE" -> "/oauth",
    "NOFLO_OAUTH_SERVICE_USER" -> "/oauth",
    "NOFLO_OAUTH_CLIENT_REDIRECT" -> "http://localhost:3569",
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
}

packageBin in Compile := {
  buildUi.value
  (packageBin in Compile).value
}
