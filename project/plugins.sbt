addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.1")

//resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.32")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.5")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.3")
