scalaVersion := "3.9.0"

name := "assembly"
organization := "default"

ThisBuild / publishTo := Some(MavenCache("local-maven", file("/tmp")))

enablePlugins(ReproducibleBuildsPlugin)

addArtifact(Def.setting((Compile / assembly / artifact).value.withClassifier(Some("assembly"))), assembly)
