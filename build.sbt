import scalariform.formatter.preferences._

name := "scalafbp"
organization := "net.ruippeixotog"
version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.8"

// resolvers ++= Seq(
//   Resolver.sonatypeRepo("snapshots"),
//   "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases")

libraryDependencies ++= Seq(
  "com.github.nscala-time"     %% "nscala-time"          % "2.12.0",
  "com.typesafe.akka"             %% "akka-actor"                         % "2.4.8",
  "com.typesafe.akka"             %% "akka-stream"                         % "2.4.8",
  "com.typesafe.akka"             %% "akka-http-experimental"              % "2.4.8",
  "com.typesafe.akka"             %% "akka-http-spray-json-experimental"   % "2.4.8",
  "ch.qos.logback"                 % "logback-classic"          % "1.1.3"  % "runtime",
  "com.typesafe.akka"             %% "akka-slf4j"                         % "2.4.8",
  "com.typesafe"                % "config"               % "1.3.0",
  "com.github.fommil" %% "spray-json-shapeless" % "1.2.0",
  "io.spray"                   %% "spray-json"           % "1.3.2",
  "org.java-websocket"          % "Java-WebSocket"       % "1.3.0",
  "org.specs2"                 %% "specs2-core"          % "3.8.4"                % "test")

scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)

//scalacOptions += "-Xlog-implicits"
