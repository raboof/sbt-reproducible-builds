addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.8.0")

addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.32+53-86fb0a63-SNAPSHOT")
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("net.bzzt" % "sbt-strict-scala-versions" % "0.0.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.13.0")
