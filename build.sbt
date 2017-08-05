sbtPlugin := true

organization := "net.bzzt"

resolvers += Resolver.mavenLocal
// Based on https://github.com/raboof/reproducible-build-maven-plugin
classpathTypes += "maven-plugin"
libraryDependencies += "io.github.zlika" % "reproducible-build-maven-plugin" % "0.3-SNAPSHOT"
