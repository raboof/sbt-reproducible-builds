scalaVersion := "3.9.0"

name := "assembly"
organization := "default"

enablePlugins(ReproducibleBuildsPlugin)

addArtifact(Def.setting((Compile / assembly / artifact).value.withClassifier(Some("assembly"))), assembly)

ThisBuild / publishTo := Some(MavenCache("local-maven", file(s"expected_${sbtBinaryVersion.value}")))
