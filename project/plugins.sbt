addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")

addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.32+10-1c096efd-SNAPSHOT")
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.1")
