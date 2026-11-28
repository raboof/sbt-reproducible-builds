addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")

resolvers += Resolver.sonatypeCentralSnapshots
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.35+25-23fb226d-SNAPSHOT")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.10")
