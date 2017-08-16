sbtPlugin := true

organization := "net.bzzt"

// Based on https://github.com/raboof/reproducible-build-maven-plugin
resolvers += Resolver.mavenLocal
libraryDependencies += "io.github.zlika" % "reproducible-build" % "0.3-SNAPSHOT"
