import scalariform.formatter.preferences._

inThisBuild(Seq(
  organization := "net.ruippeixotog",
  homepage := Some(url("https://github.com/ruippeixotog/scalafbp")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
  version := "0.1.0-SNAPSHOT"))

// -- core component classes --

lazy val core = (project in file("core")).
  settings(commonSettings).
  settings(name := "scalafbp-core")

// -- runtime --

lazy val runtime = (project in file("runtime")).
  dependsOn(core, testkit % "test", `components-core` % "test").
  settings(commonSettings).
  settings(name := "scalafbp-runtime")

// -- testkit for components --

lazy val testkit = (project in file("testkit")).
  dependsOn(core).
  settings(commonSettings).
  settings(name := "scalafbp-testkit")

// -- component packages --

def componentPack(proj: Project, packName: String) = (proj in file(s"components/$packName")).
  dependsOn(core, testkit % "test").
  settings(commonSettings).
  settings(name := s"scalafbp-components-$packName")

lazy val `components-core` = componentPack(project, "core")
lazy val `components-math` = componentPack(project, "math")
lazy val `components-stream` = componentPack(project, "stream")
lazy val `components-ppl` = componentPack(project, "ppl")

// -- runtime with included component packages --

lazy val bundle = (project in file("bundle")).
  dependsOn(core, runtime, `components-core`, `components-math`, `components-stream`, `components-ppl`).
  settings(commonSettings).
  settings(name := "scalafbp")

// -- common settings --


val commonSettings = Seq(
  scalaVersion := "2.12.3",

  resolvers += Resolver.sonatypeRepo("snapshots"),

  scalariformPreferences := scalariformPreferences.value
    .setPreference(DanglingCloseParenthesis, Prevent)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true),

  libraryDependencies ++= Seq(
    "ch.qos.logback"                 % "logback-classic"                     % "1.2.3"            % "test",
    "org.specs2"                    %% "specs2-core"                         % "3.8.6"            % "test"),

  scalacOptions ++= Seq(
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions"),

  fork in Test := true)
