scalaVersion := "2.12.21"

name := "assembly"
organization := "default"

enablePlugins(ReproducibleBuildsPlugin)

addArtifact(Def.setting((Compile / assembly / artifact).value.withClassifier(Some("assembly"))), assembly)

ThisBuild / publishTo := Some(MavenCache("local-maven", file("expected")))
