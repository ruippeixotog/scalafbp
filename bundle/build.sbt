enablePlugins(JavaServerAppPackaging)

libraryDependencies ++= Seq(
  "ch.qos.logback"                 % "logback-classic"                     % "1.2.3"            % "runtime")

mainClass in Compile := Some("net.ruippeixotog.scalafbp.Server")

publishArtifact := false

mappings in (Compile, packageBin) ~= { _.filterNot {
  case (_, resName) => resName != "application.conf"
}}

maintainer in Docker := "Rui Gonçalves <ruippeixotog@gmail.com>"
dockerExposedPorts in Docker := Seq(3569)
dockerRepository := Some("ruippeixotog")
