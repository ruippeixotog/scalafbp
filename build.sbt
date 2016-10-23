import BuildHelpers._
import BuildSettings._

shellPrompt in ThisBuild := { s => Project.extract(s).currentProject.id + " > " }

// -- core component classes --

lazy val core = projectAt("scalafbp-core").
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-actor"                          % "2.4.11",
      "io.reactivex"                  %% "rxscala"                             % "0.26.3",
      "io.spray"                      %% "spray-json"                          % "1.3.2"))

// -- runtime --

lazy val runtime = projectAt("scalafbp-runtime").
  dependsOn(core, testkit % "test", coreComponents % "test").
  settings(commonSettings: _*).
  settings(uiBuildSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "ch.megard"                     %% "akka-http-cors"                      % "0.1.7",
      "com.github.fommil"             %% "spray-json-shapeless"                % "1.3.0",
      "com.github.julien-truffaut"    %% "monocle-core"                        % "1.3.1",
      "com.github.julien-truffaut"    %% "monocle-macro"                       % "1.3.1",
      "com.typesafe"                   % "config"                              % "1.3.1",
      "com.typesafe.akka"             %% "akka-actor"                          % "2.4.11",
      "com.typesafe.akka"             %% "akka-contrib"                        % "2.4.11",
      "com.typesafe.akka"             %% "akka-http-experimental"              % "2.4.11",
      "com.typesafe.akka"             %% "akka-http-spray-json-experimental"   % "2.4.11",
      "com.typesafe.akka"             %% "akka-slf4j"                          % "2.4.11",
      "com.typesafe.akka"             %% "akka-stream"                         % "2.4.11",
      "io.spray"                      %% "spray-json"                          % "1.3.2",
      "org.clapper"                   %% "classutil"                           % "1.0.13",
      "net.ruippeixotog"              %% "akka-testkit-specs2"                 % "0.1.0"            % "test"))

// -- testkit for components --

lazy val testkit = projectAt("scalafbp-testkit").
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "net.ruippeixotog"              %% "akka-testkit-specs2"                 % "0.1.0",
      "org.specs2"                    %% "specs2-core"                         % "3.8.5.1"))

// -- component packages --

lazy val coreComponents = projectAt("scalafbp-components-core").
  dependsOn(core, testkit % "test").
  settings(commonSettings: _*)

lazy val mathComponents = projectAt("scalafbp-components-math").
  dependsOn(core, testkit % "test").
  settings(commonSettings: _*)

lazy val streamComponents = projectAt("scalafbp-components-stream").
  dependsOn(core, testkit % "test").
  settings(commonSettings: _*)

lazy val pplComponents = projectAt("scalafbp-components-ppl").
  dependsOn(core, testkit % "test").
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "net.ruippeixotog"              %% "think-bayes"                         % "1.0-SNAPSHOT"))

// -- runtime with included component packages --

lazy val scalafbp = projectAt("scalafbp").
  dependsOn(core, runtime).
  dependsOn(coreComponents, mathComponents, streamComponents, pplComponents).
  enablePlugins(JavaServerAppPackaging).
  settings(commonSettings: _*).
  settings(dockerPackagingSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback"                 % "logback-classic"                     % "1.1.7"            % "runtime"))

lazy val root = (project in file(".")).
  aggregate(core, testkit, coreComponents, mathComponents, streamComponents, pplComponents, runtime, scalafbp).
  settings(basicSettings: _*)
